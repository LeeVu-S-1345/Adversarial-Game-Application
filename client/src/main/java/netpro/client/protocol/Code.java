package netpro.client.protocol;

public final class Code {
    private Code() {}

    public static String message(int code) {
        return switch (code) {
            case 0 -> "OK";
            case 1 -> "Sai tên đăng nhập hoặc mật khẩu";
            case 2 -> "Tài khoản đang đăng nhập ở nơi khác";
            case 3 -> "Tên đăng nhập đã tồn tại";
            case 4 -> "Không tìm thấy game";
            case 5 -> "Người được mời không online";
            case 6 -> "Người được mời đang bận";
            case 7 -> "Không tìm thấy lời mời";
            case 8 -> "Đã gửi lời mời rồi";
            case 9 -> "Không thể tự mời mình";
            case 10 -> "Dữ liệu gửi lên không hợp lệ";
            case 11 -> "Tên đăng nhập hoặc mật khẩu không hợp lệ";
            case 12 -> "Thao tác không hợp lệ ở trạng thái hiện tại";
            case 13 -> "Chưa đến lượt bạn";
            case 14 -> "Nước đi ngoài bàn cờ";
            case 15 -> "Ô đã có quân";
            case 16 -> "Nước đi không hợp lệ";
            default -> "Lỗi không xác định (mã " + code + ")";
        };
    }
}