package cn.com.dcsgo.mihx.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cn.com.dcsgo.mihx.core.common.AppLog
import cn.com.dcsgo.mihx.domain.repository.EmotionFailure
import cn.com.dcsgo.mihx.domain.repository.EmotionFailureReason
import cn.com.dcsgo.mihx.domain.repository.EmotionFailureRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private const val EMOTION_FAILURE_DATASTORE_NAME = "emotion_failures"

val Context.emotionFailureDataStore: DataStore<Preferences> by preferencesDataStore(
    name = EMOTION_FAILURE_DATASTORE_NAME,
)

/**
 * 情绪分析失败记录存储（DataStore JSON，设计同 TimeSlotConfigStore——量级为个别歌曲，
 * 不引入 Room schema 变更）。
 *
 * 单键存 JSONObject：`{"<songId>":{"reason":"TOO_SHORT","failedAt":1690000000000,"attempts":2}}`。
 * 解析容错：单条损坏跳过。
 */
class EmotionFailureStore(
    private val store: DataStore<Preferences>,
) : EmotionFailureRepository {

    constructor(context: Context) : this(
        store = context.applicationContext.emotionFailureDataStore,
    )

    private val failuresKey = stringPreferencesKey("emotion_failures")

    override fun observeFailures(): Flow<Map<Int, EmotionFailure>> = store.data.map { preferences ->
        parse(preferences[failuresKey])
    }

    override suspend fun currentFailures(): Map<Int, EmotionFailure> = observeFailures().first()

    override suspend fun record(songId: Int, reason: EmotionFailureReason) {
        store.edit { preferences ->
            val current = parse(preferences[failuresKey])
            val previous = current[songId]
            val updated = current.toMutableMap().apply {
                put(
                    songId,
                    EmotionFailure(
                        songId = songId,
                        reason = reason,
                        failedAt = System.currentTimeMillis(),
                        attempts = (previous?.attempts ?: 0) + 1,
                    ),
                )
            }
            preferences[failuresKey] = serialize(updated)
        }
    }

    override suspend fun clear(songId: Int) {
        store.edit { preferences ->
            val updated = parse(preferences[failuresKey]).toMutableMap()
            updated.remove(songId)
            preferences[failuresKey] = serialize(updated)
        }
    }

    override suspend fun clearAll(songIds: List<Int>) {
        if (songIds.isEmpty()) return
        store.edit { preferences ->
            val current = parse(preferences[failuresKey])
            if (current.isEmpty()) return@edit
            val removed = current.filterKeys { it in songIds.toSet() }
            if (removed.isEmpty()) return@edit
            val updated = current.toMutableMap().apply { removed.keys.forEach { remove(it) } }
            preferences[failuresKey] = serialize(updated)
        }
    }

    override suspend fun clearForRetry(songIds: List<Int>) {
        // 重试即清记录重新入队；attempts 随下次失败重新累计
        clearAll(songIds)
    }

    // ── JSON 序列化 ──

    private fun serialize(failures: Map<Int, EmotionFailure>): String {
        val obj = JSONObject()
        failures.forEach { (songId, failure) ->
            obj.put(
                songId.toString(),
                JSONObject()
                    .put("reason", failure.reason.name)
                    .put("failedAt", failure.failedAt)
                    .put("attempts", failure.attempts),
            )
        }
        return obj.toString()
    }

    private fun parse(json: String?): Map<Int, EmotionFailure> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(json)
            buildMap {
                for (key in obj.keys()) {
                    runCatching {
                        val entry = obj.getJSONObject(key)
                        put(
                            key.toInt(),
                            EmotionFailure(
                                songId = key.toInt(),
                                reason = EmotionFailureReason.valueOf(entry.getString("reason")),
                                failedAt = entry.getLong("failedAt"),
                                attempts = entry.getInt("attempts"),
                            ),
                        )
                    }.onFailure {
                        AppLog.warning(TAG, "skip corrupted emotion failure entry $key: ${it.message}")
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }

    private companion object {
        const val TAG = "EmotionFailureStore"
    }
}
