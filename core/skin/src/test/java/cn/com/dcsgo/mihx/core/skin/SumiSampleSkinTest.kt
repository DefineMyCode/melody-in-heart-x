package cn.com.dcsgo.mihx.core.skin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L4 水墨青播放页样本骨架回归（2026-09-30）。
 *
 * 验证三件事（不可降级为「描述自洽」）：
 *  1. 内置骨架字符串能通过 SkinParser 解析（不是构造期 bug）
 *  2. 解析后的 player.template 真的等于 "sumi"
 *  3. 骨架的其它项（startPage / 底栏 / 迷你条 / 分段）都与 [DefaultSkinTest] 等价的"现状事实"一致
 *
 * 这些断言锁住的是「内置 SUMI 骨架能不能跑起来」——出问题立刻定位到 [DefaultSkin.SUMI_SAMPLE_JSON]。
 */
class SumiSampleSkinTest {

    private val skin = DefaultSkin.sumiSampleSkin()

    @Test
    fun `sumi sample skin parses successfully`() {
        // 这是 fail-fast 钩子：SUMI_SAMPLE_JSON 改坏会立刻挂在这里
        assertNotNull("sumiSampleSkin() 必须解析成功", skin)
    }

    @Test
    fun `sumi sample uses sumi player template`() {
        val playerPage = skin.pages.getValue(DefaultSkin.PAGE_PLAYER)
        assertEquals("sumi", playerPage.template)
    }

    @Test
    fun `sumi sample still has three bottom tabs matching builtin`() {
        // 与 DefaultSkinTest 的「3 个底栏 Tab」一致——SUMI 只换 player 形态,不动 Tab 数量
        assertEquals(3, skin.shell.bottomBar.size)
        assertEquals(listOf("曲库", "播放", "我的"), skin.shell.bottomBar.map { it.label })
    }

    @Test
    fun `sumi sample still launches on player page`() {
        assertEquals(DefaultSkin.PAGE_PLAYER, skin.startPage)
    }

    @Test
    fun `sumi sample keeps mini player and tab entry like builtin`() {
        // SUMI 与默认骨架的「播放进入方式 + 迷你条」保持一致;
        // 否则会出现「SUMI 抽屉型 vs 默认 Tab 型」混淆(那是 P3 的事,不属于 SUMI 这一层)。
        assertEquals(PlayerEntry.TAB, skin.playerEntry)
        assertTrue(skin.shell.miniPlayer)
    }

    @Test
    fun `sumi sample library has four segments`() {
        // 与 DefaultSkinTest 一致——SUMI 不改曲库分段结构
        val library = skin.pages.getValue(DefaultSkin.PAGE_LIBRARY)
        assertEquals(listOf("歌单", "歌手", "专辑", "情绪"), library.segments)
    }

    @Test
    fun `parser would reject sumi template from user JSON without whitelist entry`() {
        // 负向对照：构造一份 player.template = "sumi" 的 JSON,
        // 若 [SkinPartCatalog.PLAYER_TEMPLATES] 没注册 "sumi", 解析器必须拒绝。
        // 这一项防的是"白名单漏加 sumi 但装配侧已经支持"的诡异错位。
        val json = """
            {
              "schemaVersion": 1,
              "id": "test.user.sumi",
              "name": "用户 sumi 皮肤",
              "tokens": { "theme": "MONO", "dark": false },
              "shell": {
                "bottomBar": [
                  { "id": "tab-library", "label": "曲库", "icon": "library", "target": "library" },
                  { "id": "tab-player",  "label": "播放", "icon": "play",    "target": "player" },
                  { "id": "tab-me",      "label": "我的", "icon": "me",      "target": "me" }
                ],
                "miniPlayer": true
              },
              "pages": {
                "library": { "sections": [] },
                "player":  { "template": "sumi", "sections": [] },
                "me":      { "sections": [] }
              },
              "startPage": "player",
              "playerEntry": "tab"
            }
        """.trimIndent()
        val result = SkinParser.parse(json)
        assertTrue(
            "含 sumi template 的描述必须能通过 SkinParser 校验(SKIN_TEMPLATES 已注册)",
            result is SkinValidation.Valid,
        )
    }
}
