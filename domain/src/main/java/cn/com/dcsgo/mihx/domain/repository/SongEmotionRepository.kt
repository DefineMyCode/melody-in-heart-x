package cn.com.dcsgo.mihx.domain.repository

import cn.com.dcsgo.mihx.core.model.SongEmotion

/**
 * 歌曲情绪仓库接口（域层）.
 * 实现: :data SongEmotionsRepository (Room); 写入方: :player 分析器.
 */
interface SongEmotionRepository {
    fun get(songId: Int): SongEmotion?

    fun getAll(): Map<Int, SongEmotion>

    /** 逐首完成时间(ms, 升序) — 详情页平均/上一首耗时统计 */
    fun analyzedTimeline(): List<Long>

    /** 已用户校准的歌曲数 */
    fun correctionCount(): Int

    /** 已分析过的 songId -> modelVersion（据此判断是否需按新模型重扫） */
    fun analyzedVersions(): Map<Int, String>

    fun upsert(emotion: SongEmotion)

    /** 用户校准: 只写 user 三字段, 不动模型数据 */
    fun saveCorrection(songId: Int, valence: Float, arousal: Float, tags: List<String>)


    fun delete(songId: Int)
}
