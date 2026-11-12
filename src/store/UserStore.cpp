// server/src/store/UserStore.cpp
#include "UserStore.h"

#include <libpq-fe.h>

#include <cstdlib>
#include <cstring>
#include <iostream>

namespace store {

namespace {

// Tự gọi PQclear khi hết phạm vi, tránh rò rỉ bộ nhớ kết quả.
struct Result {
    PGresult* r;
    explicit Result(PGresult* p) : r(p) {}
    ~Result() { PQclear(r); }
    Result(const Result&) = delete;
    Result& operator=(const Result&) = delete;

    bool ok() const {
        const ExecStatusType s = PQresultStatus(r);
        return s == PGRES_COMMAND_OK || s == PGRES_TUPLES_OK;
    }
    int rows() const { return PQntuples(r); }
    const char* value(int row, int col) const { return PQgetvalue(r, row, col); }
};

// Luôn truyền giá trị qua tham số $1, $2... (không nối chuỗi vào câu SQL) để chống SQL injection.
Result query(PGconn* c, const char* sql, const std::vector<std::string>& params) {
    std::vector<const char*> values;
    values.reserve(params.size());
    for (const auto& p : params) values.push_back(p.c_str());
    return Result(PQexecParams(c, sql, static_cast<int>(values.size()), nullptr, values.data(),
                               nullptr, nullptr, 0));
}

void logError(const char* what, PGconn* c) {
    std::cerr << "UserStore: " << what << ": " << PQerrorMessage(c) << std::flush;
}

}  // namespace

UserStore::UserStore(std::string conninfo) : conninfo_(std::move(conninfo)) {}

UserStore::~UserStore() {
    if (conn_) PQfinish(conn_);
}

bool UserStore::load() {
    if (conn_) PQfinish(conn_);
    conn_ = PQconnectdb(conninfo_.c_str());
    if (PQstatus(conn_) != CONNECTION_OK) {
        logError("cannot connect", conn_);
        return false;
    }
    Result r(PQexec(conn_,
                    "CREATE TABLE IF NOT EXISTS users ("
                    "  name     TEXT PRIMARY KEY,"
                    "  password TEXT NOT NULL,"
                    "  elo      INTEGER NOT NULL DEFAULT 1000)"));
    if (!r.ok()) {
        logError("cannot create table", conn_);
        return false;
    }
    return true;
}

bool UserStore::ensureConnected() const {
    if (!conn_) return false;
    if (PQstatus(conn_) == CONNECTION_OK) return true;
    PQreset(conn_);  // mất kết nối (vd database khởi động lại): thử nối lại một lần
    return PQstatus(conn_) == CONNECTION_OK;
}

bool UserStore::create(const std::string& name, const std::string& password) {
    if (!ensureConnected()) return false;
    Result r = query(conn_,
                     "INSERT INTO users(name, password) VALUES($1, $2) "
                     "ON CONFLICT (name) DO NOTHING",
                     {name, password});
    if (!r.ok()) {
        logError("create failed", conn_);
        return false;
    }
    // PQcmdTuples là số dòng thực sự được thêm: "0" nghĩa là tên đã tồn tại.
    return std::strcmp(PQcmdTuples(r.r), "1") == 0;
}

bool UserStore::checkPassword(const std::string& name, const std::string& password) const {
    if (!ensureConnected()) return false;
    Result r = query(conn_, "SELECT 1 FROM users WHERE name = $1 AND password = $2",
                     {name, password});
    return r.ok() && r.rows() == 1;
}

int UserStore::elo(const std::string& name) const {
    const int kDefault = User{}.elo;
    if (!ensureConnected()) return kDefault;
    Result r = query(conn_, "SELECT elo FROM users WHERE name = $1", {name});
    if (!r.ok() || r.rows() != 1) return kDefault;
    return std::atoi(r.value(0, 0));
}

void UserStore::setElo(const std::string& name, int elo) {
    if (!ensureConnected()) return;
    Result r = query(conn_, "UPDATE users SET elo = $2 WHERE name = $1",
                     {name, std::to_string(elo)});
    if (!r.ok()) logError("setElo failed", conn_);
}

std::vector<User> UserStore::top(std::size_t n) const {
    std::vector<User> v;
    if (!ensureConnected()) return v;
    Result r = query(conn_,
                     "SELECT name, password, elo FROM users "
                     "ORDER BY elo DESC, name ASC LIMIT $1",
                     {std::to_string(n)});
    if (!r.ok()) return v;
    for (int i = 0; i < r.rows(); ++i) {
        User u;
        u.name = r.value(i, 0);
        u.password = r.value(i, 1);
        u.elo = std::atoi(r.value(i, 2));
        v.push_back(std::move(u));
    }
    return v;
}

}  // namespace store