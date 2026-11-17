// server/src/store/FileUserStore.cpp
#include "FileUserStore.h"

#include <algorithm>
#include <cstdio>
#include <cstdlib>
#include <fstream>
#include <iostream>
#include <sstream>

namespace store {

FileUserStore::FileUserStore(std::string path) : path_(std::move(path)) {}

bool FileUserStore::load() {
    users_.clear();
    if (path_.empty()) return true;

    std::ifstream in(path_);
    if (!in) return true;  // chưa có file: bắt đầu với kho rỗng

    std::string line;
    while (std::getline(in, line)) {
        if (!line.empty() && line.back() == '\r') line.pop_back();
        if (line.empty()) continue;

        std::vector<std::string> parts;
        std::stringstream ss(line);
        std::string part;
        while (std::getline(ss, part, '|')) parts.push_back(part);
        if (parts.size() != 4) {
            std::cerr << "FileUserStore: skip malformed line in " << path_ << std::endl;
            continue;
        }
        User u;
        u.password = parts[1];
        u.caro = std::atoi(parts[2].c_str());
        u.reversi = std::atoi(parts[3].c_str());
        users_[parts[0]] = std::move(u);
    }
    return !in.bad();
}

bool FileUserStore::save() const {
    if (path_.empty()) return true;

    const std::string tmp = path_ + ".tmp";
    {
        std::ofstream out(tmp, std::ios::trunc);
        if (!out) return false;
        for (const auto& [name, u] : users_) {
            out << name << '|' << u.password << '|' << u.caro << '|' << u.reversi << '\n';
        }
        out.flush();
        if (!out) return false;
    }
    return std::rename(tmp.c_str(), path_.c_str()) == 0;
}

bool FileUserStore::create(const std::string& name, const std::string& password) {
    if (users_.count(name)) return false;
    User u;
    u.password = password;
    users_[name] = std::move(u);
    if (!save()) std::cerr << "FileUserStore: cannot write " << path_ << std::endl;
    return true;
}

bool FileUserStore::checkPassword(const std::string& name, const std::string& password) const {
    auto it = users_.find(name);
    return it != users_.end() && it->second.password == password;
}

int FileUserStore::elo(const std::string& name, const std::string& game) const {
    auto it = users_.find(name);
    if (it == users_.end()) return kInitialElo;
    if (game == "caro") return it->second.caro;
    if (game == "reversi") return it->second.reversi;
    return kInitialElo;
}

void FileUserStore::setElo(const std::string& name, const std::string& game, int elo) {
    auto it = users_.find(name);
    if (it == users_.end()) return;
    if (game == "caro") it->second.caro = elo;
    else if (game == "reversi") it->second.reversi = elo;
    else return;
    if (!save()) std::cerr << "FileUserStore: cannot write " << path_ << std::endl;
}

std::vector<RankEntry> FileUserStore::ranking(const std::string& game) const {
    std::vector<RankEntry> v;
    v.reserve(users_.size());
    for (const auto& [name, u] : users_) v.push_back({name, elo(name, game)});
    // users_ đã theo tên tăng dần; stable_sort giữ thứ tự đó khi bằng điểm.
    std::stable_sort(v.begin(), v.end(),
                     [](const RankEntry& a, const RankEntry& b) { return a.elo > b.elo; });
    return v;
}

}  // namespace store
