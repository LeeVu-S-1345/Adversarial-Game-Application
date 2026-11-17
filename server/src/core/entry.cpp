// server/src/core/entry.cpp  -- Scenario 3: CROM (6), INVT (7), IACC (8), IDEC (9),
//                               DROM (10), RAND (11), RCAN (12) + startMatch
#include <string>
#include <utility>

#include "../game/GameFactory.h"
#include "Hub.h"
#include "Util.h"

namespace core {

namespace P = proto::Push;

// Tạo trận: cả hai vào IN_GAME, mỗi người nhận một STRT.
// STRT|<game>|<side>|<opponent>|<rows>,<cols>   (bên 1 đi trước)
void Hub::startMatch(ConnId p1, ConnId p2, const std::string& gameType, int roomId, Out& out) {
    Session& s1 = *reg_.findSession(p1);
    Session& s2 = *reg_.findSession(p2);
    Match& m = reg_.createMatch(gameType, p1, p2, game::createGame(gameType), roomId);
    const std::string size =
        std::to_string(m.engine->rows()) + "," + std::to_string(m.engine->cols());
    for (Session* s : {&s1, &s2}) {
        s->state = State::IN_GAME;
        s->matchId = m.id;
        s->queuedGame.clear();
    }
    push(p1, P::STRT, {gameType, "1", s2.username, size}, out);
    push(p2, P::STRT, {gameType, "2", s1.username, size}, out);
}

// ---- Phòng và lời mời ----

// 6. Create Room: 12 -> 10 -> 4 -> 0. Reply: CROM|0|<roomId>
void Hub::handleCrom(Session& s, const proto::Message& msg, Out& out) {
    const std::string& type = msg.fields[0];
    if (!game::isKnownGame(type)) {
        reply(s.conn, msg.cmd, ErrorCode::GAME_NOT_FOUND, out);
        return;
    }
    cancelInvitesOf(s, out);  // rời LOBBY thì không còn nhận lời được nữa (use case 9)
    Room& room = reg_.createRoom(s.conn, type);
    s.roomId = room.id;
    s.state = State::IN_ROOM;
    reply(s.conn, msg.cmd, ErrorCode::OK, out, {std::to_string(room.id)});
}

// 7. Invite Player: 12 -> 10 -> 9 -> 5 -> 8 -> 6 -> 0.
// Người được mời nhận: INVI|<roomId>|<creator>|<game>
void Hub::handleInvt(Session& s, const proto::Message& msg, Out& out) {
    const std::string& name = msg.fields[0];
    Room* room = reg_.findRoom(s.roomId);
    if (!room) {  // không xảy ra: ở IN_ROOM thì luôn có phòng
        reply(s.conn, msg.cmd, ErrorCode::BAD_STATE, out);
        return;
    }
    if (name == s.username) {
        reply(s.conn, msg.cmd, ErrorCode::CAN_NOT_INVITE_SELF, out);
        return;
    }
    Session* target = reg_.findByName(name);
    if (!target) {
        reply(s.conn, msg.cmd, ErrorCode::INVITEE_NOT_ONLINE, out);
        return;
    }
    if (reg_.hasInvitation(room->id, target->conn)) {
        reply(s.conn, msg.cmd, ErrorCode::INVITATION_ALREADY_SENT, out);
        return;
    }
    if (target->state != State::LOBBY) {
        reply(s.conn, msg.cmd, ErrorCode::INVITEE_BUSY, out);
        return;
    }
    reg_.addInvitation(room->id, target->conn);
    reply(s.conn, msg.cmd, ErrorCode::OK, out);
    push(target->conn, P::INVI, {std::to_string(room->id), s.username, room->gameType}, out);
}

// 8. Join Room / Accept Invitation: 12 -> 10 -> 7 -> 0.
// Chủ phòng là bên 1, người nhận lời là bên 2.
void Hub::handleIacc(Session& s, const proto::Message& msg, Out& out) {
    int roomId = 0;
    // roomId không phải số cũng là "không có lời mời" (Appendix A, A6).
    if (!parseInt(msg.fields[0], roomId) || !reg_.removeInvitation(roomId, s.conn)) {
        reply(s.conn, msg.cmd, ErrorCode::INVITATION_NOT_FOUND, out);
        return;
    }
    Room* room = reg_.findRoom(roomId);
    Session* host = room ? reg_.findSession(room->host) : nullptr;
    if (!host || host->state != State::IN_ROOM) {  // không xảy ra nếu invariant đúng
        reply(s.conn, msg.cmd, ErrorCode::INVITATION_NOT_FOUND, out);
        return;
    }
    const std::string type = room->gameType;
    const ConnId hostConn = host->conn;

    // "As one atomic step": xóa mọi lời mời khác của phòng (những người đó không
    // được báo), hủy các lời mời khác gửi tới người nhận lời (chủ các phòng đó
    // nhận IDCL), rồi đưa cả hai vào IN_GAME. Phòng còn tồn tại tới hết trận.
    reg_.dropInvitesOf(roomId);
    cancelInvitesOf(s, out);

    reply(s.conn, msg.cmd, ErrorCode::OK, out);
    startMatch(hostConn, s.conn, type, roomId, out);
}

// 9. Decline Invitation: 12 -> 10 -> 7 -> 0. Chủ phòng nhận IDCL|<invitee>.
void Hub::handleIdec(Session& s, const proto::Message& msg, Out& out) {
    int roomId = 0;
    if (!parseInt(msg.fields[0], roomId) || !reg_.removeInvitation(roomId, s.conn)) {
        reply(s.conn, msg.cmd, ErrorCode::INVITATION_NOT_FOUND, out);
        return;
    }
    reply(s.conn, msg.cmd, ErrorCode::OK, out);
    if (Room* room = reg_.findRoom(roomId)) {
        push(room->host, P::IDCL, {s.username}, out);
    }
}

// 10. Leave Room: 12 -> 10 -> 0. Người được mời không được báo.
void Hub::handleDrom(Session& s, const proto::Message& msg, Out& out) {
    closeRoomOf(s);
    s.state = State::LOBBY;
    reply(s.conn, msg.cmd, ErrorCode::OK, out);
}

// ---- Ghép ngẫu nhiên ----

// 11. Match Randomly: 12 -> 10 -> 4 -> 0.
void Hub::handleRand(Session& s, const proto::Message& msg, Out& out) {
    const std::string& type = msg.fields[0];
    if (!game::isKnownGame(type)) {
        reply(s.conn, msg.cmd, ErrorCode::GAME_NOT_FOUND, out);
        return;
    }
    cancelInvitesOf(s, out);  // rời LOBBY thì không còn nhận lời được nữa (use case 9)

    // Reply trước để người gửi nhận RAND|0 rồi mới tới STRT.
    reply(s.conn, msg.cmd, ErrorCode::OK, out);

    auto& q = reg_.queue(type);
    if (q.empty()) {
        q.push_back(s.conn);
        s.state = State::MATCHING;
        s.queuedGame = type;
    } else {
        const ConnId waiting = q.front();
        q.pop_front();
        startMatch(waiting, s.conn, type, 0, out);  // người chờ trước là bên 1
    }
}

// 12. Cancel Matching: 12 -> 10 -> 0.
void Hub::handleRcan(Session& s, const proto::Message& msg, Out& out) {
    reg_.leaveQueue(s);
    s.state = State::LOBBY;
    reply(s.conn, msg.cmd, ErrorCode::OK, out);
}

}  // namespace core
