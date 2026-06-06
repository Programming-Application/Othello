import static ap26.Color.*;
import ap26.*;
import java.util.*;
import p26x42.OurBoard;
import p26x42.OurPlayer;

/**
 * fastest-first 順序付けが終盤解の値を変えない(実装バグが無い)ことを検証 (提出物に含めない)。
 * 同じ局面を FF 有効(FF_MIN=7)と無効(FF_MIN=99)で厳密解し、最終石差が一致するか確認する。
 *
 * 実行: java -cp "bin:." FFVerify [positions] [empties]
 */
public class FFVerify {
  static final int[] CAND = {0, 1, 2, 3, 4, 5, 6, 12, 18, 24, 30};

  public static void main(String[] args) {
    int positions = args.length > 0 ? Integer.parseInt(args[0]) : 60;
    int targetE = args.length > 1 ? Integer.parseInt(args[1]) : 15;
    Random rng = new Random(99);
    OurPlayer p = new OurPlayer(BLACK);
    int checked = 0, mismatch = 0;

    for (int i = 0; i < positions; i++) {
      OurBoard b = new OurBoard();
      if (rng.nextBoolean()) {
        int nb = 1 + rng.nextInt(3);
        List<Integer> xs = new ArrayList<>();
        for (int c : CAND) xs.add(c);
        Collections.shuffle(xs, rng);
        for (int j = 0; j < nb; j++) b.set(xs.get(j), BLOCK);
      }
      Board cur = b;
      while (!cur.isEnd() && cur.count(NONE) > targetE) {
        Color t = cur.getTurn();
        var legal = cur.findLegalMoves(t);
        cur = cur.placed(legal.get(rng.nextInt(legal.size())));
      }
      if (cur.isEnd()) continue;
      OurBoard pos = (OurBoard) cur;
      OurBoard root = (pos.getTurn() == BLACK) ? pos.clone() : pos.flipped();

      OurPlayer.FF_MIN = 99; // 無効
      int vOff = p.benchSolve(root);
      OurPlayer.FF_MIN = 7;  // 有効
      int vOn = p.benchSolve(root);
      checked++;
      if (vOff != vOn) {
        mismatch++;
        System.err.println("MISMATCH pos " + i + ": off=" + vOff + " on=" + vOn);
        if (mismatch > 5) break;
      }
    }
    OurPlayer.FF_MIN = 7;
    System.err.printf("checked=%d mismatch=%d -> %s%n",
        checked, mismatch, mismatch == 0 ? "OK (fastest-first は値を変えない)" : "FAIL");
  }
}
