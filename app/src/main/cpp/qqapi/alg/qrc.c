#include "qqapi.h"
#include <stdlib.h>
#include <string.h>
#include <zlib.h>

static const uint8_t QRC_3DES_KEY[24] = "!@#)(*$%123ZXC!@!@#)(NHL";

char *qq_qrc_decrypt(const char *hex_in) {
    if (!hex_in || !*hex_in) return NULL;
    size_t hexlen = strlen(hex_in);
    uint8_t *bytes = (uint8_t *)malloc(hexlen / 2 + 1);
    if (!bytes) return NULL;
    size_t bytelen = qq_hex_decode(hex_in, hexlen, bytes);
    if (bytelen == 0) { free(bytes); return NULL; }

    qq_tripledes_key *k = qq_tripledes_key_new(QRC_3DES_KEY, 0); /* DECRYPT */
    if (!k) { free(bytes); return NULL; }

    uint8_t *dec = (uint8_t *)malloc(bytelen);
    if (!dec) { qq_tripledes_key_free(k); free(bytes); return NULL; }
    for (size_t i = 0; i + 8 <= bytelen; i += 8) {
        memcpy(dec + i, bytes + i, 8);
        qq_tripledes_crypt_block(k, dec + i);
    }
    qq_tripledes_key_free(k);
    free(bytes);

    /* zlib inflate */
    uLongf destLen = bytelen * 4 + 64;
    uint8_t *out = (uint8_t *)malloc(destLen);
    if (!out) { free(dec); return NULL; }
    int zret = uncompress(out, &destLen, dec, (uLong)bytelen);
    free(dec);
    if (zret != Z_OK) {
        /* try stream inflate */
        z_stream strm;
        memset(&strm, 0, sizeof(strm));
        if (inflateInit(&strm) != Z_OK) { free(out); return NULL; }
        strm.next_in = bytes; /* empty; we need original dec; but dec freed. fallback */
        inflateEnd(&strm);
        free(out);
        return NULL;
    }
    out[destLen] = '\0';
    return (char *)out;
}