// server/src/core/entry.cpp  -- RAND, RCAN, CROM, INVT, IACC, IDEC, DCAN + startMatch
#include <string>
#include <utility>

#include "../game/GameFactory.h"
#include "Hub.h"

namespace core {

namespace {

bool knownGame(const std::string& type) { return game::createGame(type) != nullptr; }

}  // namespace

// Tạo trận: cả hai vào IN_GAME, mỗi người nhận một STRT.
// STRT|gameType|soNguoiChoi|tenDoiThu   (người 1 đi trước)
void Hub::startMatch(ConnId p1, ConnId p2, const std::string& gameType, Out& out) {
    Session& s1 = *reg_.findSession(p1);
    Session& s2 = *reg_.findSession(p2);
    Match& m = reg_.createMatch(gameType, p1, p2, game::createGame(gameType));
    for (Session* s : {&s1, &s2}) {
        s->state = State::IN_GAME;
        s->matchId = m.id;
        s->queuedGame.clear();
    }
    push(p1, "STRT", {gameType, "1", s2.username}, out);
    push(p2, "STRT", {gameType, "2", s1.username}, out);
}

// ---- Ghép ngẫu nhiên ----

void Hub::handleRand(Session& s, const proto::Message& msg, Out& out) {
    const std::string& type = msg.fields[0];
    if (!knownGame(type)) {
        reply(s.conn, msg.cmd, ErrorCode::UNKNOWN_GAME, out);
        return;
    }
    cancelInvitesOf(s, out);  // rời LOBBY thì không còn nhận lời mời

    // Reply trước để client nhận RAND|0 rồi mới tới STRT.
    reply(s.conn, msg.cmd, ErrorCode::OK, out);

    auto& q = reg_.queue(type);
    if (q.empty()) {
        q.push_back(s.conn);
        s.state = State::MATCHING;
        s.queuedGame = type;
    } else {
        const ConnId waiting = q.front();
        q.pop_front();
        startMatch(waiting, s.conn, type, out);  // người chờ trước đi trước
    }
}

void Hub::handleRcan(Session& s, const proto::Message& msg, Out& out) {
    reg_.leaveQueue(s);
    s.state = State::LOBBY;
    reply(s.conn, msg.cmd, ErrorCode::OK, out);
}

// ---- Tạo phòng và mời ----

void Hub::handleCrom(Session& s, const proto::Message& msg, Out& out) {
    const std::string& type = msg.fields[0];
    if (!knownGame(type)) {
        reply(s.conn, msg.cmd, ErrorCode::UNKNOWN_GAME, out);
        return;
    }
    cancelInvitesOf(s, out);
    reg_.createRoom(s.conn, type);
    s.state = State::IN_ROOM;
    reply(s.conn, msg.cmd, ErrorCode::OK, out);
}

// INVT|tenNguoiDuocMoi. Người được mời nhận: INVI|tenHost|gameType
void Hub::handleInvt(Session& s, const proto::Message& msg, Out& out) {
    Room* room = reg_.findRoom(s.conn);
    Session* target = reg_.findByName(msg.fields[0]);
    if (!room || !target || target->state != State::LOBBY || target->conn == s.conn) {
        reply(s.conn, msg.cmd, ErrorCode::INVITEE_UNAVAILABLE, out);
        return;
    }
    if (!reg_.hasInvitation(s.conn, target->conn)) {  // mời lặp lại thì không gửi trùng
        reg_.addInvitation(s.conn, target->conn);
        push(target->conn, "INVI", {s.username, room->gameType}, out);
    }
    reply(s.conn, msg.cmd, ErrorCode::OK, out);
}

// IACC|tenHost: nhận lời mời, vào trận ngay. Host là người 1 (đi trước).
void Hub::handleIacc(Session& s, const proto::Message& msg, Out& out) {
    Session* host = reg_.findByName(msg.fields[0]);
    if (!host || !reg_.removeInvitation(host->conn, s.conn)) {
        reply(s.conn, msg.cmd, ErrorCode::NO_INVITATION, out);
        return;
    }
    Room* room = reg_.findRoom(host->conn);
    if (!room || host->state != State::IN_ROOM) {
        reply(s.conn, msg.cmd, ErrorCode::NO_INVITATION, out);
        return;
    }
    const std::string type = room->gameType;

    cancelInvitesOf(s, out);              // hủy các lời mời khác gửi tới mình
    reg_.takeInvitesFrom(host->conn);     // lời mời khác của host cũng hết hiệu lực
    reg_.eraseRoom(host->conn);

    reply(s.conn, msg.cmd, ErrorCode::OK, out);
    startMatch(host->conn, s.conn, type, out);
}

// IDEC|tenHost: từ chối. Host nhận IDCL|tenNguoiTuChoi.
void Hub::handleIdec(Session& s, const proto::Message& msg, Out& out) {
    Session* host = reg_.findByName(msg.fields[0]);
    if (!host || !reg_.removeInvitation(host->conn, s.conn)) {
        reply(s.conn, msg.cmd, ErrorCode::NO_INVITATION, out);
        return;
    }
    reply(s.conn, msg.cmd, ErrorCode::OK, out);
    push(host->conn, "IDCL", {s.username}, out);
}

// DCAN: host hủy phòng, về LOBBY.
void Hub::handleDcan(Session& s, const proto::Message& msg, Out& out) {
    reg_.takeInvitesFrom(s.conn);
    reg_.eraseRoom(s.conn);
    s.state = State::LOBBY;
    reply(s.conn, msg.cmd, ErrorCode::OK, out);
}

}  // namespace core