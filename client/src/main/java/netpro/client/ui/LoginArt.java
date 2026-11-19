package netpro.client.ui;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

/** Left panel of the Login screen: felt with a reversi board and a sheet of caro paper, drawn in code. */
public class LoginArt extends Pane {
    public static final double WIDTH = 480;

    public LoginArt() {
        setPrefWidth(WIDTH);
        setMinWidth(WIDTH);
        setMaxWidth(WIDTH);
        setBackground(Theme.felt(Theme.FELT_DEEP, 0));

        Label title = new Label("Adversarial Game");
        title.getStyleClass().add("felt-title");
        title.relocate(48, 40);

        getChildren().addAll(title, reversi(), paper());
    }

    private StackPane reversi() {
        double cell = 44, n = 6;
        Canvas c = new Canvas(cell * n + 1, cell * n + 1);
        GraphicsContext gc = c.getGraphicsContext2D();
        gc.setFill(Theme.FELT_BOARD);
        gc.fillRect(0, 0, c.getWidth(), c.getHeight());
        gc.setStroke(Color.rgb(10, 30, 20, 0.55));
        gc.setLineWidth(1);
        for (int i = 0; i <= n; i++) {
            double p = i * cell + 0.5;
            gc.strokeLine(p, 0, p, c.getHeight());
            gc.strokeLine(0, p, c.getWidth(), p);
        }
        int[][] discs = {{1, 2, 2}, {1, 3, 1}, {2, 1, 1}, {2, 2, 2}, {2, 3, 1}, {2, 4, 1}, {3, 2, 1}, {3, 3, 2}, {3, 4, 2}, {4, 3, 2}, {4, 1, 1}};
        for (int[] d : discs) Pen.disc(gc, (d[1] + 0.5) * cell, (d[0] + 0.5) * cell - 1, 16, d[2], 1, 1);

        StackPane frame = new StackPane(c);
        frame.getStyleClass().add("frame");
        frame.relocate(70, 176);
        frame.setRotate(-6);
        return frame;
    }

    private Canvas paper() {
        double w = 196, h = 168, step = 28;
        Canvas c = new Canvas(w, h);
        GraphicsContext gc = c.getGraphicsContext2D();
        gc.setFill(Theme.PAPER);
        gc.fillRect(0, 0, w, h);
        gc.setStroke(Theme.PAPER_LINE);
        gc.setLineWidth(1);
        for (double x = 0.5; x < w; x += step) gc.strokeLine(x, 0, x, h);
        for (double y = 0.5; y < h; y += step) gc.strokeLine(0, y, w, y);
        for (int i = 0; i < 5; i++) Pen.cross(gc, 42 + i * step, 70, 22, Theme.RED, 2.6);
        Pen.ring(gc, 98, 42, 24, Theme.BLUE, 2.4);
        Pen.ring(gc, 126, 98, 24, Theme.BLUE, 2.4);
        Pen.ring(gc, 70, 98, 24, Theme.BLUE, 2.4);
        gc.setStroke(Color.rgb(28, 29, 26, 0.7));
        gc.setLineWidth(2);
        gc.beginPath();
        gc.moveTo(26, 71);
        gc.quadraticCurveTo(98, 66, 170, 69);
        gc.stroke();

        c.setEffect(new DropShadow(20, 0, 8, Color.rgb(20, 30, 20, 0.35)));
        c.relocate(236, 330);
        c.setRotate(5);
        return c;
    }
}
