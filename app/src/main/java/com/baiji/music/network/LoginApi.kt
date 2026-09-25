package com.baiji.music.network

import com.baiji.music.data.Credential
import com.baiji.music.native.SecurityApi
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.io.IOException
import java.util.regex.Pattern
import android.util.Log

/**
 * QQ 音乐登录（二维码方式）。
 */
class LoginApi(private val client: QQMusicClient) {

    companion object {
        private const val REFERER = "https://xui.ptlogin2.qq.com/"
        private val QQ_STATUS_RE = Pattern.compile("ptuiCB\\((.*?)\\)")
        private val QQ_ARGS_RE = Pattern.compile("'((?:\\\\.|[^'])*)'")
        private val QQ_SIGX_RE = Pattern.compile("(?:\\?|&)ptsigx=(.+?)&s_url")
        private val QQ_UIN_RE = Pattern.compile("(?:\\?|&)uin=(.+?)&service")
    }

    data class Qrcode(val image: ByteArray, val qrsig: String)

    enum class QrEvent { DONE, SCAN, CONF, REFUSE, TIMEOUT, OTHER }

    /** 获取 QQ 登录二维码 */
    fun getQrcode(): Qrcode {
        val url = "https://ssl.ptlogin2.qq.com/ptqrshow"
        val params = mapOf(
            "appid" to "716027609",
            "e" to "2",
            "l" to "M",
            "s" to "3",
            "d" to "72",
            "v" to "4",
            "t" to Math.random().toString(),
            "daid" to "383",
            "pt_3rd_aid" to "100497308",
        )
        val qb = url.toHttpUrlOrNull()!!.newBuilder()
        params.forEach { (k, v) -> qb.addQueryParameter(k, v) }
        val fullUrl = qb.build().toString()
        val headers = mapOf("Referer" to REFERER)
        // 单次请求同时取图片字节与 qrsig，保证图片与轮询使用的是同一个二维码会话
        val qr = client.rawQrcode(fullUrl, headers)
        val qrsig = qr.cookies["qrsig"] ?: throw IOException("获取 qrsig 失败")
        return Qrcode(qr.bytes, qrsig)
    }

    data class QrCheck(val event: QrEvent, val uin: String = "", val sigx: String = "")

    /** 检查二维码状态 */
    fun checkQrcode(qrsig: String): QrCheck {
        val url = "https://ssl.ptlogin2.qq.com/ptqrlogin"
        val params = mapOf(
            "u1" to "https://graph.qq.com/oauth2.0/login_jump",
            "ptqrtoken" to SecurityApi.hash33(qrsig).toString(),
            "ptredirect" to "0",
            "h" to "1",
            "t" to "1",
            "g" to "1",
            "from_ui" to "1",
            "ptlang" to "2052",
            "action" to "0-0-${System.currentTimeMillis()}",
            "js_ver" to "20102616",
            "js_type" to "1",
            "pt_uistyle" to "40",
            "aid" to "716027609",
            "daid" to "383",
            "pt_3rd_aid" to "100497308",
            "has_onekey" to "1",
        )
        val qb = url.toHttpUrlOrNull()!!.newBuilder()
        params.forEach { (k, v) -> qb.addQueryParameter(k, v) }
        val fullUrl = qb.build().toString()
        val headers = mapOf("Referer" to REFERER, "Cookie" to "qrsig=$qrsig")
        val resp = client.rawRequest("GET", fullUrl, headers)
        val m = QQ_STATUS_RE.matcher(resp.text)
        if (!m.find()) return QrCheck(QrEvent.OTHER)
        val inner = m.group(1) ?: return QrCheck(QrEvent.OTHER)
        val args = ArrayList<String>()
        val am = QQ_ARGS_RE.matcher(inner)
        while (am.find()) {
            am.group(1)?.let { args.add(it) }
        }
        if (args.isEmpty()) return QrCheck(QrEvent.OTHER)
        val code = args[0].toIntOrNull() ?: return QrCheck(QrEvent.OTHER)
        val event = when (code) {
            0 -> QrEvent.DONE
            66 -> QrEvent.SCAN
            67 -> QrEvent.CONF
            65 -> QrEvent.TIMEOUT
            else -> QrEvent.OTHER
        }
        if (event != QrEvent.DONE || args.size < 3) return QrCheck(event)
        val sigxM = QQ_SIGX_RE.matcher(args[2])
        val uinM = QQ_UIN_RE.matcher(args[2])
        if (!sigxM.find() || !uinM.find()) return QrCheck(event)
        val uin = uinM.group(1) ?: return QrCheck(event)
        val sigx = sigxM.group(1) ?: return QrCheck(event)
        return QrCheck(event, uin, sigx)
    }

    /** 扫码确认后换取凭证 */
    fun authorizeQr(uin: String, sigx: String): Credential {
        // 1. check_sig（禁止重定向）
        val checkUrl = "https://ssl.ptlogin2.graph.qq.com/check_sig"
        val checkParams = mapOf(
            "uin" to uin,
            "pttype" to "1",
            "service" to "ptqrlogin",
            "nodirect" to "0",
            "ptsigx" to sigx,
            "s_url" to "https://graph.qq.com/oauth2.0/login_jump",
            "ptlang" to "2052",
            "ptredirect" to "100",
            "aid" to "716027609",
            "daid" to "383",
            "j_later" to "0",
            "low_login_hour" to "0",
            "regmaster" to "0",
            "pt_login_type" to "3",
            "pt_aid" to "0",
            "pt_aaid" to "16",
            "pt_light" to "0",
            "pt_3rd_aid" to "100497308",
        )
        val cb = checkUrl.toHttpUrlOrNull()!!.newBuilder()
        checkParams.forEach { (k, v) -> cb.addQueryParameter(k, v) }
        val checkResp = client.rawRequestNoRedirect(
            "GET", cb.build().toString(), mapOf("Referer" to REFERER)
        )
        val cookies = checkResp.cookies
        val pSkey = cookies["p_skey"] ?: cookies["p-skey"] ?: cookies["pskey"]
            ?: cookies["ptsigx"] ?: cookies["skey"]
            ?: throw IOException("获取 p_skey 失败(status=${checkResp.status}, cookies=${cookies.keys})")
        Log.d("BaiJiLogin", "check_sig ok, pSkey=${pSkey.take(6)}... cookies=${cookies.keys}")

        // 2. authorize -> code（code 在 302 重定向的 Location 头中，必须禁止重定向）
        val authUrl = "https://graph.qq.com/oauth2.0/authorize"
        val cookieStr = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
        val body = mapOf(
            "response_type" to "code",
            "client_id" to "100497308",
            "redirect_uri" to "https://y.qq.com/portal/wx_redirect.html?login_type=1&surl=https://y.qq.com/",
            "scope" to "get_user_info,get_app_friends",
            "state" to "state",
            "switch" to "",
            "from_ptlogin" to "1",
            "src" to "1",
            "update_auth" to "1",
            "openapi" to "1010_1030",
            "g_tk" to SecurityApi.hash33(pSkey, 5381).toString(),
            "auth_time" to System.currentTimeMillis().toString(),
            "ui" to client.randomUuid(),
        ).entries.joinToString("&") { "${it.key}=${encode(it.value)}" }

        val authHeaders = mapOf(
            "Content-Type" to "application/x-www-form-urlencoded",
            "Referer" to REFERER,
            "Cookie" to cookieStr,
        )
        val authResp = client.rawRequestNoRedirect("POST", authUrl, authHeaders, body)
        Log.d("BaiJiLogin", "authorize status=${authResp.status} location=${authResp.location.take(200) ?: "<empty>"}")
        val code = extractCode(authResp.location)
            ?: throw IOException("获取 code 失败(status=${authResp.status}, location=${authResp.location.take(200)})")

        // 3. QQConnectLogin -> Credential
        val param = JSONObject()
        param.put("code", code)
        val data = client.executeRaw(
            QQMusicClient.BizRequest(
                module = "QQConnectLogin.LoginServer",
                method = "QQLogin",
                param = param,
                allowErrorCodes = true,
            ),
            extraComm = JSONObject().put("tmeLoginType", 2),
        )
        Log.d("BaiJiLogin", "QQLogin 响应 code=${data.optInt("code")} msg=${data.optString("msg")} data=${data.opt("data")?.toString()?.take(300)}")
        val validated = validateResult(data)
        return Credential.fromDict(validated)
    }

    /**
     * 使用 QQ 音乐 Cookie 登录（扫码登录失败后的备选方式）。
     * 从 Cookie 字符串中提取 uin 与 qm_keyst/qqmusic_key，构建登录凭证。
     * 常见的 Cookie 键：uin / qqmusic_uin、qm_keyst / qqmusic_key（值为 musickey）。
     */
    fun loginByCookie(cookie: String): Credential {
        val trimmed = cookie.trim()
        if (trimmed.isEmpty()) throw IOException("Cookie 不能为空")

        val pairs = HashMap<String, String>()
        // 兼容直接粘贴整段 "Cookie: uin=...; qm_keyst=..." 的情况
        val body = trimmed.removePrefix("Cookie")
            .removePrefix("cookie")
            .removePrefix(":")
            .trimStart()
        for (part in body.split(";")) {
            val seg = part.trim()
            if (seg.isEmpty()) continue
            val idx = seg.indexOf("=")
            if (idx <= 0) continue
            val k = seg.substring(0, idx).trim()
            var v = seg.substring(idx + 1).trim()
            // 去掉值两端可能残留的引号
            if (v.length >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
                v = v.substring(1, v.length - 1)
            }
            if (k.isNotEmpty() && v.isNotEmpty()) pairs[k] = v
        }
        Log.d("BaiJiLogin", "cookie 键: ${pairs.keys.joinToString(",")}")

        // 提取 uin（兼容多种键名；有些账号 uin 形如 o123456789，需去掉非数字前缀）
        val uinStr: String = (pairs["uin"] ?: pairs["qqmusic_uin"] ?: pairs["uin_android"]
            ?: pairs["w_uin"] ?: pairs["u"])
            ?.trim()?.trim('"', '\'')
            ?: throw IOException("Cookie 中缺少 uin（应为 QQ 音乐登录后的 uin）")
        val uinNumeric = uinStr.replace(Regex("[^0-9]"), "")
        val uinLong = uinNumeric.toLongOrNull()
            ?: throw IOException("Cookie 中的 uin 无效: $uinStr")

        // 提取 musickey（兼容多种键名）
        val musickey: String = (pairs["qm_keyst"] ?: pairs["qqmusic_key"] ?: pairs["qm_key"]
            ?: pairs["musickey"] ?: pairs["qm_keyst_android"] ?: pairs["qqmusic_keyst"])
            ?.trim()?.trim('"', '\'')
            ?.takeIf { it.isNotEmpty() }
            ?: throw IOException("Cookie 中缺少 qm_keyst / qqmusic_key（应为 QQ 音乐登录后的音乐密钥）")

        val cred = Credential()
        cred.musicid = uinLong
        cred.musickey = musickey
        cred.strMusicid = uinStr
        cred.loginType = if (musickey.startsWith("W_X")) 1 else 2
        cred.musickeyCreateTime = System.currentTimeMillis() / 1000
        cred.keyExpiresIn = 0L
        Log.d("BaiJiLogin", "cookie 解析: uin=$uinStr loginType=${cred.loginType} key=${musickey.take(8)}...")

        // 尽量放宽校验：解析成功即返回凭证，校验失败仅记录日志，避免误判登录失败
        val prev = client.credential
        client.credential = cred
        try {
            val data = client.executeRaw(
                QQMusicClient.BizRequest(
                    module = "music.UserInfo.userInfoServer",
                    method = "GetLoginUserInfo",
                    param = JSONObject(),
                    allowErrorCodes = true,
                ),
            )
            val code = data.optInt("code", -1)
            Log.d("BaiJiLogin", "cookie 校验 GetLoginUserInfo code=$code")
            if (code != 0) {
                Log.w("BaiJiLogin", "cookie 校验未通过 code=$code，仍返回凭证（可能网络/风控导致）")
            }
        } catch (e: Exception) {
            Log.w("BaiJiLogin", "cookie 校验异常(不阻断登录)", e)
        } finally {
            client.credential = prev
        }
        return cred
    }

    private fun extractCode(text: String): String? {
        val m = Pattern.compile("(?<=code=)(.+?)(?=&)").matcher(text)
        return if (m.find()) m.group(1) else null
    }

    private fun encode(s: String): String {
        return java.net.URLEncoder.encode(s, "UTF-8")
    }

    /** 校验 QQLogin 返回，解包 {code, data}，并抛出可读的登录错误（对照参考实现 _validateResult） */
    private fun validateResult(resp: JSONObject): JSONObject {
        var cur = resp
        // 最多解包两层 {code, data}
        repeat(2) {
            if (cur.has("code") && cur.has("data")) {
                val c = cur.optInt("code")
                if (c != 0) {
                    val msg = when (c) {
                        1000, 104401, 104400 -> "登录鉴权已过期"
                        20261 -> "登录参数错误"
                        20271 -> "验证码错误"
                        20272 -> "账号绑定异常"
                        20274 -> "账号绑定缺失"
                        20277, 20278 -> "账号受限"
                        20279 -> "登录设备数超限"
                        20450 -> "账号已被封禁"
                        104604 -> "操作过于频繁"
                        else -> "未知登录错误 $c"
                    }
                    throw IOException("$msg ($c)")
                }
                cur = cur.optJSONObject("data") ?: JSONObject()
            }
        }
        return cur
    }
}