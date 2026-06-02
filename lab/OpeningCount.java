import static ap26.Color.*;
import ap26.*;
import java.util.*;
import p26x42.OurBoard;

/**
 * 序盤 D 手で到達する「異なる局面数」を列挙して数える (提出物に含めない)。
 * 重複局面は Zobrist ハッシュ(+手番)でマージ。対称性は使わない(上限の見積り)。
 * これが弱解(本)構築の「解くべき葉の数」のオーダーを与える。
 *
 * 実行: java -cp "bin:." OpeningCount [depth] [blocks(カンマ区切りindex)]
 */
public class OpeningCount {
  static final long SIDE = 0x9E3779B97F4A7C15L;
  static long CAP = 60_000_000L; // 安全上限(これ超で打ち切り)
  static long[] perDisc = new long[40]; // disc数ごとの異なる局面数
  static HashSet<Long> seen = new HashSet<>(1 << 20);
  static int maxDiscs;          // 葉(列挙終端)の disc 数
  static boolean capped = false;

  public static void main(String[] args) {
    int depth = args.length > 0 ? Integer.parseInt(args[0]) : 12;
    int[] blocks = new int[0];
    if (args.length > 1 && !args[1].isEmpty()) {
      String[] ps = args[1].split(",");
      blocks = new int[ps.length];
      for (int i = 0; i < ps.length; i++) blocks[i] = Integer.parseInt(ps[i].trim());
    }
    OurBoard start = new OurBoard();
    for (int k : blocks) start.set(k, BLOCK);
    int startDiscs = start.count(BLACK) + start.count(WHITE);
    maxDiscs = startDiscs + depth; // depth 手で disc が depth 個増える(パス除く)

    System.err.printf("depth=%d blocks=%s startDiscs=%d maxDiscs=%d cap=%d%n",
        depth, Arrays.toString(blocks), startDiscs, maxDiscs, CAP);
    long t0 = System.nanoTime();
    dfs(start);
    double sec = (System.nanoTime() - t0) / 1e9;

    long total = 0;
    for (int d = 0; d < 40; d++) {
      if (perDisc[d] > 0) {
        System.err.printf("  discs=%2d (手数%2d): %,d 局面%n", d, d - startDiscs, perDisc[d]);
        total += perDisc[d];
      }
    }
    long leaves = perDisc[maxDiscs];
    System.err.println("------------------------------------------------------------");
    System.err.printf("総異なる局面(disc<=%d) = %,d  / 葉(disc=%d, ~20空き) = %,d  %s (%.1fs)%n",
        maxDiscs, total, maxDiscs, leaves, capped ? "[CAP打ち切り]" : "", sec);
    // 試算
    double aveSolve = 4.5; // 20手ソルバ平均(s) ※共有TTでこれより速くなる
    System.err.printf("試算: 葉 %,d × %.1fs × 231 ≈ %.1f コア時間 (共有TTで更に短縮見込)%n",
        leaves, aveSolve, leaves * aveSolve * 231 / 3600.0);
  }

  static void dfs(OurBoard b) {
    if (capped) return;
    int discs = b.count(BLACK) + b.count(WHITE);
    long key = b.cellHash() ^ (b.getTurn() == BLACK ? 0L : SIDE);
    if (!seen.add(key)) return; // 既出(transposition)
    perDisc[discs]++;
    if (seen.size() >= CAP) { capped = true; return; }
    if (discs >= maxDiscs) return; // 葉

    Color turn = b.getTurn();
    var legal = b.findLegalMoves(turn);
    if (legal.isEmpty() || legal.get(0).isPass()) {
      // パス: 相手番へ(盤面同じ)。両者パスなら終局なので相手に手があるときのみ
      var opp = b.findLegalMoves(turn.flipped());
      if (!opp.isEmpty() && !opp.get(0).isPass())
        dfs((OurBoard) b.placed(Move.ofPass(turn)));
      return;
    }
    for (Move m : legal)
      dfs((OurBoard) b.placed(m));
  }
}
