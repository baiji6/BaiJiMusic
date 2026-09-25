package com.baiji.music.network

import org.json.JSONObject

class SongApi(private val client: QQMusicClient) {

    companion object {
        const val SONG_URL_FALLBACK_DOMAIN = "https://isure.stream.qqmusic.qq.com/"
    }

    /** 获取歌曲详情（用于把 song_id 转 mid 或取歌曲信息） */
    fun getDetail(mid: String): JSONObject? {
        val param = JSONObject()
        if (mid.all { it.isDigit() }) param.put("song_id", mid.toLong())
        else param.put("song_mid", mid)
        val data = client.execute(
            QQMusicClient.BizRequest(
                module = "music.pf_song_detail_svr",
                method = "get_song_detail_yqq",
                param = param,
            )
        )
        return data.optJSONObject("track_info")
    }

    /** 获取播放链接 */
    fun getPlayUrl(mid: String, quality: Quality): String {
        val url = getPlayUrls(listOf(mid), quality)[mid] ?: ""
        return url
    }

    fun getPlayUrls(mids: List<String>, quality: Quality): Map<String, String> {
        if (mids.isEmpty()) return emptyMap()
        val type = quality
        val filenameArr = mids.map { it.let { m -> type.filenameFor(m) } }
        val uin = client.credential.strMusicid.ifEmpty { client.credential.musicid.toString() }

        val param = JSONObject()
        param.put("guid", client.device.openUdid())
        val songmidArr = org.json.JSONArray()
        mids.forEach { songmidArr.put(it) }
        param.put("songmid", songmidArr)
        val songtypeArr = org.json.JSONArray()
        mids.forEach { songtypeArr.put(0) }
        param.put("songtype", songtypeArr)
        val filenameArrJSON = org.json.JSONArray()
        filenameArr.forEach { filenameArrJSON.put(it) }
        param.put("filename", filenameArrJSON)
        param.put("uin", uin)
        param.put("loginflag", 1)
        param.put("platform", "23")
        param.put("h5queryversion", 1)
        param.put("nettype", "")
        param.put("jsonpCallback", "jsonp1")
        param.put("cms", 0)
        param.put("firstlogin", 1)
        param.put("newver", 1)
        param.put("nohash", 0)
        param.put("format", "json")
        param.put("inCharset", "utf-8")
        param.put("outCharset", "utf-8")
        param.put("notice", 0)
        param.put("needNewCode", 0)
        param.put("songmid_pre", "")
        param.put("soundname", "")
        param.put("bitrate", type.bitrate)
        param.put("quality", type.code)

        val data = client.execute(
            QQMusicClient.BizRequest(
                module = "music.vkey.GetVkey",
                method = "UrlGetVkey",
                param = param,
            )
        )
        val sipArr = data.optJSONArray("sip")
        val sip = if (sipArr != null && sipArr.length() > 0) {
            (0 until sipArr.length()).map { sipArr.optString(it) }
        } else emptyList()
        val midUrlInfo = data.optJSONArray("midurlinfo")
        val result = HashMap<String, String>()
        if (midUrlInfo != null) {
            for (i in 0 until midUrlInfo.length()) {
                val item = midUrlInfo.optJSONObject(i)
                val m = item?.optString("songmid") ?: continue
                val purl = item.optString("purl")
                if (purl.isEmpty()) continue
                val url = when {
                    purl.startsWith("http://") || purl.startsWith("https://") -> purl
                    sip.isNotEmpty() -> sip[sip.indices.random()] + purl
                    else -> SONG_URL_FALLBACK_DOMAIN + purl
                }
                result[m] = url
                // 诊断：请求音质 vs 服务器实际返回音质
                val actual = Quality.fromUrlPrefix(url)
                com.baiji.music.util.AppLog.d(
                    "SongApi", "GetVkey 请求音质=${type.code}(${type.label}) 返回=${actual?.code ?: "未知"} url_pref=${url.substringAfterLast('/').substringBefore('?').take(4)}"
                )
            }
        }
        return result
    }

    /** 获取歌词（返回解密后的 LRC 文本） */
    fun getLyrics(mid: String): String {
        val param = JSONObject()
        if (mid.all { it.isDigit() }) param.put("songId", mid.toLong())
        else param.put("songMID", mid)
        param.put("crypt", 1)
        param.put("lrc_t", 0)
        param.put("qrc", 1)
        param.put("qrc_t", 0)
        param.put("roma", 0)
        param.put("roma_t", 0)
        param.put("trans", 1)
        param.put("trans_t", 0)
        param.put("type", 1)
        param.put("userIP", "127.0.0.1")
        param.put("ct", 11)
        param.put("cv", 14090008)

        val data = client.execute(
            QQMusicClient.BizRequest(
                module = "music.musichallSong.PlayLyricInfo",
                method = "GetPlayLyricInfo",
                param = param,
            )
        )
        val lyric = data.optJSONObject("lyric")
        val qrc = lyric?.optString("qrc") ?: ""
        if (qrc.isNotEmpty()) {
            return try {
                com.baiji.music.native.SecurityApi.qrcDecrypt(qrc)
            } catch (e: Exception) {
                lyric?.optString("trans") ?: ""
            }
        }
        return lyric?.optString("trans") ?: ""
    }
}