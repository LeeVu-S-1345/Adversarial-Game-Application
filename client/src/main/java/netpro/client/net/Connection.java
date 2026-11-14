package netpro.client.net;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public class Connection implements AutoCloseable {
    private final Socket socket;
    private final BufferedReader in;
    private final BufferedWriter out;
    private final Consumer<String> onLine;
    private final Runnable onClosed;

    public Connection(String host, int port, Consumer<String> onLine, Runnable onClosed)
            throws IOException {
        this.socket = new Socket(host, port);
        this.in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.out = new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        this.onLine = onLine;
        this.onClosed = onClosed;

        Thread t = new Thread(this::readLoop, "server-reader");
        t.setDaemon(true);   // đóng cửa sổ là thread tự dừng
        t.start();
    }

    // synchronized: nhiều luồng cùng gửi thì không bị trộn byte giữa các dòng
    public synchronized void writeLine(String line) throws IOException {
        out.write(line);
        out.write("\r\n");
        out.flush();
    }

    private void readLoop() {
        try {
            String line;
            while ((line = in.readLine()) != null) onLine.accept(line);
        } catch (IOException ignored) {
            // socket đóng
        } finally {
            onClosed.run();
        }
    }

    @Override
    public void close() {
        try { socket.close(); } catch (IOException ignored) {}
    }
}