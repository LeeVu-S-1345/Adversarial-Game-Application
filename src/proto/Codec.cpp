// server/src/proto/Codec.cpp
#include "Codec.h"

namespace proto {

    std::optional<Message> parseLine(const std::string& line) {
        if (line.empty()) return std::nullopt;

        // Tự tách theo '|' (không dùng getline) để không làm rơi field cuối rỗng.
        std::vector<std::string> tokens;
        std::size_t start = 0;
        while (true) {
            std::size_t pos = line.find('|', start);
            if (pos == std::string::npos) {
                tokens.push_back(line.substr(start));
                break;
            }
            tokens.push_back(line.substr(start, pos - start));
            start = pos + 1;
        }

        Message m;
        m.cmd = std::move(tokens.front());
        m.fields.assign(std::make_move_iterator(tokens.begin() + 1),
                        std::make_move_iterator(tokens.end()));
        return m;
    }

    std::string format(const std::string& cmd, const std::vector<std::string>& fields) {
        std::string s = cmd;
        for (const auto& f : fields) {
            s += '|';
            s += f;
        }
        return s;
    }
}  // namespace proto