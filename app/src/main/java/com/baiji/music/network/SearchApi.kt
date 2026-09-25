package com.baiji.music.network

import com.baiji.music.util.AppLog
import org.json.JSONObject

class SearchApi(private val client: QQMusicClient) {

    /** 按类型搜索歌曲 */
    fun searchByType(keyword: String, page: Int = 1, num: Int = 20): List<Song> {
        val param = JSONObject()
        param.put("searchid", client.getSearchId())
        param.put("query", keyword)
        param.put("search_type", 0) // SONG
        param.put("num_per_page", num)
        param.put("page_num", page)
        param.put("highlight", 1)
        param.put("grp", true)

        AppLog.i("SearchApi", "开始搜索 keyword=$keyword page=$page num=$num")
        val data = client.execute(
            QQMusicClient.BizRequest(
                module = "music.search.SearchCgiService",
                method = "DoSearchForQQMusicMobile",
                param = param,
            )
        )
        val body = data.optJSONObject("body")
        // Mobile 接口歌曲在 body.item_song（平铺字段）；部分场景也可能在 body.song.list
        val list = body?.optJSONArray("item_song")
            ?: body?.optJSONObject("song")?.optJSONArray("list")
            ?: data.optJSONArray("item_song")
        val result = ArrayList<Song>()
        if (list != null) {
            for (i in 0 until list.length()) {
                val item = list.optJSONObject(i)
                if (item == null) continue
                val track = item.optJSONObject("track_info") ?: item
                val s = Song.fromTrack(track)
                if (s.mid.isNotEmpty()) result.add(s)
            }
        }
        AppLog.i("SearchApi", "搜索结果 keyword=$keyword 命中=${result.size} 原始条目=${list?.length() ?: 0}")
        return result
    }

    /** 综合搜索（反馈展示名称/专辑/歌手） */
    fun generalSearch(keyword: String, page: Int = 1, num: Int = 20): List<Song> {
        val param = JSONObject()
        param.put("searchid", client.getSearchId())
        param.put("search_type", 100)
        param.put("page_num", num)
        param.put("query", keyword)
        param.put("page_id", page)
        param.put("highlight", 1)
        param.put("grp", true)

        AppLog.i("SearchApi", "开始综合搜索 keyword=$keyword page=$page num=$num")
        val data = client.execute(
            QQMusicClient.BizRequest(
                module = "music.adaptor.SearchAdaptor",
                method = "do_search_v2",
                param = param,
            )
        )
        val body = data.optJSONObject("body")
        // 综合搜索歌曲在 body.item_song.items（平铺字段）
        val itemSong = body?.optJSONObject("item_song")
        val list = itemSong?.optJSONArray("items")
            ?: body?.optJSONObject("song")?.optJSONArray("list")
        val result = ArrayList<Song>()
        if (list != null) {
            for (i in 0 until list.length()) {
                val item = list.optJSONObject(i)
                if (item == null) continue
                val track = item.optJSONObject("track_info") ?: item
                val s = Song.fromTrack(track)
                if (s.mid.isNotEmpty()) result.add(s)
            }
        }
        AppLog.i("SearchApi", "综合搜索结果 keyword=$keyword 命中=${result.size} 原始条目=${list?.length() ?: 0}")
        return result
    }
}