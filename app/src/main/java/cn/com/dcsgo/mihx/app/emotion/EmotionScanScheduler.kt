package cn.com.dcsgo.mihx.app.emotion

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import cn.com.dcsgo.mihx.core.common.AppLog
import java.util.concurrent.TimeUnit

/** 情绪批扫调度: 充电+空闲, 每 6 小时一轮, 每轮 ≤40 首, 增量收敛. */
object EmotionScanScheduler {
    private const val TAG = "EmotionScan"
    const val UNIQUE_NAME = "emotion_periodic_scan"

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresCharging(true)
            .setRequiresBatteryNotLow(true)
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()
        val request = PeriodicWorkRequestBuilder<EmotionScanWorker>(
            6, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setInitialDelay(30, TimeUnit.MINUTES)
            .build()
        runCatching {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
            AppLog.info(TAG, "periodic scan scheduled (keep existing)")
        }.onFailure {
            AppLog.warning(TAG, "schedule failed: ${it.message}", it)
        }
    }
}
