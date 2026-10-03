package com.baiji.music.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.baiji.music.R
import com.baiji.music.data.HistoryStore
import com.baiji.music.databinding.FragmentSearchBinding
import com.baiji.music.network.MusicApi
import com.baiji.music.network.Song
import com.baiji.music.network.Source
import com.baiji.music.player.PlayerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchFragment : Fragment() {
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: SongAdapter

    /** 当前搜索音源：qq / netease */
    private var source: String = Source.QQ

    private val sourcePrefs get() =
        requireContext().getSharedPreferences("search_prefs", android.content.Context.MODE_PRIVATE)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = SongAdapter(
            onClick = { song -> playInline(song) },
            onPlayPause = { song -> onPlayPause(song) },
            onDownload = { song -> onDownload(song) },
            onMore = { song -> onMore(song) },
        )
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter

        binding.btnSearch.setOnClickListener {
            val kw = binding.inputSearch.text.toString().trim()
            if (kw.isNotEmpty()) doSearch(kw)
        }
        binding.inputSearch.setOnEditorActionListener { _, _, _ ->
            val kw = binding.inputSearch.text.toString().trim()
            if (kw.isNotEmpty()) doSearch(kw)
            true
        }
        binding.btnClearSearchHistory.setOnClickListener {
            HistoryStore.clearSearch(requireContext())
            refreshSearchHistory()
            Toast.makeText(requireContext(), "搜索历史已清空", Toast.LENGTH_SHORT).show()
        }

        // 音源切换（记忆上次选择）
        source = sourcePrefs.getString("source", Source.QQ) ?: Source.QQ
        binding.sourceGroup.check(
            if (source == Source.NETEASE) R.id.radioNetease else R.id.radioQQ
        )
        binding.sourceGroup.setOnCheckedChangeListener { _, checkedId ->
            source = if (checkedId == R.id.radioNetease) Source.NETEASE else Source.QQ
            sourcePrefs.edit().putString("source", source).apply()
        }

        // 同步全局播放状态，刷新每行播放/暂停图标
        PlayerController.onPlayStateChanged = { isPlaying ->
            adapter.setPlaying(PlayerController.currentSong?.mid, isPlaying)
        }
        PlayerController.notifyState(requireContext())

        refreshSearchHistory()
    }

    override fun onResume() {
        super.onResume()
        refreshSearchHistory()
        PlayerController.notifyState(requireContext())
    }

    private fun doSearch(keyword: String) {
        if (!ensureLoggedIn(source)) return
        HistoryStore.addSearch(requireContext(), keyword)
        refreshSearchHistory()
        binding.searchHistoryLayout.visibility = View.GONE
        binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val result = try {
                withContext(Dispatchers.IO) {
                    MusicApi.search(source, keyword)
                }
            } catch (e: Exception) {
                com.baiji.music.util.AppLog.e("Search", "搜索失败 keyword=$keyword", e)
                binding.progress.visibility = View.GONE
                Toast.makeText(requireContext(), "搜索失败：${e.message}", Toast.LENGTH_LONG).show()
                return@launch
            }
            binding.progress.visibility = View.GONE
            adapter.submit(result)
            if (result.isEmpty()) {
                Toast.makeText(requireContext(), "未找到相关歌曲", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** 校验音源登录状态；未登录时给出提示并返回 false */
    private fun ensureLoggedIn(src: String): Boolean {
        if (MusicApi.isLoggedIn(src)) return true
        if (src == Source.NETEASE) {
            Toast.makeText(
                requireContext(),
                "请先在「设置」页粘贴网易云 Cookie 登录",
                Toast.LENGTH_LONG
            ).show()
            startActivity(Intent(requireContext(), MainActivity::class.java))
        } else {
            Toast.makeText(requireContext(), "请先登录 QQ 音乐后再搜索", Toast.LENGTH_SHORT).show()
            startActivity(Intent(requireContext(), LoginActivity::class.java))
        }
        return false
    }

    /** 行内联播放：点击歌曲行直接播放，不跳转播放器页 */
    private fun playInline(song: Song) {
        if (!ensureLoggedIn(song.source)) return
        lifecycleScope.launch {
            val url = try {
                withContext(Dispatchers.IO) {
                    MusicApi.playUrl(
                        song,
                        PlayerController.currentQuality,
                        PlayerController.currentNeteaseQuality,
                    )
                }
            } catch (e: Exception) {
                null
            }
            if (url.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "获取播放链接失败，请确认已登录", Toast.LENGTH_LONG).show()
            } else {
                PlayerController.play(requireContext(), song, url)
                HistoryStore.addPlay(requireContext(), song)
                adapter.setPlaying(song.mid, true)
            }
        }
    }

    /** 每行播放/暂停按钮：当前行正在播放则暂停，否则播放该行 */
    private fun onPlayPause(song: Song) {
        val current = PlayerController.currentSong
        if (current?.mid == song.mid) {
            PlayerController.toggle(requireContext())
        } else {
            playInline(song)
        }
    }

    private fun onDownload(song: Song) {
        if (!ensureLoggedIn(song.source)) return
        val intent = Intent(requireContext(), DownloadActivity::class.java)
        intent.putExtra("song_mid", song.mid)
        intent.putExtra("song_id", song.songId)
        intent.putExtra("song_name", song.name)
        intent.putExtra("song_singer", song.singer)
        intent.putExtra("song_source", song.source)
        startActivity(intent)
    }

    private fun onMore(song: Song) {
        val options = arrayOf("下载", "播放", "收藏到歌单")
        android.app.AlertDialog.Builder(requireContext())
            .setTitle(song.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> onDownload(song)
                    1 -> playInline(song)
                    2 -> onFavorite(song)
                }
            }
            .show()
    }

    private fun onFavorite(song: Song) {
        val names = com.baiji.music.data.PlaylistStore.playlistNames(requireContext())
        val options = names + "＋ 新建歌单"
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("收藏到歌单")
            .setItems(options.toTypedArray()) { _, which ->
                if (which >= names.size) {
                    createPlaylistAndFavorite(song)
                } else {
                    val ok = com.baiji.music.data.PlaylistStore.addSong(requireContext(), names[which], song)
                    Toast.makeText(
                        requireContext(),
                        if (ok) "已收藏到「${names[which]}」" else "该歌曲已在歌单中",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .show()
    }

    private fun createPlaylistAndFavorite(song: Song) {
        val input = android.widget.EditText(requireContext())
        input.hint = "请输入歌单名称"
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("新建歌单")
            .setView(input)
            .setPositiveButton("创建") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(requireContext(), "歌单名不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val created = com.baiji.music.data.PlaylistStore.createPlaylist(requireContext(), name)
                if (!created) {
                    Toast.makeText(requireContext(), "歌单已存在", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                com.baiji.music.data.PlaylistStore.addSong(requireContext(), name, song)
                Toast.makeText(requireContext(), "已创建并收藏到「$name」", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun refreshSearchHistory() {
        val history = HistoryStore.searchHistory(requireContext())
        if (history.isEmpty()) {
            binding.searchHistoryLayout.visibility = View.GONE
            return
        }
        binding.searchHistoryLayout.visibility = View.VISIBLE
        binding.searchHistoryChips.removeAllViews()
        for (kw in history) {
            val chip = TextView(requireContext())
            chip.text = kw
            chip.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.primary))
            chip.setBackgroundResource(R.drawable.bg_chip)
            chip.setPadding(dp(10), dp(4), dp(10), dp(4))
            chip.textSize = 13f
            val lp = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, dp(8), dp(4))
            chip.layoutParams = lp
            chip.setOnClickListener { doSearch(kw) }
            binding.searchHistoryChips.addView(chip)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        PlayerController.onPlayStateChanged = null
        _binding = null
    }
}