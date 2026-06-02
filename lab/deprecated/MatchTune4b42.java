import ap26.*;
import static ap26.Color.*;
import java.util.*;
import p26x42.OurBoard;

/**
 * Phase 4b 検証 (提出物に含めない)。
 * 特徴量評価 (位置+mobility+frontier+stability) を本番同等エンジン(fixedDepth=0)に載せ、
 * 位置のみ評価(=Phase 4a相当)・サンプル p26x00 と対戦して優劣を確認する。
 *
 * 実行: java -cp "bin:." MatchTune4b42 [games_per_config] [budget_ms]
 */
public class MatchTune4b42 {

  static final int[] W = {
      29, 10, 10, 10, 10, 29,
      10, -5, -3, -3, -5, 10,
      10, -3, 1, 1, -3, 10,
      10, -3, 1, 1, -3, 10,
      10, -5, -3, -3, -5, 10,
      29, 10, 10, 10, 10, 29,
  };
  static final int[] FEAT = {10, 31, -20, 20}; // Phase 4b
  static final int[] POSONLY = {10, 0, 0, 0};  // Phase 4a 相当

  static final String[] LABELS = {"standard", "variant{a1,f6}", "variant{c1,a2,a5}"};
  static List<int[]> boards() { return List.of(new int[] {}, new int[] {0, 35}, new int[] {2, 6, 24}); }

  public static void main(String[] args) {
    int n = args.length > 0 ? Integer.parseInt(args[0]) : 20;
    long budgetMs = args.length > 1 ? Long.parseLong(args[1]) : 150;
    p26x42tune.OurPlayer.benchBudgetNanos = budgetMs * 1_000_000L;

    System.err.println("=== FEATURE vs POSITIONAL-ONLY (both full engine) ===");
    ab(true, FEAT, POSONLY, n);
    System.err.println("=== FEATURE vs p26x00 ===");
    ab(false, FEAT, null, n);
    System.err.println("=== POSITIONAL-ONLY vs p26x00 (baseline) ===");
    ab(false, POSONLY, null, n);
  }

  /** vsTune=true なら A(coeffs) vs B(coeffs); false なら A(coeffs) vs p26x00。*/
  static void ab(boolean vsTune, int[] ca, int[] cb, int n) {
    var bs = boards();
    int W_ = 0, L = 0, D = 0, M = 0, G = 0;
    for (int bi = 0; bi < bs.size(); bi++) {
      int w = 0, l = 0, d = 0, m = 0;
      for (int g = 0; g < n; g++) {
        boolean aBlack = (g % 2 == 0);
        Player black = aBlack ? mk(BLACK, ca) : (vsTune ? mk(BLACK, cb) : new p26x00.OurPlayer(BLACK));
        Player white = aBlack ? (vsTune ? mk(WHITE, cb) : new p26x00.OurPlayer(WHITE)) : mk(WHITE, ca);
        int diff = play(black, white, bs.get(bi));
        int our = aBlack ? diff : -diff;
        if (our > 0) w++; else if (our < 0) l++; else d++;
        m += our;
      }
      System.err.printf("  [%-18s] W %2d L %2d D %2d  win %5.1f%%  margin %+.1f%n",
          LABELS[bi], w, l, d, 100.0 * w / n, (double) m / n);
      W_ += w; L += l; D += d; M += m; G += n;
    }
    System.err.printf("  TOTAL W %d L %d D %d  win %.1f%%  margin %+.2f%n%n",
        W_, L, D, 100.0 * W_ / G, (double) M / G);
  }

  static Player mk(Color c, int[] coeffs) {
    return new p26x42tune.OurPlayer(c, W, coeffs, 0);
  }

  static int play(Player black, Player white, int[] blocks) {
    OurBoard b = new OurBoard();
    for (int k : blocks) b.set(k, BLOCK);
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
}
