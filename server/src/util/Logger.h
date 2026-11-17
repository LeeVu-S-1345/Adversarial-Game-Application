// server/src/util/Logger.h
#pragma once
#include <fstream>
#include <string>

namespace util {

// Ghi log mọi message nhận và gửi (docs/protocol.md, mục 14) ra màn hình và ra file.
// Mỗi dòng: "2026-11-14 21:03:09.481 [conn 5] RECV LOGI|minh|123456"
// Nhãn: CONNECT, RECV, SEND, CLOSE.
class Logger {
public:
    // path rỗng = chỉ in ra màn hình. File được mở ở chế độ ghi nối tiếp.
    explicit Logger(const std::string& path);

    void log(int conn, const char* label, const std::string& text = "");

private:
    std::ofstream file_;
};

}  // namespace util
