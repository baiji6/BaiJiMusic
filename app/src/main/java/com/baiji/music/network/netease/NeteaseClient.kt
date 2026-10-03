package com.baiji.music.network.netease

import android.content.Context
import com.baiji.music.util.AppLog
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 网易云 API 客户端。
 * HTTP 编排在 Kotlin 层；EAPI 参数加密见 [NeteaseCrypto]。
 * 凭证以网页版 Cookie 形式保存（需包含 MUSIC_U）。
 */
class NeteaseClient(context: Context) {

    companion object {
        private const val TAG = "NeteaseClient"
        private const val PREFS = "netease_login"
        private const val KEY_COOKIE = "cookie"
        private const val KEY_USER_ID = "user_id"

        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Safari/537.36 Chrome/91.0.4472.164 NeteaseMusicDesktop/2.10.2.200154"
        private const val REFERER = "https://music.163.com/"

        /** 网页版接口根地址（搜索/详情/歌词/歌单/专辑） */
        const val API_BASE = "https://music.163.com/api"

        /** EAPI 接口根地址（取流/登录） */
        const val EAPI_BASE = "https://interface3.music.163.com/eapi"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /** 业务模块 */
    val api: NeteaseApi by lazy { NeteaseApi(this) }

    var cookie: String
        get() = prefs.getString(KEY_COOKIE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_COOKIE, value.trim()).apply()

    var userId: Long
        get() = prefs.getLong(KEY_USER_ID, 0L)
        set(value) = prefs.edit().putLong(KEY_USER_ID, value).apply()

    fun isLoggedIn(): Boolean = cookie.contains("MUSIC_U=")

    fun logout() {
        prefs.edit().clear().apply()
    }

    /** 组装请求 Cookie：用户 Cookie 优先，并补齐 os/appver 等字段（杜比等音质依赖 os=pc） */
    fun buildCookieHeader(): String {
        val map = LinkedHashMap<String, String>()
        parseCookie(cookie, map)
        if (!map.containsKey("os")) map["os"] = "pc"
        if (!map.containsKey("appver")) map["appver"] = "8.9.70"
        if (!map.containsKey("osver")) map["osver"] = "10"
        if (!map.containsKey("deviceId")) map["deviceId"] = "pyncm!"
        return map.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    /** 解析一段 Cookie 字符串到 map（兼容 "k=v; k=v" 与换行分隔） */
    fun parseCookie(raw: String, out: MutableMap<String, String>) {
        if (raw.isBlank()) return
        for (part in raw.replace("\n", ";").split(";")) {
            val seg = part.trim()
            if (seg.isEmpty()) continue
            val idx = seg.indexOf("=")
            if (idx <= 0) continue
            val k = seg.substring(0, idx).trim()
            var v = seg.substring(idx + 1).trim()
            if (v.length >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
                v = v.substring(1, v.length - 1)
            }
            if (k.isNotEmpty() && v.isNotEmpty()) out[k] = v
        }
    }

    // ================= HTTP =================

    /** EAPI 请求：payload 经加密后以 params 表单字段提交 */
    fun postEapi(eapiUrl: String, payload: JSONObject): JSONObject {
        val path = eapiUrl.toHttpUrlOrNull()?.encodedPath ?: eapiUrl
        val params = NeteaseCrypto.encryptParams(path, payload.toString())
        return postForm("params=" + URLEncoder.encode(params, "UTF-8"), eapiUrl)
    }

    /** 网页版接口 POST（表单参数） */
    fun postApi(apiUrl: String, form: Map<String, String>): JSONObject {
        val body = form.entries.joinToString("&") {
            "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}"
        }
        return postForm(body, apiUrl)
    }

    /** 网页版接口 GET */
    fun getApi(apiUrl: String): JSONObject {
        val req = baseRequest(apiUrl).get().build()
        return exec(req, apiUrl)
    }

    private fun baseRequest(url: String): Request.Builder =
        Request.Builder().url(url)
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Referer", REFERER)
            .addHeader("Cookie", buildCookieHeader())

    private fun postForm(body: String, url: String): JSONObject {
        val req = baseRequest(url)
            .post(body.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .build()
        return exec(req, url)
    }

    private fun exec(req: Request, url: String): JSONObject {
        AppLog.d(TAG, ">>> ${req.method} $url")
        val text = try {
            http.newCall(req).execute().use { resp ->
                val t = resp.body?.string() ?: ""
                AppLog.d(TAG, "<<< HTTP ${resp.code} $url resp=${t.take(3000)}")
                t
            }
        } catch (e: IOException) {
            AppLog.e(TAG, "请求异常 $url", e)
            throw e
        }
        if (text.isBlank()) throw IOException("网易云返回空响应")
        return try {
            JSONObject(text)
        } catch (e: Exception) {
            throw IOException("网易云响应解析失败: ${text.take(200)}")
        }
    }
}
