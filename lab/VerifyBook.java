import static ap26.Color.*;
import ap26.Move;
import ap26.Color;
import p26x42.OurBoard;
import p26x42.OurPlayer;
import java.io.*;
import java.util.*;

/**
 * proven.book のカバレッジ検証 (提出物外)。
 * 指定config・勝者色で「相手は全合法手・我々はbook手のみ」を辿り、空き>=23の自手番局面が
 * 全て book に在る(ギャップ無し)かを BFS で確認する。キーは side-xor 規約。
 *
 * 実行: java -cp "bin:." VerifyBook <book> <winColor:B|W> [blocks(カンマ区切り,空=標準)]
 *   例: VerifyBook p26x42/proven.book W            (標準・白勝ち)
 *       VerifyBook p26x42/proven.book B 0          (a1・黒勝ち)
 *       VerifyBook p26x42/proven.book B 6          (a2=transpose派生・黒勝ち)
 */
public class VerifyBook {
  static HashMap<Long, Integer> book = new HashMap<>();
  static int MIN_E = 23;
  static long gaps = 0, checked = 0;
  static Color win, opp;
  static long winXor; // 勝者手番キーの xor (白勝ち=BOOK_SIDE_XOR, 黒勝ち=0)
  static HashSet<Long> seen = new HashSet<>();

  public static void main(String[] args) throws IOException {
    String path = args[0];
    win = args[1].equalsIgnoreCase("W") ? WHITE : BLACK;
    opp = win.flipped();
    winXor = (win == WHITE) ? OurPlayer.BOOK_SIDE_XOR : 0L;
    int[] blocks = (args.length > 2 && !args[2].isEmpty()) ? parse(args[2]) : new int[0];

    try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(path)))) {
      int n = in.readInt();
      for (int i = 0; i < n; i++) { long k = in.readLong(); int v = in.readByte() & 0xFF; book.put(k, v); }
    }
    OurBoard start = new OurBoard();
    for (int k : blocks) start.set(k, BLOCK);
    System.err.printf("検証: book=%d局面 config=%s 勝者=%s%n", book.size(), Arrays.toString(blocks), win);

    bfs(start, BLACK); // 標準otelloは黒から
    System.err.printf("→ 自手番(>=%d空き)チェック=%d ギャップ=%d  %s%n",
        MIN_E, checked, gaps, gaps == 0 ? "カバレッジ完全 ✓" : "ギャップあり !!");
  }

  static void bfs(OurBoard b, Color turn) {
    long mark = b.cellHash() ^ (turn == WHITE ? 0x55L : 0xAAL);
    if (!seen.add(mark)) return;
    boolean has = hasMove(b, turn);
    if (!has) { if (hasMove(b, turn.flipped())) bfs(b, turn.flipped()); return; } // パス

    if (turn == opp) { // 相手: 全合法手
      for (Move m : b.findLegalMoves(turn)) if (!m.isPass()) bfs(b.placed(m), turn.flipped());
      return;
    }
    // 我々(勝者側)
    int empties = b.count(NONE);
    if (empties < MIN_E) return; // live WLD ゾーン → 打ち切り
    checked++;
    Integer mv = book.get(b.cellHash() ^ winXor);
    if (mv == null) {
      gaps++;
      if (gaps <= 8) System.err.printf("  GAP: 空き%d %s手番が book に無い%n", empties, win);
      return;
    }
    bfs(b.placed(new Move(mv, turn)), turn.flipped());
  }

  static boolean hasMove(OurBoard b, Color c) {
    var ms = b.findLegalMoves(c);
    return !(ms.size() == 1 && ms.get(0).isPass());
  }

  static int[] parse(String s) {
    String[] p = s.split(",");
    int[] r = new int[p.length];
    for (int i = 0; i < p.length; i++) r[i] = Integer.parseInt(p[i].trim());
    return r;
  }
}
