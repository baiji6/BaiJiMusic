package com.baiji.music.native

/**
 * 安全相关的原生方法封装（.so 内实现）。
 */
object SecurityApi {
    init {
        loadLibrarySafely()
    }

    /** 加载原生库。加载失败时抛出 Throwable（UnsatisfiedLinkError），由调用方捕获避免崩溃。 */
    fun loadLibrarySafely() {
        System.loadLibrary("qqmusicapi")
    }

    external fun nativeZzcSign(payload: String): String

    external fun nativeQrcDecrypt(hex: String): String

    external fun nativeHash33(s: String, seed: Long): Long

    external fun nativeBuildComm(
        credentialJson: String,
        deviceJson: String,
        loggedIn: Boolean,
        q16: String,
        q36: String,
        sessionUid: Long,
        sessionSid: String
    ): String

    external fun nativeGetSearchId(): String

    fun zzcSign(payload: String): String = nativeZzcSign(payload)

    fun qrcDecrypt(hex: String): String = nativeQrcDecrypt(hex)

    fun hash33(s: String, seed: Long = 0): Long = nativeHash33(s, seed)

    fun buildComm(
        credential: String,
        device: String,
        loggedIn: Boolean,
        q16: String,
        q36: String,
        sessionUid: Long,
        sessionSid: String
    ): String = nativeBuildComm(credential, device, loggedIn, q16, q36, sessionUid, sessionSid)

    fun getSearchId(): String = nativeGetSearchId()
}