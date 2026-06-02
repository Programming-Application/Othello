package p26xdd;

import ap26.Color;
import ap26.Move;

import java.util.List;

final class Search {
    private static final int ENDGAME_EXTENSION_EMPTY_THRESHOLD = 12;
    private static final int MAX_SEARCH_DEPTH = 36;

    private final MyEval eval;
    private final MoveOrdering moveOrdering;
    private final KillerMoveTable killerMoves;
    private Move bestMove;

    Search(MyEval eval) {
        this.eval = eval;
        this.moveOrdering = new MoveOrdering();
        this.killerMoves = new KillerMoveTable(MAX_SEARCH_DEPTH);
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
        this.killerMoves.clear();

        if (isExactEndgame(board)) {
            try {
                this.bestMove = searchRoot(board, color, maxDepth, timeManager);
            } catch (TimeUpException ignored) {
                // Keep the legal fallback when the exact endgame cannot finish in time.
            }
            return this.bestMove.colored(color);
        }

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

        for (Move move : this.moveOrdering.order(board, color, depth, this.killerMoves, this.bestMove)) {
            checkTime(timeManager);
            MyBoard next = board.placed(move);
            float value = -negamax(next, color.flipped(), nextDepth(depth, move), -beta, -alpha, timeManager);
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

        boolean exactEndgame = isExactEndgame(board);
        int searchDepth = extendDepth(board, depth);
        if (!exactEndgame && searchDepth == 0) {
            return this.eval.value(board, color);
        }

        float best = Float.NEGATIVE_INFINITY;
        for (Move move : this.moveOrdering.order(board, color, searchDepth, this.killerMoves, null)) {
            checkTime(timeManager);
            MyBoard next = board.placed(move);
            float value = -negamax(next, color.flipped(), nextDepth(searchDepth, move), -beta, -alpha, timeManager);
            best = Math.max(best, value);
            alpha = Math.max(alpha, value);
            if (alpha >= beta) {
                this.killerMoves.store(searchDepth, move);
                break;
            }
        }

        return best;
    }

    private float terminalValue(MyBoard board, Color color) {
        return MyEval.terminalValue(board, color);
    }

    private int maxDepth(int emptySquares) {
        if (emptySquares <= MyBoard.EXACT_ENDGAME_EMPTY_THRESHOLD) {
            return emptySquares + 2;
        }
        if (emptySquares <= ENDGAME_EXTENSION_EMPTY_THRESHOLD) {
            return emptySquares + 4;
        }
        return MAX_SEARCH_DEPTH;
    }

    private boolean isExactEndgame(MyBoard board) {
        return board.isExactEndgame();
    }

    private int extendDepth(MyBoard board, int depth) {
        if (isExactEndgame(board)) {
            return Math.max(depth, board.countEmptySquares() + 2);
        }
        if (board.countEmptySquares() <= ENDGAME_EXTENSION_EMPTY_THRESHOLD) {
            return Math.max(depth, board.countEmptySquares() + 2);
        }
        return depth;
    }

    private int nextDepth(int depth, Move move) {
        if (move.isPass()) {
            return depth;
        }
        return Math.max(0, depth - 1);
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
