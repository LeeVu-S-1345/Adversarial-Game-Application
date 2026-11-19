package netpro.client.ui.board;

import javafx.animation.AnimationTimer;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.geometry.VPos;
import netpro.client.app.game.GameSession;
import netpro.client.ui.Pen;
import netpro.client.ui.Theme;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Bàn cờ (docs/ui-spec.md, mục 6). Caro: tờ giấy ô ly, ✕ / ◯ nét bút. Reversi: bàn nỉ viền gỗ, quân có khối.
 * Hai lớp canvas: lớp dưới (giấy hoặc nỉ, đường kẻ, tọa độ) vẽ một lần; lớp trên (quân, gợi ý, con trỏ) vẽ lại.
 */
public class BoardView extends StackPane {
    private static final long DROP_NS = 200_000_000L, FLIP_NS = 380_000_000L;

    private final GameSession g;
    private final boolean caro;
    private final int n;
    private final double cell, left, top;
    private final Canvas bg, fg;
    private BiConsumer<Integer, Integer> onCell = (r, c) -> { };

    private int hoverR = -1, hoverC = -1;
    private Set<String> win = Set.of();

    // hiệu ứng cho nước vừa đi: quân rơi xuống, quân bị lật xoay lại
    private int animR = -1, animC = -1;
    private Set<String> animFlips = Set.of();
    private long animStart;
    private final AnimationTimer timer = new AnimationTimer() {
        @Override public void handle(long now) {
            drawPieces(now);
            if (now - animStart > FLIP_NS) {
                stop();
                animR = -1;
                animFlips = Set.of();
                drawPieces(now);
            }
        }
    };

    public BoardView(GameSession g) {
        this.g = g;
        this.caro = g.isCaro();
        this.n = g.size();
        this.cell = caro ? 29 : 52;
        this.left = caro ? 20 : 16;
        this.top = caro ? 18 : 16;
        double w = left + n * cell + 1, h = top + n * cell + 1;
        bg = new Canvas(w, h);
        fg = new Canvas(w, h);
        Pane layers = new Pane(bg, fg);
        layers.setPrefSize(w, h);
        layers.setMaxSize(w, h);
        getChildren().add(layers);
        getStyleClass().add(caro ? "paper" : "frame");
        setMaxSize(USE_PREF_SIZE, USE_PREF_SIZE);

        fg.setOnMouseMoved(e -> hover(cellAt(e.getY(), top), cellAt(e.getX(), left)));
        fg.setOnMouseExited(e -> hover(-1, -1));
        fg.setOnMouseClicked(e -> {
            int r = cellAt(e.getY(), top), c = cellAt(e.getX(), left);
            if (r >= 0 && c >= 0) onCell.accept(r, c);
        });
        drawBoard();
        drawPieces(0);
    }

    public void setOnCell(BiConsumer<Integer, Integer> handler) { this.onCell = handler; }

    /** Vẽ lại sau khi trạng thái ván đổi (lượt, kết thúc...). */
    public void refresh() { drawPieces(System.nanoTime()); }

    /** Gọi sau MOVE|0 hoặc OPPM: chạy hiệu ứng cho quân (r, c) và các quân bị lật. */
    public void animateMove(int r, int c, List<int[]> flips) {
        animR = r;
        animC = c;
        Set<String> f = new HashSet<>();
        for (int[] p : flips) f.add(p[0] + "," + p[1]);
        animFlips = f;
        animStart = System.nanoTime();
        timer.start();
    }

    /** Tô các ô của hàng thắng (caro). */
    public void setWinLine(List<int[]> cells) {
        Set<String> s = new HashSet<>();
        for (int[] p : cells) s.add(p[0] + "," + p[1]);
        win = s;
        refresh();
    }

    private int cellAt(double pos, double offset) {
        int i = (int) Math.floor((pos - offset) / cell);
        return i >= 0 && i < n ? i : -1;
    }

    private boolean playable(int r, int c) {
        return r >= 0 && c >= 0 && g.isMyTurn() && g.isLegal(r, c, g.myNo());
    }

    private void hover(int r, int c) {
        if (r == hoverR && c == hoverC) return;
        hoverR = r;
        hoverC = c;
        fg.setCursor(playable(r, c) ? Cursor.HAND : Cursor.DEFAULT);
        refresh();
    }

    // ---------- lớp dưới ----------

    private void drawBoard() {
        GraphicsContext gc = bg.getGraphicsContext2D();
        double size = n * cell;
        if (caro) {
            gc.setStroke(Theme.PAPER_LINE);
            gc.setLineWidth(1);
            for (int i = 0; i <= n; i++) {
                double p = Math.floor(i * cell) + 0.5;
                gc.strokeLine(left + p, top, left + p, top + size);
                gc.strokeLine(left, top + p, left + size, top + p);
            }
        } else {
            gc.setFill(Theme.FELT_BOARD);
            gc.fillRect(left, top, size, size);
            gc.setFill(Color.rgb(255, 255, 255, 0.05));   // vân nỉ
            for (double y = top + 1; y < top + size; y += 4) for (double x = left + 1; x < left + size; x += 4) gc.fillRect(x, y, 1, 1);
            gc.setFill(Color.rgb(0, 0, 0, 0.18));   // bóng trong ở mép trên
            gc.fillRect(left, top, size, 3);
            gc.setStroke(Color.rgb(10, 30, 20, 0.55));
            gc.setLineWidth(1);
            for (int i = 0; i <= n; i++) {
                double p = Math.floor(i * cell) + 0.5;
                gc.strokeLine(left + p, top, left + p, top + size);
                gc.strokeLine(left, top + p, left + size, top + p);
            }
        }
        // tọa độ đếm từ 0, giống MOVE|<x>,<y> (x = hàng, y = cột)
        gc.setFont(Font.font(Theme.MONO, 10.5));
        gc.setFill(caro ? Theme.FAINT : Color.web("#d8c6b2"));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        for (int i = 0; i < n; i++) {
            gc.fillText(String.valueOf(i), left + (i + 0.5) * cell, top / 2);
            gc.fillText(String.valueOf(i), left / 2, top + (i + 0.5) * cell);
        }
    }

    // ---------- lớp trên ----------

    private void drawPieces(long now) {
        GraphicsContext gc = fg.getGraphicsContext2D();
        gc.clearRect(0, 0, fg.getWidth(), fg.getHeight());
        double t = animR >= 0 ? (now - animStart) : Long.MAX_VALUE;
        if (caro) drawCaro(gc, t);
        else drawReversi(gc, t);
    }

    private void drawCaro(GraphicsContext gc, double t) {
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                double x = left + c * cell, y = top + r * cell, cx = x + cell / 2, cy = y + cell / 2;
                if (win.contains(r + "," + c)) {
                    gc.setFill(Theme.WIN);
                    gc.fillRect(x + 1, y + 1, cell - 1, cell - 1);
                }
                int v = g.cell(r, c);
                if (v == 0) continue;
                double k = 1, a = 1;
                if (r == animR && c == animC && t < DROP_NS) {
                    double p = t / DROP_NS;
                    k = 1.4 - 0.4 * p;
                    a = p;
                }
                gc.setGlobalAlpha(a);
                mark(gc, cx, cy, 19 * k, v);
                gc.setGlobalAlpha(1);
            }
        }
        if (g.lastRow() >= 0) {   // khung quanh nước vừa đi
            gc.setStroke(Color.rgb(28, 29, 26, 0.55));
            gc.setLineWidth(1.5);
            gc.strokeRoundRect(left + g.lastCol() * cell + 3, top + g.lastRow() * cell + 3, cell - 5, cell - 5, 5, 5);
        }
        if (playable(hoverR, hoverC)) {   // quân mờ dưới con trỏ
            gc.setGlobalAlpha(0.3);
            mark(gc, left + (hoverC + 0.5) * cell, top + (hoverR + 0.5) * cell, 19, g.myNo());
            gc.setGlobalAlpha(1);
        }
    }

    private void mark(GraphicsContext gc, double cx, double cy, double s, int side) {
        if (side == 1) Pen.cross(gc, cx, cy, s, Theme.RED, 2.5);
        else Pen.ring(gc, cx, cy, s, Theme.BLUE, 2.3);
    }

    private void drawReversi(GraphicsContext gc, double t) {
        boolean mine = g.isMyTurn();
        if (playable(hoverR, hoverC)) {
            gc.setFill(Color.rgb(255, 255, 255, 0.07));
            gc.fillRect(left + hoverC * cell + 1, top + hoverR * cell + 1, cell - 1, cell - 1);
        }
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                double cx = left + (c + 0.5) * cell, cy = top + (r + 0.5) * cell - 1;
                int v = g.cell(r, c);
                if (v == 0) {
                    if (mine && g.isLegal(r, c, g.myNo())) {   // gợi ý ô đi được; server vẫn quyết định
                        boolean h = r == hoverR && c == hoverC;
                        gc.setFill(Color.rgb(255, 255, 255, h ? 0.5 : 0.28));
                        gc.fillOval(cx - 5.5, cy + 1 - 5.5, 11, 11);
                    }
                    continue;
                }
                double sx = 1, a = 1;
                int side = v;
                if (r == animR && c == animC && t < DROP_NS) {
                    double p = t / DROP_NS;
                    sx = 1.4 - 0.4 * p;
                    a = p;
                    Pen.disc(gc, cx, cy, 20 * sx, side, 1, a);
                    continue;
                }
                if (animFlips.contains(r + "," + c) && t < FLIP_NS) {
                    double p = t / FLIP_NS;   // nửa đầu: màu cũ thu lại, nửa sau: màu mới mở ra
                    if (p < 0.5) {
                        side = 3 - v;
                        sx = 1 - 2 * p;
                    } else {
                        sx = 2 * p - 1;
                    }
                }
                Pen.disc(gc, cx, cy, 20, side, sx, a);
            }
        }
        if (g.lastRow() >= 0) {   // chấm đỏ ở nước vừa đi
            double cx = left + (g.lastCol() + 0.5) * cell, cy = top + (g.lastRow() + 0.5) * cell - 1;
            gc.setFill(Color.rgb(0, 0, 0, 0.15));
            gc.fillOval(cx - 6, cy - 6, 12, 12);
            gc.setFill(Color.web("#d8473a"));
            gc.fillOval(cx - 4, cy - 4, 8, 8);
        }
    }
}
