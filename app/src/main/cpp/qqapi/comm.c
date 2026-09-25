#include "qqapi.h"
#include <stdlib.h>
#include <string.h>
#include <stdio.h>

/* small helper to extract a JSON string field */
static char *extract_str(const char *json, const char *key) {
    char pat[64];
    snprintf(pat, sizeof(pat), "\"%s\"", key);
    const char *p = strstr(json, pat);
    if (!p) return NULL;
    p += strlen(pat);
    while (*p == ' ' || *p == ':' || *p == '\t') p++;
    if (*p == '"') {
        p++;
        const char *s = p;
        size_t l = 0;
        while (*p && *p != '"') { p++; l++; }
        char *out = (char *)malloc(l + 1);
        memcpy(out, s, l);
        out[l] = '\0';
        return out;
    }
    const char *s = p;
    size_t l = 0;
    while (*p && *p != ',' && *p != '}' && *p != ' ') { p++; l++; }
    char *out = (char *)malloc(l + 1);
    memcpy(out, s, l);
    out[l] = '\0';
    return out;
}

/* build Android comm. credential_json must have musicid/musickey/loginType.
   device_json provides openUdid/androidId/version.release/model/version.sdk/
   fingerprint/sessionUid/sessionSid. q16/q36 are qimei strings. */
char *qq_build_comm(const char *credential_json, const char *device_json,
                    int logged_in, const char *q16, const char *q36,
                    int64_t session_uid, const char *session_sid) {
    char *musicid = extract_str(credential_json, "musicid");
    char *musickey = extract_str(credential_json, "musickey");
    char *loginType = extract_str(credential_json, "loginType");
    char *openUdid = extract_str(device_json, "openUdid");
    char *androidId = extract_str(device_json, "androidId");
    char *model = extract_str(device_json, "model");
    char *fingerprint = extract_str(device_json, "fingerprint");

    /* release & sdk nested in version */
    char *release = NULL, *sdk = NULL;
    {
        const char *vp = strstr(device_json, "\"version\"");
        if (vp) {
            const char *vr = strstr(vp, "\"release\"");
            if (vr) { const char *rc = strchr(vr, ':'); if (rc) { const char *rv = rc + 1; while (*rv==' ') rv++; if (*rv=='"'){rv++;const char*s=rv;size_t l=0;while(*rv&&*rv!='"'){rv++;l++;}release=(char*)malloc(l+1);memcpy(release,s,l);release[l]=0;}}}
            const char *vs = strstr(vp, "\"sdk\"");
            if (vs) { const char *sc = strchr(vs, ':'); if (sc) { const char *sv = sc + 1; while (*sv==' ') sv++; const char*s=sv; size_t l=0; while(*sv&&*sv!=','&&*sv!='}'){sv++;l++;} sdk=(char*)malloc(l+1);memcpy(sdk,s,l);sdk[l]=0;}}
        }
    }
    if (!release) release = strdup("10");
    if (!sdk) sdk = strdup("29");

    if (!openUdid) openUdid = strdup("00000000000000000000000000000000");
    if (!androidId) androidId = strdup("00000000");
    if (!model) model = strdup("MI 6");
    if (!fingerprint) fingerprint = strdup("xiaomi/iarim/sagit:10/eomam.200122.001/1000000:user/release-keys");

    /* numeric fields may carry quotes; strip for qq/uid to be safe */
    char *qqnum = musicid;
    if (qqnum && *qqnum == '"') { /* shouldn't happen for musicid */ }

    char comm[4096];
    size_t pos = 0;
    pos += (size_t)snprintf(comm + pos, sizeof(comm) - pos,
        "{\"ct\":11,\"cv\":14090008,\"v\":14090008,\"chid\":\"10003505\"");
    if (logged_in && musicid) pos += (size_t)snprintf(comm + pos, sizeof(comm) - pos, ",\"qq\":%s", musicid);
    if (logged_in && musickey) pos += (size_t)snprintf(comm + pos, sizeof(comm) - pos, ",\"authst\":\"%s\"", musickey);
    pos += (size_t)snprintf(comm + pos, sizeof(comm) - pos, ",\"tmeAppID\":\"qqmusic\"");
    if (loginType) pos += (size_t)snprintf(comm + pos, sizeof(comm) - pos, ",\"tmeLoginType\":%s", loginType);
    pos += (size_t)snprintf(comm + pos, sizeof(comm) - pos,
        ",\"QIMEI\":\"%s\",\"QIMEI36\":\"%s\",\"OpenUDID\":\"%s\",\"udid\":\"%s\",\"OpenUDID2\":\"%s\",\"uid\":%lld,\"sid\":\"%s\",\"aid\":\"%s\",\"os_ver\":\"%s\",\"phonetype\":\"%s\",\"devicelevel\":\"%s\",\"newdevicelevel\":\"%s\",\"rom\":\"%s\"}",
        q16 ? q16 : "", q36 ? q36 : "",
        openUdid, openUdid, openUdid,
        (long long)session_uid, session_sid ? session_sid : "",
        androidId, release, model, sdk, sdk, fingerprint);

    free(musicid); free(musickey); free(loginType); free(openUdid);
    free(androidId); free(model); free(fingerprint); free(release); free(sdk);
    return strdup(comm);
}