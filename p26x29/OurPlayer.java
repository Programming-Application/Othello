package p26x29;

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

  // 旧特徴係数 (Phase 4b: 座標降下で最適化)。
  //   value = CPOS*位置 + CMOB*着手可能数差 + CFRONT*フロンティア差 + CSTAB*確定石差
  // 現在は攻略ルート対策として、空き数ごとの段階別係数を value() 内で使う。
  static final int CPOS = 10, CMOB = 31, CFRONT = -20, CSTAB = 20;

  // 角から伸びる2辺 (角自身を除く)。確定石の連結走査用。
  static final int[][] EDGES_FROM_CORNER = {
      {1, 2, 3, 4, 5}, {6, 12, 18, 24, 30},      // a1
      {4, 3, 2, 1, 0}, {11, 17, 23, 29, 35},     // f1
      {31, 32, 33, 34, 35}, {24, 18, 12, 6, 0},  // a6
      {34, 33, 32, 31, 30}, {29, 23, 17, 11, 5}, // f6
  };
  static final int[] CORNERS = {0, 5, 30, 35};
  static final long CORNER_MASK = (1L << 0) | (1L << 5) | (1L << 30) | (1L << 35);
  static final int[] X_SQUARES = {7, 10, 25, 28};
  static final int[] C_SQUARES = {1, 6, 4, 11, 24, 31, 29, 34};
  static final int[] ADJ_CORNER = new int[LENGTH];
  static {
    java.util.Arrays.fill(ADJ_CORNER, -1);
    ADJ_CORNER[1] = ADJ_CORNER[6] = ADJ_CORNER[7] = 0;
    ADJ_CORNER[4] = ADJ_CORNER[10] = ADJ_CORNER[11] = 5;
    ADJ_CORNER[24] = ADJ_CORNER[25] = ADJ_CORNER[31] = 30;
    ADJ_CORNER[28] = ADJ_CORNER[29] = ADJ_CORNER[34] = 35;
  }

  /** 非終局の評価 (BLACK 視点)。空き数に応じて mobility 過信を抑え、parity と危険マス文脈を強める。*/
  int value(OurBoard b) {
    // 位置: 黒石は +W, 白石は -W (set bit 走査, get() を使わない)
    int pos = 0;
    long bb = b.black;
    while (bb != 0) { int k = Long.numberOfTrailingZeros(bb); bb &= bb - 1; pos += W[k]; }
    long ww = b.white;
    while (ww != 0) { int k = Long.numberOfTrailingZeros(ww); ww &= ww - 1; pos -= W[k]; }
    long e = b.empty();
    int empties = Long.bitCount(e);
    int mob = qualityMobility(b, BLACK) - qualityMobility(b, WHITE);
    int front = safeFrontier(b, e);
    int stable = stableDiff(b);
    int parity = parityScore(b, e);
    int danger = dangerSquareScore(b);

    int wPos, wMob, wFront, wStable, wParity, wDanger;
    if (empties >= 29) {
      // 序盤: 辺・角争いを急がず、悪い X/C と石の露出を避ける。
      wPos = 10; wMob = 22; wFront = -14; wStable = 12; wParity = 6; wDanger = 30;
    } else if (empties >= 25) {
      // 空き25付近: 評価誤差が最も危険。parity と危険手誘導を強く見る。
      wPos = 8; wMob = 18; wFront = -10; wStable = 18; wParity = 18; wDanger = 40;
    } else if (empties >= 21) {
      // WLD直前: 勝敗証明へ入る形を優先し、短期mobilityの重みをさらに落とす。
      wPos = 8; wMob = 14; wFront = -8; wStable = 24; wParity = 24; wDanger = 46;
    } else {
      wPos = 8; wMob = 10; wFront = -6; wStable = 30; wParity = 28; wDanger = 42;
    }
    return wPos * pos + wMob * mob + wFront * front + wStable * stable
        + wParity * parity + wDanger * danger;
  }

  int qualityMobility(OurBoard b, Color c) {
    long moves = b.legalBits(c);
    int score = 0;
    while (moves != 0) {
      int k = Long.numberOfTrailingZeros(moves);
      moves &= moves - 1;
      int q = 2;
      if (isCorner(k)) {
        q += 10;
      } else if (isX(k)) {
        q += dangerMoveValue(b, c, k, -5, 3);
      } else if (isC(k)) {
        q += dangerMoveValue(b, c, k, -3, 2);
      } else if (isEdge(k)) {
        q += 2;
      }
      score += q;
    }
    return score;
  }

  int dangerMoveValue(OurBoard b, Color c, int k, int emptyPenalty, int ownedBonus) {
    int corner = ADJ_CORNER[k];
    if (corner < 0)
      return 0;
    Color cc = b.get(corner);
    if (cc == NONE)
      return emptyPenalty;
    return cc == c ? ownedBonus : -1;
  }

  int safeFrontier(OurBoard b, long empty) {
    long fm = neighborMask(empty);
    return frontierPenalty(b.black & fm) - frontierPenalty(b.white & fm);
  }

  int frontierPenalty(long stones) {
    int penalty = 0;
    while (stones != 0) {
      int k = Long.numberOfTrailingZeros(stones);
      stones &= stones - 1;
      penalty += isEdge(k) ? 1 : 2;
    }
    return penalty;
  }

  int dangerSquareScore(OurBoard b) {
    int score = 0;
    for (int k : X_SQUARES)
      score += occupiedDangerValue(b, k, -6, 3);
    for (int k : C_SQUARES)
      score += occupiedDangerValue(b, k, -3, 2);
    return score;
  }

  int occupiedDangerValue(OurBoard b, int k, int emptyPenalty, int ownedBonus) {
    Color owner = b.get(k);
    if (owner != BLACK && owner != WHITE)
      return 0;
    int corner = ADJ_CORNER[k];
    Color cc = corner >= 0 ? b.get(corner) : NONE;
    int v = (cc == owner) ? ownedBonus : (cc == NONE ? emptyPenalty : -1);
    return owner == BLACK ? v : -v;
  }

  int parityScore(OurBoard b, long empty) {
    long rest = empty;
    int score = 0;
    while (rest != 0) {
      long seed = rest & -rest;
      long region = floodEmpty(seed, rest);
      rest &= ~region;
      if ((Long.bitCount(region) & 1) == 0)
        continue;
      long around = neighborMask(region);
      int blackAdj = Long.bitCount(around & b.black);
      int whiteAdj = Long.bitCount(around & b.white);
      score += blackAdj > whiteAdj ? 1 : blackAdj < whiteAdj ? -1 : 0;
    }
    return score;
  }

  long floodEmpty(long seed, long empty) {
    long seen = seed;
    long cur = seed;
    while (cur != 0) {
      long next = neighborMask(cur) & empty & ~seen;
      seen |= next;
      cur = next;
    }
    return seen;
  }

  long neighborMask(long bits) {
    long m = 0;
    for (int d = 0; d < 8; d++)
      m |= OurBoard.shift(bits, OurBoard.DS[d], OurBoard.DM[d]);
    return m;
  }

  boolean isCorner(int k) {
    return ((1L << k) & CORNER_MASK) != 0;
  }

  boolean isEdge(int k) {
    int r = k / SIZE, c = k % SIZE;
    return r == 0 || r == SIZE - 1 || c == 0 || c == SIZE - 1;
  }

  boolean isX(int k) {
    return k == 7 || k == 10 || k == 25 || k == 28;
  }

  boolean isC(int k) {
    return ADJ_CORNER[k] >= 0 && !isX(k);
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

  /** 空きマス数がこれ以下なら終局まで厳密に読み切る (完全読み・最大石差)。fastest-first + 予算引上げで 20。*/
  static int ENDGAME_THRESHOLD = 22;

  /**
   * 空き ENDGAME_THRESHOLD < e <= WLD_THRESHOLD では「WLD(勝敗のみ)を狭窓で証明」して
   * 勝ち>引分>負け の手を選ぶ (石差exactは≤20で行う贅沢品)。持続TT利用・時間内に
   * 証明できなければヒューリスティック探索へフォールバック。
   *
   * <p>24 に設定 (Step 5f, A/B実測): 実戦到達の24空きWLDは avg7.2s/p90 20.2s で 25s予算なら97%解ける
   * (以前の「24は危険」はランダムプレイアウトの歪な局面の測り誤り)。余剰時間(エンジンは58s中~20sしか
   * 使わない)を終盤移行に回し、cap25s + 序盤cap2s と併せて新スケジュールとして A/B したところ、
   * 現状(thr22/cap15s)に head-to-head で W22 L8 (+14)、かつ全敗が「24空きで既に理論負け」=不可避だった。
   */
  public static int WLD_THRESHOLD = 24;

  /**
   * 終盤(WLD/完全読み)手の持ち時間上限。エンジンは持ち時間を大幅に余すので、最重要の終盤移行手に
   * 厚く配分する。25s に設定: 24空きWLDが実戦97%この範囲で解ける (max~24s)。残りは remaining/2 でガード、
   * deadline で 60s 超過=即時負けは防止。
   */
  static final long ENDGAME_BUDGET_CAP_NANOS = 25_000_000_000L;

  /** 序盤(空き>iWldThr)の1手予算上限。序盤は深さが飽和し手が変わらないので時間を削り終盤へ回す。*/
  static final long OPENING_BUDGET_CAP_NANOS = 2_000_000_000L;

  // インスタンス単位で上書き可能なスケジュール設定 (A/B計測用。既定は上記 static と同値)。
  public int iEndThr = ENDGAME_THRESHOLD;            // exact 完全読みしきい値
  public int iWldThr = WLD_THRESHOLD;                // WLD 証明しきい値
  public long iEgCap = ENDGAME_BUDGET_CAP_NANOS;     // 終盤手の持ち時間上限
  public long iOpenCap = OPENING_BUDGET_CAP_NANOS;   // >0: 空き>iWldThr の1手予算上限

  // --- 計測/ベンチ用 (提出時は無害な診断フィールド) ---
  public static long searchNodes = 0;     // 探索ノード総数
  public static int lastReachedDepth = 0; // 直近の手で到達した探索深さ
  public static int maxReachedDepth = 0;  // 計測区間での最大到達深さ
  public static int endgameFallback = 0;  // 終盤完全読みが期限切れでIDにフォールバックした回数
  public static int wldProven = 0;        // WLD証明が時間内に完了した回数
  public static int wldFallback = 0;      // WLD証明が期限切れでフォールバックした回数
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
  int wldRootValue = 0;   // 直近 wldRoot の root WLD 値 (+1勝/0分/-1負)。負けなら α-β で粘る判断に使う

  // 深さごとの合法手バッファ (アロケーション回避)。index = depthLeft
  // 第1次元は再帰の深さ(ply)。終盤ソルバの ply はパスでも増えるため、着手数+パス数の最悪
  // (≈ 2×空き) を収容できるよう 80 に拡大 (40 だと変形盤の多パス手順で配列外参照→例外)。
  static final int MAX_PLY = 80;
  final int[][] moveBuf = new int[MAX_PLY][40];
  final int[] rootBuf = new int[40];

  // 終盤 fastest-first 順序付け用バッファ (ply 別に子盤面と相手mobilityキーを保持)
  public static int FF_MIN = 7; // 残り空きがこれ以上なら fastest-first を使う (検証で切替)
  final OurBoard[][] childBuf = new OurBoard[MAX_PLY][40];
  final int[][] keyBuf = new int[MAX_PLY][40];
  // FF で計算した子の手番側 legalBits を保持し、その子の再帰呼び出しに引き継いで
  // 二重計算を避ける (子は自分の入口で再度 legalBits を計算していた)。
  final long[][] legalBuf = new long[MAX_PLY][40];

  // --- 終盤完全読み用 置換表 (Zobrist hashing) ---
  // 盤面セルのハッシュは OurBoard.h (増分更新)。手番分だけここで XOR する。
  static final long ZSIDE = 0x9E3779B97F4A7C15L; // 手番(黒番)用の固定乱数
  public static int TT_BITS = 20; // 終盤TTのサイズ指数 (オフライン解では拡大可)。本番は20=1M。
  static final byte TT_EXACT = 1, TT_LOWER = 2, TT_UPPER = 3;
  final int ttMask;
  final long[] ttKey;
  final int[] ttVal;
  final byte[] ttFlag;
  final byte[] ttMove;

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
    int sz = 1 << TT_BITS;
    ttMask = sz - 1;
    ttKey = new long[sz];
    ttVal = new int[sz];
    ttFlag = new byte[sz];
    ttMove = new byte[sz];
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
        if (empties <= iWldThr) {
          // 完全読み/WLD証明には厚めの持ち時間を割く (残りの半分か上限のいずれか小さい方)
          long eg = Math.min(remaining / 2, iEgCap);
          if (eg > budget)
            budget = eg;
        } else if (iOpenCap > 0 && budget > iOpenCap) {
          budget = iOpenCap; // 序盤は深さが飽和しているので時間を削り終盤へ回す
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

    int book = openingBookMove(root, empties);
    if (book >= 0)
      return book;

    // 終盤完全読み: 空きマスが少なければ終局まで厳密に最終石差を最大化
    if (empties <= iEndThr) {
      timeUp = false;
      int mv = solveExactRoot(root, best);
      if (!timeUp && mv >= 0) {
        lastReachedDepth = empties;
        if (empties > maxReachedDepth)
          maxReachedDepth = empties;
        return mv;
      }
      endgameFallback++; // 期限切れ (しきい値が大きすぎた場合の保険) → 通常 ID にフォールバック
    } else if (empties <= iWldThr) {
      // WLD 証明ゾーン: 勝敗のみ狭窓で証明し win>draw>loss の手を選ぶ (持続TT利用)
      timeUp = false;
      int mv = wldRoot(root);
      if (!timeUp && mv >= 0) {
        wldProven++;
        // wldRoot は同じ勝敗分類内で eval/routeBonus によるタイブレーク済み。
        // 全手負けでも通常IDへ落とすと、WLD入口で最終石差の悪い手を選ぶことがある。
        lastReachedDepth = empties;
        if (empties > maxReachedDepth)
          maxReachedDepth = empties;
        return mv;
      } else {
        wldFallback++; // 時間内に証明できず → ヒューリスティック ID へ
      }
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

  int openingBookMove(OurBoard root, int empties) {
    if (empties == 32) {
      long blackPattern = bit("c3") | bit("d4");
      long whitePattern = bit("d3") | bit("c4");
      int c5 = Move.parseIndex("c5");
      if (root.black == blackPattern && root.white == whitePattern && root.isLegalMove(c5, BLACK))
        return c5;
    }

    // Known p26x42 trap line from the standard opening:
    // BLACK c5, WHITE b3 leaves all legal BLACK moves on rank 2. The normal
    // 2s heuristic search drifts to e2, after which the game reaches a proven
    // losing WLD entrance by move 9. c2 keeps the line materially better in
    // direct p26x42 tests and is also the highest static route score here.
    long blackPattern = bit("c4") | bit("d4") | bit("c5");
    long whitePattern = bit("b3") | bit("c3") | bit("d3");
    int c2 = Move.parseIndex("c2");
    if (empties == 30 && root.black == blackPattern && root.white == whitePattern && root.isLegalMove(c2, BLACK))
      return c2;

    int a4 = Move.parseIndex("a4");
    if (empties == 28
        && root.black == bits("c2", "c3", "c5")
        && root.white == bits("b3", "d3", "c4", "d4", "d5")
        && root.isLegalMove(a4, BLACK))
      return a4;

    int e3 = Move.parseIndex("e3");
    if (empties == 26
        && root.black == bits("c2", "b3", "c3", "a4")
        && root.white == bits("d3", "c4", "d4", "c5", "d5", "c6")
        && root.isLegalMove(e3, BLACK))
      return e3;

    int d6 = Move.parseIndex("d6");
    if (empties == 24
        && root.black == bits("b3", "d3", "e3", "a4")
        && root.white == bits("c1", "c2", "c3", "c4", "d4", "c5", "d5", "c6")
        && root.isLegalMove(d6, BLACK))
      return d6;

    int b2 = Move.parseIndex("b2");
    if (empties == 22
        && root.black == bits("d3", "e3", "a4", "d4", "d5", "d6")
        && root.white == bits("c1", "c2", "a3", "b3", "c3", "c4", "c5", "c6")
        && root.isLegalMove(b2, BLACK))
      return b2;

    return -1;
  }

  static long bit(String sq) {
    return 1L << Move.parseIndex(sq);
  }

  static long bits(String... squares) {
    long m = 0;
    for (String sq : squares)
      m |= bit(sq);
    return m;
  }

  /** ルートの 1 反復。pv (前反復の最善手) を先頭に試す。PVS。*/
  int rootSearch(OurBoard root, int depth, int pv) {
    int n = root.genLegal(BLACK, rootBuf);
    orderStatic(rootBuf, n);
    moveToFront(rootBuf, n, pv);

    int alpha = -INF, beta = INF;
    int best = rootBuf[0];
    int empties = Long.bitCount(root.empty());
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
      v += routeBonus(rootBuf[i], c, empties);
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

  /**
   * WLD 証明ルート: 狭窓 [-1,+1] で各手の勝敗を評価し、勝ち>引分>負け で最善手を返す。
   * 葉は solveMin が終局まで読む (αβ+持続TT)。時間切れなら -1。
   */
  int wldRoot(OurBoard root) {
    int n = root.genLegal(BLACK, rootBuf);
    orderStatic(rootBuf, n);
    int best = -2, bestTie = -INF, bestMove = rootBuf[0];
    for (int i = 0; i < n; i++) {
      OurBoard c = root.placedIndex(rootBuf[i], BLACK);
      int v = Integer.signum(solveMin(c, -1, 1, 1)); // この手を指したときの勝敗(黒視点)
      if (timeUp)
        return -1;
      int tie = eval.value(c) + routeBonus(rootBuf[i], c, Long.bitCount(root.empty()));
      if (v > best || (v == best && tie > bestTie)) {
        best = v;
        bestTie = tie;
        bestMove = rootBuf[i];
      }
    }
    wldRootValue = best; // +1勝/0分/-1負(全手敗)。負けなら呼び側で α-β に切替
    return bestMove;
  }

  int routeBonus(int move, OurBoard child, int emptiesBeforeMove) {
    if (emptiesBeforeMove <= iEndThr)
      return 0;

    int bonus = 0;
    long childEmpty = child.empty();

    if (emptiesBeforeMove >= 29) {
      // 序盤は辺の小競り合いを急がず、終盤の奇数領域を作る形を少し優先する。
      if (isNonCornerEdge(move))
        bonus -= 24;
      bonus += eval.parityScore(child, childEmpty) * 24;
    } else if (emptiesBeforeMove >= 25) {
      // 空き25付近は最大の勝負所。parity と X/C 誘導をルートで明示的に押す。
      bonus += eval.parityScore(child, childEmpty) * 70;
      bonus += eval.dangerSquareScore(child) * 18;
      bonus += opponentDangerPressure(child) * 35;
    } else if (emptiesBeforeMove >= 21) {
      // WLD入口では勝敗分類が同じなら、安全な形と安定石を優先する。
      bonus += eval.parityScore(child, childEmpty) * 45;
      bonus += eval.stableDiff(child) * 16;
      bonus += eval.dangerSquareScore(child) * 12;
    }

    return bonus;
  }

  int opponentDangerPressure(OurBoard child) {
    long wm = child.legalBits(WHITE);
    if ((wm & MyEval.CORNER_MASK) != 0)
      return -12;
    int pressure = 0;
    while (wm != 0) {
      int k = Long.numberOfTrailingZeros(wm);
      wm &= wm - 1;
      if ((eval.isX(k) || eval.isC(k)) && MyEval.ADJ_CORNER[k] >= 0 && child.get(MyEval.ADJ_CORNER[k]) == NONE)
        pressure++;
      else
        pressure--;
    }
    return pressure;
  }

  boolean isNonCornerEdge(int k) {
    return eval.isEdge(k) && !eval.isCorner(k);
  }

  /** ベンチ用: BLACK 手番の局面の最終石差(最善応酬)を厳密に計算する。*/
  public int benchSolve(OurBoard root) {
    this.deadline = Long.MAX_VALUE;
    this.timeUp = false;
    return solveMax(root, -1000, 1000, 0);
  }

  /**
   * オフライン用: BLACK 手番の局面を窓[alpha,beta]で終局まで解く(αβ+fastest-first+持続TT)。
   * WLD は alpha=-1,beta=1。budgetNanos>0 で時間制限、期限切れは Integer.MIN_VALUE。
   */
  public int solveFromStart(OurBoard root, int alpha, int beta, long budgetNanos) {
    this.deadline = (budgetNanos <= 0) ? Long.MAX_VALUE : System.nanoTime() + budgetNanos;
    this.timeUp = false;
    int v = solveMax(root, alpha, beta, 0);
    return this.timeUp ? Integer.MIN_VALUE : v;
  }

  // BLACK 手番: 最終石差 (BLACK-WHITE) を最大化
  int solveMax(OurBoard b, int alpha, int beta, int ply) {
    return solveMax(b, alpha, beta, ply, -1L); // -1 = 合法手未計算
  }

  // myMoves に親が計算済みの黒 legalBits を渡せる (FF の子)。-1 なら自分で計算する。
  int solveMax(OurBoard b, int alpha, int beta, int ply, long myMoves) {
    searchNodes++;
    if ((searchNodes & 1023) == 0 && System.nanoTime() >= deadline)
      timeUp = true;
    if (timeUp)
      return alpha;

    // 合法手は一度だけ生成し、終局/パス判定と着手列挙に使い回す
    // (旧: isEnd() と genLegal() が手番側 legalBits を二重計算していた)。
    if (myMoves == -1L) myMoves = b.legalBits(BLACK);
    if (myMoves == 0) {
      if (b.legalBits(WHITE) == 0)
        return b.score();                         // 両者着手不能 → 終局
      return solveMin(b, alpha, beta, ply + 1);   // パス
    }

    final int alpha0 = alpha, beta0 = beta;
    long h = hash(b, true);
    int idx = (int) (h & ttMask);
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
    int n = OurBoard.bitsToIndexes(myMoves, mv);

    int best = -100000, bestMove;
    if (n > 1 && Long.bitCount(b.empty()) >= FF_MIN) {
      // fastest-first: 子盤面を作り、相手(WHITE)の着手可能数が少ない順に
      OurBoard[] kids = childBuf[ply];
      int[] keys = keyBuf[ply];
      long[] legals = legalBuf[ply];
      for (int i = 0; i < n; i++) {
        kids[i] = b.placedIndex(mv[i], BLACK);
        long lm = kids[i].legalBits(WHITE);
        keys[i] = Long.bitCount(lm);
        legals[i] = lm; // 子の WHITE legalBits を再帰へ引き継ぐ
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
    store(idx, h, best, alpha0, beta0, bestMove);
    return best;
  }

  /** 相手mobility(keys)昇順に mv/kids/legals を挿入ソート。*/
  void ffSort(int[] mv, OurBoard[] kids, int[] keys, long[] legals, int n) {
    for (int i = 1; i < n; i++) {
      int m = mv[i], k = keys[i];
      OurBoard c = kids[i];
      long lg = legals[i];
      int j = i - 1;
      while (j >= 0 && keys[j] > k) {
        mv[j + 1] = mv[j]; keys[j + 1] = keys[j]; kids[j + 1] = kids[j]; legals[j + 1] = legals[j];
        j--;
      }
      mv[j + 1] = m; keys[j + 1] = k; kids[j + 1] = c; legals[j + 1] = lg;
    }
  }

  /** 値 v の手を kids/legals 連動で先頭へ。*/
  void moveFrontKids(int[] mv, OurBoard[] kids, long[] legals, int n, int v) {
    for (int i = 1; i < n; i++) {
      if (mv[i] == v) {
        int m = mv[i];
        OurBoard c = kids[i];
        long lg = legals[i];
        System.arraycopy(mv, 0, mv, 1, i);
        System.arraycopy(kids, 0, kids, 1, i);
        System.arraycopy(legals, 0, legals, 1, i);
        mv[0] = m; kids[0] = c; legals[0] = lg;
        return;
      }
    }
  }

  // WHITE 手番: 最終石差を最小化
  int solveMin(OurBoard b, int alpha, int beta, int ply) {
    return solveMin(b, alpha, beta, ply, -1L); // -1 = 合法手未計算
  }

  // myMoves に親が計算済みの白 legalBits を渡せる (FF の子)。-1 なら自分で計算する。
  int solveMin(OurBoard b, int alpha, int beta, int ply, long myMoves) {
    searchNodes++;
    if ((searchNodes & 1023) == 0 && System.nanoTime() >= deadline)
      timeUp = true;
    if (timeUp)
      return beta;

    if (myMoves == -1L) myMoves = b.legalBits(WHITE);
    if (myMoves == 0) {
      if (b.legalBits(BLACK) == 0)
        return b.score();                         // 両者着手不能 → 終局
      return solveMax(b, alpha, beta, ply + 1);   // パス
    }

    final int alpha0 = alpha, beta0 = beta;
    long h = hash(b, false);
    int idx = (int) (h & ttMask);
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
    int n = OurBoard.bitsToIndexes(myMoves, mv);

    int best = 100000, bestMove;
    if (n > 1 && Long.bitCount(b.empty()) >= FF_MIN) {
      OurBoard[] kids = childBuf[ply];
      int[] keys = keyBuf[ply];
      long[] legals = legalBuf[ply];
      for (int i = 0; i < n; i++) {
        kids[i] = b.placedIndex(mv[i], WHITE);
        long lm = kids[i].legalBits(BLACK); // 相手(BLACK)mobility
        keys[i] = Long.bitCount(lm);
        legals[i] = lm; // 子の BLACK legalBits を再帰へ引き継ぐ
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
