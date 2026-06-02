import ap26.*;
import static ap26.Color.*;
import java.util.*;
import java.util.concurrent.*;
import p26x42.OurBoard;

/**
 * Phase F: 擬似角対応(トポロジ種類重み)の最適化と「BLOCK がどう作用するか」の計測
 * (提出物に含めない実験用)。
 *
 * - 取り得る変形盤を全列挙 (候補11マスから1〜3個 = 231通り) + 標準盤。
 * - トポロジ種類重み [corner,C,edge,X,interior] を座標降下で最適化。
 * - 参照は現行の固定重み eval (Phase 4a/4b の W)。特徴係数は両者とも 4b 値で固定し、
 *   「位置のトポロジ化」の効果だけを隔離して測る。
 * - 最後に BLOCK 個数別 (0/1/2/3) に topo vs fixed の勝率・石差を出し、BLOCK の効き方を見る。
 *
 * 実行: java -cp "bin:." TunerF42 [depth]
 */
public class TunerF42 {

  static final int[] CAND = {0, 1, 2, 3, 4, 5, 6, 12, 18, 24, 30};

  // 現行固定重み (Phase 4a) と特徴係数 (Phase 4b)
  static final int[] W_FIXED = {
      29, 10, 10, 10, 10, 29,
      10, -5, -3, -3, -5, 10,
      10, -3, 1, 1, -3, 10,
      10, -3, 1, 1, -3, 10,
      10, -5, -3, -3, -5, 10,
      29, 10, 10, 10, 10, 29,
  };
  static final int[] COEFFS = {10, 31, -20, 20};
  static final String[] TYPE_NAME = {"corner", "C", "edge", "X", "interior"};

  static List<int[]> ALL;   // 全盤面 (block index 配列)
  static List<Integer> NB;  // 各盤面の block 個数

  public static void main(String[] args) throws Exception {
    int depth = args.length > 0 ? Integer.parseInt(args[0]) : 6;

    ALL = new ArrayList<>();
    NB = new ArrayList<>();
    ALL.add(new int[] {}); NB.add(0); // 標準
    for (int r = 1; r <= 3; r++)
      combos(CAND, r, 0, new int[r], 0);

    int cores = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    ExecutorService pool = Executors.newFixedThreadPool(cores);
    System.err.printf("depth=%d boards=%d (standard + 231 variant) games/eval=%d cores=%d%n",
        depth, ALL.size(), ALL.size() * 2, cores);

    // 種類重み seed (Phase 4a を種類にマップ: corner29,C10,edge10,X-5,interior1)
    int[] tw = {29, 10, 10, -5, 1};
    long fit = fitness(tw, depth, pool);
    System.err.printf("seed %s fit=%d%n", Arrays.toString(tw), fit);

    int[] steps = {16, 8, 4, 2, 1};
    for (int step : steps) {
      boolean improved = true;
      int guard = 0;
      while (improved && guard++ < 80) {
        improved = false;
        int[] best = null; long bestFit = fit; int bi = 0, bd = 0;
        for (int i = 0; i < p26x42tune.Topo.NTYPES; i++) {
          for (int dir = -1; dir <= 1; dir += 2) {
            int[] cand = tw.clone();
            cand[i] = clamp(cand[i] + dir * step);
            if (cand[i] == tw[i]) continue;
            long f = fitness(cand, depth, pool);
            if (f > bestFit) { bestFit = f; best = cand; bi = i; bd = dir * step; }
          }
        }
        if (best != null) {
          tw = best; fit = bestFit; improved = true;
          System.err.printf("  step%2d %-9s %+d -> %s fit=%d%n", step, TYPE_NAME[bi], bd, Arrays.toString(tw), fit);
        }
      }
      System.err.printf("[step %d] %s fit=%d%n", step, Arrays.toString(tw), fit);
    }

    System.err.println("==================== RESULT (Phase F) ====================");
    for (int i = 0; i < 5; i++)
      System.err.printf("  %-9s = %d%n", TYPE_NAME[i], tw[i]);

    // BLOCK 個数別に topo(最適種類重み) vs fixed を計測
    System.err.println("--- topo vs fixed, by #blocks (how BLOCK acts) ---");
    for (int nb = 0; nb <= 3; nb++)
      report(tw, nb, depth, pool);

    pool.shutdown();
    System.err.println("=========================================================");
  }

  /**
   * topo(typeW) vs fixed。本番の生成は「BLOCK個数が一様(1〜3)」なので、
   * 個数グループ(0,1,2,3)を均等重みで合算する (レイアウト数で重み付けしない)。
   * fit = Σ_group [ (netRate)*100000 + (avgMargin)*100 ]。
   */
  static long fitness(int[] typeW, int depth, ExecutorService pool) throws Exception {
    // group -> [netTasks...]; ここでは全局を投げてから group 集計
    List<int[]> meta = new ArrayList<>();   // [groupNB]
    List<Callable<int[]>> tasks = new ArrayList<>();
    for (int gi = 0; gi < ALL.size(); gi++) {
      int[] blocks = ALL.get(gi);
      int nb = NB.get(gi);
      meta.add(new int[] {nb});
      tasks.add(() -> { int d = play(typeW, true, false, blocks, depth); return new int[] {Integer.signum(d), d}; });
      meta.add(new int[] {nb});
      tasks.add(() -> { int d = play(typeW, false, true, blocks, depth); int o = -d; return new int[] {Integer.signum(o), o}; });
    }
    int[] gNet = new int[4], gMar = new int[4], gCnt = new int[4];
    List<Future<int[]>> fs = pool.invokeAll(tasks);
    for (int i = 0; i < fs.size(); i++) {
      int nb = meta.get(i)[0];
      int[] x = fs.get(i).get();
      gNet[nb] += x[0]; gMar[nb] += x[1]; gCnt[nb]++;
    }
    double fit = 0;
    for (int nb = 0; nb <= 3; nb++) {
      if (gCnt[nb] == 0) continue;
      fit += ((double) gNet[nb] / gCnt[nb]) * 100000.0 + ((double) gMar[nb] / gCnt[nb]) * 100.0;
    }
    return Math.round(fit);
  }

  static void report(int[] typeW, int nblocks, int depth, ExecutorService pool) throws Exception {
    List<Callable<int[]>> tasks = new ArrayList<>();
    for (int i = 0; i < ALL.size(); i++) {
      if (NB.get(i) != nblocks) continue;
      int[] blocks = ALL.get(i);
      tasks.add(() -> { int d = play(typeW, true, false, blocks, depth); return new int[] {Integer.signum(d), d}; });
      tasks.add(() -> { int d = play(typeW, false, true, blocks, depth); int o = -d; return new int[] {Integer.signum(o), o}; });
    }
    int w = 0, l = 0, dr = 0, m = 0, g = 0;
    for (Future<int[]> f : pool.invokeAll(tasks)) {
      int[] x = f.get();
      if (x[0] > 0) w++; else if (x[0] < 0) l++; else dr++;
      m += x[1]; g++;
    }
    System.err.printf("  #blocks=%d  games=%d  topo W %d L %d D %d  win %5.1f%%  margin %+.2f%n",
        nblocks, g, w, l, dr, g == 0 ? 0 : 100.0 * w / g, g == 0 ? 0 : (double) m / g);
  }

  /** topoBlack/topoWhite のどちらが topo(種類重み)か指定して 1 局。戻り値 = 黒石-白石。*/
  static int play(int[] typeW, boolean topoBlack, boolean topoWhite, int[] blocks, int depth) {
    OurBoard b = new OurBoard();
    for (int k : blocks) b.set(k, BLOCK);
    Player black = topoBlack ? new p26x42tune.OurPlayer(BLACK, typeW, COEFFS, depth, true)
                             : new p26x42tune.OurPlayer(BLACK, W_FIXED, COEFFS, depth);
    Player white = topoWhite ? new p26x42tune.OurPlayer(WHITE, typeW, COEFFS, depth, true)
                             : new p26x42tune.OurPlayer(WHITE, W_FIXED, COEFFS, depth);
    black.setBoard(b.clone());
    white.setBoard(b.clone());
    Board cur = b;
    while (!cur.isEnd()) {
      Color turn = cur.getTurn();
      Player p = (turn == BLACK) ? black : white;
      Move mv;
      try { mv = p.think(cur.clone()).colored(turn); } catch (Throwable e) { cur.foul(turn); break; }
      if (!cur.findLegalMoves(turn).contains(mv)) { cur.foul(turn); break; }
      cur = cur.placed(mv);
    }
    return cur.score();
  }

  static void combos(int[] arr, int r, int start, int[] cur, int depthIdx) {
    if (depthIdx == r) { ALL.add(cur.clone()); NB.add(r); return; }
    for (int i = start; i <= arr.length - (r - depthIdx); i++) {
      cur[depthIdx] = arr[i];
      combos(arr, r, i + 1, cur, depthIdx + 1);
    }
  }

  static int clamp(int v) { return Math.max(-200, Math.min(200, v)); }
}
