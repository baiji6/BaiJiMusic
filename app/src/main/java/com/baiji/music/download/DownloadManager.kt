package com.baiji.music.download

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.util.Log
import com.baiji.music.network.Quality
import com.baiji.music.network.Song
import com.baiji.music.network.SongApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 下载管理器：支持自定义下载位置与音质选择。
 * 返回值为保存位置描述（本地绝对路径 或 SAF uri 字符串），失败返回 null。
 */
object DownloadManager {
    private const val TAG = "DownloadManager"
    private const val PREFS = "download_prefs"
    private const val KEY_PATH = "download_path"

    fun getDefaultDownloadDir(context: Context): File {
        return context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            ?: context.filesDir
    }

    fun getDownloadDir(context: Context): File {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val path = prefs.getString(KEY_PATH, null)
        if (path != null) {
            val f = File(path)
            if (f.exists() || f.parentFile?.exists() == true) return f
        }
        return getDefaultDownloadDir(context)
    }

    fun setDownloadDir(context: Context, dir: File) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_PATH, dir.absolutePath).apply()
    }

    /**
     * 尝试为指定歌曲获取可用直链。
     * 优先使用请求音质；若该音质需 VIP 返回空直链，则按降级顺序回退到较低音质，
     * 保证下载可用。返回 null 表示完全无可用直链，返回的 ext 为实际可用的扩展名。
     */
    private fun resolveUrl(
        songApi: SongApi,
        song: Song,
        quality: Quality,
    ): Pair<String, Quality>? {
        // 降级顺序：请求音质 → 320 → 192 → 128
        val fallback = listOf(
            quality,
            Quality.MP3_320,
            Quality.AAC_192,
            Quality.MP3_128,
        ).distinct()
        for (q in fallback) {
            val url = try {
                songApi.getPlayUrl(song.mid, q)
            } catch (e: Exception) {
                Log.e(TAG, "获取直链失败 ${q.label}: ${e.message}")
                ""
            }
            if (url.isNotEmpty()) {
                return url to q
            }
        }
        return null
    }

    /**
     * 下载单曲。返回保存位置描述（本地路径或 SAF uri），失败返回 null。
     * 需要登录凭证（调用方保证），音质由参数指定；若指定音质不可用则自动降级。
     */
    suspend fun download(
        context: Context,
        songApi: SongApi,
        song: Song,
        quality: Quality,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): String? = withContext(Dispatchers.IO) {
        try {
            val resolved = resolveUrl(songApi, song, quality)
            if (resolved == null) {
                Log.e(TAG, "无可用直链: ${song.name}")
                return@withContext null
            }
            val url = resolved.first
            val usedQuality = resolved.second
            val dir = getDownloadDir(context)
            if (!dir.exists()) dir.mkdirs()
            val safeName = sanitize(song.name)
            val safeSinger = sanitize(song.singer)
            val fileName = "$safeSinger - $safeName${usedQuality.ext}"

            val request = okhttp3.Request.Builder().url(url).build()
            val response = okhttp3.OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build()
                .newCall(request).execute()
            val body = response.body ?: return@withContext null
            val total = body.contentLength()
            val input = body.byteStream()
            val buf = ByteArray(64 * 1024)
            var downloaded = 0L
            var n: Int

            // 若设置了 SAF 自定义位置，先下载到内存再写 SAF
            val safUri = DownloadLocation.getUri(context)
            if (safUri != null) {
                val bos = java.io.ByteArrayOutputStream()
                try {
                    while (input.read(buf).also { n = it } != -1) {
                        bos.write(buf, 0, n)
                        downloaded += n
                        onProgress(downloaded, total)
                    }
                    input.close()
                } finally {
                    response.close()
                }
                val saved = DownloadLocation.write(context, fileName, bos.toByteArray())
                if (saved == null) {
                    Log.e(TAG, "SAF 写入失败")
                    return@withContext null
                }
                Log.d(TAG, "下载完成(SAF): $saved")
                return@withContext saved
            }

            val target = File(dir, fileName)
            val output = target.outputStream()
            try {
                while (input.read(buf).also { n = it } != -1) {
                    output.write(buf, 0, n)
                    downloaded += n
                    onProgress(downloaded, total)
                }
            } finally {
                output.flush()
                output.close()
                input.close()
                response.close()
            }
            // 通知媒体库
            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), null, null)
            Log.d(TAG, "下载完成: ${target.absolutePath}")
            target.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "下载失败: ${e.message}", e)
            null
        }
    }

    private fun sanitize(s: String): String {
        val cleaned = s.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        return if (cleaned.isEmpty()) "无名" else cleaned
    }
}