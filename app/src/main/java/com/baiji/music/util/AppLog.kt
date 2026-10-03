package com.baiji.music.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 应用内日志记录器：同时输出到 logcat 与内存/文件，供应用内日志查看页读取。
 *
 * 日志分为 6 个档位：TRACE / DEBUG / INFO / WARN / ERROR / FATAL，
 * 每个档位可通过 [setLevelEnabled] 单独开关，关闭后该档位不再记录。
 * 保存最近 [MAX_LINES] 条日志，并增量写入文件便于导出诊断。
 */
object AppLog {
    const val MAX_LINES = 800
    private const val MAX_FILE_BYTES = 512 * 1024

    // ---------- 档位定义 ----------

    /** 最详细的跟踪信息，通常仅用于开发调试 */
    const val TRACE = "TRACE"

    /** 调试信息，用于诊断问题 */
    const val DEBUG = "DEBUG"

    /** 程序正常运行的关键流程信息 */
    const val INFO = "INFO"

    /** 警告，表示潜在问题，但应用仍可运行 */
    const val WARN = "WARN"

    /** 错误，表示某个操作失败，但应用可能仍可运行 */
    const val ERROR = "ERROR"

    /** 致命错误，表示应用即将崩溃或无法继续运行 */
    const val FATAL = "FATAL"

    /** 全部档位（按严重程度由低到高） */
    val LEVELS = listOf(TRACE, DEBUG, INFO, WARN, ERROR, FATAL)

    /** 各档位默认开关：TRACE 仅在开发调试时使用，默认关闭；其余默认开启 */
    private val DEFAULT_ENABLED = mapOf(
        TRACE to false,
        DEBUG to true,
        INFO to true,
        WARN to true,
        ERROR to true,
        FATAL to true,
    )

    private const val PREFS = "log_prefs"
    private const val KEY_PREFIX = "enabled_"

    /** 内存中的一条日志（记录档位，便于按开关过滤） */
    private class Entry(val level: String, val line: String)

    /** 匹配「时间戳 档位/标签」行首，用于从文件中还原档位 */
    private val LINE_RE = Regex("""^\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3} ([A-Z]+)/""")

    private val entries = CopyOnWriteArrayList<Entry>()
    private val enabled = LinkedHashMap<String, Boolean>()
    private var prefs: SharedPreferences? = null
    private var file: File? = null
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        enabled.clear()
        LEVELS.forEach { level ->
            enabled[level] = prefs?.getBoolean(KEY_PREFIX + level, defaultOf(level)) ?: defaultOf(level)
        }
        // 使用 filesDir 持久化，避免应用数据被系统清理导致重启后日志丢失
        val dir = File(context.filesDir, "applog")
        if (!dir.exists()) dir.mkdirs()
        file = File(dir, "app.log")
        // 启动时把上次已写入文件的日志载入内存，保证重启不清空
        try {
            val f = file
            if (f != null && f.exists()) {
                entries.clear()
                var lastLevel = INFO
                f.readLines(Charsets.UTF_8).forEach { line ->
                    parseLevel(line)?.let { lastLevel = it }
                    entries.add(Entry(lastLevel, line))
                }
                trim()
            }
        } catch (_: Exception) {}
    }

    private fun defaultOf(level: String): Boolean = DEFAULT_ENABLED[level] ?: true

    /** 解析日志行的档位；无法识别时返回 null（视为上一条的续行） */
    private fun parseLevel(line: String): String? {
        val lv = LINE_RE.find(line)?.groupValues?.get(1) ?: return null
        return if (LEVELS.contains(lv)) lv else null
    }

    // ---------- 档位开关 ----------

    /** 该档位是否开启 */
    fun isLevelEnabled(level: String): Boolean = enabled[level] ?: true

    /** 开关某个档位（持久化保存）。关闭后不再记录该档位日志，查看/导出也会一并过滤 */
    fun setLevelEnabled(level: String, on: Boolean) {
        enabled[level] = on
        prefs?.edit()?.putBoolean(KEY_PREFIX + level, on)?.apply()
    }

    // ---------- 写日志 ----------

    fun trace(tag: String, msg: String) = push(TRACE, tag, msg)
    fun d(tag: String, msg: String) = push(DEBUG, tag, msg)
    fun i(tag: String, msg: String) = push(INFO, tag, msg)
    fun w(tag: String, msg: String) = push(WARN, tag, msg)

    fun e(tag: String, msg: String) = push(ERROR, tag, msg)

    fun e(tag: String, msg: String, tr: Throwable?) =
        if (tr != null) push(ERROR, tag, msg + "\n" + Log.getStackTraceString(tr)) else push(ERROR, tag, msg)

    fun fatal(tag: String, msg: String) = push(FATAL, tag, msg)

    fun fatal(tag: String, msg: String, tr: Throwable?) =
        if (tr != null) push(FATAL, tag, msg + "\n" + Log.getStackTraceString(tr)) else push(FATAL, tag, msg)

    @Synchronized
    private fun push(level: String, tag: String, msg: String) {
        if (!isLevelEnabled(level)) return

        val line = "${fmt.format(Date())} $level/$tag: $msg"
        entries.add(Entry(level, line))
        trim()

        // 增量写文件
        try {
            val f = file
            if (f != null) {
                if (f.exists() && f.length() > MAX_FILE_BYTES) f.delete()
                f.appendText(line + "\n")
            }
        } catch (_: Exception) {}

        when (level) {
            TRACE -> Log.v(tag, msg)
            DEBUG -> Log.d(tag, msg)
            INFO -> Log.i(tag, msg)
            WARN -> Log.w(tag, msg)
            else -> Log.e(tag, msg)
        }
    }

    private fun trim() {
        while (entries.size > MAX_LINES) entries.removeAt(0)
    }

    // ---------- 读取 / 导出 ----------

    /** 返回最近日志（新的在前），仅包含当前已开启的档位 */
    fun snapshot(): List<String> =
        entries.reversed().filter { isLevelEnabled(it.level) }.map { it.line }

    @Synchronized
    fun clear() {
        entries.clear()
        try { file?.delete() } catch (_: Exception) {}
    }

    fun filePath(): String? = file?.absolutePath

    private const val TIMESTAMP_FMT = "yyyyMMdd_HHmmss"

    /** 生成导出日志文件名（.log） */
    fun exportFileName(): String {
        return "baiji_music_${SimpleDateFormat(TIMESTAMP_FMT, Locale.US).format(Date())}.log"
    }

    /** 当前完整日志文本（新的在前），仅包含当前已开启的档位 */
    fun fullText(): String = snapshot().joinToString("\n")
}