package com.baiji.music.download

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File

/**
 * 自定义下载位置（支持 SAF 目录 URI 与本地路径）。
 */
object DownloadLocation {
    private const val TAG = "DownloadLocation"
    private const val PREFS = "download_location"
    private const val KEY_URI = "saf_uri"

    fun setUri(context: Context, uri: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_URI, uri).apply()
    }

    fun getUri(context: Context): Uri? {
        val s = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_URI, null)
        return s?.let { Uri.parse(it) }
    }

    fun clearUri(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_URI).apply()
    }

    /**
     * 将字节写入自定义位置。若设置了 SAF URI 则写入可持久化目录，否则返回 null。
     * 返回实际写入后的 uri 字符串。
     */
    fun write(context: Context, fileName: String, bytes: ByteArray): String? {
        val uri = getUri(context) ?: return null
        return try {
            val doc = android.provider.DocumentsContract.createDocument(
                context.contentResolver, uri, "audio/*", fileName
            ) ?: return null
            context.contentResolver.openOutputStream(doc)?.use { out ->
                out.write(bytes)
            }
            doc.toString()
        } catch (e: Exception) {
            Log.e(TAG, "SAF 写入失败: ${e.message}")
            null
        }
    }
}