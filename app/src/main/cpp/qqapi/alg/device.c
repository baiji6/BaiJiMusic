#include "qqapi.h"
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <time.h>

static void gen_uuid(char out[37]) {
    static const char c[] = "0123456789abcdef";
    uint8_t b[16];
    qq_random_bytes(b, 16);
    b[6] = (uint8_t)((b[6] & 0x0f) | 0x40);
    b[8] = (uint8_t)((b[8] & 0x3f) | 0x80);
    snprintf(out, 37,
             "%c%c%c%c%c%c%c%c-%c%c%c%c-%c%c%c%c-%c%c%c%c-%c%c%c%c%c%c%c%c%c%c%c%c",
             c[b[0]>>4],c[b[0]&15],c[b[1]>>4],c[b[1]&15],c[b[2]>>4],c[b[2]&15],c[b[3]>>4],c[b[3]&15],
             c[b[4]>>4],c[b[4]&15],c[b[5]>>4],c[b[5]&15],
             c[b[6]>>4],c[b[6]&15],c[b[7]>>4],c[b[7]&15],
             c[b[8]>>4],c[b[8]&15],c[b[9]>>4],c[b[9]&15],
             c[b[10]>>4],c[b[10]&15],c[b[11]>>4],c[b[11]&15],c[b[12]>>4],c[b[12]&15],c[b[13]>>4],c[b[13]&15],c[b[14]>>4],c[b[14]&15],c[b[15]>>4],c[b[15]&15]);
}

static void random_imei(char out[16]) {
    int digits[15];
    for (int i = 0; i < 14; i++) digits[i] = qq_rand_int(0, 9);
    int sum = 0;
    for (int i = 0; i < 14; i++) {
        int v = digits[i];
        if (i % 2 == 1) {
            v *= 2;
            if (v > 9) v -= 9;
        }
        sum += v;
    }
    digits[14] = (10 - (sum % 10)) % 10;
    for (int i = 0; i < 15; i++) out[i] = (char)('0' + digits[i]);
    out[15] = '\0';
}

char *qq_device_make_default(void) {
    char uuid[37];
    char imei[16];
    char osid[33]; /* imsiMd5 hex */
    char androidId[17];
    char procHex[9];

    gen_uuid(uuid);
    random_imei(imei);

    uint8_t imsi[16];
    qq_random_bytes(imsi, 16);
    qq_hex_encode(imsi, 16, osid);
    osid[32] = '\0';

    uint8_t aid[8];
    qq_random_bytes(aid, 8);
    qq_hex_encode(aid, 8, androidId);
    androidId[16] = '\0';

    uint8_t ph[4];
    qq_random_bytes(ph, 4);
    qq_hex_encode(ph, 4, procHex);
    procHex[8] = '\0';

    int displayRand = qq_rand_int(100000, 999998);
    int fpRand = qq_rand_int(1000000, 9999998);

    /* openUdid: uuid without dashes, 32 chars */
    char openUdid[33];
    size_t oi = 0;
    for (int i = 0; i < 36; i++) if (uuid[i] != '-') openUdid[oi++] = uuid[i];
    openUdid[oi] = '\0';

    char *r = (char *)malloc(4096);
    if (!r) return NULL;
    snprintf(r, 4096,
        "{"
        "\"display\":\"QMAPI.%d.001\","
        "\"product\":\"iarim\","
        "\"device\":\"sagit\","
        "\"board\":\"eomam\","
        "\"model\":\"MI 6\","
        "\"fingerprint\":\"xiaomi/iarim/sagit:10/eomam.200122.001/%d:user/release-keys\","
        "\"bootId\":\"%s\","
        "\"procVersion\":\"Linux 5.4.0-54-generic-%s (android-build@google.com)\","
        "\"imei\":\"%s\","
        "\"brand\":\"Xiaomi\","
        "\"bootloader\":\"U-boot\","
        "\"baseBand\":\"\","
        "\"version\":{\"incremental\":\"5891938\",\"release\":\"10\",\"codename\":\"REL\",\"sdk\":29},"
        "\"simInfo\":\"T-Mobile\","
        "\"osType\":\"android\","
        "\"macAddress\":\"00:50:56:C0:00:08\","
        "\"ipAddress\":[10,0,1,3],"
        "\"wifiBssid\":\"00:50:56:C0:00:08\","
        "\"wifiSsid\":\"<unknown ssid>\","
        "\"imsiMd5\":[\"%s\"],"
        "\"androidId\":\"%s\","
        "\"apn\":\"wifi\","
        "\"vendorName\":\"MIUI\","
        "\"vendorOsName\":\"qmapi\","
        "\"openUdid\":\"%s\""
        "}",
        displayRand, fpRand, uuid, procHex, imei, osid, androidId, openUdid);
    return r;
}