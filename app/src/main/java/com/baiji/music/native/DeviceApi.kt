package com.baiji.music.native

import org.json.JSONObject

/**
 * 设备信息相关原生方法封装（.so 内实现）。
 */
object DeviceApi {
    init {
        System.loadLibrary("qqmusicapi")
    }

    external fun nativeMakeDefault(): String

    fun makeDefault(): JSONObject {
        return JSONObject(nativeMakeDefault())
    }
}