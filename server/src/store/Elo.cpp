// server/src/store/Elo.cpp
#include "Elo.h"

#include <cmath>

namespace store {

EloResult updateElo(int ratingA, int ratingB, double scoreA, int k) {
    // Điểm kỳ vọng của A theo công thức Elo.
    const double expectedA = 1.0 / (1.0 + std::pow(10.0, (ratingB - ratingA) / 400.0));

    EloResult r;
    r.delta = static_cast<int>(std::lround(k * (scoreA - expectedA)));  // lround: .5 ra xa 0
    r.newA = ratingA + r.delta;
    r.newB = ratingB - r.delta;
    return r;
}

}  // namespace store
