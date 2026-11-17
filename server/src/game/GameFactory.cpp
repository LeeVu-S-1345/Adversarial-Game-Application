// server/src/game/GameFactory.cpp
#include "GameFactory.h"

#include "Gomoku.h"
#include "Reversi.h"

namespace game {

bool isKnownGame(const std::string& type) { return type == "caro" || type == "reversi"; }

std::unique_ptr<IGame> createGame(const std::string& type) {
    if (type == "caro") return std::make_unique<Gomoku>();
    if (type == "reversi") return std::make_unique<Reversi>();
    return nullptr;
}

}  // namespace game