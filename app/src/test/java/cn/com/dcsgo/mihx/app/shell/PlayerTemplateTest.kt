package cn.com.dcsgo.mihx.app.shell

import cn.com.dcsgo.mihx.core.skin.DefaultSkin
import cn.com.dcsgo.mihx.core.skin.SkinParser
import cn.com.dcsgo.mihx.core.skin.SkinValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L4 播放页形态(2026-09-30):
 *   - 描述侧 PLAYER_TEMPLATES 白名单 = {"classic","vinyl"}
 *   - 装配侧 PlayerTemplate fromId 容错回落 CLASSIC
 *   - 两侧必须同步——参照 L3 SongListTemplateCatalogTest 的同款防漂移对照
 *
 * 同时是 PlayerTemplate 的契约单测:
 *   - 已知 id 解析为对应 enum
 *   - 未知 / null / 空字符串回落 CLASSIC(启动路径 fail-safe)
 */
class PlayerTemplateTest {

    /** 装配侧解析必须与描述侧白名单逐项相等——双源白名单防漂移。 */
    @Test
    fun `assembly ids exactly match catalog whitelist`() {
        val assemblyIds = PlayerTemplate.entries.map { it.id }.toSet()
        val catalogIds = cn.com.dcsgo.mihx.core.skin.SkinPartCatalog.PLAYER_TEMPLATES
        assertEquals(
            "PlayerTemplate 装配侧 id 集合必须 == SkinPartCatalog.PLAYER_TEMPLATES, 否则校验通过的描述会被装配器拦下",
            catalogIds,
            assemblyIds,
        )
    }

    @Test
    fun `classic resolves to CLASSIC`() {
        assertEquals(PlayerTemplate.CLASSIC, PlayerTemplate.fromId("classic"))
    }

    @Test
    fun `vinyl resolves to VINYL`() {
        assertEquals(PlayerTemplate.VINYL, PlayerTemplate.fromId("vinyl"))
    }

    @Test
    fun `unknown id falls back to CLASSIC`() {
        assertEquals(PlayerTemplate.CLASSIC, PlayerTemplate.fromId("foobar"))
        assertEquals(PlayerTemplate.CLASSIC, PlayerTemplate.fromId(null))
        assertEquals(PlayerTemplate.CLASSIC, PlayerTemplate.fromId(""))
    }
}

/**
 * L4: 默认骨架(`DefaultSkin.skin()`)解析出来的 playerTemplate 必须是 CLASSIC
 * —— 默认皮肤必须零变化,与改造前完全一致。
 */
class PlayerTemplateResolutionTest {

    @Test
    fun `default skin resolves playerTemplate to CLASSIC`() {
        val shell = SkinShellResolver.resolve(DefaultSkin.skin())
        assertEquals(
            "默认骨架未声明 player.template,应解析为 CLASSIC 与改造前一致",
            PlayerTemplate.CLASSIC,
            shell.playerTemplate,
        )
    }

    @Test
    fun `minimal sheet skin resolves playerTemplate to CLASSIC`() {
        // P3 抽屉型骨架(playerEntry=sheet)仍走 classic — 抽屉不影响播放页内容形态
        val shell = SkinShellResolver.resolve(DefaultSkin.minimalSheetSkin())
        assertEquals(PlayerTemplate.CLASSIC, shell.playerTemplate)
    }

    @Test
    fun `vinyl sample skin resolves playerTemplate to VINYL`() {
        // 黑胶样本骨架:描述里 player.template = "vinyl"
        val shell = SkinShellResolver.resolve(DefaultSkin.vinylSampleSkin())
        assertEquals(PlayerTemplate.VINYL, shell.playerTemplate)
    }

    @Test
    fun `grid sample skin resolves playerTemplate to CLASSIC`() {
        // 网格样本骨架与播放页无关 — player.template 缺省 = CLASSIC
        val shell = SkinShellResolver.resolve(DefaultSkin.gridSampleSkin())
        assertEquals(PlayerTemplate.CLASSIC, shell.playerTemplate)
    }

    @Test
    fun `DefaultShell shell keeps CLASSIC for boot path`() {
        // 启动路径走 Kotlin 常量(不解析描述),必须 = CLASSIC 与改造前一致
        assertEquals(PlayerTemplate.CLASSIC, DefaultShell.shell.playerTemplate)
    }
}

/**
 * L4: 解析器必须拒绝非法 player.template(白名单外),但**不抛异常**;
 * 非法描述整张被拒,而不是把模板设为 null 然后悄悄回落。
 */
class PlayerTemplateParseRejectionTest {

    @Test
    fun `invalid player template in player page is rejected as issue`() {
        val json = """
            {
              "schemaVersion": 1,
              "id": "test.bad.player.tpl",
              "name": "测试非法播放页模板",
              "tokens": { "theme": "MONO", "dark": true },
              "shell": {
                "bottomBar": [
                  { "id": "t1", "label": "库", "icon": "library", "target": "library" },
                  { "id": "t2", "label": "播", "icon": "play",     "target": "player" }
                ],
                "miniPlayer": true
              },
              "pages": {
                "library": { "header": null, "sections": [] },
                "player": { "header": null, "template": "circular", "sections": [] },
                "me":      { "header": null, "sections": [] }
              },
              "startPage": "library",
              "playerEntry": "tab"
            }
        """.trimIndent()
        val result = SkinParser.parse(json)
        assertTrue("非法 player.template 必须被拒绝", result is SkinValidation.Invalid)
        val invalid = result as SkinValidation.Invalid
        assertTrue(
            "issues 应包含 INVALID_PROP_VALUE 提到 'circular', 实际=${invalid.issues}",
            invalid.issues.any {
                it.code == cn.com.dcsgo.mihx.core.skin.SkinIssue.Code.INVALID_PROP_VALUE &&
                    it.message.contains("circular") &&
                    it.field.contains("player.template")
            },
        )
    }

    @Test
    fun `valid vinyl template in player page passes parser`() {
        // 同样的骨架描述,只把 template 改成合法值("vinyl") → 必须通过校验
        val json = """
            {
              "schemaVersion": 1,
              "id": "test.vinyl.ok",
              "name": "测试合法黑胶",
              "tokens": { "theme": "MONO", "dark": true },
              "shell": {
                "bottomBar": [
                  { "id": "t1", "label": "库", "icon": "library", "target": "library" },
                  { "id": "t2", "label": "播", "icon": "play",     "target": "player" }
                ],
                "miniPlayer": true
              },
              "pages": {
                "library": { "header": null, "sections": [] },
                "player": { "header": null, "template": "vinyl", "sections": [] },
                "me":      { "header": null, "sections": [] }
              },
              "startPage": "library",
              "playerEntry": "tab"
            }
        """.trimIndent()
        val result = SkinParser.parse(json)
        assertTrue("合法 vinyl 必须通过校验", result is SkinValidation.Valid)
        val valid = result as SkinValidation.Valid
        val shell = SkinShellResolver.resolve(valid.skin)
        assertEquals(PlayerTemplate.VINYL, shell.playerTemplate)
    }
}
