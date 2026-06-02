import static ap26.Color.*;
import p26x42.OurBoard;
import p26x42.OurPlayer;

/**
 * オフライン「開始局面からの勝者証明(WLD)」の feasibility 計測 (提出物に含めない)。
 * 残りの開幕手も含めて初期局面の勝敗を厳密証明できるか(＝弱解)を、配置別に時間計測する。
 * hint.md §8: 軽い配置(3-block, 空き29 → 開幕5手)から。
 *
 * 実行: java -cp "bin:." OfflineSolve [blocks(カンマ区切り)] [budgetSec]
 */
public class OfflineSolve {
  public static void main(String[] args) {
    int[] blocks = parse(args.length > 0 ? args[0] : "0,5,30"); // 既定: a1,f1,a6 (3-block)
    long budgetSec = args.length > 1 ? Long.parseLong(args[1]) : 600;

    OurBoard start = new OurBoard();
    for (int k : blocks) start.set(k, BLOCK);
    int empties = start.count(NONE);
    System.err.printf("config blocks=%s  empties=%d  budget=%ds%n",
        java.util.Arrays.toString(blocks), empties, budgetSec);

    OurPlayer solver = new OurPlayer(BLACK);
    OurPlayer.searchNodes = 0;
    long t0 = System.nanoTime();
    int v = solver.solveFromStart(start, -1, 1, budgetSec * 1_000_000_000L); // WLD
    long dt = System.nanoTime() - t0;

    if (v == Integer.MIN_VALUE) {
      System.err.printf("%ds 以内に勝者証明できず (nodes=%,d まで)%n", budgetSec, OurPlayer.searchNodes);
    } else {
      System.err.printf("勝者証明 完了: %.1fs  nodes=%,d  WLD=%d (%s)%n",
          dt / 1e9, OurPlayer.searchNodes, Integer.signum(v),
          v > 0 ? "先手(黒)勝ち" : v < 0 ? "後手(白)勝ち" : "引分");
    }
  }

  static int[] parse(String s) {
    if (s.isEmpty()) return new int[0];
    String[] ps = s.split(",");
    int[] r = new int[ps.length];
    for (int i = 0; i < ps.length; i++) r[i] = Integer.parseInt(ps[i].trim());
    return r;
  }
}
