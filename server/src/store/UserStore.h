// server/src/store/UserStore.h
#pragma once
#include <string>
#include <vector>

namespace store {

inline constexpr int kInitialElo = 1000;

struct RankEntry {
    std::string name;
    int elo = kInitialElo;
};

// Kho tài khoản và điểm Elo. Mỗi tài khoản có một điểm RIÊNG cho từng game
// ("caro", "reversi"), đều bắt đầu từ 1000 (docs/protocol.md, mục 10).
// core/ chỉ biết giao diện này; có hai cài đặt: FileUserStore và PgUserStore.
class UserStore {
public:
    virtual ~UserStore() = default;

    // False nếu tên đã tồn tại (hoặc lỗi lưu trữ). recoveryCode: mã để đặt lại mật khẩu (use case 16).
    virtual bool create(const std::string& name, const std::string& password,
                        const std::string& recoveryCode) = 0;
    virtual bool checkPassword(const std::string& name, const std::string& password) const = 0;

    // Use case 16. Nếu `name` tồn tại và mã đúng: đổi mật khẩu, thay mã bằng newCode, trả true.
    // Tài khoản không có mã (tạo trước use case 16) không bao giờ khớp.
    virtual bool resetPassword(const std::string& name, const std::string& code,
                               const std::string& newPassword, const std::string& newCode) = 0;

    // Điểm của `name` cho `game`; 1000 nếu không có user hoặc game lạ.
    virtual int elo(const std::string& name, const std::string& game) const = 0;
    virtual void setElo(const std::string& name, const std::string& game, int elo) = 0;

    // MỌI tài khoản, xếp theo điểm của `game` giảm dần; bằng điểm thì theo tên tăng dần.
    virtual std::vector<RankEntry> ranking(const std::string& game) const = 0;
};

}  // namespace store
