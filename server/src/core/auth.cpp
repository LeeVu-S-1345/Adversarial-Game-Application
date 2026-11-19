// server/src/core/auth.cpp  -- Scenario 1: REGI (1), LOGI (2), LOGU (3), RSPW (16)
#include <cctype>
#include <random>
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

// Recovery code: 8 ký tự lấy từ 32 ký tự dễ đọc (bỏ I, O, 0, 1). docs/protocol.md, use case 16.
constexpr const char* kCodeAlphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

std::string randomRecoveryCode() {
    static std::mt19937 rng{std::random_device{}()};
    std::uniform_int_distribution<int> pick(0, 31);
    std::string code;
    for (int i = 0; i < 8; ++i) code += kCodeAlphabet[pick(rng)];
    return code;
}

// Định dạng hợp lệ của recovery code do client gửi: đúng 8 chữ in hoa hoặc số.
bool validCode(const std::string& s) {
    if (s.size() != 8) return false;
    for (unsigned char ch : s) {
        if (!std::isupper(ch) && !std::isdigit(ch)) return false;
    }
    return true;
}

}  // namespace

std::string Hub::recoveryCode() { return newRecoveryCode_ ? newRecoveryCode_() : randomRecoveryCode(); }

// 1. Register: 12 -> 10 -> 11 -> 3 -> 0. Thành công vẫn ở CONNECTED.
void Hub::handleRegi(Session& s, const proto::Message& msg, Out& out) {
    const std::string& user = msg.fields[0];
    const std::string& pass = msg.fields[1];
    if (!validName(user) || !validPassword(pass)) {
        reply(s.conn, msg.cmd, ErrorCode::BAD_USERNAME_PASSWORD, out);
        return;
    }
    // Tài khoản mới có Elo 1000 cho cả caro và reversi.
    const std::string code = recoveryCode();
    if (!users_.create(user, pass, code)) {
        reply(s.conn, msg.cmd, ErrorCode::EXISTING_USER, out);
        return;
    }
    reply(s.conn, msg.cmd, ErrorCode::OK, out, {code});  // REGI|0|<recoveryCode>
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

// 16. Reset Password: 12 -> 10 -> 11 -> 17 -> 0. Vẫn ở CONNECTED.
// RSPW|<username>|<recoveryCode>|<newPassword>  ->  RSPW|0|<newRecoveryCode>
void Hub::handleRspw(Session& s, const proto::Message& msg, Out& out) {
    const std::string& user = msg.fields[0];
    const std::string& code = msg.fields[1];
    const std::string& pass = msg.fields[2];
    if (!validName(user) || !validCode(code) || !validPassword(pass)) {
        reply(s.conn, msg.cmd, ErrorCode::BAD_USERNAME_PASSWORD, out);
        return;
    }
    // Tên không tồn tại và mã sai trả cùng một mã 17: không lộ tài khoản nào tồn tại.
    // Mỗi mã chỉ dùng được một lần: đổi mật khẩu xong thì cấp mã mới.
    const std::string fresh = recoveryCode();
    if (!users_.resetPassword(user, code, pass, fresh)) {
        reply(s.conn, msg.cmd, ErrorCode::INCORRECT_USERNAME_RECOVERY_CODE, out);
        return;
    }
    reply(s.conn, msg.cmd, ErrorCode::OK, out, {fresh});
}

}  // namespace core
