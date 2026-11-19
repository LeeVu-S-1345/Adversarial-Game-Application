// server/src/core/Hub.h
#pragma once
#include <functional>
#include <string>
#include <unordered_map>
#include <vector>

#include "../proto/Codec.h"
#include "../proto/Codes.h"
#include "../proto/Message.h"
#include "../store/UserStore.h"
#include "Registry.h"

namespace core {

// Một dòng cần gửi: gửi `line` tới `conn`. Core chỉ sinh ra danh sách này,
// việc ghi ra socket (và ghi log SEND) là của lớp net.
struct OutLine {
    ConnId conn;
    std::string line;
};
using Out = std::vector<OutLine>;

using proto::ErrorCode;

// Xử lý message theo docs/protocol.md. Mỗi handler ứng với một dòng trong
// Message Processing Table; số trong chú thích là số use case.
class Hub {
public:
    explicit Hub(store::UserStore& users);

    // Hai điểm vào duy nhất của core. Luôn được gọi tuần tự (net đảm bảo),
    // nên không cần lock.
    void onMessage(ConnId conn, const proto::Message& msg, Out& out);
    void onClose(ConnId conn, Out& out);

    // Thay bộ sinh recovery code (test dùng mã cố định). Mặc định: 8 ký tự ngẫu nhiên.
    void setRecoveryCodeGenerator(std::function<std::string()> gen) { newRecoveryCode_ = std::move(gen); }

private:
    using Handler = void (Hub::*)(Session&, const proto::Message&, Out&);

    // Một dòng của bảng lệnh (docs/protocol.md, mục 3 và mục 8).
    struct CmdSpec {
        std::vector<State> allowedStates;
        std::size_t nFields;
        Handler handler;
    };

    void initTable();

    // Trả lời một request: "CMD|code|extra...". Mỗi request có ĐÚNG một reply,
    // và reply đi trước mọi push do chính request đó gây ra.
    void reply(ConnId conn, const std::string& cmd, ErrorCode code, Out& out,
               const std::vector<std::string>& extra = {});
    // Gửi một push: INVI, IDCL, STRT, OPPM, SKIP, OVER.
    void push(ConnId conn, const std::string& cmd, const std::vector<std::string>& fields,
              Out& out);

    // auth.cpp
    void handleRegi(Session&, const proto::Message&, Out&);  // 1
    void handleLogi(Session&, const proto::Message&, Out&);  // 2
    void handleLogu(Session&, const proto::Message&, Out&);  // 3
    void handleRspw(Session&, const proto::Message&, Out&);  // 16
    std::string recoveryCode();

    // info.cpp
    void handlePlst(Session&, const proto::Message&, Out&);  // 4
    void handleRank(Session&, const proto::Message&, Out&);  // 5

    // entry.cpp
    void handleCrom(Session&, const proto::Message&, Out&);  // 6
    void handleInvt(Session&, const proto::Message&, Out&);  // 7
    void handleIacc(Session&, const proto::Message&, Out&);  // 8
    void handleIdec(Session&, const proto::Message&, Out&);  // 9
    void handleDrom(Session&, const proto::Message&, Out&);  // 10
    void handleRand(Session&, const proto::Message&, Out&);  // 11
    void handleRcan(Session&, const proto::Message&, Out&);  // 12

    // play.cpp
    void handleMove(Session&, const proto::Message&, Out&);  // 13
    void handleRsgn(Session&, const proto::Message&, Out&);  // 14

    // Các hàm dùng chung
    // Xóa mọi lời mời đang chờ gửi tới `invitee`, push IDCL cho từng chủ phòng.
    // Gọi khi invitee rời LOBBY mà chưa trả lời (RAND, CROM, LOGU, nhận lời phòng khác, rớt mạng).
    // Invariant: lời mời chỉ tồn tại khi invitee đang ở LOBBY và phòng còn tồn tại.
    void cancelInvitesOf(Session& invitee, Out& out);
    // Xóa phòng `s` đang làm chủ cùng mọi lời mời của phòng (người được mời không được báo).
    void closeRoomOf(Session& s);
    // Tạo trận, đưa cả hai vào IN_GAME, push STRT. p1 là bên 1 (đi trước).
    void startMatch(ConnId p1, ConnId p2, const std::string& gameType, int roomId, Out& out);
    // End of Game (15), chạy đúng một lần: ended, Elo, về LOBBY, xóa trận và phòng, push OVER.
    // winnerNo: 1, 2 hoặc 0 (hòa). `exclude`: conn không nhận OVER (người vừa rớt mạng), -1 nếu không có.
    void finishMatch(Match& m, int winnerNo, ConnId exclude, Out& out);

    store::UserStore& users_;
    std::function<std::string()> newRecoveryCode_;  // mặc định: ngẫu nhiên (auth.cpp)
    Registry reg_;
    std::unordered_map<std::string, CmdSpec> table_;
    bool tableReady_ = false;
};

}  // namespace core
