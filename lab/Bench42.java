import ap26.*;
import static ap26.Color.*;
import ap26.league.*;

/**
 * 探索性能の計測ハーネス (提出物には含めない)。
 *
 * 標準盤の初手から終局まで OurPlayer 同士で 1 局を自己対戦させ、
 * 探索総ノード数 / 総思考時間 = nodes/sec を測る (2c 報告の指標に対応)。
 *
 * 1 手の持ち時間は {@code OurPlayer.benchBudgetNanos} で固定し、ベンチを短時間で回す。
 * (本番は 1 ゲーム 60s の内部時間管理で動く。nodes/sec はレートなので持ち時間に概ね非依存。)
 *
 * 実行: java -cp "bin:." Bench42
 */
public class Bench42 {
    static final long BUDGET_PER_MOVE_NANOS = 100_000_000L; // 100ms/手

    public static void main(String[] args) {
        int warmup = 2;
        int trials = 5;

        p26x42.OurPlayer.benchBudgetNanos = BUDGET_PER_MOVE_NANOS;

        for (int i = 0; i < warmup; i++)
            runOneGame();

        long totalNodes = 0;
        long totalNanos = 0;
        int sampleDepth = 0;
        for (int i = 0; i < trials; i++) {
            p26x42.OurPlayer.searchNodes = 0;
            long t0 = System.nanoTime();
            runOneGame();
            long dt = System.nanoTime() - t0;
            long nodes = p26x42.OurPlayer.searchNodes;
            sampleDepth = p26x42.OurPlayer.lastReachedDepth;
            totalNodes += nodes;
            totalNanos += dt;
            double sec = dt / 1e9;
            System.err.printf("trial %d: nodes=%d  time=%.3fs  %.0f nodes/sec  (last move depth=%d)%n",
                    i + 1, nodes, sec, nodes / sec, sampleDepth);
        }

        double sec = totalNanos / 1e9;
        System.err.println("==================== BENCH ====================");
        System.err.printf("budget/move      : %d ms%n", BUDGET_PER_MOVE_NANOS / 1_000_000);
        System.err.printf("avg nodes/game   : %.0f%n", (double) totalNodes / trials);
        System.err.printf("avg time/game    : %.3fs%n", sec / trials);
        System.err.printf("nodes per second : %.0f%n", totalNodes / sec);
        System.err.printf("last-move depth  : %d (sample)%n", sampleDepth);
        System.err.println("===============================================");
    }

    static void runOneGame() {
        OfficialBoard board = new OfficialBoard();
        board.setBoardId("#0");
        Player black = new p26x42.OurPlayer(BLACK);
        Player white = new p26x42.OurPlayer(WHITE);
        Game game = new Game(board, black, white, 60);
        game.play();
    }
}
