package cn.com.dcsgo.mihx

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import cn.com.dcsgo.mihx.app.emotion.EmotionScanScheduler
import cn.com.dcsgo.mihx.core.common.AndroidAppLogger
import cn.com.dcsgo.mihx.core.common.AppLog
import cn.com.dcsgo.mihx.core.common.PerformanceTrace
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MelodyApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        // performanceLoggingEnabled = true：release 下普通日志全关，但
        // `PerformanceTrace` 白名单登记的操作仍要输出（评审 2026-10-27 · M-20）。
        AppLog.install(AndroidAppLogger(BuildConfig.DEBUG, performanceLoggingEnabled = true))
        // 评审 2026-10-27 · M-20 / 门禁建议 4：`allow()` 必须有真实调用点，否则白名单
        // API 是死代码。这里在启动时登记**核心播放链路**的操作 —— release 下这些
        // 关键路径仍会产出性能痕迹（用于线上问题定位），其余操作保持静默。
        PerformanceTrace.allow("play_next_command")
        PerformanceTrace.allow("controller_play_queue")
        PerformanceTrace.allow("controller_prepare_queue")
        PerformanceTrace.allow("controller_sync_queue")
        installUncaughtExceptionHandler()
        EmotionScanScheduler.schedule(this)
    }

    /**
     * 自定义 Coil ImageLoader：合理的内存/磁盘缓存上限 + 全局淡入。
     * 实现 [ImageLoaderFactory] 后 Coil 不再走默认初始化器（manifest 已移除
     * CoilInitializer），图片栈延迟到首次加载时才初始化，缩短冷启动路径。
     */
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache(
                MemoryCache.Builder(this)
                    .maxSizePercent(0.2)
                    .build(),
            )
            .diskCache(
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(64L * 1024 * 1024)
                    .build(),
            )
            .crossfade(true)
            .build()
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
