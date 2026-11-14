// server/src/store/Elo.h
#pragma once

namespace store {

struct EloResult {
    int newA;
    int newB;
};

// scoreA: 1.0 nếu A thắng, 0.5 nếu hòa, 0.0 nếu A thua. k là hệ số K.
EloResult updateElo(int ratingA, int ratingB, double scoreA, int k = 32);

}  // namespace store