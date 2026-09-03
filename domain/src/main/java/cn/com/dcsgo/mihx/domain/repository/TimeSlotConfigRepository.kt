package cn.com.dcsgo.mihx.domain.repository

import cn.com.dcsgo.mihx.core.model.TimeSlotConfig
import kotlinx.coroutines.flow.Flow

/**
 * 情境化随心播放的时段配置仓库（域层接口）。
 * 实现：:data TimeSlotConfigStore（DataStore JSON）。
 */
interface TimeSlotConfigRepository {
    fun observeConfigs(): Flow<List<TimeSlotConfig>>

    suspend fun currentConfigs(): List<TimeSlotConfig>

    /** 保存（新增或按 id 更新）；校验失败抛 IllegalArgumentException，消息含冲突时段名 */
    suspend fun save(config: TimeSlotConfig)

    suspend fun delete(id: Long)
}
