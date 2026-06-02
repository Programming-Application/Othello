import ap26.*;
import static ap26.Color.*;
import java.util.*;
import java.util.concurrent.*;
import p26x42.OurBoard;

/**
 * F2 公平比較: 係数を phase 別にすると単一係数より強くなるか (提出物に含めない)。
 *
 * 位置重みは固定(Phase4a)で共通。係数 [cPos,cMob,cFront,cStab] を 3 phase 化し、
 * TRAIN で単一係数(現行4b)相手に座標降下 → held-out(別レイアウト×別初手) で phased vs single を比較。
 * hint の「phase別は必須」を鵜呑みにせず、実測で採否を判断する。
 *
 * 実行: java -cp "bin:." CompareF2 [depth] [trainPerCount] [testPerCount] [Mtrain] [Mtest]
 */
public class CompareF2 {

  static final int[] CAND = {0, 1, 2, 3, 4, 5, 6, 12, 18, 24, 30};
  static final int[] W_FIXED = {
      29, 10, 10, 10, 10, 29,
      10, -5, -3, -3, -5, 10,
      10, -3, 1, 1, -3, 10,
      10, -3, 1, 1, -3, 10,
      10, -5, -3, -3, -5, 10,
      29, 10, 10, 10, 10, 29,
  };
  static final int[] SINGLE = {10, 31, -20, 20}; // 現行4b 単一係数
  static final int[] BOUNDS = {22, 14};           // phase0:>=22, phase1:14-21, phase2:<14
  static final int R = 4;

  static int DEPTH;
  static ExecutorService POOL;

  static class Data { List<int[]> blocks = new ArrayList<>(); List<List<List<Move>>> ops = new ArrayList<>(); List<Integer> nb = new ArrayList<>(); }

  public static void main(String[] args) throws Exception {
    DEPTH = args.length > 0 ? Integer.parseInt(args[0]) : 6;
    int trainPer = args.length > 1 ? Integer.parseInt(args[1]) : 3;
    int testPer = args.length > 2 ? Integer.parseInt(args[2]) : 5;
    int Mtr = args.length > 3 ? Integer.parseInt(args[3]) : 8;
    int Mte = args.length > 4 ? Integer.parseInt(args[4]) : 12;
    int cores = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    POOL = Executors.newFixedThreadPool(cores);

    Set<String> used = new HashSet<>();
    Data train = build(trainPer, Mtr, new Random(11), 0, used);
    Data test = build(testPer, Mte, new Random(22), 500000, used);
    System.err.printf("depth=%d train(boards=%d,M=%d) test(boards=%d,M=%d, held-out) phases=3 bounds=%s%n",
        DEPTH, train.blocks.size(), Mtr, test.blocks.size(), Mte, Arrays.toString(BOUNDS));

    // phased 係数 (3x4) を単一係数相手に TRAIN で座標降下。seed=全phase単一 → seed時点でsingle同等
    int[] p = new int[12];
    for (int ph = 0; ph < 3; ph++) for (int j = 0; j < 4; j++) p[ph * 4 + j] = SINGLE[j];
    long best = fitCU(matchByBlock(p, train));
    int[] steps = {16, 8, 4};
    for (int step : steps) {
      boolean imp = true; int guard = 0;
      while (imp && guard++ < 40) {
        imp = false;
        for (int i = 0; i < 12; i++) {
          for (int dir = -1; dir <= 1; dir += 2) {
            int[] c = p.clone();
            c[i] = clamp(c[i] + dir * step);
            if (c[i] == p[i]) continue;
            long f = fitCU(matchByBlock(c, train));
            if (f > best) { best = f; p = c; imp = true; }
          }
        }
      }
    }
    System.err.println("learned phased coeffs:");
    for (int ph = 0; ph < 3; ph++)
      System.err.printf("  phase%d (%s) = %s%n", ph,
          ph == 0 ? "empties>=22" : ph == 1 ? "14-21" : "<14",
          Arrays.toString(Arrays.copyOfRange(p, ph * 4, ph * 4 + 4)));

    int[][] held = matchByBlock(p, test);
    System.err.println("==================== HELD-OUT: phased vs single ====================");
    printByBlock("phased vs single", held);
    POOL.shutdown();
    System.err.println("====================================================================");
  }

  static void printByBlock(String label, int[][] agg) {
    int W = 0, L = 0, M = 0, G = 0;
    StringBuilder sb = new StringBuilder("  " + label + " | ");
    for (int nb = 0; nb <= 3; nb++) {
      int w = agg[nb][0], l = agg[nb][1], m = agg[nb][2], g = agg[nb][3];
      if (g == 0) continue;
      sb.append(String.format("nb%d:%5.1f%% ", nb, 100.0 * w / g));
      W += w; L += l; M += m; G += g;
    }
    sb.append(String.format("| ALL %5.1f%%  margin %+.2f", 100.0 * W / G, (double) M / G));
    System.err.println(sb);
  }

  static long fitCU(int[][] agg) {
    double f = 0;
    for (int nb = 0; nb <= 3; nb++) {
      int g = agg[nb][3];
      if (g == 0) continue;
      f += ((double) (agg[nb][0] - agg[nb][1]) / g) * 100000.0 + ((double) agg[nb][2] / g) * 100.0;
    }
    return Math.round(f);
  }

  /** phased(p) vs single を data 全盤面×先後。agg[nb]={winsPhased,losses,margin,games}。*/
  static int[][] matchByBlock(int[] p, Data data) throws Exception {
    int[][] cb = {{p[0], p[1], p[2], p[3]}, {p[4], p[5], p[6], p[7]}, {p[8], p[9], p[10], p[11]}};
    List<Callable<int[]>> tasks = new ArrayList<>();
    List<Integer> tag = new ArrayList<>();
    for (int i = 0; i < data.blocks.size(); i++) {
      int[] blocks = data.blocks.get(i);
      int nb = data.nb.get(i);
      for (List<Move> op : data.ops.get(i)) {
        tag.add(nb);
        tasks.add(() -> { int s = play(blocks, op, mkPhased(cb, BLACK), mkSingle(WHITE)); return new int[] {Integer.signum(s), s}; });
        tag.add(nb);
        tasks.add(() -> { int s = play(blocks, op, mkSingle(BLACK), mkPhased(cb, WHITE)); int o = -s; return new int[] {Integer.signum(o), o}; });
      }
    }
    int[][] agg = new int[4][4];
    List<Future<int[]>> fs = POOL.invokeAll(tasks);
    for (int i = 0; i < fs.size(); i++) {
      int nb = tag.get(i);
      int[] x = fs.get(i).get();
      if (x[0] > 0) agg[nb][0]++; else if (x[0] < 0) agg[nb][1]++;
      agg[nb][2] += x[1]; agg[nb][3]++;
    }
    return agg;
  }

  static Player mkPhased(int[][] cb, Color c) { return new p26x42tune.OurPlayer(c, W_FIXED, cb, BOUNDS, DEPTH); }
  static Player mkSingle(Color c) { return new p26x42tune.OurPlayer(c, W_FIXED, SINGLE, DEPTH); }

  static int play(int[] blocks, List<Move> opening, Player black, Player white) {
    OurBoard b = new OurBoard();
    for (int k : blocks) b.set(k, BLOCK);
    black.setBoard(b.clone());
    white.setBoard(b.clone());
    Board cur = b;
    for (Move om : opening) cur = cur.placed(om);
    while (!cur.isEnd()) {
      Color turn = cur.getTurn();
      Player pl = (turn == BLACK) ? black : white;
      Move mv;
      try { mv = pl.think(cur.clone()).colored(turn); } catch (Throwable e) { cur.foul(turn); break; }
      if (!cur.findLegalMoves(turn).contains(mv)) { cur.foul(turn); break; }
      cur = cur.placed(mv);
    }
    return cur.score();
  }

  static Data build(int perCount, int M, Random r, int opSeedBase, Set<String> used) {
    Data d = new Data();
    addBoard(d, new int[] {}, M, opSeedBase);
    for (int c = 1; c <= 3; c++) {
      int got = 0, tries = 0;
      while (got < perCount && tries++ < 1000) {
        List<Integer> xs = new ArrayList<>();
        for (int x : CAND) xs.add(x);
        Collections.shuffle(xs, r);
        int[] L = new int[c];
        for (int i = 0; i < c; i++) L[i] = xs.get(i);
        Arrays.sort(L);
        if (!used.add(Arrays.toString(L))) continue;
        addBoard(d, L, M, opSeedBase);
        got++;
      }
    }
    return d;
  }

  static void addBoard(Data d, int[] blocks, int M, int opSeedBase) {
    List<List<Move>> ops = new ArrayList<>();
    for (int s = 0; s < M; s++) {
      Random rng = new Random(opSeedBase + 7919L * (s + 1) + 31L * sum(blocks) + blocks.length);
      OurBoard b = new OurBoard();
      for (int k : blocks) b.set(k, BLOCK);
      List<Move> seq = new ArrayList<>();
      Board cur = b;
      for (int rr = 0; rr < R && !cur.isEnd(); rr++) {
        Color turn = cur.getTurn();
        var legal = cur.findLegalMoves(turn);
        Move mv = legal.get(rng.nextInt(legal.size()));
        seq.add(mv); cur = cur.placed(mv);
      }
      ops.add(seq);
    }
    d.blocks.add(blocks); d.ops.add(ops); d.nb.add(blocks.length);
  }

  static int sum(int[] a) { int s = 0; for (int x : a) s += x; return s; }
  static int clamp(int v) { return Math.max(-200, Math.min(200, v)); }
}
