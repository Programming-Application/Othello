package p26xdd;

import ap26.Color;
import ap26.Move;

import static ap26.Board.LENGTH;
import static ap26.Board.SIZE;
import static ap26.Color.NONE;

final class BoardAnalyzer {
    enum Phase {
        EARLY,
        MIDDLE,
        LATE
    }

    private static final int EARLY_EMPTY_THRESHOLD = 20;
    private static final int LATE_EMPTY_THRESHOLD = 10;
    private static final int[][] AXES = {
            { 1, 0 },
            { 0, 1 },
    };

    private final MyBoard board;

    BoardAnalyzer(MyBoard board) {
        this.board = board;
    }

    Phase phase() {
        int emptySquares = emptySquares();
        if (emptySquares >= EARLY_EMPTY_THRESHOLD) {
            return Phase.EARLY;
        }
        if (emptySquares >= LATE_EMPTY_THRESHOLD) {
            return Phase.MIDDLE;
        }
        return Phase.LATE;
    }

    int emptySquares() {
        return this.board.countEmptySquares();
    }

    int discDiff(Color color) {
        return this.board.count(color) - this.board.count(color.flipped());
    }

    int scoreDiff(Color color) {
        return this.board.score() * color.getValue();
    }

    int mobilityDiff(Color color) {
        return mobility(color) - mobility(color.flipped());
    }

    int mobility(Color color) {
        return this.board.findNoPassLegalIndexes(color).size();
    }

    int cornerDiff(Color color) {
        return ownedSquaresDiff(color, SquareKind.CORNER);
    }

    int pseudoCornerDiff(Color color) {
        return ownedSquaresDiff(color, SquareKind.PSEUDO_CORNER);
    }

    int blockAdjacentDiff(Color color) {
        return ownedSquaresDiff(color, SquareKind.BLOCK_ADJACENT);
    }

    int stableDiscDiff(Color color) {
        return stableDiscs(color) - stableDiscs(color.flipped());
    }

    int stableDiscs(Color color) {
        int count = 0;
        for (int k = 0; k < LENGTH; k++) {
            if (this.board.get(k) == color && isStable(k, color)) {
                count++;
            }
        }
        return count;
    }

    private int ownedSquaresDiff(Color color, SquareKind kind) {
        int diff = 0;
        for (int k = 0; k < LENGTH; k++) {
            Color current = this.board.get(k);
            if (current == NONE || current == Color.BLOCK || !kind.matches(this.board, k)) {
                continue;
            }
            diff += current == color ? 1 : -1;
        }
        return diff;
    }

    private boolean isStable(int k, Color color) {
        if (this.board.isCorner(k) || this.board.isPseudoCorner(k)) {
            return true;
        }

        for (int[] axis : AXES) {
            if (!isClosed(k, axis[0], axis[1], color) && !isClosed(k, -axis[0], -axis[1], color)) {
                return false;
            }
        }
        return true;
    }

    private boolean isClosed(int k, int deltaCol, int deltaRow, Color color) {
        int col = k % SIZE + deltaCol;
        int row = k / SIZE + deltaRow;

        while (Move.isValid(col, row)) {
            int index = Move.index(col, row);
            if (this.board.isBlocked(index)) {
                return true;
            }
            if (this.board.get(index) != color) {
                return false;
            }
            col += deltaCol;
            row += deltaRow;
        }
        return true;
    }

    private enum SquareKind {
        CORNER {
            @Override
            boolean matches(MyBoard board, int k) {
                return board.isCorner(k);
            }
        },
        PSEUDO_CORNER {
            @Override
            boolean matches(MyBoard board, int k) {
                return board.isPseudoCorner(k);
            }
        },
        BLOCK_ADJACENT {
            @Override
            boolean matches(MyBoard board, int k) {
                return board.isBlockAdjacentBonusSquare(k);
            }
        };

        abstract boolean matches(MyBoard board, int k);
    }
}
