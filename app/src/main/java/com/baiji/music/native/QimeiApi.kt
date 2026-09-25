package com.baiji.music.native

import org.json.JSONObject

/**
 * QIMEI 相关原生方法封装（.so 内实现）。
 */
object QimeiApi {
    init {
        System.loadLibrary("qqmusicapi")
    }

    external fun nativeBuildQimei(deviceJson: String): String

    /**
     * 生成 QIMEI 注册请求参数。
     * 返回 JSON: { key, params, time, nonce, sign, extra, header_sign }
     */
    fun buildQimei(deviceJson: String): JSONObject {
        val raw = nativeBuildQimei(deviceJson)
        if (raw.isNullOrEmpty() || raw == "{}") {
            throw IllegalStateException("原生 QIMEI 构建失败（nativeBuildQimei 返回空）")
        }
        if (raw.contains("\"error\"")) {
            throw IllegalStateException("原生 QIMEI 构建失败: $raw")
        }
        return JSONObject(raw)
    }
}