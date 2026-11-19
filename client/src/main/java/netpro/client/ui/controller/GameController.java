package netpro.client.ui.controller;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;
import netpro.client.app.GameClient;
import netpro.client.app.game.GameSession;
import netpro.client.protocol.Code;
import netpro.client.protocol.Wire;
import netpro.client.ui.*;
import netpro.client.ui.board.BoardView;

import java.util.List;

/** Scenario 4: Make a Move (13), Resign (14), End of Game (15). docs/ui-spec.md, section 6. */
public class GameController implements PushAware, CloseGuard {
    @FXML private StackPane root, logoHolder, boardArea;
    @FXML private Label gameTitle, roomLabel, serverLabel, statusLabel;
    @FXML private HBox oppCard, meCard, movesHeader;
    @FXML private VBox resultBox, historyRows;
    @FXML private Button resignButton;

    private final GameClient client;
    private final SceneManager scenes;
    private GameSession g;
    private BoardView board;
    private boolean busy;   // waiting for the MOVE reply: "Block further input"
    private Dialogs.Handle resignDialog;
    private Label toast;
    private SequentialTransition toastAnim;

    public GameController(GameClient client, SceneManager scenes) {
        this.client = client;
        this.scenes = scenes;
    }

    @FXML
    private void initialize() {
        g = client.getGame();   // SceneManager created it from STRT before loading this screen
        board = new BoardView(g);
        board.setOnCell(this::onCell);
        boardArea.getChildren().add(board);

        logoHolder.getChildren().add(Widgets.logo());
        gameTitle.setText(Wire.gameLabel(g.gameType()));
        roomLabel.setText(client.getRoomLabel());
        serverLabel.setText(client.getServer().toString());

        String piece = g.isCaro() ? (g.myNo() == 1 ? "X" : "O") : (g.myNo() == 1 ? "Black" : "White");
        status("info", "Game started. You play " + piece
                + (g.myNo() == 1 ? " and move first" : "; " + g.opponent() + " moves first"));
        render();
    }

    private void status(String kind, String text) {
        statusLabel.getStyleClass().setAll("label", "msg", kind);
        statusLabel.setText(text);
    }

    // ---------- the player clicks a cell ----------

    private void onCell(int r, int c) {
        if (g.isOver() || busy || !g.isMyTurn() || (resignDialog != null && resignDialog.isOpen())) return;
        busy = true;
        client.request("MOVE", r + "," + c).whenComplete((res, err) -> Fx.run(() -> {
            busy = false;
            if (err != null) {
                status("err", "Lost contact with the server");
                return;
            }
            if (res.code() == 12) return;   // the game has just ended: not an error, OVER follows
            if (res.code() != 0) {          // 13..16: show the reason and keep the turn
                status("err", Code.message(res.code()));
                return;
            }
            // MOVE|0|<k>|<flips>: update the board only AFTER the server accepted the move
            List<int[]> flips = GameSession.parseFlips(res.extra().size() > 1 ? res.extra().get(1) : "");
            g.apply(g.myNo(), r, c, flips);
            board.animateMove(r, c, flips);
            status("info", "You played " + r + "," + c);
            render();
        }));
    }

    @FXML
    private void onResign() {
        if (g.isOver()) return;
        Button keep = Widgets.button("Keep playing");
        Button resign = Widgets.button("Resign", "danger");
        resign.setOnAction(e -> sendResign());
        resignDialog = Dialogs.show(root, "Resign this game?", false,
                new Node[]{Dialogs.text(g.opponent() + " wins and your " + Wire.gameLabel(g.gameType()) + " rating goes down.")},
                keep, resign);
    }

    private void sendResign() {
        if (g.isOver()) return;
        g.markResigned();
        resignButton.setDisable(true);
        client.request("RSGN").whenComplete((res, err) -> Fx.run(() -> {
            // 0: OVER follows. 12: the game has just ended, OVER is coming or came; not an error.
            if (err != null || (res.code() != 0 && res.code() != 12)) {
                resignButton.setDisable(g.isOver());
                if (err == null) status("err", Code.message(res.code()));
            }
        }));
    }

    /** Closing the window during a game counts as a loss (protocol.md, section 12). */
    @Override
    public boolean confirmClose(Runnable quit) {
        if (g.isOver()) return true;
        Button keep = Widgets.button("Keep playing");
        Button q = Widgets.button("Quit", "danger");
        q.setOnAction(e -> quit.run());
        Dialogs.show(root, "Quit the game?", false,
                new Node[]{Dialogs.text("Quitting now counts as a loss, and " + g.opponent() + " wins.")}, keep, q);
        return false;
    }

    private void onBack() {
        client.setSelectedGame(g.gameType());
        client.setNotice("Back in the lobby. " + Wire.gameLabel(g.gameType()) + " rating is now " + g.newElo());
        scenes.show("lobby-view.fxml");
    }

    // ---------- pushes ----------

    @Override
    public void onPush(List<String> m) {
        switch (m.get(0)) {
            case "OPPM" -> {   // OPPM|<x>,<y>|<k>|<flips>
                int[] p = GameSession.parsePos(m.get(1));
                List<int[]> flips = GameSession.parseFlips(m.size() > 3 ? m.get(3) : "");
                g.apply(3 - g.myNo(), p[0], p[1], flips);
                board.animateMove(p[0], p[1], flips);
                status("info", g.opponent() + " played " + p[0] + "," + p[1]);
                render();
            }
            case "SKIP" -> {   // SKIP|<side>: <side> has no legal move
                int skipped = Integer.parseInt(m.get(1));
                g.onSkip(skipped);
                String text = skipped == g.myNo()
                        ? "You have no legal move — " + g.opponent() + " moves again"
                        : g.opponent() + " has no legal move — your turn again";
                showToast(text);
                status("info", text);
                render();
            }
            case "OVER" -> {   // OVER|<result>|<delta>|<newRating>
                g.end(m.get(1), m.get(2), Integer.parseInt(m.get(3)));
                if (resignDialog != null) resignDialog.close();
                if (g.isCaro() && g.lastRow() >= 0) board.setWinLine(g.lineThrough(g.lastRow(), g.lastCol()));
                String text = switch (g.result()) {
                    case "WIN" -> "You win! ";
                    case "LOSE" -> "You lose. ";
                    default -> "Draw. ";
                };
                status(g.result().equals("WIN") ? "ok" : g.result().equals("LOSE") ? "err" : "info",
                        text + Wire.gameLabel(g.gameType()) + " rating " + g.delta());
                render();
            }
            case "INVI" -> {   // after OVER the server already has me in LOBBY: keep it for the lobby
                Wire.Invite inv = new Wire.Invite(m.get(1), m.get(2), m.get(3));
                if (!client.getInvites().contains(inv)) client.getInvites().add(0, inv);
                status("info", inv.host() + " invited you to play " + Wire.gameLabel(inv.game()));
            }
            default -> { }
        }
    }

    private void showToast(String text) {
        if (toast != null) boardArea.getChildren().remove(toast);
        if (toastAnim != null) toastAnim.stop();
        toast = new Label(text);
        toast.getStyleClass().add("toast");
        toast.setMouseTransparent(true);
        boardArea.getChildren().add(toast);
        FadeTransition in = new FadeTransition(Duration.millis(220), toast);
        in.setFromValue(0);
        in.setToValue(1);
        FadeTransition out = new FadeTransition(Duration.millis(300), toast);
        out.setToValue(0);
        Label mine = toast;
        toastAnim = new SequentialTransition(in, new PauseTransition(Duration.millis(2000)), out);
        toastAnim.setOnFinished(e -> boardArea.getChildren().remove(mine));
        toastAnim.play();
    }

    // ---------- rendering ----------

    private void render() {
        board.refresh();
        boolean myTurn = g.isMyTurn();
        boolean oppTurn = !g.isOver() && !myTurn;
        oppCard.getChildren().setAll(playerCard(g.opponent(), false, 3 - g.myNo(), oppTurn, "THINKING"));
        meCard.getChildren().setAll(playerCard(client.getUsername(), true, g.myNo(), myTurn, "YOUR TURN"));
        resignButton.setDisable(g.isOver());
        renderResult();
        renderHistory();
    }

    private int deltaValue() {
        try { return Integer.parseInt(g.delta().replace("+", "")); }
        catch (NumberFormatException e) { return 0; }
    }

    private Node playerCard(String name, boolean me, int side, boolean turn, String pill) {
        Integer elo = g.isOver() && me ? Integer.valueOf(g.newElo() - deltaValue()) : client.ratingOf(g.gameType(), name);
        Label nameL = Widgets.label(name, "pname");
        HBox nameRow = new HBox(4, nameL);
        if (me) nameRow.getChildren().add(Widgets.label("(you)", "dim"));
        Label eloL = Widgets.label((elo == null ? "—" : String.valueOf(elo)) + " · plays", "dim", "small");
        HBox sub = new HBox(5, eloL, Widgets.piece(g.isCaro(), side, 12));
        sub.setAlignment(Pos.CENTER_LEFT);
        VBox who = new VBox(2, nameRow, sub);

        HBox card = new HBox(12, Widgets.avatar(name, 36), who, Widgets.grow());
        card.getStyleClass().add("pcard");
        if (turn) card.getStyleClass().add("turn");
        if (g.isReversi()) {
            HBox count = new HBox(6, Widgets.piece(false, side, 16), Widgets.label(String.valueOf(g.count(side)), "pcount"));
            count.setAlignment(Pos.CENTER);
            card.getChildren().add(count);
        }
        card.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(card, Priority.ALWAYS);

        StackPane wrap = new StackPane(card);
        HBox.setHgrow(wrap, Priority.ALWAYS);
        if (turn) {
            Label p = Widgets.label(pill, "turnpill");
            StackPane.setAlignment(p, Pos.TOP_RIGHT);
            StackPane.setMargin(p, new Insets(-9, 10, 0, 0));
            wrap.getChildren().add(p);
        }
        return wrap;
    }

    private void renderResult() {
        boolean over = g.isOver();
        resultBox.setVisible(over);
        resultBox.setManaged(over);
        if (!over) return;
        String color = switch (g.result()) {
            case "WIN" -> "#2f6b4f";
            case "LOSE" -> "#a3261d";
            default -> "#1c1d1a";
        };
        Label title = Widgets.label(switch (g.result()) {
            case "WIN" -> "YOU WIN";
            case "LOSE" -> "YOU LOSE";
            default -> "DRAW";
        }, "result-title");
        title.setStyle("-fx-text-fill: " + color + ";");
        Label why = Widgets.label(reason(), "modal-text");
        why.setWrapText(true);

        int d = deltaValue();
        Text t1 = new Text(Wire.gameLabel(g.gameType()) + " rating ");
        Text t2 = new Text(String.valueOf(g.newElo() - d));
        t2.setStyle("-fx-font-weight: bold;");
        Text t3 = new Text(" (" + g.delta() + ") ");
        t3.setStyle("-fx-font-weight: bold; -fx-fill: " + (d > 0 ? "#2f6b4f" : d < 0 ? "#a3261d" : "#6b6c66") + ";");
        Text t4 = new Text("→ ");
        Text t5 = new Text(String.valueOf(g.newElo()));
        t5.setStyle("-fx-font-weight: bold;");
        for (Text t : new Text[]{t1, t4}) t.setFill(Theme.INK);
        TextFlow rating = new TextFlow(t1, t2, t3, t4, t5);
        rating.setStyle("-fx-font-size: 15px;");
        rating.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        Button back = Widgets.button("Back to lobby", "primary");
        back.setMaxWidth(Double.MAX_VALUE);
        back.setOnAction(e -> onBack());

        VBox card = new VBox(8, title, why, rating, back);
        card.getStyleClass().add("result");
        card.setStyle("-fx-border-color: " + color + ";");
        resultBox.getChildren().setAll(card);
        back.requestFocus();
    }

    /** Why the game ended, worked out from the board (OVER does not say). */
    private String reason() {
        boolean caroLine = g.isCaro() && !g.lineThrough(g.lastRow(), g.lastCol()).isEmpty();
        if (caroLine) return g.lastMover() == g.myNo() ? "Five in a row." : g.opponent() + " made five in a row.";
        if (g.isReversi() && g.legalMoves(1).isEmpty() && g.legalMoves(2).isEmpty())
            return "No moves left: " + g.count(g.myNo()) + " to " + g.count(3 - g.myNo()) + ".";
        if (g.result().equals("DRAW")) return "The board is full.";
        if (g.resignedByMe()) return "You resigned.";
        if (g.result().equals("WIN")) return g.opponent() + " resigned or left the game.";
        return "The game is over.";
    }

    private void renderHistory() {
        movesHeader.getChildren().setAll(Widgets.section("Moves", new Label(String.valueOf(g.moves()))));
        movesHeader.setPadding(new Insets(0, 6, 0, 6));
        historyRows.getChildren().clear();
        List<GameSession.Entry> list = g.historyNewestFirst();
        if (list.isEmpty()) {
            Label l = Widgets.label(g.myNo() == 1 ? "You move first." : g.opponent() + " moves first.", "faint");
            l.setPadding(new Insets(4, 8, 4, 8));
            historyRows.getChildren().add(l);
            return;
        }
        int i = 0;
        for (GameSession.Entry e : list) {
            Label no = Widgets.label(e.no() == 0 ? "" : String.valueOf(e.no()), "hist-no");
            Label by = Widgets.label(e.by());
            by.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(by, Priority.ALWAYS);
            Label text = Widgets.label(e.text(), "mono", "small");
            HBox row = new HBox(10, no, Widgets.piece(g.isCaro(), e.side(), 14), by, text);
            row.getStyleClass().add("hist");
            if (i++ % 2 == 0) row.getStyleClass().add("odd");
            historyRows.getChildren().add(row);
        }
    }
}
