// server/src/proto/Message.h
#pragma once
#include <string>
#include <vector>

namespace proto {

// Một dòng "CMD|f1|f2|...": cmd là token đầu, fields là các token còn lại.
// Field rỗng được GIỮ NGUYÊN (vd "OPPM|2,3|" -> cmd=OPPM, fields={"2,3",""}).
struct Message {
    std::string cmd;
    std::vector<std::string> fields;
};

}  // namespace proto