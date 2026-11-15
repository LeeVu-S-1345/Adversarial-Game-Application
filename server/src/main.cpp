// server/src/main.cpp
#include <cstdint>
#include <cstdlib>
#include <iostream>
#include <memory>
#include <string>

#include "core/Hub.h"
#include "net/Server.h"
#include "store/FileUserStore.h"
#include "util/Logger.h"

#ifdef NETPRO_HAVE_PG
#include "store/PgUserStore.h"
#endif

namespace {

std::string envOr(const char* name, const char* fallback) {
    const char* v = std::getenv(name);
    return v && *v ? v : fallback;
}

}  // namespace

// Cách chạy:  ./server [port]            (mặc định 5000)
//
// Biến môi trường:
//   NETPRO_LOG_FILE    file log message (mặc định "server.log")
//   NETPRO_USERS_FILE  file lưu tài khoản và Elo (mặc định "users.db")
//   PG_CONNINFO        nếu được đặt VÀ server build kèm libpq thì dùng PostgreSQL thay cho file, vd
//                      "host=localhost port=5432 dbname=netpro user=netpro password=netpro"
int main(int argc, char** argv) {
    const std::uint16_t port = argc > 1 ? static_cast<std::uint16_t>(std::atoi(argv[1])) : 5000;

    std::unique_ptr<store::UserStore> users;
    const char* pg = std::getenv("PG_CONNINFO");
    if (pg && *pg) {
#ifdef NETPRO_HAVE_PG
        auto db = std::make_unique<store::PgUserStore>(pg);
        if (!db->load()) {
            std::cerr << "Cannot connect to PostgreSQL. Check PG_CONNINFO and that the database "
                         "is running.\n";
            return 1;
        }
        std::cout << "Storage: PostgreSQL\n";
        users = std::move(db);
#else
        std::cerr << "PG_CONNINFO is set but this server was built without libpq.\n";
        return 1;
#endif
    } else {
        const std::string path = envOr("NETPRO_USERS_FILE", "users.db");
        auto file = std::make_unique<store::FileUserStore>(path);
        if (!file->load()) {
            std::cerr << "Cannot read " << path << "\n";
            return 1;
        }
        std::cout << "Storage: file " << path << "\n";
        users = std::move(file);
    }

    util::Logger log(envOr("NETPRO_LOG_FILE", "server.log"));
    core::Hub hub(*users);
    net::Server server(hub, log, port);
    if (!server.start()) return 1;

    std::cout << "Server listening on port " << port << std::endl;
    server.run();
    return 0;
}
