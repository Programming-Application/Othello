import ap26.*;
import static ap26.Color.*;
import java.util.*;
import java.util.concurrent.*;
import p26x42.OurBoard;

/**
 * Phase F 上限ギャップ計測 (提出物に含めない実験用)。
 *
 * 「レイアウト個別に最適化した重み」(=上限) と「汎用トポロジ種類重み(1セット)」が、
 * どれだけ差があるかを測る。決定性の罠を避けるため各レイアウトで <b>ランダム初手</b> を入れ、
 * 同一初手で先後入替のペア対戦を多数行う。基準相手は固定重み(本番 Phase 4a/4b)。
 *
 *   generic[L]  = 汎用topo   vs 固定重み  (L上, ランダム初手)
 *   ceiling[L]  = L専用最適  vs 固定重み  (L専用に座標降下)
 *   gap[L]      = ceiling - generic   ← 実行時汎用分類が個別最適に届かない量
 *
 * 実行: java -cp "bin:." CeilingF42 [depth] [openings] [openingPlies]
 */
public class CeilingF42 {

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
  static final int[] GENERIC_TW = {23, 2, 8, 1, 1}; // Phase F (個数一様) で得た汎用種類重み

  static int DEPTH, M, R;
  static ExecutorService POOL;

  public static void main(String[] args) throws Exception {
    DEPTH = args.length > 0 ? Integer.parseInt(args[0]) : 6;
    M = args.length > 1 ? Integer.parseInt(args[1]) : 16;       // openings/layout
    R = args.length > 2 ? Integer.parseInt(args[2]) : 4;        // random opening plies
    int cores = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    POOL = Executors.newFixedThreadPool(cores);

    // サンプル: 標準 + 各個数3レイアウト (固定シードで再現)
    Random pick = new Random(99);
    Map<Integer, List<int[]>> samples = new LinkedHashMap<>();
    samples.put(0, List.of(new int[] {}));
    for (int c = 1; c <= 3; c++) samples.put(c, sampleLayouts(c, 3, pick));

    System.err.printf("depth=%d openings=%d plies=%d games/eval=%d cores=%d%n",
        DEPTH, M, R, M * 2, cores);
    System.err.println("nb | generic(vs fixed)   | ceiling(per-layout) | gap");

    int[][] agg = new int[4][3]; // [nb][genWins,ceilWins,games] 累積(レイアウト合算)
    for (var e : samples.entrySet()) {
      int nb = e.getKey();
      for (int[] blocks : e.getValue()) {
        // 同一レイアウト・同一初手集合を使う
        List<List<Move>> openings = makeOpenings(blocks);
        int[] gen = evalVsFixed(GENERIC_TW, blocks, openings); // {wins,losses,margin,games}
        int[] best = gen.clone();
        int[] bestTW = GENERIC_TW.clone();
        // L 専用に座標降下
        int[] tw = GENERIC_TW.clone();
        long bestFit = fit(gen);
        int[] steps = {16, 8, 4};
        for (int step : steps) {
          boolean imp = true; int guard = 0;
          while (imp && guard++ < 40) {
            imp = false;
            for (int i = 0; i < 5; i++) {
              for (int dir = -1; dir <= 1; dir += 2) {
                int[] cand = tw.clone();
                cand[i] = clamp(cand[i] + dir * step);
                if (cand[i] == tw[i]) continue;
                int[] r = evalVsFixed(cand, blocks, openings);
                if (fit(r) > bestFit) { bestFit = fit(r); tw = cand; best = r; bestTW = cand.clone(); imp = true; }
              }
            }
          }
        }
        double genWR = 100.0 * gen[0] / gen[3];
        double ceilWR = 100.0 * best[0] / best[3];
        System.err.printf("%2d | W%2d L%2d %5.1f%% m%+.1f | W%2d L%2d %5.1f%% m%+.1f | +%.1f%%  tw=%s%n",
            nb, gen[0], gen[1], genWR, (double) gen[2] / gen[3],
            best[0], best[1], ceilWR, (double) best[2] / best[3],
            ceilWR - genWR, Arrays.toString(bestTW));
        agg[nb][0] += gen[0]; agg[nb][1] += best[0]; agg[nb][2] += gen[3];
      }
    }

    System.err.println("==================== SUMMARY (by #blocks) ====================");
    System.err.println("nb | generic WR | ceiling WR | gap (ceiling-generic)");
    for (int nb = 0; nb <= 3; nb++) {
      if (agg[nb][2] == 0) continue;
      double g = 100.0 * agg[nb][0] / agg[nb][2];
      double c = 100.0 * agg[nb][1] / agg[nb][2];
      System.err.printf("%2d | %6.1f%%    | %6.1f%%    | +%.1f%%%n", nb, g, c, c - g);
    }
    POOL.shutdown();
    System.err.println("==============================================================");
  }

  static long fit(int[] r) { return (r[0] - r[1]) * 100000L + r[2]; } // net*1e5 + margin

  /** topo(typeW) vs 固定重み を、与えられた初手集合で先後ペア対戦。{wins,losses,margin,games}。*/
  static int[] evalVsFixed(int[] typeW, int[] blocks, List<List<Move>> openings) throws Exception {
    List<Callable<int[]>> tasks = new ArrayList<>();
    for (List<Move> op : openings) {
      tasks.add(() -> { int s = play(blocks, op, mkTopo(typeW, BLACK), mkFixed(WHITE)); return new int[] {Integer.signum(s), s}; });
      tasks.add(() -> { int s = play(blocks, op, mkFixed(BLACK), mkTopo(typeW, WHITE)); int o = -s; return new int[] {Integer.signum(o), o}; });
    }
    int wins = 0, losses = 0, margin = 0, games = 0;
    for (Future<int[]> f : POOL.invokeAll(tasks)) {
      int[] x = f.get();
      if (x[0] > 0) wins++; else if (x[0] < 0) losses++;
      margin += x[1]; games++;
    }
    return new int[] {wins, losses, margin, games};
  }

  static Player mkTopo(int[] tw, Color c) { return new p26x42tune.OurPlayer(c, tw, COEFFS, DEPTH, true); }
  static Player mkFixed(Color c) { return new p26x42tune.OurPlayer(c, W_FIXED, COEFFS, DEPTH); }

  /** blocks 盤に opening を適用後、両 player で終局まで。戻り = 黒石-白石。*/
  static int play(int[] blocks, List<Move> opening, Player black, Player white) {
    OurBoard b = new OurBoard();
    for (int k : blocks) b.set(k, BLOCK);
    black.setBoard(b.clone());
    white.setBoard(b.clone());
    Board cur = b;
    for (Move om : opening) cur = cur.placed(om); // 強制ランダム初手
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

  /** レイアウトごとに M 個のランダム初手列を生成 (各 R 手、シードは index で固定)。*/
  static List<List<Move>> makeOpenings(int[] blocks) {
    List<List<Move>> ops = new ArrayList<>();
    for (int s = 0; s < M; s++) {
      Random rng = new Random(1000L * (blocks.length + 1) + s * 7L + sum(blocks));
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

  static List<int[]> sampleLayouts(int count, int n, Random r) {
    Set<String> seen = new HashSet<>();
    List<int[]> out = new ArrayList<>();
    while (out.size() < n) {
      List<Integer> xs = new ArrayList<>();
      for (int c : CAND) xs.add(c);
      Collections.shuffle(xs, r);
      int[] L = new int[count];
      for (int i = 0; i < count; i++) L[i] = xs.get(i);
      Arrays.sort(L);
      String key = Arrays.toString(L);
      if (seen.add(key)) out.add(L);
    }
    return out;
  }

  static int clamp(int v) { return Math.max(-200, Math.min(200, v)); }
}
