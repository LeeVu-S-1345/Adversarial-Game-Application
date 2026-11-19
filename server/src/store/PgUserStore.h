// server/src/store/PgUserStore.h
#pragma once
#include <string>

#include "UserStore.h"

struct pg_conn;  // libpq: PGconn là typedef của struct pg_conn (khai báo ở phạm vi toàn cục)

namespace store {

// Lưu tài khoản và điểm Elo trong PostgreSQL (qua libpq).
// Chỉ được biên dịch khi CMake tìm thấy libpq (NETPRO_HAVE_PG).
// Mọi lệnh gọi libpq ở đây là đồng bộ (chặn). Với server một luồng và dữ liệu nhỏ
// thì chấp nhận được; mỗi truy vấn thường chỉ mất vài mili giây.
class PgUserStore : public UserStore {
public:
    // conninfo: chuỗi kết nối libpq, vd "host=localhost port=5432 dbname=netpro user=netpro password=..."
    explicit PgUserStore(std::string conninfo);
    ~PgUserStore() override;
    PgUserStore(const PgUserStore&) = delete;
    PgUserStore& operator=(const PgUserStore&) = delete;

    // Kết nối và tạo/nâng cấp bảng users. False nếu không kết nối được.
    bool load();

    bool create(const std::string& name, const std::string& password,
                const std::string& recoveryCode) override;
    bool checkPassword(const std::string& name, const std::string& password) const override;
    bool resetPassword(const std::string& name, const std::string& code,
                       const std::string& newPassword, const std::string& newCode) override;
    int elo(const std::string& name, const std::string& game) const override;
    void setElo(const std::string& name, const std::string& game, int elo) override;
    std::vector<RankEntry> ranking(const std::string& game) const override;

private:
    bool ensureConnected() const;  // thử nối lại nếu mất kết nối

    std::string conninfo_;
    pg_conn* conn_ = nullptr;
};

}  // namespace store
