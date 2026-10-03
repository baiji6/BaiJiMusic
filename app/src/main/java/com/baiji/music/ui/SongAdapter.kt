package com.baiji.music.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.baiji.music.R
import com.baiji.music.network.Song
import com.baiji.music.databinding.ItemSongBinding
import com.bumptech.glide.Glide

class SongAdapter(
    private val onClick: (Song) -> Unit,
    private val onPlayPause: (Song) -> Unit,
    private val onDownload: (Song) -> Unit,
    private val onMore: (Song) -> Unit,
) : RecyclerView.Adapter<SongAdapter.VH>() {

    private val items = ArrayList<Song>()

    /** 当前正在播放的歌曲 mid（用于高亮与图标切换） */
    var playingMid: String? = null
    var isPlaying: Boolean = false

    fun submit(list: List<Song>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    /** 更新当前播放状态并刷新（供全局播放状态变化时调用） */
    fun setPlaying(mid: String?, playing: Boolean) {
        playingMid = mid
        isPlaying = playing
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class VH(private val b: ItemSongBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(song: Song) {
            b.textTitle.text = song.name
            b.textSinger.text = song.singer.ifEmpty { "未知歌手" }
            if (song.album.isNotEmpty()) {
                b.textSub.text = song.album
            } else {
                b.textSub.text = song.singer.ifEmpty { "未知歌手" }
            }
            b.textDuration.text = formatDuration(song.duration)
            // 封面：优先使用歌曲自带封面（网易云），否则回退到 QQ 专辑封面
            val coverUrl = song.cover.ifEmpty {
                if (song.albumMid.isNotEmpty())
                    "https://y.qq.com/music/photo_new/T002R300x300M000${song.albumMid}.jpg"
                else ""
            }
            if (coverUrl.isNotEmpty()) {
                Glide.with(b.root)
                    .load(coverUrl)
                    .placeholder(androidx.core.content.ContextCompat.getDrawable(b.root.context, android.R.drawable.ic_media_play))
                    .into(b.imageCover)
            } else {
                Glide.with(b.root).clear(b.imageCover)
            }
            // 播放/暂停图标：当前播放且正在播放 → 暂停；否则 → 播放
            val isThisPlaying = song.mid == playingMid && isPlaying
            b.btnPlayPause.setImageResource(
                if (isThisPlaying) R.drawable.ic_pause else R.drawable.ic_play
            )
            b.btnPlayPause.setOnClickListener { onPlayPause(song) }
            b.root.setOnClickListener { onClick(song) }
            b.btnDownload.setOnClickListener { onDownload(song) }
            b.btnMore.setOnClickListener { onMore(song) }
        }
    }

    private fun formatDuration(ms: Long): String {
        val s = ms / 1000
        return "%02d:%02d".format(s / 60, s % 60)
    }
}