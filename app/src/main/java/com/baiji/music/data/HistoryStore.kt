package com.baiji.music.data

import android.content.Context
import com.baiji.music.network.Song
import com.baiji.music.network.Source
import org.json.JSONArray
import org.json.JSONObject

/**
 * 本地历史存储：播放历史 与 搜索历史。
 * 使用 SharedPreferences 持久化，重启不清空。
 */
object HistoryStore {
    private const val PREFS = "history"
    private const val KEY_SEARCH = "search_history"
    private const val KEY_PLAY = "play_history"
    private const val MAX = 30

    // ---------- 搜索历史 ----------

    @Synchronized
    private fun readSearch(context: Context): MutableList<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SEARCH, null)
        if (raw.isNullOrEmpty()) return mutableListOf()
        val list = mutableListOf<String>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val s = arr.optString(i)
                if (s.isNotEmpty()) list.add(s)
            }
        } catch (_: Exception) {}
        return list
    }

    private fun writeSearch(context: Context, list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_SEARCH, arr.toString()).apply()
    }

    /** 记录一次搜索，最新在前，去重 */
    fun addSearch(context: Context, keyword: String) {
        val k = keyword.trim()
        if (k.isEmpty()) return
        val list = readSearch(context).filterNot { it == k }.toMutableList()
        list.add(0, k)
        while (list.size > MAX) list.removeAt(list.size - 1)
        writeSearch(context, list)
    }

    /**
     * 记录一次搜索，但专用于开头搜索（避免重复记录）。
     * 返回是否已存在（用于判断是否新增）。
     */
    fun addSearchIfNew(context: Context, keyword: String): Boolean {
        val k = keyword.trim()
        if (k.isEmpty()) return false
        val list = readSearch(context)
        if (list.any { it == k }) return false
        addSearch(context, k)
        return true
    }

    fun searchHistory(context: Context): List<String> = readSearch(context)

    fun clearSearch(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_SEARCH).apply()
    }

    // ---------- 播放历史 ----------

    @Synchronized
    private fun readPlay(context: Context): MutableList<Song> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PLAY, null)
        if (raw.isNullOrEmpty()) return mutableListOf()
        val list = mutableListOf<Song>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val s = Song(
                    mid = o.optString("mid"),
                    songId = o.optLong("songId"),
                    name = o.optString("name"),
                    singer = o.optString("singer"),
                    album = o.optString("album"),
                    albumMid = o.optString("albumMid"),
                    duration = o.optLong("duration"),
                    cover = o.optString("cover"),
                    source = o.optString("source", Source.QQ),
                )
                if (s.mid.isNotEmpty()) list.add(s)
            }
        } catch (_: Exception) {}
        return list
    }

    private fun writePlay(context: Context, list: List<Song>) {
        val arr = JSONArray()
        for (s in list) {
            val o = JSONObject()
            o.put("mid", s.mid)
            o.put("songId", s.songId)
            o.put("name", s.name)
            o.put("singer", s.singer)
            o.put("album", s.album)
            o.put("albumMid", s.albumMid)
            o.put("duration", s.duration)
            o.put("cover", s.cover)
            o.put("source", s.source)
            arr.put(o)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_PLAY, arr.toString()).apply()
    }

    /** 记录一次播放，最新在前，同 mid 去重 */
    fun addPlay(context: Context, song: Song) {
        if (song.mid.isEmpty()) return
        val list = readPlay(context).filterNot { it.mid == song.mid }.toMutableList()
        list.add(0, song)
        while (list.size > MAX) list.removeAt(list.size - 1)
        writePlay(context, list)
    }

    fun playHistory(context: Context): List<Song> = readPlay(context)

    fun clearPlay(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_PLAY).apply()
    }
}