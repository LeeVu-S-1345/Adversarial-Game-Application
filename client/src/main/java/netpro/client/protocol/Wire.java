package netpro.client.protocol;

import java.util.ArrayList;
import java.util.List;

/** Tách các field dạng danh sách của protocol (";" giữa phần tử, "," giữa thuộc tính). */
public final class Wire {
    private Wire() {}

    public static final String CARO = "caro";
    public static final String REVERSI = "reversi";
    public static final List<String> GAMES = List.of(CARO, REVERSI);

    public record Player(String name, String status) {
        public boolean free() { return "FREE".equals(status); }
        @Override public String toString() { return name + (free() ? "" : "  (bận)"); }
    }

    public record RankRow(String name, int rating) {}

    /** PLST|0|<user1>,<status1>;...;<usern>,<statusn> */
    public static List<Player> parsePlayers(String field) {
        List<Player> out = new ArrayList<>();
        if (field == null || field.isEmpty()) return out;
        for (String item : field.split(";")) {
            String[] p = item.split(",", -1);
            if (p.length == 2) out.add(new Player(p[0], p[1]));
        }
        return out;
    }

    /** RANK|0|<user1>,<rating1>;...;<usern>,<ratingn> */
    public static List<RankRow> parseRanking(String field) {
        List<RankRow> out = new ArrayList<>();
        if (field == null || field.isEmpty()) return out;
        for (String item : field.split(";")) {
            String[] p = item.split(",", -1);
            if (p.length != 2) continue;
            try {
                out.add(new RankRow(p[0], Integer.parseInt(p[1])));
            } catch (NumberFormatException ignored) {
                // bỏ qua phần tử hỏng
            }
        }
        return out;
    }

    public static String gameLabel(String game) {
        return switch (game) {
            case CARO -> "Caro (15x15)";
            case REVERSI -> "Reversi (8x8)";
            default -> game;
        };
    }
}
