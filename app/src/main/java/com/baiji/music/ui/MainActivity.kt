package com.baiji.music.ui

import android.os.Bundle
import android.os.CountDownTimer
import android.view.MenuItem
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.baiji.music.R
import com.baiji.music.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            switchFragment(SearchFragment())
            binding.bottomNav.selectedItemId = R.id.nav_search
        }

        binding.bottomNav.setOnItemSelectedListener { item: MenuItem ->
            when (item.itemId) {
                R.id.nav_search -> { switchFragment(SearchFragment()); true }
                R.id.nav_my -> { switchFragment(MyFragment()); true }
                R.id.nav_settings -> { switchFragment(SettingsFragment()); true }
                else -> false
            }
        }

        showDisclaimerIfFirstLaunch()
    }

    /**
     * 仅首次启动展示免责声明；
     * 弹窗不可取消（屏蔽返回键与点击外部），需倒计时结束后才允许关闭。
     */
    private fun showDisclaimerIfFirstLaunch() {
        val prefs = getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DISCLAIMER_ACCEPTED, false)) return

        val content = TextView(this).apply {
            setText(R.string.disclaimer)
            textSize = 13f
            setPadding(48, 32, 48, 32)
            setLineSpacing(0f, 1.35f)
        }
        val scroll = ScrollView(this).apply { addView(content) }

        val dialog = AlertDialog.Builder(this)
            .setTitle("免责声明")
            .setView(scroll)
            .setCancelable(false)
            // 空实现，避免点击按钮时弹窗被默认关闭
            .setPositiveButton("请阅读 $READ_SECONDS 秒") { _, _ -> }
            .create()
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()

        val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        button.isEnabled = false

        object : CountDownTimer(READ_SECONDS * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val remain = (millisUntilFinished + 999) / 1000
                button.text = "请阅读 $remain 秒"
            }

            override fun onFinish() {
                button.isEnabled = true
                button.text = "同意并继续"
                button.setOnClickListener {
                    prefs.edit().putBoolean(KEY_DISCLAIMER_ACCEPTED, true).apply()
                    dialog.dismiss()
                }
            }
        }.start()
    }

    private fun switchFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, fragment)
            .commit()
    }

    companion object {
        private const val PREFS = "app_prefs"
        private const val KEY_DISCLAIMER_ACCEPTED = "disclaimer_accepted_v1"
        private const val READ_SECONDS = 60L
    }
}