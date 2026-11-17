package netpro.client.app;

import netpro.client.Config;
import netpro.client.app.game.GameSession;
import netpro.client.net.Connection;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class GameClient {
    /** Response của server: "CMD|code|extra...". */
    public record Response(String cmd, int code, List<String> extra) {}

    private String username = "";
    private GameSession game;   // chỉ đọc/ghi trên FX thread

    public String getUsername() { return username; }
    public void setUsername(String u) { this.username = u; }
    public GameSession getGame() { return game; }
    public void setGame(GameSession g) { this.game = g; }

    // 6 push của protocol. Mọi dòng khác là response của request đang chờ.
    private static final Set<String> PUSH = Set.of("INVI", "IDCL", "STRT", "OPPM", "SKIP", "OVER");

    private final Queue<CompletableFuture<Response>> pending = new ConcurrentLinkedQueue<>();
    private volatile Consumer<List<String>> pushListener = m -> {};
    private volatile Runnable closedListener = () -> {};
    private Config.Address server = new Config.Address(Config.HOST, Config.PORT);
    private Connection conn;

    public void setPushListener(Consumer<List<String>> l) { this.pushListener = l; }
    public void setClosedListener(Runnable r) { this.closedListener = r; }

    public synchronized Config.Address getServer() { return server; }

    /** Đổi địa chỉ server. Nếu đang nối tới địa chỉ khác thì ngắt (không báo "mất kết nối"). */
    public synchronized void setServer(Config.Address a) {
        if (a.equals(server)) return;
        disconnect();
        server = a;
    }

    /** Chặn luồng gọi: KHÔNG gọi trên FX thread. */
    public synchronized void connect() throws IOException {
        if (conn != null) return;
        conn = new Connection(server.host(), server.port(), this::onLine, this::onClosed);
    }

    public CompletableFuture<Response> request(String cmd, String... fields) {
        CompletableFuture<Response> f = new CompletableFuture<>();
        synchronized (this) {   // enqueue và gửi phải đi cùng nhau để giữ đúng thứ tự FIFO
            if (conn == null) {
                f.completeExceptionally(new IOException("Not connected"));
                return f;
            }
            List<String> parts = new ArrayList<>();
            parts.add(cmd);
            parts.addAll(Arrays.asList(fields));
            String line = String.join("|", parts);
            pending.add(f);
            try {
                conn.writeLine(line);
                System.out.println("SEND " + line);
            } catch (IOException e) {
                pending.remove(f);
                f.completeExceptionally(e);
                return f;
            }
        }
        return f.orTimeout(5, TimeUnit.SECONDS);
    }

    // Chạy trên reader thread
    private void onLine(String line) {
        System.out.println("RECV " + line);   // "Log received message" trong bảng xử lý
        String[] p = line.split("\\|", -1);   // -1: giữ field cuối rỗng (vd "MOVE|0|0|")
        List<String> parts = Arrays.asList(p);
        if (PUSH.contains(p[0])) {
            pushListener.accept(parts);
            return;
        }
        CompletableFuture<Response> f = pending.poll();
        if (f == null) return;   // không có request nào đang chờ: bỏ qua
        try {
            int code = Integer.parseInt(p[1]);
            f.complete(new Response(p[0], code, parts.subList(2, parts.size())));
        } catch (RuntimeException e) {
            f.completeExceptionally(e);
        }
    }

    // Chạy trên reader thread khi socket đóng.
    private void onClosed(Connection closed) {
        synchronized (this) {
            if (conn != closed) return;   // do chính mình chủ động ngắt: đã dọn trong disconnect()
            conn = null;
        }
        failPending("Connection closed");
        closedListener.run();
    }

    private void failPending(String why) {
        CompletableFuture<Response> f;
        while ((f = pending.poll()) != null) f.completeExceptionally(new IOException(why));
    }

    public synchronized void disconnect() {
        if (conn != null) {
            Connection c = conn;
            conn = null;   // đặt trước khi đóng để onClosed biết đây là chủ động
            c.close();
            failPending("Disconnected");
        }
    }
}
