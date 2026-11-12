// server/src/core/Registry.h
#pragma once
#include <deque>
#include <memory>
#include <string>
#include <unordered_map>
#include <vector>

#include "../game/IGame.h"
#include "../proto/Codes.h"

namespace core {

using ConnId = int;
using proto::State;

struct Session {
    ConnId conn = 0;
    State state = State::CONNECTED;
    std::string username;    // rỗng nếu chưa login
    std::string queuedGame;  // game đang chờ ghép (khi MATCHING)
    int matchId = 0;         // 0 nếu không ở trong trận
};

struct Room {
    ConnId host;
    std::string gameType;
};

struct Invitation {
    ConnId host;
    ConnId invitee;
};

struct Match {
    int id = 0;
    std::string gameType;
    ConnId players[2] = {0, 0};  // players[0] là người 1 (đi trước)
    std::unique_ptr<game::IGame> engine;
    bool ended = false;

    int playerNo(ConnId c) const { return players[0] == c ? 1 : 2; }
};

// Chỉ giữ dữ liệu và các thao tác tra cứu/sửa đơn giản; không biết protocol,
// không sinh Out. Luật nằm ở Hub và các handler.
class Registry {
public:
    // Session
    Session& session(ConnId conn);  // tạo mới nếu chưa có
    Session* findSession(ConnId conn);
    Session* findByName(const std::string& username);  // chỉ người đã login
    void setOnline(const std::string& username, ConnId conn);
    void setOffline(const std::string& username);
    void eraseSession(ConnId conn);
    std::vector<const Session*> lobbyPlayers() const;

    // Hàng đợi RAND theo từng game
    std::deque<ConnId>& queue(const std::string& gameType);
    void leaveQueue(Session& s);

    // Phòng (khóa là conn của host)
    Room& createRoom(ConnId host, const std::string& gameType);
    Room* findRoom(ConnId host);
    void eraseRoom(ConnId host);

    // Lời mời
    bool hasInvitation(ConnId host, ConnId invitee) const;
    void addInvitation(ConnId host, ConnId invitee);
    bool removeInvitation(ConnId host, ConnId invitee);       // true nếu có để xóa
    std::vector<ConnId> takeInvitesTo(ConnId invitee);        // xóa hết, trả danh sách host
    std::vector<ConnId> takeInvitesFrom(ConnId host);         // xóa hết, trả danh sách invitee

    // Trận đấu
    Match& createMatch(const std::string& gameType, ConnId p1, ConnId p2,
                       std::unique_ptr<game::IGame> engine);
    Match* findMatch(int id);
    void eraseMatch(int id);

private:
    std::unordered_map<ConnId, Session> sessions_;
    std::unordered_map<std::string, ConnId> names_;  // username -> conn (người đang online)
    std::unordered_map<std::string, std::deque<ConnId>> queues_;
    std::unordered_map<ConnId, Room> rooms_;
    std::vector<Invitation> invites_;
    std::unordered_map<int, Match> matches_;
    int nextMatchId_ = 1;
};

}  // namespace core