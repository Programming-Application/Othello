import ap26.*;
import static ap26.Color.*;
import java.util.*;
import java.util.concurrent.*;
import p26x42.OurBoard;

/**
 * Phase F: 代表レイアウトでの「厚い個別最適」の吟味 (提出物に含めない)。
 *
 * 各個数(1,2,3)の代表レイアウトについて:
 *   - 厚い学習初手(64) で個別最適重みを座標降下 (頑健)
 *   - 薄い学習初手(16) で個別最適重みを座標降下 (前回相当 / 過適合の確認用)
 *   - 汎用topo / 固定 はそのまま
 * を、<b>学習に使っていない検証初手(64, 別シード)</b> で比較する:
 *   1) 各重みの「固定重みに対する勝率 (held-out)」ladder
 *   2) 厚い個別最適 vs 汎用topo / 薄い個別最適 の直接対決 (held-out)
 *
 * 実行: java -cp "bin:." ThickF42 [depth]
 */
public class ThickF42 {

  static final int[] W_FIXED = {
      29, 10, 10, 10, 10, 29,
      10, -5, -3, -3, -5, 10,
      10, -3, 1, 1, -3, 10,
      10, -3, 1, 1, -3, 10,
      10, -5, -3, -3, -5, 10,
      29, 10, 10, 10, 10, 29,
  };
  static final int[] COEFFS = {10, 31, -20, 20};
  static final int[] GENERIC_TW = {23, 2, 8, 1, 1};

  static final int R = 4;          // ランダム初手手数
  static final int TRAIN_HEAVY = 64;
  static final int TRAIN_LIGHT = 16;
  static final int TEST = 64;

  static int DEPTH;
  static ExecutorService POOL;

  public static void main(String[] args) throws Exception {
    DEPTH = args.length > 0 ? Integer.parseInt(args[0]) : 6;
    int cores = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    POOL = Executors.newFixedThreadPool(cores);

    int[][] examples = {
        {2},          // nb=1: c1
        {0, 5},       // nb=2: a1,f1
        {0, 12, 24},  // nb=3: a1,a3,a5
    };

    System.err.printf("depth=%d  trainHeavy=%d trainLight=%d test=%d (held-out)%n",
        DEPTH, TRAIN_HEAVY, TRAIN_LIGHT, TEST);

    for (int[] blocks : examples) {
      int nb = blocks.length;
      // 学習/検証で初手シード空間を分離 (train: seed 0.., test: seed 100000..)
      var trainHeavy = makeOpenings(blocks, TRAIN_HEAVY, 0);
      var trainLight = makeOpenings(blocks, TRAIN_LIGHT, 0);     // train集合の先頭16 (薄い)
      var test = makeOpenings(blocks, TEST, 100000);

      int[] thick = optimize(blocks, trainHeavy);
      int[] light = optimize(blocks, trainLight);

      // held-out 勝率 vs 固定
      double wrTopo = wrVsFixed(GENERIC_TW, blocks, test);
      double wrLight = wrVsFixed(light, blocks, test);
      double wrThick = wrVsFixed(thick, blocks, test);

      // held-out 直接対決 (厚い個別最適 視点)
      int[] hThVsTopo = head(thick, GENERIC_TW, blocks, test);
      int[] hThVsLight = head(thick, light, blocks, test);

      System.err.println("================ layout nb=" + nb + " blocks=" + Arrays.toString(blocks) + " ================");
      System.err.println("  weights: thick=" + Arrays.toString(thick) + "  light=" + Arrays.toString(light)
          + "  generic=" + Arrays.toString(GENERIC_TW));
      System.err.println("  [held-out] win-rate vs FIXED:");
      System.err.printf("    topo(generic) = %5.1f%%%n", wrTopo);
      System.err.printf("    light-optimal = %5.1f%%%n", wrLight);
      System.err.printf("    thick-optimal = %5.1f%%%n", wrThick);
      System.err.println("  [held-out] head-to-head (thick perspective):");
      System.err.printf("    thick vs topo  : W%d L%d  %5.1f%%  margin %+.1f%n",
          hThVsTopo[0], hThVsTopo[1], 100.0 * hThVsTopo[0] / hThVsTopo[3], (double) hThVsTopo[2] / hThVsTopo[3]);
      System.err.printf("    thick vs light : W%d L%d  %5.1f%%  margin %+.1f%n",
          hThVsLight[0], hThVsLight[1], 100.0 * hThVsLight[0] / hThVsLight[3], (double) hThVsLight[2] / hThVsLight[3]);
    }
    POOL.shutdown();
    System.err.println("=========================================================");
  }

  /** topo(typeW) を固定重み相手に train初手で座標降下して最適 typeW を返す。*/
  static int[] optimize(int[] blocks, List<List<Move>> train) throws Exception {
    int[] tw = GENERIC_TW.clone();
    long best = fit(head(tw, null, blocks, train)); // null = 相手は固定
    int[] steps = {16, 8, 4, 2};
    for (int step : steps) {
      boolean imp = true; int guard = 0;
      while (imp && guard++ < 40) {
        imp = false;
        for (int i = 0; i < 5; i++) {
          for (int dir = -1; dir <= 1; dir += 2) {
            int[] c = tw.clone();
            c[i] = clamp(c[i] + dir * step);
            if (c[i] == tw[i]) continue;
            long f = fit(head(c, null, blocks, train));
            if (f > best) { best = f; tw = c; imp = true; }
          }
        }
      }
    }
    return tw;
  }

  static long fit(int[] r) { return (r[0] - r[1]) * 100000L + r[2]; }

  static double wrVsFixed(int[] tw, int[] blocks, List<List<Move>> ops) throws Exception {
    int[] r = head(tw, null, blocks, ops);
    return 100.0 * r[0] / r[3];
  }

  /**
   * A=topo(twA) vs B を初手集合で先後ペア対戦。B は twB!=null なら topo(twB)、null なら固定重み。
   * 戻り {winsA, lossesA, marginA, games}。
   */
  static int[] head(int[] twA, int[] twB, int[] blocks, List<List<Move>> ops) throws Exception {
    List<Callable<int[]>> tasks = new ArrayList<>();
    for (List<Move> op : ops) {
      tasks.add(() -> { int s = play(blocks, op, mk(twA, BLACK), mkB(twB, WHITE)); return new int[] {Integer.signum(s), s}; });
      tasks.add(() -> { int s = play(blocks, op, mkB(twB, BLACK), mk(twA, WHITE)); int o = -s; return new int[] {Integer.signum(o), o}; });
    }
    int w = 0, l = 0, m = 0, g = 0;
    for (Future<int[]> f : POOL.invokeAll(tasks)) {
      int[] x = f.get();
      if (x[0] > 0) w++; else if (x[0] < 0) l++;
      m += x[1]; g++;
    }
    return new int[] {w, l, m, g};
  }

  static Player mk(int[] tw, Color c) { return new p26x42tune.OurPlayer(c, tw, COEFFS, DEPTH, true); }
  static Player mkB(int[] twB, Color c) {
    return twB == null ? new p26x42tune.OurPlayer(c, W_FIXED, COEFFS, DEPTH) : mk(twB, c);
  }

  static int play(int[] blocks, List<Move> opening, Player black, Player white) {
    OurBoard b = new OurBoard();
    for (int k : blocks) b.set(k, BLOCK);
    black.setBoard(b.clone());
    white.setBoard(b.clone());
    Board cur = b;
    for (Move om : opening) cur = cur.placed(om);
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

  static List<List<Move>> makeOpenings(int[] blocks, int count, int seedBase) {
    List<List<Move>> ops = new ArrayList<>();
    for (int s = 0; s < count; s++) {
      Random rng = new Random(seedBase + 7919L * (s + 1) + sum(blocks));
      OurBoard b = new OurBoard();
      for (int k : blocks) b.set(k, BLOCK);
      List<Move> seq = new ArrayList<>();
      Board cur = b;
      for (int r = 0; r < R && !cur.isEnd(); r++) {
        Color turn = cur.getTurn();
        var legal = cur.findLegalMoves(turn);
        Move mv = legal.get(rng.nextInt(legal.size()));
        seq.add(mv);
        cur = cur.placed(mv);
      }
      ops.add(seq);
    }
    return ops;
  }

  static int sum(int[] a) { int s = 0; for (int x : a) s += x; return s; }
  static int clamp(int v) { return Math.max(-200, Math.min(200, v)); }
}
