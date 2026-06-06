import ap26.*;
import static ap26.Color.*;
import ap26.league.*;
import java.util.*;
import p26x42.OurBoard;

/**
 * ビットボード OurBoard の正しさを審判 OfficialBoard と照合する (提出物に含めない)。
 *
 * 標準盤からランダムに対局を進め、各局面で OfficialBoard と OurBoard の
 * (1) 全マスの色 get(k), (2) 両色の合法手集合, (3) 着手後の盤面 を比較する。
 * 1 つでも食い違えばエラー報告。BLOCK ありの照合は実リーグ(Illegals=0)で担保する。
 *
 * 実行: java -cp "bin:." VerifyBB [games]
 */
public class VerifyBB {
  public static void main(String[] args) {
    int games = args.length > 0 ? Integer.parseInt(args[0]) : 2000;
    Random rng = new Random(12345);
    int mismatches = 0, plies = 0;

    for (int g = 0; g < games; g++) {
      OfficialBoard ref = new OfficialBoard();
      OurBoard ours = new OurBoard();
      while (!ref.isEnd()) {
        plies++;
        if (!sameCells(ref, ours) || !sameLegal(ref, ours, BLACK) || !sameLegal(ref, ours, WHITE)) {
          mismatches++;
          System.err.println("MISMATCH game " + g + " ply " + plies);
          System.err.println("ref:\n" + ref);
          System.err.println("ours:\n" + ours);
          if (mismatches > 5) { System.err.println("too many, abort"); return; }
          break;
        }
        Color turn = ref.getTurn();
        var legal = ref.findLegalMoves(turn);
        Move mv = legal.get(rng.nextInt(legal.size()));
        ref = (OfficialBoard) ref.placed(mv);
        ours = ours.placed(mv);
      }
      // 終局: score 一致確認
      if (ref.score() != ours.score()) {
        mismatches++;
        System.err.println("SCORE MISMATCH game " + g + ": ref=" + ref.score() + " ours=" + ours.score());
      }
    }
    System.err.printf("games=%d plies=%d mismatches=%d -> %s%n",
        games, plies, mismatches, mismatches == 0 ? "OK (OurBoard == OfficialBoard)" : "FAIL");
  }

  static boolean sameCells(Board a, Board b) {
    for (int k = 0; k < Board.LENGTH; k++)
      if (a.get(k) != b.get(k)) return false;
    return true;
  }

  static boolean sameLegal(Board a, Board b, Color c) {
    var sa = new TreeSet<Integer>();
    for (Move m : a.findLegalMoves(c)) sa.add(m.getIndex());
    var sb = new TreeSet<Integer>();
    for (Move m : b.findLegalMoves(c)) sb.add(m.getIndex());
    return sa.equals(sb);
  }
}
