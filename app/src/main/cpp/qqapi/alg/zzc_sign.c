#include "qqapi.h"
#include <mbedtls/sha1.h>
#include <string.h>
#include <stdio.h>

static const int PART_1_INDEXES[] = {23, 14, 6, 36, 16, 7, 19};
static const int PART_2_INDEXES[] = {16, 1, 32, 12, 19, 27, 8, 5};
static const int SCRAMBLE_VALUES[] = {
    89, 39, 179, 150, 218, 82, 58, 252, 177, 52,
    186, 123, 120, 64, 242, 133, 143, 161, 121, 179,
};

int qq_sha1_hex(const uint8_t *p, size_t n, char out[41]) {
    uint8_t digest[20];
    mbedtls_sha1(p, n, digest);
    for (int i = 0; i < 20; i++) {
        out[i * 2] = "0123456789abcdef"[digest[i] >> 4];
        out[i * 2 + 1] = "0123456789abcdef"[digest[i] & 15];
    }
    out[40] = '\0';
    return 0;
}

int qq_zzc_sign(const uint8_t *payload, size_t len, char *out) {
    char hashHex[41];
    qq_sha1_hex(payload, len, hashHex);
    /* Uppercase */
    for (int i = 0; i < 40; i++) {
        if (hashHex[i] >= 'a' && hashHex[i] <= 'f') hashHex[i] -= 32;
    }

    char part1[8], part2[9];
    for (int i = 0; i < 7; i++) part1[i] = hashHex[PART_1_INDEXES[i]];
    part1[7] = '\0';
    for (int i = 0; i < 8; i++) part2[i] = hashHex[PART_2_INDEXES[i]];
    part2[8] = '\0';

    uint8_t part3[20];
    for (int i = 0; i < 20; i++) {
        int pair;
        sscanf(hashHex + i * 2, "%2x", &pair);
        part3[i] = (uint8_t)(SCRAMBLE_VALUES[i] ^ pair);
    }
    char b64raw[32];
    size_t b64len = qq_b64_encode(part3, 20, b64raw);
    char b64[32];
    size_t j = 0;
    for (size_t i = 0; i < b64len; i++) {
        if (b64raw[i] == '/' || b64raw[i] == '\\' || b64raw[i] == '+' || b64raw[i] == '=') continue;
        b64[j++] = b64raw[i];
    }
    b64[j] = '\0';

    char buf[128];
    snprintf(buf, sizeof(buf), "zzc%s%s%s", part1, b64, part2);
    size_t outlen = strlen(buf);
    for (size_t i = 0; i < outlen; i++) {
        if (buf[i] >= 'A' && buf[i] <= 'Z') buf[i] += 32;
    }
    memcpy(out, buf, outlen + 1);
    return (int)outlen;
}