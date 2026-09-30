package cn.com.dcsgo.mihx.core.skin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 网易云式用户导入皮肤 JSON 的解析回归（2026-09-30）。
 *
 * 这是 zip 包里 `dcsgo.skin.netease.json` 的"能不能装进心乐"的硬约束。
 *
 * 验证三件事：
 *  1. JSON 能通过 SkinParser 校验（不挑 bad-prop / bad-page / bad-section）
 *  2. 解析后的字段 = 设计意图（surface=1 卡片墙, playerEntry=sheet 抽屉型, 3 Tab）
 *  3. tokens 数值与 .netease 描述一致（不是凭空抄）
 */
class NeteaseSkinJsonTest {

    private val json = java.io.File("/tmp/skinpack/dcsgo.skin.netease.json").readText()

    @Test
    fun `netease JSON parses successfully`() {
        val result = SkinParser.parse(json)
        assertTrue(
            "网易云式 JSON 必须解析成功：${(result as? SkinValidation.Invalid)?.issues?.joinToString { it.message }}",
            result is SkinValidation.Valid,
        )
    }

    @Test
    fun `netease skin uses sheet player entry`() {
        val s = (SkinParser.parse(json) as SkinValidation.Valid).skin
        assertEquals(PlayerEntry.SHEET, s.playerEntry)
    }

    @Test
    fun `netease skin has three tabs labeled by net-ease naming`() {
        val s = (SkinParser.parse(json) as SkinValidation.Valid).skin
        assertEquals(3, s.shell.bottomBar.size)
        assertEquals(listOf("我的", "发现", "云村"), s.shell.bottomBar.map { it.label })
    }

    @Test
    fun `netease skin surface token is one (card wall style)`() {
        // 仿造网易云 = 卡片墙；与心乐默认 surface=0(零实心块) 互补
        val s = (SkinParser.parse(json) as SkinValidation.Valid).skin
        assertEquals(1f, s.tokens.surface, 0.001f)
    }

    @Test
    fun `netease skin keeps grid template for songList on artist and album segments`() {
        val s = (SkinParser.parse(json) as SkinValidation.Valid).skin
        val library = s.pages.getValue(DefaultSkin.PAGE_LIBRARY)
        val artistSeg = library.sectionsBySegment.getValue("歌手")
        val albumSeg = library.sectionsBySegment.getValue("专辑")
        assertEquals("grid", artistSeg.first { it.part == SkinPartCatalog.SONG_LIST }.props["template"])
        assertEquals("grid", albumSeg.first { it.part == SkinPartCatalog.SONG_LIST }.props["template"])
    }

    @Test
    fun `netease skin launches on library not player`() {
        // 抽屉型骨架:播放页不在底栏 → 启动页必须是 library
        val s = (SkinParser.parse(json) as SkinValidation.Valid).skin
        assertEquals(DefaultSkin.PAGE_LIBRARY, s.startPage)
    }
}
