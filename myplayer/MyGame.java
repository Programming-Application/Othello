package myplayer;

import ap26.Board;
import ap26.Color;
import ap26.Move;
import ap26.Player;
import ap26.league.OfficialBoard;
import p26x29.OurPlayer;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static ap26.Board.LENGTH;
import static ap26.Color.BLACK;
import static ap26.Color.BLOCK;
import static ap26.Color.NONE;
import static ap26.Color.WHITE;

/**
 * Local Task 10 verification driver.
 *
 * <p>Run:
 * <pre>
 *   javac -encoding UTF-8 -d bin $(find . -name "*.java")
 *   java -cp "bin:." myplayer.MyGame
 *   java -Dgames=10 -cp "bin:." myplayer.MyGame
 * </pre>
 */
public final class MyGame {
    private static final long TIME_LIMIT_NANOS = 60_000_000_000L;
    private static final int DEFAULT_GAMES_PER_BOARD = 1;
    private static final Path LOG_DIR = Path.of("logs", "myplayer");

    private MyGame() {
    }

    public static void main(String[] args) {
        Locale.setDefault(Locale.ROOT);

        int gamesPerBoard = Integer.getInteger("games", DEFAULT_GAMES_PER_BOARD);
        List<Board> boards = verificationBoards();

        System.out.println("== Rule checks ==");
        runRuleChecks();

        System.out.println();
        System.out.println("== OurPlayer vs RandomPlayer ==");
        BatchStats randomStats = new BatchStats();
        for (Board board : boards) {
            randomStats.addAll(runPair(board, gamesPerBoard, OurPlayer::new, RandomPlayer::new, true));
            randomStats.addAll(runPair(board, gamesPerBoard, RandomPlayer::new, OurPlayer::new, true));
        }
        randomStats.print("Random baseline");

        System.out.println();
        System.out.println("== OurPlayer self-play ==");
        BatchStats selfStats = new BatchStats();
        for (Board board : boards) {
            selfStats.addAll(runPair(board, gamesPerBoard, OurPlayer::new, OurPlayer::new, false));
        }
        selfStats.print("Self-play");
    }

    private static List<GameStats> runPair(
            Board board,
            int games,
            Function<Color, Player> blackFactory,
            Function<Color, Player> whiteFactory,
            boolean writeDebugLog) {
        List<GameStats> stats = new ArrayList<>();
        for (int i = 0; i < games; i++) {
            Player black = blackFactory.apply(BLACK);
            Player white = whiteFactory.apply(WHITE);
            GameStats result = play(board, black, white, writeDebugLog ? logPath(board, black, white, i + 1) : null);
            stats.add(result);
            System.out.println(result.shortLine());
        }
        return stats;
    }

    private static GameStats play(Board initialBoard, Player black, Player white, Path logPath) {
        Board board = initialBoard.clone();
        black.setBoard(board.clone());
        white.setBoard(board.clone());

        long blackNanos = 0L;
        long whiteNanos = 0L;
        int blackTurns = 0;
        int whiteTurns = 0;
        int passCount = 0;
        int lateOurLosses = 0;
        List<Move> moves = new ArrayList<>();
        String foul = "";

        PrintWriter log = openLog(logPath);
        writeGameHeader(log, initialBoard, black, white);

        long gameStartedAt = System.nanoTime();
        while (!board.isEnd()) {
            Color turn = board.getTurn();
            Player player = turn == BLACK ? black : white;
            int moveNumber = moves.size() + 1;
            writeBeforeMove(log, moveNumber, board, player, turn);
            long startedAt = System.nanoTime();
            Move move;
            try {
                move = player.think(board.clone());
            } catch (Throwable e) {
                move = Move.ofError(turn);
                foul = turn + " error: " + e.getClass().getSimpleName();
            }
            long elapsed = Math.max(1L, System.nanoTime() - startedAt);

            if (turn == BLACK) {
                blackNanos += elapsed;
                blackTurns++;
            } else {
                whiteNanos += elapsed;
                whiteTurns++;
            }

            Move colored = move == null ? Move.ofError(turn) : move.colored(turn);
            if (foul.isEmpty()) {
                foul = foulReason(board, turn, colored, blackNanos, whiteNanos);
            }
            moves.add(colored);
            writeMoveResult(log, moveNumber, board, player, colored, elapsed, foul);

            if (!foul.isEmpty()) {
                board.foul(turn);
                writeBoard(log, "After foul", board);
                break;
            }
            if (colored.isPass()) {
                passCount++;
            }

            int beforeLateEmpty = count(board, NONE);
            board = board.placed(colored);
            writeBoard(log, "After move " + moveNumber, board);
            Player opponent = turn == BLACK ? white : black;
            if (beforeLateEmpty <= 8
                    && isOurPlayer(player)
                    && !isOurPlayer(opponent)
                    && board.isEnd()
                    && board.winner() == turn.flipped()) {
                lateOurLosses++;
            }
        }

        long gameNanos = System.nanoTime() - gameStartedAt;
        writeGameFooter(log, board, moves, foul, blackNanos, whiteNanos, blackTurns, whiteTurns, gameNanos);
        closeLog(log);

        return new GameStats(
                initialBoard.getBoardId(),
                black.toString(),
                white.toString(),
                board.score(),
                board.winner(),
                moves.size(),
                passCount,
                foul,
                blackNanos,
                whiteNanos,
                blackTurns,
                whiteTurns,
                gameNanos,
                lateOurLosses);
    }

    private static PrintWriter openLog(Path logPath) {
        if (logPath == null) {
            return null;
        }
        try {
            Files.createDirectories(logPath.getParent());
            return new PrintWriter(Files.newBufferedWriter(logPath, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Could not open debug log: " + logPath, e);
        }
    }

    private static void closeLog(PrintWriter log) {
        if (log != null) {
            log.close();
        }
    }

    private static Path logPath(Board board, Player black, Player white, int gameNumber) {
        String boardId = sanitize(board.getBoardId());
        String fileName = String.format("%s_%s-vs-%s_%02d.log",
                boardId, sanitize(black.toString()), sanitize(white.toString()), gameNumber);
        return LOG_DIR.resolve(fileName);
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "board";
        }
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static void writeGameHeader(PrintWriter log, Board board, Player black, Player white) {
        if (log == null) {
            return;
        }
        log.printf("Board: %s%n", board.getBoardId());
        log.printf("Black: %s%n", black);
        log.printf("White: %s%n", white);
        writeBoard(log, "Initial board", board);
    }

    private static void writeBeforeMove(PrintWriter log, int moveNumber, Board board, Player player, Color turn) {
        if (log == null) {
            return;
        }
        log.println();
        log.printf("-- Move %d --%n", moveNumber);
        log.printf("Turn: %s (%s)%n", turn, player);
        log.printf("Legal moves: %s%n", board.findLegalMoves(turn));
    }

    private static void writeMoveResult(PrintWriter log, int moveNumber, Board board, Player player, Move move, long elapsedNanos, String foul) {
        if (log == null) {
            return;
        }
        log.printf("Selected: %s by %s in %.3f ms%n", move, player, elapsedNanos / 1_000_000.0);
        log.printf("Legal: %s%n", board.findLegalMoves(move.getColor()).contains(move));
        if (!foul.isEmpty()) {
            log.printf("Foul: %s%n", foul);
        }
        log.flush();
    }

    private static void writeBoard(PrintWriter log, String label, Board board) {
        if (log == null) {
            return;
        }
        log.println();
        log.println(label + ":");
        log.println(board);
        log.flush();
    }

    private static void writeGameFooter(
            PrintWriter log,
            Board board,
            List<Move> moves,
            String foul,
            long blackNanos,
            long whiteNanos,
            int blackTurns,
            int whiteTurns,
            long gameNanos) {
        if (log == null) {
            return;
        }
        log.println();
        log.println("== Result ==");
        log.printf("Winner: %s%n", board.winner());
        log.printf("Score: %d%n", board.score());
        log.printf("Moves: %s%n", moves);
        log.printf("Foul: %s%n", foul.isEmpty() ? "-" : foul);
        log.printf("Average think ms (black/white): %.3f / %.3f%n",
                avgMs(blackNanos, blackTurns), avgMs(whiteNanos, whiteTurns));
        log.printf("Total game seconds: %.3f%n", gameNanos / 1_000_000_000.0);
        writeBoard(log, "Final board", board);
    }

    private static String foulReason(Board board, Color turn, Move move, long blackNanos, long whiteNanos) {
        if (move == null || move.isError()) {
            return turn + " error";
        }
        if (move.isTimeout()) {
            return turn + " timeout move";
        }
        if ((turn == BLACK ? blackNanos : whiteNanos) > TIME_LIMIT_NANOS) {
            return turn + " exceeded 60s";
        }
        if (!board.findLegalMoves(turn).contains(move)) {
            return turn + " illegal " + move;
        }
        return "";
    }

    private static void runRuleChecks() {
        Board blockLine = board("block-line", cells(
                BLACK, WHITE, BLOCK, NONE, NONE, NONE,
                NONE, NONE, NONE, NONE, NONE, NONE,
                NONE, NONE, NONE, NONE, NONE, NONE,
                NONE, NONE, NONE, NONE, NONE, NONE,
                NONE, NONE, NONE, NONE, NONE, NONE,
                NONE, NONE, NONE, NONE, NONE, NONE));
        Board placedAcrossBlock = blockLine.placed(Move.of("d1", BLACK));
        check("BLOCK stops flips", placedAcrossBlock.get(Move.parseIndex("b1")) == WHITE);

        Board oneBlock = variant("one-block", "a1");
        check("BLOCK is not legal", oneBlock.findLegalMoves(BLACK).stream()
                .noneMatch(move -> move.getIndex() == Move.parseIndex("a1")));

        Board passBoard = board("pass", cells(
                WHITE, WHITE, WHITE, WHITE, WHITE, WHITE,
                WHITE, WHITE, WHITE, WHITE, WHITE, WHITE,
                WHITE, WHITE, WHITE, WHITE, WHITE, WHITE,
                WHITE, WHITE, WHITE, WHITE, WHITE, WHITE,
                WHITE, WHITE, WHITE, WHITE, WHITE, WHITE,
                WHITE, WHITE, WHITE, WHITE, WHITE, NONE));
        check("pass is returned when no legal move exists", passBoard.findLegalMoves(BLACK).get(0).isPass());
    }

    private static List<Board> verificationBoards() {
        return List.of(
                standard("standard"),
                variant("block-1-corner", "a1"),
                variant("block-2-edge", "a1", "c1"),
                variant("block-3-mixed", "a1", "f1", "a6"),
                variant("block-3-left-edge", "a2", "a4", "a6"));
    }

    private static Board standard(String id) {
        OfficialBoard board = new OfficialBoard();
        board.setBoardId(id);
        return board;
    }

    private static Board variant(String id, String... blocks) {
        Color[] cells = initialCells();
        for (String block : blocks) {
            cells[Move.parseIndex(block)] = BLOCK;
        }
        return board(id, cells);
    }

    private static Board board(String id, Color[] cells) {
        int[] values = Arrays.stream(cells).mapToInt(Color::getValue).toArray();
        OfficialBoard board = OfficialBoard.fromCells(values, Move.ofPass(NONE), id);
        board.setBoardId(id);
        return board;
    }

    private static Color[] initialCells() {
        Color[] cells = new Color[LENGTH];
        Arrays.fill(cells, NONE);
        cells[Move.parseIndex("c3")] = BLACK;
        cells[Move.parseIndex("d4")] = BLACK;
        cells[Move.parseIndex("d3")] = WHITE;
        cells[Move.parseIndex("c4")] = WHITE;
        return cells;
    }

    private static Color[] cells(Color... values) {
        if (values.length != LENGTH) {
            throw new IllegalArgumentException("Expected " + LENGTH + " cells");
        }
        return values;
    }

    private static int count(Board board, Color color) {
        int count = 0;
        for (int k = 0; k < LENGTH; k++) {
            if (board.get(k) == color) {
                count++;
            }
        }
        return count;
    }

    private static boolean isOurPlayer(Player player) {
        return player instanceof OurPlayer;
    }

    private static void check(String label, boolean ok) {
        System.out.printf("%-42s %s%n", label, ok ? "OK" : "NG");
        if (!ok) {
            throw new IllegalStateException(label);
        }
    }

    private record GameStats(
            String boardId,
            String black,
            String white,
            int score,
            Color winner,
            int moves,
            int passes,
            String foul,
            long blackNanos,
            long whiteNanos,
            int blackTurns,
            int whiteTurns,
            long gameNanos,
            int lateOurLosses) {
        String shortLine() {
            return String.format("%-18s %4s vs %-4s winner=%-5s score=%3d moves=%2d passes=%d foul=%s avgMs(B/W)=%.1f/%.1f gameSec=%.2f",
                    boardId,
                    black,
                    white,
                    winner,
                    score,
                    moves,
                    passes,
                    foul.isEmpty() ? "-" : foul,
                    avgMs(blackNanos, blackTurns),
                    avgMs(whiteNanos, whiteTurns),
                    gameNanos / 1_000_000_000.0);
        }
    }

    private static final class BatchStats {
        private final List<GameStats> games = new ArrayList<>();

        void addAll(List<GameStats> results) {
            this.games.addAll(results);
        }

        void print(String label) {
            long fouls = games.stream().filter(g -> !g.foul().isEmpty()).count();
            long over60 = games.stream().filter(g -> g.blackNanos() > TIME_LIMIT_NANOS || g.whiteNanos() > TIME_LIMIT_NANOS).count();
            long draws = games.stream().filter(g -> g.winner() == NONE).count();
            long blackWins = games.stream().filter(g -> g.winner() == BLACK).count();
            long whiteWins = games.stream().filter(g -> g.winner() == WHITE).count();
            int lateOurLosses = games.stream().mapToInt(GameStats::lateOurLosses).sum();
            double avgBlackMs = games.stream().mapToDouble(g -> avgMs(g.blackNanos(), g.blackTurns())).average().orElse(0.0);
            double avgWhiteMs = games.stream().mapToDouble(g -> avgMs(g.whiteNanos(), g.whiteTurns())).average().orElse(0.0);
            double maxGameSec = games.stream().mapToDouble(g -> g.gameNanos() / 1_000_000_000.0).max().orElse(0.0);

            long externalGames = games.stream().filter(this::hasExactlyOneOurPlayer).count();
            if (externalGames > 0) {
                long ourWins = games.stream().filter(this::ourWon).count();
                long ourLosses = games.stream().filter(this::ourLost).count();
                System.out.printf("%s summary: games=%d ourWins=%d ourLosses=%d draws=%d fouls=%d over60=%d lateOurLosses=%d avgMs(B/W)=%.1f/%.1f maxGameSec=%.2f%n",
                        label, games.size(), ourWins, ourLosses, draws, fouls, over60, lateOurLosses, avgBlackMs, avgWhiteMs, maxGameSec);
                return;
            }

            System.out.printf("%s summary: games=%d blackWins=%d whiteWins=%d draws=%d fouls=%d over60=%d avgMs(B/W)=%.1f/%.1f maxGameSec=%.2f%n",
                    label, games.size(), blackWins, whiteWins, draws, fouls, over60, avgBlackMs, avgWhiteMs, maxGameSec);
        }

        private boolean hasExactlyOneOurPlayer(GameStats stats) {
            return "26dd".equals(stats.black()) ^ "26dd".equals(stats.white());
        }

        private boolean ourWon(GameStats stats) {
            return (stats.winner() == BLACK && "26dd".equals(stats.black()))
                    || (stats.winner() == WHITE && "26dd".equals(stats.white()));
        }

        private boolean ourLost(GameStats stats) {
            return (stats.winner() == WHITE && "26dd".equals(stats.black()))
                    || (stats.winner() == BLACK && "26dd".equals(stats.white()));
        }
    }

    private static double avgMs(long nanos, int turns) {
        return turns == 0 ? 0.0 : nanos / 1_000_000.0 / turns;
    }
}
