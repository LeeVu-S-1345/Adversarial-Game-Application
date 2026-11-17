// server/src/store/PgUserStore.cpp
#include "PgUserStore.h"

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
Result query(PGconn* c, const std::string& sql, const std::vector<std::string>& params) {
    std::vector<const char*> values;
    values.reserve(params.size());
    for (const auto& p : params) values.push_back(p.c_str());
    return Result(PQexecParams(c, sql.c_str(), static_cast<int>(values.size()), nullptr,
                               values.data(), nullptr, nullptr, 0));
}

void logError(const char* what, PGconn* c) {
    std::cerr << "PgUserStore: " << what << ": " << PQerrorMessage(c) << std::flush;
}

// Tên cột chứa điểm của một game. Chỉ trả về hằng số cố định (whitelist), nên
// ghép vào câu SQL là an toàn; nullptr nếu game lạ.
const char* eloColumn(const std::string& game) {
    if (game == "caro") return "elo_caro";
    if (game == "reversi") return "elo_reversi";
    return nullptr;
}

}  // namespace

PgUserStore::PgUserStore(std::string conninfo) : conninfo_(std::move(conninfo)) {}

PgUserStore::~PgUserStore() {
    if (conn_) PQfinish(conn_);
}

bool PgUserStore::load() {
    if (conn_) PQfinish(conn_);
    conn_ = PQconnectdb(conninfo_.c_str());
    if (PQstatus(conn_) != CONNECTION_OK) {
        logError("cannot connect", conn_);
        return false;
    }
    // Không in các NOTICE vô hại (vd "relation users already exists, skipping") ra màn hình.
    PQsetNoticeProcessor(conn_, [](void*, const char*) {}, nullptr);
    // Bảng mới có hai cột điểm. Bảng cũ (chỉ có cột `elo`) được thêm hai cột này;
    // cột `elo` cũ để nguyên, không dùng nữa.
    static const char* const kSetup[] = {
        "CREATE TABLE IF NOT EXISTS users ("
        "  name        TEXT PRIMARY KEY,"
        "  password    TEXT NOT NULL,"
        "  elo_caro    INTEGER NOT NULL DEFAULT 1000,"
        "  elo_reversi INTEGER NOT NULL DEFAULT 1000)",
        "ALTER TABLE users ADD COLUMN IF NOT EXISTS elo_caro INTEGER NOT NULL DEFAULT 1000",
        "ALTER TABLE users ADD COLUMN IF NOT EXISTS elo_reversi INTEGER NOT NULL DEFAULT 1000",
    };
    for (const char* sql : kSetup) {
        Result r(PQexec(conn_, sql));
        if (!r.ok()) {
            logError("cannot set up table", conn_);
            return false;
        }
    }
    return true;
}

bool PgUserStore::ensureConnected() const {
    if (!conn_) return false;
    if (PQstatus(conn_) == CONNECTION_OK) return true;
    PQreset(conn_);  // mất kết nối (vd database khởi động lại): thử nối lại một lần
    return PQstatus(conn_) == CONNECTION_OK;
}

bool PgUserStore::create(const std::string& name, const std::string& password) {
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

bool PgUserStore::checkPassword(const std::string& name, const std::string& password) const {
    if (!ensureConnected()) return false;
    Result r = query(conn_, "SELECT 1 FROM users WHERE name = $1 AND password = $2",
                     {name, password});
    return r.ok() && r.rows() == 1;
}

int PgUserStore::elo(const std::string& name, const std::string& game) const {
    const char* col = eloColumn(game);
    if (!col || !ensureConnected()) return kInitialElo;
    Result r = query(conn_, std::string("SELECT ") + col + " FROM users WHERE name = $1", {name});
    if (!r.ok() || r.rows() != 1) return kInitialElo;
    return std::atoi(r.value(0, 0));
}

void PgUserStore::setElo(const std::string& name, const std::string& game, int elo) {
    const char* col = eloColumn(game);
    if (!col || !ensureConnected()) return;
    Result r = query(conn_, std::string("UPDATE users SET ") + col + " = $2 WHERE name = $1",
                     {name, std::to_string(elo)});
    if (!r.ok()) logError("setElo failed", conn_);
}

std::vector<RankEntry> PgUserStore::ranking(const std::string& game) const {
    std::vector<RankEntry> v;
    const char* col = eloColumn(game);
    if (!col || !ensureConnected()) return v;
    // COLLATE "C": so sánh tên theo byte, giống FileUserStore, không phụ thuộc locale của database.
    Result r = query(conn_,
                     std::string("SELECT name, ") + col + " FROM users ORDER BY " + col +
                         " DESC, name COLLATE \"C\" ASC",
                     {});
    if (!r.ok()) return v;
    for (int i = 0; i < r.rows(); ++i) v.push_back({r.value(i, 0), std::atoi(r.value(i, 1))});
    return v;
}

}  // namespace store
