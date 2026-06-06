import static ap26.Color.*;
import ap26.*;
import java.util.*;
import p26x42.OurBoard;
import p26x42.OurPlayer;

/**
 * 実効分岐数(EBF)で move ordering の良さを測る (提出物に含めない)。
 * EBF = (nodes(d2)/nodes(d1))^(1/(d2-d1))。完全順序の理論下限 ≈ sqrt(平均分岐) と比較。
 * EBF が sqrt(b) に近い → 順序付けは良好(node数の伸びしろ小、node/secが本命)。
 *
 * 実行: java -cp "bin:." EbfBench [positions] [d1] [d2]
 */
public class EbfBench {
  static final int[] CAND = {0, 1, 2, 3, 4, 5, 6, 12, 18, 24, 30};

  public static void main(String[] args) {
    int positions = args.length > 0 ? Integer.parseInt(args[0]) : 150;
    int d1 = args.length > 1 ? Integer.parseInt(args[1]) : 6;
    int d2 = args.length > 2 ? Integer.parseInt(args[2]) : 8;
    Random rng = new Random(7);
    OurPlayer p = new OurPlayer(BLACK);
    int[] buf = new int[40];
    long n1 = 0, n2 = 0, branchSum = 0, branchCnt = 0;

    for (int i = 0; i < positions; i++) {
      OurBoard b = new OurBoard();
      if (rng.nextBoolean()) {
        int nb = 1 + rng.nextInt(3);
        List<Integer> xs = new ArrayList<>();
        for (int c : CAND) xs.add(c);
        Collections.shuffle(xs, rng);
        for (int j = 0; j < nb; j++) b.set(xs.get(j), BLOCK);
      }
      int steps = 6 + rng.nextInt(14);
      Board cur = b;
      for (int s = 0; s < steps && !cur.isEnd(); s++) {
        Color t = cur.getTurn();
        var legal = cur.findLegalMoves(t);
        cur = cur.placed(legal.get(rng.nextInt(legal.size())));
      }
      if (cur.isEnd()) continue;
      OurBoard pos = (OurBoard) cur;
      // 平均分岐 (この局面の合法手数の代表値)
      branchSum += pos.findLegalMoves(pos.getTurn()).size();
      branchCnt++;

      OurPlayer.searchNodes = 0; p.searchValue(pos, d1, true); n1 += OurPlayer.searchNodes;
      OurPlayer.searchNodes = 0; p.searchValue(pos, d2, true); n2 += OurPlayer.searchNodes;
    }
    double ebf = Math.pow((double) n2 / n1, 1.0 / (d2 - d1));
    double b = (double) branchSum / branchCnt;
    System.err.printf("positions=%d  avg branching b=%.1f  sqrt(b)=%.2f%n", branchCnt, b, Math.sqrt(b));
    System.err.printf("nodes d%d=%d d%d=%d  -> EBF=%.2f  (理論下限 sqrt(b)=%.2f, 上限=b=%.1f)%n",
        d1, n1, d2, n2, ebf, Math.sqrt(b), b);
    System.err.printf("順序付け効率: EBF/sqrt(b) = %.2f (1.0に近いほど理想)%n", ebf / Math.sqrt(b));
  }
}
