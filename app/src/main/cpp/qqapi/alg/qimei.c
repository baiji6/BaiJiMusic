#include "qqapi.h"
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <time.h>

#include <mbedtls/rsa.h>
#include <mbedtls/pk.h>
#include <mbedtls/aes.h>
#include <mbedtls/base64.h>
#include <mbedtls/md5.h>

/* Lightweight JSON string extraction. Returns malloc'd string or NULL. */
static char *json_str(const char *json, const char *key) {
    size_t klen = strlen(key);
    char pat[64];
    snprintf(pat, sizeof(pat), "\"%s\"", key);
    const char *p = strstr(json, pat);
    if (!p) return NULL;
    p += strlen(pat);
    while (*p && (*p == ' ' || *p == ':' || *p == '\t')) p++;
    if (*p == '{' || *p == '[') return NULL;
    if (*p == '"') {
        p++;
        size_t cap = 64, len = 0;
        char *out = (char *)malloc(cap);
        while (*p && *p != '"') {
            if (*p == '\\') p++;
            if (len + 1 >= cap) { cap *= 2; out = (char *)realloc(out, cap); }
            out[len++] = *p++;
        }
        out[len] = '\0';
        return out;
    }
    /* number or literal */
    const char *start = p;
    size_t len = 0;
    while (*p && *p != ',' && *p != '}' && *p != ' ') { p++; len++; }
    char *out = (char *)malloc(len + 1);
    memcpy(out, start, len);
    out[len] = '\0';
    return out;
}

static char *json_nested_str(const char *json, const char *outer, const char *inner) {
    char pat[64];
    snprintf(pat, sizeof(pat), "\"%s\"", outer);
    const char *p = strstr(json, pat);
    if (!p) return NULL;
    /* find the object start after ': {' */
    const char *colon = strchr(p, ':');
    if (!colon) return NULL;
    const char *open = strchr(colon, '{');
    if (!open) return NULL;
    /* find matching close brace */
    int depth = 1;
    const char *q = open + 1;
    while (*q && depth > 0) {
        if (*q == '{') depth++;
        else if (*q == '}') depth--;
        q++;
        if (depth == 0) break;
    }
    /* extract inner value within the object [open+1, q) */
    char innerpat[64];
    snprintf(innerpat, sizeof(innerpat), "\"%s\"", inner);
    const char *ip = strstr(open + 1, innerpat);
    if (!ip || ip >= q) return NULL;
    const char *ipcolon = strchr(ip, ':');
    if (!ipcolon || ipcolon >= q) return NULL;
    const char *iv = ipcolon + 1;
    while (*iv == ' ' || *iv == '\t') iv++;
    if (*iv == '"') {
        iv++;
        size_t cap = 32, len = 0;
        char *out = (char *)malloc(cap);
        while (iv < q && *iv && *iv != '"') {
            if (len + 1 >= cap) { cap *= 2; out = (char *)realloc(out, cap); }
            out[len++] = *iv++;
        }
        out[len] = '\0';
        return out;
    }
    const char *start = iv;
    size_t len = 0;
    while (iv < q && *iv && *iv != ',' && *iv != '}') { iv++; len++; }
    char *out = (char *)malloc(len + 1);
    memcpy(out, start, len);
    out[len] = '\0';
    return out;
}

static const char PUBLIC_KEY_DER_B64[] =
    "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDEIxgwoutfwoJxcGQeedgP7FG9qaIuS0qzfR8gWkrkTZKM2iWHn2ajQpBRZjMSoSf6+KJGvar2ORhBfpDXyVtZCKpqLQ+FLkpncClKVIrBwv6PHyUvuCb0rIarmgDnzkfQAqVufEtR64iazGDKatvJ9y6B9NMbHddGSAUmRTCrHQIDAQAB";
static const char SECRET[] = "ZdJqM15EeO2zWc08";
static const char APP_KEY[] = "0AND0HD6FE4HY80F";
static const char CHANNEL_ID[] = "10003505";
static const char PACKAGE_ID[] = "com.tencent.qqmusic";

/* mbedtls 3.x 的 RSA 加密要求提供 f_rng 回调（用于生成随机填充字节）。
   这里用项目自带的 qq_random_bytes 包装成一个合法的 RNG。 */
static int qq_rng_cb(void *unused, unsigned char *out, size_t len) {
    (void)unused;
    qq_random_bytes(out, len);
    return 0;
}

static int rsa_pkcs1_encrypt(const uint8_t *in, size_t inlen, uint8_t *out, size_t *outlen) {
    uint8_t der[1024];
    size_t derlen = 0;
    mbedtls_base64_decode(der, sizeof(der), &derlen,
                          (const unsigned char *)PUBLIC_KEY_DER_B64, strlen(PUBLIC_KEY_DER_B64));
    mbedtls_pk_context pk;
    mbedtls_pk_init(&pk);
    int ret = mbedtls_pk_parse_public_key(&pk, der, derlen);
    if (ret != 0) { mbedtls_pk_free(&pk); return -1; }
    mbedtls_rsa_context *rsa = mbedtls_pk_rsa(pk);
    ret = mbedtls_rsa_pkcs1_encrypt(rsa, qq_rng_cb, NULL, inlen, in, out);
    if (ret == 0) *outlen = mbedtls_rsa_get_len(rsa);
    mbedtls_pk_free(&pk);
    return ret;
}

static void aes_cbc_encrypt(const uint8_t key[16], const uint8_t *in, size_t inlen, uint8_t *out) {
    mbedtls_aes_context ctx;
    mbedtls_aes_init(&ctx);
    mbedtls_aes_setkey_enc(&ctx, key, 128);
    size_t i = 0;
    unsigned char iv[16];
    memcpy(iv, key, 16);
    for (; i < inlen; i += 16) {
        for (int j = 0; j < 16; j++) out[i + j] = in[i + j] ^ iv[j];
        mbedtls_aes_crypt_ecb(&ctx, MBEDTLS_AES_ENCRYPT, out + i, out + i);
        memcpy(iv, out + i, 16);
    }
    mbedtls_aes_free(&ctx);
}

int qq_md5_hex(const uint8_t *p, size_t n, char out[33]) {
    uint8_t digest[16];
    mbedtls_md5(p, n, digest);
    for (int i = 0; i < 16; i++) {
        out[i * 2] = "0123456789abcdef"[digest[i] >> 4];
        out[i * 2 + 1] = "0123456789abcdef"[digest[i] & 15];
    }
    out[32] = '\0';
    return 0;
}

static void md5_concat(char *out, const char *const *parts, int n) {
    mbedtls_md5_context ctx;
    mbedtls_md5_init(&ctx);
    mbedtls_md5_starts(&ctx);
    for (int i = 0; i < n; i++)
        mbedtls_md5_update(&ctx, (const unsigned char *)parts[i], strlen(parts[i]));
    uint8_t digest[16];
    mbedtls_md5_finish(&ctx, digest);
    mbedtls_md5_free(&ctx);
    for (int i = 0; i < 16; i++) {
        out[i*2] = "0123456789abcdef"[digest[i] >> 4];
        out[i*2+1] = "0123456789abcdef"[digest[i] & 15];
    }
    out[32] = '\0';
}

static void random_beacon_id(char *out, size_t cap) {
    char buf[4096];
    size_t pos = 0;
    time_t t = time(NULL);
    struct tm *gmt = gmtime(&t);
    char monthStart[32];
    strftime(monthStart, sizeof(monthStart), "%Y-%m-01", gmt);
    int rand1 = qq_rand_int(100000, 999999);
    int rand2 = qq_rand_int(100000000, 999999999);
    static const int k1set[] = {1,2,13,14,17,18,21,22,25,26,29,30,33,34,37,38};
    for (int i = 1; i <= 40; i++) {
        int inK1 = 0;
        for (int k = 0; k < 16; k++) if (k1set[k] == i) { inK1 = 1; break; }
        if (inK1) {
            char seg[64];
            snprintf(seg, sizeof(seg), "k%d:%s%d.%d;", i, monthStart, rand1, rand2);
            size_t l = strlen(seg);
            if (pos + l < cap) { memcpy(out + pos, seg, l); pos += l; }
        } else if (i == 3) {
            const char seg[] = "k3:0000000000000000;";
            size_t l = strlen(seg);
            if (pos + l < cap) { memcpy(out + pos, seg, l); pos += l; }
        } else if (i == 4) {
            uint8_t b[8];
            qq_random_bytes(b, 8);
            char hex[17];
            for (int j = 0; j < 8; j++) hex[j*2]="0123456789abcdef"[(b[j]>>4)&15], hex[j*2+1]="0123456789abcdef"[b[j]&15];
            hex[16] = '\0';
            /* ensure non-zero first char */
            if (hex[0] == '0') hex[0] = '1';
            char seg[32];
            snprintf(seg, sizeof(seg), "k4:%s;", hex);
            size_t l = strlen(seg);
            if (pos + l < cap) { memcpy(out + pos, seg, l); pos += l; }
        } else {
            char seg[32];
            snprintf(seg, sizeof(seg), "k%d:%d;", i, qq_rand_int(0, 9999));
            size_t l = strlen(seg);
            if (pos + l < cap) { memcpy(out + pos, seg, l); pos += l; }
        }
    }
    out[pos] = '\0';
}

static void random_hex_str(char *out, size_t n) {
    uint8_t *b = (uint8_t *)malloc((n + 1) / 2);
    qq_random_bytes(b, (n + 1) / 2);
    char hex[256];
    qq_hex_encode(b, (n + 1) / 2, hex);
    free(b);
    for (size_t i = 0; i < n; i++) out[i] = hex[i];
    out[n] = '\0';
}

int qq_qimei_build(const char *device_json, qq_qimei_request *out) {
    memset(out, 0, sizeof(*out));
    char *brand = json_str(device_json, "brand");
    char *device = json_str(device_json, "device");
    char *model = json_str(device_json, "model");
    char *procVersion = json_str(device_json, "procVersion");
    char *androidId = json_str(device_json, "androidId");
    char *imei = json_str(device_json, "imei");
    char *release = json_nested_str(device_json, "version", "release");
    char *sdk = json_nested_str(device_json, "version", "sdk");

    if (!release) release = strdup("10");
    if (!sdk) sdk = strdup("29");
    if (!brand) brand = strdup("Xiaomi");
    if (!device) device = strdup("sagit");
    if (!model) model = strdup("MI 6");
    if (!procVersion) procVersion = strdup("Linux 5.4.0-54-generic (android-build@google.com)");
    if (!androidId) androidId = strdup("00000000");
    if (!imei) imei = strdup("000000000000000");

    /* ---- build payload ---- */
    char beacon[4096];
    random_beacon_id(beacon, sizeof(beacon));
    time_t now = time(NULL);
    int fixedRand = qq_rand_int(0, 14400);
    time_t upTime = now - fixedRand;
    struct tm *ut = gmtime(&upTime);
    char upTimeStr[64];
    strftime(upTimeStr, sizeof(upTimeStr), "%Y-%m-%d %H:%M:%S", ut);

    char payload[8192];
    snprintf(payload, sizeof(payload),
        "{\"androidId\":\"%s\",\"platformId\":1,\"appKey\":\"%s\",\"appVersion\":\"14.9.0.8\","
        "\"beaconIdSrc\":\"%s\",\"brand\":\"%s\",\"channelId\":\"%s\",\"cid\":\"\","
        "\"imei\":\"%s\",\"imsi\":\"\",\"mac\":\"\",\"model\":\"%s\",\"networkType\":\"unknown\","
        "\"oaid\":\"\",\"osVersion\":\"Android %s,level %s\",\"qimei\":\"\",\"qimei36\":\"\","
        "\"sdkVersion\":\"1.2.13.6\",\"targetSdkVersion\":\"33\",\"audit\":\"\","
        "\"userId\":\"{}\",\"packageId\":\"%s\",\"deviceType\":\"Phone\",\"sdkName\":\"\","
        "\"reserved\":\"{\\\"harmony\\\":\\\"0\\\",\\\"clone\\\":\\\"0\\\",\\\"containe\\\":\\\"\\\","
        "\\\"oz\\\":\\\"UhYmelwouA+V2nPWbOvLTgN2/m8jwGB+yUB5v9tysQg=\\\","
        "\\\"oo\\\":\\\"Xecjt+9S1+f8Pz2VLSxgpw==\\\",\\\"kelong\\\":\\\"0\\\","
        "\\\"uptimes\\\":\\\"%s\\\",\\\"multiUser\\\":\\\"0\\\",\\\"bod\\\":\\\"%s\\\","
        "\\\"dv\\\":\\\"%s\\\",\\\"firstLevel\\\":\\\"\\\",\\\"manufact\\\":\\\"%s\\\","
        "\\\"name\\\":\\\"%s\\\",\\\"host\\\":\\\"se.infra\\\",\\\"kernel\\\":\\\"%s\\\"}\""
        "}",
        androidId, APP_KEY, beacon, brand, CHANNEL_ID, imei, model, release, sdk, PACKAGE_ID,
        upTimeStr, brand, device, brand, model, procVersion);

    /* ---- crypto ---- */
    char cryptKeyHex[17], nonceHex[17];
    random_hex_str(cryptKeyHex, 16);
    random_hex_str(nonceHex, 16);

    uint8_t rsaout[256];
    size_t rsaoutlen = 0;
    if (rsa_pkcs1_encrypt((const uint8_t *)cryptKeyHex, 16, rsaout, &rsaoutlen) != 0) {
        goto fail;
    }
    char keyB64[512];
    size_t keyB64len = qq_b64_encode(rsaout, rsaoutlen, keyB64);
    keyB64[keyB64len] = '\0';

    /* AES-CBC payload (PKCS7 pad) */
    size_t plen = strlen(payload);
    size_t padlen = 16 - (plen % 16);
    size_t paddedLen = plen + padlen;
    uint8_t *padded = (uint8_t *)malloc(paddedLen);
    memcpy(padded, payload, plen);
    memset(padded + plen, (int)padlen, padlen);
    uint8_t *aesout = (uint8_t *)malloc(paddedLen);
    aes_cbc_encrypt((const uint8_t *)cryptKeyHex, padded, paddedLen, aesout);
    free(padded);
    char paramsB64[16384];
    size_t paramsB64len = qq_b64_encode(aesout, paddedLen, paramsB64);
    paramsB64[paramsB64len] = '\0';
    free(aesout);

    char extra[64];
    snprintf(extra, sizeof(extra), "{\"appKey\":\"%s\"}", APP_KEY);

    char tsms[32];
    snprintf(tsms, sizeof(tsms), "%lld", (long long)now * 1000);
    char ts[32];
    snprintf(ts, sizeof(ts), "%lld", (long long)now);

    const char *reqParts[6] = { keyB64, paramsB64, tsms, nonceHex, SECRET, extra };
    char reqSign[33];
    md5_concat(reqSign, reqParts, 6);

    const char *hdrConst = "qimei_qq_androidpzAuCmaFAaFaHrdakPjLIEqKrGnSOOvH";
    char hdrStr[128];
    snprintf(hdrStr, sizeof(hdrStr), "%s%s", hdrConst, ts);
    char headerSign[33];
    {
        mbedtls_md5_context ctx;
        mbedtls_md5_init(&ctx);
        mbedtls_md5_starts(&ctx);
        mbedtls_md5_update(&ctx, (const unsigned char *)hdrStr, strlen(hdrStr));
        uint8_t dg[16];
        mbedtls_md5_finish(&ctx, dg);
        mbedtls_md5_free(&ctx);
        for (int i = 0; i < 16; i++) {
            headerSign[i*2] = "0123456789abcdef"[dg[i]>>4];
            headerSign[i*2+1] = "0123456789abcdef"[dg[i]&15];
        }
        headerSign[32] = '\0';
    }

    out->key = strdup(keyB64);
    out->params = strdup(paramsB64);
    out->time = strdup(ts);
    out->nonce = strdup(nonceHex);
    out->sign = strdup(reqSign);
    out->extra = strdup(extra);
    out->header_sign = strdup(headerSign);

    free(brand); free(device); free(model); free(procVersion);
    free(androidId); free(imei); free(release); free(sdk);
    return 0;
fail:
    free(brand); free(device); free(model); free(procVersion);
    free(androidId); free(imei); free(release); free(sdk);
    return -1;
}

void qq_qimei_request_free(qq_qimei_request *r) {
    if (!r) return;
    free(r->key); free(r->params); free(r->time); free(r->nonce);
    free(r->sign); free(r->extra); free(r->header_sign);
    memset(r, 0, sizeof(*r));
}