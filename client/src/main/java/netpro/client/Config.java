package netpro.client;

public final class Config {
    public static final String HOST = System.getProperty("server.host", "127.0.0.1");
    public static final int PORT = Integer.getInteger("server.port", 5000);

    private Config() {}
}