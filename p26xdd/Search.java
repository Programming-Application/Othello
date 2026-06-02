package p26xdd;

import ap26.Color;
import ap26.Move;

import java.util.List;

final class Search {
    private static final float WIN_SCORE = 1_000_000.0f;
    private static final int ENDGAME_EMPTY_THRESHOLD = 10;

    private final MyEval eval;
    private Move bestMove;

    Search(MyEval eval) {
        this.eval = eval;
    }

    Move findBestMove(MyBoard board, Color color, long accumulatedThinkNanos) {
        List<Move> legalMoves = board.findLegalMoves(color);
        Move fallback = legalMoves.get(0);
        if (fallback.isPass()) {
            return fallback;
        }

        this.bestMove = fallback;
        TimeManager timeManager = TimeManager.forMove(accumulatedThinkNanos, board.countEmptySquares());
        int maxDepth = maxDepth(board.countEmptySquares());

        for (int depth = 1; depth <= maxDepth; depth++) {
            if (timeManager.shouldStop()) {
                break;
            }

            try {
                this.bestMove = searchRoot(board, color, depth, timeManager);
            } catch (TimeUpException ignored) {
                break;
            }
        }

        return this.bestMove.colored(color);
    }

    private Move searchRoot(MyBoard board, Color color, int depth, TimeManager timeManager) {
        checkTime(timeManager);

        Move bestAtDepth = this.bestMove;
        float alpha = Float.NEGATIVE_INFINITY;
        float beta = Float.POSITIVE_INFINITY;

        for (Move move : board.findLegalMoves(color)) {
            checkTime(timeManager);
            MyBoard next = board.placed(move);
            float value = -negamax(next, color.flipped(), depth - 1, -beta, -alpha, timeManager);
            if (value > alpha) {
                alpha = value;
                bestAtDepth = move;
            }
        }

        return bestAtDepth;
    }

    private float negamax(MyBoard board, Color color, int depth, float alpha, float beta, TimeManager timeManager) {
        checkTime(timeManager);

        if (board.isEnd()) {
            return terminalValue(board, color);
        }
        if (depth == 0) {
            return this.eval.value(board, color);
        }

        float best = Float.NEGATIVE_INFINITY;
        for (Move move : board.findLegalMoves(color)) {
            checkTime(timeManager);
            MyBoard next = board.placed(move);
            float value = -negamax(next, color.flipped(), depth - 1, -beta, -alpha, timeManager);
            best = Math.max(best, value);
            alpha = Math.max(alpha, value);
            if (alpha >= beta) {
                break;
            }
        }

        return best;
    }

    private float terminalValue(MyBoard board, Color color) {
        int score = board.score() * color.getValue();
        if (score > 0) {
            return WIN_SCORE + score;
        }
        if (score < 0) {
            return -WIN_SCORE + score;
        }
        return 0.0f;
    }

    private int maxDepth(int emptySquares) {
        if (emptySquares <= ENDGAME_EMPTY_THRESHOLD) {
            return emptySquares + 2;
        }
        return 36;
    }

    private void checkTime(TimeManager timeManager) {
        if (timeManager.shouldStop()) {
            throw new TimeUpException();
        }
    }

    private static final class TimeUpException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
