// server/src/core/Hub.cpp
#include "Hub.h"

#include <algorithm>

namespace core {

namespace C = proto::Cmd;
namespace P = proto::Push;
using S = State;

Hub::Hub(store::UserStore& users) : users_(users) {}

void Hub::initTable() {
    // Bảng lệnh: lệnh -> (state được phép, số field, handler).
    // MOVE có 1 field "x,y"; việc "exactly two fields" (x và y) do handleMove kiểm tra.
    table_ = {
        {C::REGI, {{S::CONNECTED}, 2, &Hub::handleRegi}},
        {C::LOGI, {{S::CONNECTED}, 2, &Hub::handleLogi}},

        {C::LOGU, {{S::LOBBY}, 0, &Hub::handleLogu}},
        {C::PLST, {{S::LOBBY, S::IN_ROOM}, 0, &Hub::handlePlst}},
        {C::RANK, {{S::LOBBY}, 1, &Hub::handleRank}},
        {C::RAND, {{S::LOBBY}, 1, &Hub::handleRand}},
        {C::CROM, {{S::LOBBY}, 1, &Hub::handleCrom}},
        {C::IACC, {{S::LOBBY}, 1, &Hub::handleIacc}},
        {C::IDEC, {{S::LOBBY}, 1, &Hub::handleIdec}},

        {C::RCAN, {{S::MATCHING}, 0, &Hub::handleRcan}},

        {C::INVT, {{S::IN_ROOM}, 1, &Hub::handleInvt}},
        {C::DROM, {{S::IN_ROOM}, 0, &Hub::handleDrom}},

        {C::MOVE, {{S::IN_GAME}, 1, &Hub::handleMove}},
        {C::RSGN, {{S::IN_GAME}, 0, &Hub::handleRsgn}},
    };
    tableReady_ = true;
}

void Hub::reply(ConnId conn, const std::string& cmd, ErrorCode code, Out& out,
                const std::vector<std::string>& extra) {
    std::vector<std::string> fields{std::to_string(static_cast<int>(code))};
    fields.insert(fields.end(), extra.begin(), extra.end());
    out.push_back({conn, proto::format(cmd, fields)});
}

void Hub::push(ConnId conn, const std::string& cmd, const std::vector<std::string>& fields,
               Out& out) {
    out.push_back({conn, proto::format(cmd, fields)});
}

void Hub::onMessage(ConnId conn, const proto::Message& msg, Out& out) {
    if (!tableReady_) initTable();

    Session& s = reg_.session(conn);

    // Thứ tự kiểm tra chung (mục 8): lệnh lạ (10) -> state (12) -> số field (10)
    // -> kiểm tra riêng của lệnh. Chỉ báo lỗi đầu tiên.
    auto it = table_.find(msg.cmd);
    if (it == table_.end()) {
        reply(conn, msg.cmd, ErrorCode::BAD_FIELDS, out);  // Appendix A, A2
        return;
    }
    const CmdSpec& spec = it->second;

    if (std::find(spec.allowedStates.begin(), spec.allowedStates.end(), s.state) ==
        spec.allowedStates.end()) {
        reply(conn, msg.cmd, ErrorCode::BAD_STATE, out);
        return;
    }
    if (msg.fields.size() != spec.nFields) {
        reply(conn, msg.cmd, ErrorCode::BAD_FIELDS, out);
        return;
    }

    (this->*spec.handler)(s, msg, out);
}

// Mất kết nối (mục 12). Không có reply nào cho người đã rớt.
void Hub::onClose(ConnId conn, Out& out) {
    Session* s = reg_.findSession(conn);
    if (!s) return;

    switch (s->state) {
        case S::LOBBY:
            cancelInvitesOf(*s, out);  // các chủ phòng đang mời người này nhận IDCL
            break;
        case S::MATCHING:
            reg_.leaveQueue(*s);
            break;
        case S::IN_ROOM:
            closeRoomOf(*s);  // người được mời không được báo; IACC/IDEC sau đó nhận mã 7
            break;
        case S::IN_GAME:
            // Rớt mạng giữa trận = thua. OVER chỉ gửi cho đối thủ.
            if (Match* m = reg_.findMatch(s->matchId)) {
                finishMatch(*m, 3 - m->playerNo(conn), conn, out);
            }
            break;
        case S::CONNECTED:
            break;
    }
    reg_.eraseSession(conn);
}

void Hub::cancelInvitesOf(Session& invitee, Out& out) {
    for (int roomId : reg_.takeInvitesTo(invitee.conn)) {
        if (Room* room = reg_.findRoom(roomId)) {
            push(room->host, P::IDCL, {invitee.username}, out);
        }
    }
}

void Hub::closeRoomOf(Session& s) {
    if (s.roomId == 0) return;
    reg_.dropInvitesOf(s.roomId);
    reg_.eraseRoom(s.roomId);
    s.roomId = 0;
}

}  // namespace core
