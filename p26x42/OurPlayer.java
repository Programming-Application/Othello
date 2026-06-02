package p26x42;

import ap26.*;
import static ap26.Board.*;
import static ap26.Color.*;

/**
 * 位置評価。非終局は静的な重み行列の総和、終局は最終石差を支配的に評価する。
 */
class MyEval {
  // 位置重み (Phase 4a: 6 対称パラメータを座標降下で最適化)。
  static final int[] W = {
      29, 10, 10, 10, 10, 29,
      10, -5, -3, -3, -5, 10,
      10, -3, 1, 1, -3, 10,
      10, -3, 1, 1, -3, 10,
      10, -5, -3, -3, -5, 10,
      29, 10, 10, 10, 10, 29,
  };

  // 特徴係数 (Phase 4b: 座標降下で最適化)。
  //   value = CPOS*位置 + CMOB*着手可能数差 + CFRONT*フロンティア差 + CSTAB*確定石差
  static final int CPOS = 10, CMOB = 31, CFRONT = -20, CSTAB = 20;

  // 角から伸びる2辺 (角自身を除く)。確定石の連結走査用。
  static final int[][] EDGES_FROM_CORNER = {
      {1, 2, 3, 4, 5}, {6, 12, 18, 24, 30},      // a1
      {4, 3, 2, 1, 0}, {11, 17, 23, 29, 35},     // f1
      {31, 32, 33, 34, 35}, {24, 18, 12, 6, 0},  // a6
      {34, 33, 32, 31, 30}, {29, 23, 17, 11, 5}, // f6
  };
  static final int[] CORNERS = {0, 5, 30, 35};

  /** 非終局の評価 (BLACK 視点)。位置 + mobility + frontier + stability。全てビット演算。整数値。*/
  int value(OurBoard b) {
    // 位置: 黒石は +W, 白石は -W (set bit 走査, get() を使わない)
    int pos = 0;
    long bb = b.black;
    while (bb != 0) { int k = Long.numberOfTrailingZeros(bb); bb &= bb - 1; pos += W[k]; }
    long ww = b.white;
    while (ww != 0) { int k = Long.numberOfTrailingZeros(ww); ww &= ww - 1; pos -= W[k]; }
    // frontier: 空きに隣接する自石数 (空きを8方向シフトした和 = 空き隣接マス集合)
    long e = b.empty();
    long fm = 0;
    for (int d = 0; d < 8; d++)
      fm |= OurBoard.shift(e, OurBoard.DS[d], OurBoard.DM[d]);
    int front = Long.bitCount(b.black & fm) - Long.bitCount(b.white & fm);
    // mobility
    int mob = Long.bitCount(b.legalBits(BLACK)) - Long.bitCount(b.legalBits(WHITE));
    return CPOS * pos + CMOB * mob + CFRONT * front + CSTAB * stableDiff(b);
  }

  /** 角アンカーの辺連結による確定石の概算差 (BLACK - WHITE)。BLOCK 角は自動的に除外。*/
  int stableDiff(OurBoard b) {
    int sb = 0, sw = 0;
    for (int ci = 0; ci < 4; ci++) {
      var cc = b.get(CORNERS[ci]);
      if (cc != BLACK && cc != WHITE)
        continue;
      if (cc == BLACK) sb++; else sw++;
      for (int e = 0; e < 2; e++) {
        for (int k : EDGES_FROM_CORNER[ci * 2 + e]) {
          if (b.get(k) == cc) {
            if (cc == BLACK) sb++; else sw++;
          } else break;
        }
      }
    }
    return sb - sw;
  }

  /** 終局の評価。最終石差を支配的なスケールで返す (勝敗・石差を位置評価より優先)。整数値。*/
  int terminal(OurBoard b) {
    return 1_000_000 * b.score();
  }
}

/**
 * Phase 2 探索: 反復深化 + 時間管理 + ムーブオーダリングの α-β。
 *
 * <h2>設計</h2>
 * <ul>
 *   <li><b>常に BLACK 視点で探索</b>: 白番なら {@code flipped()} して黒視点に統一 (旧実装の良い部分を流用)。</li>
 *   <li><b>反復深化 (ID)</b>: 深さ 1 から 1 ずつ深め、時間切れになったら直前に完了した深さの最善手を採用。
 *       深さの上限は空きマス数 (それ以上深く読んでも局面が無い)。
 *       <b>空きが少ない終盤では ID が自然に「深さ=空き数」に到達し、全葉が終局＝完全読みになる。</b>
 *       (専用の終盤完全読みは Phase 3 で強化)</li>
 *   <li><b>時間管理</b>: 本番は {@code think(Board)} 単一引数で呼ばれ残り時間が渡らないため、
 *       {@code nanoTime} で自分の累積思考時間を測り、1 ゲーム {@code TOTAL} 秒から逆算して
 *       1 手の持ち時間を配分する。{@code setBoard}(ゲーム開始フック) で累積をリセット。</li>
 *   <li><b>ムーブオーダリング</b>: 角優先の静的順 + 前反復の最善手 (PV) を先頭に。α-β の枝刈り効率を上げる。</li>
 * </ul>
 */
public class OurPlayer extends ap26.Player {
  static final String MY_NAME = "26X4";

  /** 1 ゲームの持ち時間 (本番 60s)。安全マージンを見て 58s を上限として配分する。*/
  static final long TOTAL_NANOS = 58_000_000_000L;

  /** 空きマス数がこれ以下なら終局まで厳密に読み切る (完全読み)。計測で調整。*/
  static int ENDGAME_THRESHOLD = 16;

  /** 終盤完全読み手に与える持ち時間の上限 (この手で勝敗が決まるため厚めに配分)。*/
  static final long ENDGAME_BUDGET_CAP_NANOS = 6_000_000_000L;

  // --- 計測/ベンチ用 (提出時は無害な診断フィールド) ---
  public static long searchNodes = 0;     // 探索ノード総数
  public static int lastReachedDepth = 0; // 直近の手で到達した探索深さ
  public static int maxReachedDepth = 0;  // 計測区間での最大到達深さ
  public static long benchBudgetNanos = 0; // >0 ならこの値を 1 手の持ち時間に固定 (ベンチ用)

  /** ムーブオーダリング用の静的優先度 (評価値ではない。角を高く、X/C マスを低く)。*/
  static final int[] PRIO = {
      120, -20, 20, 20, -20, 120,
      -20, -40, -5, -5, -40, -20,
      20, -5, 15, 15, -5, 20,
      20, -5, 15, 15, -5, 20,
      -20, -40, -5, -5, -40, -20,
      120, -20, 20, 20, -20, 120,
  };

  MyEval eval = new MyEval();
  OurBoard board = new OurBoard();

  long timeUsedNanos = 0; // このゲームで使った累積思考時間
  long deadline = 0;      // 現在の手の打ち切り時刻 (nanoTime)
  boolean timeUp = false;

  // 深さごとの合法手バッファ (アロケーション回避)。index = depthLeft
  final int[][] moveBuf = new int[40][40];
  final int[] rootBuf = new int[40];

  // --- 終盤完全読み用 置換表 (Zobrist hashing) ---
  // 盤面セルのハッシュは OurBoard.h (増分更新)。手番分だけここで XOR する。
  static final long ZSIDE = 0x9E3779B97F4A7C15L; // 手番(黒番)用の固定乱数
  static final int TT_BITS = 20;
  static final int TT_SIZE = 1 << TT_BITS;
  static final int TT_MASK = TT_SIZE - 1;
  static final byte TT_EXACT = 1, TT_LOWER = 2, TT_UPPER = 3;
  final long[] ttKey = new long[TT_SIZE];
  final int[] ttVal = new int[TT_SIZE];
  final byte[] ttFlag = new byte[TT_SIZE];
  final byte[] ttMove = new byte[TT_SIZE];

  // --- 中盤探索用 置換表 (深さ付き) ---
  static final int INF = 1_000_000_000;
  static final int MT_BITS = 19;
  static final int MT_SIZE = 1 << MT_BITS;
  static final int MT_MASK = MT_SIZE - 1;
  final long[] mtKey = new long[MT_SIZE];
  final int[] mtVal = new int[MT_SIZE];
  final byte[] mtDepth = new byte[MT_SIZE];
  final byte[] mtFlag = new byte[MT_SIZE];
  final byte[] mtMove = new byte[MT_SIZE];
  boolean useTtPvs = true; // 検証用: false で素の α-β (TT/PVS無効)

  /** 盤面ハッシュ。盤面セルは増分更新済みの b.h、手番は ZSIDE で区別。O(1)。*/
  long hash(OurBoard b, boolean blackToMove) {
    return blackToMove ? (b.h ^ ZSIDE) : b.h;
  }

  public OurPlayer(Color color) {
    super(MY_NAME, color);
  }

  /** ゲーム開始時にリーグから呼ばれる。盤面を取り込み、持ち時間の累積をリセットする。*/
  @Override
  public void setBoard(Board b) {
    loadBoard(b);
    this.timeUsedNanos = 0;
  }

  private void loadBoard(Board b) {
    for (int k = 0; k < LENGTH; k++)
      this.board.set(k, b.get(k));
  }

  @Override
  public Move think(Board argBoard) {
    long t0 = System.nanoTime();
    loadBoard(argBoard);
    Color me = getColor();

    Move result;
    if (!this.board.hasLegalMove(me)) {
      result = Move.ofPass(me);
    } else {
      // 常に BLACK 視点に統一 (flip は色のみ入替で、マス番号は不変)
      OurBoard root = (me == BLACK) ? this.board.clone() : this.board.flipped();
      int empties = root.count(NONE);

      long budget;
      if (benchBudgetNanos > 0) {
        budget = benchBudgetNanos;
      } else {
        long remaining = TOTAL_NANOS - timeUsedNanos;
        budget = computeBudget(remaining, empties);
        if (empties <= ENDGAME_THRESHOLD) {
          // 終盤完全読みには厚めの持ち時間を割く (残りの半分か上限のいずれか小さい方)
          long eg = Math.min(remaining / 2, ENDGAME_BUDGET_CAP_NANOS);
          if (eg > budget)
            budget = eg;
        }
      }
      this.deadline = System.nanoTime() + budget;

      int chosen = searchBestMove(root, empties);

      // 防御: 念のため合法性を確認し、非合法なら最初の合法手へ
      if (chosen < 0 || !this.board.isLegalMove(chosen, me)) {
        int n = this.board.genLegal(me, rootBuf);
        chosen = (n > 0) ? rootBuf[0] : -1;
      }
      result = (chosen >= 0) ? new Move(chosen, me) : Move.ofPass(me);
    }

    timeUsedNanos += System.nanoTime() - t0;
    return result;
  }

  /** 残り時間と空きマス数から 1 手の持ち時間を決める。*/
  long computeBudget(long remainingNanos, int empties) {
    int myMovesLeft = Math.max(1, empties / 2); // 自分の残り手数の概算
    long budget = remainingNanos / (myMovesLeft + 1);
    budget = Math.min(budget, remainingNanos / 2); // 1 手で残りの半分を超えない
    budget = Math.max(budget, 5_000_000L);         // 下限 5ms
    budget = Math.min(budget, Math.max(remainingNanos, 1_000_000L)); // 残りを超えない
    return budget;
  }

  /** 反復深化。終盤は完全読みに切替。完了した最深の最善手を返す。*/
  int searchBestMove(OurBoard root, int empties) {
    int n0 = root.genLegal(BLACK, rootBuf);
    orderStatic(rootBuf, n0);
    int best = rootBuf[0];
    lastReachedDepth = 0;

    // 終盤完全読み: 空きマスが少なければ終局まで厳密に最終石差を最大化
    if (empties <= ENDGAME_THRESHOLD) {
      timeUp = false;
      int mv = solveExactRoot(root, best);
      if (!timeUp && mv >= 0) {
        lastReachedDepth = empties;
        if (empties > maxReachedDepth)
          maxReachedDepth = empties;
        return mv;
      }
      // 時間切れ (しきい値が大きすぎた場合の保険) → 通常 ID にフォールバック
    }

    for (int depth = 1; depth <= empties; depth++) {
      timeUp = false;
      int b = rootSearch(root, depth, best);
      if (timeUp)
        break; // 未完了の反復は破棄
      best = b;
      lastReachedDepth = depth;
      if (depth > maxReachedDepth)
        maxReachedDepth = depth;
      if (System.nanoTime() >= deadline)
        break; // 次の深さに行く時間がない
    }
    return best;
  }

  /** ルートの 1 反復。pv (前反復の最善手) を先頭に試す。PVS。*/
  int rootSearch(OurBoard root, int depth, int pv) {
    int n = root.genLegal(BLACK, rootBuf);
    orderStatic(rootBuf, n);
    moveToFront(rootBuf, n, pv);

    int alpha = -INF, beta = INF;
    int best = rootBuf[0];
    for (int i = 0; i < n; i++) {
      OurBoard c = root.placedIndex(rootBuf[i], BLACK);
      int v;
      if (i == 0 || !useTtPvs) {
        v = minSearch(c, alpha, beta, depth - 1);
      } else {
        v = minSearch(c, alpha, alpha + 1, depth - 1); // null window
        if (v > alpha && !timeUp)
          v = minSearch(c, alpha, beta, depth - 1);     // 失敗 → 再探索
      }
      if (timeUp)
        return best;
      if (v > alpha) {
        alpha = v;
        best = rootBuf[i];
      }
    }
    return best;
  }

  // ===================== 終盤完全読み (整数 α-β, 最終石差) =====================

  /** 完全読みルート: 最善手のマス番号を返す (BLACK 視点)。時間切れなら -1。*/
  int solveExactRoot(OurBoard root, int pv) {
    int n = root.genLegal(BLACK, rootBuf);
    orderStatic(rootBuf, n);
    moveToFront(rootBuf, n, pv);

    int alpha = -1000, beta = 1000;
    int best = rootBuf[0], bestVal = -100000;
    for (int i = 0; i < n; i++) {
      OurBoard c = root.placedIndex(rootBuf[i], BLACK);
      int v = solveMin(c, alpha, beta, 1);
      if (timeUp)
        return -1;
      if (v > bestVal) {
        bestVal = v;
        best = rootBuf[i];
        if (v > alpha)
          alpha = v;
      }
    }
    return best;
  }

  /** 検証用: 中盤探索の値を固定深さで計算 (ttpvs で TT/PVS の有無を切替)。テストフック。*/
  public int searchValue(OurBoard root, int depth, boolean ttpvs) {
    this.useTtPvs = ttpvs;
    this.deadline = Long.MAX_VALUE;
    this.timeUp = false;
    java.util.Arrays.fill(mtFlag, (byte) 0); // 比較を汚さないようTTクリア
    return maxSearch(root, -INF, INF, depth);
  }

  /** ベンチ用: BLACK 手番の局面の最終石差(最善応酬)を厳密に計算する。*/
  public int benchSolve(OurBoard root) {
    this.deadline = Long.MAX_VALUE;
    this.timeUp = false;
    return solveMax(root, -1000, 1000, 0);
  }

  // BLACK 手番: 最終石差 (BLACK-WHITE) を最大化
  int solveMax(OurBoard b, int alpha, int beta, int ply) {
    searchNodes++;
    if ((searchNodes & 1023) == 0 && System.nanoTime() >= deadline)
      timeUp = true;
    if (timeUp)
      return alpha;
    if (b.isEnd())
      return b.score();

    final int alpha0 = alpha, beta0 = beta;
    long h = hash(b, true);
    int idx = (int) (h & TT_MASK);
    int ttMv = -1;
    if (ttFlag[idx] != 0 && ttKey[idx] == h) {
      int v = ttVal[idx];
      byte fl = ttFlag[idx];
      if (fl == TT_EXACT) return v;
      if (fl == TT_LOWER && v >= beta) return v;
      if (fl == TT_UPPER && v <= alpha) return v;
      ttMv = ttMove[idx];
    }

    int[] mv = moveBuf[ply];
    int n = b.genLegal(BLACK, mv);
    if (n == 0)
      return solveMin(b, alpha, beta, ply + 1); // パス
    orderStatic(mv, n);
    if (ttMv >= 0) moveToFront(mv, n, ttMv);

    int best = -100000, bestMove = mv[0];
    for (int i = 0; i < n; i++) {
      OurBoard c = b.placedIndex(mv[i], BLACK);
      int v = solveMin(c, alpha, beta, ply + 1);
      if (timeUp)
        return best;
      if (v > best) {
        best = v;
        bestMove = mv[i];
      }
      if (best > alpha)
        alpha = best;
      if (alpha >= beta)
        break;
    }
    store(idx, h, best, alpha0, beta0, bestMove);
    return best;
  }

  // WHITE 手番: 最終石差を最小化
  int solveMin(OurBoard b, int alpha, int beta, int ply) {
    searchNodes++;
    if ((searchNodes & 1023) == 0 && System.nanoTime() >= deadline)
      timeUp = true;
    if (timeUp)
      return beta;
    if (b.isEnd())
      return b.score();

    final int alpha0 = alpha, beta0 = beta;
    long h = hash(b, false);
    int idx = (int) (h & TT_MASK);
    int ttMv = -1;
    if (ttFlag[idx] != 0 && ttKey[idx] == h) {
      int v = ttVal[idx];
      byte fl = ttFlag[idx];
      if (fl == TT_EXACT) return v;
      if (fl == TT_LOWER && v >= beta) return v;
      if (fl == TT_UPPER && v <= alpha) return v;
      ttMv = ttMove[idx];
    }

    int[] mv = moveBuf[ply];
    int n = b.genLegal(WHITE, mv);
    if (n == 0)
      return solveMax(b, alpha, beta, ply + 1); // パス
    orderStatic(mv, n);
    if (ttMv >= 0) moveToFront(mv, n, ttMv);

    int best = 100000, bestMove = mv[0];
    for (int i = 0; i < n; i++) {
      OurBoard c = b.placedIndex(mv[i], WHITE);
      int v = solveMax(c, alpha, beta, ply + 1);
      if (timeUp)
        return best;
      if (v < best) {
        best = v;
        bestMove = mv[i];
      }
      if (best < beta)
        beta = best;
      if (alpha >= beta)
        break;
    }
    store(idx, h, best, alpha0, beta0, bestMove);
    return best;
  }

  /** TT へ格納。元の窓 [alpha0,beta0] に対する best の位置で EXACT/LOWER/UPPER を決める。*/
  void store(int idx, long h, int best, int alpha0, int beta0, int bestMove) {
    byte fl = best <= alpha0 ? TT_UPPER : best >= beta0 ? TT_LOWER : TT_EXACT;
    ttKey[idx] = h;
    ttVal[idx] = best;
    ttFlag[idx] = fl;
    ttMove[idx] = (byte) bestMove;
  }

  // ===========================================================================

  // BLACK 手番 (最大化)。整数 α-β + 置換表 + PVS。
  int maxSearch(OurBoard b, int alpha, int beta, int depthLeft) {
    searchNodes++;
    if ((searchNodes & 1023) == 0 && System.nanoTime() >= deadline)
      timeUp = true;
    if (timeUp)
      return alpha;
    if (b.isEnd())
      return eval.terminal(b);
    if (depthLeft == 0)
      return eval.value(b);

    final int alpha0 = alpha, beta0 = beta;
    long h = hash(b, true);
    int idx = (int) (h & MT_MASK);
    int ttMv = -1;
    if (useTtPvs && mtFlag[idx] != 0 && mtKey[idx] == h) {
      if (mtDepth[idx] >= depthLeft) {
        int v = mtVal[idx];
        byte fl = mtFlag[idx];
        if (fl == TT_EXACT) return v;
        if (fl == TT_LOWER && v >= beta) return v;
        if (fl == TT_UPPER && v <= alpha) return v;
      }
      ttMv = mtMove[idx];
    }

    int[] mv = moveBuf[depthLeft];
    int n = b.genLegal(BLACK, mv);
    if (n == 0)
      return minSearch(b, alpha, beta, depthLeft - 1); // パス
    orderStatic(mv, n);
    if (ttMv >= 0) moveToFront(mv, n, ttMv);

    int best = -INF, bestMove = mv[0];
    for (int i = 0; i < n; i++) {
      OurBoard c = b.placedIndex(mv[i], BLACK);
      int v;
      if (i == 0 || !useTtPvs) {
        v = minSearch(c, alpha, beta, depthLeft - 1);
      } else {
        v = minSearch(c, alpha, alpha + 1, depthLeft - 1);
        if (v > alpha && v < beta && !timeUp)
          v = minSearch(c, alpha, beta, depthLeft - 1);
      }
      if (timeUp)
        return best > -INF ? best : alpha;
      if (v > best) { best = v; bestMove = mv[i]; }
      if (best > alpha) alpha = best;
      if (alpha >= beta) break;
    }
    storeMid(idx, h, best, alpha0, beta0, bestMove, depthLeft);
    return best;
  }

  // WHITE 手番 (最小化)。
  int minSearch(OurBoard b, int alpha, int beta, int depthLeft) {
    searchNodes++;
    if ((searchNodes & 1023) == 0 && System.nanoTime() >= deadline)
      timeUp = true;
    if (timeUp)
      return beta;
    if (b.isEnd())
      return eval.terminal(b);
    if (depthLeft == 0)
      return eval.value(b);

    final int alpha0 = alpha, beta0 = beta;
    long h = hash(b, false);
    int idx = (int) (h & MT_MASK);
    int ttMv = -1;
    if (useTtPvs && mtFlag[idx] != 0 && mtKey[idx] == h) {
      if (mtDepth[idx] >= depthLeft) {
        int v = mtVal[idx];
        byte fl = mtFlag[idx];
        if (fl == TT_EXACT) return v;
        if (fl == TT_LOWER && v >= beta) return v;
        if (fl == TT_UPPER && v <= alpha) return v;
      }
      ttMv = mtMove[idx];
    }

    int[] mv = moveBuf[depthLeft];
    int n = b.genLegal(WHITE, mv);
    if (n == 0)
      return maxSearch(b, alpha, beta, depthLeft - 1); // パス
    orderStatic(mv, n);
    if (ttMv >= 0) moveToFront(mv, n, ttMv);

    int best = INF, bestMove = mv[0];
    for (int i = 0; i < n; i++) {
      OurBoard c = b.placedIndex(mv[i], WHITE);
      int v;
      if (i == 0 || !useTtPvs) {
        v = maxSearch(c, alpha, beta, depthLeft - 1);
      } else {
        v = maxSearch(c, beta - 1, beta, depthLeft - 1);
        if (v < beta && v > alpha && !timeUp)
          v = maxSearch(c, alpha, beta, depthLeft - 1);
      }
      if (timeUp)
        return best < INF ? best : beta;
      if (v < best) { best = v; bestMove = mv[i]; }
      if (best < beta) beta = best;
      if (alpha >= beta) break;
    }
    storeMid(idx, h, best, alpha0, beta0, bestMove, depthLeft);
    return best;
  }

  /** 中盤TTへ格納 (深さ優先置換)。元窓 [alpha0,beta0] で EXACT/LOWER/UPPER を決定。*/
  void storeMid(int idx, long h, int best, int alpha0, int beta0, int bestMove, int depth) {
    if (!useTtPvs || timeUp) return;
    byte fl = best <= alpha0 ? TT_UPPER : best >= beta0 ? TT_LOWER : TT_EXACT;
    if (mtFlag[idx] == 0 || mtKey[idx] == h || depth >= mtDepth[idx]) {
      mtKey[idx] = h;
      mtVal[idx] = best;
      mtDepth[idx] = (byte) depth;
      mtFlag[idx] = fl;
      mtMove[idx] = (byte) bestMove;
    }
  }

  /** 先頭 n 個を PRIO 降順に挿入ソート (n は小さいので軽い)。*/
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

  /** 値 v を先頭に移動 (見つからなければ何もしない)。*/
  void moveToFront(int[] a, int n, int v) {
    for (int i = 1; i < n; i++) {
      if (a[i] == v) {
        System.arraycopy(a, 0, a, 1, i);
        a[0] = v;
        return;
      }
    }
  }
}
