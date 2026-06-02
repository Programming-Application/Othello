package p26x42;

import static ap26.Color.*;

import java.util.*;

import ap26.*;

/**
 * Board のビットボード実装 (Step 2)。
 *
 * <p>盤面を 3 本の long ({@code black}/{@code white}/{@code block}, ビット k = マス k) で表す。
 * 合法手生成・裏返しを 8 方向のシフト&マスクで行い、配列実装の {@code isLegalMove}
 * (探索の最大ボトルネック ~64%) と {@code Color[]} 確保を排除する。
 *
 * <p>BLOCK は black/white いずれにも含まれないため、ランの伸長が BLOCK で自然に止まる
 * (壁として機能)。Board インタフェース・不変性・Zobrist 増分ハッシュ {@code h} を維持。
 */
public class OurBoard implements Board, Cloneable {

  static final long FULL = (1L << LENGTH) - 1;
  static final long COL0, COL5; // col==0 / col==5 のマス集合 (横方向シフトの回り込み防止)
  static {
    long c0 = 0, c5 = 0;
    for (int r = 0; r < SIZE; r++) {
      c0 |= 1L << (SIZE * r);
      c5 |= 1L << (SIZE * r + SIZE - 1);
    }
    COL0 = c0;
    COL5 = c5;
  }

  /** フロンティア判定(MyEval)用に各マスの8方向隣接列を保持。*/
  static final int[][][] LINES = new int[LENGTH][][];
  static {
    for (int k = 0; k < LENGTH; k++) {
      int[][] dirs = new int[8][];
      for (int dir = 0; dir < 8; dir++) {
        var line = Move.line(k, dir);
        int[] a = new int[line.size()];
        for (int i = 0; i < a.length; i++)
          a[i] = line.get(i);
        dirs[dir] = a;
      }
      LINES[k] = dirs;
    }
  }

  /** Zobrist 乱数表。Z[k][0]=BLACK,[1]=WHITE,[2]=BLOCK (手番は OurPlayer 側で XOR)。*/
  static final long[][] Z = new long[LENGTH][3];
  static {
    java.util.Random r = new java.util.Random(0x06022026L);
    for (int k = 0; k < LENGTH; k++)
      for (int j = 0; j < 3; j++)
        Z[k][j] = r.nextLong();
  }

  long black, white, blockMask;
  Move move = Move.ofPass(NONE);
  long h = 0L; // 盤面の Zobrist ハッシュ (増分更新)

  public OurBoard() {
    init();
  }

  OurBoard(long black, long white, long blockMask, Move move, long h) {
    this.black = black;
    this.white = white;
    this.blockMask = blockMask;
    this.move = move;
    this.h = h;
  }

  public OurBoard clone() {
    return new OurBoard(black, white, blockMask, move, h);
  }

  void init() {
    set(Move.parseIndex("c3"), BLACK);
    set(Move.parseIndex("d4"), BLACK);
    set(Move.parseIndex("d3"), WHITE);
    set(Move.parseIndex("c4"), WHITE);
  }

  // ===== 1 方向シフト (premask で回り込み防止) =====
  static long shift(long x, int s, long premask) {
    x &= premask;
    return s > 0 ? (x << s) & FULL : (x >>> -s);
  }

  // 8 方向: {シフト量, premask}
  static final int[] DS = {1, -1, SIZE, -SIZE, SIZE + 1, SIZE - 1, -(SIZE - 1), -(SIZE + 1)};
  static final long[] DM = {~COL5, ~COL0, FULL, FULL, ~COL5, ~COL0, ~COL5, ~COL0};

  long empty() {
    return ~(black | white | blockMask) & FULL;
  }

  /** 色 color の合法手ビット集合。*/
  long legalBits(Color color) {
    long p = (color == BLACK) ? black : white;
    long o = (color == BLACK) ? white : black;
    long e = empty();
    long moves = 0;
    for (int d = 0; d < 8; d++) {
      int s = DS[d];
      long m = DM[d];
      long t = shift(p, s, m) & o;
      t |= shift(t, s, m) & o;
      t |= shift(t, s, m) & o;
      t |= shift(t, s, m) & o;
      t |= shift(t, s, m) & o; // 6マス幅: 相手連続は最大4 → 余裕を見て4回伸長
      moves |= shift(t, s, m) & e;
    }
    return moves;
  }

  /** マス k に color を置いたとき裏返るビット集合。*/
  long flipsAt(int k, Color color) {
    long mv = 1L << k;
    long p = (color == BLACK) ? black : white;
    long o = (color == BLACK) ? white : black;
    long flips = 0;
    for (int d = 0; d < 8; d++) {
      int s = DS[d];
      long m = DM[d];
      long run = 0;
      long cur = shift(mv, s, m);
      for (int i = 0; i < 5 && (cur & o) != 0; i++) {
        run |= cur;
        cur = shift(cur, s, m);
      }
      if ((cur & p) != 0)
        flips |= run; // 自石で挟めた方向のみ確定
    }
    return flips;
  }

  public Color get(int k) {
    long bit = 1L << k;
    if ((black & bit) != 0) return BLACK;
    if ((white & bit) != 0) return WHITE;
    if ((blockMask & bit) != 0) return BLOCK;
    return NONE;
  }

  public Move getMove() {
    return this.move;
  }

  public Color getTurn() {
    return this.move.isNone() ? BLACK : this.move.getColor().flipped();
  }

  public void set(int k, Color color) {
    long bit = 1L << k;
    // 旧色を h から除去
    if ((black & bit) != 0) h ^= Z[k][0];
    else if ((white & bit) != 0) h ^= Z[k][1];
    else if ((blockMask & bit) != 0) h ^= Z[k][2];
    black &= ~bit;
    white &= ~bit;
    blockMask &= ~bit;
    if (color == BLACK) { black |= bit; h ^= Z[k][0]; }
    else if (color == WHITE) { white |= bit; h ^= Z[k][1]; }
    else if (color == BLOCK) { blockMask |= bit; h ^= Z[k][2]; }
    // NONE: 何もしない
  }

  public boolean equals(Object otherObj) {
    if (otherObj instanceof OurBoard) {
      var o = (OurBoard) otherObj;
      return black == o.black && white == o.white && blockMask == o.blockMask;
    }
    return false;
  }

  public String toString() {
    return OurBoardFormatter.format(this);
  }

  public int count(Color color) {
    if (color == BLACK) return Long.bitCount(black);
    if (color == WHITE) return Long.bitCount(white);
    if (color == BLOCK) return Long.bitCount(blockMask);
    return LENGTH - Long.bitCount(black | white | blockMask); // NONE
  }

  public boolean isEnd() {
    return legalBits(BLACK) == 0 && legalBits(WHITE) == 0;
  }

  public Color winner() {
    var v = score();
    if (isEnd() == false || v == 0)
      return NONE;
    return v > 0 ? BLACK : WHITE;
  }

  public void foul(Color color) {
    var winner = color.flipped();
    if (winner == BLACK) { black = FULL; white = 0; }
    else { white = FULL; black = 0; }
    blockMask = 0;
    recomputeHash();
  }

  public int score() {
    int bs = Long.bitCount(black), ws = Long.bitCount(white);
    int ns = LENGTH - bs - ws - Long.bitCount(blockMask);
    int score = bs - ws;
    if (bs == 0 || ws == 0)
      score += Integer.signum(score) * ns;
    return score;
  }

  boolean hasLegalMove(Color color) {
    return legalBits(color) != 0;
  }

  boolean isLegalMove(int k, Color color) {
    return (legalBits(color) & (1L << k)) != 0;
  }

  public List<Move> findLegalMoves(Color color) {
    var ks = findLegalIndexes(color);
    var moves = new ArrayList<Move>(ks.size());
    for (int k : ks)
      moves.add(new Move(k, color));
    return moves;
  }

  List<Integer> findLegalIndexes(Color color) {
    var moves = findNoPassLegalIndexes(color);
    if (moves.size() == 0)
      moves.add(Move.PASS);
    return moves;
  }

  List<Integer> findNoPassLegalIndexes(Color color) {
    var moves = new ArrayList<Integer>();
    long m = legalBits(color);
    while (m != 0) {
      moves.add(Long.numberOfTrailingZeros(m));
      m &= m - 1;
    }
    return moves;
  }

  /** 合法手のマス番号を out に詰めて個数を返す (アロケーションなし、探索ホットパス用)。*/
  int genLegal(Color color, int[] out) {
    long m = legalBits(color);
    int n = 0;
    while (m != 0) {
      out[n++] = Long.numberOfTrailingZeros(m);
      m &= m - 1;
    }
    return n;
  }

  /** マス k に color を着手した新盤面 (Move 非生成の探索専用版)。*/
  OurBoard placedIndex(int k, Color color) {
    long flips = flipsAt(k, color);
    long mv = 1L << k;
    long nb, nw;
    if (color == BLACK) { nb = black | flips | mv; nw = white & ~flips; }
    else { nw = white | flips | mv; nb = black & ~flips; }
    long nh = updatedHash(k, color, flips);
    return new OurBoard(nb, nw, blockMask, this.move, nh);
  }

  public OurBoard placed(Move move) {
    if (move.isPass() || move.isNone()) {
      var b = clone();
      b.move = move;
      return b;
    }
    int k = move.getIndex();
    Color color = move.getColor();
    long flips = flipsAt(k, color);
    long mv = 1L << k;
    long nb, nw;
    if (color == BLACK) { nb = black | flips | mv; nw = white & ~flips; }
    else { nw = white | flips | mv; nb = black & ~flips; }
    long nh = updatedHash(k, color, flips);
    return new OurBoard(nb, nw, blockMask, move, nh);
  }

  /** 着手 (k=空→color) と裏返し (相手→color) を h に増分反映した値を返す。*/
  private long updatedHash(int k, Color color, long flips) {
    int me = (color == BLACK) ? 0 : 1, opp = (color == BLACK) ? 1 : 0;
    long nh = h ^ Z[k][me]; // 空(寄与0)→自色
    long f = flips;
    while (f != 0) {
      int idx = Long.numberOfTrailingZeros(f);
      f &= f - 1;
      nh ^= Z[idx][opp] ^ Z[idx][me]; // 相手色→自色
    }
    return nh;
  }

  public OurBoard flipped() {
    var nb = new OurBoard(white, black, blockMask, this.move.flipped(), 0); // 黒白入替
    nb.recomputeHash();
    return nb;
  }

  /** h を盤面から作り直す。*/
  void recomputeHash() {
    long x = 0;
    long b = black;
    while (b != 0) { int k = Long.numberOfTrailingZeros(b); b &= b - 1; x ^= Z[k][0]; }
    long w = white;
    while (w != 0) { int k = Long.numberOfTrailingZeros(w); w &= w - 1; x ^= Z[k][1]; }
    long bl = blockMask;
    while (bl != 0) { int k = Long.numberOfTrailingZeros(bl); bl &= bl - 1; x ^= Z[k][2]; }
    this.h = x;
  }
}
