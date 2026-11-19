package netpro.client.app.game;

import netpro.client.protocol.Wire;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Bàn cờ phía client. Protocol không gửi lại cả bàn cờ, nên client tự dựng từ
 * STRT (thế khởi đầu), MOVE|0|... (quân của mình) và OPPM (quân đối thủ).
 *
 * Các hàm luật ở đây (nước đi hợp lệ của reversi, hàng 5 của caro) chỉ dùng để HIỂN THỊ
 * (gợi ý ô đi được, tô hàng thắng, lý do kết thúc). Server vẫn là bên quyết định.
 */
public class GameSession {
    /** Một dòng trong danh sách nước đi. no = 0 với lượt bị bỏ qua (SKIP). */
    public record Entry(int no, int side, String by, String text) {}

    private final String gameType;   // "caro" hoặc "reversi"
    private final int myNo;          // <side> của mình: 1 đi trước
    private final String myName;
    private final String opponent;
    private final int rows, cols;
    private final int[][] cells;     // 0 trống, 1 hoặc 2 là <side>
    private final List<Entry> history = new ArrayList<>();

    private int turn = 1;
    private int moves;
    private int lastMover = 0;
    private int lastRow = -1, lastCol = -1;
    private List<int[]> lastFlips = List.of();
    private boolean over;
    private boolean resignedByMe;
    private String result = "";
    private String delta = "";
    private int newElo;

    /** STRT|<game>|<side>|<opponent>|<rows>,<cols> */
    public GameSession(String gameType, int myNo, String myName, String opponent, int rows, int cols) {
        this.gameType = gameType;
        this.myNo = myNo;
        this.myName = myName;
        this.opponent = opponent;
        this.rows = rows;
        this.cols = cols;
        this.cells = new int[rows][cols];
        if (isReversi()) {   // thế khởi đầu (docs/protocol.md, mục 9)
            cells[3][3] = 2;
            cells[4][4] = 2;
            cells[3][4] = 1;
            cells[4][3] = 1;
        }
    }

    /** Dùng chung cho MOVE|0|k|flips (quân của mình) và OPPM (quân đối thủ). */
    public void apply(int player, int r, int c, List<int[]> flips) {
        cells[r][c] = player;
        for (int[] f : flips) cells[f[0]][f[1]] = player;
        lastMover = player;
        lastRow = r;
        lastCol = c;
        lastFlips = flips;
        turn = 3 - player;
        moves++;
        history.add(new Entry(moves, player, nameOf(player), r + "," + c + (flips.isEmpty() ? "" : "  +" + flips.size())));
    }

    /** SKIP|<side>: bên <side> mất lượt, lượt thuộc về bên còn lại. */
    public void onSkip(int skippedSide) {
        turn = 3 - skippedSide;
        history.add(new Entry(0, skippedSide, nameOf(skippedSide), "skip"));
    }

    /** OVER|<result>|<delta>|<newRating> */
    public void end(String result, String delta, int newElo) {
        this.over = true;
        this.result = result;
        this.delta = delta;
        this.newElo = newElo;
    }

    public void markResigned() { resignedByMe = true; }

    // ---------- rules used only for display ----------

    /** Quân bị lật nếu <side> đặt ở (r, c); rỗng nếu nước đó không hợp lệ (reversi). */
    public List<int[]> flipsFor(int r, int c, int side) {
        List<int[]> out = new ArrayList<>();
        if (!isReversi() || cells[r][c] != 0) return out;
        int[][] dirs = {{-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1}};
        for (int[] d : dirs) {
            List<int[]> line = new ArrayList<>();
            int i = r + d[0], j = c + d[1];
            while (inside(i, j) && cells[i][j] == 3 - side) {
                line.add(new int[]{i, j});
                i += d[0];
                j += d[1];
            }
            if (!line.isEmpty() && inside(i, j) && cells[i][j] == side) out.addAll(line);
        }
        return out;
    }

    public boolean isLegal(int r, int c, int side) {
        if (cells[r][c] != 0) return false;
        return !isReversi() || !flipsFor(r, c, side).isEmpty();
    }

    public List<int[]> legalMoves(int side) {
        List<int[]> out = new ArrayList<>();
        if (!isReversi()) return out;
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) if (!flipsFor(r, c, side).isEmpty()) out.add(new int[]{r, c});
        return out;
    }

    /** Hàng từ 5 quân trở lên đi qua (r, c) của quân đang ở ô đó (caro); rỗng nếu không có. */
    public List<int[]> lineThrough(int r, int c) {
        if (isReversi() || !inside(r, c) || cells[r][c] == 0) return List.of();
        int s = cells[r][c];
        int[][] dirs = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};
        for (int[] d : dirs) {
            List<int[]> line = new ArrayList<>();
            line.add(new int[]{r, c});
            for (int sign = -1; sign <= 1; sign += 2) {
                int i = r + sign * d[0], j = c + sign * d[1];
                while (inside(i, j) && cells[i][j] == s) {
                    line.add(new int[]{i, j});
                    i += sign * d[0];
                    j += sign * d[1];
                }
            }
            if (line.size() >= 5) return line;
        }
        return List.of();
    }

    public int count(int side) {
        int n = 0;
        for (int[] row : cells) for (int v : row) if (v == side) n++;
        return n;
    }

    private boolean inside(int r, int c) { return r >= 0 && r < rows && c >= 0 && c < cols; }

    private String nameOf(int side) { return side == myNo ? myName : opponent; }

    // ---------- state ----------

    public boolean isMyTurn() { return !over && turn == myNo; }
    public boolean isCaro() { return gameType.equals(Wire.CARO); }
    public boolean isReversi() { return gameType.equals(Wire.REVERSI); }

    public String gameType() { return gameType; }
    public int myNo() { return myNo; }
    public String opponent() { return opponent; }
    public int size() { return rows; }   // hai game hiện có đều dùng bàn vuông
    public int rows() { return rows; }
    public int cols() { return cols; }
    public int cell(int r, int c) { return cells[r][c]; }
    public int moves() { return moves; }
    public int lastMover() { return lastMover; }
    public int lastRow() { return lastRow; }
    public int lastCol() { return lastCol; }
    public List<int[]> lastFlips() { return lastFlips; }
    public boolean isOver() { return over; }
    public boolean resignedByMe() { return resignedByMe; }
    public String result() { return result; }
    public String delta() { return delta; }
    public int newElo() { return newElo; }
    /** Mới nhất trước. */
    public List<Entry> historyNewestFirst() {
        List<Entry> h = new ArrayList<>(history);
        Collections.reverse(h);
        return h;
    }

    // "x,y" (x = hàng, y = cột, đếm từ 0)
    public static int[] parsePos(String s) {
        String[] p = s.split(",");
        return new int[]{Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim())};
    }

    // "x1,y1;x2,y2;..." (rỗng nếu không có quân bị lật)
    public static List<int[]> parseFlips(String s) {
        List<int[]> out = new ArrayList<>();
        if (s == null || s.isEmpty()) return out;
        for (String part : s.split(";")) out.add(parsePos(part));
        return out;
    }
}
