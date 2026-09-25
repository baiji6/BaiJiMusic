package com.baiji.music.network

import android.content.Context
import android.util.Log
import com.baiji.music.data.Credential
import com.baiji.music.data.DeviceManager
import com.baiji.music.native.QimeiApi
import com.baiji.music.native.SecurityApi
import com.baiji.music.util.AppLog
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * QQ 音乐 API 客户端核心。
 * HTTP 编排在 Kotlin 层，签名/comm/QIMEI 加密等关键逻辑在 .so。
 */
class QQMusicClient(context: Context) {
    companion object {
        private const val TAG = "QQMusicClient"
        private const val MUSICU_URL = "https://u.y.qq.com/cgi-bin/musicu.fcg"
        private const val QIMEI_HOST = "https://api.tencentmusic.com/tme/trpc/proxy"
        private const val LOGIN_PREFS = "login_info"
        private const val KEY_CRED = "credential_json"

        // Android 平台档案
        const val CT = 11
        const val CV = 14090008
        const val UA_VERSION = 14090008
        const val QIMEI_APP_VERSION = "14.9.0.8"
        const val QIMEI_SDK_VERSION = "1.2.13.6"
        const val CHID = "10003505"
    }

    val device = DeviceManager(context)
    private val appContext = context.applicationContext
    private val loginPrefs = context.getSharedPreferences(LOGIN_PREFS, Context.MODE_PRIVATE)

    private val random = SecureRandom()

    init {
        // 应用启动即主动加载原生库，尽早暴露问题。
        // 用 Throwable 捕获（UnsatisfiedLinkError 是 Error，非 Exception）。
        // 若加载失败，仅记录日志不崩溃，后续业务调用失败时由上层捕获并提示。
        try {
            SecurityApi.loadLibrarySafely()
        } catch (t: Throwable) {
            AppLog.e(TAG, "原生库加载失败: ${t.message}", t)
        }
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .cookieJar(object : CookieJar {
            private val store = ConcurrentHashMap<String, List<Cookie>>()
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                store[url.host] = cookies
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return store[url.host] ?: emptyList()
            }
        })
        .build()

    var credential: Credential
        get() {
            val raw = loginPrefs.getString(KEY_CRED, null)
            if (raw == null) return Credential()
            return try {
                Credential.fromDict(JSONObject(raw))
            } catch (e: Exception) {
                Credential()
            }
        }
        set(value) {
            val o = JSONObject()
            o.put("musicid", value.musicid)
            o.put("musickey", value.musickey)
            o.put("loginType", value.loginType)
            o.put("openid", value.openid)
            o.put("refreshToken", value.refreshToken)
            o.put("accessToken", value.accessToken)
            o.put("unionid", value.unionid)
            o.put("strMusicid", value.strMusicid)
            o.put("refreshKey", value.refreshKey)
            o.put("musickeyCreateTime", value.musickeyCreateTime)
            o.put("keyExpiresIn", value.keyExpiresIn)
            o.put("expiredAt", value.expiredAt)
            loginPrefs.edit().putString(KEY_CRED, o.toString()).apply()
        }

    fun logout() {
        loginPrefs.edit().remove(KEY_CRED).apply()
    }

    fun getUserAgent(): String {
        val rel = device.getDevice().optJSONObject("version")?.optString("release") ?: "10"
        return "QQMusic $UA_VERSION(android $rel)"
    }

    // 业务模块
    val search: SearchApi by lazy { SearchApi(this) }
    val song: SongApi by lazy { SongApi(this) }
    val login: LoginApi by lazy { LoginApi(this) }

    fun isLoggedIn(): Boolean = credential.isLoggedIn()

    // ================= HTTP =================

    fun request(
        method: String,
        url: String,
        params: Map<String, String>? = null,
        headers: Map<String, String>? = null,
        jsonBody: JSONObject? = null,
        bodyString: String? = null,
        cookies: Map<String, String>? = null,
    ): JSONObject {
        val ub = url.toHttpUrlOrNull()?.newBuilder()
            ?: throw IllegalArgumentException("bad url: $url")
        params?.forEach { (k, v) -> ub.addQueryParameter(k, v) }
        val reqBuilder = okhttp3.Request.Builder().url(ub.build())

        val cred = credential
        val hb = okhttp3.Headers.Builder()
        // 收集所有 Cookie 段，合并为单个 Cookie 头，避免重复头导致服务端取错
        val cookieParts = LinkedHashMap<String, String>()
        headers?.forEach { (k, v) ->
            if (k.equals("Cookie", ignoreCase = true)) {
                parseCookieHeader(v, cookieParts)
            } else {
                hb.add(k, v)
            }
        }
        if (cookies != null) {
            cookies.forEach { (k, v) -> cookieParts[k] = v }
        }
        if (cred.musicid != 0L) {
            val id = cred.strMusicid.ifEmpty { cred.musicid.toString() }
            cookieParts["uin"] = id
            cookieParts["qqmusic_uin"] = id
            cookieParts["qm_keyst"] = cred.musickey
            cookieParts["qqmusic_key"] = cred.musickey
        }
        if (cookieParts.isNotEmpty()) {
            hb.add("Cookie", cookieParts.entries.joinToString("; ") { "${it.key}=${it.value}" })
        }
        hb.add("User-Agent", getUserAgent())
        val headers1 = hb.build()
        reqBuilder.headers(headers1)

        val body: okhttp3.RequestBody?
        if (jsonBody != null) {
            body = jsonBody.toString().toRequestBody("application/json".toMediaType())
        } else if (bodyString != null) {
            body = bodyString.toRequestBody(null)
        } else {
            body = null
        }
        reqBuilder.method(method, body)

        val reqBodyStr = jsonBody?.toString() ?: bodyString ?: ""
        AppLog.d(TAG, ">>> $method $url  body=${reqBodyStr.take(2000)}")
        val response = try {
            httpClient.newCall(reqBuilder.build()).execute()
        } catch (e: IOException) {
            AppLog.e(TAG, "请求异常 $method $url", e)
            throw e
        }
        val text = response.body?.string() ?: ""
        AppLog.d(TAG, "<<< HTTP ${response.code} $method $url  resp=${text.take(4000)}")
        response.close()
        if (!response.isSuccessful && text.isBlank()) {
            throw IOException("HTTP ${response.code}")
        }
        return JSONObject(if (text.isBlank()) "{}" else text)
    }

    // ================= Session =================

    // ================= Raw HTTP (for QR login) =================

    data class RawResult(val text: String, val cookies: Map<String, String>)

    fun rawRequest(
        method: String,
        url: String,
        headers: Map<String, String>? = null,
        body: String? = null,
    ): RawResult {
        val reqBuilder = okhttp3.Request.Builder().url(url)
        val hb = okhttp3.Headers.Builder()
        headers?.forEach { (k, v) -> hb.add(k, v) }
        hb.add("User-Agent", getUserAgent())
        reqBuilder.headers(hb.build())
        val requestBody = body?.toRequestBody(null)
        reqBuilder.method(method, requestBody)
        val response = httpClient.newCall(reqBuilder.build()).execute()
        val text = response.body?.string() ?: ""
        val cookies = HashMap<String, String>()
        val setCookie = response.headers("Set-Cookie")
        for (c in setCookie) {
            val first = c.split(";")[0].trim()
            val idx = first.indexOf("=")
            if (idx <= 0) continue
            val name = first.substring(0, idx).trim()
            val value = first.substring(idx + 1).trim()
            if (value.isNotEmpty()) cookies[name] = value
        }
        response.close()
        if (!response.isSuccessful && text.isBlank()) {
            throw IOException("HTTP ${response.code}")
        }
        return RawResult(text, cookies)
    }

    /** 禁止重定向的原始请求，返回状态码、响应头和 Set-Cookie（用于登录流程取 Location 中的 code） */
    data class RawNoRedirectResult(
        val text: String,
        val cookies: Map<String, String>,
        val status: Int,
        val headers: Map<String, String>,
        val location: String,
    )

    private val noRedirectClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    fun rawRequestNoRedirect(
        method: String,
        url: String,
        headers: Map<String, String>? = null,
        body: String? = null,
    ): RawNoRedirectResult {
        val reqBuilder = okhttp3.Request.Builder().url(url)
        val hb = okhttp3.Headers.Builder()
        headers?.forEach { (k, v) -> hb.add(k, v) }
        hb.add("User-Agent", getUserAgent())
        reqBuilder.headers(hb.build())
        val requestBody = body?.toRequestBody(null)
        reqBuilder.method(method, requestBody)
        val response = noRedirectClient.newCall(reqBuilder.build()).execute()
        val text = response.body?.string() ?: ""
        val cookies = HashMap<String, String>()
        val setCookie = response.headers("Set-Cookie")
        for (c in setCookie) {
            val first = c.split(";")[0].trim()
            val idx = first.indexOf("=")
            if (idx <= 0) continue
            val name = first.substring(0, idx).trim()
            val value = first.substring(idx + 1).trim()
            if (value.isNotEmpty()) cookies[name] = value
        }
        val headerMap = HashMap<String, String>()
        for (i in 0 until response.headers.size) {
            val name = response.headers.name(i)
            val value = response.headers.value(i)
            val key = name.lowercase()
            if (!headerMap.containsKey(key)) headerMap[key] = value
        }
        val location = response.header("Location") ?: response.header("location") ?: ""
        val status = response.code
        response.close()
        return RawNoRedirectResult(text, cookies, status, headerMap, location)
    }

    fun rawBytes(url: String, headers: Map<String, String>? = null): ByteArray {
        val reqBuilder = okhttp3.Request.Builder().url(url)
        val hb = okhttp3.Headers.Builder()
        headers?.forEach { (k, v) -> hb.add(k, v) }
        hb.add("User-Agent", getUserAgent())
        reqBuilder.headers(hb.build())
        reqBuilder.get()
        val response = httpClient.newCall(reqBuilder.build()).execute()
        val bytes = response.body?.bytes() ?: ByteArray(0)
        response.close()
        return bytes
    }

    /** 单次请求同时返回响应体字节和 Set-Cookie 中的 cookies（用于二维码：图片与 qrsig 必须来自同一次请求） */
    data class RawQrcode(val bytes: ByteArray, val cookies: Map<String, String>)

    fun rawQrcode(url: String, headers: Map<String, String>? = null): RawQrcode {
        val reqBuilder = okhttp3.Request.Builder().url(url)
        val hb = okhttp3.Headers.Builder()
        headers?.forEach { (k, v) -> hb.add(k, v) }
        hb.add("User-Agent", getUserAgent())
        reqBuilder.headers(hb.build())
        reqBuilder.get()
        val response = httpClient.newCall(reqBuilder.build()).execute()
        val bytes = response.body?.bytes() ?: ByteArray(0)
        val cookies = HashMap<String, String>()
        val setCookie = response.headers("Set-Cookie")
        for (c in setCookie) {
            val first = c.split(";")[0].trim()
            val idx = first.indexOf("=")
            if (idx <= 0) continue
            val name = first.substring(0, idx).trim()
            val value = first.substring(idx + 1).trim()
            if (value.isNotEmpty()) cookies[name] = value
        }
        response.close()
        return RawQrcode(bytes, cookies)
    }

    private var sessionEnsured = false

    /** 登录成功后调用，强制重新建立 session（确保新凭证被 session 接受） */
    fun resetSession() {
        sessionEnsured = false
        device.sessionSaveTime = 0
    }

    fun ensureSession() {
        if (sessionEnsured && device.isSessionValid()) return
        ensureQimei()
        val comm = buildComm()
        val payload = JSONObject()
        payload.put("comm", comm)
        val req = JSONObject()
        req.put("module", "music.getSession.session")
        req.put("method", "GetSession")
        val param = JSONObject()
        param.put("uid", device.sessionUid.toString())
        param.put("vkey", 0)
        param.put("caller", 0)
        req.put("param", param)
        payload.put("req_0", req)

        val resp = request(
            "POST", MUSICU_URL,
            headers = mapOf("User-Agent" to getUserAgent()),
            jsonBody = payload
        )
        val req0 = resp.optJSONObject("req_0")
        val session = req0?.optJSONObject("data")?.optJSONObject("session")
        if (session != null) {
            device.sessionUid = session.optString("uid").toLongOrNull() ?: 0
            device.sessionSid = session.optString("sid") ?: ""
            device.sessionSaveTime = System.currentTimeMillis() / 1000
            sessionEnsured = true
            AppLog.i(TAG, "getSession 成功 uid=${device.sessionUid} sid=${device.sessionSid.take(8)}")
        } else {
            AppLog.e(TAG, "getSession 失败: code=${req0?.optInt("code")} msg=${req0?.optString("msg")} resp=${resp.toString().take(500)}")
            throw IOException("获取 session 失败")
        }
    }

    fun ensureQimei() {
        if (device.hasQimei()) return
        val d = device.getDevice()
        val q = QimeiApi.buildQimei(d.toString())
        val body = JSONObject()
        body.put("app", 0)
        body.put("os", 1)
        val qp = JSONObject()
        qp.put("key", q.getString("key"))
        qp.put("params", q.getString("params"))
        qp.put("time", q.getString("time"))
        qp.put("nonce", q.getString("nonce"))
        qp.put("sign", q.getString("sign"))
        qp.put("extra", q.getString("extra"))
        body.put("qimeiParams", qp)

        val headers = mapOf(
            "Host" to "api.tencentmusic.com",
            "method" to "GetQimei",
            "service" to "trpc.tme_datasvr.qimeiproxy.QimeiProxy",
            "appid" to "qimei_qq_android",
            "sign" to q.getString("header_sign"),
            "user-agent" to "QQMusic",
            "timestamp" to q.getString("time"),
            "Content-Type" to "application/json",
        )
        val resp = request("POST", QIMEI_HOST, headers = headers, jsonBody = body)
        val data = resp.opt("data")
        val inner: JSONObject = when (data) {
            is String -> JSONObject(data).optJSONObject("data")
            is JSONObject -> data.optJSONObject("data")
            else -> null
        } ?: let {
            AppLog.e(TAG, "QIMEI 注册失败: resp=${resp.toString().take(500)}")
            throw IOException("QIMEI 注册失败")
        }
        val q16 = inner.optString("q16")
        val q36 = inner.optString("q36")
        if (q16.isEmpty() || q36.isEmpty()) {
            AppLog.e(TAG, "QIMEI 缺少 q16/q36 resp=${resp.toString().take(500)}")
            throw IOException("QIMEI 缺少 q16/q36")
        }
        device.applyQimei(q16, q36)
        AppLog.i(TAG, "QIMEI 注册成功 q16=${q16.take(8)} q36=${q36.take(8)}")
    }

    // ================= Comm =================

    fun buildComm(): JSONObject {
        val opened = credential
        val loggedIn = opened.isLoggedIn()
        val comm = SecurityApi.buildComm(
            opened.toJsonString(),
            device.getDevice().toString(),
            loggedIn,
            if (device.hasQimei()) device.q16() else "",
            if (device.hasQimei()) device.q36() else "",
            device.sessionUid,
            device.sessionSid
        )
        return JSONObject(comm)
    }

    // ================= 业务请求 =================

    data class BizRequest(
        val module: String,
        val method: String,
        val param: JSONObject,
        val allowErrorCodes: Boolean = false,
        val sign: Boolean = false,
    )

    fun execute(req: BizRequest, extraComm: JSONObject? = null): JSONObject {
        val req0 = executeFull(req, extraComm)
        return req0.optJSONObject("data") ?: JSONObject()
    }

    /** 返回完整 req_0（含 code 与 data），供登录流程校验错误码 */
    fun executeRaw(req: BizRequest, extraComm: JSONObject? = null): JSONObject {
        return executeFull(req, extraComm)
    }

    private fun executeFull(req: BizRequest, extraComm: JSONObject? = null): JSONObject {
        ensureSession()
        val payload = JSONObject()
        val comm = buildComm()
        extraComm?.let { ec ->
            val keys = ec.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                comm.put(k, ec.opt(k))
            }
        }
        payload.put("comm", comm)
        val r = JSONObject()
        r.put("module", req.module)
        r.put("method", req.method)
        r.put("param", boolToInt(req.param))
        payload.put("req_0", r)

        val url = MUSICU_URL
        AppLog.d(TAG, "业务请求 module=${req.module} method=${req.method} param=${boolToInt(req.param).toString().take(1500)} 已登录=${credential.isLoggedIn()}")
        val resp = request(
            "POST", url,
            headers = mapOf("User-Agent" to getUserAgent()),
            jsonBody = payload
        )
        val req0 = resp.optJSONObject("req_0") ?: JSONObject()
        val code = req0.optInt("code")
        AppLog.d(TAG, "业务响应 ${req.module}.${req.method} code=$code msg=${req0.optString("msg")} data=${req0.opt("data")?.toString()?.take(2000)}")
        if (code != 0 && !req.allowErrorCodes) {
            AppLog.w(TAG, "业务错误 ${req.module}.${req.method} code=$code msg=${req0.optString("msg")}")
            throw CgiException(code, req0.optString("msg"))
        }
        return req0
    }

    private fun boolToInt(o: JSONObject): JSONObject {
        val out = JSONObject()
        o.keys().forEach { k ->
            val v = o.opt(k)
            when (v) {
                is Boolean -> out.put(k, if (v) 1 else 0)
                is JSONObject -> out.put(k, boolToInt(v))
                is JSONArray -> {
                    val arr = JSONArray()
                    for (i in 0 until v.length()) {
                        val e = v.opt(i)
                        arr.put(if (e is Boolean) (if (e) 1 else 0) else e)
                    }
                    out.put(k, arr)
                }
                else -> out.put(k, v)
            }
        }
        return out
    }

    fun getSearchId(): String = SecurityApi.getSearchId()

    /** 解析一个 Cookie 头字符串（如 "uin=123; qm_keyst=abc"）到给定 map */
    private fun parseCookieHeader(header: String, out: MutableMap<String, String>) {
        for (part in header.split(";")) {
            val seg = part.trim()
            if (seg.isEmpty()) continue
            val idx = seg.indexOf("=")
            if (idx <= 0) continue
            out[seg.substring(0, idx).trim()] = seg.substring(idx + 1).trim()
        }
    }

    fun randomUuid(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x40).toByte()
        bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte()
        val sb = StringBuilder()
        for (i in bytes.indices) {
            sb.append(String.format("%02x", bytes[i]))
            if (i == 3 || i == 5 || i == 7 || i == 9) sb.append("-")
        }
        return sb.toString()
    }
}

class CgiException(val code: Int, val msg: String) : IOException("业务错误 $code: $msg")