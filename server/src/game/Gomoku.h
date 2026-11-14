// server/src/game/Gomoku.h
#pragma once
#include <vector>

#include "IGame.h"

namespace game {

// Caro / Gomoku: ai có từ 5 quân liên tiếp trở lên (ngang, dọc, chéo) thắng.
class Gomoku : public IGame {
public:
    explicit Gomoku(int size = 15);

    int currentPlayer() const override { return turn_; }
    MoveResult applyMove(int player, Pos pos) override;

private:
    bool inRange(int r, int c) const { return r >= 0 && r < size_ && c >= 0 && c < size_; }
    int at(int r, int c) const { return cells_[r * size_ + c]; }
    // Đếm số quân liên tiếp của `player` đi từ (r,c) theo hướng (dr,dc), không tính (r,c).
    int countDir(int r, int c, int dr, int dc, int player) const;

    int size_;
    std::vector<int> cells_;  // 0 trống, 1 hoặc 2 là người chơi
    int turn_ = 1;
    int moves_ = 0;
};

}  // namespace game