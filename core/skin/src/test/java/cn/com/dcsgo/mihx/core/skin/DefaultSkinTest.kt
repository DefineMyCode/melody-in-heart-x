package cn.com.dcsgo.mihx.core.skin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 默认骨架回归：**这份描述 = 当前心乐的 App 结构**，P1 的验收标准。
 *
 * 这些断言刻意写成"对照源码事实"的形式（3 个 Tab、播放页是 Tab 不是抽屉、
 * 曲库 5 个分段、我的页一屏不滚），这样一旦有人改描述而没改 App 结构（或反之），
 * 会立刻失败——正是 P2 之前必须锁住的地基。
 */
class DefaultSkinTest {

    private val skin = DefaultSkin.skin()

    @Test
    fun `builtin skin passes validation without warnings`() {
        val result = SkinParser.parse(DefaultSkin.JSON)
        assertTrue("内置骨架必须校验通过", result is SkinValidation.Valid)
        val warnings = (result as SkinValidation.Valid).warnings
        assertEquals("内置骨架不应产生 warning：$warnings", 0, warnings.size)
    }

    @Test
    fun `matches current app three bottom tabs`() {
        // 对照 AppDestinations：PLAYLIST / HOME / USER 三项固定
        assertEquals(3, skin.shell.bottomBar.size)
        assertEquals(listOf("曲库", "播放", "我的"), skin.shell.bottomBar.map { it.label })
    }

    @Test
    fun `player is a normal tab not a sheet in the builtin skeleton`() {
        // 对照 AppNavHost:164 —— 播放页现在是 AppRoutes.HOME 的普通 Tab
        assertEquals(PlayerEntry.TAB, skin.playerEntry)
        assertTrue(
            "内置骨架的底栏必须有指向 player 页的入口",
            skin.shell.bottomBar.any { it.target == DefaultSkin.PAGE_PLAYER },
        )
    }

    @Test
    fun `library page keeps five segments`() {
        // 对照曲库页现状：歌单 / 歌手 / 专辑 / 情绪 / 歌曲
        val library = skin.pages.getValue(DefaultSkin.PAGE_LIBRARY)
        assertEquals(listOf("歌单", "歌手", "专辑", "情绪", "歌曲"), library.segments)
        assertEquals("歌单", library.defaultSegment)
        assertTrue("曲库页必须有搜索入口", library.search)
    }

    @Test
    fun `every library segment has sections so none renders blank`() {
        val library = skin.pages.getValue(DefaultSkin.PAGE_LIBRARY)
        library.segments.forEach { segment ->
            val sections = library.sectionsBySegment[segment]
            assertTrue("分段 \"$segment\" 缺少分区，点进去会是空白", !sections.isNullOrEmpty())
        }
    }

    @Test
    fun `me page is fixed to one screen per single-screen principle`() {
        // 用户定的单屏原则：固定内容页一屏完整展示、不滚动
        assertTrue(skin.pages.getValue(DefaultSkin.PAGE_ME).fixedOneScreen)
    }

    @Test
    fun `surface token is zero because mihx has no solid container blocks`() {
        // 心乐是"零实心块"风格 —— 这是与网易云式"卡片墙"的核心差别
        assertEquals(0f, skin.tokens.surface, 0.001f)
    }

    @Test
    fun `art size matches shipped VisTokens default`() {
        // 对照已交付的 VisTokens.listCoverSizeDp = 44，避免"纯重构"变成静默视觉回归
        assertEquals(44f, skin.tokens.artSizeDp, 0.001f)
    }

    @Test
    fun `startPage is the player page because the app launches on it`() {
        // 对照 AppNavHost:132 `startDestination = AppRoutes.HOME` —— 启动落在**播放页**。
        // 2026-09-29 P2 修正：P1 初版这里错写成 library，被"与现状逐页无差异"的验收标准抓出。
        assertEquals(DefaultSkin.PAGE_PLAYER, skin.startPage)
    }

    @Test
    fun `startPage points at an existing page`() {
        assertTrue(skin.pages.containsKey(skin.startPage))
    }
}
