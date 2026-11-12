// server/src/net/Server.h
#pragma once
#include <cstdint>
#include <string>
#include <unordered_map>

#include "core/Hub.h"

namespace net {

// TCP server dùng poll() một luồng. Vì chỉ một luồng nên Hub luôn được gọi
// tuần tự và không cần lock.
class Server {
public:
    Server(core::Hub& hub, std::uint16_t port);
    ~Server();

    bool start();  // tạo socket, bind, listen; false nếu lỗi
    void run();    // vòng lặp chính, chặn đến khi lỗi

private:
    struct Connection {
        int fd;
        std::string inbuf;   // byte đã nhận nhưng chưa đủ một dòng
        std::string outbuf;  // byte chờ gửi
    };

    void acceptClients();
    bool readFrom(Connection& c);  // false = cần đóng kết nối
    bool flush(Connection& c);     // false = cần đóng kết nối
    void dispatch(const core::Out& out);
    void closeConn(int fd);

    core::Hub& hub_;
    std::uint16_t port_;
    int listenFd_ = -1;
    std::unordered_map<int, Connection> conns_;  // khóa chính là fd, cũng là ConnId
};

}  // namespace net