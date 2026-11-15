package netpro.client;

import javafx.application.Application;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import netpro.client.app.GameClient;
import java.util.List;
import netpro.client.ui.Fx;
import netpro.client.ui.SceneManager;

public class App extends Application {

    private GameClient client;

    @Override
    public void start(Stage stage) {
        client = new GameClient();
        chooseServer(getParameters().getRaw());
        SceneManager scenes = new SceneManager(stage, client);

        client.setPushListener(msg -> Fx.run(() -> scenes.onPush(msg)));
        client.setClosedListener(() -> Fx.run(() -> {
            scenes.show("login-view.fxml");
            new Alert(Alert.AlertType.WARNING, "Mất kết nối tới server").show();
        }));

        stage.setTitle("Adversarial Game Application");
        scenes.show("login-view.fxml");
        stage.show();
    }

    // Tham số dòng lệnh "host[:port]" > -Dserver.host/-Dserver.port > địa chỉ dùng lần trước > mặc định.
    private void chooseServer(List<String> args) {
        try {
            if (!args.isEmpty()) {
                client.setServer(Config.parseAddress(args.get(0)));
            } else if (!Config.propertyGiven() && Config.savedAddress() != null) {
                client.setServer(Config.savedAddress());
            }
        } catch (IllegalArgumentException e) {
            System.err.println("Bad server address, using " + client.getServer());
        }
    }

    @Override
    public void stop() {
        if (client != null) client.disconnect();
    }

    public static void main(String[] args) {
        launch(args);
    }
}