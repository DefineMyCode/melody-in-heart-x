package cn.com.dcsgo.mihx.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 单次播放会话记录。
 *
 * 每次播放会话在结算（切歌/自然结束/退出）时写入一行，用于按日/周/月聚合：
 * - 今日 / 本周听歌时长：按 [startedAtMs] 所在天聚合 [durationMs]
 * - 本周 / 本月歌曲 TOP 榜：按 [songId] 聚合条数
 *
 * 索引 ([startedAtMs], [isEffectivePlay], [songId]) 覆盖全部按时间范围聚合的查询
 * （时长 / 去重歌数 / 逐日时长 / 歌曲榜），其中 TOP 榜查询可完全由索引覆盖。
 *
 * 另有 [songId] 单列索引（评审 2026-10-27 · M-6）：`DELETE FROM playback_events
 * WHERE songId IN (...)`（删歌/去重/文件校验时清理关联数据）用 songId 等值过滤，
 * 而在复合索引里 songId 排第三位、无法作为查找前缀 → 每次清理都是全表扫描。
 * 而本表是纯追加的、从不清理，会随应用生命周期无限增长，扫描成本随之放大。
 */
@Entity(
    tableName = "playback_events",
    indices = [
        Index(value = ["startedAtMs", "isEffectivePlay", "songId"]),
        Index(value = ["songId"]),
    ],
)
data class PlaybackEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val songId: Int,
    /** 会话开始时间（epoch 毫秒） */
    val startedAtMs: Long,
    /** 会话累计播放时长（毫秒） */
    val durationMs: Long,
    /** 是否有效播放（完播率达标或长歌超 5 分钟） */
    val isEffectivePlay: Boolean,
)
