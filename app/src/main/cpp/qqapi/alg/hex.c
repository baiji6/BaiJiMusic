#include "qqapi.h"

static const char HEXC[] = "0123456789abcdef";

size_t qq_hex_encode(const uint8_t *in, size_t inlen, char *out) {
    for (size_t i = 0; i < inlen; i++) {
        out[i * 2] = HEXC[in[i] >> 4];
        out[i * 2 + 1] = HEXC[in[i] & 0xf];
    }
    return inlen * 2;
}

static int hexval(char c) {
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    return -1;
}

size_t qq_hex_decode(const char *in, size_t inlen, uint8_t *out) {
    size_t n = 0;
    for (size_t i = 0; i + 1 < inlen; i += 2) {
        int hi = hexval(in[i]);
        int lo = hexval(in[i + 1]);
        if (hi < 0 || lo < 0) break;
        out[n++] = (uint8_t)((hi << 4) | lo);
    }
    return n;
}