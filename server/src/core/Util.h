// server/src/core/Util.h
#pragma once
#include <charconv>
#include <string>
#include <system_error>

namespace core {

// Số nguyên thập phân, dùng hết chuỗi. "" , "3x", "+3", " 3" đều là sai.
inline bool parseInt(const std::string& s, int& v) {
    if (s.empty()) return false;
    const char* end = s.data() + s.size();
    auto [p, ec] = std::from_chars(s.data(), end, v);
    return ec == std::errc() && p == end;
}

}  // namespace core
