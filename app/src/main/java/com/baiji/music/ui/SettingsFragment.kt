package com.baiji.music.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.baiji.music.App
import com.baiji.music.databinding.FragmentSettingsBinding
import com.baiji.music.download.DownloadManager
import com.baiji.music.network.Quality
import com.baiji.music.network.netease.NeteaseQuality
import com.baiji.music.player.PlayerController

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val pickDir = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            val dir = java.io.File(requireContext().cacheDir, "")
            // 持久化授权
            try {
                requireContext().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {}
            binding.textPath.text = "下载位置：${uri.toString()}"
            // 记录 URI 并在实际下载时解析
            com.baiji.music.download.DownloadLocation.setUri(requireContext(), uri.toString())
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.textPath.text = "下载位置：${DownloadManager.getDownloadDir(requireContext()).absolutePath}"

        binding.btnPickPath.setOnClickListener {
            pickDir.launch(null)
        }

        binding.rowPlaybackQuality.setOnClickListener {
            val options = Quality.PLAYBACK_OPTIONS.map { it.label }.toTypedArray()
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("QQ 默认播放音质")
                .setItems(options) { _, which ->
                    val q = Quality.PLAYBACK_OPTIONS[which]
                    PlayerController.saveDefaultQuality(requireContext(), q)
                    binding.textPlaybackQuality.text = "默认播放音质：${q.label}"
                }
                .show()
        }
        PlayerController.loadDefaultQuality(requireContext())
        binding.textPlaybackQuality.text = "默认播放音质：${PlayerController.currentQuality.label}"

        binding.rowNeteaseQuality.setOnClickListener {
            val options = NeteaseQuality.PLAYBACK_OPTIONS.map { it.label }.toTypedArray()
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("网易云默认播放音质")
                .setItems(options) { _, which ->
                    val q = NeteaseQuality.PLAYBACK_OPTIONS[which]
                    PlayerController.saveNeteaseQuality(requireContext(), q)
                    binding.textNeteaseQuality.text = "网易云默认播放音质：${q.label}"
                }
                .show()
        }
        binding.textNeteaseQuality.text = "网易云默认播放音质：${PlayerController.currentNeteaseQuality.label}"

        binding.btnNeteaseLogin.setOnClickListener { onNeteaseLoginClick() }

        binding.btnLogin.setOnClickListener {
            if (App.api.isLoggedIn()) {
                App.api.logout()
                binding.textLogin.text = "未登录"
                binding.btnLogin.text = "QQ 扫码登录"
            } else {
                startActivity(Intent(requireContext(), LoginActivity::class.java))
            }
        }

        binding.btnViewLog.setOnClickListener {
            startActivity(Intent(requireContext(), LogViewerActivity::class.java))
        }
        refreshLogin()
        refreshNeteaseLogin()
    }

    override fun onResume() {
        super.onResume()
        refreshLogin()
        refreshNeteaseLogin()
    }

    private fun refreshLogin() {
        if (App.api.isLoggedIn()) {
            binding.textLogin.text = "已登录（UID: ${App.api.credential.musicid}）"
            binding.btnLogin.text = "退出登录"
        } else {
            binding.textLogin.text = "未登录"
            binding.btnLogin.text = "QQ 扫码登录"
        }
    }

    private fun refreshNeteaseLogin() {
        if (App.netease.isLoggedIn()) {
            binding.textNeteaseState.text = "已登录（已保存网页版 Cookie）"
            binding.btnNeteaseLogin.text = "更新 / 退出网易云登录"
        } else {
            binding.textNeteaseState.text = "未登录（需粘贴网页版 Cookie）"
            binding.btnNeteaseLogin.text = "网易云 Cookie 登录"
        }
    }

    private fun onNeteaseLoginClick() {
        if (App.netease.isLoggedIn()) {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("网易云登录")
                .setMessage("当前已登录，是否退出登录？")
                .setPositiveButton("更新 Cookie") { _, _ -> showNeteaseCookieDialog() }
                .setNegativeButton("退出登录") { _, _ ->
                    App.netease.logout()
                    refreshNeteaseLogin()
                    Toast.makeText(requireContext(), "已退出网易云登录", Toast.LENGTH_SHORT).show()
                }
                .setNeutralButton("取消", null)
                .show()
        } else {
            showNeteaseCookieDialog()
        }
    }

    private fun showNeteaseCookieDialog() {
        val input = EditText(requireContext()).apply {
            hint = "请粘贴网易云网页版 Cookie（需包含 MUSIC_U）"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE
            minLines = 3
            setPadding(40, 20, 40, 20)
        }
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("网易云 Cookie 登录")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                val cookie = input.text.toString().trim()
                if (!cookie.contains("MUSIC_U=")) {
                    Toast.makeText(requireContext(), "Cookie 无效，需包含 MUSIC_U", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                App.netease.cookie = cookie
                refreshNeteaseLogin()
                Toast.makeText(requireContext(), "网易云登录成功", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}