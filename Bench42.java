import ap26.*;
import static ap26.Color.*;
import ap26.league.*;

/**
 * Phase 0 ベースライン計測用ハーネス (提出物には含めない)。
 *
 * 標準盤の初手から終局まで OurPlayer 同士で 1 局を自己対戦させ、
 * 探索総ノード数 / 総思考時間 = nodes/sec を測る (2c 報告の指標に対応)。
 *
 * 実行: java -cp "bin:." Bench42
 */
public class Bench42 {
    public static void main(String[] args) {
        int warmup = 2;   // JIT ウォームアップ
        int trials = 5;   // 計測本番

        // ウォームアップ
        for (int i = 0; i < warmup; i++) {
            runOneGame();
        }

        long totalNodes = 0;
        long totalNanos = 0;
        for (int i = 0; i < trials; i++) {
            p26x42.OurPlayer.searchNodes = 0;
            long t0 = System.nanoTime();
            runOneGame();
            long dt = System.nanoTime() - t0;
            long nodes = p26x42.OurPlayer.searchNodes;
            totalNodes += nodes;
            totalNanos += dt;
            double sec = dt / 1e9;
            System.err.printf("trial %d: nodes=%d  time=%.3fs  %.0f nodes/sec%n",
                    i + 1, nodes, sec, nodes / sec);
        }

        double sec = totalNanos / 1e9;
        System.err.println("==================== BASELINE ====================");
        System.err.printf("depth limit      : %d (plies searched)%n", 5);
        System.err.printf("avg nodes/game   : %.0f%n", (double) totalNodes / trials);
        System.err.printf("avg time/game    : %.3fs%n", sec / trials);
        System.err.printf("nodes per second : %.0f%n", totalNodes / sec);
        System.err.println("==================================================");
    }

    static void runOneGame() {
        OfficialBoard board = new OfficialBoard();
        board.setBoardId("#0");
        Player black = new p26x42.OurPlayer(BLACK);
        Player white = new p26x42.OurPlayer(WHITE);
        // Game は同一 JVM で think() を直接呼ぶドライバ (Socket 不使用)
        Game game = new Game(board, black, white, 60);
        game.play();
    }
}
