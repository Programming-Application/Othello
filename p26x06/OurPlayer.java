package p26x06;

import ap26.*;
import static ap26.Board.*;
import static ap26.Color.*;

public class OurPlayer extends ap26.Player {
    static final String MY_NAME = "26X6";
    static final long TOTAL_NANOS = 58_000_000_000L;
    static final int  ENDGAME_THRESHOLD = 20;   // 完全読みしきい値
    static final int  WLD_THRESHOLD     = 24;   // WLD証明しきい値
    static final long ENDGAME_BUDGET_CAP = 25_000_000_000L;
    static final long OPENING_BUDGET_CAP =  2_000_000_000L;
    static final int  INF    = 1_000_000_000;
    static final int  FF_MIN = 7;               // fastest-first を使う残り空き数下限
    static final int  LMR_MIN_DEPTH = 3;       // LMR を適用する最小深さ
    static final int  LMR_MIN_MOVE  = 3;       // LMR を適用する手番インデックス下限
    static final int  ASPIRATION_DELTA = 100;  // アスピレーション初期ウィンドウ幅
    static final long ZSIDE  = 0x9E3779B97F4A7C15L;
    static final byte TT_EXACT = 1, TT_LOWER = 2, TT_UPPER = 3;

    // 位置評価マトリクス（26X4の調整済みパラメータをベースに安定度を重視）
    static final int[] W = {
        29, 10, 10, 10, 10, 29,
        10, -5, -3, -3, -5, 10,
        10, -3,  1,  1, -3, 10,
        10, -3,  1,  1, -3, 10,
        10, -5, -3, -3, -5, 10,
        29, 10, 10, 10, 10, 29,
    };
    // 評価係数：安定石をより重視し、潜在的機動性を追加
    static final int CPOS = 10, CMOB = 25, CFRONT = -15, CSTAB = 30, CPOTMOB = 8;

    // 角から伸びる辺（安定石計算用）
    static final int[][] EDGES_FROM_CORNER = {
        {1, 2, 3, 4, 5}, {6, 12, 18, 24, 30},
        {4, 3, 2, 1, 0}, {11, 17, 23, 29, 35},
        {31, 32, 33, 34, 35}, {24, 18, 12, 6, 0},
        {34, 33, 32, 31, 30}, {29, 23, 17, 11, 5},
    };
    static final int[] CORNERS = {0, 5, 30, 35};

    // ムーブオーダリング優先度（角優先・X/C打ち抑制）
    static final int[] PRIO = {
        120, -20, 20, 20, -20, 120,
        -20, -40, -5, -5, -40, -20,
         20,  -5, 15, 15,  -5,  20,
         20,  -5, 15, 15,  -5,  20,
        -20, -40, -5, -5, -40, -20,
        120, -20, 20, 20, -20, 120,
    };

    OurBoard board = new OurBoard();
    long timeUsedNanos = 0;
    long deadline      = 0;
    boolean timeUp     = false;
    int  wldRootValue  = 0;
    int  rootScore     = 0;
    long searchNodes   = 0;

    static final int MAX_PLY = 80;
    final int[][]    moveBuf  = new int[MAX_PLY][40];
    final int[]      rootBuf  = new int[40];

    // fastest-first バッファ（終盤ソルバ専用）
    final OurBoard[][] childBuf  = new OurBoard[MAX_PLY][40];
    final int[][]      keyBuf    = new int[MAX_PLY][40];
    final long[][]     legalBuf  = new long[MAX_PLY][40];

    // 終盤 TT
    static final int TT_BITS = 20;
    final int    ttMask;
    final long[] ttKey;
    final int[]  ttVal;
    final byte[] ttFlag;
    final byte[] ttMove;

    // 中盤 TT（深さ付き）
    static final int MT_BITS = 19;
    static final int MT_MASK = (1 << MT_BITS) - 1;
    final long[] mtKey   = new long[1 << MT_BITS];
    final int[]  mtVal   = new int [1 << MT_BITS];
    final byte[] mtDepth = new byte[1 << MT_BITS];
    final byte[] mtFlag  = new byte[1 << MT_BITS];
    final byte[] mtMove  = new byte[1 << MT_BITS];

    public OurPlayer(Color color) {
        super(MY_NAME, color);
        int sz = 1 << TT_BITS;
        ttMask = sz - 1;
        ttKey  = new long[sz];
        ttVal  = new int [sz];
        ttFlag = new byte[sz];
        ttMove = new byte[sz];
    }

    @Override
    public void setBoard(Board b) {
        for (int k = 0; k < LENGTH; k++) board.set(k, b.get(k));
        timeUsedNanos = 0;
    }

    @Override
    public Move think(Board argBoard) {
        long t0 = System.nanoTime();
        for (int k = 0; k < LENGTH; k++) board.set(k, argBoard.get(k));
        Color me = getColor();

        Move result;
        if (!board.hasLegalMove(me)) {
            result = Move.ofPass(me);
        } else {
            OurBoard root = (me == BLACK) ? board.clone() : board.flipped();
            int empties = root.count(NONE);

            long remaining = TOTAL_NANOS - timeUsedNanos;
            long budget    = computeBudget(remaining, empties);
            if (empties <= WLD_THRESHOLD) {
                long eg = Math.min(remaining / 2, ENDGAME_BUDGET_CAP);
                if (eg > budget) budget = eg;
            } else if (budget > OPENING_BUDGET_CAP) {
                budget = OPENING_BUDGET_CAP;
            }
            deadline = System.nanoTime() + budget;

            int chosen = searchBestMove(root, empties);
            if (chosen < 0 || !board.isLegalMove(chosen, me)) {
                int n = board.genLegal(me, rootBuf);
                chosen = (n > 0) ? rootBuf[0] : -1;
            }
            result = (chosen >= 0) ? new Move(chosen, me) : Move.ofPass(me);
        }

        timeUsedNanos += System.nanoTime() - t0;
        return result;
    }

    long computeBudget(long remaining, int empties) {
        int movesLeft = Math.max(1, empties / 2);
        long b = remaining / (movesLeft + 1);
        b = Math.min(b, remaining / 2);
        b = Math.max(b, 5_000_000L);
        b = Math.min(b, Math.max(remaining, 1_000_000L));
        return b;
    }

    int searchBestMove(OurBoard root, int empties) {
        int n0 = root.genLegal(BLACK, rootBuf);
        orderStatic(rootBuf, n0);
        int best = rootBuf[0];

        if (empties <= ENDGAME_THRESHOLD) {
            timeUp = false;
            int mv = solveExactRoot(root, best);
            if (!timeUp && mv >= 0) return mv;
            // 時間切れならIDにフォールバック
        } else if (empties <= WLD_THRESHOLD) {
            timeUp = false;
            int mv = wldRoot(root);
            if (!timeUp && mv >= 0 && wldRootValue >= 0) return mv;
            // 理論負けまたは時間切れ → IDで最善の負け手を探す
        }

        boolean hasPrevScore = false;
        for (int depth = 1; depth <= empties; depth++) {
            timeUp = false;
            int b;
            if (hasPrevScore && depth >= 3) {
                int lo = rootScore - ASPIRATION_DELTA;
                int hi = rootScore + ASPIRATION_DELTA;
                b = rootSearch(root, depth, best, lo, hi);
                if (!timeUp && (rootScore <= lo || rootScore >= hi)) {
                    b = rootSearch(root, depth, best, -INF, INF);
                }
            } else {
                b = rootSearch(root, depth, best, -INF, INF);
            }
            if (timeUp) break;
            best = b;
            hasPrevScore = true;
            if (System.nanoTime() >= deadline) break;
        }
        return best;
    }

    // ===== 終盤完全読みソルバ =====

    int solveExactRoot(OurBoard root, int pv) {
        int n = root.genLegal(BLACK, rootBuf);
        orderStatic(rootBuf, n);
        moveToFront(rootBuf, n, pv);
        int alpha = -1000, beta = 1000;
        int best = rootBuf[0], bestVal = -100000;
        for (int i = 0; i < n; i++) {
            OurBoard c = root.placedIndex(rootBuf[i], BLACK);
            int v = solveMin(c, alpha, beta, 1);
            if (timeUp) return -1;
            if (v > bestVal) {
                bestVal = v; best = rootBuf[i];
                if (v > alpha) alpha = v;
            }
        }
        return best;
    }

    int wldRoot(OurBoard root) {
        int n = root.genLegal(BLACK, rootBuf);
        orderStatic(rootBuf, n);
        int alpha = -1, beta = 1;
        int best = -2, bestMove = rootBuf[0];
        for (int i = 0; i < n; i++) {
            OurBoard c = root.placedIndex(rootBuf[i], BLACK);
            int v = Integer.signum(solveMin(c, alpha, beta, 1));
            if (timeUp) return -1;
            if (v > best) {
                best = v; bestMove = rootBuf[i];
                if (v > alpha) alpha = v;
            }
            if (alpha >= beta) break;
        }
        wldRootValue = best;
        return bestMove;
    }

    int solveMax(OurBoard b, int alpha, int beta, int ply) {
        return solveMax(b, alpha, beta, ply, -1L);
    }

    int solveMax(OurBoard b, int alpha, int beta, int ply, long myMoves) {
        searchNodes++;
        if ((searchNodes & 1023) == 0 && System.nanoTime() >= deadline) timeUp = true;
        if (timeUp) return alpha;
        if (myMoves == -1L) myMoves = b.legalBits(BLACK);
        if (myMoves == 0) {
            long wm = b.legalBits(WHITE);
            if (wm == 0) return b.score();
            return solveMin(b, alpha, beta, ply + 1, wm);
        }
        final int alpha0 = alpha, beta0 = beta;
        long h = b.h ^ ZSIDE;
        int idx = (int)(h & ttMask);
        int ttMv = -1;
        if (ttFlag[idx] != 0 && ttKey[idx] == h) {
            int v = ttVal[idx]; byte fl = ttFlag[idx];
            if (fl == TT_EXACT) return v;
            if (fl == TT_LOWER && v >= beta)  return v;
            if (fl == TT_UPPER && v <= alpha) return v;
            ttMv = ttMove[idx] & 0xFF;
        }
        int[] mv = moveBuf[ply];
        int n = OurBoard.bitsToIndexes(myMoves, mv);
        int best = -100000, bestMove;
        if (n > 1 && Long.bitCount(b.empty()) >= FF_MIN) {
            OurBoard[] kids = childBuf[ply];
            int[]      keys = keyBuf[ply];
            long[]  legals  = legalBuf[ply];
            for (int i = 0; i < n; i++) {
                kids[i] = b.placedIndex(mv[i], BLACK);
                long lm  = kids[i].legalBits(WHITE);
                keys[i]  = Long.bitCount(lm);
                legals[i] = lm;
            }
            ffSort(mv, kids, keys, legals, n);
            if (ttMv >= 0) moveFrontKids(mv, kids, legals, n, ttMv);
            bestMove = mv[0];
            for (int i = 0; i < n; i++) {
                int v = solveMin(kids[i], alpha, beta, ply + 1, legals[i]);
                if (timeUp) return best;
                if (v > best) { best = v; bestMove = mv[i]; }
                if (best > alpha) alpha = best;
                if (alpha >= beta) break;
            }
        } else {
            orderStatic(mv, n);
            if (ttMv >= 0) moveToFront(mv, n, ttMv);
            bestMove = mv[0];
            for (int i = 0; i < n; i++) {
                int v = solveMin(b.placedIndex(mv[i], BLACK), alpha, beta, ply + 1);
                if (timeUp) return best;
                if (v > best) { best = v; bestMove = mv[i]; }
                if (best > alpha) alpha = best;
                if (alpha >= beta) break;
            }
        }
        storeTT(idx, h, best, alpha0, beta0, bestMove);
        return best;
    }

    int solveMin(OurBoard b, int alpha, int beta, int ply) {
        return solveMin(b, alpha, beta, ply, -1L);
    }

    int solveMin(OurBoard b, int alpha, int beta, int ply, long myMoves) {
        searchNodes++;
        if ((searchNodes & 1023) == 0 && System.nanoTime() >= deadline) timeUp = true;
        if (timeUp) return beta;
        if (myMoves == -1L) myMoves = b.legalBits(WHITE);
        if (myMoves == 0) {
            long bm = b.legalBits(BLACK);
            if (bm == 0) return b.score();
            return solveMax(b, alpha, beta, ply + 1, bm);
        }
        final int alpha0 = alpha, beta0 = beta;
        long h = b.h;
        int idx = (int)(h & ttMask);
        int ttMv = -1;
        if (ttFlag[idx] != 0 && ttKey[idx] == h) {
            int v = ttVal[idx]; byte fl = ttFlag[idx];
            if (fl == TT_EXACT) return v;
            if (fl == TT_LOWER && v >= beta)  return v;
            if (fl == TT_UPPER && v <= alpha) return v;
            ttMv = ttMove[idx] & 0xFF;
        }
        int[] mv = moveBuf[ply];
        int n = OurBoard.bitsToIndexes(myMoves, mv);
        int best = 100000, bestMove;
        if (n > 1 && Long.bitCount(b.empty()) >= FF_MIN) {
            OurBoard[] kids = childBuf[ply];
            int[]      keys = keyBuf[ply];
            long[]  legals  = legalBuf[ply];
            for (int i = 0; i < n; i++) {
                kids[i] = b.placedIndex(mv[i], WHITE);
                long lm  = kids[i].legalBits(BLACK);
                keys[i]  = Long.bitCount(lm);
                legals[i] = lm;
            }
            ffSort(mv, kids, keys, legals, n);
            if (ttMv >= 0) moveFrontKids(mv, kids, legals, n, ttMv);
            bestMove = mv[0];
            for (int i = 0; i < n; i++) {
                int v = solveMax(kids[i], alpha, beta, ply + 1, legals[i]);
                if (timeUp) return best;
                if (v < best) { best = v; bestMove = mv[i]; }
                if (best < beta) beta = best;
                if (alpha >= beta) break;
            }
        } else {
            orderStatic(mv, n);
            if (ttMv >= 0) moveToFront(mv, n, ttMv);
            bestMove = mv[0];
            for (int i = 0; i < n; i++) {
                int v = solveMax(b.placedIndex(mv[i], WHITE), alpha, beta, ply + 1);
                if (timeUp) return best;
                if (v < best) { best = v; bestMove = mv[i]; }
                if (best < beta) beta = best;
                if (alpha >= beta) break;
            }
        }
        storeTT(idx, h, best, alpha0, beta0, bestMove);
        return best;
    }

    void storeTT(int idx, long h, int best, int a0, int b0, int mv) {
        byte fl = best <= a0 ? TT_UPPER : best >= b0 ? TT_LOWER : TT_EXACT;
        ttKey[idx] = h; ttVal[idx] = best; ttFlag[idx] = fl; ttMove[idx] = (byte) mv;
    }

    // ===== 中盤 PVS α-β =====

    int rootSearch(OurBoard root, int depth, int pv, int initAlpha, int initBeta) {
        int n = root.genLegal(BLACK, rootBuf);
        orderStatic(rootBuf, n);
        moveToFront(rootBuf, n, pv);
        int alpha = initAlpha, beta = initBeta;
        int best = rootBuf[0];
        int bestScore = -INF;
        for (int i = 0; i < n; i++) {
            OurBoard c = root.placedIndex(rootBuf[i], BLACK);
            int v;
            if (i == 0) {
                v = midMin(c, alpha, beta, depth - 1);
            } else {
                v = midMin(c, alpha, alpha + 1, depth - 1);
                if (!timeUp && v > alpha) v = midMin(c, alpha, beta, depth - 1);
            }
            if (timeUp) { rootScore = bestScore; return best; }
            if (v > bestScore) { bestScore = v; best = rootBuf[i]; }
            if (bestScore > alpha) alpha = bestScore;
            if (alpha >= beta) break;
        }
        rootScore = bestScore;
        return best;
    }

    int midMax(OurBoard b, int alpha, int beta, int depth) {
        searchNodes++;
        if ((searchNodes & 1023) == 0 && System.nanoTime() >= deadline) timeUp = true;
        if (timeUp) return alpha;
        if (b.isEnd()) return 1_000_000 * b.score();
        if (depth == 0) return evalMid(b);
        final int alpha0 = alpha, beta0 = beta;
        long h = b.h ^ ZSIDE;
        int idx = (int)(h & MT_MASK);
        int ttMv = -1;
        if (mtFlag[idx] != 0 && mtKey[idx] == h) {
            if (mtDepth[idx] >= depth) {
                int v = mtVal[idx]; byte fl = mtFlag[idx];
                if (fl == TT_EXACT) return v;
                if (fl == TT_LOWER && v >= beta)  return v;
                if (fl == TT_UPPER && v <= alpha) return v;
            }
            ttMv = mtMove[idx] & 0xFF;
        }
        int[] mv = moveBuf[depth];
        int n = b.genLegal(BLACK, mv);
        if (n == 0) return midMin(b, alpha, beta, depth - 1);
        orderStatic(mv, n);
        if (ttMv >= 0) moveToFront(mv, n, ttMv);
        int best = -INF, bestMove = mv[0];
        for (int i = 0; i < n; i++) {
            OurBoard c = b.placedIndex(mv[i], BLACK);
            int v;
            if (i == 0) {
                v = midMin(c, alpha, beta, depth - 1);
            } else {
                int d = (i >= LMR_MIN_MOVE && depth >= LMR_MIN_DEPTH) ? depth - 2 : depth - 1;
                v = midMin(c, alpha, alpha + 1, d);
                if (!timeUp && v > alpha) v = midMin(c, alpha, beta, depth - 1);
            }
            if (timeUp) return best > -INF ? best : alpha;
            if (v > best) { best = v; bestMove = mv[i]; }
            if (best > alpha) alpha = best;
            if (alpha >= beta) break;
        }
        storeMid(idx, h, best, alpha0, beta0, bestMove, depth);
        return best;
    }

    int midMin(OurBoard b, int alpha, int beta, int depth) {
        searchNodes++;
        if ((searchNodes & 1023) == 0 && System.nanoTime() >= deadline) timeUp = true;
        if (timeUp) return beta;
        if (b.isEnd()) return 1_000_000 * b.score();
        if (depth == 0) return evalMid(b);
        final int alpha0 = alpha, beta0 = beta;
        long h = b.h;
        int idx = (int)(h & MT_MASK);
        int ttMv = -1;
        if (mtFlag[idx] != 0 && mtKey[idx] == h) {
            if (mtDepth[idx] >= depth) {
                int v = mtVal[idx]; byte fl = mtFlag[idx];
                if (fl == TT_EXACT) return v;
                if (fl == TT_LOWER && v >= beta)  return v;
                if (fl == TT_UPPER && v <= alpha) return v;
            }
            ttMv = mtMove[idx] & 0xFF;
        }
        int[] mv = moveBuf[depth];
        int n = b.genLegal(WHITE, mv);
        if (n == 0) return midMax(b, alpha, beta, depth - 1);
        orderStatic(mv, n);
        if (ttMv >= 0) moveToFront(mv, n, ttMv);
        int best = INF, bestMove = mv[0];
        for (int i = 0; i < n; i++) {
            OurBoard c = b.placedIndex(mv[i], WHITE);
            int v;
            if (i == 0) {
                v = midMax(c, alpha, beta, depth - 1);
            } else {
                int d = (i >= LMR_MIN_MOVE && depth >= LMR_MIN_DEPTH) ? depth - 2 : depth - 1;
                v = midMax(c, beta - 1, beta, d);
                if (!timeUp && v < beta) v = midMax(c, alpha, beta, depth - 1);
            }
            if (timeUp) return best < INF ? best : beta;
            if (v < best) { best = v; bestMove = mv[i]; }
            if (best < beta) beta = best;
            if (alpha >= beta) break;
        }
        storeMid(idx, h, best, alpha0, beta0, bestMove, depth);
        return best;
    }

    void storeMid(int idx, long h, int best, int a0, int b0, int mv, int depth) {
        if (timeUp) return;
        byte fl = best <= a0 ? TT_UPPER : best >= b0 ? TT_LOWER : TT_EXACT;
        if (mtFlag[idx] == 0 || mtKey[idx] == h || depth >= mtDepth[idx]) {
            mtKey[idx] = h; mtVal[idx] = best;
            mtDepth[idx] = (byte) depth; mtFlag[idx] = fl; mtMove[idx] = (byte) mv;
        }
    }

    // ===== 評価関数 =====

    int evalMid(OurBoard b) {
        // 位置評価
        int pos = 0;
        long bb = b.black;
        while (bb != 0) { int k = Long.numberOfTrailingZeros(bb); bb &= bb-1; pos += W[k]; }
        long ww = b.white;
        while (ww != 0) { int k = Long.numberOfTrailingZeros(ww); ww &= ww-1; pos -= W[k]; }

        // フロンティア（空きに隣接する石数）
        long e = b.empty();
        long fm = 0;
        for (int d = 0; d < 8; d++) fm |= OurBoard.shift(e, OurBoard.DS[d], OurBoard.DM[d]);
        int front = Long.bitCount(b.black & fm) - Long.bitCount(b.white & fm);

        // 機動性
        int mob = Long.bitCount(b.legalBits(BLACK)) - Long.bitCount(b.legalBits(WHITE));

        // 潜在的機動性（相手石に隣接する空きマス数）
        long bAdj = 0, wAdj = 0;
        for (int d = 0; d < 8; d++) {
            bAdj |= OurBoard.shift(b.white, OurBoard.DS[d], OurBoard.DM[d]);
            wAdj |= OurBoard.shift(b.black, OurBoard.DS[d], OurBoard.DM[d]);
        }
        int potMob = Long.bitCount(bAdj & e) - Long.bitCount(wAdj & e);

        // 安定石差（角アンカー辺）
        int stab = stableDiff(b);

        return CPOS * pos + CMOB * mob + CFRONT * front + CSTAB * stab + CPOTMOB * potMob;
    }

    int stableDiff(OurBoard b) {
        int sb = 0, sw = 0;
        for (int ci = 0; ci < 4; ci++) {
            Color cc = b.get(CORNERS[ci]);
            if (cc != BLACK && cc != WHITE) continue;
            if (cc == BLACK) sb++; else sw++;
            for (int e = 0; e < 2; e++) {
                for (int k : EDGES_FROM_CORNER[ci * 2 + e]) {
                    if (b.get(k) == cc) { if (cc == BLACK) sb++; else sw++; } else break;
                }
            }
        }
        return sb - sw;
    }

    // ===== ムーブオーダリング =====

    void orderStatic(int[] a, int n) {
        for (int i = 1; i < n; i++) {
            int x = a[i], px = PRIO[x], j = i - 1;
            while (j >= 0 && PRIO[a[j]] < px) { a[j+1] = a[j]; j--; }
            a[j+1] = x;
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

    void ffSort(int[] mv, OurBoard[] kids, int[] keys, long[] legals, int n) {
        for (int i = 1; i < n; i++) {
            int m = mv[i], k = keys[i]; OurBoard c = kids[i]; long lg = legals[i];
            int j = i - 1;
            while (j >= 0 && keys[j] > k) {
                mv[j+1]=mv[j]; keys[j+1]=keys[j]; kids[j+1]=kids[j]; legals[j+1]=legals[j];
                j--;
            }
            mv[j+1]=m; keys[j+1]=k; kids[j+1]=c; legals[j+1]=lg;
        }
    }

    void moveFrontKids(int[] mv, OurBoard[] kids, long[] legals, int n, int v) {
        for (int i = 1; i < n; i++) {
            if (mv[i] == v) {
                int m = mv[i]; OurBoard c = kids[i]; long lg = legals[i];
                System.arraycopy(mv,    0, mv,    1, i);
                System.arraycopy(kids,  0, kids,  1, i);
                System.arraycopy(legals,0, legals,1, i);
                mv[0]=m; kids[0]=c; legals[0]=lg;
                return;
            }
        }
    }
}
