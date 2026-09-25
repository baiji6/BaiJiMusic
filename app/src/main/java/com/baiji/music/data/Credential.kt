package com.baiji.music.data

import org.json.JSONObject

/**
 * 登录凭证。
 */
data class Credential(
    var openid: String = "",
    var refreshToken: String = "",
    var accessToken: String = "",
    var expiredAt: Long = 0,
    var musicid: Long = 0,
    var musickey: String = "",
    var unionid: String = "",
    var strMusicid: String = "",
    var refreshKey: String = "",
    var musickeyCreateTime: Long = 0,
    var keyExpiresIn: Long = 0,
    var firstLogin: Int = 0,
    var bindAccountType: Int = 0,
    var needRefreshKeyIn: Long = 0,
    var encryptUin: String = "",
    var loginType: Int = 0,
) {
    fun isLoggedIn(): Boolean = musicid != 0L && musickey.isNotEmpty()

    fun isExpired(): Boolean {
        if (musickeyCreateTime == 0L || keyExpiresIn == 0L) return false
        val now = System.currentTimeMillis() / 1000
        return now >= musickeyCreateTime + keyExpiresIn
    }

    fun toJsonString(): String {
        val o = JSONObject()
        o.put("musicid", musicid)
        o.put("musickey", musickey)
        o.put("loginType", loginType)
        return o.toString()
    }

    companion object {
        val ALIAS = mapOf(
            "openid" to "openid",
            "refresh_token" to "refreshToken",
            "refreshToken" to "refreshToken",
            "access_token" to "accessToken",
            "accessToken" to "accessToken",
            "expired_at" to "expiredAt",
            "expiredAt" to "expiredAt",
            "musicid" to "musicid",
            "musickey" to "musickey",
            "unionid" to "unionid",
            "str_musicid" to "strMusicid",
            "strMusicid" to "strMusicid",
            "refresh_key" to "refreshKey",
            "refreshKey" to "refreshKey",
            "musickeycreatetime" to "musickeyCreateTime",
            "musickeyCreateTime" to "musickeyCreateTime",
            "key_expires_in" to "keyExpiresIn",
            "keyExpiresIn" to "keyExpiresIn",
            "first_login" to "firstLogin",
            "firstLogin" to "firstLogin",
            "bind_account_type" to "bindAccountType",
            "bindAccountType" to "bindAccountType",
            "need_refresh_key_in" to "needRefreshKeyIn",
            "needRefreshKeyIn" to "needRefreshKeyIn",
            "encrypt_uin" to "encryptUin",
            "encryptUin" to "encryptUin",
            "login_type" to "loginType",
            "loginType" to "loginType",
        )

        fun fromDict(data: JSONObject): Credential {
            val c = Credential()
            val keys = data.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val target = ALIAS[k] ?: k
                val v = data.opt(k)
                when (target) {
                    "openid" -> if (v is String) c.openid = v
                    "refreshToken" -> if (v is String) c.refreshToken = v
                    "accessToken" -> if (v is String) c.accessToken = v
                    "expiredAt" -> if (v is Number) c.expiredAt = v.toLong() else if (v is String) c.expiredAt = v.toLongOrNull() ?: 0
                    "musicid" -> if (v is Number) c.musicid = v.toLong() else if (v is String) c.musicid = v.toLongOrNull() ?: 0
                    "musickey" -> if (v is String) c.musickey = v
                    "unionid" -> if (v is String) c.unionid = v
                    "strMusicid" -> if (v is String) c.strMusicid = v
                    "refreshKey" -> if (v is String) c.refreshKey = v
                    "musickeyCreateTime" -> if (v is Number) c.musickeyCreateTime = v.toLong() else if (v is String) c.musickeyCreateTime = v.toLongOrNull() ?: 0
                    "keyExpiresIn" -> if (v is Number) c.keyExpiresIn = v.toLong() else if (v is String) c.keyExpiresIn = v.toLongOrNull() ?: 0
                    "firstLogin" -> if (v is Number) c.firstLogin = v.toInt() else if (v is String) c.firstLogin = v.toIntOrNull() ?: 0
                    "bindAccountType" -> if (v is Number) c.bindAccountType = v.toInt() else if (v is String) c.bindAccountType = v.toIntOrNull() ?: 0
                    "needRefreshKeyIn" -> if (v is Number) c.needRefreshKeyIn = v.toLong() else if (v is String) c.needRefreshKeyIn = v.toLongOrNull() ?: 0
                    "encryptUin" -> if (v is String) c.encryptUin = v
                    "loginType" -> if (v is Number) c.loginType = v.toInt() else if (v is String) c.loginType = v.toIntOrNull() ?: 0
                }
            }
            if (data.has("musickey") && !data.has("login_type") && !data.has("loginType") && c.musickey.isNotEmpty()) {
                c.loginType = if (c.musickey.startsWith("W_X")) 1 else 2
            }
            return c
        }
    }
}