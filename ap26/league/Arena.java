package ap26.league;

import ap26.*;
import static ap26.Color.*;
import java.util.*;

/**
 * head-to-head ベンチ用ドライバ (提出物外・ap26 ロジックは不変更)。
 *
 * <p>同一 JVM 内で {@code think()} を直接呼び、指定マッチアップを
 * 「標準盤 + 変形盤」× 両先後で総当たりし、勝率/平均石差/勝ち点を集計する。
 * 変形盤はシード固定で生成するので、複数マッチアップを同一盤面集合で比較できる。
 *
 * 使い方: java -cp "bin:." ap26.league.Arena <variants> <budgetMs> <seed>
 */
public class Arena {

  /** 競技ルールの勝ち点: 勝 = 10 + min(石差,10), 引 = 5, 負 = 0 (our 視点)。*/
  static int points(int ourDiff) {
    if (ourDiff > 0) return 10 + Math.min(ourDiff, 10);
    if (ourDiff == 0) return 5;
    return 0;
  }

  /** 標準盤(#0) + numVariants 枚の変形盤を seed 固定で生成 (League.makeBoard と同方式)。*/
  static List<OfficialBoard> makeBoards(int numVariants, long seed) {
    List<OfficialBoard> boards = new ArrayList<>();
    OfficialBoard std = new OfficialBoard();
    std.setBoardId("#0");
    boards.add(std);
    Random rand = new Random(seed);
    List<Integer> candidates = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 12, 18, 24, 30);
    for (int i = 0; i < numVariants; i++) {
      List<Integer> xs = new ArrayList<>(candidates);
      Collections.shuffle(xs, rand);
      OfficialBoard b = new OfficialBoard();
      int cnt = rand.nextInt(3) + 1; // 1..3 個
      for (int j = 0; j < cnt; j++)
        b.set(xs.get(j), BLOCK);
      b.setBoardId("#" + (i + 1));
      boards.add(b);
    }
    return boards;
  }

  /** 反則時のスコア (敗者視点で全マスを相手に渡す近似)。*/
  static int foulScore(Color loser) {
    return loser == BLACK ? -64 : 64;
  }

  /** 1 ゲームを進行し最終スコア (BLACK - WHITE) を返す。反則は ±64。*/
  static int playGame(OfficialBoard start, Player black, Player white, double timeLimitSec) {
    Map<Color, Player> players = Map.of(BLACK, black, WHITE, white);
    Board board = start.clone();
    black.setBoard(board.clone());
    white.setBoard(board.clone());
    Map<Color, Double> times = new HashMap<>();
    times.put(BLACK, 0.0);
    times.put(WHITE, 0.0);

    while (!board.isEnd()) {
      Color turn = board.getTurn();
      Player p = players.get(turn);
      long t = System.nanoTime();
      Move mv;
      try {
        mv = p.think(board.clone()).colored(turn);
      } catch (Throwable e) {
        return foulScore(turn); // 例外 → 反則
      }
      double sec = (System.nanoTime() - t) / 1e9;
      times.merge(turn, sec, Double::sum);
      if (times.get(turn) > timeLimitSec)
        return foulScore(turn); // 時間切れ → 反則
      List<Move> legals = board.findLegalMoves(turn);
      if (!legals.contains(mv))
        return foulScore(turn); // 非合法 → 反則
      board = board.placed(mv);
    }
    return board.score();
  }

  interface PF {
    Player make(Color c);
  }

  /** our (= ourFac) を p26x00 相手に全盤面×両先後で対戦させ結果を集計表示。*/
  static void runMatchup(String label, PF ourFac, List<OfficialBoard> boards, double timeLimitSec) {
    int win = 0, draw = 0, loss = 0, games = 0;
    long diffSum = 0, pointSum = 0;
    for (OfficialBoard b : boards) {
      // game A: our = BLACK, p26x00 = WHITE
      {
        Player our = ourFac.make(BLACK);
        Player opp = new p26x00.OurPlayer(WHITE);
        int s = playGame(b, our, opp, timeLimitSec); // our 視点 = +s
        int d = s;
        if (d > 0) win++; else if (d < 0) loss++; else draw++;
        diffSum += d; pointSum += points(d); games++;
      }
      // game B: our = WHITE, p26x00 = BLACK
      {
        Player opp = new p26x00.OurPlayer(BLACK);
        Player our = ourFac.make(WHITE);
        int s = playGame(b, opp, our, timeLimitSec); // our 視点 = -s
        int d = -s;
        if (d > 0) win++; else if (d < 0) loss++; else draw++;
        diffSum += d; pointSum += points(d); games++;
      }
    }
    double wr = 100.0 * win / games;
    double wrScored = 100.0 * (win + 0.5 * draw) / games; // 引分0.5換算
    System.out.printf(Locale.US,
        "%-22s games=%d  W-D-L = %d-%d-%d  winRate=%.1f%%  scored(½draw)=%.1f%%  avgDiff=%+.2f  avgPts=%.2f%n",
        label, games, win, draw, loss, wr, wrScored, (double) diffSum / games, (double) pointSum / games);
  }

  public static void main(String[] args) {
    int variants = args.length > 0 ? Integer.parseInt(args[0]) : 12;
    long budgetMs = args.length > 1 ? Long.parseLong(args[1]) : 300;
    long seed = args.length > 2 ? Long.parseLong(args[2]) : 42L;

    // budgetMs>0 のときのみ 1 手持ち時間を固定 (高速ベンチ用)。
    // budgetMs<=0 なら本番同様の適応スケジュール (序盤cap/終盤厚め/58s逆算) を使う。
    if (budgetMs > 0) {
      p26x42.OurPlayer.benchBudgetNanos = budgetMs * 1_000_000L;
      p26x42base.OurPlayer.benchBudgetNanos = budgetMs * 1_000_000L;
    }
    double timeLimitGame = budgetMs > 0 ? 300.0 : 60.0; // 本番は 60s/ゲーム

    List<OfficialBoard> boards = makeBoards(variants, seed);
    System.out.printf("=== Arena: %d boards (1 standard + %d variants), budget=%dms/move, seed=%d ===%n",
        boards.size(), variants, budgetMs, seed);
    System.out.println("(each board played both colors => " + (boards.size() * 2) + " games per matchup)\n");

    runMatchup("p26x42base vs p26x00", c -> new p26x42base.OurPlayer(c), boards, timeLimitGame);
    runMatchup("p26x42     vs p26x00", c -> new p26x42.OurPlayer(c), boards, timeLimitGame);

    System.out.println("\np26x42 book hits (total moves played from proven.book): " + p26x42.OurPlayer.bookHits);
  }
}
