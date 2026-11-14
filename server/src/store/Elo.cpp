// server/src/store/Elo.cpp
#include "Elo.h"

#include <cmath>

namespace store {

EloResult updateElo(int ratingA, int ratingB, double scoreA, int k) {
    // Xác suất A thắng theo công thức Elo.
    const double expectedA = 1.0 / (1.0 + std::pow(10.0, (ratingB - ratingA) / 400.0));
    const double scoreB = 1.0 - scoreA;
    const double expectedB = 1.0 - expectedA;

    EloResult r;
    r.newA = static_cast<int>(std::lround(ratingA + k * (scoreA - expectedA)));
    r.newB = static_cast<int>(std::lround(ratingB + k * (scoreB - expectedB)));
    return r;
}

}  // namespace store