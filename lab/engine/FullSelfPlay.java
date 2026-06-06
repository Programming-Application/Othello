import ap26.*;
import static ap26.Color.*;
import java.util.*;
import p26x42.OurBoard;
import p26x42.OurPlayer;

/**
 * フル予算(本番同等の時間管理)で自己対戦し、終盤完全読みのフォールバック回数と
 * 各プレイヤーの累積思考時間・到達深さを確認する (提出物に含めない)。
 * しきい値20 + 終盤予算引上げで E=20 が実際に完走する(fallback~0)かを見る。
 *
 * 実行: java -cp "bin:." FullSelfPlay [gamesPerConfig]
 */
public class FullSelfPlay {
  public static void main(String[] args) {
    int n = args.length > 0 ? Integer.parseInt(args[0]) : 2;
    OurPlayer.benchBudgetNanos = 0; // フル予算(58s/ゲーム の内部管理)

    List<int[]> boards = List.of(new int[] {}, new int[] {0, 5}, new int[] {2, 6, 24});
    String[] labels = {"standard", "variant{a1,f6}", "variant{c1,a2,a5}"};

    OurPlayer.endgameFallback = 0;
    OurPlayer.wldProven = 0;
    OurPlayer.wldFallback = 0;
    long worstGameNanos = 0;

    for (int bi = 0; bi < boards.size(); bi++) {
      for (int g = 0; g < n; g++) {
        OurPlayer.maxReachedDepth = 0;
        OurBoard b = new OurBoard();
        for (int k : boards.get(bi)) b.set(k, BLOCK);
        OurPlayer black = new OurPlayer(BLACK), white = new OurPlayer(WHITE);
        black.setBoard(b.clone());
        white.setBoard(b.clone());
        long tB = 0, tW = 0;
        Board cur = b;
        while (!cur.isEnd()) {
          Color turn = cur.getTurn();
          OurPlayer p = (turn == BLACK) ? black : white;
          long t0 = System.nanoTime();
          Move mv = p.think(cur.clone()).colored(turn);
          long dt = System.nanoTime() - t0;
          if (turn == BLACK) tB += dt; else tW += dt;
          if (!cur.findLegalMoves(turn).contains(mv)) { cur.foul(turn); break; }
          cur = cur.placed(mv);
        }
        worstGameNanos = Math.max(worstGameNanos, Math.max(tB, tW));
        System.err.printf("[%-16s g%d] black think=%.1fs white think=%.1fs maxDepth=%d%n",
            labels[bi], g, tB / 1e9, tW / 1e9, OurPlayer.maxReachedDepth);
      }
    }
    System.err.println("------------------------------------------------------------");
    System.err.printf("WLD証明: 成功=%d  フォールバック=%d  (成功が多いほど証明ゾーンが高い)%n",
        OurPlayer.wldProven, OurPlayer.wldFallback);
    System.err.printf("endgame(≤20) fallback = %d%n", OurPlayer.endgameFallback);
    System.err.printf("到達した最大の証明/読み空き数(maxReachedDepth) は各ゲーム末に表示%n");
    System.err.printf("worst single-player think time = %.1fs (60s 未満必須)%n", worstGameNanos / 1e9);
  }
}
