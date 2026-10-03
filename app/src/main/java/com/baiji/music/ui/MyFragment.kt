package com.baiji.music.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.baiji.music.App
import com.baiji.music.data.HistoryStore
import com.baiji.music.data.PlaylistStore
import com.baiji.music.databinding.FragmentMyBinding
import com.baiji.music.databinding.ItemPlaylistBinding
import com.baiji.music.player.PlayerController
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MyFragment : Fragment() {
    private var _binding: FragmentMyBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: PlaylistAdapter
    private lateinit var historyAdapter: SongAdapter

    /** 「最近播放」是否展开（可折叠） */
    private var historyExpanded = true
    private var historyCount = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerPlaylists.layoutManager = LinearLayoutManager(requireContext())
        adapter = PlaylistAdapter(
            onClick = { name -> openPlaylist(name) },
            onDelete = { name ->
                AlertDialog.Builder(requireContext())
                    .setTitle("删除歌单")
                    .setMessage("确定删除歌单「$name」吗？")
                    .setPositiveButton("删除") { _, _ ->
                        PlaylistStore.deletePlaylist(requireContext(), name)
                        refreshPlaylists()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            },
        )
        binding.recyclerPlaylists.adapter = adapter

        // 最近播放列表
        binding.recyclerHistory.layoutManager = LinearLayoutManager(requireContext())
        historyAdapter = SongAdapter(
            onClick = { song -> playInline(song) },
            onPlayPause = { song -> onPlayPause(song) },
            onDownload = { song -> onDownload(song) },
            onMore = { song -> onFavOrRemove(song) },
        )
        binding.recyclerHistory.adapter = historyAdapter
        PlayerController.onPlayStateChanged = { isPlaying ->
            historyAdapter.setPlaying(PlayerController.currentSong?.mid, isPlaying)
        }

        binding.btnCreatePlaylist.setOnClickListener { createPlaylist() }
        binding.btnToggleHistory.setOnClickListener {
            historyExpanded = !historyExpanded
            applyHistoryExpanded()
        }
        binding.btnClearHistory.setOnClickListener {
            HistoryStore.clearPlay(requireContext())
            refreshHistory()
            Toast.makeText(requireContext(), "播放历史已清空", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
        PlayerController.notifyState(requireContext())
    }

    private fun playInline(song: com.baiji.music.network.Song) {
        if (!com.baiji.music.network.MusicApi.isLoggedIn(song.source)) {
            Toast.makeText(requireContext(), "请先登录后再播放", Toast.LENGTH_SHORT).show()
            if (song.source != com.baiji.music.network.Source.NETEASE) {
                startActivity(Intent(requireContext(), LoginActivity::class.java))
            }
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val url = try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.baiji.music.network.MusicApi.playUrl(
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
                historyAdapter.setPlaying(song.mid, true)
            }
        }
    }

    private fun onPlayPause(song: com.baiji.music.network.Song) {
        val current = PlayerController.currentSong
        if (current?.mid == song.mid) {
            PlayerController.toggle(requireContext())
        } else {
            playInline(song)
        }
    }

    private fun onDownload(song: com.baiji.music.network.Song) {
        if (!com.baiji.music.network.MusicApi.isLoggedIn(song.source)) {
            Toast.makeText(requireContext(), "下载前请先登录", Toast.LENGTH_SHORT).show()
            if (song.source != com.baiji.music.network.Source.NETEASE) {
                startActivity(Intent(requireContext(), LoginActivity::class.java))
            }
            return
        }
        val intent = Intent(requireContext(), DownloadActivity::class.java)
        intent.putExtra("song_mid", song.mid)
        intent.putExtra("song_id", song.songId)
        intent.putExtra("song_name", song.name)
        intent.putExtra("song_singer", song.singer)
        intent.putExtra("song_source", song.source)
        startActivity(intent)
    }

    private fun onFavOrRemove(song: com.baiji.music.network.Song) {
        val options = arrayOf("播放", "下载", "收藏到歌单", "从历史移除")
        android.app.AlertDialog.Builder(requireContext())
            .setTitle(song.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> playInline(song)
                    1 -> onDownload(song)
                    2 -> {
                        val names = PlaylistStore.playlistNames(requireContext())
                        android.app.AlertDialog.Builder(requireContext())
                            .setTitle("收藏到歌单")
                            .setItems((names + "＋ 新建歌单").toTypedArray()) { _, i ->
                                if (i >= names.size) {
                                    createPlaylistThenFav(song)
                                } else {
                                    val ok = PlaylistStore.addSong(requireContext(), names[i], song)
                                    Toast.makeText(
                                        requireContext(),
                                        if (ok) "已收藏到「${names[i]}」" else "该歌曲已在歌单中",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            .show()
                    }
                    3 -> {
                        HistoryStore.clearPlay(requireContext())
                        refreshHistory()
                        Toast.makeText(requireContext(), "已清空播放历史", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun createPlaylistThenFav(song: com.baiji.music.network.Song) {
        val input = android.widget.EditText(requireContext())
        input.hint = "请输入歌单名称"
        AlertDialog.Builder(requireContext())
            .setTitle("新建歌单")
            .setView(input)
            .setPositiveButton("创建") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(requireContext(), "歌单名不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val created = PlaylistStore.createPlaylist(requireContext(), name)
                if (!created) {
                    Toast.makeText(requireContext(), "歌单已存在", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                PlaylistStore.addSong(requireContext(), name, song)
                Toast.makeText(requireContext(), "已创建并收藏到「$name」", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun refresh() {
        val loggedIn = App.api.isLoggedIn()
        if (loggedIn) {
            binding.textState.text = "已登录（UID: ${App.api.credential.musicid}）"
            binding.btnLogin.text = "退出登录"
        } else {
            binding.textState.text = "未登录"
            binding.btnLogin.text = "绿源 扫码登录"
        }
        binding.btnLogin.setOnClickListener {
            if (loggedIn) {
                App.api.logout()
                refresh()
            } else {
                startActivity(Intent(requireContext(), LoginActivity::class.java))
            }
        }
        refreshPlaylists()
        refreshHistory()
    }

    private fun refreshHistory() {
        val history = HistoryStore.playHistory(requireContext())
        historyCount = history.size
        historyAdapter.submit(history)
        binding.textNoHistory.visibility =
            if (history.isEmpty()) View.VISIBLE else View.GONE
        applyHistoryExpanded()
    }

    /** 应用「最近播放」折叠状态并同步标题（带数量与箭头） */
    private fun applyHistoryExpanded() {
        binding.historyContainer.visibility = if (historyExpanded) View.VISIBLE else View.GONE
        binding.btnToggleHistory.text =
            (if (historyExpanded) "▾" else "▸") + " 最近播放（$historyCount）"
    }

    private fun refreshPlaylists() {
        adapter.submit(PlaylistStore.playlistNames(requireContext()))
        binding.textNoPlaylist.visibility =
            if (adapter.songsCount() == 0) View.VISIBLE else View.GONE
    }

    private fun createPlaylist() {
        val input = android.widget.EditText(requireContext())
        input.hint = "请输入歌单名称"
        AlertDialog.Builder(requireContext())
            .setTitle("新建歌单")
            .setView(input)
            .setPositiveButton("创建") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(requireContext(), "歌单名不能为空", Toast.LENGTH_SHORT).show()
                } else if (PlaylistStore.createPlaylist(requireContext(), name)) {
                    Toast.makeText(requireContext(), "歌单「$name」已创建", Toast.LENGTH_SHORT).show()
                    refreshPlaylists()
                } else {
                    Toast.makeText(requireContext(), "歌单已存在", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun openPlaylist(name: String) {
        val intent = Intent(requireContext(), PlaylistActivity::class.java)
        intent.putExtra("playlist_name", name)
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        PlayerController.onPlayStateChanged = null
        _binding = null
    }
}

/** 歌单列表适配器 */
class PlaylistAdapter(
    private val onClick: (String) -> Unit,
    private val onDelete: (String) -> Unit,
) : RecyclerView.Adapter<PlaylistAdapter.VH>() {

    private val items = ArrayList<String>()

    fun submit(list: List<String>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun songsCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemPlaylistBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class VH(private val b: ItemPlaylistBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(name: String) {
            b.textPlaylistName.text = name
            val count = PlaylistStore.songsOf(b.root.context, name).size
            b.textPlaylistCount.text = "$count 首"
            b.root.setOnClickListener { onClick(name) }
            b.btnDeletePlaylist.setOnClickListener { onDelete(name) }
        }
    }
}