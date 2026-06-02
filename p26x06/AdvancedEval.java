package p26x06;

import ap26.*;
import static ap26.Board.*;
import static ap26.Color.*;

/**
 * 応用杯向け：進行度・開放度・機動性・石数連動型の最強総合評価関数クラス
 */
public class AdvancedEval {
    
    // 序盤用の重み (石の総数 12個以下) : 辺に急いで乗らないように低め設定
    private static final float[][] M_EARLY = {
        { 80, -20,  5,  5, -20,  80 },
        {-20, -40, -3, -3, -40, -20 },
        {  5,  -3,  1,  1,  -3,   5 },
        {  5,  -3,  1,  1,  -3,   5 },
        {-20, -40, -3, -3, -40, -20 },
        { 80, -20,  5,  5, -20,  80 }
    };

    // 中盤用の重み (石の総数 13個以上) : 辺の確保とX・C打ちの徹底排除
    private static final float[][] M_MID = {
        { 120, -40, 20, 20, -40, 120 },
        {-40, -60, -5, -5, -60, -40 },
        { 20,  -5,  3,  3,  -5,  20 },
        { 20,  -5,  3,  3,  -5,  20 },
        {-40, -60, -5, -5, -60, -40 },
        { 120, -40, 20, 20, -40, 120 }
    };

    // 課題1f(着手並び替え)の簡易ソートで利用するための静的マトリクス
    public static final float[][] M_STATIC = {
        { 10,  10, 10, 10,  10, 10 },
        { 10,  -5,  1,  1,  -5, 10 },
        { 10,   1,  1,  1,   1, 10 },
        { 10,   1,  1,  1,   1, 10 },
        { 10,  -5,  1,  1,  -5, 10 },
        { 10,  10, 10, 10,  10, 10 }
    };

    public float value(Board board) {
        if (board.isEnd()) {
            return 1000000f * board.score(); // 終局時は純粋な勝敗スコア
        }

        int nb = board.count(BLACK);
        int nw = board.count(WHITE);
        int totalStones = nb + nw;
        int emptySquares = LENGTH - totalStones;

        // 各プレイヤーの合法手数（機動性 / Mobility）
        int lb = board.findLegalMoves(BLACK).size();
        int lw = board.findLegalMoves(WHITE).size();

        float matrixScore = 0;
        float opennessScore = 0;

        // 1. 進行度による位置評価マトリクスの切り替え
        float[][] currentM = (totalStones <= 12) ? M_EARLY : M_MID;

        // 盤面全体を走査
        for (int k = 0; k < LENGTH; k++) {
            Color c = board.get(k);
            if (c == NONE) continue;

            int row = k / SIZE;
            int col = k % SIZE;

            if (c == BLACK) {
                matrixScore += currentM[row][col];
                opennessScore += calculateOpenness(board, k);
            } else if (c == WHITE) {
                matrixScore -= currentM[row][col];
                opennessScore -= calculateOpenness(board, k);
            }
        }

        // 2. 進行度に応じたパラメータ wi の決定（数式への適用）
        float w_matrix, w_mobility, w_openness, w_count;

        if (totalStones <= 12) {
            // 【序盤】マスの位置、かつ「石を裏返さない(開放度抑制)」「機動性の確保」
            w_matrix   = 1.0f;
            w_mobility = 10.0f;  // 自分の合法手を広げ、相手を狭める
            w_openness = -5.0f;  // 開放度の高い(周囲が空いている)着手を徹底的に嫌う
            w_count    = -2.0f;  // 自分の石数は「少ない方」が良い（定石）
        } else if (emptySquares > 18) {
            // 【中盤】角の奪い合いフェーズ。位置評価と相手の機動性奪取を最大化
            w_matrix   = 2.5f;
            w_mobility = 15.0f;
            w_openness = -1.0f;
            w_count    = 0.0f;
        } else {
            // 【終盤（完全読み切りの一歩手前）】純粋な石の数を増やしに行く
            w_matrix   = 0.2f;
            w_mobility = 2.0f;
            w_openness = 0.0f;
            w_count    = 20.0f;  // 石の多さが最優先
        }

        return (w_matrix * matrixScore) 
             + (w_mobility * (lb - lw)) 
             + (w_openness * opennessScore) 
             + (w_count * (nb - nw));
    }

    /**
     * 各石の「開放度（周囲8マスにある空きマスの数）」を計算する
     */
    private int calculateOpenness(Board board, int k) {
        int row = k / SIZE;
        int col = k % SIZE;
        int openCount = 0;

        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) continue;
                int nr = row + dr;
                int nc = col + dc;
                if (nr >= 0 && nr < SIZE && nc >= 0 && nc < SIZE) {
                    if (board.get(nr * SIZE + nc) == NONE) {
                        openCount++;
                    }
                }
            }
        }
        return openCount;
    }
}