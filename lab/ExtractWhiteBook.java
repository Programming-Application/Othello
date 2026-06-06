import static ap26.Color.*;
import ap26.Move;
import p26x42.OurBoard;
import p26x42.OurPlayer;
import java.io.*;
import java.util.*;

/**
 * 標準6×6(後手白必勝)の「白の必勝手順book」をオフライン抽出する (提出物に含めない)。
 *
 * 統一探索(solveFromStart)を1回回し、その最中に solveMin(白手番)で
 *   白勝ち確定(best<=-1) かつ 空き数 >= BOOK_REC_MIN_EMPTIES のノードを
 *   flipped().cellHash() -> bestMove で OurPlayer.bookRec に記録する。
 * 記録ノードは木の上部のみ(空き>=閾値)なので、対局時に book が切れた後の
 * 最初の自手番は空き(閾値-2)になり、live の厳密WLD(<=WLD_THRESHOLD)が勝ちを保持する。
 *
 * 実行: java -Xmx6g -cp "bin:." ExtractWhiteBook [minEmpties=23] [ttBits=27] [out=p26x42/stdwhite.book]
 */
public class ExtractWhiteBook {
  public static void main(String[] args) throws IOException {
    int minEmpties = args.length > 0 ? Integer.parseInt(args[0]) : 23;
    OurPlayer.TT_BITS = args.length > 1 ? Integer.parseInt(args[1]) : 27;
    String out = args.length > 2 ? args[2] : "p26x42/stdwhite.book";

    OurPlayer.BOOK_REC_MIN_EMPTIES = minEmpties;
    OurPlayer.bookRec = new HashMap<>(1 << 20);

    OurBoard start = new OurBoard(); // 標準盤(ブロック無し)
    System.err.printf("白book抽出: 標準盤 空き=%d  記録閾値>=%d空き  TT_BITS=%d(%,d)%n",
        start.count(NONE), minEmpties, OurPlayer.TT_BITS, 1 << OurPlayer.TT_BITS);

    OurPlayer solver = new OurPlayer(BLACK);
    OurPlayer.searchNodes = 0;
    long t0 = System.nanoTime();
    Thread prog = new Thread(() -> {
      try {
        long last = 0;
        while (true) {
          Thread.sleep(120_000);
          long n = OurPlayer.searchNodes, el = System.nanoTime() - t0;
          System.err.printf("  ...%.0fs nodes=%,d  (%.1fM/s)  book=%d%n",
              el / 1e9, n, n / (el / 1e3), OurPlayer.bookRec.size());
          last = n;
        }
      } catch (Exception e) { /* 終了 */ }
    });
    prog.setDaemon(true);
    prog.start();

    int v = solver.solveFromStart(start, -1, 1, 0); // 予算無制限・終局まで
    prog.interrupt();
    long dt = System.nanoTime() - t0;

    System.err.printf("証明完了: %.1fs  nodes=%,d  WLD=%d (%s)%n",
        dt / 1e9, OurPlayer.searchNodes, Integer.signum(v),
        v > 0 ? "黒勝ち" : v < 0 ? "白勝ち" : "引分");
    if (v >= 0) {
      System.err.println("!! WLD>=0: 標準盤が白必勝でない → 抽出中止"); return;
    }

    Map<Long, Byte> book = OurPlayer.bookRec;
    // 書き出し (int count, then count×{long key, byte move})
    File f = new File(out);
    if (f.getParentFile() != null) f.getParentFile().mkdirs();
    try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(f)))) {
      o.writeInt(book.size());
      for (Map.Entry<Long, Byte> e : book.entrySet()) { o.writeLong(e.getKey()); o.writeByte(e.getValue()); }
    }
    System.err.printf("白book: %d局面 -> %s (%,d bytes)%n", book.size(), f.getPath(), f.length());

    // 動作確認: 標準盤の黒1手目(全合法手)に対し、白応手がbookに在るか
    OurBoard root = new OurBoard();
    int hit = 0, tot = 0;
    for (Move m : root.findLegalMoves(BLACK)) {
      OurBoard afterBlack = root.placed(m);          // 黒着手後 = 白手番
      tot++;
      if (book.containsKey(afterBlack.cellHash())) hit++; // 白手番局面を黒正規化したキー
    }
    System.err.printf("検証: 黒1手目%d通り中 白応手book命中 %d/%d%n", tot, hit, tot);
  }
}
