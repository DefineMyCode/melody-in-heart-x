package cn.com.dcsgo.mihx.domain.repository

import cn.com.dcsgo.mihx.core.model.ThemeMode
import cn.com.dcsgo.mihx.core.model.ThemeVariant
import cn.com.dcsgo.mihx.core.model.ScreenOrientationMode
import cn.com.dcsgo.mihx.core.model.RandomEnhanceMode
import cn.com.dcsgo.mihx.domain.model.SongSortMode
import kotlinx.coroutines.flow.Flow

/**
 * 播放器设置仓库接口（域层）。
 *
 * M-5（评审 2026-10-15）：移除全部 current*() 同步快照与 setXxxBlocking 写入 ——
 * 旧实现靠 runBlocking(IO) 桥 DataStore/SharedPreferences，迫使启动路径与 UI 点击线程阻塞。
 * 现在统一为 Flow 观察 + suspend 写入：
 * - 读取方要么 collect 对应 Flow，要么在挂起上下文 `first()` 取瞬时值；
 * - Compose 里用 `stateIn/collectAsState`，stateIn 的占位初值只影响首帧。
 */
interface PlayerSettingsRepository {
    val themeMode: Flow<ThemeMode>
    val themeVariant: Flow<ThemeVariant>
    val screenOrientationMode: Flow<ScreenOrientationMode>
    val globalUniformRandomEnabled: Flow<Boolean>
    val bluetoothPlaybackMonitoringEnabled: Flow<Boolean>
    val playbackNotificationEnabled: Flow<Boolean>
    val lyricFontScale: Flow<Float>
    val dailyListeningGoalMinutes: Flow<Int>

    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setThemeVariant(variant: ThemeVariant)
    suspend fun setScreenOrientationMode(mode: ScreenOrientationMode)
    suspend fun setGlobalUniformRandomEnabled(enabled: Boolean)
    suspend fun setBluetoothPlaybackMonitoringEnabled(enabled: Boolean)
    suspend fun setPlaybackNotificationEnabled(enabled: Boolean)
    suspend fun setLyricFontScale(scale: Float)
    suspend fun setDailyListeningGoalMinutes(minutes: Int)

    /** 定时关闭结束时间戳（epoch 毫秒，0 = 未设置） */
    val sleepTimerEndAtMs: Flow<Long>
    /** 定时关闭是否「播完最后一曲」 */
    val sleepTimerPlayLastSong: Flow<Boolean>
    suspend fun setSleepTimerEndAtMs(endAtMs: Long)
    suspend fun setSleepTimerPlayLastSong(enabled: Boolean)

    /** 情绪批扫手动暂停（详情页可暂停/继续） */
    val emotionScanPaused: Flow<Boolean>
    suspend fun setEmotionScanPaused(paused: Boolean)

    /**
     * 情境化随心播放增强总开关（默认关闭）。
     * 开启后随心播放/无限随机在命中时段内只从时段配置的情绪词条歌曲中随机。
     * 兼容旧开关：等价于 [randomEnhanceMode] == TIME_SLOT。
     */
    val moodTimeSlotEnabled: Flow<Boolean>
    suspend fun setMoodTimeSlotEnabled(enabled: Boolean)

    /**
     * 随心播放增强运行模式（2026-10-20 探索播放新增）。
     * 时段模式与探索模式互斥（二者只能启用一个）。
     */
    val randomEnhanceMode: Flow<RandomEnhanceMode>
    suspend fun setRandomEnhanceMode(mode: RandomEnhanceMode)

    /** 本地音乐列表排序方式（默认 IMPORT_ORDER = 导入顺序） */
    val songSortMode: Flow<SongSortMode>
    suspend fun setSongSortMode(mode: SongSortMode)

    /** 本地音乐列表排序方向（true = 升序，默认 true） */
    val songSortAscending: Flow<Boolean>
    suspend fun setSongSortAscending(ascending: Boolean)
}
