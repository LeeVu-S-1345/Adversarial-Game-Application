// server/src/core/auth.cpp  -- REGI, LOGI, LOGU
#include <cctype>
#include <string>

#include "Hub.h"

namespace core {

namespace {

// Tên: chữ, số, gạch dưới, 1..32 ký tự (an toàn cho file lưu user và cho protocol).
bool validName(const std::string& s) {
    if (s.empty() || s.size() > 32) return false;
    for (unsigned char ch : s) {
        if (!std::isalnum(ch) && ch != '_') return false;
    }
    return true;
}

// Mật khẩu: không rỗng, không chứa xuống dòng (sẽ làm hỏng file lưu user).
// Ký tự '|' không thể xuất hiện vì đã bị tách thành field từ trước.
bool validPassword(const std::string& s) {
    return !s.empty() && s.find_first_of("\r\n") == std::string::npos;
}

}  // namespace

void Hub::handleRegi(Session& s, const proto::Message& msg, Out& out) {
    const std::string& user = msg.fields[0];
    const std::string& pass = msg.fields[1];
    if (!validName(user) || !validPassword(pass)) {
        reply(s.conn, msg.cmd, ErrorCode::BAD_FIELDS, out);
        return;
    }
    if (!users_.create(user, pass)) {
        reply(s.conn, msg.cmd, ErrorCode::USERNAME_TAKEN, out);
        return;
    }
    reply(s.conn, msg.cmd, ErrorCode::OK, out);
}

void Hub::handleLogi(Session& s, const proto::Message& msg, Out& out) {
    const std::string& user = msg.fields[0];
    const std::string& pass = msg.fields[1];
    // Kiểm tra mật khẩu trước để không lộ tài khoản nào đang online.
    if (!users_.checkPassword(user, pass)) {
        reply(s.conn, msg.cmd, ErrorCode::BAD_CREDENTIALS, out);
        return;
    }
    if (reg_.findByName(user)) {
        reply(s.conn, msg.cmd, ErrorCode::ALREADY_ONLINE, out);
        return;
    }
    s.username = user;
    s.state = State::LOBBY;
    reg_.setOnline(user, s.conn);
    reply(s.conn, msg.cmd, ErrorCode::OK, out);
}

void Hub::handleLogu(Session& s, const proto::Message& msg, Out& out) {
    cancelInvitesOf(s, out);  // phải gọi khi username còn nguyên (IDCL mang theo tên)
    reg_.setOffline(s.username);
    s.username.clear();
    s.state = State::CONNECTED;
    reply(s.conn, msg.cmd, ErrorCode::OK, out);
}

}  // namespace core