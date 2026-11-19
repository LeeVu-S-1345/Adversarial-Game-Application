package netpro.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import netpro.client.app.GameClient;
import netpro.client.app.game.GameSession;
import netpro.client.ui.CloseGuard;
import netpro.client.ui.Fx;
import netpro.client.ui.SceneManager;
import netpro.client.ui.Theme;
import netpro.client.ui.controller.GameController;
import netpro.client.ui.controller.LoginController;

import java.util.List;

public class App extends Application {

    private GameClient client;

    @Override
    public void start(Stage stage) {
        Theme.loadFonts();
        client = new GameClient();
        chooseServer(getParameters().getRaw());
        SceneManager scenes = new SceneManager(stage, client);

        client.setPushListener(msg -> Fx.run(() -> scenes.onPush(msg)));
        client.setClosedListener(() -> Fx.run(() -> {
            GameSession g = client.getGame();
            boolean duringGame = scenes.current() instanceof GameController && g != null && !g.isOver();
            client.setGame(null);
            client.getInvites().clear();
            Object c = scenes.show("login-view.fxml");
            if (c instanceof LoginController login) login.showConnectionLost(duringGame);
        }));

        // Closing the window during a game asks first (docs/ui-spec.md, 6.3)
        stage.setOnCloseRequest(e -> {
            if (scenes.current() instanceof CloseGuard guard && !guard.confirmClose(Platform::exit)) e.consume();
        });

        stage.setTitle("Adversarial Game");
        stage.setResizable(false);
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
