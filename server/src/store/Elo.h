// server/src/store/Elo.h
#pragma once

namespace store {

struct EloResult {
    int delta;  // thay đổi của A; B nhận -delta
    int newA;
    int newB;
};

// docs/protocol.md, mục 10. A là bên 1, B là bên 2.
// scoreA: 1.0 nếu A thắng, 0.5 nếu hòa, 0.0 nếu A thua. k là hệ số K.
// delta = round(k * (scoreA - E_A)), .5 làm tròn ra xa 0; newA = A + delta, newB = B - delta,
// nên tổng điểm của hai người không đổi.
EloResult updateElo(int ratingA, int ratingB, double scoreA, int k = 32);

}  // namespace store
