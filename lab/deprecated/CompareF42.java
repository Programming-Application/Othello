import ap26.*;
import static ap26.Color.*;
import java.util.*;
import java.util.concurrent.*;
import p26x42.OurBoard;

/**
 * Phase F-proper の公平比較 (提出物に含めない実験用)。
 *
 * 結論を先取りせず、固定重み / 薄いtopo(種類5) / richトポロジ(特徴NF) を公平に比較する。
 *  - TRAIN 盤面(個数一様サンプル)+ランダム初手 で thin と rich の汎用重みを各々学習。
 *  - TEST 盤面(TRAINと別レイアウト)+ランダム初手(別シード) = held-out で評価:
 *      rich vs fixed / thin vs fixed / rich vs thin  を BLOCK 個数別に。
 * いずれも「1セットの汎用重みを未知レイアウトに適用」する汎化テスト (個別表ではない)。
 *
 * 実行: java -cp "bin:." CompareF42 [depth] [trainPerCount] [testPerCount] [Mtrain] [Mtest]
 */
public class CompareF42 {

  static final int[] CAND = {0, 1, 2, 3, 4, 5, 6, 12, 18, 24, 30};
  static final int[] W_FIXED = {
      29, 10, 10, 10, 10, 29,
      10, -5, -3, -3, -5, 10,
      10, -3, 1, 1, -3, 10,
      10, -3, 1, 1, -3, 10,
      10, -5, -3, -3, -5, 10,
      29, 10, 10, 10, 10, 29,
  };
  static final int[] COEFFS = {10, 31, -20, 20};
  static final int FIXED = 0, THIN = 1, RICH = 2;
  static final int R = 4;

  static int DEPTH;
  static ExecutorService POOL;

  // 盤面集合
  static class Data {
    List<int[]> blocks = new ArrayList<>();
    List<List<List<Move>>> ops = new ArrayList<>();
    List<Integer> nb = new ArrayList<>();
  }

  public static void main(String[] args) throws Exception {
    DEPTH = args.length > 0 ? Integer.parseInt(args[0]) : 6;
    int trainPer = args.length > 1 ? Integer.parseInt(args[1]) : 4;
    int testPer = args.length > 2 ? Integer.parseInt(args[2]) : 6;
    int Mtr = args.length > 3 ? Integer.parseInt(args[3]) : 6;
    int Mte = args.length > 4 ? Integer.parseInt(args[4]) : 10;
    int cores = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    POOL = Executors.newFixedThreadPool(cores);

    Set<String> used = new HashSet<>();
    Data train = build(trainPer, Mtr, new Random(11), 0, used);
    Data test = build(testPer, Mte, new Random(22), 500000, used); // 別レイアウト・別初手

    System.err.printf("depth=%d train(boards=%d,M=%d) test(boards=%d,M=%d, held-out) cores=%d%n",
        DEPTH, train.blocks.size(), Mtr, test.blocks.size(), Mte, cores);

    // thin(5) と rich(NF) の汎用重みを TRAIN で学習 (vs 固定, 個数一様)
    int[] thinSeed = {23, 2, 8, 1, 1};
    int[] richSeed = {23, 2, 8, 1, 1, 0, 0}; // 先頭5=thin相当, 追加2(adjBlock,openRay)=0 → seed時点でthin同等
    int[] thinW = optimize(THIN, thinSeed, train);
    int[] richW = optimize(RICH, richSeed, train);
    System.err.println("learned thin = " + Arrays.toString(thinW));
    System.err.println("learned rich = " + Arrays.toString(richW));

    // held-out 評価
    System.err.println("==================== HELD-OUT (TEST) ====================");
    System.err.println("[vs FIXED] win-rate by #blocks");
    int[][] thinVsFix = matchByBlock(THIN, thinW, FIXED, null, test);
    int[][] richVsFix = matchByBlock(RICH, richW, FIXED, null, test);
    printByBlock("thin vs fixed", thinVsFix);
    printByBlock("rich vs fixed", richVsFix);
    System.err.println("[head-to-head] rich vs thin");
    int[][] richVsThin = matchByBlock(RICH, richW, THIN, thinW, test);
    printByBlock("rich vs thin ", richVsThin);

    POOL.shutdown();
    System.err.println("=========================================================");
  }

  static void printByBlock(String label, int[][] agg) {
    int W = 0, L = 0, M = 0, G = 0;
    StringBuilder sb = new StringBuilder("  " + label + " | ");
    for (int nb = 0; nb <= 3; nb++) {
      int w = agg[nb][0], l = agg[nb][1], m = agg[nb][2], g = agg[nb][3];
      if (g == 0) { sb.append(String.format("nb%d: -    ", nb)); continue; }
      sb.append(String.format("nb%d:%5.1f%%(m%+.1f) ", nb, 100.0 * w / g, (double) m / g));
      W += w; L += l; M += m; G += g;
    }
    sb.append(String.format("| ALL %5.1f%% m%+.2f", 100.0 * W / G, (double) M / G));
    System.err.println(sb);
  }

  /** kind(seed) を固定重み相手に TRAIN で座標降下 (個数一様の適応度)。*/
  static int[] optimize(int kind, int[] seed, Data train) throws Exception {
    int[] w = seed.clone();
    long best = fitCU(matchByBlock(kind, w, FIXED, null, train));
    int[] steps = {16, 8, 4, 2};
    for (int step : steps) {
      boolean imp = true; int guard = 0;
      while (imp && guard++ < 50) {
        imp = false;
        for (int i = 0; i < w.length; i++) {
          for (int dir = -1; dir <= 1; dir += 2) {
            int[] c = w.clone();
            c[i] = clamp(c[i] + dir * step);
            if (c[i] == w[i]) continue;
            long f = fitCU(matchByBlock(kind, c, FIXED, null, train));
            if (f > best) { best = f; w = c; imp = true; }
          }
        }
      }
    }
    return w;
  }

  /** 個数一様の適応度 (各nb群を均等重み)。*/
  static long fitCU(int[][] agg) {
    double f = 0;
    for (int nb = 0; nb <= 3; nb++) {
      int g = agg[nb][3];
      if (g == 0) continue;
      f += ((double) (agg[nb][0] - agg[nb][1]) / g) * 100000.0 + ((double) agg[nb][2] / g) * 100.0;
    }
    return Math.round(f);
  }

  /** A(kindA,wA) vs B(kindB,wB) を data 全盤面×先後で。戻り agg[nb] = {winsA,lossesA,margin,games}。*/
  static int[][] matchByBlock(int kindA, int[] wA, int kindB, int[] wB, Data data) throws Exception {
    List<Callable<int[]>> tasks = new ArrayList<>();
    List<Integer> tag = new ArrayList<>();
    for (int i = 0; i < data.blocks.size(); i++) {
      int[] blocks = data.blocks.get(i);
      int nb = data.nb.get(i);
      for (List<Move> op : data.ops.get(i)) {
        tag.add(nb);
        tasks.add(() -> { int s = play(blocks, op, mk(kindA, wA, BLACK), mk(kindB, wB, WHITE)); return new int[] {Integer.signum(s), s}; });
        tag.add(nb);
        tasks.add(() -> { int s = play(blocks, op, mk(kindB, wB, BLACK), mk(kindA, wA, WHITE)); int o = -s; return new int[] {Integer.signum(o), o}; });
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

  static Player mk(int kind, int[] w, Color c) {
    if (kind == FIXED) return new p26x42tune.OurPlayer(c, W_FIXED, COEFFS, DEPTH);
    if (kind == THIN) return new p26x42tune.OurPlayer(c, w, COEFFS, DEPTH, true);
    return new p26x42tune.OurPlayer(c, w, COEFFS, DEPTH, true, true); // RICH
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

  /** 標準 + 各個数 perCount レイアウト (used に無いもの) を生成し、各々 M 個のランダム初手を付ける。*/
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
        String key = Arrays.toString(L);
        if (!used.add(key)) continue;
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
