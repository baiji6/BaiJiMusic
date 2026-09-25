#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include "qqapi.h"

extern "C" {

static char *jstring_to_c(JNIEnv *env, jstring js) {
    if (!js) return NULL;
    const char *s = env->GetStringUTFChars(js, NULL);
    char *copy = strdup(s ? s : "");
    env->ReleaseStringUTFChars(js, s);
    return copy;
}

static jstring c_to_jstring(JNIEnv *env, const char *s) {
    if (!s) return env->NewStringUTF("");
    return env->NewStringUTF(s);
}

/* ---- SecurityApi ---- */

JNIEXPORT jstring JNICALL
Java_com_baiji_music_native_SecurityApi_nativeZzcSign(JNIEnv *env, jclass, jstring jpayload) {
    char *payload = jstring_to_c(env, jpayload);
    char out[128];
    qq_zzc_sign((const uint8_t *)payload, strlen(payload), out);
    jstring res = c_to_jstring(env, out);
    free(payload);
    return res;
}

JNIEXPORT jstring JNICALL
Java_com_baiji_music_native_SecurityApi_nativeQrcDecrypt(JNIEnv *env, jclass, jstring jhex) {
    char *hex = jstring_to_c(env, jhex);
    char *plain = qq_qrc_decrypt(hex);
    jstring res = c_to_jstring(env, plain ? plain : "");
    free(hex);
    free(plain);
    return res;
}

JNIEXPORT jlong JNICALL
Java_com_baiji_music_native_SecurityApi_nativeHash33(JNIEnv *env, jclass, jstring js, jlong seed) {
    char *s = jstring_to_c(env, js);
    int64_t r = qq_hash33(s, seed);
    free(s);
    return (jlong)r;
}

JNIEXPORT jstring JNICALL
Java_com_baiji_music_native_SecurityApi_nativeBuildComm(JNIEnv *env, jclass,
    jstring jcred, jstring jdev, jboolean jlogged, jstring jq16, jstring jq36,
    jlong juid, jstring jsid) {
    char *cred = jstring_to_c(env, jcred);
    char *dev = jstring_to_c(env, jdev);
    char *q16 = jstring_to_c(env, jq16);
    char *q36 = jstring_to_c(env, jq36);
    char *sid = jstring_to_c(env, jsid);
    char *comm = qq_build_comm(cred, dev, jlogged == JNI_TRUE, q16, q36, (int64_t)juid, sid);
    jstring res = c_to_jstring(env, comm ? comm : "");
    free(cred); free(dev); free(q16); free(q36); free(sid); free(comm);
    return res;
}

/* ---- QimeiApi ---- */

/* 对字符串做 JSON 转义（转义 " 和 \ ，并剔除控制字符），返回 malloc 结果，调用方 free */
static char *json_escape(const char *s) {
    if (!s) return strdup("");
    size_t cap = strlen(s) * 2 + 8;
    char *out = (char *)malloc(cap + 1);
    size_t o = 0;
    for (const unsigned char *p = (const unsigned char *)s; *p; p++) {
        if (o + 4 >= cap) { cap *= 2; out = (char *)realloc(out, cap + 1); }
        unsigned char c = *p;
        if (c == '"') { out[o++] = '\\'; out[o++] = '"'; }
        else if (c == '\\') { out[o++] = '\\'; out[o++] = '\\'; }
        else if (c == '\n') { out[o++] = '\\'; out[o++] = 'n'; }
        else if (c == '\r') { out[o++] = '\\'; out[o++] = 'r'; }
        else if (c == '\t') { out[o++] = '\\'; out[o++] = 't'; }
        else if (c < 0x20) {
            out[o++] = '\\';
            out[o++] = 'u';
            static const char hex[] = "0123456789abcdef";
            out[o++] = '0'; out[o++] = '0';
            out[o++] = hex[c >> 4]; out[o++] = hex[c & 15];
        }
        else out[o++] = (char)c;
    }
    out[o] = '\0';
    return out;
}

JNIEXPORT jstring JNICALL
Java_com_baiji_music_native_QimeiApi_nativeBuildQimei(JNIEnv *env, jclass, jstring jdev) {
    char *dev = jstring_to_c(env, jdev);
    qq_qimei_request r;
    if (qq_qimei_build(dev, &r) != 0) {
        free(dev);
        return env->NewStringUTF("{\"error\":\"qimei_build_failed\"}");
    }
    /* 各字段先做 JSON 转义，避免 extra 等含引号的内容破坏整体 JSON */
    char *key = json_escape(r.key);
    char *params = json_escape(r.params);
    char *time = json_escape(r.time);
    char *nonce = json_escape(r.nonce);
    char *sign = json_escape(r.sign);
    char *eextra = json_escape(r.extra);
    char *hsign = json_escape(r.header_sign);
    char buf[20000];
    snprintf(buf, sizeof(buf),
        "{\"key\":\"%s\",\"params\":\"%s\",\"time\":\"%s\",\"nonce\":\"%s\",\"sign\":\"%s\",\"extra\":\"%s\",\"header_sign\":\"%s\"}",
        key, params, time, nonce, sign, eextra, hsign);
    free(key); free(params); free(time); free(nonce); free(sign); free(eextra); free(hsign);
    jstring res = env->NewStringUTF(buf);
    qq_qimei_request_free(&r);
    free(dev);
    return res;
}

/* ---- DeviceApi ---- */

JNIEXPORT jstring JNICALL
Java_com_baiji_music_native_DeviceApi_nativeMakeDefault(JNIEnv *env, jclass) {
    char *dev = qq_device_make_default();
    jstring res = c_to_jstring(env, dev ? dev : "{}");
    free(dev);
    return res;
}

/* ---- SearchId ---- */

JNIEXPORT jstring JNICALL
Java_com_baiji_music_native_SecurityApi_nativeGetSearchId(JNIEnv *env, jclass) {
    int e = qq_rand_int(1, 20);
    long long t = (long long)e * 18014398509481984LL;
    long long n = (long long)qq_rand_int(0, 4194303) * 4294967296LL;
    long long now = (long long)time(NULL) * 1000LL;
    long long r = now % (24 * 60 * 60 * 1000LL);
    char buf[32];
    snprintf(buf, sizeof(buf), "%lld", t + n + r);
    return env->NewStringUTF(buf);
}

} /* extern "C" */