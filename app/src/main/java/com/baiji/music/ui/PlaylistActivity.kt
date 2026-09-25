package com.baiji.music.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.baiji.music.App
import com.baiji.music.data.HistoryStore
import com.baiji.music.data.PlaylistStore
import com.baiji.music.databinding.ActivityPlaylistBinding
import com.baiji.music.network.Song
import com.baiji.music.player.PlayerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaylistActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPlaylistBinding
    private var playlistName: String = ""
    private lateinit var adapter: SongAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaylistBinding.inflate(layoutInflater)
        setContentView(binding.root)

        playlistName = intent.getStringExtra("playlist_name") ?: ""
        binding.textPlaylistTitle.text = playlistName

        binding.recyclerPlaylist.layoutManager = LinearLayoutManager(this)
        adapter = SongAdapter(
            onClick = { song -> playInline(song) },
            onPlayPause = { song -> onPlayPause(song) },
            onDownload = { song -> onDownload(song) },
            onMore = { song -> showSongMenu(song) },
        )
        binding.recyclerPlaylist.adapter = adapter
        PlayerController.onPlayStateChanged = { isPlaying ->
            adapter.setPlaying(PlayerController.currentSong?.mid, isPlaying)
        }
        PlayerController.notifyState(this)
        refresh()
    }

    private fun playInline(song: Song) {
        if (!App.api.isLoggedIn()) {
            Toast.makeText(this, "请先登录 QQ 音乐", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            return
        }
        lifecycleScope.launch {
            val url = try {
                withContext(Dispatchers.IO) {
                    App.api.song.getPlayUrl(song.mid, PlayerController.currentQuality)
                }
            } catch (e: Exception) {
                null
            }
            if (url.isNullOrEmpty()) {
                Toast.makeText(this@PlaylistActivity, "获取播放链接失败，请确认已登录", Toast.LENGTH_LONG).show()
            } else {
                PlayerController.play(this@PlaylistActivity, song, url)
                HistoryStore.addPlay(this@PlaylistActivity, song)
                adapter.setPlaying(song.mid, true)
            }
        }
    }

    private fun onPlayPause(song: Song) {
        val current = PlayerController.currentSong
        if (current?.mid == song.mid) {
            PlayerController.toggle(this)
        } else {
            playInline(song)
        }
    }

    private fun refresh() {
        val songs = PlaylistStore.songsOf(this, playlistName)
        adapter.submit(songs)
        binding.textPlaylistCount.text = "${songs.size} 首"
        binding.textEmpty.visibility = if (songs.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        binding.textEmpty.setOnClickListener {
            finish()
        }
    }

    private fun showSongMenu(song: Song) {
        android.app.AlertDialog.Builder(this)
            .setTitle(song.name)
            .setItems(arrayOf("播放", "下载", "从歌单移除")) { _, which ->
                when (which) {
                    0 -> playInline(song)
                    1 -> onDownload(song)
                    2 -> {
                        PlaylistStore.removeSong(this, playlistName, song.mid)
                        Toast.makeText(this, "已从歌单移除", Toast.LENGTH_SHORT).show()
                        refresh()
                    }
                }
            }
            .show()
    }

    private fun onDownload(song: Song) {
        if (!App.api.isLoggedIn()) {
            Toast.makeText(this, "下载前请先登录 QQ 音乐", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            return
        }
        val intent = Intent(this, DownloadActivity::class.java)
        intent.putExtra("song_mid", song.mid)
        intent.putExtra("song_name", song.name)
        intent.putExtra("song_singer", song.singer)
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        PlayerController.onPlayStateChanged = null
    }
}