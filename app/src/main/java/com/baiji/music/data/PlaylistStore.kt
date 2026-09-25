package com.baiji.music.data

import android.content.Context
import com.baiji.music.network.Song
import org.json.JSONArray
import org.json.JSONObject

/**
 * 本地歌单存储：使用 SharedPreferences 持久化。
 * 支持创建歌单、收藏歌曲、移除歌曲、删除歌单。
 */
object PlaylistStore {
    private const val PREFS = "playlists"
    private const val KEY_PLAYLISTS = "playlist_list"

    private data class Playlist(
        val name: String,
        val songs: MutableList<Song> = mutableListOf(),
    )

    @Synchronized
    private fun readAll(context: Context): MutableList<Playlist> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_PLAYLISTS, null) ?: return mutableListOf()
        val list = mutableListOf<Playlist>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i)
                val name = obj?.optString("name") ?: continue
                val pl = Playlist(name)
                val songs = obj.optJSONArray("songs") ?: JSONArray()
                for (j in 0 until songs.length()) {
                    val s = songs.optJSONObject(j)
                    val song = Song(
                        mid = s.optString("mid"),
                        songId = s.optLong("songId"),
                        name = s.optString("name"),
                        singer = s.optString("singer"),
                        album = s.optString("album"),
                        albumMid = s.optString("albumMid"),
                        duration = s.optLong("duration"),
                        cover = s.optString("cover"),
                    )
                    if (song.mid.isNotEmpty()) pl.songs.add(song)
                }
                list.add(pl)
            }
        } catch (_: Exception) {}
        return list
    }

    @Synchronized
    private fun writeAll(context: Context, list: List<Playlist>) {
        val arr = JSONArray()
        for (pl in list) {
            val obj = JSONObject()
            obj.put("name", pl.name)
            val songs = JSONArray()
            for (s in pl.songs) {
                val so = JSONObject()
                so.put("mid", s.mid)
                so.put("songId", s.songId)
                so.put("name", s.name)
                so.put("singer", s.singer)
                so.put("album", s.album)
                so.put("albumMid", s.albumMid)
                so.put("duration", s.duration)
                so.put("cover", s.cover)
                songs.put(so)
            }
            obj.put("songs", songs)
            arr.put(obj)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_PLAYLISTS, arr.toString()).apply()
    }

    /** 歌单名称列表 */
    fun playlistNames(context: Context): List<String> =
        readAll(context).map { it.name }

    /** 获取某歌单的歌曲 */
    fun songsOf(context: Context, name: String): List<Song> =
        readAll(context).firstOrNull { it.name == name }?.songs ?: emptyList()

    /** 创建歌单，返回是否成功（已存在则失败） */
    fun createPlaylist(context: Context, name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        val list = readAll(context)
        if (list.any { it.name == trimmed }) return false
        list.add(Playlist(trimmed))
        writeAll(context, list)
        return true
    }

    /** 删除歌单 */
    fun deletePlaylist(context: Context, name: String) {
        val list = readAll(context).filterNot { it.name == name }
        writeAll(context, list)
    }

    /** 收藏歌曲到歌单，返回是否成功（已存在则失败） */
    fun addSong(context: Context, playlist: String, song: Song): Boolean {
        val list = readAll(context)
        val pl = list.firstOrNull { it.name == playlist } ?: return false
        if (pl.songs.any { it.mid == song.mid }) return false
        pl.songs.add(song)
        writeAll(context, list)
        return true
    }

    /** 从歌单移除歌曲 */
    fun removeSong(context: Context, playlist: String, songMid: String) {
        val list = readAll(context)
        val pl = list.firstOrNull { it.name == playlist } ?: return
        pl.songs.removeAll { it.mid == songMid }
        writeAll(context, list)
    }
}