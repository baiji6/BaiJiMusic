package com.baiji.music.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.baiji.music.databinding.ActivityDownloadBinding
import com.baiji.music.network.MusicApi
import com.baiji.music.network.Quality
import com.baiji.music.network.Song
import com.baiji.music.network.Source
import com.baiji.music.network.netease.NeteaseQuality
import com.baiji.music.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 下载页：选择音质后，获取所选音质的播放直链，并跳转系统默认浏览器下载。
 * （下载链接 = 所选音质的播放链接）
 * 支持 QQ 音乐与网易云两种音源。
 */
class DownloadActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDownloadBinding
    private var songMid: String = ""
    private var songId: Long = 0L
    private var songName: String = ""
    private var songSinger: String = ""
    private var songSource: String = Source.QQ
    private var qqQuality: Quality = Quality.MP3_128
    private var neQuality: NeteaseQuality = NeteaseQuality.PLAYBACK_DEFAULT
    private var starting = false

    private val isNetease get() = songSource == Source.NETEASE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDownloadBinding.inflate(layoutInflater)
        setContentView(binding.root)

        songMid = intent.getStringExtra("song_mid") ?: ""
        songId = intent.getLongExtra("song_id", 0L)
        songName = intent.getStringExtra("song_name") ?: ""
        songSinger = intent.getStringExtra("song_singer") ?: ""
        songSource = intent.getStringExtra("song_source") ?: Source.QQ

        binding.textTitle.text = songName
        binding.textSinger.text = songSinger
        binding.btnQuality.text = "音质：${currentQualityLabel()}"
        binding.textPath.text = "将使用系统浏览器下载所选音质"

        binding.btnQuality.setOnClickListener {
            showQualityPicker()
        }

        // 移除"更改位置"按钮（改为浏览器下载，由系统管理保存位置）
        binding.btnChangePath.visibility = android.view.View.GONE

        binding.btnDownload.setOnClickListener {
            if (!starting) startBrowserDownload()
        }
    }

    private fun currentQualityLabel(): String =
        if (isNetease) neQuality.label else qqQuality.label

    private fun showQualityPicker() {
        val labels = if (isNetease) {
            NeteaseQuality.DOWNLOAD_OPTIONS.map { it.label }
        } else {
            Quality.DOWNLOAD_OPTIONS.map { it.label }
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("选择下载音质")
            .setItems(labels.toTypedArray()) { _, which ->
                if (isNetease) {
                    neQuality = NeteaseQuality.DOWNLOAD_OPTIONS[which]
                } else {
                    qqQuality = Quality.DOWNLOAD_OPTIONS[which]
                }
                binding.btnQuality.text = "音质：${currentQualityLabel()}"
            }
            .show()
    }

    /** 获取所选音质直链并跳转系统浏览器下载 */
    private fun startBrowserDownload() {
        if (songMid.isEmpty()) return
        if (!MusicApi.isLoggedIn(songSource)) {
            Toast.makeText(this, "请先登录后再下载", Toast.LENGTH_SHORT).show()
            if (!isNetease) startActivity(Intent(this, LoginActivity::class.java))
            return
        }
        starting = true
        binding.btnDownload.isEnabled = false
        binding.progressBar.visibility = android.view.View.VISIBLE
        binding.progressBar.isIndeterminate = true

        val song = Song(songMid, songId, songName, songSinger, "", "", 0, "", songSource)
        lifecycleScope.launch {
            val url = try {
                withContext(Dispatchers.IO) {
                    MusicApi.playUrl(song, qqQuality, neQuality)
                }
            } catch (e: Exception) {
                AppLog.e("Download", "获取下载直链失败 mid=${song.mid}", e)
                null
            }
            withContext(Dispatchers.Main) {
                starting = false
                binding.btnDownload.isEnabled = true
                binding.progressBar.visibility = android.view.View.GONE
                if (url.isNullOrEmpty()) {
                    Toast.makeText(this@DownloadActivity, "获取下载链接失败，请确认已登录或更换音质", Toast.LENGTH_LONG).show()
                    return@withContext
                }
                AppLog.d("Download", "跳转浏览器下载 ${song.name} quality=${currentQualityLabel()}")
                openInBrowser(url)
            }
        }
    }

    /** 用系统默认浏览器打开直链开始下载 */
    private fun openInBrowser(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
            Toast.makeText(this, "已跳转系统浏览器下载：${currentQualityLabel()}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            AppLog.e("Download", "打开浏览器失败: ${e.message}", e)
            Toast.makeText(this, "打开浏览器失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
