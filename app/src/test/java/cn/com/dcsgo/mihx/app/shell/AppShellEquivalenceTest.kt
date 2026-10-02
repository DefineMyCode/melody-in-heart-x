package cn.com.dcsgo.mihx.app.shell

import cn.com.dcsgo.mihx.navigation.AppRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P2 验收测试：**运行期外壳与改造前的行为完全一致**。
 *
 * 设计文档 `docs/architecture/PLUGIN_SHELL_DESIGN.md` §六 P2 的验收标准是
 * 「用内置描述驱动的 App 与现状**逐页无差异**」。逐页比对要靠真机/模拟器截图，
 * 但**导航层的等价性可以在这里逐条锁死**——这才是"无差异"里最容易退化的部分。
 *
 * 每个断言都标注了它对照的**改造前源码位置**，这样将来有人改坏了能一眼看出
 * 破坏的是哪条既有行为。
 */
class AppShellEquivalenceTest {

    private val shell = DefaultShell.shell

    // ── 底栏：对照改造前的 AppDestinations.entries ──────────────

    @Test
    fun `bottom bar keeps the original three tabs in the original order`() {
        // 改造前：AppDestinations = PLAYLIST(曲库) / HOME(播放) / USER(我的)，ordinal 序即展示序
        assertEquals(3, shell.tabs.size)
        assertEquals(listOf("曲库", "播放", "我的"), shell.tabs.map { it.label })
        assertEquals(
            listOf(AppRoutes.PLAYLIST, AppRoutes.HOME, AppRoutes.USER),
            shell.tabs.map { it.route },
        )
    }

    @Test
    fun `tab ids are stable and unique so recomposition keys cannot collide`() {
        // 原型期实踩：底栏 id 重复会造成渲染 key 冲突（脏 tab 残留）
        val ids = shell.tabs.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    // ── 启动页：对照 NavHost(startDestination = AppRoutes.HOME) ──

    @Test
    fun `start route is the player page matching the original start destination`() {
        // 改造前：AppNavHost:132 `startDestination = AppRoutes.HOME`
        assertEquals(AppRoutes.HOME, shell.startRoute)
    }

    // ── 迷你条：对照 AppScaffold 的 `currentDestination != HOME` ──

    @Test
    fun `mini player shows on library page when a song is loaded`() {
        assertTrue(shouldShowMiniPlayer(shell, AppRoutes.PLAYLIST, hasCurrentSong = true))
    }

    @Test
    fun `mini player hides on the player page itself`() {
        // 改造前的关键行为：在播放页不显示迷你条（避免与播放页自身重复）
        assertFalse(shouldShowMiniPlayer(shell, AppRoutes.HOME, hasCurrentSong = true))
    }

    @Test
    fun `mini player hides when no song is loaded`() {
        assertFalse(shouldShowMiniPlayer(shell, AppRoutes.PLAYLIST, hasCurrentSong = false))
    }

    @Test
    fun `mini player shows on child pages of a tab`() {
        // 子页面（设置/歌手详情）也应显示迷你条——改造前按"所属 Tab != HOME"判定，等价成立
        assertTrue(shouldShowMiniPlayer(shell, AppRoutes.SETTINGS, hasCurrentSong = true))
        assertTrue(shouldShowMiniPlayer(shell, "artist/foo", hasCurrentSong = true))
    }

    @Test
    fun `sheet skin hides mini player on player route even if player is not a tab`() {
        // 抽屉型骨架：播放页不在底栏。用"所属 Tab"判定会失效（找不到 → 兜底 HOME → 误判），
        // 因此必须按**路由**判定。这是 P2 相对 P1 的关键正确性改进。
        val sheetShell = shell.copy(playerEntry = PlayerEntry.SHEET)
        assertFalse(shouldShowMiniPlayer(sheetShell, AppRoutes.HOME, hasCurrentSong = true))
    }

    // ── 顶级路由归属：逐条对照改造前 AppDestinations.fromRoute ──

    @Test
    fun `user branch children map to the me tab`() {
        // 改造前 fromRoute：USER / SETTINGS / 三个统计页 / TOP榜 / 情绪分析 / 随心播放 -> USER
        listOf(
            AppRoutes.USER,
            AppRoutes.SETTINGS,
            AppRoutes.PLAYBACK_STATS,
            AppRoutes.FILE_CHECK,
            AppRoutes.RAW_PLAY_STATS,
            AppRoutes.EFFECTIVE_PLAY_STATS,
            AppRoutes.SONG_TOP_LIST_FULL,
            AppRoutes.EMOTION_ANALYSIS,
            AppRoutes.MOOD_TIME_SLOT,
        ).forEach { route ->
            assertEquals("$route 应归属「我的」", AppRoutes.USER, RouteAffinity.owningTopLevelRoute(route))
        }
    }

    @Test
    fun `library branch children map to the library tab`() {
        // 改造前 fromRoute：PLAYLIST / playlist/{id} / artist/ / album/ / 多版本管理 / 版本对比 / 秒切 -> PLAYLIST
        //
        // 注意：这里刻意用**字面量**而非 AppRoutes.artistDetail(...) 等辅助函数——
        // 那些函数内部调用 android.net.Uri.encode，在 JVM 单测里是未实现的 Android API
        // （直接抛 "Method encode in android.net.Uri not mocked"）。
        // 字面量同时更能说明"匹配的是前缀而非某个函数的输出"。
        listOf(
            AppRoutes.PLAYLIST,
            AppRoutes.playlistDetail(86),
            "artist/周杰伦",
            "album/叶惠美",
            AppRoutes.VERSION_MANAGEMENT,
            "version-comparison/group-1",
            AppRoutes.QUICK_SKIP_SONGS,
        ).forEach { route ->
            assertEquals("$route 应归属「曲库」", AppRoutes.PLAYLIST, RouteAffinity.owningTopLevelRoute(route))
        }
    }

    @Test
    fun `everything else falls back to the player tab`() {
        // 改造前 fromRoute 的 else 分支 -> HOME（全屏歌词页也走这里）
        listOf(AppRoutes.HOME, AppRoutes.LYRICS, "unknown-route", null).forEach { route ->
            assertEquals("$route 应兜底到播放页", AppRoutes.HOME, RouteAffinity.owningTopLevelRoute(route))
        }
    }

    // ── Tab 序号：对照改造前的 .ordinal（驱动转场方向与横滑翻页） ──

    @Test
    fun `tab ordinals match the pre-refactor enum ordinals`() {
        // 改造前：PLAYLIST=0 / HOME=1 / USER=2 —— 序数决定转场方向，必须一致
        assertEquals(0, shell.tabs.indexOfRoute(AppRoutes.PLAYLIST))
        assertEquals(1, shell.tabs.indexOfRoute(AppRoutes.HOME))
        assertEquals(2, shell.tabs.indexOfRoute(AppRoutes.USER))
    }

    @Test
    fun `children inherit their parent tab ordinal so same-tab navigation has no transition`() {
        // 改造前语义："同一 Tab 内序号相同则无转场"
        assertEquals(
            shell.tabs.indexOfRoute(AppRoutes.PLAYLIST),
            shell.tabs.indexOfRoute(AppRoutes.playlistDetail(1)),
        )
        assertEquals(
            shell.tabs.indexOfRoute(AppRoutes.USER),
            shell.tabs.indexOfRoute(AppRoutes.SETTINGS),
        )
    }

    @Test
    fun `swipe paging uses the runtime tab list so neighbours are resolvable`() {
        // 改造前：AppDestinations.entries[ordinal ± 1]
        val libIdx = shell.tabs.indexOfRoute(AppRoutes.PLAYLIST)
        assertEquals(shell.tabs[1], shell.tabs.tabAt(libIdx + 1))
        assertNull("左边界不应越界（改造前用 getOrNull 同样的语义）", shell.tabs.tabAt(libIdx - 1))
        val meIdx = shell.tabs.indexOfRoute(AppRoutes.USER)
        assertNull("右边界不应越界", shell.tabs.tabAt(meIdx + 1))
    }

    @Test
    fun `indexOfRoute never returns a negative index`() {
        // 负索引会让 pointerInput 里的边界判断出错；兜底返回 0
        val empty = emptyList<AppTab>()
        assertEquals(0, empty.indexOfRoute(AppRoutes.HOME))
        assertTrue(shell.tabs.indexOfRoute("no-such-route") >= 0)
    }
}
