package com.baiji.music.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.ui.PlayerView
import com.baiji.music.App
import com.baiji.music.R
import com.baiji.music.databinding.ActivityPlayerBinding
import com.baiji.music.network.Quality
import com.baiji.music.network.Song
import com.baiji.music.player.PlayerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPlayerBinding
    private var songMid: String = ""
    private var songName: String = ""
    private var songSinger: String = ""
    private var currentQuality: Quality = Quality.PLAYBACK_DEFAULT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        songMid = intent.getStringExtra("song_mid") ?: ""
        songName = intent.getStringExtra("song_name") ?: ""
        songSinger = intent.getStringExtra("song_singer") ?: ""

        // 应用持久化的默认播放音质
        PlayerController.loadDefaultQuality(this)
        currentQuality = PlayerController.currentQuality

        binding.textTitle.text = songName
        binding.textSinger.text = songSinger
        binding.btnQuality.text = "音质：${currentQuality.label}"

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
            intent.putExtra("song_name", songName)
            intent.putExtra("song_singer", songSinger)
            startActivity(intent)
        }

        loadAndPlay()
    }

    private fun loadAndPlay() {
        if (songMid.isEmpty()) {
            Toast.makeText(this, "歌曲信息缺失", Toast.LENGTH_SHORT).show()
            return
        }
        binding.progress.visibility = android.view.View.VISIBLE
        lifecycleScope.launch {
            val url = try {
                withContext(Dispatchers.IO) {
                    App.api.song.getPlayUrl(songMid, currentQuality)
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
                val song = Song(songMid, 0, songName, songSinger, "", "", 0, "")
                com.baiji.music.data.HistoryStore.addPlay(this@PlayerActivity, song)
                // 显示服务器实际返回的音质，便于用户判断所选音质是否生效
                val actual = com.baiji.music.network.Quality.fromUrlPrefix(url)
                if (actual != null && actual != currentQuality) {
                    binding.btnQuality.text = "音质：${currentQuality.label}（实际 ${actual.label}）"
                } else {
                    binding.btnQuality.text = "音质：${currentQuality.label}"
                }
            }
        }
    }

    private fun showQualityPicker() {
        val options = Quality.PLAYBACK_OPTIONS.map { it.label }.toTypedArray()
        android.app.AlertDialog.Builder(this)
            .setTitle("选择播放音质")
            .setItems(options) { _, which ->
                currentQuality = Quality.PLAYBACK_OPTIONS[which]
                PlayerController.saveDefaultQuality(this, currentQuality)
                binding.btnQuality.text = "音质：${currentQuality.label}"
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