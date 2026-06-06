import static ap26.Color.*;
import ap26.*;
import java.util.*;
import p26x42.OurBoard;
import p26x42.OurPlayer;

/**
 * TT+PVS が探索値を変えない(厳密最適化である)ことを検証する (提出物に含めない)。
 * ランダム局面で固定深さの探索値を ttpvs=on と off で比較し、全一致を確認する。
 *
 * 実行: java -cp "bin:." VerifySearch [positions] [depth]
 */
public class VerifySearch {
  static final int[] CAND = {0, 1, 2, 3, 4, 5, 6, 12, 18, 24, 30};

  public static void main(String[] args) {
    int positions = args.length > 0 ? Integer.parseInt(args[0]) : 400;
    int depth = args.length > 1 ? Integer.parseInt(args[1]) : 7;
    Random rng = new Random(2024);
    OurPlayer p = new OurPlayer(BLACK);
    int checked = 0, mismatch = 0;

    for (int i = 0; i < positions; i++) {
      // ランダムな盤(標準 or 変形) を作り、ランダムに数手進めた局面
      OurBoard b = new OurBoard();
      if (rng.nextBoolean()) {
        int nb = 1 + rng.nextInt(3);
        List<Integer> xs = new ArrayList<>();
        for (int c : CAND) xs.add(c);
        Collections.shuffle(xs, rng);
        for (int j = 0; j < nb; j++) b.set(xs.get(j), BLOCK);
      }
      int steps = rng.nextInt(20);
      Board cur = b;
      for (int s = 0; s < steps && !cur.isEnd(); s++) {
        Color turn = cur.getTurn();
        var legal = cur.findLegalMoves(turn);
        cur = cur.placed(legal.get(rng.nextInt(legal.size())));
      }
      if (cur.isEnd()) continue;
      OurBoard pos = (OurBoard) cur;

      int vOn = p.searchValue(pos, depth, true);
      int vOff = p.searchValue(pos, depth, false);
      checked++;
      if (vOn != vOff) {
        mismatch++;
        System.err.println("MISMATCH pos " + i + ": on=" + vOn + " off=" + vOff);
        if (mismatch > 5) break;
      }
    }
    System.err.printf("checked=%d mismatch=%d depth=%d -> %s%n",
        checked, mismatch, depth, mismatch == 0 ? "OK (TT+PVS は値を変えない)" : "FAIL");
  }
}
