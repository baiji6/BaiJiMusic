#include "qqapi.h"
#include <string.h>

static const char B64C[] =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

size_t qq_b64_encode(const uint8_t *in, size_t inlen, char *out) {
    size_t outlen = 0;
    size_t i = 0;
    while (i + 2 < inlen) {
        uint32_t n = (in[i] << 16) | (in[i + 1] << 8) | in[i + 2];
        out[outlen++] = B64C[(n >> 18) & 63];
        out[outlen++] = B64C[(n >> 12) & 63];
        out[outlen++] = B64C[(n >> 6) & 63];
        out[outlen++] = B64C[n & 63];
        i += 3;
    }
    if (i + 1 == inlen) {
        uint32_t n = in[i] << 16;
        out[outlen++] = B64C[(n >> 18) & 63];
        out[outlen++] = B64C[(n >> 12) & 63];
        out[outlen++] = '=';
        out[outlen++] = '=';
    } else if (i + 2 == inlen) {
        uint32_t n = (in[i] << 16) | (in[i + 1] << 8);
        out[outlen++] = B64C[(n >> 18) & 63];
        out[outlen++] = B64C[(n >> 12) & 63];
        out[outlen++] = B64C[(n >> 6) & 63];
        out[outlen++] = '=';
    }
    return outlen;
}

static int b64val(char c) {
    if (c >= 'A' && c <= 'Z') return c - 'A';
    if (c >= 'a' && c <= 'z') return c - 'a' + 26;
    if (c >= '0' && c <= '9') return c - '0' + 52;
    if (c == '+') return 62;
    if (c == '/') return 63;
    return -1;
}

size_t qq_b64_decode(const char *in, size_t inlen, uint8_t *out) {
    size_t outlen = 0;
    int buf = 0, bits = 0;
    for (size_t i = 0; i < inlen; i++) {
        if (in[i] == '=' || in[i] == '\n' || in[i] == '\r') continue;
        int v = b64val(in[i]);
        if (v < 0) continue;
        buf = (buf << 6) | v;
        bits += 6;
        if (bits >= 8) {
            bits -= 8;
            out[outlen++] = (uint8_t)((buf >> bits) & 0xff);
        }
    }
    return outlen;
}