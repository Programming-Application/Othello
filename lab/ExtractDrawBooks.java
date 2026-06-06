import static ap26.Color.*;
import p26x42.OurBoard;
import p26x42.OurPlayer;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * 引分config(WLD=0)の「引分保持book」を既存 proven.book に追記する (提出物外)。
 * BOOK_REC_DRAW=true で 黒best>=0/白best<=0(=引分以上)の手番(空き>=23)を両色記録。
 * 引分configはbook_sweep.logの "WLD=0" 行から取得。各configを一時mapへ→完走時のみmerge。
 *
 * 実行: java -Xmx6g -cp "bin:." ExtractDrawBooks [ttBits=27]
 */
public class ExtractDrawBooks {
  static final String BOOK = "p26x42/resources/proven.book";

  public static void main(String[] args) throws IOException {
    OurPlayer.TT_BITS = args.length > 0 ? Integer.parseInt(args[0]) : 27;
    OurPlayer.BOOK_REC_MIN_EMPTIES = 23;
    OurPlayer.BOOK_REC_DRAW = true; // 引分以上を記録

    HashMap<Long, Byte> master = new HashMap<>(1 << 20);
    loadInto(master, BOOK);
    int base = master.size();
    System.err.printf("既存 proven.book: %d 局面%n", base);

    // 引分config取得
    List<int[]> draws = new ArrayList<>();
    for (String ln : Files.readAllLines(Paths.get("lab/book_sweep.log"))) {
      int i = ln.indexOf("blocks="); if (i < 0 || !ln.contains("WLD=0")) continue;
      draws.add(parse(ln.substring(i + 7, ln.indexOf(']', i) + 1)));
    }
    System.err.printf("引分 %d config を引分保持book化%n", draws.size());

    long[] t0 = {System.nanoTime()}; String[] cur = {"(init)"};
    Thread prog = new Thread(() -> { try { while (true) { Thread.sleep(120_000);
      var t = OurPlayer.bookRec;
      System.err.printf("  [%s] %.0fs nodes=%,d temp=%d%n", cur[0],
          (System.nanoTime() - t0[0]) / 1e9, OurPlayer.searchNodes, t == null ? 0 : t.size());
    } } catch (Exception e) {} });
    prog.setDaemon(true); prog.start();

    OurPlayer solver = new OurPlayer(BLACK);
    for (int[] cfg : draws) {
      cur[0] = Arrays.toString(cfg);
      OurBoard start = new OurBoard();
      for (int k : cfg) start.set(k, BLOCK);
      HashMap<Long, Byte> temp = new HashMap<>(1 << 15);
      OurPlayer.bookRec = temp;
      OurPlayer.searchNodes = 0;
      long s = System.nanoTime();
      int v = solver.solveFromStart(start, -1, 1, 0); // 上限なし
      double dt = (System.nanoTime() - s) / 1e9;
      if (v != 0) { System.err.printf("!! %s WLD=%d (引分でない,skip)%n", cur[0], v); continue; }
      master.putAll(temp);
      dump(master, BOOK);
      System.err.printf("done %s %.1fs +%d (master=%d)%n", cur[0], dt, temp.size(), master.size());
    }
    System.err.printf("=== 引分book追記完了: %d → %d 局面 (+%d) %s ===%n",
        base, master.size(), master.size() - base, BOOK);
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
    Files.move(tmp.toPath(), Paths.get(path), StandardCopyOption.REPLACE_EXISTING);
  }

  static int[] parse(String s) {
    s = s.replaceAll("[\\[\\] ]", "");
    if (s.isEmpty()) return new int[0];
    String[] p = s.split(",");
    int[] r = new int[p.length];
    for (int i = 0; i < p.length; i++) r[i] = Integer.parseInt(p[i]);
    return r;
  }
}
