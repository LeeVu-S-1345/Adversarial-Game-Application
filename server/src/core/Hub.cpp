// server/src/core/Hub.cpp
#include "Hub.h"

#include <algorithm>

namespace core {

namespace C = proto::Cmd;
using S = State;

Hub::Hub(store::UserStore& users) : users_(users) {}

void Hub::initTable() {
    // Bảng lệnh: lệnh -> (state được phép, số field, handler)
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
        {C::DCAN, {{S::IN_ROOM}, 0, &Hub::handleDcan}},

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

    auto it = table_.find(msg.cmd);
    if (it == table_.end()) {
        reply(conn, msg.cmd, ErrorCode::BAD_FIELDS, out);  // lệnh lạ: chỉnh theo doc nếu có mã riêng
        return;
    }
    const CmdSpec& spec = it->second;

    // Thứ tự kiểm tra: state (12) trước, số field (10) sau.
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

void Hub::onClose(ConnId conn, Out& out) {
    Session* s = reg_.findSession(conn);
    if (!s) return;

    switch (s->state) {
        case S::MATCHING:
            reg_.leaveQueue(*s);
            break;
        case S::IN_ROOM:
            reg_.takeInvitesFrom(conn);
            reg_.eraseRoom(conn);
            break;
        case S::IN_GAME:
            // Rớt mạng giữa trận = đầu hàng. Không reply, OVER chỉ gửi cho đối thủ.
            if (Match* m = reg_.findMatch(s->matchId)) {
                finishMatch(*m, 3 - m->playerNo(conn), conn, out);
            }
            break;
        case S::LOBBY:
            cancelInvitesOf(*s, out);
            break;
        case S::CONNECTED:
            break;
    }
    reg_.eraseSession(conn);
}

void Hub::cancelInvitesOf(Session& invitee, Out& out) {
    for (ConnId host : reg_.takeInvitesTo(invitee.conn)) {
        push(host, "IDCL", {invitee.username}, out);
    }
}

}  // namespace core