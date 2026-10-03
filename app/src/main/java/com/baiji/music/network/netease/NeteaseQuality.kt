package com.baiji.music.network.netease

/**
 * 网易云音质等级。
 * level 为接口 level 参数，ext 为接口返回文件类型的兜底扩展名。
 */
enum class NeteaseQuality(
    val level: String,
    val label: String,
    val ext: String,
) {
    DOLBY("dolby", "杜比全景声", ".mp4"),
    JYMASTER("jymaster", "超清母带", ".flac"),
    SKY("sky", "沉浸环绕声", ".flac"),
    JYEFFECT("jyeffect", "高清环绕声", ".flac"),
    HIRES("hires", "Hi-Res", ".flac"),
    LOSSLESS("lossless", "无损 FLAC", ".flac"),
    EXHIGH("exhigh", "极高 320k", ".mp3"),
    STANDARD("standard", "标准 128k", ".mp3"),
    ;

    companion object {
        val PLAYBACK_DEFAULT = EXHIGH

        val DOWNLOAD_OPTIONS = listOf(DOLBY, JYMASTER, SKY, JYEFFECT, HIRES, LOSSLESS, EXHIGH, STANDARD)
        val PLAYBACK_OPTIONS = listOf(DOLBY, JYMASTER, SKY, JYEFFECT, HIRES, LOSSLESS, EXHIGH, STANDARD)

        fun fromLevel(level: String?): NeteaseQuality? {
            if (level == null) return null
            return entries.firstOrNull { it.level == level }
        }
    }
}
