package p26x42;

import static ap26.Color.*;

import java.util.*;

import ap26.*;

/**
 * Board の高速実装 (Phase 1)。
 *
 * <p>p26x00 の素朴版を、探索のホットパスから Stream・autoboxing・都度の
 * ArrayList/Move.line 生成を排除して高速化したもの。Board インタフェースの
 * 意味と不変性 (placed/flipped/clone は新インスタンスを返す) は維持する。
 *
 * <h2>高速化の要点</h2>
 * <ul>
 *   <li>各マス k の 8 方向の走査マス列を静的テーブル {@link #LINES} に事前計算</li>
 *   <li>合法判定・裏返し・石数カウントを primitive ループ化 (アロケーションなし)</li>
 * </ul>
 */
public class OurBoard implements Board, Cloneable {

  /** LINES[k][dir] = マス k から方向 dir へ外側に伸びるマス番号の配列 (盤端まで)。*/
  static final int[][][] LINES = new int[LENGTH][][];
  static {
    for (int k = 0; k < LENGTH; k++) {
      int[][] dirs = new int[8][];
      for (int dir = 0; dir < 8; dir++) {
        var line = Move.line(k, dir); // List<Integer>（初期化時のみ）
        int[] a = new int[line.size()];
        for (int i = 0; i < a.length; i++)
          a[i] = line.get(i);
        dirs[dir] = a;
      }
      LINES[k] = dirs;
    }
  }

  Color board[];
  Move move = Move.ofPass(NONE);

  public OurBoard() {
    this.board = new Color[LENGTH];
    Arrays.fill(this.board, NONE);
    init();
  }

  OurBoard(Color board[], Move move) {
    this.board = Arrays.copyOf(board, board.length);
    this.move = move;
  }

  public OurBoard clone() {
    return new OurBoard(this.board, this.move);
  }

  void init() {
    set(Move.parseIndex("c3"), BLACK);
    set(Move.parseIndex("d4"), BLACK);
    set(Move.parseIndex("d3"), WHITE);
    set(Move.parseIndex("c4"), WHITE);
  }

  public Color get(int k) {
    return this.board[k];
  }

  public Move getMove() {
    return this.move;
  }

  public Color getTurn() {
    return this.move.isNone() ? BLACK : this.move.getColor().flipped();
  }

  public void set(int k, Color color) {
    this.board[k] = color;
  }

  public boolean equals(Object otherObj) {
    if (otherObj instanceof OurBoard) {
      var other = (OurBoard) otherObj;
      return Arrays.equals(this.board, other.board);
    }
    return false;
  }

  public String toString() {
    return OurBoardFormatter.format(this);
  }

  public int count(Color color) {
    int n = 0;
    for (int k = 0; k < LENGTH; k++)
      if (this.board[k] == color)
        n++;
    return n;
  }

  public boolean isEnd() {
    return !hasLegalMove(BLACK) && !hasLegalMove(WHITE);
  }

  public Color winner() {
    var v = score();
    if (isEnd() == false || v == 0)
      return NONE;
    return v > 0 ? BLACK : WHITE;
  }

  public void foul(Color color) {
    var winner = color.flipped();
    for (int k = 0; k < LENGTH; k++)
      this.board[k] = winner;
  }

  public int score() {
    int bs = 0, ws = 0, ns = 0;
    for (int k = 0; k < LENGTH; k++) {
      var c = this.board[k];
      if (c == BLACK)
        bs++;
      else if (c == WHITE)
        ws++;
      else if (c == NONE)
        ns++;
    }
    int score = bs - ws;
    if (bs == 0 || ws == 0)
      score += Integer.signum(score) * ns;
    return score;
  }

  /** ある色に少なくとも 1 つ合法手があるか (アロケーションなし)。*/
  boolean hasLegalMove(Color color) {
    for (int k = 0; k < LENGTH; k++) {
      if (this.board[k] != NONE)
        continue;
      if (isLegalMove(k, color))
        return true;
    }
    return false;
  }

  /** マス k に color を置けるか (1 方向でも挟めれば合法)。アロケーションなし。*/
  boolean isLegalMove(int k, Color color) {
    if (this.board[k] != NONE)
      return false;
    int[][] dirs = LINES[k];
    for (int dir = 0; dir < 8; dir++) {
      int[] line = dirs[dir];
      boolean seenOpp = false;
      for (int i = 0; i < line.length; i++) {
        var c = this.board[line[i]];
        if (c == NONE || c == BLOCK)
          break;
        if (c == color) {
          if (seenOpp)
            return true;
          break;
        }
        seenOpp = true; // 相手石
      }
    }
    return false;
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
    for (int k = 0; k < LENGTH; k++) {
      if (this.board[k] != NONE)
        continue;
      if (isLegalMove(k, color))
        moves.add(k);
    }
    return moves;
  }

  /**
   * 合法手のマス番号を {@code out} に詰めて個数を返す (PASS は含めない、アロケーションなし)。
   * 探索のホットパス専用。{@code out} は十分大きいこと (>= 36)。
   */
  int genLegal(Color color, int[] out) {
    int n = 0;
    for (int k = 0; k < LENGTH; k++) {
      if (this.board[k] != NONE)
        continue;
      if (isLegalMove(k, color))
        out[n++] = k;
    }
    return n;
  }

  /**
   * マス k に color を着手した新盤面を返す (Move を生成しない探索専用版)。
   * {@code move} フィールドは更新しない (探索中は参照しないため)。
   */
  OurBoard placedIndex(int k, Color color) {
    var b = clone();
    int[][] dirs = LINES[k];
    for (int dir = 0; dir < 8; dir++) {
      int[] line = dirs[dir];
      int run = 0;
      boolean closed = false;
      for (int i = 0; i < line.length; i++) {
        var c = b.board[line[i]];
        if (c == NONE || c == BLOCK)
          break;
        if (c == color) {
          closed = (run > 0);
          break;
        }
        run++;
      }
      if (closed) {
        for (int i = 0; i < run; i++)
          b.board[line[i]] = color;
      }
    }
    b.board[k] = color;
    return b;
  }

  public OurBoard placed(Move move) {
    var b = clone();
    b.move = move;

    if (move.isPass() | move.isNone())
      return b;

    int k = move.getIndex();
    Color color = move.getColor();
    int[][] dirs = LINES[k];
    for (int dir = 0; dir < 8; dir++) {
      int[] line = dirs[dir];
      // この方向で color に挟まれる相手石の連続を探す
      int run = 0;
      boolean closed = false;
      for (int i = 0; i < line.length; i++) {
        var c = b.board[line[i]];
        if (c == NONE || c == BLOCK)
          break;
        if (c == color) {
          closed = (run > 0);
          break;
        }
        run++; // 相手石
      }
      if (closed) {
        for (int i = 0; i < run; i++)
          b.board[line[i]] = color;
      }
    }
    b.board[k] = color;
    return b;
  }

  public OurBoard flipped() {
    var b = clone();
    for (int k = 0; k < LENGTH; k++)
      b.board[k] = b.board[k].flipped();
    b.move = this.move.flipped();
    return b;
  }
}
