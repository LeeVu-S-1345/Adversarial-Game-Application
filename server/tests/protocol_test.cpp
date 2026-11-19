// server/tests/protocol_test.cpp
// Chạy các phiên mẫu của docs/protocol.md thẳng vào Hub, không cần socket.
// Mỗi dòng mong đợi có dạng "<conn> <message>", theo đúng thứ tự server gửi.
#include <cstdio>
#include <iostream>
#include <string>
#include <vector>

#include "core/Hub.h"
#include "proto/Codec.h"
#include "store/Elo.h"
#include "store/FileUserStore.h"

namespace {

int g_failed = 0;
int g_checked = 0;

using Lines = std::vector<std::string>;

struct Server {
    store::FileUserStore users{""};  // path rỗng: chỉ giữ trong RAM
    core::Hub hub{users};
    int codes = 0;  // recovery code cố định cho test: CODE0001, CODE0002, ...

    Server() {
        hub.setRecoveryCodeGenerator([this] {
            char buf[16];
            std::snprintf(buf, sizeof(buf), "CODE%04d", ++codes);
            return std::string(buf);
        });
    }

    static Lines fmt(const core::Out& out) {
        Lines v;
        for (const auto& o : out) v.push_back(std::to_string(o.conn) + " " + o.line);
        return v;
    }
    Lines send(int conn, const std::string& line) {
        core::Out out;
        if (auto msg = proto::parseLine(line)) hub.onMessage(conn, *msg, out);
        return fmt(out);
    }
    Lines close(int conn) {
        core::Out out;
        hub.onClose(conn, out);
        return fmt(out);
    }
};

std::string join(const Lines& v) {
    std::string s;
    for (const auto& l : v) s += "\n      " + l;
    return v.empty() ? "\n      (nothing)" : s;
}

void expect(const char* what, const Lines& got, const Lines& want) {
    ++g_checked;
    if (got == want) return;
    ++g_failed;
    std::cout << "FAIL: " << what << "\n   want:" << join(want) << "\n   got: " << join(got)
              << "\n";
}

void expectInt(const char* what, int got, int want) {
    ++g_checked;
    if (got == want) return;
    ++g_failed;
    std::cout << "FAIL: " << what << ": want " << want << ", got " << got << "\n";
}

// Đăng ký và đăng nhập: conn 1 = minh, 2 = tuan, 3 = linh.
void loginThree(Server& s) {
    s.send(1, "REGI|minh|123456");
    s.send(2, "REGI|tuan|abcdef");
    s.send(3, "REGI|linh|qwerty");
    s.send(1, "LOGI|minh|123456");
    s.send(2, "LOGI|tuan|abcdef");
    s.send(3, "LOGI|linh|qwerty");
}

// 16. Reset Password (RSPW)
void testResetPassword() {
    Server s;
    s.send(1, "REGI|minh|123456");  // recovery code CODE0001
    // (every RSPW that passes the format check draws a new code, even when it then fails)
    expect("RSPW unknown user", s.send(1, "RSPW|nobody|CODE0001|newpass"), {"1 RSPW|17"});
    expect("RSPW wrong code", s.send(1, "RSPW|minh|CODE9999|newpass"), {"1 RSPW|17"});
    expect("RSPW lower-case code", s.send(1, "RSPW|minh|code0001|newpass"), {"1 RSPW|11"});
    expect("RSPW short code", s.send(1, "RSPW|minh|CODE01|newpass"), {"1 RSPW|11"});
    expect("RSPW bad new password", s.send(1, "RSPW|minh|CODE0001|new pass"), {"1 RSPW|11"});
    expect("RSPW missing field", s.send(1, "RSPW|minh|CODE0001"), {"1 RSPW|10"});
    expect("RSPW ok", s.send(1, "RSPW|minh|CODE0001|newpass"), {"1 RSPW|0|CODE0004"});
    expect("RSPW old code used again", s.send(1, "RSPW|minh|CODE0001|other1"), {"1 RSPW|17"});
    expect("LOGI old password", s.send(1, "LOGI|minh|123456"), {"1 LOGI|1"});
    expect("LOGI new password", s.send(1, "LOGI|minh|newpass"), {"1 LOGI|0"});
    expect("RSPW while logged in", s.send(1, "RSPW|minh|CODE0004|abcdef"), {"1 RSPW|12"});
    expect("RSPW from another connection", s.send(2, "RSPW|minh|CODE0004|abcdef"), {"2 RSPW|0|CODE0006"});
    expectInt("logged-in session kept", static_cast<int>(s.send(1, "PLST").size()), 1);
}

void testAuthentication() {
    Server s;
    expect("REGI ok", s.send(1, "REGI|minh|123456"), {"1 REGI|0|CODE0001"});
    expect("REGI existing", s.send(1, "REGI|minh|abcdef"), {"1 REGI|3"});
    expect("REGI space in username", s.send(1, "REGI|hoang minh|123"), {"1 REGI|11"});
    expect("REGI empty password", s.send(1, "REGI|minh2|"), {"1 REGI|11"});
    expect("REGI missing field", s.send(1, "REGI|minh"), {"1 REGI|10"});
    expect("REGI extra field", s.send(1, "REGI|a|b|c"), {"1 REGI|10"});
    expect("PLST before login", s.send(1, "PLST"), {"1 PLST|12"});
    expect("unknown type", s.send(1, "HELO|x"), {"1 HELO|10"});
    expect("lowercase type is unknown", s.send(1, "logi|minh|123456"), {"1 logi|10"});
    expect("LOGI wrong password", s.send(1, "LOGI|minh|wrong"), {"1 LOGI|1"});
    expect("LOGI unknown account", s.send(1, "LOGI|nobody|123456"), {"1 LOGI|1"});
    expect("LOGI invalid format", s.send(1, "LOGI|mi nh|123456"), {"1 LOGI|11"});
    expect("LOGI ok", s.send(1, "LOGI|minh|123456"), {"1 LOGI|0"});
    expect("LOGI again on same connection", s.send(1, "LOGI|minh|123456"), {"1 LOGI|12"});
    expect("REGI while logged in", s.send(1, "REGI|other|123456"), {"1 REGI|12"});
    expect("LOGI same account elsewhere", s.send(2, "LOGI|minh|123456"), {"2 LOGI|2"});
    expect("LOGI elsewhere, wrong password first", s.send(2, "LOGI|minh|nope"), {"2 LOGI|1"});
    expect("LOGU extra field", s.send(1, "LOGU|x"), {"1 LOGU|10"});
    expect("LOGU ok", s.send(1, "LOGU"), {"1 LOGU|0"});
    expect("LOGU when CONNECTED", s.send(1, "LOGU"), {"1 LOGU|12"});
    expect("LOGI after logout from another connection", s.send(2, "LOGI|minh|123456"),
           {"2 LOGI|0"});
}

void testLobbyInformation() {
    Server s;
    loginThree(s);
    expect("PLST includes requester, sorted", s.send(1, "PLST"),
           {"1 PLST|0|linh,FREE;minh,FREE;tuan,FREE"});
    expect("PLST extra field", s.send(1, "PLST|x"), {"1 PLST|10"});
    expect("RANK unknown game", s.send(1, "RANK|chess"), {"1 RANK|4"});
    expect("RANK game name is case-sensitive", s.send(1, "RANK|Caro"), {"1 RANK|4"});
    expect("RANK missing field", s.send(1, "RANK"), {"1 RANK|10"});
    expect("RANK ties by username", s.send(1, "RANK|caro"),
           {"1 RANK|0|linh,1000;minh,1000;tuan,1000"});
    s.send(2, "CROM|caro");
    expect("PLST shows BUSY for a host", s.send(1, "PLST"),
           {"1 PLST|0|linh,FREE;minh,FREE;tuan,BUSY"});
    expect("PLST allowed in IN_ROOM", s.send(2, "PLST"),
           {"2 PLST|0|linh,FREE;minh,FREE;tuan,BUSY"});
    expect("RANK not allowed in IN_ROOM", s.send(2, "RANK|caro"), {"2 RANK|12"});
}

void testRoomAndInvitation() {
    Server s;
    loginThree(s);
    expect("CROM unknown game", s.send(1, "CROM|chess"), {"1 CROM|4"});
    expect("CROM ok", s.send(1, "CROM|caro"), {"1 CROM|0|1"});
    expect("CROM while IN_ROOM", s.send(1, "CROM|caro"), {"1 CROM|12"});
    expect("LOGU while IN_ROOM", s.send(1, "LOGU"), {"1 LOGU|12"});
    expect("INVT self", s.send(1, "INVT|minh"), {"1 INVT|9"});
    expect("INVT offline", s.send(1, "INVT|nobody"), {"1 INVT|5"});
    expect("INVT ok", s.send(1, "INVT|tuan"), {"1 INVT|0", "2 INVI|1|minh|caro"});
    expect("INVT again", s.send(1, "INVT|tuan"), {"1 INVT|8"});
    expect("INVT from LOBBY", s.send(2, "INVT|linh"), {"2 INVT|12"});
    expect("IDEC non-numeric room", s.send(2, "IDEC|abc"), {"2 IDEC|7"});
    expect("IDEC ok", s.send(2, "IDEC|1"), {"2 IDEC|0", "1 IDCL|tuan"});
    expect("IDEC again", s.send(2, "IDEC|1"), {"2 IDEC|7"});
    expect("IACC after declining", s.send(2, "IACC|1"), {"2 IACC|7"});
    expect("INVT second player", s.send(1, "INVT|linh"), {"1 INVT|0", "3 INVI|1|minh|caro"});
    expect("IACC ok: host is side 1", s.send(3, "IACC|1"),
           {"3 IACC|0", "1 STRT|caro|1|linh|15,15", "3 STRT|caro|2|minh|15,15"});
    expect("IACC while IN_GAME", s.send(3, "IACC|9"), {"3 IACC|12"});
    expect("INVT a busy player", (s.send(2, "CROM|reversi"), s.send(2, "INVT|minh")),
           {"2 INVT|6"});
    expect("DROM ok", s.send(2, "DROM"), {"2 DROM|0"});
    expect("DROM when back in LOBBY", s.send(2, "DROM"), {"2 DROM|12"});
}

void testInvitationLifecycle() {
    {
        Server s;  // host leaves the room: invitee is not notified, later answer gets 7
        loginThree(s);
        s.send(1, "CROM|caro");
        s.send(1, "INVT|tuan");
        expect("DROM does not notify the invitee", s.send(1, "DROM"), {"1 DROM|0"});
        expect("IACC on a deleted room", s.send(2, "IACC|1"), {"2 IACC|7"});
    }
    {
        Server s;  // host disconnects in IN_ROOM
        loginThree(s);
        s.send(1, "CROM|caro");
        s.send(1, "INVT|tuan");
        expect("host disconnect notifies nobody", s.close(1), {});
        expect("IDEC after host disconnect", s.send(2, "IDEC|1"), {"2 IDEC|7"});
    }
    {
        Server s;  // invitee leaves LOBBY without answering: host gets IDCL
        loginThree(s);
        s.send(1, "CROM|caro");
        s.send(1, "INVT|tuan");
        s.send(1, "INVT|linh");
        expect("RAND cancels pending invitations", s.send(2, "RAND|reversi"),
               {"1 IDCL|tuan", "2 RAND|0"});
        expect("LOGU cancels pending invitations", s.send(3, "LOGU"), {"1 IDCL|linh", "3 LOGU|0"});
    }
    {
        Server s;  // invitee disconnects in LOBBY
        loginThree(s);
        s.send(1, "CROM|caro");
        s.send(1, "INVT|tuan");
        expect("invitee disconnect", s.close(2), {"1 IDCL|tuan"});
        expect("the host can invite someone else", s.send(1, "INVT|linh"),
               {"1 INVT|0", "3 INVI|1|minh|caro"});
    }
    {
        Server s;  // two rooms invite the same player; accepting one declines the other
        loginThree(s);
        s.send(1, "CROM|caro");     // room 1
        s.send(2, "CROM|reversi");  // room 2
        s.send(1, "INVT|linh");
        s.send(2, "INVT|linh");
        expect("accepting room 2 sends IDCL to the host of room 1", s.send(3, "IACC|2"),
               {"1 IDCL|linh", "3 IACC|0", "2 STRT|reversi|1|linh|8,8", "3 STRT|reversi|2|tuan|8,8"});
    }
    {
        Server s;  // another invitee accepted first
        loginThree(s);
        s.send(4, "REGI|nam|zzzzzz");
        s.send(4, "LOGI|nam|zzzzzz");
        s.send(1, "CROM|caro");
        s.send(1, "INVT|tuan");
        s.send(1, "INVT|linh");
        s.send(2, "IACC|1");
        expect("late IACC after someone else accepted", s.send(3, "IACC|1"), {"3 IACC|7"});
    }
}

void testRandomMatching() {
    Server s;
    loginThree(s);
    expect("RAND unknown game", s.send(1, "RAND|chess"), {"1 RAND|4"});
    expect("RAND enqueues", s.send(1, "RAND|caro"), {"1 RAND|0"});
    expect("RAND while MATCHING", s.send(1, "RAND|caro"), {"1 RAND|12"});
    expect("PLST not allowed while MATCHING", s.send(1, "PLST"), {"1 PLST|12"});
    expect("another game has its own queue", s.send(3, "RAND|reversi"), {"3 RAND|0"});
    expect("RCAN ok", s.send(3, "RCAN"), {"3 RCAN|0"});
    expect("RCAN when back in LOBBY", s.send(3, "RCAN"), {"3 RCAN|12"});
    expect("RAND pairs: the waiting player is side 1", s.send(2, "RAND|caro"),
           {"2 RAND|0", "1 STRT|caro|1|tuan|15,15", "2 STRT|caro|2|minh|15,15"});
    expect("RCAN after being paired", s.send(1, "RCAN"), {"1 RCAN|12"});

    Server t;  // disconnect while MATCHING removes the player from the queue
    loginThree(t);
    t.send(1, "RAND|caro");
    expect("disconnect while MATCHING", t.close(1), {});
    expect("the queue is empty again", t.send(2, "RAND|caro"), {"2 RAND|0"});
}

void testCaroGame() {
    Server s;
    loginThree(s);
    s.send(1, "RAND|caro");
    s.send(2, "RAND|caro");  // minh = side 1, tuan = side 2

    expect("MOVE out of turn", s.send(2, "MOVE|7,7"), {"2 MOVE|13"});
    expect("13 is checked before 14", s.send(2, "MOVE|a,b"), {"2 MOVE|13"});
    expect("MOVE one coordinate", s.send(1, "MOVE|3"), {"1 MOVE|10"});
    expect("MOVE three coordinates", s.send(1, "MOVE|3,4,5"), {"1 MOVE|10"});
    expect("MOVE two fields", s.send(1, "MOVE|3|4"), {"1 MOVE|10"});
    expect("MOVE no field", s.send(1, "MOVE"), {"1 MOVE|10"});
    expect("MOVE not integers", s.send(1, "MOVE|a,b"), {"1 MOVE|14"});
    expect("MOVE negative", s.send(1, "MOVE|-1,2"), {"1 MOVE|14"});
    expect("MOVE outside the board", s.send(1, "MOVE|7,20"), {"1 MOVE|14"});
    expect("MOVE ok, k = 0 keeps the empty field", s.send(1, "MOVE|7,7"),
           {"1 MOVE|0|0|", "2 OPPM|7,7|0|"});
    expect("MOVE occupied cell", s.send(2, "MOVE|7,7"), {"2 MOVE|15"});
    expect("the turn is kept after an error", s.send(2, "MOVE|8,7"),
           {"2 MOVE|0|0|", "1 OPPM|8,7|0|"});

    for (int c = 8; c <= 10; ++c) {
        s.send(1, "MOVE|7," + std::to_string(c));
        s.send(2, "MOVE|8," + std::to_string(c));
    }
    expect("five in a row ends the game", s.send(1, "MOVE|7,11"),
           {"1 MOVE|0|0|", "2 OPPM|7,11|0|", "1 OVER|WIN|+16|1016", "2 OVER|LOSE|-16|984"});
    expect("MOVE after the game", s.send(2, "MOVE|8,11"), {"2 MOVE|12"});
    expect("RSGN after the game", s.send(1, "RSGN"), {"1 RSGN|12"});
    expect("both are back in LOBBY", s.send(1, "PLST"),
           {"1 PLST|0|linh,FREE;minh,FREE;tuan,FREE"});
    expect("caro ranking changed", s.send(1, "RANK|caro"),
           {"1 RANK|0|minh,1016;linh,1000;tuan,984"});
    expect("reversi ranking did not", s.send(1, "RANK|reversi"),
           {"1 RANK|0|linh,1000;minh,1000;tuan,1000"});

    // Second game between 1016 and 984; side 2 disconnects.
    s.send(1, "RAND|caro");
    s.send(2, "RAND|caro");
    expect("disconnect in game: only the opponent gets OVER", s.close(2),
           {"1 OVER|WIN|+15|1031"});
    expect("the disconnected player's rating is updated too", s.send(1, "RANK|caro"),
           {"1 RANK|0|minh,1031;linh,1000;tuan,969"});
}

void testReversiAndResign() {
    Server s;
    loginThree(s);
    s.send(1, "CROM|reversi");
    s.send(1, "INVT|tuan");
    expect("STRT carries the board size", s.send(2, "IACC|1"),
           {"2 IACC|0", "1 STRT|reversi|1|tuan|8,8", "2 STRT|reversi|2|minh|8,8"});
    expect("move that flanks nothing", s.send(1, "MOVE|0,0"), {"1 MOVE|16"});
    expect("occupied centre cell", s.send(1, "MOVE|3,3"), {"1 MOVE|15"});
    expect("first move flips one piece", s.send(1, "MOVE|2,3"),
           {"1 MOVE|0|1|3,3", "2 OPPM|2,3|1|3,3"});
    expect("RSGN is allowed out of turn", s.send(1, "RSGN"),
           {"1 RSGN|0", "1 OVER|LOSE|-16|984", "2 OVER|WIN|+16|1016"});
    expect("the room is gone after the game", s.send(3, "IACC|1"), {"3 IACC|7"});
    expect("the host can create a new room", s.send(1, "CROM|caro"), {"1 CROM|0|2"});
}

void testElo() {
    expectInt("equal ratings, A wins", store::updateElo(1000, 1000, 1.0).delta, 16);
    expectInt("equal ratings, draw", store::updateElo(1000, 1000, 0.5).delta, 0);
    expectInt("1000 beats 1200", store::updateElo(1000, 1200, 1.0).delta, 24);
    expectInt("1000 loses to 1200", store::updateElo(1000, 1200, 0.0).delta, -8);
    const store::EloResult e = store::updateElo(1000, 1200, 1.0);
    expectInt("new rating of A", e.newA, 1024);
    expectInt("new rating of B", e.newB, 1176);
}

}  // namespace

int main() {
    testAuthentication();
    testResetPassword();
    testLobbyInformation();
    testRoomAndInvitation();
    testInvitationLifecycle();
    testRandomMatching();
    testCaroGame();
    testReversiAndResign();
    testElo();
    std::cout << (g_checked - g_failed) << "/" << g_checked << " checks passed\n";
    return g_failed == 0 ? 0 : 1;
}
