package netpro.client.ui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import netpro.client.app.GameClient;
import netpro.client.app.GameClient.Response;
import netpro.client.protocol.Code;
import netpro.client.ui.Fx;
import netpro.client.ui.PushAware;
import netpro.client.ui.SceneManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class LobbyController implements PushAware {
    private enum Mode { LOBBY, MATCHING, IN_ROOM }

    private record Invite(String host, String game) {
        @Override public String toString() { return host + " mời bạn chơi " + game; }
    }

    @FXML private Label whoLabel, statusLabel;
    @FXML private ListView<String> playerList, rankList;
    @FXML private ListView<Invite> inviteList;
    @FXML private ComboBox<String> gameBox;
    @FXML private Button randButton, cancelRandButton, createRoomButton, inviteButton,
            cancelRoomButton, acceptButton, declineButton, logoutButton;

    private final GameClient client;
    private final SceneManager scenes;
    private Mode mode = Mode.LOBBY;   // sau mỗi ván server đưa về LOBBY, controller mới nên bắt đầu LOBBY

    public LobbyController(GameClient client, SceneManager scenes) {
        this.client = client;
        this.scenes = scenes;
    }

    @FXML
    private void initialize() {
        whoLabel.setText("Xin chào, " + client.getUsername());
        gameBox.getItems().addAll("GOMOKU", "REVERSI");   // phải khớp tên trong GameFactory của server
        gameBox.getSelectionModel().selectFirst();
        updateButtons();
        refreshLists();
    }

    // ---- gửi request, xử lý kết quả trên FX thread ----

    private void send(String cmd, Consumer<Response> onOk, String... fields) {
        client.request(cmd, fields).whenComplete((r, err) -> Fx.run(() -> {
            if (err != null) {
                status("Lỗi kết nối tới server");
                return;
            }
            if (r.code() != 0) {
                status(Code.message(r.code()));
                return;
            }
            onOk.accept(r);
        }));
    }

    private void status(String s) { statusLabel.setText(s); }

    private static String first(Response r) { return r.extra().isEmpty() ? "" : r.extra().get(0); }

    private static List<String> splitList(String s) {
        return s.isEmpty() ? List.of() : Arrays.asList(s.split(","));
    }

    // ---- danh sách ----

    private void refreshLists() {
        if (mode != Mode.MATCHING) {   // PLST chỉ hợp lệ ở LOBBY và IN_ROOM
            send("PLST", r -> playerList.getItems().setAll(splitList(first(r))));
        }
        if (mode == Mode.LOBBY) {      // RANK chỉ hợp lệ ở LOBBY
            send("RANK", r -> {
                List<String> rows = new ArrayList<>();
                int i = 1;
                for (String e : splitList(first(r))) {
                    String[] p = e.split(":");
                    rows.add(i++ + ". " + p[0] + " (" + p[1] + ")");
                }
                rankList.getItems().setAll(rows);
            }, "10");
        }
    }

    private void updateButtons() {
        boolean lobby = mode == Mode.LOBBY;
        boolean matching = mode == Mode.MATCHING;
        boolean room = mode == Mode.IN_ROOM;
        gameBox.setDisable(!lobby);
        randButton.setDisable(!lobby);
        cancelRandButton.setDisable(!matching);
        createRoomButton.setDisable(!lobby);
        inviteButton.setDisable(!room);
        cancelRoomButton.setDisable(!room);
        acceptButton.setDisable(!lobby);
        declineButton.setDisable(!lobby);
        logoutButton.setDisable(!lobby);
    }

    private void setMode(Mode m) {
        mode = m;
        updateButtons();
        refreshLists();
    }

    // ---- nút bấm ----

    @FXML private void onRefresh() { refreshLists(); }

    @FXML
    private void onRand() {
        send("RAND", r -> {
            inviteList.getItems().clear();   // server đã hủy các lời mời gửi tới mình
            status("Đang tìm đối thủ...");   // nếu có người chờ sẵn, STRT sẽ đến ngay sau
            setMode(Mode.MATCHING);
        }, gameBox.getValue());
    }

    @FXML
    private void onCancelRand() {
        send("RCAN", r -> {
            status("Đã hủy ghép trận");
            setMode(Mode.LOBBY);
        });
    }

    @FXML
    private void onCreateRoom() {
        send("CROM", r -> {
            inviteList.getItems().clear();
            status("Đã tạo phòng. Chọn một người ở danh sách bên trái rồi bấm Mời");
            setMode(Mode.IN_ROOM);
        }, gameBox.getValue());
    }

    @FXML
    private void onInvite() {
        String name = playerList.getSelectionModel().getSelectedItem();
        if (name == null) {
            status("Chọn một người chơi trước");
            return;
        }
        send("INVT", r -> status("Đã gửi lời mời tới " + name), name);
    }

    @FXML
    private void onCancelRoom() {
        send("DCAN", r -> {
            status("Đã hủy phòng");
            setMode(Mode.LOBBY);
        });
    }

    @FXML private void onAccept() { answer("IACC"); }
    @FXML private void onDecline() { answer("IDEC"); }

    private void answer(String cmd) {
        Invite inv = inviteList.getSelectionModel().getSelectedItem();
        if (inv == null) {
            status("Chọn một lời mời trước");
            return;
        }
        inviteList.getItems().remove(inv);   // dù thành công hay lỗi, lời mời này không dùng lại được
        send(cmd, r -> status(cmd.equals("IACC") ? "Đang vào trận..." : "Đã từ chối"), inv.host());
    }

    @FXML
    private void onLogout() {
        send("LOGU", r -> scenes.show("login-view.fxml"));
    }

    // ---- push từ server ----

    @Override
    public void onPush(List<String> m) {
        switch (m.get(0)) {
            case "INVI" -> {   // INVI|tenHost|gameType
                Invite inv = new Invite(m.get(1), m.get(2));
                if (!inviteList.getItems().contains(inv)) inviteList.getItems().add(inv);
                status("Có lời mời mới từ " + m.get(1));
            }
            case "IDCL" -> status(m.get(1) + " đã từ chối hoặc rời sảnh");   // IDCL|tenNguoiTuChoi
            default -> { }
        }
    }
}