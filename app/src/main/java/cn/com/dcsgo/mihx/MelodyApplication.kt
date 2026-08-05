package cn.com.dcsgo.mihx

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import cn.com.dcsgo.mihx.core.common.AndroidAppLogger
import cn.com.dcsgo.mihx.core.common.AppLog
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MelodyApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        AppLog.install(AndroidAppLogger(BuildConfig.DEBUG))
        installUncaughtExceptionHandler()
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
