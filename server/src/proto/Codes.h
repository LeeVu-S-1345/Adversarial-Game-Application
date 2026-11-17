// server/src/proto/Codes.h
#pragma once

namespace proto {

// Trạng thái của một session (docs/protocol.md, mục 3).
enum class State { CONNECTED, LOBBY, MATCHING, IN_ROOM, IN_GAME };

// 14 request của client, dùng làm khóa trong bảng lệnh để tránh gõ sai chuỗi.
namespace Cmd {
inline constexpr const char* REGI = "REGI";
inline constexpr const char* LOGI = "LOGI";
inline constexpr const char* LOGU = "LOGU";
inline constexpr const char* PLST = "PLST";
inline constexpr const char* RANK = "RANK";
inline constexpr const char* RAND = "RAND";
inline constexpr const char* RCAN = "RCAN";
inline constexpr const char* CROM = "CROM";
inline constexpr const char* DROM = "DROM";
inline constexpr const char* INVT = "INVT";
inline constexpr const char* IACC = "IACC";
inline constexpr const char* IDEC = "IDEC";
inline constexpr const char* MOVE = "MOVE";
inline constexpr const char* RSGN = "RSGN";
}  // namespace Cmd

// 6 push của server.
namespace Push {
inline constexpr const char* INVI = "INVI";
inline constexpr const char* IDCL = "IDCL";
inline constexpr const char* STRT = "STRT";
inline constexpr const char* OPPM = "OPPM";
inline constexpr const char* SKIP = "SKIP";
inline constexpr const char* OVER = "OVER";
}  // namespace Push

// Mã trạng thái (docs/protocol.md, mục 5).
enum class ErrorCode : int {
    OK = 0,
    INCORRECT_USERNAME_PASSWORD = 1,
    ALREADY_LOGGED_IN = 2,
    EXISTING_USER = 3,
    GAME_NOT_FOUND = 4,
    INVITEE_NOT_ONLINE = 5,
    INVITEE_BUSY = 6,
    INVITATION_NOT_FOUND = 7,
    INVITATION_ALREADY_SENT = 8,
    CAN_NOT_INVITE_SELF = 9,
    BAD_FIELDS = 10,
    BAD_USERNAME_PASSWORD = 11,
    BAD_STATE = 12,
    NOT_IN_TURN = 13,
    OUT_OF_BOUNDS = 14,
    CELL_OCCUPIED = 15,
    ILLEGAL_MOVE = 16
};

}  // namespace proto
