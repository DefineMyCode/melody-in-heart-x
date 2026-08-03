package cn.com.dcsgo.mihx

import android.app.Application
import cn.com.dcsgo.mihx.core.common.AndroidAppLogger
import cn.com.dcsgo.mihx.core.common.AppLog
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MelodyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLog.install(AndroidAppLogger(BuildConfig.DEBUG))
    }
}
