package netpro.client.ui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import netpro.client.app.GameClient;
import netpro.client.app.game.GameSession;
import netpro.client.protocol.Code;
import netpro.client.protocol.Wire;
import netpro.client.ui.Fx;
import netpro.client.ui.PushAware;
import netpro.client.ui.SceneManager;
import netpro.client.ui.board.BoardView;

import java.util.List;

/** Scenario 4: Make a Move (13), Resign (14), End of Game (15). */
public class GameController implements PushAware {
    @FXML private StackPane boardHolder;
    @FXML private Label infoLabel, turnLabel, resultLabel;
    @FXML private Button resignButton, backButton;

    private final GameClient client;
    private final SceneManager scenes;
    private GameSession g;
    private BoardView board;
    private boolean busy;   // đang chờ reply MOVE: chặn click thêm ("Block further input")

    public GameController(GameClient client, SceneManager scenes) {
        this.client = client;
        this.scenes = scenes;
    }

    @FXML
    private void initialize() {
        g = client.getGame();   // SceneManager đã tạo từ STRT trước khi load màn này
        board = new BoardView(g);
        board.setOnCell(this::onCell);
        boardHolder.getChildren().add(board);

        infoLabel.setText(Wire.gameLabel(g.gameType()) + " | Bạn là bên " + g.myNo()
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
            if (res.code() == 12) return;   // ván vừa kết thúc: không phải lỗi, OVER sẽ lo
            if (res.code() != 0) {          // 13..16: báo lý do, vẫn giữ lượt để đi lại
                resultLabel.setText(Code.message(res.code()));
                return;
            }
            // MOVE|0|<k>|<flips>: chỉ cập nhật bàn cờ SAU khi server xác nhận
            String flips = res.extra().size() > 1 ? res.extra().get(1) : "";
            g.apply(g.myNo(), r, c, GameSession.parseFlips(flips));
            resultLabel.setText("");
            refresh();
        }));
    }

    @FXML
    private void onResign() {
        resignButton.setDisable(true);
        client.request("RSGN").whenComplete((res, err) -> Fx.run(() -> {
            // 0: chờ OVER. 12: ván vừa kết thúc, OVER đang tới hoặc đã tới; không phải lỗi.
            if (err != null || (res.code() != 0 && res.code() != 12)) {
                resignButton.setDisable(g.isOver());
                if (err == null) resultLabel.setText(Code.message(res.code()));
            }
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
            case "OPPM" -> {   // OPPM|<x>,<y>|<k>|<flips>
                int[] p = GameSession.parsePos(m.get(1));
                String flips = m.size() > 3 ? m.get(3) : "";
                g.apply(3 - g.myNo(), p[0], p[1], GameSession.parseFlips(flips));
                resultLabel.setText("");
                refresh();
            }
            case "SKIP" -> {   // SKIP|<side>: bên <side> mất lượt
                int skipped = Integer.parseInt(m.get(1));
                g.onSkip(skipped);
                resultLabel.setText(skipped == g.myNo()
                        ? "Bạn không có nước đi, mất lượt"
                        : "Đối thủ không có nước đi, bạn đi tiếp");
                refresh();
            }
            case "OVER" -> {   // OVER|<result>|<delta>|<newRating>
                g.end(m.get(1), m.get(2), Integer.parseInt(m.get(3)));
                String text = switch (g.result()) {
                    case "WIN" -> "Bạn thắng!";
                    case "LOSE" -> "Bạn thua.";
                    default -> "Hòa.";
                };
                turnLabel.setText("Ván đấu kết thúc");
                resultLabel.setText(text + " Elo: " + g.delta() + " → " + g.newElo());
                resignButton.setDisable(true);
                backButton.setDisable(false);
                refresh();
            }
            default -> { }
        }
    }
}
