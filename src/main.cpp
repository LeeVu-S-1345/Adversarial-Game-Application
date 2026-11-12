// server/src/main.cpp
#include <cstdint>
#include <cstdlib>
#include <iostream>

#include "core/Hub.h"
#include "net/Server.h"
#include "store/UserStore.h"

int main(int argc, char** argv) {
    const std::uint16_t port = argc > 1 ? static_cast<std::uint16_t>(std::atoi(argv[1])) : 5000;

    // Chuỗi kết nối PostgreSQL lấy từ biến môi trường PG_CONNINFO, ví dụ:
    //   export PG_CONNINFO="host=localhost port=5432 dbname=netpro user=netpro password=netpro"
    // Không đặt thì dùng mặc định của libpq (user hệ thống, database "netpro").
    const char* env = std::getenv("PG_CONNINFO");
    store::UserStore users(env ? env : "dbname=netpro");
    if (!users.load()) {
        std::cerr << "Cannot connect to PostgreSQL. Check PG_CONNINFO and that the server is running."
                  << std::endl;
        return 1;
    }

    core::Hub hub(users);
    net::Server server(hub, port);
    if (!server.start()) return 1;

    std::cout << "Server listening on port " << port << std::endl;
    server.run();
    return 0;
}