package com.baiji.music.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.baiji.music.R
import com.baiji.music.databinding.ActivityPlayerBinding
import com.baiji.music.network.MusicApi
import com.baiji.music.network.Quality
import com.baiji.music.network.Song
import com.baiji.music.network.Source
import com.baiji.music.network.netease.NeteaseQuality
import com.baiji.music.player.PlayerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPlayerBinding
    private var songMid: String = ""
    private var songId: Long = 0L
    private var songName: String = ""
    private var songSinger: String = ""
    private var songSource: String = Source.QQ
    private var currentQuality: Quality = Quality.PLAYBACK_DEFAULT
    private var currentNeteaseQuality: NeteaseQuality = NeteaseQuality.PLAYBACK_DEFAULT

    private val isNetease get() = songSource == Source.NETEASE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        songMid = intent.getStringExtra("song_mid") ?: ""
        songId = intent.getLongExtra("song_id", 0L)
        songName = intent.getStringExtra("song_name") ?: ""
        songSinger = intent.getStringExtra("song_singer") ?: ""
        songSource = intent.getStringExtra("song_source") ?: Source.QQ

        // 应用持久化的默认播放音质
        PlayerController.loadDefaultQuality(this)
        currentQuality = PlayerController.currentQuality
        currentNeteaseQuality = PlayerController.currentNeteaseQuality

        binding.textTitle.text = songName
        binding.textSinger.text = songSinger
        binding.btnQuality.text = "音质：${currentQualityLabel()}"

        binding.btnQuality.setOnClickListener {
            showQualityPicker()
        }

        binding.btnPlayer.player = PlayerController.get(this)

        // 显式的播放/暂停按钮
        binding.btnPlayPause.setOnClickListener {
            PlayerController.toggle(this)
        }
        PlayerController.onPlayStateChanged = { isPlaying ->
            binding.btnPlayPause.setImageResource(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            )
        }
        PlayerController.notifyState(this)

        binding.btnDownload.setOnClickListener {
            val intent = Intent(this, DownloadActivity::class.java)
            intent.putExtra("song_mid", songMid)
            intent.putExtra("song_id", songId)
            intent.putExtra("song_name", songName)
            intent.putExtra("song_singer", songSinger)
            intent.putExtra("song_source", songSource)
            startActivity(intent)
        }

        loadAndPlay()
    }

    private fun currentQualityLabel(): String =
        if (isNetease) currentNeteaseQuality.label else currentQuality.label

    private fun loadAndPlay() {
        if (songMid.isEmpty()) {
            Toast.makeText(this, "歌曲信息缺失", Toast.LENGTH_SHORT).show()
            return
        }
        binding.progress.visibility = android.view.View.VISIBLE
        lifecycleScope.launch {
            val url = try {
                withContext(Dispatchers.IO) {
                    MusicApi.playUrl(
                        Song(songMid, songId, songName, songSinger, "", "", 0, "", songSource),
                        currentQuality,
                        currentNeteaseQuality,
                    )
                }
            } catch (e: Exception) {
                null
            }
            binding.progress.visibility = android.view.View.GONE
            if (url.isNullOrEmpty()) {
                Toast.makeText(this@PlayerActivity, "获取播放链接失败，请确认已登录", Toast.LENGTH_LONG).show()
            } else {
                PlayerController.playUrl(this@PlayerActivity, url)
                // 记录播放历史
                val song = Song(songMid, songId, songName, songSinger, "", "", 0, "", songSource)
                com.baiji.music.data.HistoryStore.addPlay(this@PlayerActivity, song)
                // 显示服务器实际返回的音质（仅 QQ 直链可从前缀识别）
                val actual = if (isNetease) null else Quality.fromUrlPrefix(url)
                if (actual != null && actual != currentQuality) {
                    binding.btnQuality.text = "音质：${currentQuality.label}（实际 ${actual.label}）"
                } else {
                    binding.btnQuality.text = "音质：${currentQualityLabel()}"
                }
            }
        }
    }

    private fun showQualityPicker() {
        val labels = if (isNetease) {
            NeteaseQuality.PLAYBACK_OPTIONS.map { it.label }
        } else {
            Quality.PLAYBACK_OPTIONS.map { it.label }
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("选择播放音质")
            .setItems(labels.toTypedArray()) { _, which ->
                if (isNetease) {
                    currentNeteaseQuality = NeteaseQuality.PLAYBACK_OPTIONS[which]
                    PlayerController.saveNeteaseQuality(this, currentNeteaseQuality)
                } else {
                    currentQuality = Quality.PLAYBACK_OPTIONS[which]
                    PlayerController.saveDefaultQuality(this, currentQuality)
                }
                binding.btnQuality.text = "音质：${currentQualityLabel()}"
                loadAndPlay()
            }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.btnPlayer.player = null
        PlayerController.onPlayStateChanged = null
    }
}
