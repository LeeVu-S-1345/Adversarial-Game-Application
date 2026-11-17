package netpro.client.ui.board;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import netpro.client.app.game.GameSession;
import netpro.client.protocol.Wire;

import java.util.function.BiConsumer;

public class BoardView extends Canvas {
    private final GameSession g;
    private final double cell;
    private BiConsumer<Integer, Integer> onCell = (r, c) -> { };

    public BoardView(GameSession g) {
        this.g = g;
        this.cell = g.size() == 15 ? 36 : 60;   // Caro 15x15 = 540px, Reversi 8x8 = 480px
        setWidth(g.size() * cell);
        setHeight(g.size() * cell);
        setOnMouseClicked(e -> {
            int c = (int) (e.getX() / cell);
            int r = (int) (e.getY() / cell);
            if (r >= 0 && r < g.size() && c >= 0 && c < g.size()) onCell.accept(r, c);
        });
        draw();
    }

    public void setOnCell(BiConsumer<Integer, Integer> handler) { this.onCell = handler; }

    public void draw() {
        GraphicsContext gc = getGraphicsContext2D();
        double w = getWidth(), h = getHeight();
        int n = g.size();
        boolean gomoku = g.gameType().equals(Wire.CARO);

        gc.setFill(gomoku ? Color.web("#E8C17A") : Color.web("#2E8B57"));
        gc.fillRect(0, 0, w, h);

        gc.setStroke(Color.BLACK);
        gc.setLineWidth(1);
        if (gomoku) {   // quân đặt ở giao điểm
            for (int i = 0; i < n; i++) {
                double p = (i + 0.5) * cell;
                gc.strokeLine(cell / 2, p, w - cell / 2, p);
                gc.strokeLine(p, cell / 2, p, h - cell / 2);
            }
        } else {        // quân đặt trong ô
            for (int i = 0; i <= n; i++) {
                double p = i * cell;
                gc.strokeLine(0, p, w, p);
                gc.strokeLine(p, 0, p, h);
            }
        }

        double rad = cell * 0.42;
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                int v = g.cell(r, c);
                if (v == 0) continue;
                double cx = (c + 0.5) * cell, cy = (r + 0.5) * cell;
                gc.setFill(v == 1 ? Color.BLACK : Color.WHITE);
                gc.fillOval(cx - rad, cy - rad, 2 * rad, 2 * rad);
                gc.setStroke(Color.BLACK);
                gc.strokeOval(cx - rad, cy - rad, 2 * rad, 2 * rad);
            }
        }

        if (g.lastRow() >= 0) {   // viền đỏ ở nước vừa đi
            double cx = (g.lastCol() + 0.5) * cell, cy = (g.lastRow() + 0.5) * cell;
            gc.setStroke(Color.RED);
            gc.setLineWidth(2);
            gc.strokeOval(cx - rad / 2, cy - rad / 2, rad, rad);
        }
    }
}