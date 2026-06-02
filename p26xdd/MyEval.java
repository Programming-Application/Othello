package p26xdd;

import ap26.Board;
import ap26.Color;

import java.util.stream.IntStream;

import static ap26.Board.LENGTH;
import static ap26.Board.SIZE;
import static ap26.Color.BLACK;
import static ap26.Color.BLOCK;
import static ap26.Color.NONE;

public class MyEval {
    static final float WIN_SCORE = 1_000_000.0f;
    static final float DRAW_SCORE = -10_000.0f;

    private static final float[][] WEIGHTS = {
            { 10, 10, 10, 10, 10, 10 },
            { 10, -5, 1, 1, -5, 10 },
            { 10, 1, 1, 1, 1, 10 },
            { 10, 1, 1, 1, 1, 10 },
            { 10, -5, 1, 1, -5, 10 },
            { 10, 10, 10, 10, 10, 10 },
    };

    public float value(Board board) {
        return value(board, BLACK);
    }

    public float value(Board board, Color color) {
        MyBoard myBoard = board instanceof MyBoard ? (MyBoard) board : MyBoard.copyOf(board);
        BoardAnalyzer analyzer = myBoard.analyzer();

        if (myBoard.isEnd()) {
            return terminalValue(myBoard, color);
        }

        return switch (analyzer.phase()) {
            case EARLY -> earlyValue(myBoard, analyzer, color);
            case MIDDLE -> middleValue(myBoard, analyzer, color);
            case LATE -> lateValue(myBoard, analyzer, color);
        };
    }

    static float positionWeight(int k) {
        return WEIGHTS[k / SIZE][k % SIZE];
    }

    static float terminalValue(BoardAnalyzer analyzer, Color color) {
        int discDiff = analyzer.scoreDiff(color);
        if (discDiff > 0) {
            return WIN_SCORE + discDiff;
        }
        if (discDiff < 0) {
            return -WIN_SCORE + discDiff;
        }
        return DRAW_SCORE;
    }

    static float terminalValue(MyBoard board, Color color) {
        int discDiff = board.score() * color.getValue();
        if (discDiff > 0) {
            return WIN_SCORE + discDiff;
        }
        if (discDiff < 0) {
            return -WIN_SCORE + discDiff;
        }
        return DRAW_SCORE;
    }

    private float earlyValue(MyBoard board, BoardAnalyzer analyzer, Color color) {
        return analyzer.mobilityDiff(color) * 120.0f
                + analyzer.discDiff(color) * -8.0f
                + analyzer.cornerDiff(color) * 250.0f
                + analyzer.pseudoCornerDiff(color) * 120.0f
                + analyzer.blockAdjacentDiff(color) * 30.0f
                + positionValue(board, color) * 2.0f;
    }

    private float middleValue(MyBoard board, BoardAnalyzer analyzer, Color color) {
        return analyzer.cornerDiff(color) * 900.0f
                + analyzer.pseudoCornerDiff(color) * 450.0f
                + analyzer.blockAdjacentDiff(color) * 90.0f
                + analyzer.stableDiscDiff(color) * 300.0f
                + analyzer.mobilityDiff(color) * 80.0f
                - analyzer.mobility(color.flipped()) * 120.0f
                + analyzer.discDiff(color) * 4.0f
                + positionValue(board, color) * 8.0f;
    }

    private float lateValue(MyBoard board, BoardAnalyzer analyzer, Color color) {
        return analyzer.discDiff(color) * 400.0f
                + analyzer.cornerDiff(color) * 500.0f
                + analyzer.pseudoCornerDiff(color) * 200.0f
                + analyzer.blockAdjacentDiff(color) * 50.0f
                + analyzer.stableDiscDiff(color) * 150.0f
                + analyzer.mobilityDiff(color) * 50.0f
                + positionValue(board, color) * 3.0f;
    }

    private float positionValue(Board board, Color perspective) {
        return (float) IntStream.range(0, LENGTH)
                .mapToDouble(k -> positionScore(board, k, perspective))
                .sum();
    }

    private float positionScore(Board board, int k, Color perspective) {
        Color current = board.get(k);
        if (current == NONE || current == BLOCK) {
            return 0.0f;
        }
        float sign = current == perspective ? 1.0f : -1.0f;
        return WEIGHTS[k / SIZE][k % SIZE] * sign;
    }
}
