// server/src/core/play.cpp  -- Scenario 4: MOVE (13), RSGN (14), End of Game (15)
#include <string>

#include "../store/Elo.h"
#include "Hub.h"
#include "Util.h"

namespace core {

namespace P = proto::Push;

namespace {

constexpr ConnId kNoConn = -1;

// Danh sách quân bị lật: "x1,y1;x2,y2;..." (rỗng nếu không có, vd caro).
// Không gồm ô vừa đặt quân (Appendix A, A8).
std::string formatFlips(const std::vector<game::Pos>& flips) {
    std::string s;
    for (const auto& f : flips) {
        if (!s.empty()) s += ';';
        s += std::to_string(f.row) + "," + std::to_string(f.col);
    }
    return s;
}

// <delta>: "+16", "-16" hoặc "0" (Appendix A, A9).
std::string formatDelta(int d) { return d > 0 ? "+" + std::to_string(d) : std::to_string(d); }

// Chỗ DUY NHẤT ánh xạ lỗi của game sang mã protocol.
ErrorCode toCode(game::MoveError e) {
    switch (e) {
        case game::MoveError::OutOfRange: return ErrorCode::OUT_OF_BOUNDS;
        case game::MoveError::Occupied: return ErrorCode::CELL_OCCUPIED;
        case game::MoveError::IllegalMove: return ErrorCode::ILLEGAL_MOVE;
        case game::MoveError::None: break;
    }
    return ErrorCode::OK;
}

}  // namespace

// 13. Make a Move: MOVE|<x>,<y>  (x = hàng, y = cột, đếm từ 0)
// Thứ tự kiểm tra: state (12) và số field (10) do bảng lệnh làm; ở đây tiếp tục:
// đúng hai trường x và y (10) -> đúng lượt (13) -> x, y là số nguyên trong bàn (14)
// -> ô trống (15) -> hợp luật game (16).
// Thành công: người đi nhận MOVE|0|<k>|<flips>, đối thủ nhận OPPM|<x>,<y>|<k>|<flips>.
void Hub::handleMove(Session& s, const proto::Message& msg, Out& out) {
    Match* m = reg_.findMatch(s.matchId);
    if (!m || m->ended) {
        reply(s.conn, msg.cmd, ErrorCode::BAD_STATE, out);
        return;
    }

    // "Exactly two fields": đúng một dấu phẩy (Appendix A, A7).
    const std::string& pos = msg.fields[0];
    const auto comma = pos.find(',');
    if (comma == std::string::npos || pos.find(',', comma + 1) != std::string::npos) {
        reply(s.conn, msg.cmd, ErrorCode::BAD_FIELDS, out);
        return;
    }

    const int me = m->playerNo(s.conn);
    if (m->engine->currentPlayer() != me) {
        reply(s.conn, msg.cmd, ErrorCode::NOT_IN_TURN, out);
        return;
    }

    int r = 0, c = 0;
    if (!parseInt(pos.substr(0, comma), r) || !parseInt(pos.substr(comma + 1), c)) {
        reply(s.conn, msg.cmd, ErrorCode::OUT_OF_BOUNDS, out);
        return;
    }

    const game::MoveResult res = m->engine->applyMove(me, {r, c});
    if (res.error != game::MoveError::None) {
        reply(s.conn, msg.cmd, toCode(res.error), out);
        return;
    }

    const std::string k = std::to_string(res.flips.size());
    const std::string flips = formatFlips(res.flips);
    const ConnId opp = m->players[me == 1 ? 1 : 0];
    reply(s.conn, msg.cmd, ErrorCode::OK, out, {k, flips});
    push(opp, P::OPPM, {std::to_string(r) + "," + std::to_string(c), k, flips}, out);

    if (res.status == game::Status::Win) {
        finishMatch(*m, res.winner, kNoConn, out);
    } else if (res.status == game::Status::Draw) {
        finishMatch(*m, 0, kNoConn, out);
    } else if (res.opponentSkipped) {
        // Reversi: người sắp đi không có nước hợp lệ. SKIP|<side bị mất lượt> cho cả hai,
        // lượt quay về người vừa đi.
        const std::string skipped = std::to_string(3 - me);
        push(s.conn, P::SKIP, {skipped}, out);
        push(opp, P::SKIP, {skipped}, out);
    }
    // Từ đây `m` có thể đã bị xóa, không dùng nữa.
}

// 14. Resign: người gửi nhận RSGN|0, rồi cả hai nhận OVER.
void Hub::handleRsgn(Session& s, const proto::Message& msg, Out& out) {
    Match* m = reg_.findMatch(s.matchId);
    if (!m || m->ended) {
        reply(s.conn, msg.cmd, ErrorCode::BAD_STATE, out);
        return;
    }
    reply(s.conn, msg.cmd, ErrorCode::OK, out);
    finishMatch(*m, 3 - m->playerNo(s.conn), kNoConn, out);
}

// 15. End of Game, chạy đúng một lần cho mỗi trận. Dùng cho thắng/hòa, RSGN và rớt mạng.
// Thứ tự: đánh dấu ended -> tính và lưu Elo của game vừa chơi -> cả hai về LOBBY,
// xóa trận và phòng của nó -> push OVER|<result>|<delta>|<newRating> cho từng người còn kết nối.
void Hub::finishMatch(Match& m, int winnerNo, ConnId exclude, Out& out) {
    if (m.ended) return;
    m.ended = true;
    const int matchId = m.id;
    const int roomId = m.roomId;
    const std::string game = m.gameType;

    Session* a = reg_.findSession(m.players[0]);  // bên 1 = A trong công thức Elo
    Session* b = reg_.findSession(m.players[1]);
    if (a && b) {
        const double scoreA = winnerNo == 0 ? 0.5 : (winnerNo == 1 ? 1.0 : 0.0);
        const store::EloResult e = store::updateElo(users_.elo(a->username, game),
                                                    users_.elo(b->username, game), scoreA);
        users_.setElo(a->username, game, e.newA);
        users_.setElo(b->username, game, e.newB);

        for (Session* s : {a, b}) {
            s->state = State::LOBBY;
            s->matchId = 0;
            if (s->roomId == roomId) s->roomId = 0;  // chủ phòng của trận này
        }

        auto result = [&](int no) -> std::string {
            if (winnerNo == 0) return "DRAW";
            return winnerNo == no ? "WIN" : "LOSE";
        };
        if (a->conn != exclude) {
            push(a->conn, P::OVER, {result(1), formatDelta(e.delta), std::to_string(e.newA)}, out);
        }
        if (b->conn != exclude) {
            push(b->conn, P::OVER, {result(2), formatDelta(-e.delta), std::to_string(e.newB)}, out);
        }
    }
    if (roomId != 0) reg_.eraseRoom(roomId);
    reg_.eraseMatch(matchId);  // sau dòng này `m` không còn hợp lệ
}

}  // namespace core
