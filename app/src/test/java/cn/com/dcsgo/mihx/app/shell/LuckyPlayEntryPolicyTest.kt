package cn.com.dcsgo.mihx.app.shell

import cn.com.dcsgo.mihx.navigation.AppRoutes
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「随心播放」入口条显示策略 [shouldShowLuckyPlayEntry] 的回归（2026-10-01 用户拍板）。
 *
 * ## 防的 bug 类型
 *  - 条件写反（有歌时显示入口条 = 与迷你条抢位）
 *  - 漏掉 SHEET 限定（默认三页也显示 = 与播放页 FAB 重复）
 *  - 与 [shouldShowMiniPlayer] 不互斥（两条同时为 true = 迷你条被入口条顶掉）
 *
 * 负向对照：把 shouldShowLuckyPlayEntry 里的 `!hasCurrentSong` 去掉 → 第 1 组用例失败。
 */
class LuckyPlayEntryPolicyTest {

    private val sheetShell = AppShell(
        tabs = listOf(
            AppTab("tab-library", AppRoutes.PLAYLIST, "曲库"),
            AppTab("tab-me", AppRoutes.USER, "我的"),
        ),
        startRoute = AppRoutes.PLAYLIST,
        miniPlayer = true,
        playerEntry = PlayerEntry.SHEET,
    )

    @Test
    fun `shows entry on sheet shell when queue empty`() {
        assertTrue(shouldShowLuckyPlayEntry(sheetShell, AppRoutes.PLAYLIST, hasCurrentSong = false))
        assertTrue(shouldShowLuckyPlayEntry(sheetShell, AppRoutes.USER, hasCurrentSong = false))
    }

    @Test
    fun `never shows entry when a song is playing`() {
        // 有歌 = 迷你条占位,两条必须互斥
        for (route in listOf(AppRoutes.PLAYLIST, AppRoutes.USER)) {
            assertFalse(shouldShowLuckyPlayEntry(sheetShell, route, hasCurrentSong = true))
            assertTrue(shouldShowMiniPlayer(sheetShell, route, hasCurrentSong = true))
        }
    }

    @Test
    fun `never shows entry on tab shell (default three pages)`() {
        // 默认三页播放页自带「随心播放」FAB,抽屉型限定挡住它
        val tabShell = DefaultShell.shell
        assertFalse(shouldShowLuckyPlayEntry(tabShell, AppRoutes.PLAYLIST, hasCurrentSong = false))
        assertFalse(shouldShowLuckyPlayEntry(tabShell, AppRoutes.USER, hasCurrentSong = false))
    }

    @Test
    fun `never shows entry on player route`() {
        // 抽屉型没有播放 Tab(兜底路由 "home"),但策略与迷你条保持同一条对称判定
        assertFalse(shouldShowLuckyPlayEntry(sheetShell, "home", hasCurrentSong = false))
    }
}
