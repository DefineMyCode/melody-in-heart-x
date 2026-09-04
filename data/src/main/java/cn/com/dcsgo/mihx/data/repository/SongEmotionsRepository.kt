package cn.com.dcsgo.mihx.data.repository

import cn.com.dcsgo.mihx.core.model.SongEmotion
import cn.com.dcsgo.mihx.data.local.dao.MelodyDao
import cn.com.dcsgo.mihx.data.local.entity.SongEmotionEntity
import cn.com.dcsgo.mihx.domain.repository.SongEmotionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray

/**
 * 歌曲情绪仓库: Room song_emotions 表唯一数据源.
 * curveJson 用 JSONArray 编解 [v,a,v,a,...] 扁平数组(零额外依赖).
 */
class SongEmotionsRepository(
    private val melodyDao: MelodyDao,
) : SongEmotionRepository {

    override fun get(songId: Int): SongEmotion? = runBlocking(Dispatchers.IO) {
        melodyDao.songEmotion(songId)?.toDomain()
    }

    override fun getAll(): Map<Int, SongEmotion> = runBlocking(Dispatchers.IO) {
        melodyDao.allSongEmotions().associate { it.songId to it.toDomain() }
    }

    override fun analyzedVersions(): Map<Int, String> = runBlocking(Dispatchers.IO) {
        melodyDao.songEmotionVersions().associate { it.songId to it.modelVersion }
    }

    override fun analyzedTimeline(): List<Long> = runBlocking(Dispatchers.IO) {
        melodyDao.emotionAnalyzedTimeline()
    }

    override fun correctionCount(): Int = runBlocking(Dispatchers.IO) {
        melodyDao.emotionCorrectionCount()
    }

    override fun upsert(emotion: SongEmotion) {
        runBlocking(Dispatchers.IO) {
            // 重扫(模型升级)不吞用户校准: 新记录无校准数据时继承旧记录
            if (emotion.userValence == null) {
                val old = melodyDao.songEmotion(emotion.songId)
                if (old != null && old.userValence != null) {
                    melodyDao.upsertSongEmotion(
                        emotion.toEntity().copy(
                            userValence = old.userValence,
                            userArousal = old.userArousal,
                            userTags = old.userTags,
                        )
                    )
                    return@runBlocking
                }
            }
            melodyDao.upsertSongEmotion(emotion.toEntity())
        }
    }

        /**
     * 保存用户校准（2026-09-04 支持"分析失败歌曲"手动标记）：
     * 已有分析行 → UPDATE user 三字段（不动模型数据）；
     * 无分析行（分析失败/未分析）→ INSERT 仅含 user 字段的行
     * （曲线空、modelVersion="user-only"），读侧 emotionTagsOf 以 userTags 优先，
     * 渲染端对空曲线显示词条列表而非曲线图。
     */
    override fun saveCorrection(songId: Int, valence: Float, arousal: Float, tags: List<String>) {
        runBlocking(Dispatchers.IO) {
            val tagsText = tags.joinToString(",")
            if (melodyDao.songEmotion(songId) != null) {
                melodyDao.updateSongEmotionCorrection(songId, valence, arousal, tagsText)
            } else {
                melodyDao.upsertUserOnlyCorrection(
                    SongEmotionEntity(
                        songId = songId,
                        valence = valence,
                        arousal = arousal,
                        curveJson = "[]",
                        peakSec = 0f,
                        windowsAnalyzed = 0,
                        durationSec = 0f,
                        modelVersion = "user-only",
                        analyzedAt = System.currentTimeMillis(),
                        embeddingB64 = null,
                        userValence = valence,
                        userArousal = arousal,
                        userTags = tagsText,
                    ),
                )
            }
        }
    }

    override fun clearCorrection(songId: Int) {
        runBlocking(Dispatchers.IO) {
            melodyDao.clearSongEmotionCorrection(songId)
            // user-only 行（分析失败歌曲的手动标记）清空后是全空行，
            // 会被误判为"已分析"（buildRows 以 emotion 非空判定）——直接删行
            val row = melodyDao.songEmotion(songId)
            if (row != null && row.windowsAnalyzed == 0 && row.embeddingB64 == null) {
                melodyDao.deleteSongEmotion(songId)
            }
        }
    }

    override fun delete(songId: Int) {
        runBlocking(Dispatchers.IO) {
            melodyDao.deleteSongEmotion(songId)
        }
    }
}

private fun encodeEmbedding(emb: FloatArray?): String? {
    if (emb == null) return null
    val buf = java.nio.ByteBuffer.allocate(emb.size * 4)
        .order(java.nio.ByteOrder.LITTLE_ENDIAN)
    emb.forEach { buf.putFloat(it) }
    return java.util.Base64.getEncoder().encodeToString(buf.array())
}

private fun decodeEmbedding(b64: String?): FloatArray? {
    if (b64.isNullOrEmpty()) return null
    return runCatching {
        val bytes = java.util.Base64.getDecoder().decode(b64)
        val buf = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        FloatArray(bytes.size / 4) { buf.float }
    }.getOrNull()
}

private fun SongEmotionEntity.toDomain(): SongEmotion {
    val flat = try {
        val arr = JSONArray(curveJson)
        List(arr.length() / 2) { i ->
            arr.getDouble(i * 2).toFloat() to arr.getDouble(i * 2 + 1).toFloat()
        }
    } catch (_: Exception) {
        emptyList()
    }
    return SongEmotion(
        songId = songId,
        valence = valence,
        arousal = arousal,
        curve = flat,
        peakSec = peakSec,
        windowsAnalyzed = windowsAnalyzed,
        durationSec = durationSec,
        modelVersion = modelVersion,
        analyzedAt = analyzedAt,
        embedding = decodeEmbedding(embeddingB64),
        userValence = userValence,
        userArousal = userArousal,
        userTags = userTags?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
    )
}

private fun SongEmotion.toEntity(): SongEmotionEntity {
    val arr = JSONArray()
    curve.forEach { (v, a) ->
        arr.put(v.toDouble())
        arr.put(a.toDouble())
    }
    return SongEmotionEntity(
        songId = songId,
        valence = valence,
        arousal = arousal,
        curveJson = arr.toString(),
        peakSec = peakSec,
        windowsAnalyzed = windowsAnalyzed,
        durationSec = durationSec,
        modelVersion = modelVersion,
        analyzedAt = analyzedAt,
        embeddingB64 = encodeEmbedding(embedding),
        userValence = userValence,
        userArousal = userArousal,
        userTags = userTags.joinToString(",").ifEmpty { null },
    )
}
