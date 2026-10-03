package com.baiji.music.network

import com.baiji.music.App
import com.baiji.music.network.netease.NeteaseQuality
import com.baiji.music.util.AppLog

/**
 * 音源路由：按来源分发到 QQ 音乐或网易云的实现。
 */
object MusicApi {

    fun isLoggedIn(source: String): Boolean =
        if (source == Source.NETEASE) App.netease.isLoggedIn() else App.api.isLoggedIn()

    /** 按来源搜索 */
    fun search(source: String, keyword: String, page: Int = 1, num: Int = 20): List<Song> =
        if (source == Source.NETEASE) {
            App.netease.api.search(keyword, num, (page - 1) * num)
        } else {
            App.api.search.searchByType(keyword, page, num)
        }

    /** 按来源获取相关搜索建议；失败时返回空列表，不影响正常搜索 */
    fun suggestions(source: String, keyword: String, num: Int = 8): List<String> =
        try {
            if (source == Source.NETEASE) {
                App.netease.api.suggestions(keyword, num)
            } else {
                App.api.search.suggestions(keyword, num)
            }
        } catch (e: Exception) {
            AppLog.w("MusicApi", "获取相关搜索失败: ${e.message}")
            emptyList()
        }

    /** 按歌曲来源取播放直链（qqQuality 用于 QQ 音源，neQuality 用于网易云） */
    fun playUrl(song: Song, qqQuality: Quality, neQuality: NeteaseQuality): String =
        if (song.isNetease) {
            App.netease.api.songUrl(song.songId, neQuality).url
        } else {
            App.api.song.getPlayUrl(song.mid, qqQuality)
        }
}
