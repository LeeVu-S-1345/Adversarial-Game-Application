package netpro.client.ui;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.Paint;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;

/** Drawing of pieces, shared by the boards and the small icons. */
public final class Pen {
    private Pen() {}

    /** Ball-pen cross: two slightly curved strokes. (cx, cy) is the center, s the box size. */
    public static void cross(GraphicsContext gc, double cx, double cy, double s, Color color, double width) {
        double k = s / 20.0, x = cx - s / 2, y = cy - s / 2;
        gc.setStroke(color);
        gc.setLineWidth(width);
        gc.setLineCap(StrokeLineCap.ROUND);
        gc.beginPath();
        gc.moveTo(x + 4.5 * k, y + 4.2 * k);
        gc.quadraticCurveTo(x + 10 * k, y + 9.8 * k, x + 15.7 * k, y + 15.6 * k);
        gc.moveTo(x + 15.5 * k, y + 4.4 * k);
        gc.quadraticCurveTo(x + 10.1 * k, y + 10.2 * k, x + 4.3 * k, y + 15.8 * k);
        gc.stroke();
    }

    /** Ball-pen circle with a small gap where the pen was lifted. */
    public static void ring(GraphicsContext gc, double cx, double cy, double s, Color color, double width) {
        double r = s * 0.31;
        gc.setStroke(color);
        gc.setLineWidth(width);
        gc.setLineCap(StrokeLineCap.ROUND);
        gc.strokeArc(cx - r, cy - r, 2 * r, 2 * r, 80, 330, ArcType.OPEN);
    }

    /** Shaded reversi disc. side 1 = black, 2 = white. scaleX < 1 is used by the flip animation. */
    public static void disc(GraphicsContext gc, double cx, double cy, double r, int side, double scaleX, double alpha) {
        gc.save();
        gc.setGlobalAlpha(alpha);
        gc.translate(cx, cy);
        gc.scale(Math.max(scaleX, 0.02), 1);
        gc.setFill(Color.rgb(0, 0, 0, 0.22));
        gc.fillOval(-r, -r + 4, 2 * r, 2 * r);
        gc.setFill(side == 1 ? Color.BLACK : Color.web("#a59d8c"));
        gc.fillOval(-r, -r + 2, 2 * r, 2 * r);
        gc.setFill(discPaint(side));
        gc.fillOval(-r, -r, 2 * r, 2 * r);
        gc.restore();
    }

    public static Paint discPaint(int side) {
        Stop[] stops = side == 1
                ? new Stop[]{new Stop(0, Color.web("#55524c")), new Stop(0.52, Color.web("#1b1a18")), new Stop(1, Color.web("#0d0c0b"))}
                : new Stop[]{new Stop(0, Color.WHITE), new Stop(0.5, Color.web("#f1ece0")), new Stop(1, Color.web("#d6cfbf"))};
        return new RadialGradient(0, 0, 0.34, 0.30, 0.78, true, CycleMethod.NO_CYCLE, stops);
    }
}
