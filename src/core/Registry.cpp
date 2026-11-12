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

std::vector<const Session*> Registry::lobbyPlayers() const {
    std::vector<const Session*> result;
    for (const auto& [conn, s] : sessions_) {
        if (s.state == State::LOBBY) result.push_back(&s);
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
    Room& r = rooms_[host];
    r.host = host;
    r.gameType = gameType;
    return r;
}

Room* Registry::findRoom(ConnId host) {
    auto it = rooms_.find(host);
    return it == rooms_.end() ? nullptr : &it->second;
}

void Registry::eraseRoom(ConnId host) { rooms_.erase(host); }

// ---- Lời mời ----

bool Registry::hasInvitation(ConnId host, ConnId invitee) const {
    return std::any_of(invites_.begin(), invites_.end(), [&](const Invitation& i) {
        return i.host == host && i.invitee == invitee;
    });
}

void Registry::addInvitation(ConnId host, ConnId invitee) { invites_.push_back({host, invitee}); }

bool Registry::removeInvitation(ConnId host, ConnId invitee) {
    for (auto it = invites_.begin(); it != invites_.end(); ++it) {
        if (it->host == host && it->invitee == invitee) {
            invites_.erase(it);
            return true;
        }
    }
    return false;
}

std::vector<ConnId> Registry::takeInvitesTo(ConnId invitee) {
    std::vector<ConnId> hosts;
    for (auto it = invites_.begin(); it != invites_.end();) {
        if (it->invitee == invitee) {
            hosts.push_back(it->host);
            it = invites_.erase(it);
        } else {
            ++it;
        }
    }
    return hosts;
}

std::vector<ConnId> Registry::takeInvitesFrom(ConnId host) {
    std::vector<ConnId> invitees;
    for (auto it = invites_.begin(); it != invites_.end();) {
        if (it->host == host) {
            invitees.push_back(it->invitee);
            it = invites_.erase(it);
        } else {
            ++it;
        }
    }
    return invitees;
}

// ---- Trận đấu ----

Match& Registry::createMatch(const std::string& gameType, ConnId p1, ConnId p2,
                             std::unique_ptr<game::IGame> engine) {
    const int id = nextMatchId_++;
    Match& m = matches_[id];
    m.id = id;
    m.gameType = gameType;
    m.players[0] = p1;
    m.players[1] = p2;
    m.engine = std::move(engine);
    return m;
}

Match* Registry::findMatch(int id) {
    auto it = matches_.find(id);
    return it == matches_.end() ? nullptr : &it->second;
}

void Registry::eraseMatch(int id) { matches_.erase(id); }

}  // namespace core