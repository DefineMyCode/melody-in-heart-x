package cn.com.dcsgo.mihx.core.skin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 契约表达力回归：除"现状"之外，描述还必须能表达**差异极大的骨架**。
 *
 * 这直接对应本项目的目标——用户要的是「装一个 UI 插件后心乐**看起来像另一个音乐软件**」。
 * 若契约只能表达现状，那它就是无用的抽象；因此这里拿原型里那两套真实皮肤做对照：
 *  - 网易云式：3 Tab（我的/发现/正在播放）· 2 列卡片墙 · 黑胶播放页
 *  - 极简双页：**2 Tab** · 播放为全局抽屉 · 迷你条常驻（用户举的例子）
 *
 * 两套都只用 [SkinPartCatalog] 里 L1–L5 的零件——**没用到任何 L6 表现层零件**，
 * 这正是"范围线画在 L5/L6 之间"的可行性证据。
 */
class SkinExpressivenessTest {

    /** 网易云式：卡片墙 + 黑胶。验证 L3 卡片墙与 L4 黑胶封面在同一契约内可表达。 */
    private val neteaseStyle =
        """
        {
          "schemaVersion": 1,
          "id": "dcsgo.skin.netease",
          "name": "网易云式",
          "tokens": {
            "theme": "VERMILION", "dark": true,
            "surface": 1,
            "cardRadiusDp": 12,
            "hairlineDp": 0,
            "rowGapDp": 12,
            "artSizeDp": 48,
            "artRadiusDp": 8,
            "nowPlayingRadiusDp": 40
          },
          "shell": {
            "bottomBar": [
              { "id": "tab-mine",     "label": "我的",     "icon": "me",     "target": "me" },
              { "id": "tab-discover", "label": "发现",     "icon": "search", "target": "discover" },
              { "id": "tab-now",      "label": "正在播放", "icon": "play",   "target": "player" }
            ],
            "miniPlayer": false
          },
          "pages": {
            "discover": {
              "header": { "title": "发现", "subtitle": "每日推荐", "actions": [ { "icon": "search", "title": "搜索" } ] },
              "sections": [
                { "part": "quickGrid",     "props": { "items": "每日推荐,私人FM,排行榜,歌单广场" } },
                { "part": "playlistGrid",  "props": { "title": "推荐歌单", "hint": "根据收听口味" } },
                { "part": "songList",      "props": { "title": "推荐歌曲", "source": "all", "template": "default", "count": "6" } }
              ]
            },
            "me": {
              "fixedOneScreen": true,
              "header": { "title": "我的", "actions": [ { "icon": "sliders", "title": "设置" } ] },
              "sections": [ { "part": "myOverview", "props": { "sections": "myHero,settingsGroup" } } ]
            },
            "player": { "sections": [] }
          },
          "startPage": "discover",
          "playerEntry": "tab",
          "nowPlaying": {
            "center": true,
            "coverShape": "vinyl",
            "controlStyle": "plain",
            "showMood": true,
            "lyrics": true,
            "queue": false,
            "actions": [ { "icon": "heart", "label": "喜欢" }, { "icon": "download", "label": "下载" } ]
          }
        }
        """.trimIndent()

    /** 极简双页：2 Tab + 播放抽屉（用户举的例子）。 */
    private val minimalStyle =
        """
        {
          "schemaVersion": 1,
          "id": "dcsgo.skin.minimal",
          "name": "极简双页 · 播放抽屉",
          "tokens": {
            "theme": "MONO", "dark": true,
            "surface": 0, "cardRadiusDp": 16, "hairlineDp": 1, "rowGapDp": 14,
            "artSizeDp": 52, "artRadiusDp": 14, "nowPlayingRadiusDp": 20
          },
          "shell": {
            "bottomBar": [
              { "id": "tab-lib", "label": "曲库", "icon": "library", "target": "library" },
              { "id": "tab-me",  "label": "我的", "icon": "me",      "target": "me" }
            ],
            "miniPlayer": true
          },
          "pages": {
            "library": {
              "header": { "title": "曲库", "subtitle": "1,103 首", "actions": [ { "icon": "search", "title": "搜索" } ] },
              "sections": [
                { "part": "playlistShelf", "props": { "title": "歌单" } },
                { "part": "songList",      "props": { "title": "全部歌曲", "source": "all", "template": "compact", "count": "7" } }
              ]
            },
            "me": {
              "fixedOneScreen": true,
              "header": { "title": "我的", "actions": [ { "icon": "sliders", "title": "设置" } ] },
              "sections": [ { "part": "myOverview", "props": { "sections": "myHero,settingsGroup" } } ]
            }
          },
          "startPage": "library",
          "playerEntry": "sheet",
          "nowPlaying": {
            "center": false, "coverShape": "square", "controlStyle": "solid",
            "showMood": false, "queue": true,
            "actions": [ { "icon": "heart", "label": "喜欢" }, { "icon": "plus", "label": "歌单" } ]
          }
        }
        """.trimIndent()

    private fun requireValid(json: String): Skin = when (val r = SkinParser.parse(json)) {
        is SkinValidation.Valid -> {
            assertEquals("这些皮肤不应有 warning：${r.warnings}", 0, r.warnings.size)
            r.skin
        }

        is SkinValidation.Invalid -> error("皮肤未通过校验：${r.issues.joinToString { "${it.code} ${it.message} @${it.field}" }}")
    }

    @Test
    fun `netease style skin is expressible`() {
        val skin = requireValid(neteaseStyle)
        assertEquals(3, skin.shell.bottomBar.size)
        assertEquals(CoverShape.VINYL, skin.nowPlaying.coverShape)
        assertTrue("卡片墙需要实心容器", skin.tokens.surface > 0f)
    }

    @Test
    fun `minimal two tab skin with player sheet is expressible`() {
        val skin = requireValid(minimalStyle)
        // 用户举的例子：只有两个 Tab，播放页走全局抽屉
        assertEquals(2, skin.shell.bottomBar.size)
        assertEquals(PlayerEntry.SHEET, skin.playerEntry)
        assertTrue("抽屉型需要有迷你条作为唤起入口", skin.shell.miniPlayer)
        assertTrue(
            "抽屉型不应在底栏再放 player 页",
            skin.shell.bottomBar.none { it.target == DefaultSkin.PAGE_PLAYER },
        )
    }

    @Test
    fun `same part renders differently via row template`() {
        // L3 性价比最高的证据：同一个 songList 零件，换 template 就从"心乐式"变"极简式"
        val compact = requireValid(minimalStyle).pages.getValue("library")
            .sections.first { it.part == SkinPartCatalog.SONG_LIST }
        assertEquals("compact", compact.props["template"])

        val standard = requireValid(neteaseStyle).pages.getValue("discover")
            .sections.first { it.part == SkinPartCatalog.SONG_LIST }
        assertEquals("default", standard.props["template"])
    }

    @Test
    fun `all three skins stay within L1 to L5 and need no L6 parts`() {
        // 范围裁定的可行性证据：三套骨架都没用到表现层零件
        val used = listOf(DefaultSkin.JSON, neteaseStyle, minimalStyle)
            .map { requireValid(it) }
            .flatMap { skin ->
                skin.pages.values.flatMap { page ->
                    (page.sections + page.sectionsBySegment.values.flatten()).map { it.part }
                }
            }
            .toSet()
        val layers = used.mapNotNull { SkinPartCatalog.layerOf(it) }.toSet()
        assertTrue(
            "不应出现 L6/L7 层零件（范围线画在 L5/L6 之间），实际用到层：$layers",
            layers.all { it == SkinPartCatalog.Layer.L1_SHELL || it == SkinPartCatalog.Layer.L2_PAGE || it == SkinPartCatalog.Layer.L3_ROW || it == SkinPartCatalog.Layer.L4_PLAYER || it == SkinPartCatalog.Layer.L5_RESOURCE },
        )
    }

    @Test
    fun `catalog itself contains no L6 or L7 entries`() {
        // 反向锁死：零件库里也不该混进表现层零件
        val layers = SkinPartCatalog.parts.values.map { it.layer }.toSet()
        assertEquals(
            "零件库只应登记 L1-L5",
            setOf(
                SkinPartCatalog.Layer.L1_SHELL,
                SkinPartCatalog.Layer.L2_PAGE,
                SkinPartCatalog.Layer.L3_ROW,
                SkinPartCatalog.Layer.L4_PLAYER,
                SkinPartCatalog.Layer.L5_RESOURCE,
            ),
            layers,
        )
    }
}
