import static ap26.Color.*;
import p26x42.OurBoard;
import p26x42.OurPlayer;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * 標準盤＋全変形盤(候補11マスから1〜3個=231通り)の必勝book を一括抽出する (提出物外)。
 *
 * 各配置を WLD で解き、勝者側(白勝ち→solveMin best<=-1 / 黒勝ち→solveMax best>=+1)の
 * 手番ノード(空き>=閾値)を OurPlayer.bookRec に side-xor 付きキーで蓄積し、全配置分を
 * 1つの proven.book に統合する。標準盤は既存 stdwhite.book を新規約に再キーして merge。
 *
 * checkpoint/resume: 各配置の完了後に proven.book と book_sweep.log を更新するので、
 * 中断しても再実行で続きから(log済み配置はスキップ)。
 *
 * 実行: java -Xmx6g -cp "bin:." ExtractAllBooks [minEmpties=23] [ttBits=27] [perConfigCapSec=14400]
 */
public class ExtractAllBooks {
  static final int[] CAND = {0, 1, 2, 3, 4, 5, 6, 12, 18, 24, 30}; // 上辺+左辺
  static String BOOK = "p26x42/resources/proven.book";
  static String LOG = "lab/book_sweep.log";
  static String curLabel = "(init)";

  public static void main(String[] args) throws IOException {
    int minE = args.length > 0 ? Integer.parseInt(args[0]) : 23;
    OurPlayer.TT_BITS = args.length > 1 ? Integer.parseInt(args[1]) : 27;
    long capSec = args.length > 2 ? Long.parseLong(args[2]) : 14400; // 1配置上限(既定4h)
    int shardIdx = args.length > 3 ? Integer.parseInt(args[3]) : 0;
    int shardCnt = args.length > 4 ? Integer.parseInt(args[4]) : 1;
    if (shardCnt > 1) { BOOK = "lab/proven_s" + shardIdx + ".book"; LOG = "lab/book_sweep_s" + shardIdx + ".log"; }

    OurPlayer.BOOK_REC_MIN_EMPTIES = minE;
    HashMap<Long, Byte> master = new HashMap<>(1 << 20); // 完走configのみを溜める累積book

    // resume: 既存 proven.book を読込み(完走分のみ入っている), 完了配置を log から回収。
    //   TIMEOUT 行は done に含めない(=次回再試行)。
    Set<String> done = new HashSet<>();
    if (Files.exists(Paths.get(BOOK))) loadInto(master, BOOK);
    List<String> logs = new ArrayList<>(List.of("lab/book_sweep.log"));
    for (int s = 0; s < 32; s++) logs.add("lab/book_sweep_s" + s + ".log");
    for (String lp : logs) {
      if (!Files.exists(Paths.get(lp))) continue;
      for (String ln : Files.readAllLines(Paths.get(lp))) {
        int i = ln.indexOf("blocks=");
        if (i >= 0 && !ln.contains("TIMEOUT")) done.add(ln.substring(i + 7, ln.indexOf(' ', i)).trim());
      }
    }
    System.err.printf("resume: 完了済み %d 配置, 既存book %d 局面%n", done.size(), master.size());

    // 標準盤(白勝ち)を既存 stdwhite.book から新規約(白=^XOR)で merge (再解き不要)。
    if (!done.contains("[]") && Files.exists(Paths.get("p26x42/stdwhite.book"))) {
      OurPlayer.bookRec = master;
      int add = mergeStdWhite("p26x42/stdwhite.book");
      logLine(String.format("blocks=[] WLD=-1(白勝ち,merge) entries+%d", add));
      done.add("[]");
      dump(master, BOOK);
      System.err.printf("標準盤 merge: +%d 局面%n", add);
    }

    List<int[]> configs = enumerate();
    System.err.printf("全 %d 代表配置を sweep (minE>=%d, TT_BITS=%d, cap=%ds/config)%n",
        configs.size(), minE, OurPlayer.TT_BITS, capSec);

    // 進捗daemon (処理中config の経過とtemp記録数)
    long[] t0h = {System.nanoTime()};
    Thread prog = new Thread(() -> {
      try { while (true) { Thread.sleep(120_000);
        var t = OurPlayer.bookRec;
        System.err.printf("  [%s] %.0fs nodes=%,d temp=%d master=%d%n", curLabel,
            (System.nanoTime() - t0h[0]) / 1e9, OurPlayer.searchNodes, t == null ? 0 : t.size(), master.size());
      } } catch (Exception e) {}
    });
    prog.setDaemon(true); prog.start();

    OurPlayer solver = new OurPlayer(BLACK);
    int wWhite = 0, wBlack = 0, draw = 0, fail = 0;
    for (int ci = 0; ci < configs.size(); ci++) {
      if (shardCnt > 1 && ci % shardCnt != shardIdx) continue; // 自シャード担当分のみ
      int[] cfg = configs.get(ci);
      String label = Arrays.toString(cfg);
      if (done.contains(label)) continue;
      curLabel = label;
      OurBoard start = new OurBoard();
      for (int k : cfg) start.set(k, BLOCK);
      HashMap<Long, Byte> temp = new HashMap<>(1 << 14); // この config 専用(完走時のみ master へ)
      OurPlayer.bookRec = temp;
      OurPlayer.searchNodes = 0;
      long s = System.nanoTime();
      int v = solver.solveFromStart(start, -1, 1, capSec * 1_000_000_000L);
      double dt = (System.nanoTime() - s) / 1e9;
      if (v == Integer.MIN_VALUE) {
        // timeout: 部分結果は信頼不可 → temp 破棄, done にも入れない(再試行可能)
        fail++;
        logLine(String.format("blocks=%s TIMEOUT nodes=%,d time=%.1fs (破棄)", label, OurPlayer.searchNodes, dt));
        System.err.printf("TIMEOUT %s %.1fs (temp破棄)%n", label, dt);
        continue;
      }
      String res = v < 0 ? "WLD=-1(白勝ち)" : v > 0 ? "WLD=+1(黒勝ち)" : "WLD=0(引分)";
      if (v < 0) wWhite++; else if (v > 0) wBlack++; else draw++;
      master.putAll(temp); // 完走 → master へ統合(配置別にcellHashが異なり衝突しない)
      dump(master, BOOK);  // checkpoint
      logLine(String.format("blocks=%s %s nodes=%,d time=%.1fs +%d master=%d",
          label, res, OurPlayer.searchNodes, dt, temp.size(), master.size()));
      System.err.printf("done %s %s %.1fs (+%d master=%d)%n", label, res, dt, temp.size(), master.size());
    }
    System.err.printf("=== sweep完了: 白勝ち%d 黒勝ち%d 引分%d timeout%d  book=%d局面 (%s) ===%n",
        wWhite, wBlack, draw, fail, master.size(), BOOK);
  }

  /** 候補から size 1..3 の全組合せのうち、transpose対称の代表のみ (~120)。*/
  static List<int[]> enumerate() {
    int n = CAND.length;
    List<int[]> all = new ArrayList<>();
    for (int a = 0; a < n; a++) {
      all.add(new int[]{CAND[a]});
      for (int b = a + 1; b < n; b++) {
        all.add(new int[]{CAND[a], CAND[b]});
        for (int c = b + 1; c < n; c++) all.add(new int[]{CAND[a], CAND[b], CAND[c]});
      }
    }
    Set<String> seen = new HashSet<>();
    List<int[]> out = new ArrayList<>();
    for (int[] cfg : all) if (seen.add(canonical(cfg))) out.add(cfg); // 各transpose軌道の先頭のみ
    return out;
  }

  /** 配置とそのtransposeの辞書順小さい方を正準形キーに。*/
  static String canonical(int[] cfg) {
    int[] t = new int[cfg.length];
    for (int i = 0; i < cfg.length; i++) t[i] = OurBoard.transposeIndex(cfg[i]);
    Arrays.sort(t);
    String s1 = Arrays.toString(cfg), s2 = Arrays.toString(t);
    return s1.compareTo(s2) <= 0 ? s1 : s2;
  }

  /** 旧 stdwhite.book(白=生cellHash) を新規約(白=cellHash^XOR)に再キーして bookRec へ。*/
  static int mergeStdWhite(String path) throws IOException {
    int add = 0;
    try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(path)))) {
      int n = in.readInt();
      for (int i = 0; i < n; i++) {
        long k = in.readLong(); byte mv = in.readByte();
        if (OurPlayer.bookRec.putIfAbsent(k ^ OurPlayer.BOOK_SIDE_XOR, mv) == null) add++;
      }
    }
    return add;
  }

  static void loadInto(Map<Long, Byte> m, String path) throws IOException {
    try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(path)))) {
      int n = in.readInt();
      for (int i = 0; i < n; i++) { long k = in.readLong(); byte v = in.readByte(); m.put(k, v); }
    }
  }

  static void dump(Map<Long, Byte> m, String path) throws IOException {
    File tmp = new File(path + ".tmp");
    try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tmp)))) {
      o.writeInt(m.size());
      for (Map.Entry<Long, Byte> e : m.entrySet()) { o.writeLong(e.getKey()); o.writeByte(e.getValue()); }
    }
    Files.move(tmp.toPath(), Paths.get(path), StandardCopyOption.REPLACE_EXISTING); // 原子的置換
  }

  static void logLine(String s) throws IOException {
    try (FileWriter w = new FileWriter(LOG, true)) { w.write(s + "\n"); }
  }
}
