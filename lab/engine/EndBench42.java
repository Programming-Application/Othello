import static ap26.Color.*;
import ap26.*;
import java.util.*;
import p26x42.OurBoard;
import p26x42.OurPlayer;

/**
 * 終盤完全読みの所要時間/ノード数を空きマス数別に計測する (提出物に含めない)。
 * ランダム自己対局で目標の空きマス数まで進めた局面を厳密解し、time/nodes を集計。
 * ENDGAME_THRESHOLD の調整根拠に使う。
 *
 * 実行: java -cp "bin:." EndBench42
 */
public class EndBench42 {
    static Random rnd = new Random(12345);

    public static void main(String[] args) {
        // 引数: arg0=カンマ区切りの空き数 (例 "22"), arg1=サンプル数
        int[] targets = {10, 12, 14, 16, 18, 20};
        int samples = 12;
        if (args.length > 0) {
            String[] ps = args[0].split(",");
            targets = new int[ps.length];
            for (int i = 0; i < ps.length; i++) targets[i] = Integer.parseInt(ps[i].trim());
        }
        if (args.length > 1) samples = Integer.parseInt(args[1]);
        OurPlayer solver = new OurPlayer(BLACK);

        System.err.printf("%-8s %-8s %-14s %-14s %-12s%n", "empties", "samples", "avgTime(ms)", "maxTime(ms)", "avgNodes");
        for (int E : targets) {
            long sumNanos = 0, maxNanos = 0, sumNodes = 0;
            int got = 0;
            int attempts = 0;
            while (got < samples && attempts < samples * 50) {
                attempts++;
                OurBoard pos = playoutUntil(E);
                if (pos == null) continue; // 早く終局した等
                OurBoard root = (pos.getTurn() == BLACK) ? pos.clone() : pos.flipped();
                OurPlayer.searchNodes = 0;
                long t0 = System.nanoTime();
                solver.benchSolve(root);
                long dt = System.nanoTime() - t0;
                sumNanos += dt;
                if (dt > maxNanos) maxNanos = dt;
                sumNodes += OurPlayer.searchNodes;
                got++;
            }
            if (got == 0) {
                System.err.printf("%-8d (no samples)%n", E);
                continue;
            }
            System.err.printf("%-8d %-8d %-14.1f %-14.1f %-12d%n",
                    E, got, sumNanos / 1e6 / got, maxNanos / 1e6, sumNodes / got);
        }
    }

    /** 標準盤からランダムに進め、空きマス数が E の(未終局)局面を返す。失敗時 null。*/
    static OurBoard playoutUntil(int target) {
        OurBoard b = new OurBoard();
        while (true) {
            int empties = b.count(NONE);
            if (empties == target) return b.isEnd() ? null : b;
            if (empties < target) return null; // 行き過ぎ
            if (b.isEnd()) return null;
            Color turn = b.getTurn();
            var moves = b.findLegalMoves(turn);
            Move mv = moves.get(rnd.nextInt(moves.size()));
            b = b.placed(mv);
        }
    }
}
