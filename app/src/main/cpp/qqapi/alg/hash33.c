#include "qqapi.h"
#include <string.h>

int64_t qq_hash33(const char *s, int64_t seed) {
    int64_t h = seed;
    size_t len = strlen(s);
    for (size_t i = 0; i < len; i++) {
        h = ((h << 5) + h + (int64_t)(unsigned char)s[i]) & 0xFFFFFFFFLL;
        /* JS uses 32-bit signed overflow: -(1<<31)..(1<<31)-1 */
        if (h >= (1LL << 31)) h -= (1LL << 32);
        if (h < -(1LL << 31)) h += (1LL << 32);
    }
    return 2147483647LL & h;
}