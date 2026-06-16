import ap26.*;
import ap26.league.OfficialBoard;
import static ap26.Color.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;

/**
 * x00 / x42base / x42 の勝率と評価関数を同条件で測るための提出物外ハーネス。
 *
 * Usage:
 *   java -cp "bin:." Measure42 --boards 3 --repeats 1 --budget-ms 30 --seed 20260616
 */
public class Measure42 {
  static final List<String[]> MATCHUPS = List.of(
      new String[] {"x00", "random"},
      new String[] {"x42base", "random"},
      new String[] {"x00", "x42base"},
      new String[] {"x42", "random"},
      new String[] {"x42", "x42base"},
      new String[] {"x42", "x00"});

  static class Config {
    int boards = 3;
    int repeats = 1;
    long budgetMs = 30;
    long seed = 20260616L;
    long timeLimitMs = 60_000L;
  }

  static class FixedRandomPlayer extends Player {
    final Random random;

    FixedRandomPlayer(Color color, long seed) {
      super("RAND", color);
      this.random = new Random(seed);
    }

    @Override
    public Move think(Board board) {
      var moves = board.findLegalMoves(getColor());
      return moves.get(random.nextInt(moves.size()));
    }
  }

  static class GameResult {
    String blackId, whiteId;
    int score;
    int moves;
    boolean foul;
    String reason = "";

    int marginFor(String id) {
      if (score == 0) return 0;
      if (blackId.equals(id)) return score;
      if (whiteId.equals(id)) return -score;
      throw new IllegalArgumentException("player not in game: " + id);
    }
  }

  static class MatchStats {
    final String a, b;
    int games, wins, draws, losses, fouls;
    long marginSum;

    MatchStats(String a, String b) {
      this.a = a;
      this.b = b;
    }

    void add(GameResult r) {
      games++;
      int m = r.marginFor(a);
      marginSum += m;
      if (m > 0) wins++;
      else if (m < 0) losses++;
      else draws++;
      if (r.foul) fouls++;
    }

    void print() {
      double win = 100.0 * wins / games;
      double halfPoint = 100.0 * (wins + 0.5 * draws) / games;
      double avgMargin = (double) marginSum / games;
      System.out.printf(Locale.US,
          "%-8s vs %-8s games=%3d  W-D-L=%3d-%3d-%3d  win%%=%6.2f  win%%(draw=0.5)=%6.2f  avg_margin=%7.2f  fouls=%d%n",
          a, b, games, wins, draws, losses, win, halfPoint, avgMargin, fouls);
    }
  }

  public static void main(String[] args) throws Exception {
    Config cfg = parseArgs(args);
    p26x42base.OurPlayer.benchBudgetNanos = cfg.budgetMs > 0 ? cfg.budgetMs * 1_000_000L : 0L;
    p26x42.OurPlayer.benchBudgetNanos = cfg.budgetMs > 0 ? cfg.budgetMs * 1_000_000L : 0L;

    System.out.println("=== Measure42 configuration ===");
    System.out.printf(Locale.US, "boards=%d repeats=%d budget-ms=%d seed=%d%n",
        cfg.boards, cfg.repeats, cfg.budgetMs, cfg.seed);
    System.out.println("budget-ms applies to x42base/x42 only; x00 keeps its fixed depth-4 search.");
    System.out.println();

    List<OfficialBoard> boards = makeBoards(cfg.boards, cfg.seed);
    printBoards(boards);
    printEvalFunctionSummary();
    printEvalSamples(boards, cfg.seed);
    runWinRateSuite(boards, cfg);
  }

  static Config parseArgs(String[] args) {
    Config cfg = new Config();
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--boards" -> cfg.boards = Integer.parseInt(args[++i]);
        case "--repeats" -> cfg.repeats = Integer.parseInt(args[++i]);
        case "--budget-ms" -> cfg.budgetMs = Long.parseLong(args[++i]);
        case "--seed" -> cfg.seed = Long.parseLong(args[++i]);
        case "--time-limit-ms" -> cfg.timeLimitMs = Long.parseLong(args[++i]);
        default -> throw new IllegalArgumentException("unknown arg: " + args[i]);
      }
    }
    if (cfg.boards < 1 || cfg.repeats < 1) throw new IllegalArgumentException("boards/repeats must be positive");
    return cfg;
  }

  static List<OfficialBoard> makeBoards(int n, long seed) {
    List<OfficialBoard> boards = new ArrayList<>();
    boards.add(makeBoard("#0", List.of()));

    List<Integer> candidates = List.of(0, 1, 2, 3, 4, 5, 6, 12, 18, 24, 30);
    for (int i = 1; i < n; i++) {
      List<Integer> xs = new ArrayList<>(candidates);
      Random r = new Random(seed + 7919L * i);
      Collections.shuffle(xs, r);
      int blocks = r.nextInt(3) + 1;
      boards.add(makeBoard("#" + i, xs.subList(0, blocks)));
    }
    return boards;
  }

  static OfficialBoard makeBoard(String id, List<Integer> blocks) {
    int[] cells = new int[Board.LENGTH];
    cells[Move.parseIndex("c3")] = BLACK.getValue();
    cells[Move.parseIndex("d4")] = BLACK.getValue();
    cells[Move.parseIndex("d3")] = WHITE.getValue();
    cells[Move.parseIndex("c4")] = WHITE.getValue();
    for (int k : blocks) cells[k] = BLOCK.getValue();
    return OfficialBoard.fromCells(cells, null, id);
  }

  static void printBoards(List<OfficialBoard> boards) {
    System.out.println("=== Boards ===");
    for (OfficialBoard b : boards) {
      System.out.println(b.getBoardId() + " blocks=" + blockList(b));
      System.out.println(b);
      System.out.println();
    }
  }

  static List<String> blockList(Board b) {
    List<String> xs = new ArrayList<>();
    for (int k = 0; k < Board.LENGTH; k++) {
      if (b.get(k) == BLOCK) xs.add(Move.toIndexString(k));
    }
    return xs;
  }

  static void printEvalFunctionSummary() {
    System.out.println("=== Evaluation functions ===");
    System.out.println("x00:");
    System.out.println("  terminal: 1_000_000 * final_score");
    System.out.println("  nonterminal: sum(position_weight[k] * color_value[k])");
    System.out.println("  weights:");
    System.out.println("    10  10  10  10  10  10");
    System.out.println("    10  -5   1   1  -5  10");
    System.out.println("    10   1   1   1   1  10");
    System.out.println("    10   1   1   1   1  10");
    System.out.println("    10  -5   1   1  -5  10");
    System.out.println("    10  10  10  10  10  10");
    System.out.println("x42base:");
    System.out.println("  terminal: 1_000_000 * final_score");
    System.out.println("  nonterminal: 10*position + 31*mobility - 20*frontier + 20*stable");
    System.out.println("  position weights: [29,10,10,10,10,29, 10,-5,-3,-3,-5,10, 10,-3,1,1,-3,10, 10,-3,1,1,-3,10, 10,-5,-3,-3,-5,10, 29,10,10,10,10,29]");
    System.out.println("x42:");
    System.out.println("  same evaluation as x42base; additionally checks proven.book before search.");
    System.out.printf(Locale.US, "  proven.book loaded: %s%n", p26x42.OurPlayer.PROVEN_BOOK != null);
    if (p26x42.OurPlayer.PROVEN_BOOK != null) {
      System.out.printf(Locale.US, "  proven.book entries: %,d%n", p26x42.OurPlayer.PROVEN_BOOK.size());
    }
    System.out.println();
  }

  static void printEvalSamples(List<OfficialBoard> boards, long seed) throws Exception {
    System.out.println("=== Evaluation samples (BLACK perspective; positive favors BLACK) ===");
    List<Board> samples = new ArrayList<>();
    List<String> names = new ArrayList<>();

    samples.add(boards.get(0));
    names.add("#0 initial");
    if (boards.size() > 1) {
      samples.add(boards.get(1));
      names.add("#1 initial");
    }
    samples.add(randomPlayout(boards.get(0), 8, seed + 101));
    names.add("#0 after 8 fixed-random plies");
    samples.add(randomPlayout(boards.get(0), 16, seed + 102));
    names.add("#0 after 16 fixed-random plies");
    samples.add(randomPlayout(boards.get(0), 24, seed + 103));
    names.add("#0 after 24 fixed-random plies");

    System.out.printf("%-30s %8s %8s %8s %8s %8s %s%n",
        "sample", "turn", "score", "x00", "x42base", "x42", "counts(B/W/empty/block)");
    for (int i = 0; i < samples.size(); i++) {
      Board b = samples.get(i);
      double x00 = evalX00(b);
      int xb = evalX42Like("p26x42base", b);
      int x42 = evalX42Like("p26x42", b);
      System.out.printf(Locale.US, "%-30s %8s %8d %8.1f %8d %8d %2d/%2d/%2d/%2d%n",
          names.get(i), colorName(b.getTurn()), b.score(), x00, xb, x42,
          b.count(BLACK), b.count(WHITE), b.count(NONE), b.count(BLOCK));
    }
    System.out.println();
  }

  static Board randomPlayout(Board start, int plies, long seed) {
    Board b = start.clone();
    Random r = new Random(seed);
    for (int i = 0; i < plies && !b.isEnd(); i++) {
      Color turn = b.getTurn();
      var moves = b.findLegalMoves(turn);
      b = b.placed(moves.get(r.nextInt(moves.size())).colored(turn));
    }
    return b;
  }

  static double evalX00(Board b) throws Exception {
    Class<?> c = Class.forName("p26x00.MyEval");
    Constructor<?> ctor = c.getDeclaredConstructor();
    ctor.setAccessible(true);
    Object eval = ctor.newInstance();
    Method m = c.getDeclaredMethod("value", Board.class);
    m.setAccessible(true);
    return ((Number) m.invoke(eval, b)).doubleValue();
  }

  static int evalX42Like(String pkg, Board src) throws Exception {
    Class<?> bc = Class.forName(pkg + ".OurBoard");
    Object b = bc.getDeclaredConstructor().newInstance();
    Method set = bc.getMethod("set", int.class, Color.class);
    for (int k = 0; k < Board.LENGTH; k++) set.invoke(b, k, src.get(k));

    Class<?> ec = Class.forName(pkg + ".MyEval");
    Constructor<?> ctor = ec.getDeclaredConstructor();
    ctor.setAccessible(true);
    Object eval = ctor.newInstance();
    Method m = src.isEnd() ? ec.getDeclaredMethod("terminal", bc) : ec.getDeclaredMethod("value", bc);
    m.setAccessible(true);
    return ((Number) m.invoke(eval, b)).intValue();
  }

  static void runWinRateSuite(List<OfficialBoard> boards, Config cfg) {
    System.out.println("=== Win-rate suite ===");
    System.out.println("Each repeat plays both colors on each board. Win rates are from the left player's perspective.");
    for (String[] pair : MATCHUPS) {
      MatchStats stats = new MatchStats(pair[0], pair[1]);
      for (int bi = 0; bi < boards.size(); bi++) {
        for (int r = 0; r < cfg.repeats; r++) {
          long s = cfg.seed + 1_000_003L * bi + 97_409L * r + pair[0].hashCode() * 31L + pair[1].hashCode();
          stats.add(playOne(boards.get(bi), pair[0], pair[1], s, cfg.timeLimitMs));
          stats.add(playOne(boards.get(bi), pair[1], pair[0], s ^ 0x5DEECE66DL, cfg.timeLimitMs));
        }
      }
      stats.print();
    }
    System.out.println();
  }

  static GameResult playOne(Board start, String blackId, String whiteId, long seed, long timeLimitMs) {
    Player black = makePlayer(blackId, BLACK, seed ^ 0xB00B1EL);
    Player white = makePlayer(whiteId, WHITE, seed ^ 0xFACEFEEDL);
    Map<Color, Player> players = Map.of(BLACK, black, WHITE, white);
    Map<Color, Long> times = new HashMap<>(Map.of(BLACK, 0L, WHITE, 0L));

    Board board = start.clone();
    try {
      black.setBoard(board.clone());
      white.setBoard(board.clone());
    } catch (Throwable ignored) {
    }

    GameResult result = new GameResult();
    result.blackId = blackId;
    result.whiteId = whiteId;

    while (!board.isEnd() && result.moves < 200) {
      Color turn = board.getTurn();
      Player p = players.get(turn);
      Move mv;
      long t0 = System.nanoTime();
      try {
        mv = p.think(board.clone()).colored(turn);
      } catch (Throwable e) {
        mv = Move.ofError(turn);
        result.reason = e.getClass().getSimpleName();
      }
      long dt = (System.nanoTime() - t0) / 1_000_000L;
      times.compute(turn, (k, v) -> v + Math.max(dt, 1L));

      if (times.get(turn) > timeLimitMs) {
        mv = Move.ofTimeout(turn);
        result.reason = "timeout";
      }
      if (!board.findLegalMoves(turn).contains(mv)) {
        mv = Move.ofIllegal(mv);
        result.reason = result.reason.isEmpty() ? "illegal" : result.reason;
      }

      result.moves++;
      if (mv.isLegal()) {
        board = board.placed(mv);
      } else {
        board.foul(turn);
        result.foul = true;
        break;
      }
    }
    result.score = board.score();
    return result;
  }

  static Player makePlayer(String id, Color color, long seed) {
    return switch (id) {
      case "x00" -> new p26x00.OurPlayer(color);
      case "x42base" -> new p26x42base.OurPlayer(color);
      case "x42" -> new p26x42.OurPlayer(color);
      case "random" -> new FixedRandomPlayer(color, seed);
      default -> throw new IllegalArgumentException("unknown player: " + id);
    };
  }

  static String colorName(Color c) {
    return c == BLACK ? "BLACK" : c == WHITE ? "WHITE" : c == NONE ? "NONE" : "BLOCK";
  }
}
