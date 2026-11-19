package netpro.client.ui.controller;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import netpro.client.Config;
import netpro.client.app.GameClient;
import netpro.client.protocol.Code;
import netpro.client.ui.Dialogs;
import netpro.client.ui.Fx;
import netpro.client.ui.LoginArt;
import netpro.client.ui.SceneManager;
import netpro.client.ui.Theme;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.prefs.Preferences;

/** Scenario 1: Register (1), Log in (2), Reset Password (16). docs/ui-spec.md, section 3. */
public class LoginController {
    @FXML private StackPane root, artHolder;
    @FXML private VBox loginForm, resetForm;
    @FXML private TextField userField, passText, serverField, rsUser, rsCode;
    @FXML private PasswordField passField, rsNew, rsConfirm;
    @FXML private CheckBox rememberBox;
    @FXML private Button loginButton, registerButton, resetButton;
    @FXML private Label statusLabel, rsStatus, serverLabel;
    @FXML private Circle serverDot;

    private static final String PREF_USER = "lastUser";
    private final Preferences prefs = Preferences.userNodeForPackage(LoginController.class);

    private final GameClient client;
    private final SceneManager scenes;

    // Phải public: SceneManager tạo controller bằng reflection
    public LoginController(GameClient client, SceneManager scenes) {
        this.client = client;
        this.scenes = scenes;
    }

    @FXML
    private void initialize() {
        artHolder.getChildren().add(new LoginArt());
        passText.textProperty().bindBidirectional(passField.textProperty());

        String last = prefs.get(PREF_USER, "");
        rememberBox.setSelected(!last.isEmpty());
        userField.setText(!client.getUsername().isEmpty() ? client.getUsername() : last);

        serverField.setText(client.getServer().toString());
        checkServer();
    }

    /** Called by App when the connection closed by itself (docs/ui-spec.md, section 7). */
    public void showConnectionLost(boolean duringGame) {
        setServer(false);
        String text = duringGame
                ? "The connection to the server was lost during the game. The game counts as a loss. Please log in again."
                : "The connection to the server was lost. Please log in again.";
        Dialogs.show(root, "Connection lost", true, new Node[]{Dialogs.text(text)}, primary("OK"));
    }

    // ---------- server status ----------

    private void checkServer() {
        serverLabel.setText("Server: Connecting…");
        serverDot.setFill(Color.web("#c99a2e"));
        CompletableFuture
                .runAsync(() -> {
                    try { client.connect(); }
                    catch (IOException e) { throw new CompletionException(e); }
                })
                .whenComplete((v, err) -> Fx.run(() -> setServer(err == null)));
    }

    private void setServer(boolean connected) {
        serverLabel.setText(connected ? "Server: Connected" : "Server: Disconnected");
        serverDot.setFill(connected ? Theme.FELT : Theme.RED);
    }

    /** Reads the "host" or "host:port" field. False if malformed. */
    private boolean applyServerField() {
        try {
            Config.Address a = Config.parseAddress(serverField.getText());
            client.setServer(a);   // a different address closes the old connection
            Config.saveAddress(a);
            serverField.setText(a.toString());
            return true;
        } catch (IllegalArgumentException e) {
            setServer(false);
            error(statusLabel, "Server address must be host or host:port");
            return false;
        }
    }

    @FXML
    private void onServerChanged() {
        statusLabel.setText("");
        if (applyServerField()) checkServer();
    }

    // ---------- log in / create an account ----------

    @FXML
    private void onToggleEye() {
        boolean show = !passText.isVisible();
        passText.setVisible(show);
        passText.setManaged(show);
        passField.setVisible(!show);
        passField.setManaged(!show);
        (show ? passText : passField).requestFocus();
        (show ? passText : passField).end();
    }

    @FXML private void onLogin() { submit("LOGI"); }
    @FXML private void onRegister() { submit("REGI"); }

    private static boolean validName(String u) { return u.matches("[A-Za-z0-9_]{1,32}"); }
    private static boolean validPass(String p) { return p.matches("[\\x21-\\x7E&&[^|]]{1,64}"); }

    private void submit(String cmd) {
        String user = userField.getText().trim();
        String pass = passField.getText();
        if (user.isEmpty() || pass.isEmpty()) {
            error(statusLabel, cmd.equals("REGI") ? "Enter a username and password, then click Create an account"
                    : "Enter username and password");
            return;
        }
        // "Validate input locally" (use case 1): same rules as code 11 on the server
        if (!validName(user)) {
            error(statusLabel, "Username: 1-32 letters, digits or _");
            return;
        }
        if (!validPass(pass)) {
            error(statusLabel, "Password: 1-64 characters, no spaces, no |");
            return;
        }
        if (!applyServerField()) return;

        setBusy(true);
        statusLabel.setText("");
        connectThen(cmd, user, pass).whenComplete((r, err) -> Fx.run(() -> {
            setBusy(false);
            if (err != null) {
                setServer(false);
                error(statusLabel, "Cannot connect to the server");
                return;
            }
            setServer(true);
            if (r.code() != 0) {
                error(statusLabel, Code.message(r.code()));
                return;
            }
            if (cmd.equals("REGI")) {
                passField.clear();
                // REGI|0|<recoveryCode>; a server without use case 16 replies REGI|0
                if (!r.extra().isEmpty() && !r.extra().get(0).isEmpty()) showRecoveryCode(r.extra().get(0), true);
                else ok(statusLabel, "Account created. Log in with your new password.");
            } else {
                if (rememberBox.isSelected()) prefs.put(PREF_USER, user);
                else prefs.remove(PREF_USER);
                client.setUsername(user);
                scenes.show("lobby-view.fxml");
            }
        }));
    }

    private CompletableFuture<GameClient.Response> connectThen(String cmd, String... fields) {
        return CompletableFuture
                .runAsync(() -> {   // connect() may block: never on the FX thread
                    try { client.connect(); }
                    catch (IOException e) { throw new CompletionException(e); }
                })
                .thenCompose(v -> client.request(cmd, fields));
    }

    private void setBusy(boolean busy) {
        loginButton.setDisable(busy);
        registerButton.setDisable(busy);
        resetButton.setDisable(busy);
    }

    // ---------- reset password (use case 16) ----------

    @FXML
    private void onForgot() {
        rsUser.setText(userField.getText().trim());
        rsCode.clear();
        rsNew.clear();
        rsConfirm.clear();
        rsStatus.setText("");
        showReset(true);
        (rsUser.getText().isEmpty() ? rsUser : rsCode).requestFocus();
    }

    @FXML
    private void onBackToLogin() {
        statusLabel.setText("");
        showReset(false);
    }

    private void showReset(boolean reset) {
        resetForm.setVisible(reset);
        resetForm.setManaged(reset);
        loginForm.setVisible(!reset);
        loginForm.setManaged(!reset);
    }

    @FXML
    private void onReset() {
        String user = rsUser.getText().trim();
        String code = rsCode.getText().replaceAll("\\s+", "").toUpperCase();
        String pass = rsNew.getText();
        if (user.isEmpty() || code.isEmpty() || pass.isEmpty()) {
            error(rsStatus, "Fill in all fields");
            return;
        }
        if (!pass.equals(rsConfirm.getText())) {
            error(rsStatus, "Passwords do not match");
            return;
        }
        if (!validName(user) || !validPass(pass) || !code.matches("[A-Z0-9]{8}")) {
            error(rsStatus, "Invalid username, code or password format");
            return;
        }
        if (!applyServerField()) return;

        setBusy(true);
        rsStatus.setText("");
        connectThen("RSPW", user, code, pass).whenComplete((r, err) -> Fx.run(() -> {
            setBusy(false);
            if (err != null) {
                setServer(false);
                error(rsStatus, "Cannot connect to the server");
                return;
            }
            setServer(true);
            if (r.code() != 0) {
                error(rsStatus, Code.message(r.code()));
                return;
            }
            rsNew.clear();
            rsConfirm.clear();
            userField.setText(user);
            passField.clear();
            showRecoveryCode(r.extra().isEmpty() ? "" : r.extra().get(0), false);
        }));
    }

    /** Recovery code dialog after REGI|0|<code> (registered = true) or RSPW|0|<code>. */
    private void showRecoveryCode(String code, boolean registered) {
        Label codeBox = new Label(code);
        codeBox.getStyleClass().add("code");
        codeBox.setMaxWidth(Double.MAX_VALUE);
        Label copied = new Label("");
        copied.getStyleClass().addAll("msg", "ok");

        Button copy = new Button("Copy code");
        copy.getStyleClass().add("btn");
        Button done = primary(registered ? "I have saved it" : "Back to log in");
        done.setOnAction(e -> {
            showReset(false);
            ok(statusLabel, registered ? "Account created. Log in with your new password."
                    : "Password changed. Log in with the new password.");
            passField.requestFocus();
        });

        Node[] body = registered
                ? new Node[]{Dialogs.text("Save this recovery code somewhere safe. It is the only way to reset your password, and it is shown only once."), codeBox, copied}
                : new Node[]{Dialogs.text("Log in with your new password. The old recovery code no longer works; here is your new code:"), codeBox, copied};
        Dialogs.show(root, registered ? "Account created" : "Password changed", false, body, copy, done);

        // Copy must not close the dialog: replace the closing handler that Dialogs installed
        copy.setOnAction(e -> {
            ClipboardContent cc = new ClipboardContent();
            cc.putString(code);
            Clipboard.getSystemClipboard().setContent(cc);
            copied.setText("Copied to clipboard");
        });
    }

    // ---------- helpers ----------

    private static Button primary(String text) {
        Button b = new Button(text);
        b.getStyleClass().addAll("btn", "primary");
        return b;
    }

    private static void error(Label l, String s) {
        l.getStyleClass().setAll("label", "msg", "err");
        l.setText(s);
    }

    private static void ok(Label l, String s) {
        l.getStyleClass().setAll("label", "msg", "ok");
        l.setText(s);
    }
}
