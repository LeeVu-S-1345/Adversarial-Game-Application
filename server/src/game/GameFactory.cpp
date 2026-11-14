// server/src/game/GameFactory.cpp
#include "GameFactory.h"

#include "Gomoku.h"
#include "Reversi.h"

namespace game {

std::unique_ptr<IGame> createGame(const std::string& type) {
    if (type == "GOMOKU") return std::make_unique<Gomoku>();
    if (type == "REVERSI") return std::make_unique<Reversi>();
    return nullptr;
}

}  // namespace game