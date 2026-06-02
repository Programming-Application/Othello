package p26xdd;

import ap26.Board;
import ap26.Color;
import ap26.Move;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static ap26.Board.LENGTH;
import static ap26.Board.SIZE;
import static ap26.Color.BLACK;
import static ap26.Color.BLOCK;
import static ap26.Color.NONE;
import static ap26.Color.WHITE;

public class MyBoard implements Board, Cloneable {
    static final int EXACT_ENDGAME_EMPTY_THRESHOLD = 8;

    private final Color[] board;
    private final Color[] initialBoard;
    private final boolean[] blocked;
    private Move move;
    private String boardId;

    public MyBoard() {
        this.board = new Color[LENGTH];
        this.initialBoard = new Color[LENGTH];
        this.blocked = new boolean[LENGTH];
        Arrays.fill(this.board, NONE);
        Arrays.fill(this.initialBoard, NONE);
        this.move = Move.ofPass(NONE);
        init();
        recordInitialPosition();
    }

    private MyBoard(Color[] board, Color[] initialBoard, boolean[] blocked, Move move, String boardId) {
        this.board = Arrays.copyOf(board, board.length);
        this.initialBoard = Arrays.copyOf(initialBoard, initialBoard.length);
        this.blocked = Arrays.copyOf(blocked, blocked.length);
        this.move = move;
        this.boardId = boardId;
    }

    public static MyBoard copyOf(Board source) {
        MyBoard copied = new MyBoard();
        copied.copyFrom(source);
        return copied;
    }

    public void copyFrom(Board source) {
        for (int k = 0; k < LENGTH; k++) {
            Color color = source.get(k);
            this.board[k] = color;
            this.initialBoard[k] = color;
            this.blocked[k] = color == BLOCK;
        }
        this.move = source.getMove();
        this.boardId = source.getBoardId();
    }

    public void syncCurrentPosition(Board source) {
        for (int k = 0; k < LENGTH; k++) {
            Color color = source.get(k);
            if (this.blocked[k] || color == BLOCK) {
                this.board[k] = BLOCK;
                this.blocked[k] = true;
            } else {
                this.board[k] = color;
            }
        }
        this.move = source.getMove();
        this.boardId = source.getBoardId();
    }

    public void syncLastMove(Board source) {
        applyMove(source.getMove());
        this.boardId = source.getBoardId();
    }

    public void applyMove(Move move) {
        this.move = move;

        if (!move.isLegal() || move.isPass() || move.isNone()) {
            return;
        }

        int index = move.getIndex();
        if (isBlocked(index) || get(index) != NONE) {
            return;
        }

        Color color = move.getColor();
        for (List<Integer> line : lines(index)) {
            for (Move flippable : outflanked(line, color)) {
                this.board[flippable.getIndex()] = color;
            }
        }
        set(index, color);
    }

    public boolean hasSamePosition(Board source) {
        for (int k = 0; k < LENGTH; k++) {
            if (this.board[k] != source.get(k)) {
                return false;
            }
        }
        return true;
    }

    public boolean hasSameState(Board source) {
        return this.move.equals(source.getMove()) && hasSamePosition(source);
    }

    public String firstDifference(Board source) {
        if (!this.move.equals(source.getMove())) {
            return "move: internal=" + this.move + ", external=" + source.getMove();
        }
        for (int k = 0; k < LENGTH; k++) {
            if (this.board[k] != source.get(k)) {
                return Move.toIndexString(k) + ": internal=" + this.board[k] + ", external=" + source.get(k);
            }
        }
        return "none";
    }

    private void recordInitialPosition() {
        for (int k = 0; k < LENGTH; k++) {
            this.initialBoard[k] = this.board[k];
            this.blocked[k] = this.board[k] == BLOCK;
        }
    }

    private void init() {
        set(Move.parseIndex("c3"), BLACK);
        set(Move.parseIndex("d4"), BLACK);
        set(Move.parseIndex("d3"), WHITE);
        set(Move.parseIndex("c4"), WHITE);
    }

    @Override
    public Color get(int k) {
        return this.board[k];
    }

    @Override
    public Move getMove() {
        return this.move;
    }

    @Override
    public Color getTurn() {
        return this.move.isNone() ? BLACK : this.move.getColor().flipped();
    }

    public void set(int k, Color color) {
        if (color == BLOCK) {
            this.blocked[k] = true;
        }
        if (this.blocked[k]) {
            this.board[k] = BLOCK;
            return;
        }
        this.board[k] = color;
    }

    boolean isBlocked(int k) {
        return this.blocked[k];
    }

    Color getInitial(int k) {
        return this.initialBoard[k];
    }

    @Override
    public int count(Color color) {
        return countAll().getOrDefault(color, 0);
    }

    int countEmptySquares() {
        return count(NONE);
    }

    boolean isExactEndgame() {
        return countEmptySquares() <= EXACT_ENDGAME_EMPTY_THRESHOLD;
    }

    BoardAnalyzer analyzer() {
        return new BoardAnalyzer(this);
    }

    boolean isCorner(int k) {
        return hasHorizontalOffBoard(k) && hasVerticalOffBoard(k);
    }

    boolean isPseudoCorner(int k) {
        if (isCorner(k)) {
            return false;
        }

        boolean horizontalOffBoard = hasHorizontalOffBoard(k);
        boolean verticalOffBoard = hasVerticalOffBoard(k);
        boolean horizontalBlock = hasHorizontalBlock(k);
        boolean verticalBlock = hasVerticalBlock(k);

        return (horizontalOffBoard && verticalBlock)
                || (verticalOffBoard && horizontalBlock)
                || countOrthogonalBlocks(k) >= 2;
    }

    boolean isBlockAdjacentBonusSquare(int k) {
        return !isCorner(k) && !isPseudoCorner(k) && countOrthogonalBlocks(k) == 1;
    }

    private boolean hasHorizontalOffBoard(int k) {
        int col = k % SIZE;
        return col == 0 || col == SIZE - 1;
    }

    private boolean hasVerticalOffBoard(int k) {
        int row = k / SIZE;
        return row == 0 || row == SIZE - 1;
    }

    private boolean hasHorizontalBlock(int k) {
        int row = k / SIZE;
        int col = k % SIZE;
        return isBlockAt(col - 1, row) || isBlockAt(col + 1, row);
    }

    private boolean hasVerticalBlock(int k) {
        int row = k / SIZE;
        int col = k % SIZE;
        return isBlockAt(col, row - 1) || isBlockAt(col, row + 1);
    }

    private int countOrthogonalBlocks(int k) {
        int row = k / SIZE;
        int col = k % SIZE;
        int count = 0;
        int[][] offsets = { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } };
        for (int[] offset : offsets) {
            if (isBlockAt(col + offset[0], row + offset[1])) {
                count++;
            }
        }
        return count;
    }

    private boolean isBlockAt(int col, int row) {
        return Move.isValid(col, row) && isBlocked(Move.index(col, row));
    }

    boolean isDangerous(int k) {
        int row = k / SIZE;
        int col = k % SIZE;
        int[][] corners = { { 0, 0 }, { SIZE - 1, 0 }, { 0, SIZE - 1 }, { SIZE - 1, SIZE - 1 } };

        for (int[] corner : corners) {
            int cornerIndex = Move.index(corner[0], corner[1]);
            if (get(cornerIndex) != NONE || isBlocked(cornerIndex)) {
                continue;
            }
            if (Math.abs(col - corner[0]) <= 1 && Math.abs(row - corner[1]) <= 1) {
                return true;
            }
        }
        return false;
    }

    int countStableCandidates(Color color) {
        return analyzer().stableDiscs(color);
    }

    @Override
    public boolean isEnd() {
        return !hasLegalMove(BLACK) && !hasLegalMove(WHITE);
    }

    boolean hasLegalMove(Color color) {
        return !findNoPassLegalIndexes(color).isEmpty();
    }

    @Override
    public Color winner() {
        int value = score();
        if (!isEnd() || value == 0) {
            return NONE;
        }
        return value > 0 ? BLACK : WHITE;
    }

    @Override
    public void foul(Color color) {
        Color winner = color.flipped();
        IntStream.range(0, LENGTH).forEach(k -> this.board[k] = winner);
    }

    @Override
    public int score() {
        int black = count(BLACK);
        int white = count(WHITE);
        int empty = count(NONE);
        int score = black - white;

        if (black == 0 || white == 0) {
            score += Integer.signum(score) * empty;
        }

        return score;
    }

    private Map<Color, Integer> countAll() {
        Map<Color, Integer> counts = new EnumMap<>(Color.class);
        for (Color color : this.board) {
            counts.merge(color, 1, Integer::sum);
        }
        return counts;
    }

    @Override
    public List<Move> findLegalMoves(Color color) {
        return findLegalIndexes(color).stream()
                .map(k -> Move.of(k, color))
                .toList();
    }

    private List<Integer> findLegalIndexes(Color color) {
        List<Integer> moves = findNoPassLegalIndexes(color);
        if (moves.isEmpty()) {
            moves.add(Move.PASS);
        }
        return moves;
    }

    List<Integer> findNoPassLegalIndexes(Color color) {
        List<Integer> moves = new ArrayList<>();
        for (int k = 0; k < LENGTH; k++) {
            if (this.board[k] != NONE || isBlocked(k)) {
                continue;
            }
            for (List<Integer> line : lines(k)) {
                if (!outflanked(line, color).isEmpty()) {
                    moves.add(k);
                    break;
                }
            }
        }
        return moves;
    }

    private List<List<Integer>> lines(int k) {
        List<List<Integer>> lines = new ArrayList<>();
        for (int dir = 0; dir < 8; dir++) {
            lines.add(Move.line(k, dir));
        }
        return lines;
    }

    private List<Move> outflanked(List<Integer> line, Color color) {
        if (line.size() <= 1) {
            return new ArrayList<>();
        }

        List<Move> flippables = new ArrayList<>();
        for (int k : line) {
            Color current = get(k);
            if (current == NONE || current == BLOCK) {
                break;
            }
            if (current == color) {
                return flippables;
            }
            flippables.add(Move.of(k, color));
        }
        return new ArrayList<>();
    }

    @Override
    public MyBoard placed(Move move) {
        MyBoard next = clone();
        next.move = move;

        if (move.isPass() || move.isNone()) {
            return next;
        }

        int index = move.getIndex();
        if (next.isBlocked(index) || next.get(index) != NONE) {
            return next;
        }

        Color color = move.getColor();
        for (List<Integer> line : next.lines(index)) {
            for (Move flippable : next.outflanked(line, color)) {
                next.board[flippable.getIndex()] = color;
            }
        }
        next.set(index, color);

        return next;
    }

    @Override
    public MyBoard flipped() {
        MyBoard next = clone();
        IntStream.range(0, LENGTH)
                .forEach(k -> next.board[k] = next.board[k].flipped());
        next.move = this.move.flipped();
        return next;
    }

    @Override
    public MyBoard clone() {
        return new MyBoard(this.board, this.initialBoard, this.blocked, this.move, this.boardId);
    }

    @Override
    public void setBoardId(String boardId) {
        this.boardId = boardId;
    }

    @Override
    public String getBoardId() {
        return this.boardId;
    }
}
