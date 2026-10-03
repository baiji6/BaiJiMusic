package com.baiji.music.network.netease

import com.baiji.music.network.Song
import com.baiji.music.network.Source
import com.baiji.music.util.AppLog
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * 网易云业务接口：搜索、歌曲详情、取流、歌词、专辑、歌单、封面。
 * 对应 Python 参考实现（Netease_url）的接口一一移植。
 */
class NeteaseApi(private val client: NeteaseClient) {

    companion object {
        private const val TAG = "NeteaseApi"
    }

    /** 取流结果 */
    data class NeteaseUrl(
        val url: String,
        val ext: String,
        val level: String,
        val size: Long,
        val br: Int,
    ) {
        fun isUsable(): Boolean = url.isNotEmpty()
    }

    // ================= 搜索 =================

    /** 关键词搜索歌曲 */
    fun search(keyword: String, limit: Int = 30, offset: Int = 0): List<Song> {
        val form = linkedMapOf(
            "s" to keyword,
            "type" to "1",
            "limit" to limit.toString(),
            "offset" to offset.toString(),
            "total" to "true",
            "csrf_token" to "",
        )
        AppLog.i(TAG, "网易云搜索 keyword=$keyword limit=$limit offset=$offset")
        val json = client.postApi("${NeteaseClient.API_BASE}/cloudsearch/pc", form)
        val songs = json.optJSONObject("result")?.optJSONArray("songs")
        val result = ArrayList<Song>()
        if (songs != null) {
            for (i in 0 until songs.length()) {
                toSong(songs.optJSONObject(i))?.let { result.add(it) }
            }
        }
        AppLog.i(TAG, "网易云搜索结果 keyword=$keyword 命中=${result.size}")
        return result
    }

    // ================= 详情 =================

    /** 歌曲详情（原样返回接口 JSON） */
    fun songDetail(id: Long): JSONObject {
        return client.postApi(
            "${NeteaseClient.API_BASE}/v3/song/detail",
            mapOf("c" to JSONArray().put(JSONObject().put("id", id).put("v", 0)).toString()),
        )
    }

    /** 批量歌曲详情，映射为 [Song] */
    fun songDetailSongs(ids: List<Long>): List<Song> {
        if (ids.isEmpty()) return emptyList()
        val arr = JSONArray()
        ids.forEach { arr.put(JSONObject().put("id", it).put("v", 0)) }
        val json = client.postApi("${NeteaseClient.API_BASE}/v3/song/detail", mapOf("c" to arr.toString()))
        val songs = json.optJSONArray("songs") ?: return emptyList()
        val result = ArrayList<Song>()
        for (i in 0 until songs.length()) {
            toSong(songs.optJSONObject(i))?.let { result.add(it) }
        }
        return result
    }

    // ================= 取流 =================

    /** 获取指定音质的播放直链（EAPI /eapi/song/enhance/player/url/v1） */
    fun songUrl(id: Long, quality: NeteaseQuality): NeteaseUrl {
        val header = JSONObject()
        header.put("os", "pc")
        header.put("appver", "")
        header.put("osver", "")
        header.put("deviceId", "pyncm!")
        header.put("requestId", (20_000_000..29_999_999).random().toString())

        val payload = JSONObject()
        payload.put("ids", JSONArray().put(id))
        payload.put("level", quality.level)
        payload.put("encodeType", if (quality == NeteaseQuality.DOLBY) "mp4" else "flac")
        payload.put("header", header.toString())
        if (quality == NeteaseQuality.SKY) payload.put("immerseType", "c51")

        AppLog.d(TAG, "网易云取流 id=$id quality=${quality.level}(${quality.label})")
        val json = client.postEapi("${NeteaseClient.EAPI_BASE}/song/enhance/player/url/v1", payload)
        if (json.optInt("code") != 200) {
            throw IOException("网易云取流失败: code=${json.optInt("code")} msg=${json.optString("message")}")
        }
        val item = json.optJSONArray("data")?.optJSONObject(0)
            ?: return NeteaseUrl("", quality.ext, quality.level, 0, 0)
        val url = item.optString("url")
        val type = item.optString("type")
        val ext = if (type.isNotEmpty()) ".$type" else quality.ext
        val actualLevel = item.optString("level").ifEmpty { quality.level }
        AppLog.d(TAG, "网易云取流返回 level=$actualLevel type=$type br=${item.optInt("br")} url_empty=${url.isEmpty()}")
        return NeteaseUrl(url, ext, actualLevel, item.optLong("size"), item.optInt("br"))
    }

    // ================= 歌词 =================

    /** 获取歌词（原文，无则返回翻译） */
    fun lyric(id: Long): String {
        val form = linkedMapOf(
            "id" to id.toString(),
            "cp" to "false",
            "tv" to "0",
            "lv" to "-1",
            "rv" to "0",
            "kv" to "-1",
            "yv" to "0",
            "ytv" to "0",
            "yrv" to "0",
        )
        val json = client.postApi("${NeteaseClient.API_BASE}/song/lyric", form)
        val lrc = json.optJSONObject("lrc")?.optString("lyric") ?: ""
        if (lrc.isNotEmpty()) return lrc
        return json.optJSONObject("tlyric")?.optString("lyric") ?: ""
    }

    // ================= 歌单 / 专辑 =================

    /** 歌单详情中的歌曲列表 */
    fun playlistDetail(playlistId: Long): List<Song> {
        val json = client.postApi(
            "${NeteaseClient.API_BASE}/v6/playlist/detail",
            mapOf("id" to playlistId.toString(), "n" to "100000", "s" to "0"),
        )
        val playlist = json.optJSONObject("playlist") ?: return emptyList()
        val tracks = playlist.optJSONArray("tracks") ?: return emptyList()
        val result = ArrayList<Song>()
        for (i in 0 until tracks.length()) {
            toSong(tracks.optJSONObject(i))?.let { result.add(it) }
        }
        return result
    }

    /** 专辑详情中的歌曲列表 */
    fun albumDetail(albumId: Long): List<Song> {
        val json = client.getApi("${NeteaseClient.API_BASE}/v1/album/$albumId")
        val album = json.optJSONObject("album")
        val albumName = album?.optString("name") ?: ""
        val picId = album?.optLong("pic") ?: 0L
        val albumCover = NeteaseCrypto.picUrl(picId, 300)
        val songs = json.optJSONArray("songs") ?: return emptyList()
        val result = ArrayList<Song>()
        for (i in 0 until songs.length()) {
            val o = songs.optJSONObject(i) ?: continue
            val song = toSong(o) ?: continue
            // 专辑接口的歌曲可能缺少专辑信息，补齐
            result.add(
                if (song.album.isEmpty()) song.copy(album = albumName, cover = song.cover.ifEmpty { albumCover })
                else song
            )
        }
        return result
    }

    // ================= 映射 =================

    /** 网易云接口 JSON → 通用 [Song]（mid 前缀 ne 以区分来源，songId 保存数字 ID） */
    private fun toSong(o: JSONObject?): Song? {
        if (o == null) return null
        val id = o.optLong("id")
        if (id == 0L) return null

        val name = o.optString("name")
        val ar = o.optJSONArray("ar") ?: o.optJSONArray("artists")
        val singer = buildSinger(ar)

        val al = o.optJSONObject("al") ?: o.optJSONObject("album")
        val album = al?.optString("name") ?: ""
        val picId = al?.optLong("pic") ?: 0L
        val cover = al?.optString("picUrl")?.takeIf { it.isNotEmpty() }
            ?: NeteaseCrypto.picUrl(picId, 300)

        var duration = o.optLong("dt")
        if (duration <= 0) duration = o.optLong("duration")

        return Song(
            mid = "ne$id",
            songId = id,
            name = name,
            singer = singer,
            album = album,
            albumMid = "",
            duration = duration,
            cover = cover,
            source = Source.NETEASE,
        )
    }

    private fun buildSinger(arr: JSONArray?): String {
        if (arr == null || arr.length() == 0) return ""
        val names = ArrayList<String>()
        for (i in 0 until arr.length()) {
            val n = arr.optJSONObject(i)?.optString("name") ?: ""
            if (n.isNotEmpty()) names.add(n)
        }
        return names.joinToString(" / ")
    }
}
