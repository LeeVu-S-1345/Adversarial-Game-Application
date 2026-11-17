package netpro.client.app.game;

import netpro.client.protocol.Wire;

import java.util.ArrayList;
import java.util.List;

/**
 * Bàn cờ phía client. Protocol không gửi lại cả bàn cờ, nên client tự dựng từ
 * STRT (thế khởi đầu), MOVE|0|... (quân của mình) và OPPM (quân đối thủ).
 */
public class GameSession {
    private final String gameType;   // "caro" hoặc "reversi"
    private final int myNo;          // <side> của mình: 1 đi trước
    private final String opponent;
    private final int rows, cols;
    private final int[][] cells;     // 0 trống, 1 hoặc 2 là <side>

    private int turn = 1;
    private int lastMover = 0;
    private int lastRow = -1, lastCol = -1;
    private boolean over;
    private String result = "";
    private String delta = "";
    private int newElo;

    /** STRT|<game>|<side>|<opponent>|<rows>,<cols> */
    public GameSession(String gameType, int myNo, String opponent, int rows, int cols) {
        this.gameType = gameType;
        this.myNo = myNo;
        this.opponent = opponent;
        this.rows = rows;
        this.cols = cols;
        this.cells = new int[rows][cols];
        if (gameType.equals(Wire.REVERSI)) {   // thế khởi đầu (docs/protocol.md, mục 9)
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
        turn = 3 - player;
    }

    /** SKIP|<side>: bên <side> mất lượt, lượt thuộc về bên còn lại. */
    public void onSkip(int skippedSide) { turn = 3 - skippedSide; }

    /** OVER|<result>|<delta>|<newRating> */
    public void end(String result, String delta, int newElo) {
        this.over = true;
        this.result = result;
        this.delta = delta;
        this.newElo = newElo;
    }

    public boolean isMyTurn() { return !over && turn == myNo; }

    public String gameType() { return gameType; }
    public int myNo() { return myNo; }
    public String opponent() { return opponent; }
    public int size() { return rows; }   // hai game hiện có đều dùng bàn vuông
    public int rows() { return rows; }
    public int cols() { return cols; }
    public int cell(int r, int c) { return cells[r][c]; }
    public int lastMover() { return lastMover; }
    public int lastRow() { return lastRow; }
    public int lastCol() { return lastCol; }
    public boolean isOver() { return over; }
    public String result() { return result; }
    public String delta() { return delta; }
    public int newElo() { return newElo; }

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
