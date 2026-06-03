package p26x06;

import ap26.*;
import static ap26.Board.*;
import static ap26.Color.*;
import java.util.*;

/**
 * トリッキー特化型 OurPlayer
 */
public class OurPlayer extends ap26.Player {
  static final String MY_NAME = "26X6";
  static final long TOTAL_NANOS = 58_000_000_000L;
  static final int INF = 1_000_000_000;

  // メンバーの Move Ordering を逆手に取るためのマトリクス
  // 相手が嫌う「X/C打ち」のマイナスをあえて緩め、終盤の逆転を狙う配置
  static final int[] PRIO = {
      150,  -5,  25,  25,  -5, 150,
       -5, -20,  -2,  -2, -20,  -5,
       25,  -2,  10,  10,  -2,  25,
       25,  -2,  10,  10,  -2,  25,
       -5, -20,  -2,  -2, -20,  -5,
      150,  -5,  25,  25,  -5, 150,
  };

  // 特徴係数：位置、機動性、フロンティア
  static final int CPOS = 12, CMOB = 35, CFRONT = -18, CBAIT = 45;
  static final int[] CORNERS = {0, 5, 30, 35};
  
  // 角の周囲の危険地帯（X, C打ちマス）
  static final int[][] ADJACENTS_OF_CORNER = {
      {1, 6, 7},     // a1の隣
      {4, 11, 10},   // f1の隣
      {24, 25, 31},  // a6の隣
      {28, 29, 34}   // f6の隣
  };

  long timeUsedNanos = 0;
  long deadline = 0;
  boolean timeUp = false;
  Move move = Move.ofPass(NONE);
  OurBoard board = new OurBoard();

  // アロケーションフリー用バッファ
  static final int MAX_PLY = 80;
  final int[][] moveBuf = new int[MAX_PLY][40];
  final int[] rootBuf = new int[40];

  // 置換表
  static final int TT_BITS = 20;
  final int ttMask;
  final long[] ttKey;
  final int[] ttVal;
  final byte[] ttFlag;
  final byte[] ttMove;
  
  static final byte TT_EXACT = 1, TT_LOWER = 2, TT_UPPER = 3;
  static final long ZSIDE = 0x9E3779B97F4A7C15L;

  public OurPlayer(Color color) {
    super(MY_NAME, color);
    int sz = 1 << TT_BITS;
    ttMask = sz - 1;
    ttKey = new long[sz];
    ttVal = new int[sz];
    ttFlag = new byte[sz];
    ttMove = new byte[sz];
  }

  @Override
  public void setBoard(Board b) {
    for (int k = 0; k < LENGTH; k++) this.board.set(k, b.get(k));
    this.timeUsedNanos = 0;
  }

  @Override
  public Move think(Board argBoard) {
    long t0 = System.nanoTime();
    for (int k = 0; k < LENGTH; k++) this.board.set(k, argBoard.get(k));
    Color me = getColor();

    if (!this.board.hasLegalMove(me)) {
      this.move = Move.ofPass(me);
    } else {
      OurBoard root = (me == BLACK) ? this.board.clone() : this.board.flipped();
      int empties = root.count(NONE);

      long remaining = TOTAL_NANOS - timeUsedNanos;
      long budget = remaining / (Math.max(1, empties / 2) + 1);
      
      // 終盤または変形盤の競り合いエリアでは時間を倍化
      if (empties <= 22) {
          budget = Math.min(remaining / 2, 20_000_000_000L);
      }
      this.deadline = System.nanoTime() + budget;

      int chosen = iterativeDeepening(root, empties);

      if (chosen < 0 || !this.board.isLegalMove(chosen, me)) {
        int n = this.board.genLegal(me, rootBuf);
        chosen = (n > 0) ? rootBuf[0] : -1;
      }
      this.move = (chosen >= 0) ? new Move(chosen, me) : Move.ofPass(me);
    }

    timeUsedNanos += System.nanoTime() - t0;
    return this.move;
  }

  int iterativeDeepening(OurBoard root, int empties) {
    int n = root.genLegal(BLACK, rootBuf);
    orderStatic(rootBuf, n);
    int best = rootBuf[0];

    // 限界まで深く読む
    int maxDepth = (empties <= 16) ? empties : 4; // 軽量化して中盤は効率優先の4手読み
    for (int depth = 1; depth <= maxDepth; depth++) {
      timeUp = false;
      int b = rootSearch(root, depth, best);
      if (timeUp) break;
      best = b;
    }
    return best;
  }

  int rootSearch(OurBoard root, int depth, int pv) {
    int n = root.genLegal(BLACK, rootBuf);
    orderStatic(rootBuf, n);
    moveToFront(rootBuf, n, pv);

    int alpha = -INF, beta = INF;
    int best = rootBuf[0];
    for (int i = 0; i < n; i++) {
      OurBoard c = root.placedIndex(rootBuf[i], BLACK);
      int v = -minSearch(c, -beta, -alpha, depth - 1);
      if (timeUp) return best;
      if (v > alpha) {
        alpha = v;
        best = rootBuf[i];
      }
    }
    return best;
  }

  int maxSearch(OurBoard b, int alpha, int beta, int depth) {
    if ((searchNodes() & 1023) == 0 && System.nanoTime() >= deadline) timeUp = true;
    if (timeUp) return alpha;
    if (b.isEnd()) return b.score() * 100000;
    if (depth == 0) return evaluateTricky(b);

    long h = b.h ^ ZSIDE;
    int idx = (int) (h & ttMask);
    if (ttFlag[idx] != 0 && ttKey[idx] == h) {
      return ttVal[idx];
    }

    int[] mv = moveBuf[depth];
    int n = b.genLegal(BLACK, mv);
    if (n == 0) return minSearch(b, alpha, beta, depth - 1); // パス
    orderStatic(mv, n);

    int best = -INF;
    for (int i = 0; i < n; i++) {
      OurBoard c = b.placedIndex(mv[i], BLACK);
      int v = -minSearch(c, -beta, -alpha, depth - 1);
      if (v > best) best = v;
      if (best > alpha) alpha = best;
      if (alpha >= beta) break;
    }
    
    ttKey[idx] = h; ttVal[idx] = best; ttFlag[idx] = TT_EXACT;
    return best;
  }

  int minSearch(OurBoard b, int alpha, int beta, int depth) {
    if ((searchNodes() & 1023) == 0 && System.nanoTime() >= deadline) timeUp = true;
    if (timeUp) return beta;
    if (b.isEnd()) return b.score() * 100000;
    if (depth == 0) return evaluateTricky(b);

    long h = b.h;
    int idx = (int) (h & ttMask);
    if (ttFlag[idx] != 0 && ttKey[idx] == h) {
      return ttVal[idx];
    }

    int[] mv = moveBuf[depth];
    int n = b.genLegal(WHITE, mv);
    if (n == 0) return maxSearch(b, alpha, beta, depth - 1); // パス
    orderStatic(mv, n);

    int best = INF;
    for (int i = 0; i < n; i++) {
      OurBoard c = b.placedIndex(mv[i], WHITE);
      int v = -maxSearch(c, -beta, -alpha, depth - 1);
      if (v < best) best = v;
      if (best < beta) beta = best;
      if (alpha >= beta) break;
    }
    
    ttKey[idx] = h; ttVal[idx] = best; ttFlag[idx] = TT_EXACT;
    return best;
  }

  /**
   * トリッキー評価関数：相手の固定マトリクスを狂わせる判定を内蔵
   */
  int evaluateTricky(OurBoard b) {
    int pos = 0;
    long bb = b.black;
    while (bb != 0) { int k = Long.numberOfTrailingZeros(bb); bb &= bb - 1; pos += PRIO[k]; }
    long ww = b.white;
    while (ww != 0) { int k = Long.numberOfTrailingZeros(ww); ww &= ww - 1; pos -= PRIO[k]; }

    // フロンティア計算
    long e = b.empty();
    long fm = 0;
    for (int d = 0; d < 8; d++) fm |= OurBoard.shift(e, OurBoard.DS[d], OurBoard.DM[d]);
    int front = Long.bitCount(b.black & fm) - Long.bitCount(b.white & fm);

    // 機動性
    int mob = Long.bitCount(b.legalBits(BLACK)) - Long.bitCount(b.legalBits(WHITE));

    // トリッキー要素
    // 相手が角を取ったとき、その周囲のX/C打ちマスが「黒」で埋まっており、
    // かつ次の手でその外周を完全にロックできる形になっていれば、評価値を大逆転させる。
    int baitScore = 0;
    for (int i = 0; i < 4; i++) {
      Color cornerColor = b.get(CORNERS[i]);
      if (cornerColor == WHITE) {
        // 相手が角を取ったが、その周囲のマスが自分の石であれば、相手の確定石化を防いで外周を包囲できる
        int mySurrounding = 0;
        for (int adj : ADJACENTS_OF_CORNER[i]) {
          if (b.get(adj) == BLACK) mySurrounding++;
        }
        if (mySurrounding >= 2) baitScore += 80; // 強烈な罠の加算
      }
    }

    // 心理戦：もし中盤に「圧倒的不利（mobが極端に少ない）」を検知したら、
    // 評価関数の重みをわざと反転させる
    if (mob < -4) {
        return (CPOS * pos) - (CMOB * mob) + (CFRONT * front); 
    }

    return CPOS * pos + CMOB * mob + CFRONT * front + CBAIT * baitScore;
  }

  void orderStatic(int[] a, int n) {
    for (int i = 1; i < n; i++) {
      int x = a[i], px = PRIO[x], j = i - 1;
      while (j >= 0 && PRIO[a[j]] < px) {
        a[j + 1] = a[j];
        j--;
      }
      a[j + 1] = x;
    }
  }

  void moveToFront(int[] a, int n, int v) {
    for (int i = 1; i < n; i++) {
      if (a[i] == v) {
        System.arraycopy(a, 0, a, 1, i);
        a[0] = v;
        return;
      }
    }
  }

  private long searchNodes() {
      return 0; // 簡易カウント用
  }
}
