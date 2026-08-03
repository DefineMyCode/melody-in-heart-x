package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Playlist
import cn.com.dcsgo.mihx.core.model.Song

/**
 * 播放器 UI 状态数据类
 *
 * 使用不可变数据类 + StateFlow 推送，避免跨 Composable 的状态散落。
 * ViewModel 持有唯一的 MutableStateFlow<PlayerUiState>，Screen 只读取。
 */
data class PlayerUiState(
    /** 当前正在播放（或已暂停）的歌曲，null 表示尚未选曲 */
    val currentSong: Song? = null,
    /** 是否正在播放（true = 播放中，false = 暂停/停止） */
    val isPlaying: Boolean = false,
    /** 当前播放位置（毫秒） */
    val currentPositionMs: Long = 0L,
    /** 当前曲目总时长（毫秒） */
    val durationMs: Long = 0L,
    /** 所有歌曲列表（本地导入的） */
    val songs: List<Song> = emptyList(),
    /** 所有歌单列表 */
    val playlists: List<Playlist> = emptyList(),
    /** 错误提示信息，null 表示无错误 */
    val errorMessage: String? = null,
    /** 数据是否正在加载（启动时扫描） */
    val isLoading: Boolean = false,
    /** 是否正在导入文件夹（扫描 + 元数据提取中） */
    val isImporting: Boolean = false,
    /** 导入进度：当前已处理文件数 */
    val importProgress: Int = 0,
    /** 导入进度：文件总数 */
    val importTotal: Int = 0,

    // ── 播放队列 ──

    /** 播放队列 */
    val playQueue: PlayQueue = PlayQueue(),

    // ── 同名歌曲版本 ──

    /** 当前播放歌曲的同名版本列表（按采样率降序），只有一首时为空 */
    val sameNameSongs: List<Song> = emptyList(),

    // ── 无限播放 ──

    /** 是否处于无限随机播放模式 */
    val isInfinitePlay: Boolean = false,
    /** 是否启用全局均匀随机 */
    val globalUniformRandomEnabled: Boolean = true,
    /** 是否启用蓝牙播放监听 */
    val bluetoothPlaybackMonitoringEnabled: Boolean = false,
    /** 是否启用播放通知控制 */
    val playbackNotificationEnabled: Boolean = false,
    /** 无限播放模式中已播放过的歌曲ID集合（用于避免重复选择） */
    val infinitePlayedSongIds: Set<Int> = emptySet(),

    // ── 添加到下一首：播放模式临时切换 ──

    /**
     * 当前通过"添加到下一首"插入的歌曲 ID。
     * 该歌曲播放完毕后，会将播放模式恢复为 [playModeBeforeNext]。
     * null 表示当前没有待恢复的状态。
     */
    val nextPlaySongId: Int? = null,
    /**
     * 执行"添加到下一首"之前的播放模式，用于该歌曲播完后恢复。
     * 仅在 [nextPlaySongId] 不为 null 时有效。
     */
    val playModeBeforeNext: PlayMode? = null,
    /**
     * 无限播放模式下：下一次调用 playNext() 时跳过队列补充逻辑。
     * 在执行"添加到下一首"时设为 true，切到下一首后自动重置为 false。
     * 防止补充队列时把刚加入的"下一首"歌曲挤到后面。
     */
    val skipNextRefill: Boolean = false,
)
