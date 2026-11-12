// server/src/core/info.cpp  -- PLST, RANK
#include <charconv>
#include <string>
#include <system_error>

#include "Hub.h"

namespace core {

namespace {

bool parseInt(const std::string& s, int& v) {
    const char* end = s.data() + s.size();
    auto [p, ec] = std::from_chars(s.data(), end, v);
    return ec == std::errc() && p == end;
}

}  // namespace

// PLST: danh sách người đang ở LOBBY (những người có thể mời), trừ chính mình.
// Reply: PLST|0|name1,name2,...   (field cuối rỗng nếu không có ai)
void Hub::handlePlst(Session& s, const proto::Message& msg, Out& out) {
    std::string list;
    for (const Session* p : reg_.lobbyPlayers()) {
        if (p->conn == s.conn) continue;
        if (!list.empty()) list += ',';
        list += p->username;
    }
    reply(s.conn, msg.cmd, ErrorCode::OK, out, {list});
}

// RANK|n: n người điểm cao nhất (1..100).
// Reply: RANK|0|name1:elo1,name2:elo2,...
void Hub::handleRank(Session& s, const proto::Message& msg, Out& out) {
    int n = 0;
    if (!parseInt(msg.fields[0], n) || n < 1 || n > 100) {
        reply(s.conn, msg.cmd, ErrorCode::BAD_FIELDS, out);
        return;
    }
    std::string list;
    for (const store::User& u : users_.top(static_cast<std::size_t>(n))) {
        if (!list.empty()) list += ',';
        list += u.name + ":" + std::to_string(u.elo);
    }
    reply(s.conn, msg.cmd, ErrorCode::OK, out, {list});
}

}  // namespace core