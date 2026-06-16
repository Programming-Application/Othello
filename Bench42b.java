import ap26.*;
import static ap26.Color.*;
import java.util.*;
import p26x42.OurBoard;
import p26x42.OurPlayer;

/**
 * 高速化要素の寄与を分離計測する (提出物外)。
 *  (A) 中盤: 置換表(TT)+PVS の on/off を固定深さで比較 (ノード数=枝刈り効率)。
 *  (B) 終盤: fastest-first 着手順序の on/off を完全読みで比較。
 */
public class Bench42b {

  /** 標準盤から first-legal を指し続け、BLACK 手番かつ空き<=target の局面を作る。*/
  static OurBoard reach(int target) {
    OurBoard b = new OurBoard();
    while (true) {
      Color turn = b.getTurn();
      if (turn == BLACK && b.count(NONE) <= target && !b.isEnd()) return b;
      List<Move> ms = b.findLegalMoves(turn);
      b = b.placed(ms.get(0)); // pass も Move として処理される
      if (b.isEnd()) return reachRandom(target); // 念のため
    }
  }
  static OurBoard reachRandom(int target) { return new OurBoard(); }

  public static void main(String[] args) {
    // JIT ウォームアップ (計測を代表値にする)
    { OurPlayer w = new OurPlayer(BLACK); OurBoard wb = new OurBoard();
      w.searchValue(wb, 10, true); w.searchValue(wb, 10, false);
      OurPlayer.FF_MIN = 7; new OurPlayer(BLACK).benchSolve(reach(15)); }

    // ---------- (A) TT + PVS ----------
    int depth = 13;
    OurBoard mid = new OurBoard(); // 標準盤初期 (BLACK 手番, 32 空き)
    System.out.println("=== (A) 中盤 TT+PVS 寄与 : 標準盤初期局面, 固定深さ " + depth + " ===");
    long[] r1 = measureMid(mid, depth, false); // 素の α-β (move ordering のみ)
    long[] r2 = measureMid(mid, depth, true);  // + TT + PVS
    report("α-β のみ (TT/PVS なし)", r1);
    report("α-β + TT + PVS       ", r2);
    System.out.printf(Locale.US, "  → ノード数 %.2fx 削減, 時間 %.2fx 短縮%n%n",
        (double) r1[0] / r2[0], (double) r1[1] / r2[1]);

    // ---------- (B) fastest-first ----------
    int empties = 19;
    System.out.println("=== (B) 終盤 fastest-first 寄与 : 空き " + empties + " 完全読み ===");
    long[] f1 = measureEnd(empties, 999); // FF 無効 (静的順序のみ)
    long[] f2 = measureEnd(empties, 7);   // FF 有効
    report("静的順序のみ (FF なし)", f1);
    report("fastest-first 有効   ", f2);
    System.out.printf(Locale.US, "  → ノード数 %.2fx 削減, 時間 %.2fx 短縮%n",
        (double) f1[0] / f2[0], (double) f1[1] / f2[1]);
  }

  /** {nodes, nanos} を返す。毎回新インスタンス (TT クリア) で公平に。*/
  static long[] measureMid(OurBoard root, int depth, boolean ttpvs) {
    OurPlayer p = new OurPlayer(BLACK);
    OurPlayer.searchNodes = 0;
    long t = System.nanoTime();
    p.searchValue(root, depth, ttpvs);
    long dt = System.nanoTime() - t;
    return new long[] {OurPlayer.searchNodes, dt};
  }

  static long[] measureEnd(int empties, int ffMin) {
    OurBoard root = reach(empties);
    OurPlayer.FF_MIN = ffMin;
    OurPlayer p = new OurPlayer(BLACK);
    OurPlayer.searchNodes = 0;
    long t = System.nanoTime();
    p.benchSolve(root);
    long dt = System.nanoTime() - t;
    return new long[] {OurPlayer.searchNodes, dt};
  }

  static void report(String label, long[] r) {
    System.out.printf(Locale.US, "  %-22s : %,15d nodes   %8.3f s   %,.0f nps%n",
        label, r[0], r[1] / 1e9, r[0] / (r[1] / 1e9));
  }
}
