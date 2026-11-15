// server/src/core/auth.cpp  -- Scenario 1: REGI (1), LOGI (2), LOGU (3)
#include <cctype>
#include <string>

#include "Hub.h"

namespace core {

namespace {

// Appendix A, A1. Username: 1..32 ký tự, chỉ chữ, số, gạch dưới.
bool validName(const std::string& s) {
    if (s.empty() || s.size() > 32) return false;
    for (unsigned char ch : s) {
        if (!std::isalnum(ch) && ch != '_') return false;
    }
    return true;
}

// Password: 1..64 ký tự ASCII in được, không có dấu cách (0x21..0x7E).
// Ký tự '|' không thể xuất hiện vì đã bị tách thành field từ trước.
bool validPassword(const std::string& s) {
    if (s.empty() || s.size() > 64) return false;
    for (unsigned char ch : s) {
        if (ch < 0x21 || ch > 0x7E) return false;
    }
    return true;
}

}  // namespace

// 1. Register: 12 -> 10 -> 11 -> 3 -> 0. Thành công vẫn ở CONNECTED.
void Hub::handleRegi(Session& s, const proto::Message& msg, Out& out) {
    const std::string& user = msg.fields[0];
    const std::string& pass = msg.fields[1];
    if (!validName(user) || !validPassword(pass)) {
        reply(s.conn, msg.cmd, ErrorCode::BAD_USERNAME_PASSWORD, out);
        return;
    }
    // Tài khoản mới có Elo 1000 cho cả caro và reversi.
    if (!users_.create(user, pass)) {
        reply(s.conn, msg.cmd, ErrorCode::EXISTING_USER, out);
        return;
    }
    reply(s.conn, msg.cmd, ErrorCode::OK, out);
}

// 2. Log in: 12 -> 10 -> 11 -> 1 -> 2 -> 0.
void Hub::handleLogi(Session& s, const proto::Message& msg, Out& out) {
    const std::string& user = msg.fields[0];
    const std::string& pass = msg.fields[1];
    if (!validName(user) || !validPassword(pass)) {
        reply(s.conn, msg.cmd, ErrorCode::BAD_USERNAME_PASSWORD, out);
        return;
    }
    // Kiểm tra mật khẩu trước (mã 1) rồi mới tới "đang online" (mã 2), để người
    // không biết mật khẩu không dò được tài khoản nào đang online.
    if (!users_.checkPassword(user, pass)) {
        reply(s.conn, msg.cmd, ErrorCode::INCORRECT_USERNAME_PASSWORD, out);
        return;
    }
    if (reg_.findByName(user)) {
        reply(s.conn, msg.cmd, ErrorCode::ALREADY_LOGGED_IN, out);
        return;
    }
    s.username = user;
    s.state = State::LOBBY;
    reg_.setOnline(user, s.conn);
    reply(s.conn, msg.cmd, ErrorCode::OK, out);
}

// 3. Log out: 12 -> 10 -> 0. Kết nối TCP vẫn mở.
void Hub::handleLogu(Session& s, const proto::Message& msg, Out& out) {
    cancelInvitesOf(s, out);  // phải gọi khi username còn nguyên (IDCL mang theo tên)
    reg_.setOffline(s.username);
    s.username.clear();
    s.state = State::CONNECTED;
    reply(s.conn, msg.cmd, ErrorCode::OK, out);
}

}  // namespace core
