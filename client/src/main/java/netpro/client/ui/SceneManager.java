package netpro.client.ui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import netpro.client.app.GameClient;
import netpro.client.app.game.GameSession;

import java.io.IOException;
import java.util.List;

public class SceneManager {
    private final Stage stage;
    private final GameClient client;
    private PushAware current;   // controller của màn đang hiện

    public SceneManager(Stage stage, GameClient client) {
        this.stage = stage;
        this.client = client;
    }

    public void show(String fxmlName) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    SceneManager.class.getResource("/netpro/client/ui/" + fxmlName));
            loader.setControllerFactory(type -> {
                try {
                    return type.getConstructor(GameClient.class, SceneManager.class)
                            .newInstance(client, this);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            });
            Parent root = loader.load();
            Object controller = loader.getController();
            current = controller instanceof PushAware p ? p : null;

            if (stage.getScene() == null) stage.setScene(new Scene(root));
            else stage.getScene().setRoot(root);
            stage.sizeToScene();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load " + fxmlName, e);
        }
    }

    /** Luôn chạy trên FX thread (App bọc bằng Fx.run). */
    public void onPush(List<String> msg) {
        if (msg.get(0).equals("STRT")) {
            // STRT|<game>|<side>|<opponent>|<rows>,<cols>
            int[] size = GameSession.parsePos(msg.get(4));
            client.setGame(new GameSession(msg.get(1), Integer.parseInt(msg.get(2)), msg.get(3),
                    size[0], size[1]));
            show("game-view.fxml");
        } else if (current != null) {
            current.onPush(msg);
        }
    }
}