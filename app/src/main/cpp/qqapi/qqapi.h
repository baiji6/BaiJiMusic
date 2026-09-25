#ifndef QQAPI_ALG_H
#define QQAPI_ALG_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/* ---- random ---- */
void qq_random_bytes(uint8_t *out, size_t n);
uint64_t qq_rand_u64(void);
int qq_rand_int(int min, int max); /* inclusive */

/* ---- hex ---- */
size_t qq_hex_encode(const uint8_t *in, size_t inlen, char *out); /* returns written (2*inlen) */
size_t qq_hex_decode(const char *in, size_t inlen, uint8_t *out); /* returns decoded len */

/* ---- base64 ---- */
size_t qq_b64_encode(const uint8_t *in, size_t inlen, char *out); /* returns written incl NUL? false: raw len */
size_t qq_b64_decode(const char *in, size_t inlen, uint8_t *out);

/* ---- hash33 ---- */
int64_t qq_hash33(const char *s, int64_t seed);

/* ---- zzc sign (SHA1 based) ---- */
/* payload: raw bytes; out must hold at least 128 bytes; returns string length */
int qq_zzc_sign(const uint8_t *payload, size_t len, char *out);

/* ---- tripledes (custom, for QRC) ---- */
typedef struct qq_tripledes_key qq_tripledes_key;
qq_tripledes_key *qq_tripledes_key_new(const uint8_t key[24], int encrypt); /* encrypt: 1 */
void qq_tripledes_key_free(qq_tripledes_key *k);
/* crypt one 8-byte block in place */
void qq_tripledes_crypt_block(qq_tripledes_key *k, uint8_t block[8]);

/* ---- qrc decrypt ---- */
/* in: hex string of encrypted qrc; returns malloc'd string or NULL. caller frees. */
char *qq_qrc_decrypt(const char *hex_in);

/* ---- device ---- */
/* Generate default device info as JSON string (malloc) */
char *qq_device_make_default(void);

/* ---- qimei ---- */
typedef struct {
    char *key;      /* base64 encrypted cryptKey */
    char *params;   /* base64 AES payload */
    char *time;     /* ts as string (seconds) */
    char *nonce;
    char *sign;     /* reqSign */
    char *extra;
    char *header_sign; /* the custom header sign */
} qq_qimei_request;

/* build qimei request from device JSON; returns 0 on success */
int qq_qimei_build(const char *device_json, qq_qimei_request *out);
void qq_qimei_request_free(qq_qimei_request *r);

/* ---- comm ---- */
/* Build comm JSON for android platform. Returns malloc'd string or NULL. */
char *qq_build_comm(const char *credential_json, const char *device_json,
                    int logged_in, const char *q16, const char *q36,
                    int64_t session_uid, const char *session_sid);

/* internal helpers reused across alg */
int qq_md5_hex(const uint8_t *p, size_t n, char out[33]);
int qq_sha1_hex(const uint8_t *p, size_t n, char out[41]);

#ifdef __cplusplus
}
#endif

#endif