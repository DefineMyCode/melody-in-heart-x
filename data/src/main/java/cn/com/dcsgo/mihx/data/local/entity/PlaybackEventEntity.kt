package cn.com.dcsgo.mihx.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 单次播放会话记录。
 *
 * 每次播放会话在结算（切歌/自然结束/退出）时写入一行，用于按日/周/月聚合：
 * - 今日 / 本周听歌时长：按 [startedAtMs] 所在天聚合 [durationMs]
 * - 本周 / 本月歌曲 TOP 榜：按 [songId] 聚合条数
 */
@Entity(tableName = "playback_events")
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
