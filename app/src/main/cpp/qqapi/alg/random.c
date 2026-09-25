#include "qqapi.h"
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

static uint64_t rng_state = 0;

static void seed_rng(void) {
    if (rng_state == 0) {
        rng_state = (uint64_t)time(NULL) ^ ((uint64_t)getpid() << 32);
        rng_state ^= (uint64_t)(uintptr_t)&rng_state;
        rng_state |= 0x123456789abcdef0ULL;
    }
}

void qq_random_bytes(uint8_t *out, size_t n) {
    seed_rng();
    for (size_t i = 0; i < n; i++) {
        rng_state ^= rng_state << 13;
        rng_state ^= rng_state >> 7;
        rng_state ^= rng_state << 17;
        out[i] = (uint8_t)(rng_state & 0xff);
    }
}

uint64_t qq_rand_u64(void) {
    uint8_t b[8];
    qq_random_bytes(b, 8);
    uint64_t v = 0;
    for (int i = 0; i < 8; i++) v = (v << 8) | b[i];
    return v;
}

int qq_rand_int(int min, int max) {
    if (max < min) return min;
    uint64_t range = (uint64_t)(max - min) + 1;
    return (int)(min + (qq_rand_u64() % range));
}