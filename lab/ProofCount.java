import static ap26.Color.*;
import ap26.*;
import java.util.*;
import p26x42.OurBoard;

/**
 * 弱解の「証明木」のサイズを実測する (提出物に含めない / B の feasibility 検討)。
 *
 * 8x8論文の序盤設計を模倣: WLD(勝敗のみ, 窓[-1,+1]) の α-β を開始局面から走らせ、
 * eval 誘導の着手順序で「自分手番は最善1手で fail-high → 1手のみ展開」(証明木最小性) を狙う。
 * 葉は「空き <= LEAF_E (=20, ライブソルバ担当)」で、そこを浅い探索のサインで近似評価し、
 * 「解くべき異なる葉局面の数」を数える。これ×(20空き厳密解時間)が B のオフライン費用。
 *
 * 注: 葉サインは浅い探索の近似なので、得られる葉数は「現実的なオーダーの推定」。
 *
 * 実行: java -cp "bin:." ProofCount [leafEmpties] [leafDepth] [capLeaves]
 */
public class ProofCount {
  static final long SIDE = 0x9E3779B97F4A7C15L;
  static int LEAF_E = 20;     // この空き数以下を葉(ライブソルバ担当)とする
  static int LEAF_DEPTH = 6;  // 葉サイン近似の浅い探索深さ
  static long CAP = 3_000_000L;

  static final int[] W = {
      29, 10, 10, 10, 10, 29, 10, -5, -3, -3, -5, 10, 10, -3, 1, 1, -3, 10,
      10, -3, 1, 1, -3, 10, 10, -5, -3, -3, -5, 10, 29, 10, 10, 10, 10, 29};

  static HashSet<Long> leaves = new HashSet<>(1 << 20);
  static HashMap<Long, Integer> leafSign = new HashMap<>(1 << 20);
  static HashMap<Long, int[]> tt = new HashMap<>(1 << 20); // 内部節のメモ化 key->{val,flag}
  static long nodes = 0, proofLeafVisits = 0;
  static long[] perEmptyNodes = new long[40];
  static boolean capped = false;

  public static void main(String[] args) {
    if (args.length > 0) LEAF_E = Integer.parseInt(args[0]);
    if (args.length > 1) LEAF_DEPTH = Integer.parseInt(args[1]);
    if (args.length > 2) CAP = Long.parseLong(args[2]);

    OurBoard start = new OurBoard();
    System.err.printf("標準盤・WLD証明木: 葉=空き<=%d, 葉サイン=浅さ%d, cap=%d%n", LEAF_E, LEAF_DEPTH, CAP);
    long t0 = System.nanoTime();
    int v = wld(start, BLACK, -1, 1);
    double sec = (System.nanoTime() - t0) / 1e9;

    System.err.println("--- 証明木ノード数(空きマス別) ---");
    long tot = 0;
    for (int e = 39; e >= 0; e--) if (perEmptyNodes[e] > 0) {
      System.err.printf("  空き%2d: %,d%n", e, perEmptyNodes[e]); tot += perEmptyNodes[e];
    }
    System.err.println("------------------------------------------------------------");
    System.err.printf("証明木 総ノード=%,d  葉到達(延べ)=%,d  解くべき異なる葉=%,d  %s (%.1fs)%n",
        tot, proofLeafVisits, leaves.size(), capped ? "[CAP打切り]" : "", sec);
    System.err.printf("根のWLD = %d (%s)%n", v, v > 0 ? "先手勝ち寄り" : v < 0 ? "後手勝ち寄り" : "引分寄り");
    double solveSec = 4.5;
    System.err.printf("試算: 異なる葉 %,d × %.1fs = %.1f コア時間/配置 (共有TTで短縮見込)%n",
        leaves.size(), solveSec, leaves.size() * solveSec / 3600.0);
  }

  /** WLD α-β。turn 手番の局面 b を窓[alpha,beta]で評価({-1,0,1})。証明木を数える。*/
  static int wld(OurBoard b, Color turn, int alpha, int beta) {
    if (capped) return 0;
    nodes++;
    int e = b.count(NONE);
    perEmptyNodes[e]++;
    if (b.isEnd()) return clamp(Integer.signum(scoreFor(b, turn)));
    if (e <= LEAF_E) {
      proofLeafVisits++;
      long key = b.cellHash() ^ (turn == BLACK ? 0L : SIDE);
      leaves.add(key);
      if (leaves.size() >= CAP) capped = true;
      return leafSignCached(b, turn, key);
    }
    // 置換表(メモ化): 手順前後の同一局面を再探索しない
    long key = b.cellHash() ^ (turn == BLACK ? 0L : SIDE);
    int[] ent = tt.get(key);
    if (ent != null) {
      int v = ent[0], fl = ent[1];
      if (fl == 0) return v;
      if (fl == 1 && v >= beta) return v;   // lower bound
      if (fl == 2 && v <= alpha) return v;  // upper bound
    }
    int alpha0 = alpha;

    var legal = b.findLegalMoves(turn);
    if (legal.isEmpty() || legal.get(0).isPass())
      return -wld(b, turn.flipped(), -beta, -alpha); // パス (negamax)
    int[] ord = ordered(b, turn, legal);
    int best = -2;
    for (int k : ord) {
      OurBoard c = (OurBoard) b.placed(new Move(k, turn));
      int v = -wld(c, turn.flipped(), -beta, -alpha);
      if (v > best) best = v;
      if (best > alpha) alpha = best;
      if (alpha >= beta) break; // cutoff (= 自手番なら最善1手で打切り)
    }
    int fl = best <= alpha0 ? 2 : best >= beta ? 1 : 0;
    tt.put(key, new int[] {best, fl});
    return best;
  }

  /** 葉の予測サイン {-1,0,1} を浅い探索で求める(キャッシュ)。turn 手番視点。*/
  static int leafSignCached(OurBoard b, Color turn, long key) {
    Integer s = leafSign.get(key);
    if (s != null) return s;
    int v = clamp(Integer.signum(shallow(b, turn, LEAF_DEPTH)));
    leafSign.put(key, v);
    return v;
  }

  /** 浅い negamax (生eval)。turn 視点の評価値。*/
  static int shallow(OurBoard b, Color turn, int depth) {
    if (b.isEnd()) return 1_000_000 * scoreFor(b, turn);
    if (depth == 0) return evalFor(b, turn);
    var legal = b.findLegalMoves(turn);
    if (legal.isEmpty() || legal.get(0).isPass())
      return -shallow(b, turn.flipped(), depth - 1);
    int best = Integer.MIN_VALUE + 1;
    for (Move m : legal) {
      int v = -shallow((OurBoard) b.placed(m), turn.flipped(), depth - 1);
      if (v > best) best = v;
    }
    return best;
  }

  /** 着手を「結果局面の静的eval(指す側視点)」降順に (eval誘導順序). */
  static int[] ordered(OurBoard b, Color turn, List<Move> legal) {
    int n = legal.size();
    int[] ks = new int[n];
    int[] sc = new int[n];
    for (int i = 0; i < n; i++) {
      ks[i] = legal.get(i).getIndex();
      sc[i] = evalFor((OurBoard) b.placed(legal.get(i)), turn); // 指した後の自分視点
    }
    for (int i = 1; i < n; i++) {
      int k = ks[i], s = sc[i], j = i - 1;
      while (j >= 0 && sc[j] < s) { ks[j + 1] = ks[j]; sc[j + 1] = sc[j]; j--; }
      ks[j + 1] = k; sc[j + 1] = s;
    }
    return ks;
  }

  static int evalFor(OurBoard b, Color me) {
    int pos = 0, mob = 0;
    Color opp = me.flipped();
    for (int k = 0; k < 36; k++) {
      Color c = b.get(k);
      if (c == me) pos += W[k]; else if (c == opp) pos -= W[k];
    }
    mob = noPassCount(b, me) - noPassCount(b, opp);
    return 10 * pos + 31 * mob;
  }

  static int noPassCount(OurBoard b, Color c) {
    var l = b.findLegalMoves(c);
    if (l.isEmpty() || l.get(0).isPass()) return 0;
    return l.size();
  }

  static int scoreFor(OurBoard b, Color me) { return me == BLACK ? b.score() : -b.score(); }
  static int signScore(OurBoard b) { return Integer.signum(b.score()); } // BLACK視点
  static int clamp(int v) { return v > 0 ? 1 : v < 0 ? -1 : 0; }
}
