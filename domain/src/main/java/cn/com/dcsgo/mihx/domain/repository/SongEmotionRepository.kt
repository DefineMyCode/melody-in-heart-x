package cn.com.dcsgo.mihx.domain.repository

import cn.com.dcsgo.mihx.core.model.SongEmotion
import kotlinx.coroutines.flow.Flow

/**
 * 歌曲情绪仓库接口（域层）.
 * 实现: :data SongEmotionsRepository (Room); 写入方: :player 分析器.
 *
 * M-5（评审 2026-10-15）：同步方法全部改 suspend —— 实现是 Room runBlocking 桥；
 * 现有调用方（元数据页/情绪页/批扫 Worker/配置页统计）均已在挂起上下文，无需改动。
 */
interface SongEmotionRepository {
    suspend fun get(songId: Int): SongEmotion?

    suspend fun getAll(): Map<Int, SongEmotion>

    /**
     * 全量情绪可观察流（写入即重发）：M-2（评审 2026-10-15）
     * PlayerRuntime 情绪词条缓存由此持续订阅，新分析/用户校准即时反哺随心播放判定，
     * 不再依赖启动时一次性快照。
     */
    fun observeAll(): Flow<Map<Int, SongEmotion>>

    /** 逐首完成时间(ms, 升序) — 详情页平均/上一首耗时统计 */
    suspend fun analyzedTimeline(): List<Long>

    /** 已用户校准的歌曲数 */
    suspend fun correctionCount(): Int

    /** 已分析过的 songId -> modelVersion（据此判断是否需按新模型重扫） */
    suspend fun analyzedVersions(): Map<Int, String>

    suspend fun upsert(emotion: SongEmotion)

    /** 用户校准: 只写 user 三字段, 不动模型数据 */
    suspend fun saveCorrection(songId: Int, valence: Float, arousal: Float, tags: List<String>)

    /** 清除用户校准(值列置 NULL), 该歌回落到曲线自动词条 */
    suspend fun clearCorrection(songId: Int)


    suspend fun delete(songId: Int)
}
