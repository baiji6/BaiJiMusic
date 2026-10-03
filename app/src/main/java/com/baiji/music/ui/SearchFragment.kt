package com.baiji.music.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.baiji.music.R
import com.baiji.music.data.HistoryStore
import com.baiji.music.databinding.FragmentSearchBinding
import com.baiji.music.databinding.ItemSuggestBinding
import com.baiji.music.network.MusicApi
import com.baiji.music.network.Song
import com.baiji.music.network.Source
import com.baiji.music.player.PlayerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchFragment : Fragment() {

    /** 页面三态：落地（搜索框居中）/ 输入（搜索框置顶 + 搜索建议）/ 结果 */
    private enum class PageState { LANDING, INPUT, RESULT }

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: SongAdapter

    /** 落地页「最近播放」列表适配器 */
    private lateinit var recentAdapter: SongAdapter

    /** 输入态搜索建议适配器 */
    private lateinit var suggestAdapter: SuggestAdapter

    /** 当前搜索音源：qq / netease */
    private var source: String = Source.QQ

    private var pageState = PageState.LANDING

    /** 搜索建议防抖任务 */
    private var suggestJob: Job? = null

    /** 落地页两个分组的折叠状态（最近搜索默认展开，最近播放默认折叠） */
    private var historyExpanded = true
    private var recentExpanded = false

    private val sourcePrefs get() =
        requireContext().getSharedPreferences("search_prefs", Context.MODE_PRIVATE)

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

        // 落地页「最近播放」
        recentAdapter = SongAdapter(
            onClick = { song -> playInline(song) },
            onPlayPause = { song -> onPlayPause(song) },
            onDownload = { song -> onDownload(song) },
            onMore = { song -> onMore(song) },
        )
        binding.recyclerRecent.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerRecent.adapter = recentAdapter

        // 输入态搜索建议
        suggestAdapter = SuggestAdapter { text -> doSearch(text) }
        binding.recyclerSuggest.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerSuggest.adapter = suggestAdapter

        binding.btnSearch.setOnClickListener {
            val kw = binding.inputSearch.text.toString().trim()
            if (kw.isNotEmpty()) doSearch(kw)
        }
        binding.inputSearch.setOnEditorActionListener { _, _, _ ->
            val kw = binding.inputSearch.text.toString().trim()
            if (kw.isNotEmpty()) doSearch(kw)
            true
        }

        // 点击搜索框立即上移到距顶 36dp，并进入输入态
        binding.inputSearch.setOnClickListener { enterInputState() }
        binding.inputSearch.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) enterInputState()
        }
        binding.inputSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (pageState != PageState.INPUT) return
                requestSuggestions(s?.toString()?.trim().orEmpty())
            }
        })

        // 折叠开关
        binding.btnToggleSearchHistory.setOnClickListener {
            historyExpanded = !historyExpanded
            applyHistoryExpanded()
        }
        binding.btnToggleRecentPlay.setOnClickListener {
            recentExpanded = !recentExpanded
            applyRecentExpanded()
        }

        binding.btnClearSearchHistory.setOnClickListener {
            HistoryStore.clearSearch(requireContext())
            refreshSearchHistory()
            Toast.makeText(requireContext(), "搜索历史已清空", Toast.LENGTH_SHORT).show()
        }
        binding.btnClearRecentPlay.setOnClickListener {
            HistoryStore.clearPlay(requireContext())
            refreshRecentPlay()
            Toast.makeText(requireContext(), "播放历史已清空", Toast.LENGTH_SHORT).show()
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
            val mid = PlayerController.currentSong?.mid
            adapter.setPlaying(mid, isPlaying)
            recentAdapter.setPlaying(mid, isPlaying)
        }

        refreshSearchHistory()
        refreshRecentPlay()
        PlayerController.notifyState(requireContext())
    }

    override fun onResume() {
        super.onResume()
        refreshSearchHistory()
        refreshRecentPlay()
        PlayerController.notifyState(requireContext())
    }

    // ================= 三态切换 =================

    /** 输入态：搜索框立即上移到距顶 36dp（12dp 内边距 + 24dp 外边距） */
    private fun enterInputState() {
        if (pageState == PageState.INPUT) return
        pageState = PageState.INPUT
        binding.spacerTop.visibility = View.GONE
        binding.recycler.visibility = View.GONE
        binding.relatedLayout.visibility = View.GONE
        binding.recyclerSuggest.visibility = View.GONE
        binding.landingScroll.visibility = View.VISIBLE

        refreshSearchHistory()
        refreshRecentPlay()

        val kw = binding.inputSearch.text.toString().trim()
        if (kw.isEmpty()) {
            suggestAdapter.submit(emptyList())
            applyInputContent()
        } else {
            requestSuggestions(kw)
        }
    }

    /** 结果态：搜索框置顶，展示相关搜索与歌曲结果 */
    private fun enterResultState() {
        pageState = PageState.RESULT
        suggestJob?.cancel()
        binding.spacerTop.visibility = View.GONE
        binding.landingScroll.visibility = View.GONE
        binding.recyclerSuggest.visibility = View.GONE
        binding.recycler.visibility = View.VISIBLE
    }

    // ================= 搜索建议（输入态） =================

    /** 输入防抖后拉取搜索建议；接口无数据时列表自动隐藏 */
    private fun requestSuggestions(keyword: String) {
        suggestJob?.cancel()
        if (keyword.isEmpty()) {
            suggestAdapter.submit(emptyList())
            applyInputContent()
            return
        }
        suggestJob = lifecycleScope.launch {
            delay(250)
            val src = source
            val list = try {
                withContext(Dispatchers.IO) { MusicApi.suggestions(src, keyword, 12) }
            } catch (e: Exception) {
                emptyList()
            }
            if (pageState != PageState.INPUT) return@launch
            if (binding.inputSearch.text.toString().trim() != keyword) return@launch
            suggestAdapter.submit(list)
            applyInputContent()
        }
    }

    /** 有建议时铺满下拉列表，否则回落到「最近搜索 / 最近播放」 */
    private fun applyInputContent() {
        if (pageState != PageState.INPUT) return
        val hasSuggest = suggestAdapter.itemCount > 0
        binding.recyclerSuggest.visibility = if (hasSuggest) View.VISIBLE else View.GONE
        binding.landingScroll.visibility = if (hasSuggest) View.GONE else View.VISIBLE
    }

    // ================= 搜索 =================

    private fun doSearch(keyword: String) {
        if (!ensureLoggedIn(source)) return
        // 先切结果态，避免 setText 触发建议请求
        enterResultState()
        // 回填搜索框（点击搜索建议或历史标签时自动填入）
        binding.inputSearch.setText(keyword)
        binding.inputSearch.setSelection(keyword.length)
        hideKeyboard()

        HistoryStore.addSearch(requireContext(), keyword)
        binding.relatedLayout.visibility = View.GONE
        binding.progress.visibility = View.VISIBLE

        lifecycleScope.launch {
            val src = source
            // 相关搜索与歌曲搜索并行，互不阻塞
            val relatedDeferred = async(Dispatchers.IO) {
                MusicApi.suggestions(src, keyword)
            }

            val result = try {
                withContext(Dispatchers.IO) {
                    MusicApi.search(src, keyword)
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

            val related = try {
                relatedDeferred.await()
            } catch (e: Exception) {
                emptyList()
            }
            // 接口无建议时，退化为「用结果里的歌手名作为相关搜索」
            showRelated(if (related.isNotEmpty()) related else deriveRelated(result))
        }
    }

    /** 展示相关搜索；点击后自动填入搜索框并再次搜索 */
    private fun showRelated(keywords: List<String>) {
        binding.relatedChips.removeAllViews()
        if (keywords.isEmpty()) {
            binding.relatedLayout.visibility = View.GONE
            return
        }
        keywords.forEach { kw ->
            binding.relatedChips.addView(makeChip(kw) { doSearch(kw) })
        }
        binding.relatedLayout.visibility = View.VISIBLE
    }

    /** 从搜索结果派生相关搜索词（取歌手名，去重后最多 8 个） */
    private fun deriveRelated(songs: List<Song>): List<String> {
        val out = LinkedHashSet<String>()
        for (s in songs) {
            for (name in s.singer.split("/", "、", "&", ",")) {
                val n = name.trim()
                if (n.isNotEmpty()) out.add(n)
            }
            if (out.size >= 8) break
        }
        return out.take(8).toList()
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.inputSearch.windowToken, 0)
        binding.inputSearch.clearFocus()
    }

    /** 校验音源登录状态；未登录时给出提示并返回 false */
    private fun ensureLoggedIn(src: String): Boolean {
        if (MusicApi.isLoggedIn(src)) return true
        if (src == Source.NETEASE) {
            Toast.makeText(
                requireContext(),
                "请先在「设置」页粘贴红源 Cookie 登录",
                Toast.LENGTH_LONG
            ).show()
            startActivity(Intent(requireContext(), MainActivity::class.java))
        } else {
            Toast.makeText(requireContext(), "请先登录绿源后再搜索", Toast.LENGTH_SHORT).show()
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

    // ================= 落地页：最近搜索 / 最近播放 =================

    private fun refreshSearchHistory() {
        if (pageState == PageState.RESULT) return
        val history = HistoryStore.searchHistory(requireContext())
        if (history.isEmpty()) {
            binding.searchHistoryLayout.visibility = View.GONE
            return
        }
        binding.searchHistoryLayout.visibility = View.VISIBLE
        binding.searchHistoryChips.removeAllViews()
        for (kw in history) {
            binding.searchHistoryChips.addView(makeChip(kw) { doSearch(kw) })
        }
        applyHistoryExpanded()
    }

    private fun refreshRecentPlay() {
        if (pageState == PageState.RESULT) return
        val history = HistoryStore.playHistory(requireContext())
        if (history.isEmpty()) {
            binding.recentPlayLayout.visibility = View.GONE
            return
        }
        binding.recentPlayLayout.visibility = View.VISIBLE
        recentAdapter.submit(history)
        applyRecentExpanded(history.size)
    }

    private fun applyHistoryExpanded() {
        binding.searchHistoryChips.visibility = if (historyExpanded) View.VISIBLE else View.GONE
        binding.btnToggleSearchHistory.text =
            (if (historyExpanded) "▾" else "▸") + " 最近搜索"
    }

    private fun applyRecentExpanded(count: Int = recentAdapter.itemCount) {
        binding.recyclerRecent.visibility = if (recentExpanded) View.VISIBLE else View.GONE
        binding.btnToggleRecentPlay.text =
            (if (recentExpanded) "▾" else "▸") + " 最近播放（$count）"
    }

    /** 生成一个可点击的标签（历史标签 / 相关搜索） */
    private fun makeChip(text: String, onClick: () -> Unit): TextView {
        val chip = TextView(requireContext())
        chip.text = text
        chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary))
        chip.setBackgroundResource(R.drawable.bg_chip)
        chip.setPadding(dp(10), dp(4), dp(10), dp(4))
        chip.textSize = 13f
        val lp = ViewGroup.MarginLayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(0, 0, dp(8), dp(4))
        chip.layoutParams = lp
        chip.setOnClickListener { onClick() }
        return chip
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        suggestJob?.cancel()
        PlayerController.onPlayStateChanged = null
        _binding = null
    }

    /** 输入态搜索建议列表：白底整行文本，点击即以其为关键词搜索 */
    private class SuggestAdapter(private val onClick: (String) -> Unit) :
        RecyclerView.Adapter<SuggestAdapter.VH>() {

        private val items = ArrayList<String>()

        fun submit(list: List<String>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemSuggestBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return VH(b)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val text = items[position]
            holder.b.textSuggest.text = text
            holder.b.root.setOnClickListener { onClick(text) }
        }

        class VH(val b: ItemSuggestBinding) : RecyclerView.ViewHolder(b.root)
    }
}