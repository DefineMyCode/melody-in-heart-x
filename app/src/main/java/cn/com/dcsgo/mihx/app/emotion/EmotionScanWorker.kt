package cn.com.dcsgo.mihx.app.emotion

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import cn.com.dcsgo.mihx.core.common.AppLog
import cn.com.dcsgo.mihx.data.player.EmotionAnalyzer
import cn.com.dcsgo.mihx.domain.repository.SongEmotionRepository
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * 情绪批扫 Worker: 给曲库中未分析(或模型版本过期)的歌曲补 V/A 曲线.
 *
 * 两种触发:
 *  - 周期(默认): 充电+空闲约束, 每轮 ≤[BATCH_SONGS] 首, 增量收敛.
 *  - 手动(input "manual"=true): 无充电约束, 每轮 ≤[MANUAL_BATCH_SONGS] 首,
 *    一轮没扫完自动续排下一轮(WorkManager 单任务 10 分钟上限兜底).
 * 单首失败跳过不阻断整批.
 */
class EmotionScanWorker(
    @ApplicationContext context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val manual = inputData.getBoolean(KEY_MANUAL, false)
        val ep = EntryPointAccessors.fromApplication(
            applicationContext, EmotionScanEntryPoint::class.java
        )
        if (isPaused()) {
            AppLog.info(TAG, "scan skipped: user paused")
            return Result.success()
        }
        val songs = ep.musicRepository().loadSongs()
        val analyzed = ep.emotionRepository().analyzedVersions()
        val failures = ep.emotionFailureRepository().currentFailures()
        // pending = 未按当前模型分析过、有本地文件、且不是"反复失败"的歌曲。
        // 失败 >=3 次的歌曲不再自动重试——否则每轮扫描都在同一批坏文件上空转，
        // 用户看到的永远是"差几首没分析"却永远扫不完（2026-09-04 失败标记）。
        val retryableFailures = failures.filterValues { it.attempts < MAX_ATTEMPTS }
        val pending = songs.filter {
            analyzed[it.id] != EmotionAnalyzer.MODEL_VERSION &&
                it.uri != null &&
                failures[it.id]?.attempts?.let { it >= MAX_ATTEMPTS } != true
        }
        val failedCount = failures.count { it.value.attempts >= MAX_ATTEMPTS }
        if (pending.isEmpty()) {
            val failureNote = if (failedCount > 0) "，$failedCount 首因反复失败已跳过（见详情页）" else ""
            AppLog.info(TAG, "scan: library up-to-date (${songs.size} songs$failureNote)")
            return Result.success()
        }
        val batch = if (manual) MANUAL_BATCH_SONGS else BATCH_SONGS
        AppLog.info(
            TAG,
            "scan: ${pending.size}/${songs.size} pending, batch=$batch manual=$manual, " +
                "excludedFailures=${failures.size - retryableFailures.size}",
        )
        val analyzer = ep.emotionAnalyzer()
        val failureRepo = ep.emotionFailureRepository()
        var done = 0
        var attempted = 0
        var pausedMidway = false
        for (song in pending.take(batch)) {
            if (isStopped) break
            if (isPaused()) {
                pausedMidway = true
                AppLog.info(TAG, "scan paused at $attempted/$batch")
                break
            }
            val uri = song.uri ?: continue
            attempted++
            setProgress(workDataOf(KEY_PROGRESS_CURRENT to song.title))
            val t0 = System.currentTimeMillis()
            AppLog.info(TAG, "analyzing [$attempted]: ${song.title} (songId=${song.id})")
            val result = runCatching {
                analyzer.analyze(
                    songId = song.id,
                    uri = uri,
                    modelVersion = EmotionAnalyzer.MODEL_VERSION,
                )
            }.getOrElse { cn.com.dcsgo.mihx.domain.repository.EmotionAnalysisResult.Failure(
                cn.com.dcsgo.mihx.domain.repository.EmotionFailureReason.INFERENCE_ERROR,
            ) }
            when (result) {
                is cn.com.dcsgo.mihx.domain.repository.EmotionAnalysisResult.Success -> {
                    ep.emotionRepository().upsert(result.emotion)
                    failureRepo.clear(song.id)
                    done++
                    AppLog.info(
                        TAG,
                        "analyzed [$attempted]: ${song.title} ok in " +
                            "${(System.currentTimeMillis() - t0) / 1000}s, " +
                            "${result.emotion.windowsAnalyzed}w",
                    )
                }
                is cn.com.dcsgo.mihx.domain.repository.EmotionAnalysisResult.Failure -> {
                    failureRepo.record(song.id, result.reason)
                    AppLog.warning(
                        TAG,
                        "analyzed [$attempted]: ${song.title} FAILED(${result.reason.name}) in " +
                            "${(System.currentTimeMillis() - t0) / 1000}s",
                        null,
                    )
                }
            }
        }
        AppLog.info(TAG, "scan batch done: $done/$attempted analyzed")
        // 续排条件: 仍有缺口(含 10 分钟超时被 isStopped 截断的情况).
        // 整批 attempted>0 但 done==0 说明分析器持续故障, 不再续排防死循环.
        // 用户手动暂停(pausedMidway)不续排, 由详情页"继续"按钮重新拉起.
        if (manual && attempted > 0 && done == 0) {
            AppLog.warning(TAG, "manual scan aborted: whole batch failed", null)
            return Result.success()
        }
        if (manual && !pausedMidway && pending.size - done > 0) {
            // APPEND 语义: 同名 unique 任务链, 新批次排在当前(正在跑的)这一轮之后;
            // cancelUniqueWork(emotion_manual_scan) 会取消整条链 — 暂停正依赖这一点.
            WorkManager.getInstance(applicationContext)
                .enqueueUniqueManualScan(ExistingWorkPolicy.APPEND)
        }
        return Result.success()
    }

    /** 用户是否手动暂停批扫(协作式: 当前歌曲跑完即停). */
    private fun isPaused(): Boolean = runCatching {
        EntryPointAccessors.fromApplication(
            applicationContext, EmotionScanEntryPoint::class.java
        ).settingsRepository().currentEmotionScanPaused()
    }.getOrDefault(false)

    companion object {
        private const val TAG = "EmotionScan"
        private const val BATCH_SONGS = 40
        private const val MANUAL_BATCH_SONGS = 300
        const val KEY_MANUAL = "manual"
        const val KEY_PROGRESS_CURRENT = "current"
        const val UNIQUE_MANUAL = "emotion_manual_scan"

        /** 同一首歌累计失败达到该次数后不再自动重试（详情页仍可手动重试单首） */
        const val MAX_ATTEMPTS = 3
    }
}

/** 手动立即扫描: 一次性任务, 不受充电约束; 已在跑则不重复排. */
fun WorkManager.enqueueUniqueManualScan(policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP) {
    val request = OneTimeWorkRequestBuilder<EmotionScanWorker>()
        .setInputData(workDataOf(EmotionScanWorker.KEY_MANUAL to true))
        .build()
    enqueueUniqueWork(
        EmotionScanWorker.UNIQUE_MANUAL,
        policy,
        request,
    )
    AppLog.info("EmotionScan", "manual scan enqueued (policy=$policy)")
}
