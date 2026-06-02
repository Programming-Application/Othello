package p26xdd;

import ap26.Board;
import ap26.Color;

import java.util.stream.IntStream;

import static ap26.Board.LENGTH;
import static ap26.Board.SIZE;

public class MyEval {
    private static final float[][] WEIGHTS = {
            { 10, 10, 10, 10, 10, 10 },
            { 10, -5, 1, 1, -5, 10 },
            { 10, 1, 1, 1, 1, 10 },
            { 10, 1, 1, 1, 1, 10 },
            { 10, -5, 1, 1, -5, 10 },
            { 10, 10, 10, 10, 10, 10 },
    };

    public float value(Board board) {
        if (board.isEnd()) {
            return 1_000_000.0f * board.score();
        }

        return (float) IntStream.range(0, LENGTH)
                .mapToDouble(k -> score(board, k))
                .sum();
    }

    public float value(Board board, Color color) {
        return value(board) * color.getValue();
    }

    static float positionWeight(int k) {
        return WEIGHTS[k / SIZE][k % SIZE];
    }

    private float score(Board board, int k) {
        Color color = board.get(k);
        if (color == Color.BLOCK) {
            return 0.0f;
        }
        return WEIGHTS[k / SIZE][k % SIZE] * color.getValue();
    }
}
