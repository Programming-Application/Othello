package p26x06;

import ap26.*;
import static ap26.Board.*;
import static ap26.Color.*;
import java.util.*;

/**
 * 応用杯向け最強オセロAIプレイヤー
 * 高速NegaScout探索、置換表(TT)、Move Orderingを1ファイルに内包。
 */
public class OurPlayer extends ap26.Player {
    static final String MY_NAME = "26X4";
    
    private final AdvancedEval eval;
    private final int depthLimit;
    private final TranspositionTable tt; // 置換表キャッシュ
    private Move move;
    private OurBoard board;

    public OurPlayer(Color color) {
        // 標準探索深さは4、終盤の残り手数が少なくなると自動的に完全読み切り(ソルバー)に切り替わります
        this(MY_NAME, color, new AdvancedEval(), 4);
    }

    public OurPlayer(String name, Color color, AdvancedEval eval, int depthLimit) {
        super(name, color);
        this.eval = eval;
        this.depthLimit = depthLimit;
        this.tt = new TranspositionTable();
        this.board = new OurBoard();
    }

    public OurPlayer(String name, Color color, int depthLimit) {
        this(name, color, new AdvancedEval(), depthLimit);
    }

    public void setBoard(Board board) {
        for (var i = 0; i < LENGTH; i++) {
            this.board.set(i, board.get(i));
        }
    }

    boolean isBlack() {
        return getColor() == BLACK;
    }

    @Override
    public Move think(Board board) {
        setBoard(board);

        List<Integer> legals = this.board.findNoPassLegalIndexes(getColor());
        if (legals.isEmpty()) {
            this.move = Move.ofPass(getColor());
        } else {
            // 常に黒(自分)視点で探索するためのクローン/反転
            OurBoard searchBoard = isBlack() ? this.board.clone() : this.board.flipped();
            this.move = null;

            // 残り空きマス数
            int currentStones = searchBoard.count(BLACK) + searchBoard.count(WHITE);
            int emptySquares = LENGTH - currentStones;

            // ★終盤のゴリ押し読み切り判定（チームの課題: 残り18手〜20手読みの実現）
            int currentDepthLimit = this.depthLimit;
            if (emptySquares <= 19) { 
                // 残り19手以下なら、置換表とNegaScoutの力で最後まで読み切る(ソルバー化)
                currentDepthLimit = emptySquares; 
            }

            // アルゴリズムを通常のαβから超高速な「NegaScout法（PVS）」へシフト
            negaScout(searchBoard, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, 0, currentDepthLimit);

            this.move = this.move.colored(getColor());

            // 非合法手フォールバック（安全対策）
            if (!legals.contains(this.move.getIndex())) {
                this.move = new Move(legals.get(0), getColor());
            }
        }

        this.board = this.board.placed(this.move);
        return this.move;
    }

    /**
     * 最適化アルゴリズム: NegaScout（主要変化探索）
     */
    private float negaScout(Board board, float alpha, float beta, int depth, int limit) {
        // 1. 置換表(TT)のチェックによる高速スキップ（重複局面の完全排除）
        String boardKey = board.toString();
        TranspositionTable.TTEntry entry = tt.get(boardKey);
        if (entry != null && entry.depth >= (limit - depth)) {
            return entry.value;
        }

        if (board.isEnd() || depth >= limit) {
            return this.eval.value(board);
        }

        List<Move> moves = board.findLegalMoves(BLACK);
        // 2. 着手並び替え(Move Ordering)の適用
        moves = sortMoves(moves);

        if (depth == 0) {
            this.move = moves.get(0); // フォールバック
        }

        float adaptiveBeta = beta;
        float bestValue = Float.NEGATIVE_INFINITY;

        for (int i = 0; i < moves.size(); i++) {
            Move nextMove = moves.get(i);
            Board nextBoard = board.placed(nextMove);

            float v;
            if (i == 0) {
                // 最善手予想の筆頭は通常の広い窓で探索
                v = -minSearchNega(nextBoard, -adaptiveBeta, -alpha, depth + 1, limit);
            } else {
                // 2番目以降の手は「先頭の手を超えられるか？」だけを調べる狭い窓（零窓）でスカウト
                v = -minSearchNega(nextBoard, -alpha - 1, -alpha, depth + 1, limit);
                // 予想外に強い手が見つかった場合のみ、正しい窓で再探索(リサーチ)
                if (alpha < v && v < beta) {
                    v = -minSearchNega(nextBoard, -beta, -v, depth + 1, limit);
                }
            }

            bestValue = Math.max(bestValue, v);
            if (v > alpha) {
                alpha = v;
                if (depth == 0) {
                    this.move = nextMove;
                }
            }

            // αβカット
            if (alpha >= beta) {
                break;
            }
            adaptiveBeta = alpha + 1;
        }

        // 結果を置換表に記録して再利用可能に
        tt.put(boardKey, bestValue, limit - depth);
        return alpha;
    }

    private float minSearchNega(Board board, float alpha, float beta, int depth, int limit) {
        if (board.isEnd() || depth >= limit) {
            return this.eval.value(board);
        }

        List<Move> moves = board.findLegalMoves(WHITE);
        moves = sortMoves(moves);

        float score = Float.POSITIVE_INFINITY;
        for (Move nextMove : moves) {
            Board nextBoard = board.placed(nextMove);
            float v = negaScout(nextBoard, alpha, beta, depth + 1, limit);
            score = Math.min(score, v);
            beta = Math.min(beta, v);
            if (alpha >= beta) {
                break; // カット
            }
        }
        return score;
    }

    /**
     * 高速な着手並び替え(Move Ordering)
     * 親クラスにないためメソッド名を独自のものに変更してエラーを回避
     */
    private List<Move> sortMoves(List<Move> moves) {
        if (moves.size() <= 1) return moves;

        List<Move> ordered = new ArrayList<>(moves);
        // 簡易位置評価(AdvancedEval.M_STATIC)を使って、評価の高い着手候補を先頭にソート
        ordered.sort((m1, m2) -> {
            float score1 = AdvancedEval.M_STATIC[m1.getRow()][m1.getCol()];
            float score2 = AdvancedEval.M_STATIC[m2.getRow()][m2.getCol()];
            return Float.compare(score2, score1); // 降順
        });
        return ordered;
    }

    // =========================================================================
    // インナークラス: 超高速置換表（メモ化キャッシュ）
    // =========================================================================
    static class TranspositionTable {
        static class TTEntry {
            float value;
            int depth;
            TTEntry(float v, int d) { this.value = v; this.depth = d; }
        }
        
        private final Map<String, TTEntry> cache = new HashMap<>(16384);

        public void put(String key, float value, int depth) {
            cache.put(key, new TTEntry(value, depth));
        }

        public TTEntry get(String key) {
            return cache.get(key);
        }
    }
}