// server/src/game/Reversi.h
#pragma once
#include <vector>

#include "IGame.h"

namespace game {

// Reversi 8x8. Người 1 (đen) đi trước.
class Reversi : public IGame {
public:
    Reversi();

    int currentPlayer() const override { return turn_; }
    MoveResult applyMove(int player, Pos pos) override;

private:
    static constexpr int N = 8;

    bool inRange(int r, int c) const { return r >= 0 && r < N && c >= 0 && c < N; }
    int at(int r, int c) const { return cells_[r * N + c]; }

    // Các quân bị lật nếu `player` đặt vào (r,c). Rỗng nghĩa là nước đi không hợp lệ.
    std::vector<Pos> flipsFor(int player, int r, int c) const;
    bool hasMove(int player) const;

    std::vector<int> cells_;  // 0 trống, 1 hoặc 2
    int turn_ = 1;
};

}  // namespace game