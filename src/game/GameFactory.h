// server/src/game/GameFactory.h
#pragma once
#include <memory>
#include <string>

#include "IGame.h"

namespace game {

// type: "GOMOKU" hoặc "REVERSI" (đổi chuỗi cho khớp tên game trong protocol).
// Trả nullptr nếu không nhận ra.
std::unique_ptr<IGame> createGame(const std::string& type);

}  // namespace game