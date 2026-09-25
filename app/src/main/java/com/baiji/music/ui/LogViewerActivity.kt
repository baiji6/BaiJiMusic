package com.baiji.music.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.baiji.music.databinding.ActivityLogViewerBinding
import com.baiji.music.util.AppLog

/** 应用内日志查看器：可刷新 / 导出 .log / 复制全部 / 分享 / 清空，便于导出诊断信息。 */
class LogViewerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLogViewerBinding

    /** 通过系统文件选择器导出 .log 文件 */
    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            val ok = writeLogTo(uri)
            Toast.makeText(
                this,
                if (ok) "日志已导出：$uri" else "导出失败",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        refreshLog()
        binding.btnRefreshLog.setOnClickListener { refreshLog() }
        binding.btnExportLog.setOnClickListener {
            exportLauncher.launch(AppLog.exportFileName())
        }
        binding.btnClearLog.setOnClickListener {
            AppLog.clear()
            refreshLog()
            Toast.makeText(this, "日志已清空", Toast.LENGTH_SHORT).show()
        }
        binding.btnCopyLog.setOnClickListener {
            val text = AppLog.snapshot().joinToString("\n")
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("applog", text))
            Toast.makeText(this, "已复制 ${text.lines().size} 行日志", Toast.LENGTH_SHORT).show()
        }
        binding.btnShareLog.setOnClickListener { shareLog() }
        binding.btnBack.setOnClickListener { finish() }
    }

    private fun refreshLog() {
        val logs = AppLog.snapshot()
        binding.textLog.text = if (logs.isEmpty()) "（暂无日志）" else logs.joinToString("\n")
        binding.textLogHint.text = "最近日志（共 ${logs.size} 条，新的在前）"
        binding.textLogPath.text = "日志文件：${AppLog.filePath() ?: "（无）"}"
    }

    /** 把完整日志写入用户选择的 uri */
    private fun writeLogTo(uri: Uri): Boolean {
        return try {
            contentResolver.openOutputStream(uri)?.use { out ->
                out.write(AppLog.fullText().toByteArray(Charsets.UTF_8))
            } != null
        } catch (e: Exception) {
            android.util.Log.e("LogViewer", "导出日志失败: ${e.message}", e)
            false
        }
    }

    /** 通过系统分享面板导出 .log 文件 */
    private fun shareLog() {
        try {
            val uri = writeLogToCache()
            if (uri == null) {
                Toast.makeText(this, "生成日志文件失败", Toast.LENGTH_SHORT).show()
                return
            }
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "百记音乐 日志")
                putExtra(Intent.EXTRA_TEXT, "应用日志，请查收。")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(share, "分享日志"))
        } catch (e: Exception) {
            android.util.Log.e("LogViewer", "分享日志失败: ${e.message}", e)
            Toast.makeText(this, "分享失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /** 将日志写入应用缓存区，返回可分享的 content uri */
    private fun writeLogToCache(): Uri? {
        return try {
            val file = java.io.File(cacheDir, AppLog.exportFileName())
            file.writeText(AppLog.fullText(), Charsets.UTF_8)
            androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            android.util.Log.e("LogViewer", "写入缓存日志失败: ${e.message}", e)
            null
        }
    }
}