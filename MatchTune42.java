import ap26.*;
import static ap26.Color.*;
import java.util.*;
import p26x42.OurBoard;

/**
 * 重み検証 (Phase 4a, 提出物に含めない)。
 *
 * チューニングで得た重みを「本番同等エンジン (p26x42tune を fixedDepth=0 で全機能)」に載せ、
 * デフォルト重み・サンプル p26x00 と対戦させて実戦での優劣を確認する。
 * 持ち時間は benchBudgetNanos で固定し短時間で多数対局。審判は OurBoard。
 *
 * 実行: java -cp "bin:." MatchTune42 [games_per_config] [budget_ms]
 */
public class MatchTune42 {

  static final int[] CLASS = buildClassMap();

  // 比較する 6 パラメータ
  static final int[] DEFAULT6 = {10, 10, 10, -5, 1, 1};
  static final int[] TUNED6 = {29, 10, 10, -5, -3, 1};

  public static void main(String[] args) {
    int n = args.length > 0 ? Integer.parseInt(args[0]) : 20;
    long budgetMs = args.length > 1 ? Long.parseLong(args[1]) : 150;
    p26x42tune.OurPlayer.benchBudgetNanos = budgetMs * 1_000_000L;

    int[] wTuned = expand(TUNED6);
    int[] wDefault = expand(DEFAULT6);

    List<int[]> boards = boardSet();

    System.err.println("=== TUNED vs DEFAULT (both full engine) ===");
    abTune(wTuned, wDefault, boards, n);

    System.err.println("=== TUNED vs p26x00 (sample) ===");
    abVsSample(wTuned, boards, n);

    System.err.println("=== DEFAULT vs p26x00 (sample, baseline) ===");
    abVsSample(wDefault, boards, n);
  }

  static List<int[]> boardSet() {
    return List.of(new int[] {}, new int[] {0, 35}, new int[] {2, 6, 24});
  }
  static final String[] LABELS = {"standard", "variant{a1,f6}", "variant{c1,a2,a5}"};

  /** A(重み) vs B(重み): 両方 p26x42tune フルエンジン。*/
  static void abTune(int[] wa, int[] wb, List<int[]> boards, int n) {
    int W = 0, L = 0, D = 0, M = 0, G = 0;
    for (int bi = 0; bi < boards.size(); bi++) {
      int w = 0, l = 0, d = 0, m = 0;
      for (int g = 0; g < n; g++) {
        boolean aBlack = (g % 2 == 0);
        int diff = play(
            aBlack ? new p26x42tune.OurPlayer(BLACK, wa, 0) : new p26x42tune.OurPlayer(BLACK, wb, 0),
            aBlack ? new p26x42tune.OurPlayer(WHITE, wb, 0) : new p26x42tune.OurPlayer(WHITE, wa, 0),
            boards.get(bi));
        int our = aBlack ? diff : -diff;
        if (our > 0) w++; else if (our < 0) l++; else d++;
        m += our;
      }
      System.err.printf("  [%-18s] W %2d L %2d D %2d  win %5.1f%%  margin %+.1f%n",
          LABELS[bi], w, l, d, 100.0 * w / n, (double) m / n);
      W += w; L += l; D += d; M += m; G += n;
    }
    System.err.printf("  TOTAL W %d L %d D %d  win %.1f%%  margin %+.2f%n%n",
        W, L, D, 100.0 * W / G, (double) M / G);
  }

  /** A(重み, フルエンジン) vs サンプル p26x00。*/
  static void abVsSample(int[] wa, List<int[]> boards, int n) {
    int W = 0, L = 0, D = 0, M = 0, G = 0;
    for (int bi = 0; bi < boards.size(); bi++) {
      int w = 0, l = 0, d = 0, m = 0;
      for (int g = 0; g < n; g++) {
        boolean aBlack = (g % 2 == 0);
        int diff = play(
            aBlack ? new p26x42tune.OurPlayer(BLACK, wa, 0) : new p26x00.OurPlayer(BLACK),
            aBlack ? new p26x00.OurPlayer(WHITE) : new p26x42tune.OurPlayer(WHITE, wa, 0),
            boards.get(bi));
        int our = aBlack ? diff : -diff;
        if (our > 0) w++; else if (our < 0) l++; else d++;
        m += our;
      }
      System.err.printf("  [%-18s] W %2d L %2d D %2d  win %5.1f%%  margin %+.1f%n",
          LABELS[bi], w, l, d, 100.0 * w / n, (double) m / n);
      W += w; L += l; D += d; M += m; G += n;
    }
    System.err.printf("  TOTAL W %d L %d D %d  win %.1f%%  margin %+.2f%n%n",
        W, L, D, 100.0 * W / G, (double) M / G);
  }

  static int play(Player black, Player white, int[] blocks) {
    OurBoard b = new OurBoard();
    for (int k : blocks) b.set(k, BLOCK);
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
    for (int k = 0; k < 36; k++) w[k] = p6[CLASS[k]];
    return w;
  }

  static int[] buildClassMap() {
    int[][] pairs = {{0, 0}, {0, 1}, {0, 2}, {1, 1}, {1, 2}, {2, 2}};
    int[] map = new int[36];
    for (int k = 0; k < 36; k++) {
      int rr = Math.min(k / 6, 5 - k / 6), cc = Math.min(k % 6, 5 - k % 6);
      int a = Math.min(rr, cc), bb = Math.max(rr, cc);
      for (int i = 0; i < pairs.length; i++)
        if (pairs[i][0] == a && pairs[i][1] == bb) { map[k] = i; break; }
    }
    return map;
  }
}
