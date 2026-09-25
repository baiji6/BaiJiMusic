package com.baiji.music

import android.app.Application
import com.baiji.music.network.QQMusicClient
import com.baiji.music.util.AppLog

class App : Application() {
    companion object {
        lateinit var api: QQMusicClient
            private set
    }

    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        AppLog.i("App", "应用启动")
        api = QQMusicClient(this)
        com.baiji.music.player.PlayerController.loadDefaultQuality(this)
    }
}