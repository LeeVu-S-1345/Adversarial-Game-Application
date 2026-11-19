package netpro.client.ui;

import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

/** Small building blocks used by the lobby and the game screen. */
public final class Widgets {
    private Widgets() {}

    /** Round monogram in the player's color. */
    public static StackPane avatar(String name, double size) {
        Circle c = new Circle(size / 2, Theme.avatarColor(name));
        Label l = new Label(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase());
        l.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: " + Math.round(size * 0.43) + "px;");
        StackPane p = new StackPane(c, l);
        p.setMinSize(size, size);
        p.setMaxSize(size, size);
        return p;
    }

    /** App mark: a black disc, a red cross and a blue ring. */
    public static Canvas logo() {
        Canvas c = new Canvas(26, 26);
        GraphicsContext gc = c.getGraphicsContext2D();
        gc.setFill(Theme.INK);
        gc.fillOval(1.5, 5.5, 15, 15);
        Pen.cross(gc, 19.5, 10.5, 12, Theme.RED, 2.4);
        gc.setStroke(Theme.BLUE);
        gc.setLineWidth(2);
        gc.strokeOval(15.5, 16, 8, 8);
        return c;
    }

    /** Piece icon for player cards and the move list. side 1 / 2; caro draws ✕ / ◯, reversi a disc. */
    public static Canvas piece(boolean caro, int side, double size) {
        Canvas c = new Canvas(size, size);
        GraphicsContext gc = c.getGraphicsContext2D();
        if (caro) {
            if (side == 1) Pen.cross(gc, size / 2, size / 2, size, Theme.RED, Math.max(2, size / 7));
            else Pen.ring(gc, size / 2, size / 2, size * 1.05, Theme.BLUE, Math.max(2, size / 8));
        } else {
            gc.setFill(Pen.discPaint(side));
            gc.fillOval(0.5, 0.5, size - 1, size - 1);
            if (side == 2) {
                gc.setStroke(Color.web("#c9c2b2"));
                gc.setLineWidth(0.8);
                gc.strokeOval(0.5, 0.5, size - 1, size - 1);
            }
        }
        return c;
    }

    public static Label label(String text, String... styles) {
        Label l = new Label(text);
        l.getStyleClass().addAll(styles);
        return l;
    }

    public static Button button(String text, String... styles) {
        Button b = new Button(text);
        b.getStyleClass().add("btn");
        b.getStyleClass().addAll(styles);
        return b;
    }

    public static Region grow() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    /** Section heading such as "ONLINE NOW  4 players". */
    public static HBox section(String title, Label count) {
        HBox h = new HBox(8, label(title.toUpperCase(), "sec"));
        if (count != null) {
            count.getStyleClass().add("sec-count");
            h.getChildren().add(count);
        }
        h.setStyle("-fx-alignment: baseline-left;");
        return h;
    }

    public static Node vsep(double height) {
        Region r = new Region();
        r.getStyleClass().add("vsep");
        r.setMinHeight(height);
        r.setMaxHeight(height);
        return r;
    }
}
