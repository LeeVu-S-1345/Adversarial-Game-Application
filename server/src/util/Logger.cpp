// server/src/util/Logger.cpp
#include "Logger.h"

#include <chrono>
#include <cstdio>
#include <ctime>
#include <iostream>

namespace util {

Logger::Logger(const std::string& path) {
    if (path.empty()) return;
    file_.open(path, std::ios::app);
    if (!file_) std::cerr << "Logger: cannot open " << path << ", logging to terminal only\n";
}

void Logger::log(int conn, const char* label, const std::string& text) {
    using namespace std::chrono;
    const auto now = system_clock::now();
    const std::time_t secs = system_clock::to_time_t(now);
    const long ms =
        static_cast<long>(duration_cast<milliseconds>(now.time_since_epoch()).count() % 1000);

    std::tm tm{};
    localtime_r(&secs, &tm);
    char stamp[32];
    std::strftime(stamp, sizeof(stamp), "%Y-%m-%d %H:%M:%S", &tm);
    char head[80];
    std::snprintf(head, sizeof(head), "%s.%03ld [conn %d] %s", stamp, ms, conn, label);

    std::string line = head;
    if (!text.empty()) {
        line += ' ';
        line += text;
    }
    std::cout << line << std::endl;
    if (file_) file_ << line << std::endl;  // endl: xả ngay, không mất log khi server bị tắt
}

}  // namespace util
