// server/src/game/GameFactory.h
#pragma once
#include <memory>
#include <string>

#include "IGame.h"

namespace game {

// type: "caro" hoặc "reversi" (đúng <game> trong docs/protocol.md, phân biệt hoa thường).
bool isKnownGame(const std::string& type);

// Trả nullptr nếu không nhận ra.
std::unique_ptr<IGame> createGame(const std::string& type);

}  // namespace game