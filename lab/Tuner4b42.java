import ap26.*;
import static ap26.Color.*;
import java.util.*;
import java.util.concurrent.*;
import p26x42.OurBoard;

/**
 * 特徴係数の最適化 (Phase 4b, 提出物に含めない)。
 *
 * 位置重みは Phase 4a の最適値で固定し、特徴係数 [cMob, cFront, cStab] を座標降下で最適化する
 * (cPos は 10 に固定; 比はこの3つで決まる)。参照は「位置のみ (cMob=cFront=cStab=0)」=
 * Phase 4a 相当なので、勝ち越せれば「特徴追加が効いた」ことになる。
 * 局は標準盤+変形盤サンプル×先後、マルチコアCPU並列、固定深さ。審判は OurBoard。
 *
 * 実行: java -cp "bin:." Tuner4b42 [depth] [variantBoards] [seed]
 */
public class Tuner4b42 {

  static final int[] CAND = {0, 1, 2, 3, 4, 5, 6, 12, 18, 24, 30};

  // Phase 4a 最適化済みの位置重み (固定)
  static final int[] W_FIXED = {
      29, 10, 10, 10, 10, 29,
      10, -5, -3, -3, -5, 10,
      10, -3, 1, 1, -3, 10,
      10, -3, 1, 1, -3, 10,
      10, -5, -3, -3, -5, 10,
      29, 10, 10, 10, 10, 29,
  };
  static final int CPOS = 10;
  static final int[] REF = {CPOS, 0, 0, 0}; // 位置のみ (Phase 4a 相当)
  static final String[] NAME = {"cMob", "cFront", "cStab"};

  public static void main(String[] args) throws Exception {
    int depth = args.length > 0 ? Integer.parseInt(args[0]) : 6;
    int nVariant = args.length > 1 ? Integer.parseInt(args[1]) : 40;
    long seed = args.length > 2 ? Long.parseLong(args[2]) : 7L;

    List<int[]> boards = new ArrayList<>();
    boards.add(new int[] {});
    Random rnd = new Random(seed);
    for (int i = 0; i < nVariant; i++)
      boards.add(randomBlocks(rnd));

    int cores = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    ExecutorService pool = Executors.newFixedThreadPool(cores);
    System.err.printf("depth=%d boards=%d games/eval=%d cores=%d (cPos=%d fixed)%n",
        depth, boards.size(), boards.size() * 2, cores, CPOS);

    int[] p = {0, 0, 0}; // [cMob,cFront,cStab] seed = 位置のみ
    long fit = fitness(p, boards, depth, pool);
    System.err.printf("seed %s fit=%d%n", Arrays.toString(p), fit);

    int[] steps = {16, 8, 4, 2, 1};
    for (int step : steps) {
      boolean improved = true;
      int guard = 0;
      while (improved && guard++ < 60) {
        improved = false;
        int[] best = null;
        long bestFit = fit;
        int bi = 0, bd = 0;
        for (int i = 0; i < 3; i++) {
          for (int dir = -1; dir <= 1; dir += 2) {
            int[] cand = p.clone();
            cand[i] = clamp(cand[i] + dir * step);
            if (cand[i] == p[i]) continue;
            long f = fitness(cand, boards, depth, pool);
            if (f > bestFit) { bestFit = f; best = cand; bi = i; bd = dir * step; }
          }
        }
        if (best != null) {
          p = best; fit = bestFit; improved = true;
          System.err.printf("  step%2d %-7s %+d -> %s fit=%d%n", step, NAME[bi], bd, Arrays.toString(p), fit);
        }
      }
      System.err.printf("[step %d] %s fit=%d%n", step, Arrays.toString(p), fit);
    }
    pool.shutdown();

    System.err.println("==================== RESULT (Phase 4b) ====================");
    System.err.printf("cPos(fixed)=%d  cMob=%d  cFront=%d  cStab=%d%n", CPOS, p[0], p[1], p[2]);
    System.err.println("coeffs for MyEval: {" + CPOS + ", " + p[0] + ", " + p[1] + ", " + p[2] + "}");
    System.err.println("===========================================================");
  }

  static long fitness(int[] coeffs3, List<int[]> boards, int depth, ExecutorService pool) throws Exception {
    int[] cand = {CPOS, coeffs3[0], coeffs3[1], coeffs3[2]};
    List<Callable<int[]>> tasks = new ArrayList<>();
    for (int[] blocks : boards) {
      tasks.add(() -> { int d = playGame(cand, REF, blocks, depth); return new int[] {Integer.signum(d), d}; });
      tasks.add(() -> { int d = playGame(REF, cand, blocks, depth); int our = -d; return new int[] {Integer.signum(our), our}; });
    }
    long net = 0, margin = 0;
    for (Future<int[]> f : pool.invokeAll(tasks)) { int[] x = f.get(); net += x[0]; margin += x[1]; }
    return net * 100000L + margin;
  }

  static int playGame(int[] cBlack, int[] cWhite, int[] blocks, int depth) {
    OurBoard b = new OurBoard();
    for (int k : blocks) b.set(k, BLOCK);
    Player black = new p26x42tune.OurPlayer(BLACK, W_FIXED, cBlack, depth);
    Player white = new p26x42tune.OurPlayer(WHITE, W_FIXED, cWhite, depth);
    Board cur = b;
    while (!cur.isEnd()) {
      Color turn = cur.getTurn();
      Player pl = (turn == BLACK) ? black : white;
      Move mv;
      try {
        mv = pl.think(cur.clone()).colored(turn);
      } catch (Throwable e) { cur.foul(turn); break; }
      if (!cur.findLegalMoves(turn).contains(mv)) { cur.foul(turn); break; }
      cur = cur.placed(mv);
    }
    return cur.score();
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

  static int clamp(int v) { return Math.max(-200, Math.min(200, v)); }
}
