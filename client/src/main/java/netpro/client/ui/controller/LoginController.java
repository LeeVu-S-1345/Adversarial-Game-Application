package netpro.client.ui.controller;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import netpro.client.Config;
import netpro.client.app.GameClient;
import netpro.client.protocol.Code;
import netpro.client.ui.Fx;
import netpro.client.ui.SceneManager;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.prefs.Preferences;

public class LoginController {
    @FXML private javafx.scene.layout.HBox userBox, passBox;
    @FXML private TextField userField, passText, serverField;
    @FXML private PasswordField passField;
    @FXML private CheckBox rememberBox;   // có thể null: ô này đang bị tắt trong login-view.fxml
    @FXML private Button loginButton, registerButton, eyeButton;
    @FXML private Label statusLabel, serverLabel;
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
        // Hai ô mật khẩu dùng chung nội dung
        passText.textProperty().bindBidirectional(passField.textProperty());

        // Viền sáng khi ô đang được focus
        highlightOnFocus(userField, userBox);
        highlightOnFocus(passField, passBox);
        highlightOnFocus(passText, passBox);

        // Điền sẵn username nếu lần trước đã tick "Ghi nhớ"
        String last = prefs.get(PREF_USER, "");
        if (!last.isEmpty()) {
            userField.setText(last);
            if (rememberBox != null) rememberBox.setSelected(true);
        }

        serverField.setText(client.getServer().toString());
        checkServer();
    }

    private void highlightOnFocus(Node input, Node box) {
        input.focusedProperty().addListener((obs, was, now) -> {
            if (now) {
                if (!box.getStyleClass().contains("focused")) box.getStyleClass().add("focused");
            } else if (!passField.isFocused() && !passText.isFocused() && !userField.isFocused()) {
                box.getStyleClass().remove("focused");
            } else {
                // chỉ gỡ viền của đúng ô vừa mất focus
                if (box == userBox && !userField.isFocused()) box.getStyleClass().remove("focused");
                if (box == passBox && !passField.isFocused() && !passText.isFocused())
                    box.getStyleClass().remove("focused");
            }
        });
    }

    // ---- Trạng thái server (góc dưới trái) ----

    private void checkServer() {
        setServer("Server: Connecting...", Color.web("#f5c542"));
        CompletableFuture
                .runAsync(() -> {
                    try { client.connect(); }
                    catch (IOException e) { throw new CompletionException(e); }
                })
                .whenComplete((v, err) -> Fx.run(() -> {
                    if (err == null) setServer("Server: Connected", Color.web("#22c55e"));
                    else setServer("Server: Disconnected", Color.web("#ef4444"));
                }));
    }

    // Đọc ô địa chỉ server ("host" hoặc "host:port"). False nếu sai dạng.
    private boolean applyServerField() {
        try {
            Config.Address a = Config.parseAddress(serverField.getText());
            client.setServer(a);   // đổi địa chỉ thì ngắt kết nối cũ
            Config.saveAddress(a);
            serverField.setText(a.toString());
            return true;
        } catch (IllegalArgumentException e) {
            setServer("Server: Bad address", Color.web("#ef4444"));
            error("Server address must be host or host:port");
            return false;
        }
    }

    @FXML
    private void onServerChanged() {
        statusLabel.setText("");
        if (applyServerField()) checkServer();
    }

    private void setServer(String text, Color dot) {
        serverLabel.setText(text);
        serverDot.setFill(dot);
    }

    // ---- Nút bấm ----

    @FXML
    private void onToggleEye() {
        boolean show = !passText.isVisible();
        passText.setVisible(show);
        passText.setManaged(show);
        passField.setVisible(!show);
        passField.setManaged(!show);
        (show ? passText : passField).requestFocus();
    }

    @FXML
    private void onForgot() {
        // Protocol hiện chưa có lệnh đổi mật khẩu
        error("This function is not supported currently");
    }

    @FXML private void onLogin()    { submit("LOGI"); }
    @FXML private void onRegister() { submit("REGI"); }

    private void submit(String cmd) {
        String user = userField.getText().trim();
        String pass = passField.getText();
        if (user.isEmpty() || pass.isEmpty()) {
            error("Enter username and password");
            return;
        }
        // "Validate input locally" (use case 1), cùng quy tắc với mã 11 của server:
        // username 1-32 chữ, số, '_'; password 1-64 ký tự ASCII in được, không dấu cách, không '|'.
        if (!user.matches("[A-Za-z0-9_]{1,32}")) {
            error("Username: 1-32 letters, digits or '_'");
            return;
        }
        if (!pass.matches("[\\x21-\\x7E&&[^|]]{1,64}")) {
            error("Password: 1-64 characters, no spaces, no '|'");
            return;
        }
        if (!applyServerField()) return;

        setBusy(true);
        statusLabel.setText("");

        CompletableFuture
                .runAsync(() -> {   // connect() có thể chặn, không chạy trên FX thread
                    try { client.connect(); }
                    catch (IOException e) { throw new CompletionException(e); }
                })
                .thenCompose(v -> client.request(cmd, user, pass))
                .whenComplete((r, err) -> Fx.run(() -> {
                    setBusy(false);
                    if (err != null) {
                        setServer("Server: Cannot connect", Color.web("#ef4444"));
                        error("Cannot connect to server");
                        return;
                    }
                    setServer("Server: Connected", Color.web("#22c55e"));
                    if (r.code() != 0) {
                        error(Code.message(r.code()));
                        return;
                    }
                    if (cmd.equals("REGI")) {
                        ok("Register successfully. Please login!");
                    } else {
                        if (rememberBox != null) {
                            if (rememberBox.isSelected()) prefs.put(PREF_USER, user);
                            else prefs.remove(PREF_USER);
                        }
                        client.setUsername(user);
                        scenes.show("lobby-view.fxml");
                    }
                }));
    }

    private void setBusy(boolean busy) {
        loginButton.setDisable(busy);
        registerButton.setDisable(busy);
    }

    private void error(String s) {
        statusLabel.getStyleClass().remove("ok");
        statusLabel.setText(s);
    }

    private void ok(String s) {
        if (!statusLabel.getStyleClass().contains("ok")) statusLabel.getStyleClass().add("ok");
        statusLabel.setText(s);
    }
}