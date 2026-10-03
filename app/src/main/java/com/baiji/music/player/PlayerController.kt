package com.baiji.music.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.baiji.music.network.Quality
import com.baiji.music.network.Song
import com.baiji.music.network.netease.NeteaseQuality

/**
 * 全局播放控制器（单例）。
 * 在线播放默认 128k，可切换音质（默认音质持久化保存）。
 * QQ 与网易云分别保存默认音质。
 */
object PlayerController {
    private const val PREFS = "player_prefs"
    private const val KEY_QUALITY = "default_quality"
    private const val KEY_NETEASE_QUALITY = "default_netease_quality"

    private var player: ExoPlayer? = null
    var currentSong: Song? = null
    var currentQuality: Quality = Quality.PLAYBACK_DEFAULT
    var currentNeteaseQuality: NeteaseQuality = NeteaseQuality.PLAYBACK_DEFAULT
    var urlProvider: ((String, Quality) -> String)? = null

    /** 播放状态变化监听器（用于刷新播放/暂停按钮图标） */
    var onPlayStateChanged: ((Boolean) -> Unit)? = null

    fun get(context: Context): ExoPlayer {
        player?.let { return it }
        val p = ExoPlayer.Builder(context).build()
        p.repeatMode = Player.REPEAT_MODE_OFF
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                onPlayStateChanged?.invoke(isPlaying)
            }
        })
        player = p
        return p
    }

    /** 读取并应用持久化的默认播放音质 */
    fun loadDefaultQuality(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        currentQuality = Quality.fromCode(prefs.getString(KEY_QUALITY, null)) ?: Quality.PLAYBACK_DEFAULT
        currentNeteaseQuality = NeteaseQuality.fromLevel(prefs.getString(KEY_NETEASE_QUALITY, null))
            ?: NeteaseQuality.PLAYBACK_DEFAULT
    }

    /** 持久化默认播放音质 */
    fun saveDefaultQuality(context: Context, quality: Quality) {
        currentQuality = quality
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_QUALITY, quality.code).apply()
    }

    /** 持久化网易云默认播放音质 */
    fun saveNeteaseQuality(context: Context, quality: NeteaseQuality) {
        currentNeteaseQuality = quality
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_NETEASE_QUALITY, quality.level).apply()
    }

    fun play(context: Context, song: Song, url: String) {
        currentSong = song
        val p = get(context)
        val item = MediaItem.fromUri(url)
        p.setMediaItem(item)
        p.prepare()
        p.play()
    }

    fun playUrl(context: Context, url: String) {
        val p = get(context)
        p.setMediaItem(MediaItem.fromUri(url))
        p.prepare()
        p.play()
    }

    fun toggle(context: Context) {
        val p = get(context)
        if (p.isPlaying) p.pause() else p.play()
        onPlayStateChanged?.invoke(p.isPlaying)
    }

    /** 主动触发一次状态刷新（供 UI 初始化/恢复时使用） */
    fun notifyState(context: Context) {
        val p = player
        if (p != null) onPlayStateChanged?.invoke(p.isPlaying)
    }

    fun isPlaying(context: Context): Boolean = get(context).isPlaying

    fun release() {
        player?.release()
        player = null
    }
}