package netpro.client.app;

import netpro.client.Config;
import netpro.client.app.game.GameSession;
import netpro.client.net.Connection;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class GameClient {
    public record Response(String cmd, int code, List<String> extra) {}

    private String username = "";
    private GameSession game;   // chỉ đọc/ghi trên FX thread

    public String getUsername() { return username; }
    public void setUsername(String u) { this.username = u; }
    public GameSession getGame() { return game; }
    public void setGame(GameSession g) { this.game = g; }

    private static final Set<String> PUSH = Set.of("INVI", "IDCL", "STRT", "OPPM", "SKIP", "OVER");

    private final Queue<CompletableFuture<Response>> pending = new ConcurrentLinkedQueue<>();
    private volatile Consumer<List<String>> pushListener = m -> {};
    private volatile Runnable closedListener = () -> {};
    private Connection conn;

    public void setPushListener(Consumer<List<String>> l) { this.pushListener = l; }
    public void setClosedListener(Runnable r) { this.closedListener = r; }

    /** Chặn luồng gọi: KHÔNG gọi trên FX thread. */
    public synchronized void connect() throws IOException {
        if (conn != null) return;
        conn = new Connection(Config.HOST, Config.PORT, this::onLine, this::onClosed);
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
            pending.add(f);
            try {
                conn.writeLine(String.join("|", parts));
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
        String[] p = line.split("\\|", -1);   // -1: giữ field cuối rỗng
        List<String> parts = Arrays.asList(p);
        if (PUSH.contains(p[0])) {
            pushListener.accept(parts);
            return;
        }
        CompletableFuture<Response> f = pending.poll();
        if (f == null) return;
        try {
            int code = Integer.parseInt(p[1]);
            f.complete(new Response(p[0], code, parts.subList(2, parts.size())));
        } catch (RuntimeException e) {
            f.completeExceptionally(e);
        }
    }

    private void onClosed() {
        synchronized (this) { conn = null; }
        CompletableFuture<Response> f;
        while ((f = pending.poll()) != null) {
            f.completeExceptionally(new IOException("Connection closed"));
        }
        closedListener.run();
    }

    public synchronized void disconnect() {
        if (conn != null) {
            conn.close();
            conn = null;
        }
    }
}