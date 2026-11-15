// server/src/store/FileUserStore.h
#pragma once
#include <map>
#include <string>

#include "UserStore.h"

namespace store {

// Lưu tài khoản trong một file text, nạp hết vào RAM khi khởi động.
// Mỗi dòng: name|password|eloCaro|eloReversi
// Ghi atomic: ghi ra file tạm rồi rename, nên file không bao giờ dở dang.
// path rỗng = chỉ giữ trong RAM (dùng cho test).
class FileUserStore : public UserStore {
public:
    explicit FileUserStore(std::string path);

    // Đọc file nếu có. False nếu file tồn tại nhưng không đọc được.
    bool load();

    bool create(const std::string& name, const std::string& password) override;
    bool checkPassword(const std::string& name, const std::string& password) const override;
    int elo(const std::string& name, const std::string& game) const override;
    void setElo(const std::string& name, const std::string& game, int elo) override;
    std::vector<RankEntry> ranking(const std::string& game) const override;

private:
    struct User {
        std::string password;  // lưu thẳng (đủ cho bài tập)
        int caro = kInitialElo;
        int reversi = kInitialElo;
    };

    bool save() const;

    std::string path_;
    std::map<std::string, User> users_;  // map: duyệt theo tên tăng dần
};

}  // namespace store
