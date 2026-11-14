// server/src/game/Reversi.cpp
#include "Reversi.h"

namespace game {

namespace {
    const int kDirs[8][2] = {{-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1}};
}

Reversi::Reversi() : cells_(N * N, 0) {
    // Thế khởi đầu chuẩn: 4 quân ở giữa, chéo nhau.
    cells_[3 * N + 3] = 2;
    cells_[4 * N + 4] = 2;
    cells_[3 * N + 4] = 1;
    cells_[4 * N + 3] = 1;
}

std::vector<Pos> Reversi::flipsFor(int player, int r, int c) const {
    std::vector<Pos> result;
    const int opp = 3 - player;
    for (const auto& d : kDirs) {
        std::vector<Pos> line;
        int rr = r + d[0];
        int cc = c + d[1];
        while (inRange(rr, cc) && at(rr, cc) == opp) {
            line.push_back({rr, cc});
            rr += d[0];
            cc += d[1];
        }
        // Chuỗi quân đối thủ phải kết thúc bằng quân của mình mới lật được.
        if (!line.empty() && inRange(rr, cc) && at(rr, cc) == player) {
            result.insert(result.end(), line.begin(), line.end());
        }
    }
    return result;
}

bool Reversi::hasMove(int player) const {
    for (int r = 0; r < N; ++r) {
        for (int c = 0; c < N; ++c) {
            if (at(r, c) == 0 && !flipsFor(player, r, c).empty()) return true;
        }
    }
    return false;
}

MoveResult Reversi::applyMove(int player, Pos p) {
    MoveResult res;
    if (!inRange(p.row, p.col)) {
        res.error = MoveError::OutOfRange;
        return res;
    }
    if (at(p.row, p.col) != 0) {
        res.error = MoveError::Occupied;
        return res;
    }
    std::vector<Pos> flips = flipsFor(player, p.row, p.col);
    if (flips.empty()) {
        res.error = MoveError::IllegalMove;
        return res;
    }

    cells_[p.row * N + p.col] = player;
    for (const Pos& f : flips) cells_[f.row * N + f.col] = player;
    res.flips = std::move(flips);

    const int opp = 3 - player;
    if (hasMove(opp)) {
        turn_ = opp;
    } else if (hasMove(player)) {
        turn_ = player;  // đối thủ bị bỏ lượt (SKIP)
        res.opponentSkipped = true;
    } else {
        // Cả hai hết nước: kết thúc, so số quân.
        int ones = 0, twos = 0;
        for (int v : cells_) {
            if (v == 1) ++ones;
            else if (v == 2) ++twos;
        }
        if (ones == twos) {
            res.status = Status::Draw;
        } else {
            res.status = Status::Win;
            res.winner = ones > twos ? 1 : 2;
        }
    }
    return res;
}

}  // namespace game