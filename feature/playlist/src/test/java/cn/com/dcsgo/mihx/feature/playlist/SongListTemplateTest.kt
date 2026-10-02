package cn.com.dcsgo.mihx.feature.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * L3 行模板化的 enum 单元测试（2026-09-29）。
 *
 * 这些是纯 Kotlin 单测，不依赖任何 Android/Compose/JVM 特性，可直接跑。
 * Compose 集成行为（实际渲染差异）由真机验收覆盖，不在单元测试范围。
 */
class SongListTemplateTest {

    @Test
    fun `default template id matches catalog whitelist`() {
        // 描述里 template 字段的合法值见 SkinPartCatalog.SONG_LIST_TEMPLATES,
        // 这里锁死 DEFAULT/GRID 的 id 与白名单对得上,避免有人改 enum 忘记改白名单。
        assertEquals("default", SongListTemplate.DEFAULT.id)
        assertEquals("grid", SongListTemplate.GRID.id)
    }

    @Test
    fun `fromId returns DEFAULT for unknown values`() {
        // fail-safe:未知值不能崩,必须回落
        assertSame(SongListTemplate.DEFAULT, SongListTemplate.fromId(null))
        assertSame(SongListTemplate.DEFAULT, SongListTemplate.fromId(""))
        assertSame(SongListTemplate.DEFAULT, SongListTemplate.fromId("compact"))
        assertSame(SongListTemplate.DEFAULT, SongListTemplate.fromId("GRID"))  // 大小写敏感,不变 DEFAULT
    }

    @Test
    fun `fromId returns GRID for grid`() {
        assertSame(SongListTemplate.GRID, SongListTemplate.fromId("grid"))
    }

    @Test
    fun `templates have distinct ids`() {
        val ids = SongListTemplate.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `all template ids are non-blank`() {
        SongListTemplate.entries.forEach { assert(it.id.isNotBlank()) }
    }
}
