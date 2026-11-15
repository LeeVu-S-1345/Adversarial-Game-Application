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
    int roomId = 0;          // phòng mình làm chủ; 0 nếu không có
    int matchId = 0;         // 0 nếu không ở trong trận
};

struct Room {
    int id = 0;
    ConnId host = 0;
    std::string gameType;
};

// Một lời mời được xác định bởi cặp (phòng, người được mời).
struct Invitation {
    int roomId;
    ConnId invitee;
};

struct Match {
    int id = 0;
    std::string gameType;
    ConnId players[2] = {0, 0};  // players[0] là bên 1 (đi trước)
    std::unique_ptr<game::IGame> engine;
    int roomId = 0;  // phòng của trận; 0 nếu ghép ngẫu nhiên
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
    std::vector<const Session*> onlinePlayers() const;  // mọi session đã login

    // Hàng đợi RAND theo từng game
    std::deque<ConnId>& queue(const std::string& gameType);
    void leaveQueue(Session& s);

    // Phòng (roomId tăng dần từ 1, không dùng lại)
    Room& createRoom(ConnId host, const std::string& gameType);
    Room* findRoom(int roomId);
    void eraseRoom(int roomId);

    // Lời mời
    bool hasInvitation(int roomId, ConnId invitee) const;
    void addInvitation(int roomId, ConnId invitee);
    bool removeInvitation(int roomId, ConnId invitee);  // true nếu có để xóa
    std::vector<int> takeInvitesTo(ConnId invitee);     // xóa hết, trả danh sách roomId
    void dropInvitesOf(int roomId);                     // xóa hết lời mời của một phòng

    // Trận đấu
    Match& createMatch(const std::string& gameType, ConnId p1, ConnId p2,
                       std::unique_ptr<game::IGame> engine, int roomId);
    Match* findMatch(int id);
    void eraseMatch(int id);

private:
    std::unordered_map<ConnId, Session> sessions_;
    std::unordered_map<std::string, ConnId> names_;  // username -> conn (người đang online)
    std::unordered_map<std::string, std::deque<ConnId>> queues_;
    std::unordered_map<int, Room> rooms_;
    std::vector<Invitation> invites_;
    std::unordered_map<int, Match> matches_;
    int nextRoomId_ = 1;
    int nextMatchId_ = 1;
};

}  // namespace core
