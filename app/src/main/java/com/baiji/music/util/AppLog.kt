package com.baiji.music.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 应用内日志记录器：同时输出到 logcat 与内存/文件，供应用内日志查看页读取。
 * 保存最近 [MAX_LINES] 条日志，并增量写入文件便于导出诊断。
 */
object AppLog {
    const val MAX_LINES = 800
    private const val MAX_FILE_BYTES = 512 * 1024

    private val lines = CopyOnWriteArrayList<String>()
    private var file: File? = null
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        // 使用 filesDir 持久化，避免应用数据被系统清理导致重启后日志丢失
        val dir = File(context.filesDir, "applog")
        if (!dir.exists()) dir.mkdirs()
        file = File(dir, "app.log")
        // 启动时把上次已写入文件的日志载入内存，保证重启不清空
        try {
            val f = file
            if (f != null && f.exists()) {
                val existing = f.readLines(Charsets.UTF_8)
                lines.clear()
                existing.forEach { lines.add(it) }
                while (lines.size > MAX_LINES) lines.removeAt(0)
            }
        } catch (_: Exception) {}
    }

    @Synchronized
    fun d(tag: String, msg: String) = push("D", tag, msg)
    @Synchronized
    fun i(tag: String, msg: String) = push("I", tag, msg)
    @Synchronized
    fun w(tag: String, msg: String) = push("W", tag, msg)
    @Synchronized
    fun e(tag: String, msg: String) = push("E", tag, msg)
    @Synchronized
    fun e(tag: String, msg: String, tr: Throwable?) {
        if (tr != null) push("E", tag, msg + "\n" + Log.getStackTraceString(tr))
        else push("E", tag, msg)
    }

    private fun push(level: String, tag: String, msg: String) {
        val stamp = fmt.format(Date())
        val line = "$stamp $level/$tag: $msg"
        lines.add(line)
        while (lines.size > MAX_LINES) lines.removeAt(0)

        // 增量写文件
        try {
            val f = file
            if (f != null) {
                if (f.exists() && f.length() > MAX_FILE_BYTES) f.delete()
                f.appendText(line + "\n")
            }
        } catch (_: Exception) {}

        when (level) {
            "D" -> Log.d(tag, msg)
            "I" -> Log.i(tag, msg)
            "W" -> Log.w(tag, msg)
            else -> Log.e(tag, msg)
        }
    }

    /** 返回最近日志（新的在前） */
    fun snapshot(): List<String> = lines.reversed()

    fun clear() {
        lines.clear()
        try { file?.delete() } catch (_: Exception) {}
    }

    fun filePath(): String? = file?.absolutePath

    private const val TIMESTAMP_FMT = "yyyyMMdd_HHmmss"

    /** 生成导出日志文件名（.log） */
    fun exportFileName(): String {
        return "baiji_music_${java.text.SimpleDateFormat(TIMESTAMP_FMT, Locale.US).format(Date())}.log"
    }

    /** 当前完整日志文本（新的在前） */
    fun fullText(): String = snapshot().joinToString("\n")
}