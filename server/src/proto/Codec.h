#pragma once
#include <optional>
#include <string>
#include <vector>

#include "Message.h"

namespace proto {

// line: một dòng đã bỏ "\r\n". Trả nullopt nếu dòng rỗng.
// Field rỗng được giữ nguyên ("OPPM|2,3|" -> fields = {"2,3", ""}).
std::optional<Message> parseLine(const std::string& line);

// Ghép thành một dòng (chưa có "\r\n"; lớp net tự thêm khi gửi).
std::string format(const std::string& cmd, const std::vector<std::string>& fields);

}  // namespace proto