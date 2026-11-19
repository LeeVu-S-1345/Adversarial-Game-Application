// Takes the screenshots in docs/ui-spec/images from the real client (see capture.sh).
// It starts a server, logs in four bot players over plain sockets, launches the JavaFX client,
// drives it through each screen and saves a snapshot plus the positions of the numbered parts.
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextInputControl;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.stage.Window;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public class Capture {
    static Path out;
    static int port;
    static Process server;
    static final StringBuilder callouts = new StringBuilder("{\n");
    static boolean firstShot = true;

    public static void main(String[] args) throws Exception {
        try {
            run(args);
        } catch (Throwable t) {
            t.printStackTrace();
            if (server != null) server.destroy();
            System.exit(1);
        }
    }

    static void run(String[] args) throws Exception {
        Path serverBin = Paths.get(args[0]);
        out = Paths.get(args[1]);
        port = Integer.parseInt(args[2]);
        Files.createDirectories(out);
        Path users = out.resolve("users.db");
        Files.deleteIfExists(users);

        ProcessBuilder pb = new ProcessBuilder(serverBin.toString(), String.valueOf(port));
        pb.environment().put("NETPRO_USERS_FILE", users.toString());
        pb.environment().put("NETPRO_LOG_FILE", out.resolve("server.log").toString());
        pb.environment().remove("PG_CONNINFO");
        pb.redirectOutput(out.resolve("server.out").toFile()).redirectErrorStream(true);
        server = pb.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.destroy()));
        Thread.sleep(600);

        // ---------- accounts and some rated games, so the leaderboard is not flat ----------
        String minhCode = Bot.register("minh", "123456");
        for (String n : new String[]{"an", "binh", "dung", "chi"}) Bot.register(n, "x7#kq2");
        Bot an = Bot.login("an"), binh = Bot.login("binh"), dung = Bot.login("dung"), chi = Bot.login("chi");
        Bot minh = Bot.login("minh", "123456");
        ratedGame(an, binh, "caro");
        ratedGame(minh, chi, "caro");
        ratedGame(an, dung, "caro");
        ratedGame(dung, an, "reversi");
        ratedGame(binh, chi, "reversi");
        minh.req("LOGU");
        minh.close();

        // ---------- the client ----------
        new Thread(() -> Application.launch(netpro.client.App.class, "127.0.0.1:" + port), "fx").start();
        waitFor("window", () -> Window.getWindows().stream().anyMatch(Window::isShowing));
        Thread.sleep(800);

        // 01 log in with a wrong password
        fx(() -> {
            text("#userField", "minh");
            text("#passField", "wrong1");
            fire("#loginButton");
        });
        waitFor("login error", () -> labelWith("Incorrect username or password") != null);
        shot("01-login", c -> {
            c.put("1", node("#userField"));
            c.put("2", node("#passField"));
            c.put("3", node("#eyeButton"));
            c.put("4", node("#rememberBox"));
            c.put("5", button("Forgot password?"));
            c.put("6", node("#loginButton"));
            c.put("7", node("#statusLabel"));
            c.put("8", node("#registerButton"));
            c.put("9", node("#serverField"));
        });

        // 02 create an account: the recovery code dialog
        fx(() -> {
            text("#userField", "lan");
            text("#passField", "secret1");
            fire("#registerButton");
        });
        waitFor("recovery dialog", () -> labelWith("Account created") != null);
        shot("02-register-code", c -> {
            c.put("1", styled("code"));
            c.put("2", button("Copy code"));
            c.put("3", button("I have saved it"));
        });
        fx(() -> button("I have saved it").fire());
        Thread.sleep(300);

        // 03 reset password with a wrong code
        fx(() -> {
            text("#userField", "minh");
            button("Forgot password?").fire();
        });
        Thread.sleep(300);
        fx(() -> {
            text("#rsCode", "ABCD2345");
            text("#rsNew", "123456");
            text("#rsConfirm", "123456");
            fire("#resetButton");
        });
        waitFor("reset error", () -> labelWith("Incorrect username or recovery code") != null);
        shot("03-forgot-password", c -> {
            c.put("1", node("#rsUser"));
            c.put("2", node("#rsCode"));
            c.put("3", node("#rsNew"));
            c.put("4", node("#rsConfirm"));
            c.put("5", node("#resetButton"));
            c.put("6", node("#rsStatus"));
            c.put("7", button("← Back to log in"));
        });

        // 04 the right code: password changed, new code
        fx(() -> {
            text("#rsCode", minhCode.toLowerCase());   // the client upper-cases it
            fire("#resetButton");
        });
        waitFor("password changed", () -> labelWith("Password changed") != null);
        shot("04-reset-done", c -> c.put("1", styled("code")));
        fx(() -> button("Back to log in").fire());
        Thread.sleep(300);

        // 05 the lobby with two invitations; chi is busy (searching for a reversi game)
        chi.req("RAND|reversi");
        fx(() -> {
            text("#passField", "123456");
            fire("#loginButton");
        });
        waitFor("lobby", () -> node("#playersCard") != null);
        Thread.sleep(700);
        String dungRoom = dung.req("CROM|reversi").split("\\|")[2];
        dung.req("INVT|minh");
        Thread.sleep(400);
        String anRoom = an.req("CROM|caro").split("\\|")[2];
        an.req("INVT|minh");
        Thread.sleep(2600);   // let the new-invitation highlight fade
        shot("05-lobby", c -> {
            c.put("1", node("#caroTile"));
            c.put("2", button("Find an opponent"));
            c.put("3", button("Create a room"));
            c.put("4", node("#invitesBox"));
            c.put("5", button("Accept"));
            c.put("6", node("#playersCard"));
            c.put("7", node("#rankCard"));
            c.put("8", node("#refreshButton"));
            c.put("9", node("#logoutButton"));
            c.put("10", node("#statusLabel"));
        });
        an.req("DROM");
        dung.req("DROM");

        // 06 searching for an opponent
        fx(() -> button("Find an opponent").fire());
        Thread.sleep(3300);
        shot("06-lobby-matching", c -> {
            c.put("1", styled("on-felt"));
            c.put("2", button("Cancel"));
        });
        fx(() -> button("Cancel").fire());
        Thread.sleep(500);

        // 07 a room: an has not answered yet, binh declined
        fx(() -> button("Create a room").fire());
        waitFor("room", () -> button("Close room") != null);
        Thread.sleep(400);
        fx(() -> inviteButton("an").fire());
        Thread.sleep(300);
        fx(() -> inviteButton("binh").fire());
        String binhInv = binh.waitPush("INVI");
        binh.req("IDEC|" + binhInv.split("\\|")[1]);
        String anInv = an.waitPush("INVI");
        Thread.sleep(700);
        shot("07-lobby-room", c -> {
            c.put("1", styled("on-felt"));
            c.put("2", button("Close room"));
            c.put("3", labelWith("Waiting…"));
            c.put("4", button("Invite again"));
            c.put("5", inviteButton("dung"));
        });

        // 08 caro: an accepts, ten moves, the mouse over 8,7
        an.req("IACC|" + anInv.split("\\|")[1]);
        waitFor("game", () -> board() != null);
        Thread.sleep(500);
        int[][] caro = {{7, 7}, {6, 7}, {6, 6}, {5, 5}, {7, 5}, {8, 6}, {8, 8}, {7, 4}, {7, 6}, {7, 8}};
        for (int i = 0; i < caro.length; i++) {
            if (i % 2 == 0) uiMove(caro[i][0], caro[i][1]);
            else an.move(caro[i][0], caro[i][1]);
        }
        Thread.sleep(500);
        fx(() -> hover(8, 7));
        Thread.sleep(200);
        shot("08-game-caro", c -> {
            c.put("1", board());
            c.put("2", node("#oppCard"));
            c.put("3", node("#meCard"));
            c.put("4", node("#historyRows"));
            c.put("5", node("#resignButton"));
            c.put("6", node("#statusLabel"));
        });

        // 10 resign? (keep playing)
        fx(() -> fire("#resignButton"));
        Thread.sleep(400);
        shot("10-resign-confirm", c -> {
            c.put("1", button("Keep playing"));
            c.put("2", dialogButton("Resign"));
        });
        fx(() -> button("Keep playing").fire());
        Thread.sleep(300);

        // 11 five in a row on the diagonal 6,6 .. 10,10
        uiMove(9, 9);
        an.move(0, 14);
        uiMove(10, 10);
        waitFor("result", () -> labelWith("YOU WIN") != null);
        Thread.sleep(600);
        shot("11-game-over", c -> {
            c.put("1", board());
            c.put("2", labelWith("YOU WIN"));
            c.put("3", styled("result"));
            c.put("4", button("Back to lobby"));
        });
        fx(() -> button("Back to lobby").fire());
        waitFor("lobby again", () -> node("#playersCard") != null);
        Thread.sleep(800);

        // 09 reversi: replay a real game that ends with SKIP|2 (white has no legal move)
        chi.req("RCAN");
        List<int[]> seq = Reversi.gameWithSkip();
        fx(() -> button("Reversi").fire());   // the tile's text starts with "Reversi"
        Thread.sleep(400);
        fx(() -> button("Create a room").fire());
        waitFor("room 2", () -> button("Close room") != null);
        Thread.sleep(300);
        fx(() -> inviteButton("dung").fire());
        String dungInv = dung.waitPush("INVI");
        dung.req("IACC|" + dungInv.split("\\|")[1]);
        waitFor("reversi", () -> board() != null);
        Thread.sleep(400);
        for (int[] m : seq) {
            if (m[2] == 1) uiMove(m[0], m[1]);
            else dung.move(m[0], m[1]);
        }
        waitFor("skip toast", () -> styled("toast") != null);
        Thread.sleep(350);
        shot("09-game-reversi", c -> {
            c.put("1", board());
            c.put("2", styled("toast"));
            c.put("3", node("#meCard"));
            c.put("4", node("#historyRows"));
        });

        // 12 connection lost during a game: stop the server
        Thread.sleep(500);
        server.destroy();
        waitFor("lost dialog", () -> labelWith("Connection lost") != null);
        Thread.sleep(500);
        shot("12-disconnected", c -> {
            c.put("1", labelWith("Connection lost"));
            c.put("2", styled("modal-text"));
            c.put("3", node("#serverLabel"));
        });

        callouts.append("\n}\n");
        Files.writeString(out.resolve("callouts.json"), callouts.toString());
        Platform.exit();
        System.exit(0);
    }

    // ---------- rated games, decided by a resignation ----------

    static void ratedGame(Bot winner, Bot loser, String game) throws Exception {
        String room = winner.req("CROM|" + game).split("\\|")[2];
        winner.req("INVT|" + loser.name);
        loser.waitPush("INVI");
        loser.req("IACC|" + room);
        winner.waitPush("STRT");
        loser.waitPush("STRT");
        loser.req("RSGN");
        winner.waitPush("OVER");
        loser.waitPush("OVER");
    }

    // ---------- UI helpers (everything that touches nodes runs on the FX thread) ----------

    static Scene scene() {
        for (Window w : Window.getWindows()) if (w instanceof Stage s && s.isShowing()) return s.getScene();
        return null;
    }

    static void fx(Runnable r) throws Exception {
        CompletableFuture<Void> f = new CompletableFuture<>();
        Platform.runLater(() -> {
            try { r.run(); f.complete(null); } catch (Throwable t) { f.completeExceptionally(t); }
        });
        f.get(10, TimeUnit.SECONDS);
    }

    static <T> T onFx(Supplier<T> s) throws Exception {
        CompletableFuture<T> f = new CompletableFuture<>();
        Platform.runLater(() -> {
            try { f.complete(s.get()); } catch (Throwable t) { f.completeExceptionally(t); }
        });
        return f.get(10, TimeUnit.SECONDS);
    }

    static void waitFor(String what, Supplier<Object> cond) throws Exception {
        long end = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < end) {
            Object v = Platform.isFxApplicationThread() ? cond.get() : onFxSafe(cond);
            if (v instanceof Boolean b ? b : v != null) return;
            Thread.sleep(100);
        }
        throw new IllegalStateException("timed out waiting for " + what);
    }

    static Object onFxSafe(Supplier<Object> s) {
        try { return onFx(s); } catch (Exception e) { return null; }
    }

    static List<Node> all() {
        List<Node> list = new ArrayList<>();
        Scene sc = scene();
        if (sc != null) collect(sc.getRoot(), list);
        return list;
    }

    static void collect(Node n, List<Node> list) {
        if (!n.isVisible()) return;
        list.add(n);
        if (n instanceof Labeled l && l.getGraphic() != null) collect(l.getGraphic(), list);
        if (n instanceof Parent p) for (Node c : p.getChildrenUnmodifiable()) collect(c, list);
    }

    static Node node(String sel) {
        Scene sc = scene();
        Node n = sc == null ? null : sc.lookup(sel);
        return n != null && n.isVisible() ? n : null;
    }

    static void text(String sel, String v) { ((TextInputControl) node(sel)).setText(v); }

    static void fire(String sel) { ((Button) node(sel)).fire(); }

    static Button button(String prefix) {
        Button found = null;
        for (Node n : all()) if (n instanceof Button b && b.getText() != null && b.getText().startsWith(prefix)) found = b;
        if (found == null) {   // a tile: its text sits in a graphic
            for (Node n : all()) if (n instanceof Button b && b.getGraphic() != null && labelIn(b.getGraphic(), prefix)) return b;
        }
        return found;
    }

    static boolean labelIn(Node g, String text) {
        List<Node> l = new ArrayList<>();
        collect(g, l);
        for (Node n : l) if (n instanceof Labeled lb && text.equals(lb.getText())) return true;
        return false;
    }

    /** The last button with this text (in a dialog it is on top of the screen's own). */
    static Button dialogButton(String text) {
        Button found = null;
        for (Node n : all()) if (n instanceof Button b && text.equals(b.getText())) found = b;
        return found;
    }

    static Labeled labelWith(String text) {
        for (Node n : all()) if (n instanceof Labeled l && l.getText() != null && l.getText().startsWith(text)) return l;
        return null;
    }

    static Node styled(String cls) {
        for (Node n : all()) if (n.getStyleClass().contains(cls)) return n;
        return null;
    }

    /** The Invite / Invite again button in the player row of `name`. */
    static Button inviteButton(String name) {
        for (Node n : all()) {
            if (n.getStyleClass().contains("prow") && labelIn(n, name)) {
                List<Node> l = new ArrayList<>();
                collect(n, l);
                for (Node c : l) if (c instanceof Button b && b.getText().startsWith("Invite")) return b;
            }
        }
        return null;
    }

    static Node board() {
        for (Node n : all()) if (n.getClass().getSimpleName().equals("BoardView")) return n;
        return null;
    }

    @SuppressWarnings("unchecked")
    static void uiMove(int r, int c) throws Exception {
        String before = onFx(() -> {
            Node l = node("#statusLabel");
            return l == null ? "" : ((Labeled) l).getText();
        });
        fx(() -> {
            try {
                Field f = board().getClass().getDeclaredField("onCell");
                f.setAccessible(true);
                ((BiConsumer<Integer, Integer>) f.get(board())).accept(r, c);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        });
        waitFor("move " + r + "," + c, () -> {
            Node l = node("#statusLabel");
            String t = l == null ? "" : ((Labeled) l).getText();
            return !t.equals(before);
        });
        Thread.sleep(120);
    }

    static void hover(int r, int c) {
        try {
            Method m = board().getClass().getDeclaredMethod("hover", int.class, int.class);
            m.setAccessible(true);
            m.invoke(board(), r, c);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    interface Parts { void put(String no, Node n); }

    static void shot(String name, java.util.function.Consumer<Parts> parts) throws Exception {
        Thread.sleep(450);   // let dialog and toast animations finish
        Map<String, double[]> boxes = new LinkedHashMap<>();
        WritableImage img = onFx(() -> {
            parts.accept((no, n) -> {
                if (n == null) {
                    System.err.println(name + ": part " + no + " not found");
                    return;
                }
                Bounds b = n.localToScene(n.getLayoutBounds());   // layout bounds: without shadows
                boxes.put(no, new double[]{b.getMinX(), b.getMinY(), b.getWidth(), b.getHeight()});
            });
            return scene().snapshot(null);
        });
        PixelReader pr = img.getPixelReader();
        int w = (int) img.getWidth(), h = (int) img.getHeight();
        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) bi.setRGB(x, y, pr.getArgb(x, y));
        ImageIO.write(bi, "png", out.resolve(name + ".png").toFile());

        if (!firstShot) callouts.append(",\n");
        firstShot = false;
        callouts.append("  \"").append(name).append("\": {");
        boolean first = true;
        for (var e : boxes.entrySet()) {
            double[] v = e.getValue();
            callouts.append(first ? "" : ", ").append('"').append(e.getKey()).append("\": [")
                    .append(String.format(Locale.ROOT, "%.0f, %.0f, %.0f, %.0f", v[0], v[1], v[2], v[3])).append(']');
            first = false;
        }
        callouts.append('}');
        System.out.println("shot " + name);
    }

    // ---------- bot players over plain sockets ----------

    static class Bot {
        final String name;
        final Socket sock;
        final BufferedReader in;
        final Writer w;
        final BlockingQueue<String> replies = new LinkedBlockingQueue<>();
        final BlockingQueue<String> pushes = new LinkedBlockingQueue<>();
        static final Set<String> PUSH = Set.of("INVI", "IDCL", "STRT", "OPPM", "SKIP", "OVER");

        Bot(String name) throws IOException {
            this.name = name;
            sock = new Socket("127.0.0.1", port);
            in = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
            w = new OutputStreamWriter(sock.getOutputStream(), StandardCharsets.UTF_8);
            Thread t = new Thread(() -> {
                try {
                    String line;
                    while ((line = in.readLine()) != null) {
                        String head = line.split("\\|", 2)[0];
                        (PUSH.contains(head) ? pushes : replies).add(line);
                    }
                } catch (IOException ignored) { }
            }, "bot-" + name);
            t.setDaemon(true);
            t.start();
        }

        static String register(String name, String pass) throws Exception {
            Bot b = new Bot(name);
            String r = b.req("REGI|" + name + "|" + pass);
            b.close();
            String[] p = r.split("\\|");
            return p.length > 2 ? p[2] : "";
        }

        static Bot login(String name) throws Exception { return login(name, "x7#kq2"); }

        static Bot login(String name, String pass) throws Exception {
            Bot b = new Bot(name);
            b.req("LOGI|" + name + "|" + pass);
            return b;
        }

        String req(String line) throws Exception {
            w.write(line + "\r\n");
            w.flush();
            String r = replies.poll(5, TimeUnit.SECONDS);
            if (r == null) throw new IllegalStateException(name + ": no reply to " + line);
            if (!r.split("\\|")[1].equals("0")) System.err.println(name + ": " + line + " -> " + r);
            return r;
        }

        String waitPush(String type) throws Exception {
            long end = System.currentTimeMillis() + 8000;
            while (System.currentTimeMillis() < end) {
                String p = pushes.poll(200, TimeUnit.MILLISECONDS);
                if (p != null && p.startsWith(type)) return p;
            }
            throw new IllegalStateException(name + ": no " + type);
        }

        /** Waits until it is this bot's turn (the client's move reached it), then moves. */
        void move(int r, int c) throws Exception {
            waitPush("OPPM");
            req("MOVE|" + r + "," + c);
            Thread.sleep(250);
        }

        void close() throws IOException { sock.close(); }
    }

    // ---------- in-memory Preferences, so a run does not touch the user's saved address ----------

    public static class MemPrefsFactory implements java.util.prefs.PreferencesFactory {
        static final java.util.prefs.Preferences ROOT = new MemNode(null, "");
        public java.util.prefs.Preferences systemRoot() { return ROOT; }
        public java.util.prefs.Preferences userRoot() { return ROOT; }
    }

    static class MemNode extends java.util.prefs.AbstractPreferences {
        final Map<String, String> values = new HashMap<>();
        final Map<String, MemNode> kids = new HashMap<>();
        MemNode(MemNode parent, String name) { super(parent, name); }
        protected void putSpi(String k, String v) { values.put(k, v); }
        protected String getSpi(String k) { return values.get(k); }
        protected void removeSpi(String k) { values.remove(k); }
        protected void removeNodeSpi() { }
        protected String[] keysSpi() { return values.keySet().toArray(new String[0]); }
        protected String[] childrenNamesSpi() { return kids.keySet().toArray(new String[0]); }
        protected java.util.prefs.AbstractPreferences childSpi(String n) { return kids.computeIfAbsent(n, x -> new MemNode(this, x)); }
        protected void syncSpi() { }
        protected void flushSpi() { }
    }

    // ---------- a real reversi game that ends with white unable to move ----------

    static class Reversi {
        static final int[][] D = {{-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1}};

        static List<int[]> flips(int[][] b, int r, int c, int s) {
            List<int[]> out = new ArrayList<>();
            if (b[r][c] != 0) return out;
            for (int[] d : D) {
                List<int[]> line = new ArrayList<>();
                int i = r + d[0], j = c + d[1];
                while (i >= 0 && i < 8 && j >= 0 && j < 8 && b[i][j] == 3 - s) { line.add(new int[]{i, j}); i += d[0]; j += d[1]; }
                if (!line.isEmpty() && i >= 0 && i < 8 && j >= 0 && j < 8 && b[i][j] == s) out.addAll(line);
            }
            return out;
        }

        static List<int[]> legal(int[][] b, int s) {
            List<int[]> out = new ArrayList<>();
            for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++) if (!flips(b, r, c, s).isEmpty()) out.add(new int[]{r, c});
            return out;
        }

        /** Moves {r, c, side}, no skip before the last one; after the last black move white cannot move. */
        static List<int[]> gameWithSkip() {
            for (long seed = 1; seed < 200000; seed++) {
                Random rnd = new Random(seed);
                int[][] b = new int[8][8];
                b[3][3] = b[4][4] = 2;
                b[3][4] = b[4][3] = 1;
                List<int[]> seq = new ArrayList<>();
                int s = 1;
                while (true) {
                    List<int[]> ms = legal(b, s);
                    if (ms.isEmpty()) break;   // an earlier skip: try another seed
                    int[] m = ms.get(rnd.nextInt(ms.size()));
                    for (int[] f : flips(b, m[0], m[1], s)) b[f[0]][f[1]] = s;
                    b[m[0]][m[1]] = s;
                    seq.add(new int[]{m[0], m[1], s});
                    if (s == 1 && legal(b, 2).isEmpty() && !legal(b, 1).isEmpty() && seq.size() > 30) return seq;
                    s = 3 - s;
                }
            }
            throw new IllegalStateException("no game found");
        }
    }
}
