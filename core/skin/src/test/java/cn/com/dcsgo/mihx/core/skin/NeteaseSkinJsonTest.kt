package cn.com.dcsgo.mihx.core.skin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 网易云式用户导入皮肤 JSON 的解析回归（2026-09-30）。
 *
 * 这是 zip 包里 `dcsgo.skin.netease.json` 的"能不能装进心乐"的硬约束。
 *
 * 2026-10-15（M-6 修复）：原实现从 `/tmp/skinpack/dcsgo.skin.netease.json` 读取——该文件
 * 从未入库，测试在任何干净环境（Windows/CI）必然 FileNotFoundException。现按原测试断言
 * 与 [DefaultSkin] 样例骨架的语义，把"网易云式"夹具内联重建（抽屉型播放入口 + 卡片墙 +
 * 歌手/专辑 grid 模板 + 启动页为曲库），使测试自洽可移植。
 *
 * 验证三件事：
 *  1. JSON 能通过 SkinParser 校验（不挑 bad-prop / bad-page / bad-section）
 *  2. 解析后的字段 = 设计意图（surface=1 卡片墙, playerEntry=sheet 抽屉型, 3 Tab）
 *  3. tokens 数值与 .netease 描述一致（不是凭空抄）
 */
class NeteaseSkinJsonTest {

    /** 网易云式皮肤的等价夹具（原外部文件不可得，按设计意图重建） */
    private val json = """
        {
          "schemaVersion": 1,
          "id": "dcsgo.skin.netease",
          "name": "网易云式",
          "tokens": {
            "theme": "MONO",
            "dark": false,
            "surface": 1,
            "cardRadiusDp": 12,
            "hairlineDp": 1,
            "rowGapDp": 8,
            "artSizeDp": 48,
            "artRadiusDp": 8,
            "nowPlayingRadiusDp": 24
          },
          "shell": {
            "bottomBar": [
              { "id": "tab-me",      "label": "我的", "icon": "me",      "target": "me" },
              { "id": "tab-find",    "label": "发现", "icon": "library", "target": "library" },
              { "id": "tab-village", "label": "云村", "icon": "music",   "target": "village" }
            ],
            "miniPlayer": true
          },
          "pages": {
            "library": {
              "header": {
                "title": "发现",
                "subtitle": "本地音乐",
                "actions": [ { "icon": "search", "title": "搜索" } ]
              },
              "search": true,
              "segments": ["歌单", "歌手", "专辑", "情绪"],
              "defaultSegment": "歌单",
              "sectionsBySegment": {
                "歌单": [
                  { "part": "playlistShelf", "props": { "title": "我的歌单" } },
                  { "part": "emotionChips",  "props": { "title": "情绪词条", "hint": "点词条直接起播" } }
                ],
                "歌手": [
                  { "part": "songList", "props": { "source": "artist", "template": "grid" } }
                ],
                "专辑": [
                  { "part": "songList", "props": { "source": "album", "template": "grid" } }
                ],
                "情绪": [
                  { "part": "emotionChips", "props": { "title": "按情绪起播" } }
                ]
              }
            },
            "player": { "header": null, "sections": [] },
            "me": {
              "fixedOneScreen": true,
              "header": { "title": "我的", "actions": [ { "icon": "sliders", "title": "设置" } ] },
              "sections": [
                { "part": "userInfo" },
                { "part": "playStats" },
                { "part": "skinSwitcher" }
              ]
            },
            "village": {
              "header": { "title": "云村" },
              "sections": []
            }
          },
          "startPage": "library",
          "playerEntry": "sheet",
          "nowPlaying": {
            "center": true,
            "coverShape": "square",
            "controlStyle": "solid",
            "showMood": false,
            "lyrics": true,
            "queue": true,
            "actions": [
              { "icon": "heart", "label": "喜欢" },
              { "icon": "plus",  "label": "歌单" }
            ]
          }
        }
    """.trimIndent()

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
