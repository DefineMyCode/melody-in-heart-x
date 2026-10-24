package cn.com.dcsgo.mihx.app.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 播放抽屉的**内容栈**语义（2026-10-24）。
 *
 * ## 背景
 *
 * 极简双页的播放页是**全局抽屉**（浮层，不是路由）。抽屉里点歌手/专辑后，
 * 详情页曾经做成导航目的地，于是"从详情页返回 → 回抽屉"只能靠抢系统返回键，
 * 抢不过 Navigation 2.8 的 `NavHost` 内部 `PredictiveBackHandler`
 * （它在栈变化时会重新注册、变成"最后一个"从而优先）——真机实测系统返回回不到抽屉。
 *
 * 改成和**歌词页一样**：详情页也是抽屉内的一层（[PlayerSheetContent]），
 * 导航完全不碰主导航栈，返回键只需在这几层之间切。
 *
 * ## 防的 bug 类型
 *  - 详情/歌词层返回时直接关掉抽屉（应该先退回播放页）
 *  - 播放页再按返回却没有关抽屉（应该关）
 *  - 抽屉关掉后残留着详情层（下次打开时停在详情页）
 */
class PlayerSheetNavTest {

    @Test
    fun `back from artist detail returns to the player page`() {
        assertEquals(
            PlayerSheetContent.Player,
            previousPlayerSheetContent(PlayerSheetContent.Artist("周杰伦")),
        )
    }

    @Test
    fun `back from album detail returns to the player page`() {
        assertEquals(
            PlayerSheetContent.Player,
            previousPlayerSheetContent(PlayerSheetContent.Album("叶惠美")),
        )
    }

    @Test
    fun `back from lyrics returns to the player page`() {
        assertEquals(
            PlayerSheetContent.Player,
            previousPlayerSheetContent(PlayerSheetContent.Lyrics),
        )
    }

    @Test
    fun `back on the player page closes the drawer`() {
        assertNull(
            "播放页再退一层就该关抽屉了（返回 null）",
            previousPlayerSheetContent(PlayerSheetContent.Player),
        )
    }
}
