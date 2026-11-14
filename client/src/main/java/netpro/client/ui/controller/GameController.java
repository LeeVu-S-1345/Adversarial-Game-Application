package netpro.client.ui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import netpro.client.app.GameClient;
import netpro.client.app.game.GameSession;
import netpro.client.protocol.Code;
import netpro.client.ui.Fx;
import netpro.client.ui.PushAware;
import netpro.client.ui.SceneManager;
import netpro.client.ui.board.BoardView;

import java.util.List;

public class GameController implements PushAware {
    @FXML private StackPane boardHolder;
    @FXML private Label infoLabel, turnLabel, resultLabel;
    @FXML private Button resignButton, backButton;

    private final GameClient client;
    private final SceneManager scenes;
    private GameSession g;
    private BoardView board;
    private boolean busy;   // đang chờ reply MOVE, bỏ qua click thêm

    public GameController(GameClient client, SceneManager scenes) {
        this.client = client;
        this.scenes = scenes;
    }

    @FXML
    private void initialize() {
        g = client.getGame();   // SceneManager đã tạo trước khi load màn này
        board = new BoardView(g);
        board.setOnCell(this::onCell);
        boardHolder.getChildren().add(board);

        infoLabel.setText(g.gameType() + " | Bạn là người " + g.myNo()
                + (g.myNo() == 1 ? " (đi trước)" : "") + " | Đối thủ: " + g.opponent());
        backButton.setDisable(true);
        refresh();
    }

    private void refresh() {
        board.draw();
        if (!g.isOver()) {
            turnLabel.setText(g.isMyTurn() ? "Đến lượt bạn" : "Đến lượt " + g.opponent());
        }
    }

    // ---- người chơi bấm vào ô ----

    private void onCell(int r, int c) {
        if (g.isOver() || busy || !g.isMyTurn()) return;
        busy = true;
        client.request("MOVE", r + "," + c).whenComplete((res, err) -> Fx.run(() -> {
            busy = false;
            if (err != null) {
                resultLabel.setText("Lỗi kết nối tới server");
                return;
            }
            if (res.code() != 0) {
                resultLabel.setText(Code.message(res.code()));
                return;
            }
            // MOVE|0|flips: chỉ cập nhật bàn cờ SAU khi server xác nhận
            String flips = res.extra().isEmpty() ? "" : res.extra().get(0);
            g.apply(g.myNo(), r, c, GameSession.parseFlips(flips));
            resultLabel.setText("");
            refresh();
        }));
    }

    @FXML
    private void onResign() {
        resignButton.setDisable(true);
        client.request("RSGN").whenComplete((res, err) -> Fx.run(() -> {
            if (err != null || res.code() != 0) resignButton.setDisable(g.isOver());
            // thành công: OVER sẽ đến qua onPush
        }));
    }

    @FXML
    private void onBack() {
        scenes.show("lobby-view.fxml");
    }

    // ---- push từ server ----

    @Override
    public void onPush(List<String> m) {
        switch (m.get(0)) {
            case "OPPM" -> {   // OPPM|r,c|flips
                int[] p = GameSession.parsePos(m.get(1));
                g.apply(3 - g.myNo(), p[0], p[1], GameSession.parseFlips(m.get(2)));
                refresh();
            }
            case "SKIP" -> {   // người vừa đi sẽ đi tiếp
                g.onSkip();
                resultLabel.setText(g.lastMover() == g.myNo()
                        ? "Đối thủ hết nước đi, bạn đi tiếp"
                        : "Bạn hết nước đi, đối thủ đi tiếp");
                refresh();
            }
            case "OVER" -> {   // OVER|WIN/LOSE/DRAW|eloMoi
                g.end(m.get(1), Integer.parseInt(m.get(2)));
                String text = switch (g.result()) {
                    case "WIN" -> "Bạn thắng!";
                    case "LOSE" -> "Bạn thua.";
                    default -> "Hòa.";
                };
                turnLabel.setText("Ván đấu kết thúc");
                resultLabel.setText(text + " Elo mới: " + g.newElo());
                resignButton.setDisable(true);
                backButton.setDisable(false);
                refresh();
            }
            default -> { }
        }
    }
}