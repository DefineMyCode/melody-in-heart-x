package cn.com.dcsgo.mihx.data.repository

import cn.com.dcsgo.mihx.core.model.SongEmotion
import cn.com.dcsgo.mihx.data.local.dao.MelodyDao
import cn.com.dcsgo.mihx.data.local.entity.SongEmotionEntity
import cn.com.dcsgo.mihx.data.local.entity.SongEmotionLite
import cn.com.dcsgo.mihx.domain.repository.EmotionTableStat
import cn.com.dcsgo.mihx.domain.repository.SongEmotionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap

/**
 * 歌曲情绪仓库: Room song_emotions 表唯一数据源.
 * curveJson 用 JSONArray 编解 [v,a,v,a,...] 扁平数组(零额外依赖).
 *
 * M-5（评审 2026-10-15）：接口改 suspend，直接挂起查询，不再 runBlocking 桥接。
 *
 * P0（2026-10-24）：读路径（[getAll] / [observeAll]）改走**轻量投影**，
 * 不再解码 embedding——整库 embedding 约 5.3KB/首（base64 文本 + float32 数组），
 * 而这两个入口的消费者（情绪词条缓存/曲库情绪 Tab/时段统计）只用得到曲线与校准字段。
 * embedding 仍随分析入库（未来监督重训 VA 头的原料），需要时用 [get] 单条整行读取。
 */
class SongEmotionsRepository(
    private val melodyDao: MelodyDao,
) : SongEmotionRepository {

    /**
     * 已解析曲线缓存：songId -> (analyzedAt, 曲线)。
     *
     * 评审 2026-10-27 · M-1：Room 的失效跟踪器在 `song_emotions` 任意一行变化时
     * **重发整表**。批扫是「一首一首写」，于是 300 首 × 1000 首库 = 30 万次
     * 曲线 JSON 重解析（每次重发都要把整表每行的 `curveJson` 重新 `JSONArray`）。
     *
     * `analyzedAt` 是行的分析时间戳——行没变它就不变，据此判定缓存是否可复用：
     * 未变的行直接复用已解析曲线，**每次重发只有新写入的那一行需要解析**。
     */
    private val parsedCurveCache = ConcurrentHashMap<Int, ParsedCurve>()

    private data class ParsedCurve(
        val analyzedAt: Long,
        val curve: List<Pair<Float, Float>>,
    )

    /**
     * 曲线解析缓存（评审 2026-10-27 · M-1）。
     *
     * 行未变（`analyzedAt` 相同）→ 直接复用已解析曲线；变了才重新 `JSONArray` 解析。
     * 用户校准（[updateSongEmotionCorrection]）不改 `analyzedAt` 也不改曲线，
     * 因此校准后缓存依然有效。
     */
    private fun String.cachedCurve(songId: Int, analyzedAt: Long): List<Pair<Float, Float>> {
        val cached = parsedCurveCache[songId]
        if (cached != null && cached.analyzedAt == analyzedAt) return cached.curve
        val parsed = parseCurve(this)
        parsedCurveCache[songId] = ParsedCurve(analyzedAt, parsed)
        return parsed
    }

    /**
     * 轻量投影 → 域模型（**不含 embedding**，P0 2026-10-24）。
     *
     * embedding 为 null 是刻意的：读路径的消费者（情绪词条缓存/曲库情绪 Tab/时段统计）
     * 都不读 embedding，整库解码会物化上百 MB。需要 embedding 时走整行读 [SongEmotionEntity.toDomain]。
     *
     * 曲线走 [parsedCurveCache]（评审 2026-10-27 · M-1）：行未变则复用已解析结果。
     */
    private fun SongEmotionLite.liteToDomain(): SongEmotion = SongEmotion(
        songId = songId,
        valence = valence,
        arousal = arousal,
        curve = curveJson.cachedCurve(songId, analyzedAt),
        peakSec = peakSec,
        windowsAnalyzed = windowsAnalyzed,
        durationSec = durationSec,
        modelVersion = modelVersion,
        analyzedAt = analyzedAt,
        embedding = null,
        userValence = userValence,
        userArousal = userArousal,
        userTags = parseTags(userTags),
        userTagsEdited = userTagsEdited,
        vocalScore = vocalScore,
    )

    override suspend fun get(songId: Int): SongEmotion? = withContext(Dispatchers.IO) {
        melodyDao.songEmotion(songId)?.toDomain()
    }

    override suspend fun getAll(): Map<Int, SongEmotion> = withContext(Dispatchers.IO) {
        melodyDao.allSongEmotions().associate { it.songId to it.liteToDomain() }
    }

    override suspend fun analyzedVersions(): Map<Int, String> = withContext(Dispatchers.IO) {
        melodyDao.songEmotionVersions().associate { it.songId to it.modelVersion }
    }

    override suspend fun songIdsMissingVocalScore(): Set<Int> = withContext(Dispatchers.IO) {
        melodyDao.songIdsMissingVocalScore().toSet()
    }

    override fun observeAll(): Flow<Map<Int, SongEmotion>> =
        melodyDao.observeAllSongEmotions().map { rows ->
            val result = rows.associate { it.songId to it.liteToDomain() }
            // 清理已删除歌曲的缓存条目，避免无限增长
            if (parsedCurveCache.size > result.size) {
                parsedCurveCache.keys.retainAll(result.keys)
            }
            result
        }

    override suspend fun tableStat(): EmotionTableStat = withContext(Dispatchers.IO) {
        melodyDao.emotionTableStat()
    }

    override suspend fun recentAnalyzedTimestamps(limit: Int): List<Long> = withContext(Dispatchers.IO) {
        melodyDao.emotionRecentAnalyzedTimestamps(limit)
    }

    override suspend fun correctionCount(): Int = withContext(Dispatchers.IO) {
        melodyDao.emotionCorrectionCount()
    }

    override suspend fun upsert(emotion: SongEmotion) = withContext(Dispatchers.IO) {
        // 重扫(模型升级)不吞用户校准: 继承旧记录的**词条**与"是否被纠正"标记。
        // 坐标不继承 —— 它是派生值(词条 → 该曲线坐标系的锚点均值), 读时现算。
        // 继承坐标正是"手动点过期"的根源: 曲线换了坐标系, 存的坐标还停在旧的。
        val old = melodyDao.songEmotion(emotion.songId)
        if (old != null && !old.userTags.isNullOrEmpty() && emotion.userTags.isEmpty()) {
            melodyDao.upsertSongEmotion(
                emotion.toEntity().copy(
                    userTags = old.userTags,
                    // 校准的"是否被纠正"标记一并继承：重扫换了模型，
                    // 但用户的判断仍然成立（词条是用户定的，不是模型定的）
                    userTagsEdited = old.userTagsEdited,
                )
            )
            return@withContext
        }
        melodyDao.upsertSongEmotion(emotion.toEntity())
    }

        /**
     * 保存用户校准（2026-09-04 支持"分析失败歌曲"手动标记）：
     * 已有分析行 → UPDATE user 字段（不动模型数据）；
     * 无分析行（分析失败/未分析）→ INSERT 仅含 user 字段的行
     * （曲线空、modelVersion="user-only"），读侧 emotionTagsOf 以 userTags 优先，
     * 渲染端对空曲线显示词条列表而非曲线图。
     *
     * **只写词条，不写坐标** —— 坐标是派生值，读时按曲线坐标系现算
     * （见 SongEmotion.effectiveValence）。
     */
    override suspend fun saveCorrection(
        songId: Int,
        tags: List<String>,
        edited: Boolean?,
    ) = withContext(Dispatchers.IO) {
        val tagsText = tags.joinToString(",")
        if (melodyDao.songEmotion(songId) != null) {
            melodyDao.updateSongEmotionCorrection(songId, tagsText, edited)
        } else {
            melodyDao.upsertUserOnlyCorrection(
                SongEmotionEntity(
                    songId = songId,
                    valence = 0f,
                    arousal = 0f,
                    curveJson = "[]",
                    peakSec = 0f,
                    windowsAnalyzed = 0,
                    durationSec = 0f,
                    modelVersion = "user-only",
                    analyzedAt = System.currentTimeMillis(),
                    embeddingB64 = null,
                    userValence = null,
                    userArousal = null,
                    userTags = tagsText,
                    userTagsEdited = null,
                ),
            )
        }
    }

    override suspend fun clearCorrection(songId: Int) = withContext(Dispatchers.IO) {
        melodyDao.clearSongEmotionCorrection(songId)
        // user-only 行（分析失败歌曲的手动标记）清空后是全空行，
        // 会被误判为"已分析"（buildRows 以 emotion 非空判定）——直接删行
        val row = melodyDao.songEmotion(songId)
        if (row != null && row.windowsAnalyzed == 0 && row.embeddingB64 == null) {
            melodyDao.deleteSongEmotion(songId)
        }
    }

    override suspend fun delete(songId: Int) = withContext(Dispatchers.IO) {
        melodyDao.deleteSongEmotion(songId)
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

/** 扁平 [v,a,v,a,...] JSON → 逐窗曲线；解析失败降级为空曲线 */
private fun parseCurve(curveJson: String): List<Pair<Float, Float>> = try {
    val arr = JSONArray(curveJson)
    List(arr.length() / 2) { i ->
        arr.getDouble(i * 2).toFloat() to arr.getDouble(i * 2 + 1).toFloat()
    }
} catch (_: Exception) {
    emptyList()
}

/** 逗号拼接的词条串 → 词条列表（过滤空段） */
private fun parseTags(userTags: String?): List<String> =
    userTags?.split(",")?.filter { it.isNotBlank() } ?: emptyList()

/** 整行读（含 embedding）：仅单条查询用 */
private fun SongEmotionEntity.toDomain(): SongEmotion = SongEmotion(
    songId = songId,
    valence = valence,
    arousal = arousal,
    curve = parseCurve(curveJson),
    peakSec = peakSec,
    windowsAnalyzed = windowsAnalyzed,
    durationSec = durationSec,
    modelVersion = modelVersion,
    analyzedAt = analyzedAt,
    embedding = decodeEmbedding(embeddingB64),
    userValence = userValence,
    userArousal = userArousal,
    userTags = parseTags(userTags),
    userTagsEdited = userTagsEdited,
    vocalScore = vocalScore,
)

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
        userTagsEdited = userTagsEdited,
        vocalScore = vocalScore,
    )
}
