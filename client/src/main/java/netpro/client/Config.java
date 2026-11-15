package netpro.client;

import java.util.prefs.Preferences;

/**
 * Địa chỉ server. Thứ tự ưu tiên khi khởi động (xem App):
 * tham số dòng lệnh "host[:port]" > -Dserver.host / -Dserver.port > địa chỉ dùng lần trước > mặc định.
 * Người dùng cũng sửa được ngay trên màn hình đăng nhập.
 */
public final class Config {
    public static final int DEFAULT_PORT = 5000;
    public static final String HOST = System.getProperty("server.host", "127.0.0.1");
    public static final int PORT = Integer.getInteger("server.port", DEFAULT_PORT);

    public record Address(String host, int port) {
        @Override public String toString() { return host + ":" + port; }
    }

    private static final String PREF_SERVER = "server";
    private static final Preferences PREFS = Preferences.userNodeForPackage(Config.class);

    private Config() {}

    /** "host" hoặc "host:port". Ném IllegalArgumentException nếu sai dạng. */
    public static Address parseAddress(String text) {
        String s = text == null ? "" : text.trim();
        if (s.isEmpty()) throw new IllegalArgumentException("empty address");
        int colon = s.lastIndexOf(':');
        if (colon < 0) return new Address(s, DEFAULT_PORT);
        String host = s.substring(0, colon).trim();
        int port;
        try {
            port = Integer.parseInt(s.substring(colon + 1).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("bad port");
        }
        if (host.isEmpty() || port < 1 || port > 65535) throw new IllegalArgumentException("bad address");
        return new Address(host, port);
    }

    public static boolean propertyGiven() {
        return System.getProperty("server.host") != null || System.getProperty("server.port") != null;
    }

    /** Địa chỉ dùng lần trước, hoặc null. */
    public static Address savedAddress() {
        try {
            String s = PREFS.get(PREF_SERVER, "");
            return s.isEmpty() ? null : parseAddress(s);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static void saveAddress(Address a) {
        try {
            PREFS.put(PREF_SERVER, a.toString());
        } catch (RuntimeException ignored) {
            // không lưu được thì thôi
        }
    }
}
