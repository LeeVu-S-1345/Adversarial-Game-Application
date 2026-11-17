// server/src/core/Registry.cpp
#include "Registry.h"

#include <algorithm>

namespace core {

// ---- Session ----

Session& Registry::session(ConnId conn) {
    auto [it, inserted] = sessions_.try_emplace(conn);
    if (inserted) it->second.conn = conn;
    return it->second;
}

Session* Registry::findSession(ConnId conn) {
    auto it = sessions_.find(conn);
    return it == sessions_.end() ? nullptr : &it->second;
}

Session* Registry::findByName(const std::string& username) {
    auto it = names_.find(username);
    return it == names_.end() ? nullptr : findSession(it->second);
}

void Registry::setOnline(const std::string& username, ConnId conn) { names_[username] = conn; }

void Registry::setOffline(const std::string& username) { names_.erase(username); }

void Registry::eraseSession(ConnId conn) {
    auto it = sessions_.find(conn);
    if (it == sessions_.end()) return;
    if (!it->second.username.empty()) names_.erase(it->second.username);
    sessions_.erase(it);
}

std::vector<const Session*> Registry::onlinePlayers() const {
    std::vector<const Session*> result;
    for (const auto& [conn, s] : sessions_) {
        if (s.state != State::CONNECTED) result.push_back(&s);
    }
    return result;
}

// ---- Hàng đợi ----

std::deque<ConnId>& Registry::queue(const std::string& gameType) { return queues_[gameType]; }

void Registry::leaveQueue(Session& s) {
    if (s.queuedGame.empty()) return;
    auto& q = queues_[s.queuedGame];
    q.erase(std::remove(q.begin(), q.end(), s.conn), q.end());
    s.queuedGame.clear();
}

// ---- Phòng ----

Room& Registry::createRoom(ConnId host, const std::string& gameType) {
    const int id = nextRoomId_++;
    Room& r = rooms_[id];
    r.id = id;
    r.host = host;
    r.gameType = gameType;
    return r;
}

Room* Registry::findRoom(int roomId) {
    auto it = rooms_.find(roomId);
    return it == rooms_.end() ? nullptr : &it->second;
}

void Registry::eraseRoom(int roomId) { rooms_.erase(roomId); }

// ---- Lời mời ----

bool Registry::hasInvitation(int roomId, ConnId invitee) const {
    return std::any_of(invites_.begin(), invites_.end(), [&](const Invitation& i) {
        return i.roomId == roomId && i.invitee == invitee;
    });
}

void Registry::addInvitation(int roomId, ConnId invitee) { invites_.push_back({roomId, invitee}); }

bool Registry::removeInvitation(int roomId, ConnId invitee) {
    for (auto it = invites_.begin(); it != invites_.end(); ++it) {
        if (it->roomId == roomId && it->invitee == invitee) {
            invites_.erase(it);
            return true;
        }
    }
    return false;
}

std::vector<int> Registry::takeInvitesTo(ConnId invitee) {
    std::vector<int> rooms;
    for (auto it = invites_.begin(); it != invites_.end();) {
        if (it->invitee == invitee) {
            rooms.push_back(it->roomId);
            it = invites_.erase(it);
        } else {
            ++it;
        }
    }
    return rooms;
}

void Registry::dropInvitesOf(int roomId) {
    invites_.erase(std::remove_if(invites_.begin(), invites_.end(),
                                  [&](const Invitation& i) { return i.roomId == roomId; }),
                   invites_.end());
}

// ---- Trận đấu ----

Match& Registry::createMatch(const std::string& gameType, ConnId p1, ConnId p2,
                             std::unique_ptr<game::IGame> engine, int roomId) {
    const int id = nextMatchId_++;
    Match& m = matches_[id];
    m.id = id;
    m.gameType = gameType;
    m.players[0] = p1;
    m.players[1] = p2;
    m.engine = std::move(engine);
    m.roomId = roomId;
    return m;
}

Match* Registry::findMatch(int id) {
    auto it = matches_.find(id);
    return it == matches_.end() ? nullptr : &it->second;
}

void Registry::eraseMatch(int id) { matches_.erase(id); }

}  // namespace core
