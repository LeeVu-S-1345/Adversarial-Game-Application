// server/src/core/info.cpp  -- Scenario 2: PLST (4), RANK (5)
#include <algorithm>
#include <string>

#include "../game/GameFactory.h"
#include "Hub.h"

namespace core {

// 4. View Online Players: 12 -> 10 -> 0.
// Reply: PLST|0|<user1>,<status1>;...;<usern>,<statusn>
// Mọi session đã login, GỒM CẢ người gửi, xếp theo username tăng dần (Appendix A, A4).
// FREE nếu ở LOBBY; BUSY nếu ở MATCHING, IN_ROOM, IN_GAME.
void Hub::handlePlst(Session& s, const proto::Message& msg, Out& out) {
    std::vector<const Session*> players = reg_.onlinePlayers();
    std::sort(players.begin(), players.end(),
              [](const Session* a, const Session* b) { return a->username < b->username; });

    std::string list;
    for (const Session* p : players) {
        if (!list.empty()) list += ';';
        list += p->username;
        list += p->state == State::LOBBY ? ",FREE" : ",BUSY";
    }
    reply(s.conn, msg.cmd, ErrorCode::OK, out, {list});
}

// 5. View Player Rankings: 12 -> 10 -> 4 -> 0.
// Reply: RANK|0|<user1>,<rating1>;...;<usern>,<ratingn>
// Mọi tài khoản đã đăng ký, theo Elo của <game> giảm dần; bằng điểm thì theo username.
void Hub::handleRank(Session& s, const proto::Message& msg, Out& out) {
    const std::string& game = msg.fields[0];
    if (!game::isKnownGame(game)) {
        reply(s.conn, msg.cmd, ErrorCode::GAME_NOT_FOUND, out);
        return;
    }
    std::string list;
    for (const store::RankEntry& e : users_.ranking(game)) {
        if (!list.empty()) list += ';';
        list += e.name + "," + std::to_string(e.elo);
    }
    reply(s.conn, msg.cmd, ErrorCode::OK, out, {list});
}

}  // namespace core
