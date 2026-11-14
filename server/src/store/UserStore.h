// server/src/store/UserStore.h
#pragma once
#include <cstddef>
#include <string>
#include <vector>

struct pg_conn;  // libpq: PGconn là typedef của struct pg_conn (khai báo ở phạm vi toàn cục)

namespace store {

struct User {
    std::string name;
    std::string password;  // lưu thẳng (đủ cho bài tập); muốn an toàn thì băm trước khi lưu
    int elo = 1000;
};

// Lưu tài khoản và điểm Elo trong PostgreSQL (qua libpq).
// Giao diện giữ nguyên như bản dùng file, nên core/ không phải sửa gì.
// Lưu ý: mọi lệnh gọi libpq ở đây là đồng bộ (chặn). Với server một luồng và dữ liệu nhỏ
// thì chấp nhận được; mỗi truy vấn thường chỉ mất vài mili giây.
class UserStore {
public:
    // conninfo: chuỗi kết nối libpq, vd "host=localhost port=5432 dbname=netpro user=netpro password=..."
    explicit UserStore(std::string conninfo);
    ~UserStore();
    UserStore(const UserStore&) = delete;
    UserStore& operator=(const UserStore&) = delete;

    // Kết nối và tạo bảng users nếu chưa có. False nếu không kết nối được.
    bool load();

    // False nếu tên đã tồn tại (hoặc lỗi database; lỗi được in ra stderr).
    bool create(const std::string& name, const std::string& password);
    bool checkPassword(const std::string& name, const std::string& password) const;

    int elo(const std::string& name) const;  // 1000 nếu không có user
    void setElo(const std::string& name, int elo);

    // n người điểm cao nhất, giảm dần (cùng điểm thì theo tên).
    std::vector<User> top(std::size_t n) const;

private:
    bool ensureConnected() const;  // thử nối lại nếu mất kết nối

    std::string conninfo_;
    pg_conn* conn_ = nullptr;
};

}  // namespace store