// server/src/net/Server.cpp
#include "Server.h"

#include <arpa/inet.h>
#include <fcntl.h>
#include <netinet/in.h>
#include <poll.h>
#include <sys/socket.h>
#include <unistd.h>

#include <cerrno>
#include <cstdio>
#include <vector>

#include "proto/Codec.h"

namespace net {

namespace {

constexpr std::size_t kMaxLine = 4096;  // chống client gửi rác mà không xuống dòng

bool setNonBlocking(int fd) {
    int flags = fcntl(fd, F_GETFL, 0);
    return flags >= 0 && fcntl(fd, F_SETFL, flags | O_NONBLOCK) == 0;
}

}  // namespace

Server::Server(core::Hub& hub, std::uint16_t port) : hub_(hub), port_(port) {}

Server::~Server() {
    for (auto& [fd, c] : conns_) close(fd);
    if (listenFd_ >= 0) close(listenFd_);
}

bool Server::start() {
    listenFd_ = socket(AF_INET, SOCK_STREAM, 0);
    if (listenFd_ < 0) {
        perror("socket");
        return false;
    }
    int yes = 1;
    setsockopt(listenFd_, SOL_SOCKET, SO_REUSEADDR, &yes, sizeof(yes));

    sockaddr_in addr{};
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = htonl(INADDR_ANY);
    addr.sin_port = htons(port_);
    if (bind(listenFd_, reinterpret_cast<sockaddr*>(&addr), sizeof(addr)) < 0) {
        perror("bind");
        return false;
    }
    if (listen(listenFd_, 64) < 0) {
        perror("listen");
        return false;
    }
    return setNonBlocking(listenFd_);
}

void Server::run() {
    while (true) {
        // Dựng lại danh sách fd mỗi vòng: socket nghe + mọi client.
        std::vector<pollfd> pfds;
        pfds.push_back({listenFd_, POLLIN, 0});
        for (const auto& [fd, c] : conns_) {
            short events = POLLIN;
            if (!c.outbuf.empty()) events |= POLLOUT;  // chỉ quan tâm ghi khi có dữ liệu chờ
            pfds.push_back({fd, events, 0});
        }

        if (poll(pfds.data(), pfds.size(), -1) < 0) {
            if (errno == EINTR) continue;
            perror("poll");
            break;
        }

        std::vector<int> toClose;  // đóng sau khi duyệt xong, tránh sửa conns_ giữa chừng
        for (const auto& p : pfds) {
            if (p.revents == 0) continue;
            if (p.fd == listenFd_) {
                acceptClients();
                continue;
            }
            auto it = conns_.find(p.fd);
            if (it == conns_.end()) continue;
            Connection& c = it->second;

            if (p.revents & (POLLIN | POLLHUP | POLLERR)) {
                if (!readFrom(c)) {
                    toClose.push_back(p.fd);
                    continue;
                }
            }
            if (p.revents & POLLOUT) {
                if (!flush(c)) toClose.push_back(p.fd);
            }
        }
        for (int fd : toClose) closeConn(fd);
    }
}

void Server::acceptClients() {
    while (true) {
        int fd = accept(listenFd_, nullptr, nullptr);
        if (fd < 0) break;  // EAGAIN: hết kết nối chờ
        if (!setNonBlocking(fd)) {
            close(fd);
            continue;
        }
        conns_.emplace(fd, Connection{fd, {}, {}});
    }
}

bool Server::readFrom(Connection& c) {
    char buf[4096];
    ssize_t n = recv(c.fd, buf, sizeof(buf), 0);
    if (n == 0) return false;  // client đóng kết nối
    if (n < 0) return errno == EAGAIN || errno == EWOULDBLOCK || errno == EINTR;

    c.inbuf.append(buf, static_cast<std::size_t>(n));

    // Cắt từng dòng kết thúc bằng "\r\n" rồi đưa cho Hub.
    std::size_t pos;
    while ((pos = c.inbuf.find("\r\n")) != std::string::npos) {
        std::string line = c.inbuf.substr(0, pos);
        c.inbuf.erase(0, pos + 2);
        if (auto msg = proto::parseLine(line)) {
            core::Out out;
            hub_.onMessage(c.fd, *msg, out);
            dispatch(out);
        }
    }
    return c.inbuf.size() <= kMaxLine;
}

bool Server::flush(Connection& c) {
    if (c.outbuf.empty()) return true;
    ssize_t n = send(c.fd, c.outbuf.data(), c.outbuf.size(), MSG_NOSIGNAL);
    if (n < 0) return errno == EAGAIN || errno == EWOULDBLOCK || errno == EINTR;
    c.outbuf.erase(0, static_cast<std::size_t>(n));
    return true;
}

void Server::dispatch(const core::Out& out) {
    for (const auto& item : out) {
        auto it = conns_.find(item.conn);
        if (it == conns_.end()) continue;  // client đã đi
        it->second.outbuf += item.line;
        it->second.outbuf += "\r\n";
    }
}

void Server::closeConn(int fd) {
    // Báo cho Hub dọn trước, rồi mới đóng socket.
    core::Out out;
    hub_.onClose(fd, out);
    close(fd);
    conns_.erase(fd);
    dispatch(out);  // gửi thông báo cho những người còn lại (vd OVER cho đối thủ)
}

}  // namespace net