import static ap26.Color.*;
import ap26.*;
import java.util.*;
import p26x42.OurBoard;
import p26x42.OurPlayer;

/**
 * TT+PVS による「同じ深さでのノード削減」を測る (提出物に含めない)。
 * 実行: java -cp "bin:." NodeBench [positions] [depth]
 */
public class NodeBench {
  static final int[] CAND = {0, 1, 2, 3, 4, 5, 6, 12, 18, 24, 30};

  public static void main(String[] args) {
    int positions = args.length > 0 ? Integer.parseInt(args[0]) : 200;
    int depth = args.length > 1 ? Integer.parseInt(args[1]) : 8;
    Random rng = new Random(7);
    OurPlayer p = new OurPlayer(BLACK);
    long nOn = 0, nOff = 0;

    for (int i = 0; i < positions; i++) {
      OurBoard b = new OurBoard();
      if (rng.nextBoolean()) {
        int nb = 1 + rng.nextInt(3);
        List<Integer> xs = new ArrayList<>();
        for (int c : CAND) xs.add(c);
        Collections.shuffle(xs, rng);
        for (int j = 0; j < nb; j++) b.set(xs.get(j), BLOCK);
      }
      int steps = 4 + rng.nextInt(16);
      Board cur = b;
      for (int s = 0; s < steps && !cur.isEnd(); s++) {
        Color t = cur.getTurn();
        var legal = cur.findLegalMoves(t);
        cur = cur.placed(legal.get(rng.nextInt(legal.size())));
      }
      if (cur.isEnd()) continue;
      OurBoard pos = (OurBoard) cur;

      OurPlayer.searchNodes = 0;
      p.searchValue(pos, depth, false);
      nOff += OurPlayer.searchNodes;

      OurPlayer.searchNodes = 0;
      p.searchValue(pos, depth, true);
      nOn += OurPlayer.searchNodes;
    }
    System.err.printf("depth=%d  nodes off(plain ab)=%d  on(TT+PVS)=%d  reduction=%.2fx%n",
        depth, nOff, nOn, (double) nOff / nOn);
  }
}
