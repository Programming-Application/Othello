import static ap26.Color.*;
import ap26.*;
import java.io.*;
import p26x42.OurBoard;
import p26x42.OurPlayer;

/**
 * 人間 vs 我々AI(p26x42) の対話対戦 (提出物に含めない)。
 * 盤を表示し、人間は手をマス座標(例 e3)で入力、AIは think() で応手する。強さの体感用。
 *
 * 実行: java -cp "bin:." HumanPlay [human=b|w] [blocks(空=標準, 例 "0,5,30")] [aiBudgetMs(0=フル時間)]
 *   入力: マス座標 "e3" / "q"で投了終了。合法手は毎手表示される。
 */
public class HumanPlay {
  public static void main(String[] args) throws IOException {
    Color human = (args.length > 0 && args[0].toLowerCase().startsWith("w")) ? WHITE : BLACK;
    int[] blocks = (args.length > 1 && !args[1].isEmpty()) ? parse(args[1]) : new int[0];
    long budgetMs = args.length > 2 ? Long.parseLong(args[2]) : 0;
    OurPlayer.benchBudgetNanos = budgetMs * 1_000_000L; // 0 ならフル時間管理

    Color ai = human.flipped();
    OurBoard b = new OurBoard();
    for (int k : blocks) b.set(k, BLOCK);
    OurPlayer brain = new OurPlayer(ai);
    brain.setBoard(b.clone());
    BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
    System.out.println("あなた=" + (human == BLACK ? "黒(x)" : "白(o)") + " / AI=" + (ai == BLACK ? "黒(x)" : "白(o)")
        + "  blocks=" + java.util.Arrays.toString(blocks) + "  (手は 'e3' 形式, 'q'で終了)");

    Color turn = BLACK;
    while (!b.isEnd()) {
      System.out.print(b);
      System.out.printf("石 黒%d 白%d  空き%d  手番=%s%n",
          b.count(BLACK), b.count(WHITE), b.count(NONE), turn == BLACK ? "黒(x)" : "白(o)");
      var moves = b.findLegalMoves(turn);
      if (moves.size() == 1 && moves.get(0).isPass()) {
        System.out.println((turn == human ? "あなた" : "AI") + " はパス");
        turn = turn.flipped();
        continue;
      }
      Move mv;
      if (turn == human) {
        System.out.print("合法手 ");
        for (Move m : moves) System.out.print(coord(m.getIndex()) + " ");
        System.out.println();
        while (true) {
          System.out.print("> ");
          String line = in.readLine();
          if (line == null || line.trim().equalsIgnoreCase("q")) { System.out.println("終了"); return; }
          line = line.trim().toLowerCase();
          int k = parseCoord(line);
          if (k < 0) { System.out.println("形式エラー (例 e3)"); continue; }
          Move cand = new Move(k, human);
          if (b.findLegalMoves(human).contains(cand)) { mv = cand; break; }
          System.out.println("非合法手");
        }
      } else {
        long t0 = System.nanoTime();
        mv = brain.think(b.clone()).colored(ai);
        System.out.printf("AI -> %s (%.1fs)%n", coord(mv.getIndex()), (System.nanoTime() - t0) / 1e9);
      }
      b = b.placed(mv);
      turn = turn.flipped();
    }
    System.out.print(b);
    int s = b.score();
    System.out.printf("=== 終局 黒%d 白%d  石差(黒-白)%+d → %s ===%n",
        b.count(BLACK), b.count(WHITE), s,
        s == 0 ? "引分" : (s > 0 ? "黒(x)勝ち" : "白(o)勝ち"));
    System.out.println((Integer.signum(s) == (human == BLACK ? 1 : -1)) ? "あなたの勝ち！"
        : s == 0 ? "引分" : "AIの勝ち");
  }

  /** "e3" → マス番号。不正は -1。*/
  static int parseCoord(String s) {
    if (s.length() != 2) return -1;
    int c = s.charAt(0) - 'a', r = s.charAt(1) - '1';
    if (c < 0 || c >= 6 || r < 0 || r >= 6) return -1;
    return r * 6 + c;
  }

  static String coord(int k) { return "" + (char) ('a' + k % 6) + (k / 6 + 1); }

  static int[] parse(String s) {
    String[] p = s.split(",");
    int[] r = new int[p.length];
    for (int i = 0; i < p.length; i++) r[i] = Integer.parseInt(p[i].trim());
    return r;
  }
}
