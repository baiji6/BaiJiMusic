package com.baiji.music.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.baiji.music.App
import com.baiji.music.databinding.FragmentSettingsBinding
import com.baiji.music.download.DownloadManager
import com.baiji.music.network.Quality

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
                .setTitle("默认播放音质")
                .setItems(options) { _, which ->
                    val q = Quality.PLAYBACK_OPTIONS[which]
                    com.baiji.music.player.PlayerController.saveDefaultQuality(requireContext(), q)
                    binding.textPlaybackQuality.text = "默认播放音质：${q.label}"
                }
                .show()
        }
        com.baiji.music.player.PlayerController.loadDefaultQuality(requireContext())
        val defaultQ = com.baiji.music.player.PlayerController.currentQuality
        binding.textPlaybackQuality.text = "默认播放音质：${defaultQ.label}"

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
    }

    override fun onResume() {
        super.onResume()
        refreshLogin()
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}