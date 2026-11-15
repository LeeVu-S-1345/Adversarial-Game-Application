package netpro.client.ui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.StringConverter;
import netpro.client.app.GameClient;
import netpro.client.app.GameClient.Response;
import netpro.client.protocol.Code;
import netpro.client.protocol.Wire;
import netpro.client.protocol.Wire.Player;
import netpro.client.ui.Fx;
import netpro.client.ui.PushAware;
import netpro.client.ui.SceneManager;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntPredicate;

/** Scenario 2 (PLST, RANK) và Scenario 3 (CROM, INVT, IACC, IDEC, DROM, RAND, RCAN). */
public class LobbyController implements PushAware {
    private enum Mode { LOBBY, MATCHING, IN_ROOM }

    // Một lời mời được xác định bởi roomId: INVI|<roomId>|<creator>|<game>
    private record Invite(String roomId, String host, String game) {
        @Override public String toString() { return host + " mời bạn chơi " + Wire.gameLabel(game); }
    }

    @FXML private Label whoLabel, statusLabel, rankTitle;
    @FXML private ListView<Player> playerList;
    @FXML private ListView<String> rankList;
    @FXML private ListView<Invite> inviteList;
    @FXML private ComboBox<String> gameBox;
    @FXML private Button randButton, cancelRandButton, createRoomButton, inviteButton,
            cancelRoomButton, acceptButton, declineButton, logoutButton;

    private final GameClient client;
    private final SceneManager scenes;
    private Mode mode = Mode.LOBBY;   // sau mỗi ván server đưa về LOBBY, controller mới nên bắt đầu LOBBY
    private String roomId = "";       // phòng mình làm chủ (CROM|0|<roomId>)

    public LobbyController(GameClient client, SceneManager scenes) {
        this.client = client;
        this.scenes = scenes;
    }

    @FXML
    private void initialize() {
        whoLabel.setText("Xin chào, " + client.getUsername());
        gameBox.getItems().addAll(Wire.GAMES);   // gửi đúng "caro" / "reversi", chỉ đổi cách hiển thị
        gameBox.setConverter(new StringConverter<>() {
            @Override public String toString(String game) { return game == null ? "" : Wire.gameLabel(game); }
            @Override public String fromString(String label) { return label; }
        });
        gameBox.getSelectionModel().selectFirst();
        gameBox.valueProperty().addListener((obs, old, now) -> refreshRanking());
        updateButtons();
        refreshLists();
    }

    // ---- gửi request, xử lý kết quả trên FX thread ----

    /** onCode: trả true nếu đã tự xử lý mã lỗi này (không hiện thông báo chung). */
    private void send(String cmd, Consumer<Response> onOk, IntPredicate onCode, String... fields) {
        client.request(cmd, fields).whenComplete((r, err) -> Fx.run(() -> {
            if (err != null) {
                status("Lỗi kết nối tới server");
                return;
            }
            if (r.code() != 0) {
                if (!onCode.test(r.code())) status(Code.message(r.code()));
                return;
            }
            onOk.accept(r);
        }));
    }

    private void send(String cmd, Consumer<Response> onOk, String... fields) {
        send(cmd, onOk, code -> false, fields);
    }

    private void status(String s) { statusLabel.setText(s); }

    private static String first(Response r) { return r.extra().isEmpty() ? "" : r.extra().get(0); }

    // ---- danh sách ----

    private void refreshLists() {
        refreshPlayers();
        refreshRanking();
    }

    // 4. View Online Players: PLST chỉ hợp lệ ở LOBBY và IN_ROOM
    private void refreshPlayers() {
        if (mode == Mode.MATCHING) return;
        send("PLST", r -> {
            List<Player> others = new ArrayList<>();
            for (Player p : Wire.parsePlayers(first(r))) {
                if (!p.name().equals(client.getUsername())) others.add(p);   // server trả cả chính mình
            }
            playerList.getItems().setAll(others);
        }, code -> code == 12);   // trạng thái vừa đổi (vd vừa vào ván): bỏ qua
    }

    // 5. View Player Rankings: RANK|<game> chỉ hợp lệ ở LOBBY
    private void refreshRanking() {
        String game = gameBox.getValue();
        if (mode != Mode.LOBBY || game == null) return;
        send("RANK", r -> {
            List<String> rows = new ArrayList<>();
            int i = 1;
            for (Wire.RankRow e : Wire.parseRanking(first(r))) {
                rows.add(i++ + ". " + e.name() + " (" + e.rating() + ")");
            }
            rankTitle.setText("Bảng xếp hạng " + Wire.gameLabel(game));
            rankList.getItems().setAll(rows);
        }, code -> code == 12, game);
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

    // 11. Match Randomly
    @FXML
    private void onRand() {
        send("RAND", r -> {
            inviteList.getItems().clear();   // server đã hủy các lời mời gửi tới mình
            status("Đang tìm đối thủ...");   // RAND|0 chỉ là đã nhận yêu cầu; STRT có thể đến ngay sau
            setMode(Mode.MATCHING);
        }, gameBox.getValue());
    }

    // 12. Cancel Matching. RCAN|12: đã được ghép cặp, STRT sẽ đưa vào ván; không báo lỗi.
    @FXML
    private void onCancelRand() {
        send("RCAN", r -> {
            status("Đã hủy ghép trận");
            setMode(Mode.LOBBY);
        }, code -> code == 12);
    }

    // 6. Create Room
    @FXML
    private void onCreateRoom() {
        send("CROM", r -> {
            roomId = first(r);
            inviteList.getItems().clear();
            status("Đã tạo phòng " + roomId + ". Chọn một người đang rảnh ở danh sách bên trái rồi bấm Mời");
            setMode(Mode.IN_ROOM);
        }, gameBox.getValue());
    }

    // 7. Invite Player
    @FXML
    private void onInvite() {
        Player p = playerList.getSelectionModel().getSelectedItem();
        if (p == null) {
            status("Chọn một người chơi trước");
            return;
        }
        send("INVT", r -> status("Đã gửi lời mời tới " + p.name() + ", đang chờ trả lời"), code -> {
            if (code != 5 && code != 6) return false;
            status(Code.message(code));
            refreshPlayers();   // danh sách đã cũ: lấy lại
            return true;
        }, p.name());
    }

    // 10. Leave Room. DROM|12: người được mời vừa nhận lời, STRT sẽ đưa vào ván; không báo lỗi.
    @FXML
    private void onCancelRoom() {
        send("DROM", r -> {
            roomId = "";
            status("Đã hủy phòng");
            setMode(Mode.LOBBY);
        }, code -> code == 12);
    }

    @FXML private void onAccept() { answer("IACC"); }    // 8. Join Room / Accept Invitation
    @FXML private void onDecline() { answer("IDEC"); }   // 9. Decline Invitation

    private void answer(String cmd) {
        Invite inv = inviteList.getSelectionModel().getSelectedItem();
        if (inv == null) {
            status("Chọn một lời mời trước");
            return;
        }
        inviteList.getItems().remove(inv);   // 0 hay 7 thì lời mời này cũng không dùng lại được
        send(cmd, r -> {
            if (cmd.equals("IACC")) {
                inviteList.getItems().clear();   // các lời mời khác đã bị server hủy
                status("Đang vào trận...");
            } else {
                status("Đã từ chối");
            }
        }, code -> {
            if (code != 7) return false;
            status("Lời mời không còn hiệu lực");
            return true;
        }, inv.roomId());
    }

    // 3. Log out
    @FXML
    private void onLogout() {
        send("LOGU", r -> {
            client.setUsername("");
            scenes.show("login-view.fxml");
        });
    }

    // ---- push từ server ----

    @Override
    public void onPush(List<String> m) {
        switch (m.get(0)) {
            case "INVI" -> {   // INVI|<roomId>|<creator>|<game>
                Invite inv = new Invite(m.get(1), m.get(2), m.get(3));
                if (!inviteList.getItems().contains(inv)) inviteList.getItems().add(inv);
                status("Có lời mời mới từ " + inv.host());
            }
            case "IDCL" -> {   // IDCL|<user>: lời mời tới <user> đã hết
                status(m.get(1) + " đã từ chối hoặc không còn rảnh");
                refreshPlayers();
            }
            default -> { }
        }
    }
}
