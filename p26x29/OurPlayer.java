package p26x29;

import ap26.Board;
import ap26.Color;
import ap26.Move;
import ap26.Player;

import java.util.List;

public class OurPlayer extends Player {
    private static final String MY_NAME = "26dd";
    private static final boolean DEBUG_SYNC = Boolean.getBoolean("p26xdd.debugSync");

    private final Search search;
    private MyBoard board;
    private Move move;
    private long accumulatedThinkNanos;

    public OurPlayer(Color color) {
        this(MY_NAME, color, new MyEval());
    }

    OurPlayer(String name, Color color, MyEval eval) {
        super(name, color);
        this.search = new Search(eval);
        this.board = new MyBoard();
        resetTimeState();
    }

    @Override
    public void setBoard(Board board) {
        loadInitialBoard(board);
        resetTimeState();
    }

    private void loadInitialBoard(Board board) {
        this.board = MyBoard.copyOf(board);
        super.setBoard(this.board);
    }

    private void syncBoard(Board board) {
        this.board.syncLastMove(board);
        super.setBoard(this.board);
        verifySynchronized(board, "opponent move");
    }

    private void verifySynchronized(Board externalBoard, String phase) {
        if (!DEBUG_SYNC || this.board.hasSameState(externalBoard)) {
            return;
        }

        System.err.println("[p26xdd] board sync mismatch after " + phase
                + " (" + this.board.firstDifference(externalBoard) + ")");
    }

    private void resetTimeState() {
        this.accumulatedThinkNanos = 0L;
    }

    @Override
    public Move think(Board board) {
        long startedAtNanos = System.nanoTime();
        syncBoard(board);

        List<Integer> legalIndexes = this.board.findNoPassLegalIndexes(getColor());
        if (legalIndexes.isEmpty()) {
            this.move = Move.ofPass(getColor());
            this.board.applyMove(this.move);
            super.setBoard(this.board);
            if (DEBUG_SYNC) {
                verifySynchronized(board.placed(this.move), "own pass");
            }
            this.accumulatedThinkNanos += System.nanoTime() - startedAtNanos;
            return this.move;
        }

        Move selected = this.search.findBestMove(this.board.clone(), getColor(), this.accumulatedThinkNanos);

        if (!legalIndexes.contains(selected.getIndex())) {
            selected = Move.of(legalIndexes.get(0), getColor());
        }

        this.move = selected;
        this.board = this.board.placed(this.move);
        super.setBoard(this.board);
        if (DEBUG_SYNC) {
            verifySynchronized(board.placed(this.move), "own move");
        }
        this.accumulatedThinkNanos += System.nanoTime() - startedAtNanos;
        return this.move;
    }
}
