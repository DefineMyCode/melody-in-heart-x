package cn.com.dcsgo.mihx.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cn.com.dcsgo.mihx.core.model.TimeSlotConfig
import cn.com.dcsgo.mihx.core.common.AppLog
import cn.com.dcsgo.mihx.domain.playback.MoodSlotResolver
import cn.com.dcsgo.mihx.domain.playback.SlotValidation
import cn.com.dcsgo.mihx.domain.repository.TimeSlotConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private const val TIME_SLOT_CONFIG_DATASTORE_NAME = "mood_time_slot"

val Context.timeSlotConfigDataStore: DataStore<Preferences> by preferencesDataStore(
    name = TIME_SLOT_CONFIG_DATASTORE_NAME,
)

/**
 * 情境化随心播放的时段配置存储（DataStore JSON，设计文档 §3.2）。
 *
 * 配置量级为个位数～十几条，JSON 序列化足够，不引入 Room schema 变更。
 * 单键存 JSONArray：`[{"id":1,"name":"深夜静谧","start":1320,"end":360,"tags":["静谧","禅"]}]`。
 * 解析容错：单条损坏跳过该条（不抛异常拖垮整个列表）。
 */
class TimeSlotConfigStore(
    private val store: DataStore<Preferences>,
    private val resolver: MoodSlotResolver = MoodSlotResolver(),
) : TimeSlotConfigRepository {
    constructor(context: Context) : this(
        store = context.applicationContext.timeSlotConfigDataStore,
    )

    private val configsKey = stringPreferencesKey("time_slot_configs")

    /** 全部时段配置（按开始时间排序） */
    override fun observeConfigs(): Flow<List<TimeSlotConfig>> = store.data.map { preferences ->
        parse(preferences[configsKey])
    }

    override suspend fun currentConfigs(): List<TimeSlotConfig> = observeConfigs().first()

    /** 保存（新增或更新，按 id 判定）；校验失败抛 [IllegalArgumentException]，携带冲突时段名 */
    override suspend fun save(config: TimeSlotConfig) {
        val existing = currentConfigs()
        when (val result = resolver.validate(config, existing)) {
            is SlotValidation.Valid -> store.edit { preferences ->
                val updated = existing.filter { it.id != config.id } + config
                preferences[configsKey] = serialize(updated.sortedBy { it.startMinutes })
            }

            is SlotValidation.Invalid ->
                throw IllegalArgumentException(result.error.name)

            is SlotValidation.Conflict ->
                throw IllegalArgumentException("与「${result.conflicting.name}」时间段重叠")
        }
    }

    override suspend fun delete(id: Long) {
        store.edit { preferences ->
            val updated = parse(preferences[configsKey]).filter { it.id != id }
            preferences[configsKey] = serialize(updated)
        }
    }

    // ── JSON 序列化 ──

    private fun serialize(configs: List<TimeSlotConfig>): String {
        val array = JSONArray()
        configs.forEach { config ->
            val tags = JSONArray()
            config.tags.forEach { tags.put(it) }
            array.put(
                JSONObject()
                    .put("id", config.id)
                    .put("name", config.name)
                    .put("start", config.startMinutes)
                    .put("end", config.endMinutes)
                    .put("tags", tags),
            )
        }
        return array.toString()
    }

    private fun parse(json: String?): List<TimeSlotConfig> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    runCatching {
                        val o = array.getJSONObject(i)
                        val tags = mutableListOf<String>()
                        val tagArray = o.getJSONArray("tags")
                        for (j in 0 until tagArray.length()) {
                            tags.add(tagArray.getString(j))
                        }
                        add(
                            TimeSlotConfig(
                                id = o.getLong("id"),
                                name = o.getString("name"),
                                startMinutes = o.getInt("start"),
                                endMinutes = o.getInt("end"),
                                tags = tags,
                            ),
                        )
                    }.onFailure {
                        AppLog.warning(TAG, "skip corrupted time slot config at #$i: ${it.message}")
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val TAG = "TimeSlotConfigStore"
    }
}
