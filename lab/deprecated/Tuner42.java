import ap26.*;
import static ap26.Color.*;
import java.util.*;
import java.util.concurrent.*;
import p26x42.OurBoard;

/**
 * 評価重みの最適化 (Phase 4a, 提出物に含めない)。
 *
 * 専用プレイヤーパッケージ {@code p26x42tune}（本番 p26x42 のコピーに重み注入と
 * 固定深さモードを足したもの）を使い、評価重みを座標降下で最適化する。
 *
 * - 重みは 6×6 の 8 対称性で 6 パラメータに圧縮。
 * - 適応度は「固定参照の重みセット群」に対する勝敗(と石差)。動く現行ではなく
 *   固定参照に対して測るのでスカラーになり、循環しない最急上昇になる。
 * - 局は標準盤 + 変形盤サンプル × 先後入替。コア数ぶん CPU 並列。審判は OurBoard。
 *
 * 実行: java -cp "bin:." Tuner42 [depth] [variantBoards] [seed]
 */
public class Tuner42 {

  static final int[] CAND = {0, 1, 2, 3, 4, 5, 6, 12, 18, 24, 30};
  static final int[] CLASS = buildClassMap();
  static final String[] CLASS_NAME = {"corner", "C", "edgeMid", "X", "innerEdge", "center"};

  // 固定参照の重みセット (これらに勝つことを目的にする)
  static final int[][] REFS = {
      {10, 10, 10, -5, 1, 1},      // 現行デフォルト
      {100, -20, 10, -40, 5, 5},   // 角重視・X/C嫌い
      {1, 1, 1, 1, 1, 1},          // フラット(石数貪欲的)
  };

  public static void main(String[] args) throws Exception {
    int depth = args.length > 0 ? Integer.parseInt(args[0]) : 6;
    int nVariant = args.length > 1 ? Integer.parseInt(args[1]) : 40;
    long seed = args.length > 2 ? Long.parseLong(args[2]) : 7L;

    List<int[]> boards = new ArrayList<>();
    boards.add(new int[] {});
    Random r = new Random(seed);
    for (int i = 0; i < nVariant; i++)
      boards.add(randomBlocks(r));

    int cores = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    ExecutorService pool = Executors.newFixedThreadPool(cores);
    System.err.printf("depth=%d boards=%d refs=%d games/eval=%d cores=%d%n",
        depth, boards.size(), REFS.length, boards.size() * 2 * REFS.length, cores);

    int[] incumbent = {10, 10, 10, -5, 1, 1};
    long fitInc = fitness(incumbent, boards, depth, pool);
    System.err.printf("seed %s  fit=%d%n", fmt(incumbent), fitInc);

    int[] steps = {16, 8, 4, 2, 1};
    for (int step : steps) {
      boolean improved = true;
      int guard = 0;
      while (improved && guard++ < 60) {
        improved = false;
        int[] bestCand = null;
        long bestFit = fitInc;
        int bi = -1, bdir = 0;
        for (int i = 0; i < 6; i++) {
          for (int dir = -1; dir <= 1; dir += 2) {
            int[] cand = incumbent.clone();
            cand[i] = clamp(cand[i] + dir * step);
            if (cand[i] == incumbent[i]) continue;
            long f = fitness(cand, boards, depth, pool);
            if (f > bestFit) {
              bestFit = f;
              bestCand = cand;
              bi = i;
              bdir = dir * step;
            }
          }
        }
        if (bestCand != null) {
          incumbent = bestCand;
          fitInc = bestFit;
          improved = true;
          System.err.printf("  step%2d  %-9s %+d -> %s  fit=%d%n",
              step, CLASS_NAME[bi], bdir, fmt(incumbent), fitInc);
        }
      }
      System.err.printf("[step %d done] %s  fit=%d%n", step, fmt(incumbent), fitInc);
    }
    pool.shutdown();

    System.err.println("==================== RESULT ====================");
    System.err.println("best 6-params : " + fmt(incumbent));
    for (int i = 0; i < 6; i++)
      System.err.printf("  %-9s = %d%n", CLASS_NAME[i], incumbent[i]);
    int[] w = expand(incumbent);
    StringBuilder sb = new StringBuilder("expanded 36 (paste into MyEval.DEFAULT_W):\n");
    for (int k = 0; k < 36; k++) {
      sb.append(String.format("%4d,", w[k]));
      if (k % 6 == 5) sb.append("\n");
    }
    System.err.println(sb);
    System.err.println("================================================");
  }

  /** 候補の適応度 = 全固定参照に対する (ネット勝敗*100000 + 石差合計) の総和。*/
  static long fitness(int[] cand, List<int[]> boards, int depth, ExecutorService pool) throws Exception {
    int[] wc = expand(cand);
    List<Callable<int[]>> tasks = new ArrayList<>();
    for (int[] ref : REFS) {
      int[] wr = expand(ref);
      for (int[] blocks : boards) {
        tasks.add(() -> {
          int diff = playGame(wc, wr, blocks, depth); // 候補=黒
          return new int[] {Integer.signum(diff), diff};
        });
        tasks.add(() -> {
          int diff = playGame(wr, wc, blocks, depth); // 候補=白
          int our = -diff;
          return new int[] {Integer.signum(our), our};
        });
      }
    }
    long net = 0, margin = 0;
    for (Future<int[]> f : pool.invokeAll(tasks)) {
      int[] x = f.get();
      net += x[0];
      margin += x[1];
    }
    return net * 100000L + margin;
  }

  static int playGame(int[] wBlack, int[] wWhite, int[] blocks, int depth) {
    OurBoard b = new OurBoard();
    for (int k : blocks) b.set(k, BLOCK);
    Player black = new p26x42tune.OurPlayer(BLACK, wBlack, depth);
    Player white = new p26x42tune.OurPlayer(WHITE, wWhite, depth);
    Board cur = b;
    while (!cur.isEnd()) {
      Color turn = cur.getTurn();
      Player p = (turn == BLACK) ? black : white;
      Move mv;
      try {
        mv = p.think(cur.clone()).colored(turn);
      } catch (Throwable e) {
        cur.foul(turn);
        break;
      }
      if (!cur.findLegalMoves(turn).contains(mv)) {
        cur.foul(turn);
        break;
      }
      cur = cur.placed(mv);
    }
    return cur.score();
  }

  static int[] expand(int[] p6) {
    int[] w = new int[36];
    for (int k = 0; k < 36; k++)
      w[k] = p6[CLASS[k]];
    return w;
  }

  static int[] buildClassMap() {
    int[][] pairs = {{0, 0}, {0, 1}, {0, 2}, {1, 1}, {1, 2}, {2, 2}};
    int[] map = new int[36];
    for (int k = 0; k < 36; k++) {
      int rr = Math.min(k / 6, 5 - k / 6), cc = Math.min(k % 6, 5 - k % 6);
      int a = Math.min(rr, cc), b = Math.max(rr, cc);
      for (int i = 0; i < pairs.length; i++)
        if (pairs[i][0] == a && pairs[i][1] == b) { map[k] = i; break; }
    }
    return map;
  }

  static int[] randomBlocks(Random r) {
    List<Integer> xs = new ArrayList<>();
    for (int c : CAND) xs.add(c);
    Collections.shuffle(xs, r);
    int n = r.nextInt(3) + 1;
    int[] out = new int[n];
    for (int i = 0; i < n; i++) out[i] = xs.get(i);
    return out;
  }

  static int clamp(int v) {
    return Math.max(-200, Math.min(200, v));
  }

  static String fmt(int[] p) {
    return Arrays.toString(p);
  }
}
