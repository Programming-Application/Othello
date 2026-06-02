package p26xdd;

import ap26.Color;
import ap26.Move;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class MoveOrdering {
    private static final int WINNING_TERMINAL_BONUS = 1_000_000;
    private static final int PREFERRED_MOVE_BONUS = 100_000;
    private static final int CORNER_BONUS = 50_000;
    private static final int PSEUDO_CORNER_BONUS = 25_000;
    private static final int KILLER_PRIMARY_BONUS = 12_000;
    private static final int KILLER_SECONDARY_BONUS = 10_000;
    private static final int MOBILITY_WEIGHT = 700;
    private static final int STABLE_WEIGHT = 350;
    private static final int POSITION_WEIGHT = 10;
    private static final int DANGEROUS_MOVE_PENALTY = 35_000;
    private static final int GIVES_CORNER_PENALTY = 45_000;

    List<Move> order(MyBoard board, Color color, int depth, KillerMoveTable killerMoves, Move preferredMove) {
        List<Move> moves = board.findLegalMoves(color);
        if (moves.size() <= 1) {
            return moves;
        }

        List<ScoredMove> scoredMoves = new ArrayList<>(moves.size());
        for (Move move : moves) {
            scoredMoves.add(new ScoredMove(move, score(board, color, move, depth, killerMoves, preferredMove)));
        }

        scoredMoves.sort(Comparator
                .comparingInt(ScoredMove::score)
                .reversed()
                .thenComparingInt(scoredMove -> scoredMove.move().getIndex()));

        return scoredMoves.stream()
                .map(ScoredMove::move)
                .toList();
    }

    private int score(MyBoard board, Color color, Move move, int depth, KillerMoveTable killerMoves, Move preferredMove) {
        if (move.isPass()) {
            return Integer.MIN_VALUE / 2;
        }

        MyBoard next = board.placed(move);
        int score = 0;

        if (preferredMove != null && preferredMove.equals(move)) {
            score += PREFERRED_MOVE_BONUS;
        }
        if (next.isEnd() && next.score() * color.getValue() > 0) {
            score += WINNING_TERMINAL_BONUS;
        }
        if (board.isCorner(move.getIndex())) {
            score += CORNER_BONUS;
        } else if (board.isPseudoCorner(move.getIndex())) {
            score += PSEUDO_CORNER_BONUS;
        }

        int killerRank = killerMoves.rank(depth, move);
        if (killerRank == 0) {
            score += KILLER_PRIMARY_BONUS;
        } else if (killerRank == 1) {
            score += KILLER_SECONDARY_BONUS;
        }

        int opponentMobility = next.findNoPassLegalIndexes(color.flipped()).size();
        score -= opponentMobility * MOBILITY_WEIGHT;
        score += next.countStableCandidates(color) * STABLE_WEIGHT;
        score += Math.round(MyEval.positionWeight(move.getIndex()) * POSITION_WEIGHT);

        if (board.isDangerous(move.getIndex())) {
            score -= DANGEROUS_MOVE_PENALTY;
        }
        if (opponentCanTakeCorner(next, color.flipped())) {
            score -= GIVES_CORNER_PENALTY;
        }

        return score;
    }

    private boolean opponentCanTakeCorner(MyBoard board, Color opponent) {
        for (Move opponentMove : board.findLegalMoves(opponent)) {
            if (!opponentMove.isPass() && board.isCorner(opponentMove.getIndex())) {
                return true;
            }
        }
        return false;
    }

    private record ScoredMove(Move move, int score) {
    }
}
