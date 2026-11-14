package netpro.client.app.game;

import java.util.ArrayList;
import java.util.List;

public class GameSession {
    private final String gameType;
    private final int myNo;
    private final String opponent;
    private final int size;
    private final int[][] cells;

    private int turn = 1;
    private int lastMover = 0;
    private int lastRow = -1, lastCol = -1;
    private boolean over;
    private String result = "";
    private int newElo;

    public GameSession(String gameType, int myNo, String opponent) {
        this.gameType = gameType;
        this.myNo = myNo;
        this.opponent = opponent;
        this.size = gameType.equals("REVERSI") ? 8 : 15;
        this.cells = new int[size][size];
        if (gameType.equals("REVERSI")) {   // thế khởi đầu giống server
            cells[3][3] = 2;
            cells[4][4] = 2;
            cells[3][4] = 1;
            cells[4][3] = 1;
        }
    }

    /** Dùng chung cho MOVE|0|flips (quân của mình) và OPPM (quân đối thủ). */
    public void apply(int player, int r, int c, List<int[]> flips) {
        cells[r][c] = player;
        for (int[] f : flips) cells[f[0]][f[1]] = player;
        lastMover = player;
        lastRow = r;
        lastCol = c;
        turn = 3 - player;
    }

    /** SKIP: đối thủ của người vừa đi hết nước, người vừa đi đi tiếp. */
    public void onSkip() { turn = lastMover; }

    public void end(String result, int newElo) {
        this.over = true;
        this.result = result;
        this.newElo = newElo;
    }

    public boolean isMyTurn() { return !over && turn == myNo; }

    public String gameType() { return gameType; }
    public int myNo() { return myNo; }
    public String opponent() { return opponent; }
    public int size() { return size; }
    public int cell(int r, int c) { return cells[r][c]; }
    public int lastMover() { return lastMover; }
    public int lastRow() { return lastRow; }
    public int lastCol() { return lastCol; }
    public boolean isOver() { return over; }
    public String result() { return result; }
    public int newElo() { return newElo; }

    // "r,c"
    public static int[] parsePos(String s) {
        String[] p = s.split(",");
        return new int[]{Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim())};
    }

    // "r,c;r,c;..." (rỗng nếu không có quân bị lật)
    public static List<int[]> parseFlips(String s) {
        List<int[]> out = new ArrayList<>();
        if (s == null || s.isEmpty()) return out;
        for (String part : s.split(";")) out.add(parsePos(part));
        return out;
    }
}