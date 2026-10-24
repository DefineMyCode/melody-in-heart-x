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
    /**
     * 单条查询（整行）：**含 embedding**。
     *
     * P0（2026-10-24）：只有这个入口会带回 embedding（约 5.3KB/首），
     * 供将来监督重训 VA 头按需取用；批量读接口一律不带。
     */
    suspend fun get(songId: Int): SongEmotion?

    /**
     * 全量查询（轻量）：**不含 embedding**（[SongEmotion.embedding] 恒为 null）。
     *
     * P0（2026-10-24）：整库 embedding 约 5.3KB/首（base64 文本 + float32 数组），
     * 而批量读的消费方（情绪词条缓存/曲库情绪 Tab/时段统计）只用到曲线与校准字段，
     * 故批量路径走轻量投影，避免整库物化上百 MB。
     */
    suspend fun getAll(): Map<Int, SongEmotion>

    /**
     * 全量情绪可观察流（写入即重发）：M-2（评审 2026-10-15）
     * PlayerRuntime 情绪词条缓存由此持续订阅，新分析/用户校准即时反哺随心播放判定，
     * 不再依赖启动时一次性快照。
     *
     * P0（2026-10-24）：同 [getAll]，**不含 embedding**——批扫每写一首都会触发整表重发，
     * 投影保证每次重发不再携带整库 embedding。
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
