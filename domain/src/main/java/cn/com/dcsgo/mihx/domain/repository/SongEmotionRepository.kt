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

    /**
     * 情绪表轻量统计（评审 2026-10-27 · M-3）。
     *
     * 轮询路径用它替代整表读：行数 + 最近一次分析时间足以作为「需要重建列表」的指纹。
     */
    suspend fun tableStat(): EmotionTableStat

    /**
     * 最近 N 条分析完成时间（**降序**）—— 轮询算「上一首耗时 / 近 N 首平均耗时」用。
     *
     * 只取最近若干条而非全表：1000 首库每 2 秒物化一次全表时间戳没有必要。
     */
    suspend fun recentAnalyzedTimestamps(limit: Int): List<Long>

    /** 已用户校准的歌曲数 */
    suspend fun correctionCount(): Int

    /** 已分析过的 songId -> modelVersion（据此判断是否需按新模型重扫） */
    suspend fun analyzedVersions(): Map<Int, String>

    /**
     * 已分析但缺人声分数的 songId（2026-10-25 引入 vocalScore 之前的数据）。
     *
     * 这些行需要补扫一次, 把人声分数补上 —— 它是校正 valence「人声偏置」的关键变量。
     * 补完后该列表自然为空, 不会反复重扫。
     */
    suspend fun songIdsMissingVocalScore(): Set<Int>

    suspend fun upsert(emotion: SongEmotion)

    /**
     * 用户校准: 只写 user 词条, 不动模型数据.
     *
     * **不写坐标** —— 坐标是派生值(词条 → 该曲线坐标系的锚点均值), 读时现算,
     * 见 [SongEmotion.effectiveValence]。存下来的坐标会在曲线换坐标系后过期.
     *
     * @param edited 用户是否不认同模型结论: true=主动纠正模型的词 / false=只是采纳 /
     *              null=无模型输出可比（该歌未分析）。用于把「独立纠正」与
     *              「用户点确认」区分开——后者不是标注, 不能当训练真值。
     */
    suspend fun saveCorrection(
        songId: Int,
        tags: List<String>,
        edited: Boolean?,
    )

    /** 清除用户校准(值列置 NULL), 该歌回落到曲线自动词条 */
    suspend fun clearCorrection(songId: Int)


    suspend fun delete(songId: Int)
}
