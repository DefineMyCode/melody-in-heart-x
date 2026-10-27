package cn.com.dcsgo.mihx.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cn.com.dcsgo.mihx.core.model.EmotionGroup
import cn.com.dcsgo.mihx.core.model.SongEmotion
import cn.com.dcsgo.mihx.data.local.MelodyDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 情绪仓库回归（真实 Room 内存库，不用假 DAO——投影 SQL 本身要被验证）。
 *
 * 覆盖两件事：
 * 1. **P0（2026-10-24）轻量投影**：批量读（getAll/observeAll）不再物化 embedding，
 *    但 embedding 仍随分析入库（未来监督重训 VA 头的原料不能丢），单条整行读仍能拿到。
 * 2. **此前无测试覆盖的语义**：upsert 继承用户校准（重扫不吞校准）、
 *    saveCorrection 的 user-only 行、clearCorrection 的删行规则、curve 编解码往返。
 *
 * 2026-10-25：校准的**坐标不再落库**，只存词条 —— 坐标是派生值，读时按该行
 * 曲线自己的坐标系现算（详见 SongEmotion.effectiveValence）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SongEmotionsRepositoryTest {

    private lateinit var db: MelodyDatabase
    private lateinit var repository: SongEmotionsRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MelodyDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = SongEmotionsRepository(db.melodyDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun emotion(
        songId: Int,
        valence: Float = 0.2f,
        arousal: Float = 0.8f,
        curve: List<Pair<Float, Float>> = listOf(0.1f to 0.2f, 0.3f to 0.4f),
        embedding: FloatArray? = null,
        userValence: Float? = null,
        userArousal: Float? = null,
        userTags: List<String> = emptyList(),
        modelVersion: String = MODEL_VERSION,
        vocalScore: Float? = null,
    ) = SongEmotion(
        songId = songId,
        valence = valence,
        arousal = arousal,
        curve = curve,
        peakSec = 2.5f,
        windowsAnalyzed = curve.size,
        durationSec = 10f,
        modelVersion = modelVersion,
        analyzedAt = 1L,
        embedding = embedding,
        userValence = userValence,
        userArousal = userArousal,
        userTags = userTags,
        vocalScore = vocalScore,
    )

    // ───────────── M-23：用户校准不被重扫覆盖（TOCTOU 修复） ─────────────

    /**
     * 评审 2026-10-27 · M-23：重扫（模型结果不带用户词条）**必须保留**用户校准。
     *
     * 原实现是「读旧行 → 内存里合并 userTags → 整行 upsert」，读与写之间有窗口：
     * 用户恰在那一瞬间保存校准，扫描随后用**陈旧的** userTags 覆写回去 ——
     * 用户亲手标的词条静默消失。修复后合并规则下沉到 SQL 的 CASE 表达式，
     * 一条语句完成，窗口消失。
     */
    @Test
    fun `rescan keeps user correction when upsert carries no user tags`() = runBlocking {
        repository.saveCorrection(songId = 1, tags = listOf("燃"), edited = true)

        // 模拟重扫：模型结果**不带**用户词条
        repository.upsert(emotion(songId = 1, userTags = emptyList()))

        assertEquals(listOf("燃"), repository.get(1)?.userTags)
        assertEquals(true, repository.get(1)?.userTagsEdited)
    }

    /** 反向：本次写入**带**用户词条时，以新值为准（用户主动标记的场景）。 */
    @Test
    fun `upsert with user tags wins over the stored correction`() = runBlocking {
        repository.saveCorrection(songId = 1, tags = listOf("燃"), edited = true)

        repository.upsert(emotion(songId = 1, userTags = listOf("治愈")))

        assertEquals(listOf("治愈"), repository.get(1)?.userTags)
    }

    /** 没有分析行时保存校准 → 插入一条 user-only 行，且不吞掉后续重扫的模型数据。 */
    @Test
    fun `correction on unanalysed song creates user-only row without wiping later analysis`() =
        runBlocking {
            repository.saveCorrection(songId = 42, tags = listOf("静谧"), edited = true)
            assertEquals(listOf("静谧"), repository.get(42)?.userTags)

            // 之后这首歌被分析（模型结果不带用户词条）→ 用户词条仍在，模型数据也写上了
            repository.upsert(emotion(songId = 42, userTags = emptyList()))

            val row = repository.get(42)
            assertEquals(listOf("静谧"), row?.userTags)
            assertTrue((row?.windowsAnalyzed ?: 0) > 0)
        }

    // ───────────── P0：轻量投影与 embedding 契约 ─────────────

    @Test
    fun `getAll omits embedding while the row still stores it`() = runBlocking {
        repository.upsert(emotion(songId = 1, embedding = floatArrayOf(0.5f, -0.25f)))

        val all = repository.getAll()

        assertEquals(1, all.size)
        // 库里确实存了 embedding（整行读能拿到）
        assertNotNull(repository.get(1)?.embedding)
        // 但批量读路径不物化 embedding（读路径只用得到曲线与校准字段）
        assertNull(all.getValue(1).embedding)
    }

    @Test
    fun `observeAll omits embedding`() = runBlocking {
        repository.upsert(emotion(songId = 1, embedding = floatArrayOf(0.5f, -0.25f)))

        val all = repository.observeAll().first()

        assertNull(all.getValue(1).embedding)
    }

    @Test
    fun `get returns full row with decoded embedding`() = runBlocking {
        repository.upsert(emotion(songId = 7, embedding = floatArrayOf(0.5f, -0.25f, 1f, 0f)))

        val row = repository.get(7)

        assertNotNull(row)
        val emb = row!!.embedding
        assertNotNull(emb)
        assertEquals(4, emb!!.size)
        assertEquals(0.5f, emb[0], 1e-6f)
        assertEquals(-0.25f, emb[1], 1e-6f)
    }

    @Test
    fun `upsert still persists embedding for future va head retraining`() = runBlocking {
        repository.upsert(emotion(songId = 12, embedding = floatArrayOf(0.1f, 0.2f)))

        val row = db.melodyDao().songEmotion(12)

        assertNotNull(row)
        assertNotNull(row!!.embeddingB64)
    }

    // ───────────── 校准写回语义（此前无测试） ─────────────

    @Test
    fun `upsert inherits user tags so rescan does not swallow them`() = runBlocking {
        repository.upsert(emotion(songId = 3, embedding = floatArrayOf(1f)))
        repository.saveCorrection(3, listOf("燃", "静谧"), edited = true)

        // 模型升级重扫：新记录不带校准
        repository.upsert(
            emotion(songId = 3, valence = 0.9f, arousal = 0.9f, modelVersion = "yamnet-va-v3")
        )

        val row = repository.get(3)!!
        assertEquals("yamnet-va-v3", row.modelVersion) // 模型数据已更新
        assertEquals(0.9f, row.valence, 1e-6f)
        assertEquals(listOf("燃", "静谧"), row.userTags) // 词条被继承
        assertEquals(true, row.userTagsEdited) // 「是否纠正模型」标记一并继承
        // 坐标不再落库：它是派生值，读时按**该行的坐标系**现算
        assertNull(row.userValence)
        assertNull(row.userArousal)
        // 派生值 = 燃+静谧 在旧坐标系锚点下的均值（旧表 燃(0.30,+0.85) 静谧(0.30,-0.55)）
        assertEquals(0.3f, row.effectiveValence, 1e-6f)
        assertEquals(0.15f, row.effectiveArousal, 1e-6f)
    }

    @Test
    fun `upsert with explicit calibration overrides old one`() = runBlocking {
        repository.upsert(emotion(songId = 4))
        repository.saveCorrection(4, listOf("emo"), edited = true)

        repository.upsert(
            emotion(songId = 4, userTags = listOf("燃"))
        )

        val row = repository.get(4)!!
        assertEquals(listOf("燃"), row.userTags)
        // 坐标不落库，派生值按当前曲线坐标系现算（燃 = RAGE 当前锚点）
        assertNull(row.userValence)
        assertNull(row.userArousal)
        assertEquals(EmotionGroup.RAGE.anchorV, row.effectiveValence, 1e-6f)
        assertEquals(EmotionGroup.RAGE.anchorA, row.effectiveArousal, 1e-6f)
    }

    @Test
    fun `saveCorrection creates user-only row for unanalyzed song`() = runBlocking {
        repository.saveCorrection(9, listOf("治愈"), edited = null)

        val row = repository.get(9)!!

        assertEquals("user-only", row.modelVersion)
        assertTrue(row.curve.isEmpty())
        assertEquals(0, row.windowsAnalyzed)
        assertEquals(listOf("治愈"), row.userTags)
        // 坐标不落库
        assertNull(row.userValence)
        assertNull(row.userArousal)
        // 但派生坐标仍有值：词条 → 当前坐标系锚点均值（治愈 = LOVE）
        assertEquals(EmotionGroup.LOVE.anchorV, row.effectiveValence, 1e-6f)
        assertEquals(EmotionGroup.LOVE.anchorA, row.effectiveArousal, 1e-6f)
    }

    @Test
    fun `saveCorrection keeps model data untouched`() = runBlocking {
        repository.upsert(
            emotion(songId = 5, valence = 0.7f, arousal = -0.3f, curve = listOf(0.4f to 0.5f))
        )

        repository.saveCorrection(5, listOf("emo"), edited = true)

        val row = repository.get(5)!!
        assertEquals(0.7f, row.valence, 1e-6f)
        assertEquals(-0.3f, row.arousal, 1e-6f)
        assertEquals(listOf(0.4f to 0.5f), row.curve)
        assertEquals(MODEL_VERSION, row.modelVersion)
        assertEquals(listOf("emo"), row.userTags)
    }

    @Test
    fun `clearCorrection resets user fields but keeps model data`() = runBlocking {
        repository.upsert(emotion(songId = 6))
        repository.saveCorrection(6, listOf("emo"), edited = false)

        repository.clearCorrection(6)

        val row = repository.get(6)!!
        assertNull(row.userValence)
        assertNull(row.userArousal)
        assertTrue(row.userTags.isEmpty())
        assertNull(row.userTagsEdited) // 恢复自动后无从判断"改没改"，重置为 null
        assertEquals(MODEL_VERSION, row.modelVersion)
        assertEquals(2, row.windowsAnalyzed)
    }

    // ───────────── userTagsEdited：区分「用户纠正模型」与「用户只是采纳」 ─────────────

    @Test
    fun `saveCorrection persists the edited flag`() = runBlocking {
        repository.upsert(emotion(songId = 21))

        repository.saveCorrection(21, listOf("emo"), edited = true)

        assertEquals(true, repository.get(21)!!.userTagsEdited)
    }

    @Test
    fun `saveCorrection persists the not-edited flag`() = runBlocking {
        repository.upsert(emotion(songId = 22))

        repository.saveCorrection(22, listOf("emo"), edited = false)

        assertEquals(false, repository.get(22)!!.userTagsEdited)
    }

    @Test
    fun `user-only row carries no edited flag because there is no model to disagree with`() =
        runBlocking {
            repository.saveCorrection(23, listOf("emo"), edited = null)

            val row = repository.get(23)!!
            assertEquals("user-only", row.modelVersion)
            assertNull(row.userTagsEdited)
        }

    @Test
    fun `clearCorrection deletes user-only row entirely`() = runBlocking {
        repository.saveCorrection(8, listOf("emo"), edited = null)

        repository.clearCorrection(8)

        assertNull(repository.get(8))
    }

    @Test
    fun `curve round-trips through flat json`() = runBlocking {
        val curve = listOf(0.1f to -0.2f, 0.33f to 0.44f, -0.5f to 0.6f)

        repository.upsert(emotion(songId = 11, curve = curve))

        assertEquals(curve, repository.get(11)!!.curve)
    }

    private companion object {
        // 单一真源: 当前坐标系版本由 core/model 的生成物给出(含内容指纹)
        const val MODEL_VERSION = cn.com.dcsgo.mihx.core.model.EmotionGroup.CURRENT_MODEL_VERSION
    }

    /**
     * 批量读必须**保留 vocalScore**（2026-10-25 回归）。
     *
     * 这是一条真实的漏项：轻量投影 SQL 与 [SongEmotionLite] 当时都没带
     * `vocalScore` 列，Room 映射成 null —— 于是
     *  - 曲库「情绪」Tab 的列表行（走 getAll）**不做**人声成分补偿
     *  - 歌曲详情弹窗（走整行读 get）**做**补偿
     * 两条路径给出不同词条，而真机验证只看到列表行那一侧，差点误判成
     * "补偿算法没生效"。
     *
     * 投影漏列是**静默**的：不会编译失败、不会崩，只是那一个字段变 null。
     */
    @Test
    fun `bulk read keeps the vocal score`() = runBlocking {
        repository.upsert(emotion(songId = 1, vocalScore = 0.0031f))

        val all = repository.getAll()
        assertEquals(0.0031f, all.getValue(1).vocalScore!!, 1e-6f)

        val observed = repository.observeAll().first()
        assertEquals(0.0031f, observed.getValue(1).vocalScore!!, 1e-6f)
    }
}
