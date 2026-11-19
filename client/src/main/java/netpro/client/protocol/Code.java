package netpro.client.protocol;

public final class Code {
    private Code() {}

    public static String message(int code) {
        return switch (code) {
            case 0 -> "OK";
            case 1 -> "Incorrect username or password";
            case 2 -> "This account is already logged in";
            case 3 -> "Username already exists";
            case 4 -> "Game not found";
            case 5 -> "That player is not online";
            case 6 -> "That player is busy";
            case 7 -> "This invitation is no longer valid";
            case 8 -> "You already invited that player";
            case 9 -> "You cannot invite yourself";
            case 10 -> "Invalid request";
            case 11 -> "Invalid username or password format";
            case 12 -> "Not allowed right now";
            case 13 -> "It is not your turn";
            case 14 -> "That cell is outside the board";
            case 15 -> "That cell is already taken";
            case 16 -> "That move is not allowed";
            case 17 -> "Incorrect username or recovery code";
            default -> "Unknown error (code " + code + ")";
        };
    }
}