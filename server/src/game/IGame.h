// server/src/game/IGame.h
#pragma once
#include <vector>

namespace game {

// Tọa độ 0-based: row, col. Nếu protocol của nhóm dùng 1-based thì đổi ở Hub
// khi parse "r,c", đừng đổi trong game/.
struct Pos {
    int row;
    int col;
};

// Game chỉ báo lỗi theo nghĩa; Hub tự ánh xạ sang mã 14/15/16 của protocol.
enum class MoveError { None, OutOfRange, Occupied, IllegalMove };

enum class Status { Playing, Win, Draw };

struct MoveResult {
    MoveError error = MoveError::None;
    Status status = Status::Playing;  // trạng thái ván SAU nước đi này
    int winner = 0;                   // 1 hoặc 2 khi status == Win
    std::vector<Pos> flips;           // chỉ Reversi: các quân bị lật
    bool opponentSkipped = false;     // Reversi: đối thủ hết nước, người vừa đi đi tiếp (SKIP)
};

class IGame {
public:
    virtual ~IGame() = default;

    // Kích thước bàn cờ, gửi cho client trong STRT.
    virtual int rows() const = 0;
    virtual int cols() const = 0;

    // Người sẽ đi nước kế tiếp: 1 (đi trước) hoặc 2.
    virtual int currentPlayer() const = 0;

    // Hub đã kiểm tra đúng lượt (mã 13) trước khi gọi. Nếu error != None thì
    // bàn cờ KHÔNG thay đổi.
    virtual MoveResult applyMove(int player, Pos pos) = 0;
};

}  // namespace game