import ap26.*;
import static ap26.Color.*;
import java.util.*;
import p26x42.OurBoard;

/**
 * 強さの A/B テストハーネス (提出物には含めない)。
 *
 * 新 p26x42.OurPlayer と サンプル p26x00.OurPlayer を、同一 JVM で標準盤・変形盤・
 * 先後入替で多数対戦させ、勝率と平均石差を集計する。
 *
 * 審判には検証済みの自前 OurBoard を使い、Board API だけで対局を進める
 * (OfficialBoard/Game の package-private に依存しないため)。
 * 本番 ProxyGame は別プロセスで静的な持ち時間上書きが効かないので、ここでは
 * {@code OurPlayer.benchBudgetNanos} で持ち時間を制御して短時間で回す。
 *
 * 実行: java -cp "bin:." Match42 [games_per_config] [budget_ms]
 */
public class Match42 {
    public static void main(String[] args) {
        int gamesPerConfig = args.length > 0 ? Integer.parseInt(args[0]) : 20;
        long budgetMs = args.length > 1 ? Long.parseLong(args[1]) : 200;
        p26x42.OurPlayer.benchBudgetNanos = budgetMs * 1_000_000L;

        List<int[]> blockSets = List.of(
                new int[] {},            // 標準
                new int[] {0, 35},       // a1, f6
                new int[] {2, 6, 24}     // c1, a2, a5
        );
        String[] labels = {"standard", "variant{a1,f6}", "variant{c1,a2,a5}"};

        int totWin = 0, totLose = 0, totDraw = 0, totMargin = 0, totGames = 0;
        for (int bi = 0; bi < blockSets.size(); bi++) {
            int win = 0, lose = 0, draw = 0, margin = 0;
            for (int g = 0; g < gamesPerConfig; g++) {
                boolean ourBlack = (g % 2 == 0);
                int diff = playGame(blockSets.get(bi), ourBlack); // 黒石 - 白石
                int ourDiff = ourBlack ? diff : -diff;            // 我々視点
                if (ourDiff > 0) win++;
                else if (ourDiff < 0) lose++;
                else draw++;
                margin += ourDiff;
            }
            totWin += win; totLose += lose; totDraw += draw; totMargin += margin; totGames += gamesPerConfig;
            System.err.printf("[%-18s] W %2d  L %2d  D %2d  | winrate %5.1f%%  avgMargin %+.1f%n",
                    labels[bi], win, lose, draw, 100.0 * win / gamesPerConfig, (double) margin / gamesPerConfig);
        }
        System.err.println("------------------------------------------------------------");
        System.err.printf("TOTAL  W %d  L %d  D %d  | winrate %.1f%%  avgMargin %+.2f  (budget %dms, %d games)%n",
                totWin, totLose, totDraw, 100.0 * totWin / totGames,
                (double) totMargin / totGames, budgetMs, totGames);
    }

    /** 1 局プレイし、終局盤の score() (= 黒石 - 白石) を返す。審判は OurBoard。*/
    static int playGame(int[] blocks, boolean ourBlack) {
        OurBoard board = new OurBoard();
        for (int k : blocks) board.set(k, BLOCK);

        Player ours = new p26x42.OurPlayer(ourBlack ? BLACK : WHITE);
        Player samp = new p26x00.OurPlayer(ourBlack ? WHITE : BLACK);
        Map<Color, Player> players = Map.of(
                ourBlack ? BLACK : WHITE, ours,
                ourBlack ? WHITE : BLACK, samp);

        ours.setBoard(board.clone());
        samp.setBoard(board.clone());

        Board cur = board;
        while (!cur.isEnd()) {
            Color turn = cur.getTurn();
            Player p = players.get(turn);
            Move mv;
            try {
                mv = p.think(cur.clone()).colored(turn);
            } catch (Throwable e) {
                cur.foul(turn);
                break;
            }
            if (!cur.findLegalMoves(turn).contains(mv)) {
                cur.foul(turn); // 反則 → 相手の総取り
                break;
            }
            cur = cur.placed(mv);
        }
        return cur.score();
    }
}
