package p26x29;

import ap26.Move;

final class KillerMoveTable {
    private static final int SLOTS = 2;

    private final Move[][] killerMoves;

    KillerMoveTable(int maxDepth) {
        this.killerMoves = new Move[maxDepth + 1][SLOTS];
    }

    void clear() {
        for (int depth = 0; depth < this.killerMoves.length; depth++) {
            for (int slot = 0; slot < SLOTS; slot++) {
                this.killerMoves[depth][slot] = null;
            }
        }
    }

    void store(int depth, Move move) {
        if (!isUsableDepth(depth) || move.isPass()) {
            return;
        }
        Move normalized = Move.of(move.getIndex(), move.getColor());
        if (normalized.equals(this.killerMoves[depth][0])) {
            return;
        }
        this.killerMoves[depth][1] = this.killerMoves[depth][0];
        this.killerMoves[depth][0] = normalized;
    }

    int rank(int depth, Move move) {
        if (!isUsableDepth(depth) || move.isPass()) {
            return -1;
        }
        for (int slot = 0; slot < SLOTS; slot++) {
            Move killer = this.killerMoves[depth][slot];
            if (killer != null && killer.equals(move)) {
                return slot;
            }
        }
        return -1;
    }

    private boolean isUsableDepth(int depth) {
        return 0 <= depth && depth < this.killerMoves.length;
    }
}
