// server/src/core/Hub.h
#pragma once
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
// việc ghi ra socket là của lớp net.
struct OutLine {
    ConnId conn;
    std::string line;
};
using Out = std::vector<OutLine>;

using proto::ErrorCode;

class Hub {
public:
    explicit Hub(store::UserStore& users);

    // Hai điểm vào duy nhất của core. Luôn được gọi tuần tự (net đảm bảo).
    void onMessage(ConnId conn, const proto::Message& msg, Out& out);
    void onClose(ConnId conn, Out& out);

private:
    using Handler = void (Hub::*)(Session&, const proto::Message&, Out&);

    // Một dòng của bảng lệnh (chép từ state machine 4.3).
    struct CmdSpec {
        std::vector<State> allowedStates;
        std::size_t nFields;
        Handler handler;
    };

    void initTable();

    // Trả lời một request: "CMD|code|extra...". Mỗi request PHẢI có đúng một reply,
    // vì client ghép response với request theo thứ tự (FIFO).
    void reply(ConnId conn, const std::string& cmd, ErrorCode code, Out& out,
               const std::vector<std::string>& extra = {});
    // Gửi một message chủ động (push): INVI, IDCL, STRT, OPPM, SKIP, OVER.
    void push(ConnId conn, const std::string& cmd, const std::vector<std::string>& fields,
              Out& out);

    // auth.cpp
    void handleRegi(Session&, const proto::Message&, Out&);
    void handleLogi(Session&, const proto::Message&, Out&);
    void handleLogu(Session&, const proto::Message&, Out&);

    // info.cpp
    void handlePlst(Session&, const proto::Message&, Out&);
    void handleRank(Session&, const proto::Message&, Out&);

    // entry.cpp
    void handleRand(Session&, const proto::Message&, Out&);
    void handleRcan(Session&, const proto::Message&, Out&);
    void handleCrom(Session&, const proto::Message&, Out&);
    void handleInvt(Session&, const proto::Message&, Out&);
    void handleIacc(Session&, const proto::Message&, Out&);
    void handleIdec(Session&, const proto::Message&, Out&);
    void handleDcan(Session&, const proto::Message&, Out&);

    // play.cpp
    void handleMove(Session&, const proto::Message&, Out&);
    void handleRsgn(Session&, const proto::Message&, Out&);

    // Các hàm dùng chung
    // Xóa mọi lời mời đang chờ gửi tới `invitee`, push IDCL cho từng host.
    // Invariant: lời mời chỉ tồn tại khi invitee đang ở LOBBY.
    void cancelInvitesOf(Session& invitee, Out& out);
    // Tạo trận, đưa cả hai vào IN_GAME, push STRT. p1 đi trước.
    void startMatch(ConnId p1, ConnId p2, const std::string& gameType, Out& out);
    // Kết thúc trận đúng một lần: ended, Elo, về LOBBY, push OVER, xóa trận.
    // winnerNo: 1, 2 hoặc 0 (hòa). `exclude`: conn không nhận OVER (người vừa rớt mạng), -1 nếu không có.
    void finishMatch(Match& m, int winnerNo, ConnId exclude, Out& out);

    store::UserStore& users_;
    Registry reg_;
    std::unordered_map<std::string, CmdSpec> table_;
    bool tableReady_ = false;
};

}  // namespace core