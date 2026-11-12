// server/src/game/Gomoku.cpp
#include "Gomoku.h"

namespace game {

Gomoku::Gomoku(int size) : size_(size), cells_(size * size, 0) {}

int Gomoku::countDir(int r, int c, int dr, int dc, int player) const {
    int n = 0;
    r += dr;
    c += dc;
    while (inRange(r, c) && at(r, c) == player) {
        ++n;
        r += dr;
        c += dc;
    }
    return n;
}

MoveResult Gomoku::applyMove(int player, Pos p) {
    MoveResult res;
    if (!inRange(p.row, p.col)) {
        res.error = MoveError::OutOfRange;
        return res;
    }
    if (at(p.row, p.col) != 0) {
        res.error = MoveError::Occupied;
        return res;
    }

    cells_[p.row * size_ + p.col] = player;
    ++moves_;

    // 4 trục: ngang, dọc, chéo xuống, chéo lên. Mỗi trục đếm cả hai phía.
    static const int dirs[4][2] = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};
    for (const auto& d : dirs) {
        int n = 1 + countDir(p.row, p.col, d[0], d[1], player) +
                countDir(p.row, p.col, -d[0], -d[1], player);
        if (n >= 5) {
            res.status = Status::Win;
            res.winner = player;
            return res;
        }
    }

    if (moves_ == size_ * size_) {
        res.status = Status::Draw;
        return res;
    }

    turn_ = 3 - player;
    return res;
}

}  // namespace game