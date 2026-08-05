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
        installUncaughtExceptionHandler()
    }

    /** 全局未捕获异常兜底：先经 AppLog 记录（含去敏），再交给系统默认处理器崩溃退出 */
    private fun installUncaughtExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                AppLog.error("MelodyApplication", "Uncaught exception on ${thread.name}", throwable)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}
