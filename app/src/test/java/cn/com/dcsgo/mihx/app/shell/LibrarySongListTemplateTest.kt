package cn.com.dcsgo.mihx.app.shell

import cn.com.dcsgo.mihx.core.skin.DefaultSkin
import cn.com.dcsgo.mihx.core.skin.Skin
import cn.com.dcsgo.mihx.core.skin.SkinIssue
import cn.com.dcsgo.mihx.core.skin.SkinParser
import cn.com.dcsgo.mihx.core.skin.SkinValidation
import cn.com.dcsgo.mihx.feature.playlist.SongListTemplate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * L3 模板解析回归（2026-09-29）。
 *
 * 关键不变式:
 *  - 默认骨架解析出的 librarySongListTemplate == DEFAULT(与改造前一致)
 *  - 网格样本骨架解析出 == GRID
 *  - 模板未知时回落 DEFAULT
 *
 * 这条保证"默认行为零变化"——若 DefaultShell 不再是 DEFAULT,
 * 那曲库页的歌手/专辑分段会从行变成网格,而默认骨架的视觉验证会被破坏。
 *
 * 注:不做"内置默认与解析默认逐项对比"测试,那应由 DefaultShellConsistencyTest
 * 覆盖(现有测试)。本测试专注 L3 新增的字段。
 */
class LibrarySongListTemplateTest {

    @Test
    fun `default skeleton resolves to DEFAULT template`() {
        val shell = SkinShellResolver.resolve(DefaultSkin.skin())
        assertEquals(
            "默认骨架必须解析出 DEFAULT 模板,与改造前一致(零视觉变化)",
            SongListTemplate.DEFAULT, shell.librarySongListTemplate,
        )
    }

    @Test
    fun `grid sample skeleton resolves to GRID template`() {
        val gridSkin = DefaultSkin.gridSampleSkin()
        assertEquals(
            "网格样本骨架应解析出 GRID 模板(供真机 A/B 验收用)",
            SongListTemplate.GRID,
            SkinShellResolver.resolve(gridSkin).librarySongListTemplate,
        )
    }

    @Test
    fun `minimalSheet skeleton also defaults to DEFAULT`() {
        // 抽屉型骨架没有 songList part,应回落到 DEFAULT(行为零变化)
        assertEquals(
            SongListTemplate.DEFAULT,
            SkinShellResolver.resolve(DefaultSkin.minimalSheetSkin()).librarySongListTemplate,
        )
    }

    @Test
    fun `unknown template value is rejected by validator`() {
        // L3 fail-closed:非法 template 值由校验器直接拒绝,不进入装配环节。
        // 这是 L3 设计的一部分:模板字段不是"运行时偷偷忽略",而是"导入期严格拒"。
        val rawJson = """
            {
              "schemaVersion": 1,
              "id": "test.bad.template",
              "name": "bad template test",
              "tokens": { "theme": "VERMILION", "dark": false },
              "shell": { "bottomBar": [
                {"id":"t1","label":"x","icon":"library","target":"library"}
              ], "miniPlayer": true },
              "pages": {
                "library": {
                  "header": null,
                  "search": false,
                  "segments": ["歌单"],
                  "defaultSegment": "歌单",
                  "sectionsBySegment": {
                    "歌单": [
                      { "part": "songList", "props": { "source": "all", "template": "circular" } }
                    ]
                  }
                },
                "player": { "header": null, "sections": [] },
                "me": { "fixedOneScreen": true, "header": null, "sections": [] }
              },
              "startPage": "library",
              "playerEntry": "tab"
            }
        """.trimIndent()
        val parsed = SkinParser.parse(rawJson)
        // 关键断言:非法 template 必须被拒
        assert(parsed is SkinValidation.Invalid) {
            "未知 template 'circular' 应被 SkinParser 拒绝,但实际: $parsed"
        }
        val issues = (parsed as SkinValidation.Invalid).issues
        assert(issues.any { it.code == SkinIssue.Code.INVALID_PROP_VALUE && "template" in it.field })
    }
}
