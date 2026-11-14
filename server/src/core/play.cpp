// server/src/core/play.cpp  -- MOVE, RSGN + finishMatch
#include <charconv>
#include <string>
#include <system_error>

#include "../store/Elo.h"
#include "Hub.h"

namespace core {

namespace {

constexpr ConnId kNoConn = -1;

bool parseInt(const std::string& s, int& v) {
    const char* end = s.data() + s.size();
    auto [p, ec] = std::from_chars(s.data(), end, v);
    return ec == std::errc() && p == end;
}

// "r,c" (0-based)
bool parsePos(const std::string& s, int& r, int& c) {
    const auto comma = s.find(',');
    if (comma == std::string::npos) return false;
    return parseInt(s.substr(0, comma), r) && parseInt(s.substr(comma + 1), c);
}

// Danh sách quân bị lật: "r,c;r,c;..." (rỗng nếu không có, vd Gomoku)
std::string formatFlips(const std::vector<game::Pos>& flips) {
    std::string s;
    for (const auto& f : flips) {
        if (!s.empty()) s += ';';
        s += std::to_string(f.row) + "," + std::to_string(f.col);
    }
    return s;
}

// Chỗ DUY NHẤT ánh xạ lỗi của game sang mã protocol.
ErrorCode toCode(game::MoveError e) {
    switch (e) {
        case game::MoveError::OutOfRange: return ErrorCode::OUT_OF_RANGE;
        case game::MoveError::Occupied: return ErrorCode::OCCUPIED;
        case game::MoveError::IllegalMove: return ErrorCode::ILLEGAL_MOVE;
        case game::MoveError::None: break;
    }
    return ErrorCode::OK;
}

}  // namespace

// MOVE|r,c
// Thứ tự kiểm tra: state (12) và số field (10) do bảng lệnh làm; ở đây tiếp tục:
// định dạng r,c (10), đúng lượt (13), rồi luật game (14/15/16).
// Thành công: người đi nhận MOVE|0|flips, đối thủ nhận OPPM|r,c|flips.
void Hub::handleMove(Session& s, const proto::Message& msg, Out& out) {
    Match* m = reg_.findMatch(s.matchId);
    if (!m || m->ended) {
        reply(s.conn, msg.cmd, ErrorCode::BAD_STATE, out);
        return;
    }

    int r = 0, c = 0;
    if (!parsePos(msg.fields[0], r, c)) {
        reply(s.conn, msg.cmd, ErrorCode::BAD_FIELDS, out);
        return;
    }

    const int me = m->playerNo(s.conn);
    if (m->engine->currentPlayer() != me) {
        reply(s.conn, msg.cmd, ErrorCode::NOT_YOUR_TURN, out);
        return;
    }

    const game::MoveResult res = m->engine->applyMove(me, {r, c});
    if (res.error != game::MoveError::None) {
        reply(s.conn, msg.cmd, toCode(res.error), out);
        return;
    }

    const std::string flips = formatFlips(res.flips);
    const ConnId opp = m->players[me == 1 ? 1 : 0];
    reply(s.conn, msg.cmd, ErrorCode::OK, out, {flips});
    push(opp, "OPPM", {std::to_string(r) + "," + std::to_string(c), flips}, out);

    if (res.opponentSkipped) {  // Reversi: đối thủ hết nước, người vừa đi đi tiếp
        push(opp, "SKIP", {}, out);
        push(s.conn, "SKIP", {}, out);
    }

    if (res.status == game::Status::Win) {
        finishMatch(*m, res.winner, kNoConn, out);
    } else if (res.status == game::Status::Draw) {
        finishMatch(*m, 0, kNoConn, out);
    }
    // Từ đây `m` có thể đã bị xóa, không dùng nữa.
}

// RSGN: đầu hàng. Người gửi nhận RSGN|0, rồi cả hai nhận OVER.
void Hub::handleRsgn(Session& s, const proto::Message& msg, Out& out) {
    Match* m = reg_.findMatch(s.matchId);
    if (!m || m->ended) {
        reply(s.conn, msg.cmd, ErrorCode::BAD_STATE, out);
        return;
    }
    reply(s.conn, msg.cmd, ErrorCode::OK, out);
    finishMatch(*m, 3 - m->playerNo(s.conn), kNoConn, out);
}

// Kết thúc trận đúng một lần, theo thứ tự: đánh dấu ended -> tính Elo -> về LOBBY
// -> push OVER -> xóa trận. Dùng cho thắng/hòa, RSGN và rớt mạng.
// OVER|WIN|LOSE|DRAW|eloMoi
void Hub::finishMatch(Match& m, int winnerNo, ConnId exclude, Out& out) {
    if (m.ended) return;
    m.ended = true;
    const int matchId = m.id;

    Session* a = reg_.findSession(m.players[0]);
    Session* b = reg_.findSession(m.players[1]);
    if (a && b) {
        const double scoreA = winnerNo == 0 ? 0.5 : (winnerNo == 1 ? 1.0 : 0.0);
        const store::EloResult e =
            store::updateElo(users_.elo(a->username), users_.elo(b->username), scoreA);
        users_.setElo(a->username, e.newA);
        users_.setElo(b->username, e.newB);

        a->state = State::LOBBY;
        a->matchId = 0;
        b->state = State::LOBBY;
        b->matchId = 0;

        auto result = [&](int no) -> std::string {
            if (winnerNo == 0) return "DRAW";
            return winnerNo == no ? "WIN" : "LOSE";
        };
        if (a->conn != exclude) push(a->conn, "OVER", {result(1), std::to_string(e.newA)}, out);
        if (b->conn != exclude) push(b->conn, "OVER", {result(2), std::to_string(e.newB)}, out);
    }
    reg_.eraseMatch(matchId);  // sau dòng này `m` không còn hợp lệ
}

}  // namespace core