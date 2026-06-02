package p26xdd;

import ap26.Board;
import ap26.Color;
import ap26.Move;
import ap26.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static ap26.Color.BLACK;
import static ap26.Color.WHITE;

public class OurPlayer extends Player {
    private static final String MY_NAME = "26dd";
    private static final int DEFAULT_DEPTH_LIMIT = 4;

    private final MyEval eval;
    private final int depthLimit;
    private MyBoard board;
    private Move move;

    public OurPlayer(Color color) {
        this(MY_NAME, color, new MyEval(), DEFAULT_DEPTH_LIMIT);
    }

    OurPlayer(String name, Color color, MyEval eval, int depthLimit) {
        super(name, color);
        this.eval = eval;
        this.depthLimit = depthLimit;
        this.board = new MyBoard();
    }

    @Override
    public void setBoard(Board board) {
        this.board = MyBoard.copyOf(board);
        super.setBoard(this.board);
    }

    @Override
    public Move think(Board board) {
        setBoard(board);

        List<Integer> legalIndexes = this.board.findNoPassLegalIndexes(getColor());
        if (legalIndexes.isEmpty()) {
            this.move = Move.ofPass(getColor());
            return this.move;
        }

        Board searchBoard = isBlack() ? this.board.clone() : this.board.flipped();
        this.move = null;
        maxSearch(searchBoard, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, 0);

        Move selected = this.move == null
                ? Move.of(legalIndexes.get(0), getColor())
                : this.move.colored(getColor());

        if (!legalIndexes.contains(selected.getIndex())) {
            selected = Move.of(legalIndexes.get(0), getColor());
        }

        this.move = selected;
        this.board = this.board.placed(this.move);
        super.setBoard(this.board);
        return this.move;
    }

    private boolean isBlack() {
        return getColor() == BLACK;
    }

    private float maxSearch(Board board, float alpha, float beta, int depth) {
        if (isTerminal(board, depth)) {
            return this.eval.value(board);
        }

        List<Move> moves = order(board.findLegalMoves(BLACK));
        if (depth == 0) {
            this.move = moves.get(0);
        }

        for (Move move : moves) {
            Board newBoard = board.placed(move);
            float value = minSearch(newBoard, alpha, beta, depth + 1);

            if (value > alpha) {
                alpha = value;
                if (depth == 0) {
                    this.move = move;
                }
            }

            if (alpha >= beta) {
                break;
            }
        }

        return alpha;
    }

    private float minSearch(Board board, float alpha, float beta, int depth) {
        if (isTerminal(board, depth)) {
            return this.eval.value(board);
        }

        for (Move move : order(board.findLegalMoves(WHITE))) {
            Board newBoard = board.placed(move);
            float value = maxSearch(newBoard, alpha, beta, depth + 1);
            beta = Math.min(beta, value);
            if (alpha >= beta) {
                break;
            }
        }

        return beta;
    }

    private boolean isTerminal(Board board, int depth) {
        return board.isEnd() || depth > this.depthLimit;
    }

    private List<Move> order(List<Move> moves) {
        List<Move> shuffled = new ArrayList<>(moves);
        Collections.shuffle(shuffled);
        return shuffled;
    }
}
