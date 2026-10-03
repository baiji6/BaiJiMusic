package com.baiji.music.ui

import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.baiji.music.App
import com.baiji.music.databinding.ActivityLoginBinding
import com.baiji.music.network.LoginApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private var qrsig: String = ""
    private var pollJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRefresh.setOnClickListener { loadQrcode() }
        binding.btnCookieLogin.setOnClickListener { loginByCookie() }
        loadQrcode()
    }

    private fun loginByCookie() {
        val cookie = binding.editCookie.text.toString().trim()
        if (cookie.isEmpty()) {
            Toast.makeText(this, "请先粘贴绿源 Cookie", Toast.LENGTH_SHORT).show()
            return
        }
        binding.textStatus.text = "正在使用 Cookie 登录..."
        lifecycleScope.launch {
            val cred = try {
                withContext(Dispatchers.IO) { App.api.login.loginByCookie(cookie) }
            } catch (t: Throwable) {
                android.util.Log.e("BaiJiLogin", "loginByCookie failed", t)
                binding.textStatus.text = "Cookie 登录失败: ${t.message}"
                Toast.makeText(this@LoginActivity, "Cookie 登录失败: ${t.message}", Toast.LENGTH_LONG).show()
                return@launch
            }
            if (cred.isLoggedIn()) {
                App.api.credential = cred
                App.api.resetSession()
                binding.textStatus.text = "Cookie 登录成功"
                Toast.makeText(this@LoginActivity, "Cookie 登录成功", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                binding.textStatus.text = "Cookie 登录失败，未获取到有效凭证"
                Toast.makeText(this@LoginActivity, "Cookie 登录失败，未获取到有效凭证", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadQrcode() {
        binding.progress.visibility = android.view.View.VISIBLE
        lifecycleScope.launch {
            val qr = try {
                withContext(Dispatchers.IO) { App.api.login.getQrcode() }
            } catch (t: Throwable) {
                // 捕获 Error（如原生库加载失败的 UnsatisfiedLinkError），避免闪退
                android.util.Log.e("BaiJiLogin", "getQrcode failed", t)
                null
            }
            binding.progress.visibility = android.view.View.GONE
            if (qr == null) {
                val hint = if (!isNativeAvailable()) "本机不支持安全组件，无法登录" else "获取二维码失败，请检查网络"
                Toast.makeText(this@LoginActivity, hint, Toast.LENGTH_LONG).show()
                binding.textStatus.text = hint
                return@launch
            }
            qrsig = qr.qrsig
            val bmp = try {
                BitmapFactory.decodeByteArray(qr.image, 0, qr.image.size)
            } catch (t: Throwable) { null }
            if (bmp == null) {
                Toast.makeText(this@LoginActivity, "二维码图片解析失败", Toast.LENGTH_LONG).show()
                return@launch
            }
            binding.imageQrcode.setImageBitmap(bmp)
            binding.textStatus.text = "请使用 QQ 扫码登录"
            startPolling()
        }
    }

    private fun isNativeAvailable(): Boolean {
        return try {
            com.baiji.music.native.SecurityApi.hash33("ping", 0) >= 0L
            true
        } catch (t: Throwable) {
            false
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = lifecycleScope.launch {
            while (true) {
                delay(2000)
                val check = try {
                    withContext(Dispatchers.IO) { App.api.login.checkQrcode(qrsig) }
                } catch (t: Throwable) {
                    continue
                }
                when (check.event) {
                    LoginApi.QrEvent.SCAN -> binding.textStatus.text = "请使用手机 QQ 扫码"
                    LoginApi.QrEvent.CONF -> binding.textStatus.text = "已扫码，请在手机上确认"
                    LoginApi.QrEvent.REFUSE -> {
                        binding.textStatus.text = "已拒绝，请重新扫码"
                        pollJob?.cancel(); return@launch
                    }
                    LoginApi.QrEvent.TIMEOUT -> {
                        binding.textStatus.text = "二维码已过期，请刷新"
                        pollJob?.cancel(); return@launch
                    }
                    LoginApi.QrEvent.DONE -> {
                        binding.textStatus.text = "登录成功"
                        pollJob?.cancel()
                        authorize(check.uin, check.sigx)
                        return@launch
                    }
                    else -> {}
                }
            }
        }
    }

    private fun authorize(uin: String, sigx: String) {
        lifecycleScope.launch {
            val cred = try {
                withContext(Dispatchers.IO) { App.api.login.authorizeQr(uin, sigx) }
            } catch (t: Throwable) {
                android.util.Log.e("BaiJiLogin", "authorizeQr failed", t)
                Toast.makeText(this@LoginActivity, "登录失败: ${t.message}", Toast.LENGTH_LONG).show()
                binding.textStatus.text = "登录失败: ${t.message}"
                return@launch
            }
            if (cred != null && cred.isLoggedIn()) {
                App.api.credential = cred
                App.api.resetSession()
                Toast.makeText(this@LoginActivity, "登录成功", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                binding.textStatus.text = "登录失败，未获取到有效凭证"
                Toast.makeText(this@LoginActivity, "登录失败，未获取到有效凭证", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        pollJob?.cancel()
        super.onDestroy()
    }
}