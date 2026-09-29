package cn.com.dcsgo.mihx.app.shell

import cn.com.dcsgo.mihx.core.skin.DefaultSkin
import cn.com.dcsgo.mihx.navigation.AppRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P3 验收测试：**抽屉型骨架**（用户举例的那套：2 Tab + 播放为全局抽屉）。
 *
 * 用户原话：「只保留两个页面曲库和我的，播放页弄成一个全局抽屉可通过迷你播放条调出来」。
 * 本测试把这个诉求落成可执行断言——它证明描述层与外壳层都真的支持该形态，
 * 而不只是接口上留了个字段。
 *
 * 与之对照，`AppShellEquivalenceTest` 锁的是**默认骨架与改造前一致**；
 * 本测试锁的是**新形态能成立**。
 */
class SheetSkeletonTest {

    private val shell = SkinShellResolver.resolve(DefaultSkin.minimalSheetSkin())

    @Test
    fun `minimal sheet skin description is valid`() {
        // 描述本身必须能过校验（否则"支持抽屉"只是空话）
        val result = cn.com.dcsgo.mihx.core.skin.SkinParser.parse(DefaultSkin.MINIMAL_SHEET_JSON)
        assertTrue("抽屉型样本骨架必须校验通过", result is cn.com.dcsgo.mihx.core.skin.SkinValidation.Valid)
        assertEquals(
            0,
            (result as cn.com.dcsgo.mihx.core.skin.SkinValidation.Valid).warnings.size,
        )
    }

    @Test
    fun `sheet skeleton keeps exactly two tabs`() {
        // 用户要的就是"只保留两个页面"
        assertEquals(2, shell.tabs.size)
        assertEquals(listOf("曲库", "我的"), shell.tabs.map { it.label })
    }

    @Test
    fun `sheet skeleton has no player tab`() {
        assertTrue(
            "播放页不应出现在底栏",
            shell.tabs.none { it.route == AppRoutes.HOME },
        )
        assertEquals(PlayerEntry.SHEET, shell.playerEntry)
    }

    @Test
    fun `sheet skeleton keeps the mini player as the drawer handle`() {
        // 抽屉要靠迷你条唤起，所以必须常驻
        assertTrue(shell.miniPlayer)
    }

    @Test
    fun `sheet skeleton starts on the library page`() {
        // 播放页已不在底栏，若仍落在播放页会没有对应 Tab 可高亮
        assertEquals(AppRoutes.PLAYLIST, shell.startRoute)
    }

    @Test
    fun `mini player shows on every tab of a sheet skeleton`() {
        // 抽屉型骨架下播放页不是路由（是抽屉），因此两个 Tab 上都应显示迷你条
        assertTrue(shouldShowMiniPlayer(shell, AppRoutes.PLAYLIST, hasCurrentSong = true))
        assertTrue(shouldShowMiniPlayer(shell, AppRoutes.USER, hasCurrentSong = true))
    }

    @Test
    fun `sheet skeleton never accidentally hides the mini player`() {
        // ★ 这是 P2 那条"按路由而非按所属 Tab 判定"的设计所保护的核心场景。
        // 若按"所属 Tab"判定：曲库/我的之外的任何路由都会因找不到而兜底到 home，
        // 于是被误判为"在播放页"，把迷你条错误隐藏——抽屉就再也拉不起来了。
        listOf(
            AppRoutes.PLAYLIST,
            AppRoutes.USER,
            AppRoutes.SETTINGS,
            "artist/周杰伦",
            "album/叶惠美",
            AppRoutes.PLAYBACK_STATS,
        ).forEach { route ->
            assertTrue(
                "在 $route 上迷你条必须可见（否则抽屉无法唤起）",
                shouldShowMiniPlayer(shell, route, hasCurrentSong = true),
            )
        }
    }

    @Test
    fun `both skeletons coexist and are independently resolvable`() {
        // 两套骨架同时存在，互不影响——这是"皮肤可切换"的前提
        val default = SkinShellResolver.resolve(DefaultSkin.skin())
        assertEquals(3, default.tabs.size)
        assertEquals(PlayerEntry.TAB, default.playerEntry)
        assertEquals(2, shell.tabs.size)
        assertEquals(PlayerEntry.SHEET, shell.playerEntry)
    }

    @Test
    fun `default skeleton still behaves as before when sheet feature exists`() {
        // 新增抽屉能力**不得**改变默认骨架的行为（回归保护）
        val default = SkinShellResolver.resolve(DefaultSkin.skin())
        assertFalse(shouldShowMiniPlayer(default, AppRoutes.HOME, hasCurrentSong = true))
        assertEquals(AppRoutes.HOME, default.startRoute)
    }
}
