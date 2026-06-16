import ap26.*;
import ap26.league.OfficialBoard;
import static ap26.Color.*;
import java.util.*;

/**
 * p26x42 探索速度ベンチ (提出物外)。
 * 標準盤(#0)で p26x42 同士の自己対戦を 1 局、初手から終局まで進め、
 * その間に探索した総ノード数 (OurPlayer.searchNodes) を、全 think() 呼び出しの
 * 累積思考時間で割って ノード/秒 を報告する。
 *
 *   java -cp "bin:." Bench42   ← 本番同等 (proven.book 有効)
 *   java -cp "bin"   Bench42   ← book 無効 (全局面を実際に探索 = 純探索スループット)
 */
public class Bench42 {
  public static void main(String[] args) {
    OfficialBoard start = new OfficialBoard();
    start.setBoardId("#0");

    Player black = new p26x42.OurPlayer(BLACK);
    Player white = new p26x42.OurPlayer(WHITE);
    Map<Color, Player> players = Map.of(BLACK, black, WHITE, white);

    Board board = start.clone();
    black.setBoard(board.clone());
    white.setBoard(board.clone());

    // 計測開始: ノードカウンタ・book ヒット数をリセット
    p26x42.OurPlayer.searchNodes = 0;
    p26x42.OurPlayer.bookHits = 0;

    long totalThinkNanos = 0;
    int moves = 0;
    while (!board.isEnd()) {
      Color turn = board.getTurn();
      Player p = players.get(turn);
      long t = System.nanoTime();
      Move mv = p.think(board.clone()).colored(turn);
      totalThinkNanos += System.nanoTime() - t;
      moves++;
      List<Move> legals = board.findLegalMoves(turn);
      if (!legals.contains(mv)) {
        System.out.println("ILLEGAL MOVE by " + turn + ": " + mv);
        return;
      }
      board = board.placed(mv);
    }

    long nodes = p26x42.OurPlayer.searchNodes;
    double sec = totalThinkNanos / 1e9;
    double nps = nodes / sec;
    boolean bookLoaded = p26x42.OurPlayer.PROVEN_BOOK != null;

    System.out.println("================ p26x42 1局ベンチ (標準盤 #0, 自己対戦) ================");
    System.out.printf(Locale.US, "proven.book      : %s%n", bookLoaded ? "有効 (本番同等)" : "無効 (純探索)");
    System.out.printf(Locale.US, "着手数 (両者計)   : %d%n", moves);
    System.out.printf(Locale.US, "book ヒット手数   : %d%n", p26x42.OurPlayer.bookHits);
    System.out.printf(Locale.US, "最終石差 (黒-白)  : %+d%n", board.score());
    System.out.printf(Locale.US, "探索総ノード数    : %,d nodes%n", nodes);
    System.out.printf(Locale.US, "総思考時間        : %.3f s%n", sec);
    System.out.printf(Locale.US, "最大到達深さ      : %d%n", p26x42.OurPlayer.maxReachedDepth);
    System.out.printf(Locale.US, "------------------------------------------------------------%n");
    System.out.printf(Locale.US, ">>> 探索速度       : %,.0f ノード/秒  (%.2f Mnps)%n", nps, nps / 1e6);
    System.out.println("============================================================");
  }
}
