package netpro.client.ui;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * In-window dialogs drawn over the current screen (recovery code, resign, quit, connection lost).
 * The host is the screen's root StackPane.
 */
public final class Dialogs {
    private Dialogs() {}

    public static final class Handle {
        private final StackPane host;
        private final Node shade;

        private Handle(StackPane host, Node shade) {
            this.host = host;
            this.shade = shade;
        }

        public void close() { host.getChildren().remove(shade); }

        public boolean isOpen() { return host.getChildren().contains(shade); }
    }

    /**
     * Shows a dialog. Each action button closes the dialog before running its own handler,
     * so handlers only add what happens next. Esc runs the first button (the safe choice).
     */
    public static Handle show(StackPane host, String title, boolean danger, Node[] body, Button... actions) {
        VBox card = new VBox();
        card.getStyleClass().add("modal");
        card.setMaxWidth(440);
        card.setPrefWidth(440);
        card.setMaxHeight(Region.USE_PREF_SIZE);

        Label t = new Label(title);
        t.getStyleClass().add("modal-title");
        if (danger) t.getStyleClass().add("danger-text");
        card.getChildren().add(t);
        card.getChildren().addAll(body);

        HBox row = new HBox(10, actions);
        row.setAlignment(Pos.CENTER_RIGHT);
        card.getChildren().add(row);

        StackPane shade = new StackPane(card);
        shade.getStyleClass().add("shade");
        Handle h = new Handle(host, shade);

        for (Button b : actions) {
            var own = b.getOnAction();
            b.setOnAction(e -> {
                h.close();
                if (own != null) own.handle(e);
            });
        }
        shade.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE && actions.length > 0) actions[0].fire();
        });

        host.getChildren().add(shade);
        if (actions.length > 0) actions[actions.length - 1].requestFocus();

        FadeTransition fade = new FadeTransition(Duration.millis(150), shade);
        fade.setFromValue(0);
        fade.setToValue(1);
        TranslateTransition rise = new TranslateTransition(Duration.millis(200), card);
        rise.setFromY(8);
        rise.setToY(0);
        new ParallelTransition(fade, rise).play();
        return h;
    }

    public static Label text(String s) {
        Label l = new Label(s);
        l.setWrapText(true);
        l.getStyleClass().add("modal-text");
        return l;
    }
}
