package cn.com.dcsgo.mihx.app.emotion

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import cn.com.dcsgo.mihx.core.common.AppLog
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.core.model.SongEmotion
import cn.com.dcsgo.mihx.domain.repository.PlayerSettingsRepository
import cn.com.dcsgo.mihx.domain.repository.SongEmotionRepository
import cn.com.dcsgo.mihx.domain.repository.SongRepository
import cn.com.dcsgo.mihx.core.model.emotionTagsOf
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 情绪分析详情页一行(拍平的歌+情绪). */
data class EmotionSongRow(
    val song: Song,
    val emotion: SongEmotion?,
    /** 展示词条(用户校准优先, 否则曲线投票) */
    val tags: List<String>,
    val corrected: Boolean,
)

/** 我的页情绪扫描卡/详情页状态. */
data class EmotionScanUiStatus(
    val analyzedCount: Int = 0,
    val totalCount: Int = 0,
    val scanning: Boolean = false,
    val paused: Boolean = false,
    /** 本轮批扫进度快照 */
    val currentSongTitle: String? = null,
    /** 上一首分析耗时 ms(成功记录时间线近似) */
    val lastSongMs: Long = 0L,
    /** 平均分析耗时 ms */
    val avgSongMs: Long = 0L,
    val correctedCount: Int = 0,
    /** 分析失败歌曲（songId → 记录），UI 展示"无法分析"分区与原因（2026-09-04） */
    val failures: Map<Int, cn.com.dcsgo.mihx.domain.repository.EmotionFailure> = emptyMap(),
)

/**
 * 情绪批扫状态机.
 *
 * 全部状态走 Flow 推送(WorkManager workInfo flow + DataStore flow),
 * 不依赖"进页面才查询"——enqueue/RUNNING 状态迁移、暂停/继续、杀进程恢复
 * 都会自动推到所有订阅页面(我的页卡 + 分析详情页共享同一 ViewModel 实例).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EmotionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songRepository: SongRepository,
    private val emotionRepository: SongEmotionRepository,
    private val settingsRepository: PlayerSettingsRepository,
    private val failureRepository: cn.com.dcsgo.mihx.domain.repository.EmotionFailureRepository,
) : ViewModel() {

    private data class ScanSignal(
        val manualRunning: Boolean,
        val progress: Data?,
        val periodicRunning: Boolean,
        val paused: Boolean,
    )

    /** 手动刷新触发器(校准保存等数据变化) */
    private val tick = MutableStateFlow(0L)

    private val wm = WorkManager.getInstance(context)

    private val scanSignal: Flow<ScanSignal> = combine(
        tick,
        wm.getWorkInfosForUniqueWorkFlow(EmotionScanWorker.UNIQUE_MANUAL).map { infos ->
            // 手动任务: 点击即算扫描中(ENQUEUED 也是用户主动发起), 杀进程重启后
            // WorkManager 恢复排队/运行态同样会推到这里; 仅 RUNNING 会让按钮"点了没反应"
            val running = infos.any {
                it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
            }
            val data = infos.lastOrNull { it.state == WorkInfo.State.RUNNING }?.progress
                ?: infos.lastOrNull()?.progress
            running to data
        },
        wm.getWorkInfosForUniqueWorkFlow(EmotionScanScheduler.UNIQUE_NAME)
            .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING } },
        settingsRepository.emotionScanPaused,
    ) { _, manual, periodic, paused -> ScanSignal(manual.first, manual.second, periodic, paused) }

    private val _status = MutableStateFlow(EmotionScanUiStatus())
    val status: StateFlow<EmotionScanUiStatus> = _status.asStateFlow()

    private val _rows = MutableStateFlow<List<EmotionSongRow>>(emptyList())
    val rows: StateFlow<List<EmotionSongRow>> = _rows.asStateFlow()

    /** rows 重建判据指纹: 计数/校准/暂停/刷新变化才全量解析 */
    private var lastRowsKey = ""

    /** 状态流收集 job：由 [observe] 幂等启动，避免冷启动无情绪页面时也做全库读取 */
    private var observeJob: Job? = null

    /**
     * 启动状态流收集（幂等）。
     *
     * 原本在 init 无条件收集：冷启动即使情绪页/卡片没打开，也会立即做
     * buildStatus/buildRows → songRepository.loadSongs() + 情绪表查询，
     * 既增加冷启动开销，又是与 PlayerViewModel 并发 loadSongs 的竞态来源
     * （2026-10-06 播放队列/进度无法恢复）。改为由消费 status/rows 的页面
     * 在可见时调用本方法；进入任一情绪相关页面后即常驻 VM 作用域。
     */
    fun observe() {
        if (observeJob?.isActive == true) return
        AppLog.info("EmotionViewModel", "observe(): start emotion status flow collection")
        observeJob = viewModelScope.launch {
            scanSignal.flatMapLatest { sig ->
                if (sig.manualRunning || sig.periodicRunning) {
                    // 扫描中: 2s 节奏刷计数; workInfo/progress 推送也会即时触发
                    flow {
                        while (true) {
                            emit(buildStatus(sig))
                            delay(2_000)
                        }
                    }
                } else {
                    flowOf(buildStatus(sig))
                }
            }.collect { (s, rowList) ->
                _status.value = s
                if (rowList != null) _rows.value = rowList
            }
        }
    }

    /** 数据变化后主动刷新(校准保存回调). Flow 已推送的不依赖此方法. */
    fun refresh() {
        tick.value = System.nanoTime()
    }

    private suspend fun buildStatus(sig: ScanSignal): Pair<EmotionScanUiStatus, List<EmotionSongRow>?> {
        // 仓库读是 runBlocking(IO) 桥, 主线程直调会卡 UI — 整体搬到 Default 线程
        val (total, versions, timeline, correctedCount, failures) = withContext(Dispatchers.Default) {
            val total = runCatching { songRepository.countSongs() }.getOrDefault(0)
            val versions = runCatching { emotionRepository.analyzedVersions() }
                .getOrDefault(emptyMap())
            val timeline = runCatching { emotionRepository.analyzedTimeline() }
                .getOrDefault(emptyList())
            val correctedCount = runCatching { emotionRepository.correctionCount() }.getOrDefault(0)
            val failures = runCatching { failureRepository.currentFailures() }
                .getOrDefault(emptyMap())
            RowSources(total, versions, timeline, correctedCount, failures)
        }
        // 相邻成功时间差=单首耗时近似; 间隔>10min 视为批次间空闲, 剔除
        val durations = timeline.zipWithNext { a, b -> b - a }.filter { it in 1..600_000 }
        val status = EmotionScanUiStatus(
            analyzedCount = minOf(versions.size, total),
            totalCount = total,
            scanning = sig.manualRunning || sig.periodicRunning,
            paused = sig.paused,
            currentSongTitle = sig.progress?.getString(EmotionScanWorker.KEY_PROGRESS_CURRENT),
            lastSongMs = durations.lastOrNull() ?: 0L,
            avgSongMs = if (durations.isNotEmpty()) durations.average().toLong() else 0L,
            correctedCount = correctedCount,
            failures = failures,
        )
        // 指纹含情绪内容: versions(map 指纹=逐首 songId→modelVersion, 顺序无关)
        // + timeline(逐首 analyzedAt) + failures(失败记录变化也要推到 UI)
        val contentFingerprint = "${versions.hashCode()}|${timeline.hashCode()}|${failures.hashCode()}"
        val key = "$total|${versions.size}|$correctedCount|${sig.paused}|${tick.value}|$contentFingerprint"
        return if (key != lastRowsKey) {
            val rowList = runCatching { buildRows() }.getOrDefault(null)
            // 只有构建成功才提交新指纹: 失败保留旧 key, 下一个 tick 自动重试
            if (rowList != null) lastRowsKey = key
            status to rowList
        } else {
            status to null
        }
    }

    /** buildStatus 的仓库读取结果(集中在一个 withContext 里减少线程切换). */
    private data class RowSources(
        val total: Int,
        val versions: Map<Int, String>,
        val timeline: List<Long>,
        val correctedCount: Int,
        val failures: Map<Int, cn.com.dcsgo.mihx.domain.repository.EmotionFailure>,
    )

    /** 拍平列表数据(情绪 Tab 用): 已分析的歌 + 展示词条(用户词 > 曲线投票). */
    private suspend fun buildRows(): List<EmotionSongRow> = withContext(Dispatchers.Default) {
        val emotions = emotionRepository.getAll()
        songRepository.loadSongs().mapNotNull { song ->
            val e = emotions[song.id] ?: return@mapNotNull null
            EmotionSongRow(
                song = song,
                emotion = e,
                tags = emotionTagsOf(e),
                corrected = e.userCorrected,
            )
        }
    }

    fun startManualScan() {
        // 先清暂停标志再排队, 防 Worker 启动时读到旧值直接跳过
        viewModelScope.launch {
            runCatching { settingsRepository.setEmotionScanPaused(false) }
            WorkManager.getInstance(context).enqueueUniqueManualScan()
        }
    }

    /**
     * 手动重试失败歌曲（2026-09-04）：清除失败记录后重新排队，
     * Worker 的 pending 过滤会重新纳入这批歌曲。
     */
    fun retryFailedSongs() {
        viewModelScope.launch {
            runCatching {
                failureRepository.clearForRetry(failureRepository.currentFailures().keys.toList())
                settingsRepository.setEmotionScanPaused(false)
                WorkManager.getInstance(context).enqueueUniqueManualScan(
                    ExistingWorkPolicy.APPEND,
                )
            }
        }
    }

    /**
     * 查询歌曲的用户标记词条（2026-09-04 失败标记 UI）：用于区分
     * 失败列表中"已标记/未标记"的歌曲。失败数通常为个位数，逐首查询成本可忽略。
     */
    suspend fun calibratedTagsOf(songId: Int): List<String> =
        withContext(Dispatchers.IO) {
            runCatching { emotionRepository.get(songId)?.userTags.orEmpty() }
                .getOrDefault(emptyList())
        }

    /** 暂停: 协作式, 当前歌曲跑完即停; 排队中的续扫任务直接清掉. */
    fun pauseScan() {
        viewModelScope.launch {
            runCatching { settingsRepository.setEmotionScanPaused(true) }
            runCatching {
                wm.cancelUniqueWork(EmotionScanWorker.UNIQUE_MANUAL)
            }
        }
    }

    fun resumeScan() {
        startManualScan()
    }
}
