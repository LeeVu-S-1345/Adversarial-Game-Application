package netpro.client.ui.controller;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;
import netpro.client.app.GameClient;
import netpro.client.app.GameClient.Response;
import netpro.client.protocol.Code;
import netpro.client.protocol.Wire;
import netpro.client.protocol.Wire.Invite;
import netpro.client.protocol.Wire.Player;
import netpro.client.protocol.Wire.RankRow;
import netpro.client.ui.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntPredicate;

/**
 * Lobby: Scenario 2 (PLST, RANK) and Scenario 3 (CROM, INVT, IACC, IDEC, DROM, RAND, RCAN).
 * docs/ui-spec.md, section 5.
 */
public class LobbyController implements PushAware, Disposable {
    private enum Mode { LOBBY, MATCHING, IN_ROOM }

    @FXML private StackPane root, logoHolder, meAvatar;
    @FXML private Label meName, rankTitle, serverLabel, statusLabel;
    @FXML private Button refreshButton, logoutButton;
    @FXML private VBox table, matchBox, roomBox, invitesBox, inviteRows, playersCard, playerRows, rankCard, rankRows;
    @FXML private HBox caroTile, reversiTile, invitesHeader, playersHeader;

    private final GameClient client;
    private final SceneManager scenes;
    private Mode mode = Mode.LOBBY;   // the server puts both players back in LOBBY after every game
    private String roomId = "", roomGame = Wire.CARO;
    private final Map<String, String> invited = new LinkedHashMap<>();   // name -> "wait" | "declined"
    private List<Player> players = new ArrayList<>();
    private String flashRoom = "";

    private Timeline matchClock, dots;
    private int matchSecs;
    private Label matchTime;
    private final Button caroButton = new Button(), reversiButton = new Button();

    public LobbyController(GameClient client, SceneManager scenes) {
        this.client = client;
        this.scenes = scenes;
    }

    @FXML
    private void initialize() {
        logoHolder.getChildren().add(Widgets.logo());
        meAvatar.getChildren().add(Widgets.avatar(client.getUsername(), 30));
        meName.setText(client.getUsername());
        serverLabel.setText(client.getServer().toString());
        table.setBackground(Theme.felt(Theme.FELT_DEEP, 12));
        Theme.roundClip(table, 12);

        caroTile.getChildren().add(caroButton);
        reversiTile.getChildren().add(reversiButton);
        for (Button b : new Button[]{caroButton, reversiButton}) {
            b.getStyleClass().setAll("gtile");
            b.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(b, Priority.ALWAYS);
        }
        caroButton.setOnAction(e -> pickGame(Wire.CARO));
        reversiButton.setOnAction(e -> pickGame(Wire.REVERSI));

        String notice = client.takeNotice();
        if (!notice.isEmpty()) status("info", notice);
        else if (!client.getInvites().isEmpty()) {
            Invite last = client.getInvites().get(client.getInvites().size() - 1);
            status("info", last.host() + " invited you to play " + Wire.gameLabel(last.game()));
        }
        renderAll();
        refreshLists();
    }

    @Override
    public void dispose() {
        if (matchClock != null) matchClock.stop();
        if (dots != null) dots.stop();
    }

    // ---------- requests ----------

    /** onCode returns true when it handled that error code itself. */
    private void send(String cmd, Consumer<Response> onOk, IntPredicate onCode, String... fields) {
        client.request(cmd, fields).whenComplete((r, err) -> Fx.run(() -> {
            if (err != null) {
                status("err", "Lost contact with the server");
                return;
            }
            if (r.code() != 0) {
                if (!onCode.test(r.code())) status("err", Code.message(r.code()));
                return;
            }
            onOk.accept(r);
        }));
    }

    private static String first(Response r) { return r.extra().isEmpty() ? "" : r.extra().get(0); }

    private void status(String kind, String text) {
        statusLabel.getStyleClass().setAll("label", "msg", kind);
        statusLabel.setText(text);
    }

    private String game() { return client.getSelectedGame(); }

    // 4. View Online Players: PLST is valid in LOBBY and IN_ROOM
    private void refreshPlayers() {
        if (mode == Mode.MATCHING) return;
        send("PLST", r -> {
            List<Player> others = new ArrayList<>();
            for (Player p : Wire.parsePlayers(first(r))) if (!p.name().equals(client.getUsername())) others.add(p);
            players = others;
            renderPlayers();
        }, code -> code == 12, new String[0]);
    }

    // 5. View Player Rankings: RANK|<game> is valid in LOBBY only. Both games: the tiles show both ratings.
    private void refreshRanking(String g) {
        if (mode != Mode.LOBBY) return;
        send("RANK", r -> {
            client.setRanking(g, Wire.parseRanking(first(r)));
            renderTiles();
            renderRanking();
            renderPlayers();
            renderInvites();
        }, code -> code == 12, g);
    }

    private void refreshLists() {
        refreshPlayers();
        refreshRanking(Wire.CARO);
        refreshRanking(Wire.REVERSI);
    }

    private void setMode(Mode m) {
        mode = m;
        if (m == Mode.MATCHING) startClock();
        else stopClock();
        renderAll();
        if (m != Mode.MATCHING) refreshPlayers();
        if (m == Mode.LOBBY) refreshRanking(game());
    }

    // ---------- actions ----------

    @FXML private void onRefresh() { refreshLists(); }

    private void pickGame(String g) {
        if (mode != Mode.LOBBY) return;
        client.setSelectedGame(g);
        renderAll();
        refreshRanking(g);
    }

    // 11. Match Randomly
    private void onRand() {
        String g = game();
        send("RAND", r -> {
            client.getInvites().clear();   // the server cancelled the invitations sent to me
            client.setRoomLabel("Random match");
            status("info", "Searching for a " + Wire.gameLabel(g) + " opponent…");   // STRT may follow at once
            setMode(Mode.MATCHING);
        }, code -> false, g);
    }

    // 12. Cancel Matching. RCAN|12: already paired, STRT follows; not an error.
    private void onCancelRand() {
        send("RCAN", r -> {
            status("info", "Matching cancelled");
            setMode(Mode.LOBBY);
        }, code -> code == 12, new String[0]);
    }

    // 6. Create Room
    private void onCreateRoom() {
        String g = game();
        send("CROM", r -> {
            roomId = first(r);
            roomGame = g;
            invited.clear();
            client.getInvites().clear();
            client.setRoomLabel("Room #" + roomId);
            status("ok", "Room #" + roomId + " created. Invite a free player from the list");
            setMode(Mode.IN_ROOM);
        }, code -> false, g);
    }

    // 7. Invite Player
    private void invite(String name) {
        send("INVT", r -> {
            invited.put(name, "wait");
            status("info", "Invited " + name + ", waiting for an answer");
            renderPlayers();
            renderRoom();
        }, code -> {
            if (code != 5 && code != 6) return false;
            status("err", Code.message(code));
            refreshPlayers();   // the list is out of date
            return true;
        }, name);
    }

    // 10. Leave Room. DROM|12: an invitee has just accepted, STRT follows; not an error.
    private void onCloseRoom() {
        send("DROM", r -> {
            roomId = "";
            invited.clear();
            status("info", "Room closed");
            setMode(Mode.LOBBY);
        }, code -> code == 12, new String[0]);
    }

    // 8. Join Room / Accept Invitation, 9. Decline Invitation
    private void answer(Invite inv, boolean accept) {
        client.getInvites().remove(inv);   // after 0 or 7 this invitation cannot be used again
        renderInvites();
        send(accept ? "IACC" : "IDEC", r -> {
            if (accept) {
                client.getInvites().clear();   // the server removed my other invitations
                client.setRoomLabel("Room #" + inv.roomId());
                renderInvites();
                status("info", "Joining the game…");
            } else {
                status("info", "Declined the invitation from " + inv.host());
            }
        }, code -> {
            if (code != 7) return false;
            status("err", "This invitation is no longer valid");
            return true;
        }, inv.roomId());
    }

    // 3. Log out
    @FXML
    private void onLogout() {
        send("LOGU", r -> {
            client.setUsername("");
            client.getInvites().clear();
            scenes.show("login-view.fxml");
        }, code -> false, new String[0]);
    }

    // ---------- pushes ----------

    @Override
    public void onPush(List<String> m) {
        switch (m.get(0)) {
            case "INVI" -> {   // INVI|<roomId>|<creator>|<game>
                Invite inv = new Invite(m.get(1), m.get(2), m.get(3));
                if (!client.getInvites().contains(inv)) client.getInvites().add(0, inv);
                flashRoom = inv.roomId();
                status("info", inv.host() + " invited you to play " + Wire.gameLabel(inv.game()));
                renderInvites();
            }
            case "IDCL" -> {   // IDCL|<user>: my invitation to <user> is over
                String user = m.get(1);
                if (invited.containsKey(user)) invited.put(user, "declined");
                status("info", user + " declined or is no longer available");
                renderRoom();
                refreshPlayers();
            }
            default -> { }
        }
    }

    // ---------- rendering ----------

    private void renderAll() {
        boolean lobby = mode == Mode.LOBBY;
        refreshButton.setDisable(mode == Mode.MATCHING);
        logoutButton.setDisable(!lobby);
        playersCard.getStyleClass().remove("faded");
        if (mode == Mode.MATCHING) playersCard.getStyleClass().add("faded");
        rankCard.getStyleClass().remove("faded");
        if (!lobby) rankCard.getStyleClass().add("faded");
        renderTiles();
        renderMatch();
        renderRoom();
        renderInvites();
        renderPlayers();
        renderRanking();
    }

    private void renderTiles() {
        tile(caroButton, Wire.CARO);
        tile(reversiButton, Wire.REVERSI);
    }

    private void tile(Button b, String g) {
        boolean on = g.equals(game());
        b.getStyleClass().setAll("gtile");
        if (on) b.getStyleClass().add("on");
        if (!on && mode != Mode.LOBBY) b.getStyleClass().add("faded");
        b.setDisable(mode != Mode.LOBBY);

        Integer elo = client.ratingOf(g, client.getUsername());
        int rank = rankOf(g, client.getUsername());
        Label name = Widgets.label(Wire.gameLabel(g), "gtile-name");
        Label eloL = Widgets.label(elo == null ? "—" : String.valueOf(elo), "gtile-elo");
        Label rankL = Widgets.label(rank > 0 ? "rank #" + rank : "", "gtile-rank");
        VBox right = new VBox(eloL, rankL);
        right.setAlignment(Pos.CENTER_RIGHT);
        HBox content = new HBox(14, tileArt(g), name, Widgets.grow(), right);
        content.setAlignment(Pos.CENTER_LEFT);
        b.setGraphic(content);
        content.prefWidthProperty().bind(b.widthProperty().subtract(24));
    }

    private int rankOf(String g, String name) {
        List<RankRow> rows = client.getRanking(g);
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).name().equals(name)) return i + 1;
        return 0;
    }

    /** Mini board: graph paper with a few marks, or a felt square with four discs. */
    private static Canvas tileArt(String g) {
        Canvas c = new Canvas(58, 58);
        GraphicsContext gc = c.getGraphicsContext2D();
        if (g.equals(Wire.CARO)) {
            gc.setFill(Theme.PAPER);
            gc.fillRoundRect(0, 0, 58, 58, 8, 8);
            gc.setStroke(Theme.PAPER_LINE);
            gc.setLineWidth(1);
            for (double p = 0.5; p < 58; p += 11.6) {
                gc.strokeLine(p, 0, p, 58);
                gc.strokeLine(0, p, 58, p);
            }
            for (int i = 0; i < 3; i++) Pen.cross(gc, 12 + i * 11.6, 29, 10, Theme.RED, 2);
            Pen.ring(gc, 29, 17, 11, Theme.BLUE, 1.8);
            Pen.ring(gc, 41, 41, 11, Theme.BLUE, 1.8);
        } else {
            gc.setFill(Color.web("#5b3e29"));
            gc.fillRoundRect(0, 0, 58, 58, 8, 8);
            gc.setFill(Theme.FELT_BOARD);
            gc.fillRect(3, 3, 52, 52);
            int[][] d = {{0, 0, 2}, {0, 1, 1}, {1, 0, 1}, {1, 1, 2}};
            for (int[] p : d) Pen.disc(gc, 17 + p[1] * 24, 16 + p[0] * 24, 9, p[2], 1, 1);
        }
        return c;
    }

    private void renderMatch() {
        matchBox.getChildren().clear();
        if (mode == Mode.MATCHING) {
            HBox dotsBox = new HBox(4);
            dotsBox.setAlignment(Pos.CENTER);
            Circle[] cs = new Circle[3];
            for (int i = 0; i < 3; i++) cs[i] = new Circle(2.5, Color.WHITE);
            dotsBox.getChildren().addAll(cs);
            if (dots != null) dots.stop();
            dots = new Timeline();
            for (int i = 0; i < 3; i++) {
                double off = i * 0.2;
                dots.getKeyFrames().addAll(
                        new KeyFrame(Duration.seconds(off), new KeyValue(cs[i].opacityProperty(), 0.2)),
                        new KeyFrame(Duration.seconds(off + 0.48), new KeyValue(cs[i].opacityProperty(), 1)),
                        new KeyFrame(Duration.seconds(off + 0.96), new KeyValue(cs[i].opacityProperty(), 0.2)));
            }
            dots.getKeyFrames().add(new KeyFrame(Duration.seconds(1.2)));
            dots.setCycleCount(Animation.INDEFINITE);
            dots.play();

            Label text = Widgets.label("Looking for an opponent", "semi", "on-felt");
            text.setMinWidth(Region.USE_PREF_SIZE);
            matchTime = Widgets.label(clock(), "mono", "on-felt-dim");
            matchTime.setMinWidth(Region.USE_PREF_SIZE);
            HBox row = new HBox(10, dotsBox, text, Widgets.grow(), matchTime);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setMinHeight(46);
            Button cancel = Widgets.button("Cancel", "feltdanger");
            cancel.setMaxWidth(Double.MAX_VALUE);
            cancel.setOnAction(e -> onCancelRand());
            matchBox.getChildren().addAll(row, cancel);
        } else {
            Button find = Widgets.button("Find an opponent", "ivory", "big");
            find.setMaxWidth(Double.MAX_VALUE);
            find.setDisable(mode != Mode.LOBBY);
            find.setOnAction(e -> onRand());
            matchBox.getChildren().add(find);
        }
    }

    private String clock() { return matchSecs / 60 + ":" + String.format("%02d", matchSecs % 60); }

    private void startClock() {
        matchSecs = 0;
        stopClock();
        matchClock = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            matchSecs++;
            if (matchTime != null) matchTime.setText(clock());
        }));
        matchClock.setCycleCount(Animation.INDEFINITE);
        matchClock.play();
    }

    private void stopClock() {
        if (matchClock != null) matchClock.stop();
        if (dots != null) dots.stop();
    }

    private void renderRoom() {
        roomBox.getChildren().clear();
        roomBox.getStyleClass().remove("faded");
        if (mode == Mode.MATCHING) roomBox.getStyleClass().add("faded");
        if (mode == Mode.IN_ROOM) {
            long waiting = invited.values().stream().filter("wait"::equals).count();
            Label title = Widgets.label("Room #" + roomId + " · " + Wire.gameLabel(roomGame), "on-felt", "bold");
            title.setStyle("-fx-font-size: 16px;");
            Label hint = Widgets.label(waiting > 0 ? "Waiting for " + waiting + " answer" + (waiting > 1 ? "s" : "")
                    : "Invite a free player from the list", "on-felt-dim", "small");
            Button close = Widgets.button("Close room", "feltdanger", "sm");
            close.setOnAction(e -> onCloseRoom());
            HBox row = new HBox(10, new VBox(2, title, hint), Widgets.grow(), close);
            row.setAlignment(Pos.CENTER_LEFT);
            roomBox.getChildren().add(row);
        } else {
            Button create = Widgets.button("Create a room", "onfelt");
            create.setMaxWidth(Double.MAX_VALUE);
            create.setDisable(mode != Mode.LOBBY);
            create.setOnAction(e -> onCreateRoom());
            roomBox.getChildren().add(create);
        }
        VBox.setMargin(roomBox, new javafx.geometry.Insets(0));
        roomBox.setPadding(new javafx.geometry.Insets(12, 0, 0, 0));
    }

    private void renderInvites() {
        List<Invite> list = client.getInvites();
        boolean any = !list.isEmpty();
        invitesBox.setVisible(any);
        invitesBox.setManaged(any);
        invitesBox.getStyleClass().remove("faded");
        if (mode != Mode.LOBBY) invitesBox.getStyleClass().add("faded");
        invitesHeader.getChildren().setAll(Widgets.section("Invitations", new Label(String.valueOf(list.size()))));
        inviteRows.getChildren().clear();
        for (Invite inv : list) {
            Text host = new Text(inv.host());
            host.setStyle("-fx-font-weight: bold;");
            Text mid = new Text(" invites you to ");
            Text g = new Text(Wire.gameLabel(inv.game()));
            g.setStyle("-fx-font-weight: bold;");
            for (Text t : new Text[]{host, mid, g}) t.setFill(Theme.INK);
            TextFlow line = new TextFlow(host, mid, g);
            Integer elo = client.ratingOf(inv.game(), inv.host());
            Label sub = Widgets.label("Room #" + inv.roomId() + (elo == null ? "" : " · rated " + elo), "dim", "small");
            VBox text = new VBox(2, line, sub);
            HBox.setHgrow(text, Priority.ALWAYS);

            Button decline = Widgets.button("Decline", "sm");
            decline.setOnAction(e -> answer(inv, false));
            Button accept = Widgets.button("Accept", "primary", "sm");
            accept.setOnAction(e -> answer(inv, true));
            accept.setDisable(mode != Mode.LOBBY);
            decline.setDisable(mode != Mode.LOBBY);

            HBox row = new HBox(10, Widgets.avatar(inv.host(), 30), text, decline, accept);
            row.getStyleClass().add("inv");
            if (inv.roomId().equals(flashRoom)) flash(row);
            inviteRows.getChildren().add(row);
        }
        flashRoom = "";
    }

    /** A new invitation lights up briefly. */
    private static void flash(Region row) {
        ObjectProperty<Color> bg = new SimpleObjectProperty<>(Color.web("#fff1c2"));
        bg.addListener((o, a, c) -> row.setBackground(new Background(new BackgroundFill(c, new CornerRadii(9), null))));
        row.setBackground(new Background(new BackgroundFill(bg.get(), new CornerRadii(9), null)));
        new Timeline(new KeyFrame(Duration.seconds(2.4), new KeyValue(bg, Color.WHITE))).play();
    }

    private void renderPlayers() {
        List<Player> sorted = new ArrayList<>(players);
        sorted.sort(Comparator.comparing((Player p) -> !p.free()).thenComparing(Player::name));
        playersHeader.getChildren().setAll(Widgets.section("Online now",
                new Label(sorted.size() + (sorted.size() == 1 ? " player" : " players"))));
        playersHeader.setPadding(new javafx.geometry.Insets(0, 0, 6, 0));
        playerRows.getChildren().clear();
        if (sorted.isEmpty()) {
            playerRows.getChildren().add(Widgets.label("Nobody else is online", "dim", "small"));
            return;
        }
        boolean room = mode == Mode.IN_ROOM;
        for (Player p : sorted) {
            Label name = Widgets.label(p.name(), "semi");
            Label st = Widgets.label(p.free() ? "Free" : "Busy", "st");
            if (p.free()) st.getStyleClass().add("free");
            VBox who = new VBox(1, name, st);
            HBox.setHgrow(who, Priority.ALWAYS);
            Integer elo = client.ratingOf(game(), p.name());
            Label eloL = Widgets.label(elo == null ? "" : String.valueOf(elo), "dim");
            eloL.setMinWidth(40);
            eloL.setAlignment(Pos.CENTER_RIGHT);

            HBox row = new HBox(10, Widgets.avatar(p.name(), 30), who, eloL);
            row.getStyleClass().add("prow");
            String state = invited.get(p.name());
            if (room && "wait".equals(state)) {
                row.getChildren().add(Widgets.label("Waiting…", "tag", "wait"));
            } else if (room && p.free()) {
                if ("declined".equals(state)) row.getChildren().add(Widgets.label("Declined", "tag", "declined"));
                Button inv = Widgets.button("declined".equals(state) ? "Invite again" : "Invite", "sm");
                inv.setOnAction(e -> invite(p.name()));
                row.getChildren().add(inv);
            }
            playerRows.getChildren().add(row);
        }
    }

    private void renderRanking() {
        rankTitle.setText(Wire.gameLabel(game()).toUpperCase() + " LEADERBOARD");
        rankRows.getChildren().clear();
        List<RankRow> rows = client.getRanking(game());
        for (int i = 0; i < rows.size(); i++) {
            RankRow r = rows.get(i);
            Node pos;
            if (i < 3) {
                String[] fill = {"#dcb64c", "#c3c9d0", "#c99062"}, ink = {"#3b2c00", "#2b3036", "#3a2210"};
                Label n = new Label(String.valueOf(i + 1));
                n.setStyle("-fx-text-fill: " + ink[i] + "; -fx-font-size: 12px; -fx-font-weight: bold;");
                StackPane medal = new StackPane(new Circle(11, Color.web(fill[i])), n);
                medal.setMinWidth(22);
                pos = medal;
            } else {
                Label n = Widgets.label(String.valueOf(i + 1), "rk");
                n.setMinWidth(22);
                n.setAlignment(Pos.CENTER);
                pos = n;
            }
            Label name = Widgets.label(r.name());
            HBox.setHgrow(name, Priority.ALWAYS);
            name.setMaxWidth(Double.MAX_VALUE);
            HBox row = new HBox(10, pos, Widgets.avatar(r.name(), 24), name, Widgets.label(String.valueOf(r.rating()), "semi"));
            row.getStyleClass().add("rrow");
            if (r.name().equals(client.getUsername())) row.getStyleClass().add("me");
            rankRows.getChildren().add(row);
        }
    }
}
