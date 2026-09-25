package com.baiji.music.data

import android.content.Context
import android.util.Log
import com.baiji.music.native.DeviceApi
import org.json.JSONObject

/**
 * 设备信息管理器：生成/持久化设备指纹，管理 session。
 */
class DeviceManager(private val context: Context) {
    companion object {
        private const val TAG = "DeviceManager"
        private const val PREFS = "device_info"
        private const val KEY_DEVICE = "device_json"
        private const val KEY_UID = "session_uid"
        private const val KEY_SID = "session_sid"
        private const val KEY_SAVE_TIME = "session_save_time"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private var _device: JSONObject? = null
    var sessionUid: Long
        get() = prefs.getLong(KEY_UID, 0)
        set(v) = prefs.edit().putLong(KEY_UID, v).apply()
    var sessionSid: String
        get() = prefs.getString(KEY_SID, "") ?: ""
        set(v) = prefs.edit().putString(KEY_SID, v).apply()
    var sessionSaveTime: Long
        get() = prefs.getLong(KEY_SAVE_TIME, 0)
        set(v) = prefs.edit().putLong(KEY_SAVE_TIME, v).apply()

    fun getDevice(): JSONObject {
        _device?.let { return it }
        val saved = prefs.getString(KEY_DEVICE, null)
        val dev = if (saved != null) {
            try {
                JSONObject(saved)
            } catch (e: Exception) {
                DeviceApi.makeDefault()
            }
        } else {
            DeviceApi.makeDefault()
        }
        _device = dev
        saveDevice()
        return dev
    }

    fun saveDevice() {
        _device?.let {
            prefs.edit().putString(KEY_DEVICE, it.toString()).apply()
        }
    }

    fun applyQimei(q16: String, q36: String) {
        val d = getDevice()
        d.put("qimei", q16)
        d.put("qimei36", q36)
        d.put("qimeiSaveTime", System.currentTimeMillis() / 1000)
        saveDevice()
    }

    fun openUdid(): String = getDevice().optString("openUdid", "")

    fun isSessionValid(): Boolean {
        if (sessionSaveTime == 0L) return false
        val now = System.currentTimeMillis() / 1000
        if (now - sessionSaveTime >= 86400) return false
        return sessionUid != 0L && sessionSid.isNotEmpty()
    }

    fun hasQimei(): Boolean {
        val now = System.currentTimeMillis() / 1000
        val d = getDevice()
        return d.optString("qimei").isNotEmpty() &&
            d.optString("qimei36").isNotEmpty() &&
            d.optLong("qimeiSaveTime") != 0L &&
            now - d.optLong("qimeiSaveTime") < 86400
    }

    fun q16(): String = getDevice().optString("qimei", "")
    fun q36(): String = getDevice().optString("qimei36", "")

    fun logDebug() {
        Log.d(TAG, "device=${getDevice().toString().take(200)}")
    }
}