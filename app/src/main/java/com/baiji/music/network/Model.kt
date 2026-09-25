package com.baiji.music.network

import org.json.JSONObject

/** 音质枚举 */
enum class Quality(
    val code: String,
    val ext: String,
    val label: String,
    val bitrate: Int,
) {
    DTS_X("DT03", ".mp4", "DTS:X", 0),
    ATMOS_DB("D004", ".mp4", "杜比全景声", 0),
    ATMOS_71("Q003", ".ogg", "臻品全景声 7.1", 0),
    ATMOS_51("Q001", ".flac", "臻品全景声 5.1", 0),
    ATMOS_2("Q000", ".flac", "臻品音质", 0),
    MASTER("AI00", ".flac", "臻品母带", 0),
    FLAC("F000", ".flac", "SQ 无损", 0),
    HIRES("RS01", ".flac", "Hi-Res", 0),
    NAC("TL01", ".nac", "NAC 音质", 128),
    OGG_640("O801", ".ogg", "OGG 640", 0),
    OGG_320("O800", ".ogg", "OGG 320", 320),
    OGG_192("O600", ".ogg", "OGG 192", 192),
    MP3_320("M800", ".mp3", "MP3 320", 320),
    MP3_128("M500", ".mp3", "MP3 128", 128),
    AAC_192("C600", ".m4a", "AAC 192", 192),
    AAC_96("C400", ".m4a", "AAC 96", 96),
    AAC_48("C200", ".m4a", "AAC 48", 48),
    ;

    companion object {
        // 播放默认 128k
        val PLAYBACK_DEFAULT = MP3_128
        // 下载可选音质（含杜比/全景声/DTS）
        val DOWNLOAD_OPTIONS = listOf(
            DTS_X, ATMOS_DB, ATMOS_71, ATMOS_51, ATMOS_2, MASTER,
            FLAC, HIRES, NAC, OGG_640, OGG_320, OGG_192, MP3_320, MP3_128, AAC_192, AAC_96, AAC_48
        )
        val PLAYBACK_OPTIONS = listOf(
            DTS_X, ATMOS_DB, ATMOS_71, ATMOS_51, ATMOS_2, MASTER,
            FLAC, HIRES, NAC, OGG_640, OGG_320, OGG_192, MP3_320, MP3_128, AAC_192, AAC_96, AAC_48
        )

        fun fromCode(code: String?): Quality? {
            if (code == null) return null
            return entries.firstOrNull { it.code == code }
        }

        /**
         * 从播放链接的文件名前缀推断服务器实际提供的音质。
         * 例：<url>/M800xxxxxxxx.mp3  → MP3_320；<url>/F000...flac → FLAC。
         * 无法识别返回 null。
         */
        fun fromUrlPrefix(url: String): Quality? {
            // 取最后一段路径（文件名）
            val name = url.substringAfterLast('/').substringBefore('?')
            if (name.isEmpty()) return null
            // 优先匹配 4 位前缀
            val prefix4 = name.take(4)
            entries.firstOrNull { it.code == prefix4 }?.let { return it }
            // 兼容部分无后缀/特殊前缀
            entries.firstOrNull { name.startsWith(it.code) }?.let { return it }
            return null
        }
    }

    fun filenameFor(mid: String): String = "$code$mid$mid$ext"

    fun inferExt(): String = ext
}

/** 歌曲信息 */
data class Song(
    val mid: String,
    val songId: Long,
    val name: String,
    val singer: String,
    val album: String,
    val albumMid: String,
    val duration: Long,
    val cover: String,
) {
    companion object {
        fun fromTrack(t: JSONObject): Song {
            val mid = t.optString("mid")
            val info = t.optJSONObject("info")
            // 兼容搜索返回结构
            val name = t.optString("name").ifEmpty { t.optString("title") }
            val albumName = t.optString("album").ifEmpty {
                t.optJSONObject("album")?.optString("name") ?: ""
            }
            val albumMid = t.optJSONObject("album")?.optString("mid") ?: ""
            val singer = t.optJSONArray("singer")?.let { arr ->
                (0 until arr.length()).joinToString(" / ") { i ->
                    arr.optJSONObject(i)?.optString("name") ?: ""
                }
            } ?: t.optString("singer").ifEmpty { t.optString("singerName") }

            val cover = t.optJSONObject("album")?.optJSONObject("picUrl")?.optString("s") ?: ""
            val duration = t.optLong("interval") * 1000
            return Song(
                mid = mid,
                songId = t.optLong("id"),
                name = name,
                singer = singer,
                album = albumName,
                albumMid = albumMid,
                duration = duration,
                cover = cover,
            )
        }
    }
}