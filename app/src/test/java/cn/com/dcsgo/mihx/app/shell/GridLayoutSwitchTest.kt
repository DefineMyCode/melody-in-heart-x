package cn.com.dcsgo.mihx.app.shell

import cn.com.dcsgo.mihx.core.skin.DefaultSkin
import cn.com.dcsgo.mihx.feature.playlist.SongListTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * 「歌手/专辑网格布局」全局开关 → 外壳映射的回归（2026-09-30 用户拍板）。
 *
 * 防的 bug 类型：
 *  - 开关被误做成「按样式分键」（用户要求两种样式共用一个开关）；
 *  - 关开关时把样式的 songList.template 覆盖掉（关 = 还原样式自身解析结果）。
 *
 * 负向对照：把 [AppShell.withGridLayout] 的 `if` 条件取反 → 第 1、3 条失败。
 */
class GridLayoutSwitchTest {

    @Test
    fun `switch on forces grid regardless of skin`() {
        for (skin in SkinShellResolver.knownSkins) {
            val shell = SkinShellResolver.resolve(skin).withGridLayout(true)
            assertEquals(
                "开关打开后「${skin.name}」必须是网格",
                SongListTemplate.GRID,
                shell.librarySongListTemplate,
            )
        }
    }

    @Test
    fun `switch off keeps skin own template`() {
        for (skin in SkinShellResolver.knownSkins) {
            val resolved = SkinShellResolver.resolve(skin)
            val shell = resolved.withGridLayout(false)
            assertSame(
                "开关关闭时不得改动样式自身的模板「${skin.name}」",
                resolved.librarySongListTemplate,
                shell.librarySongListTemplate,
            )
        }
    }

    @Test
    fun `both builtin skins are list rows when switch off`() {
        // 两套内置样式默认都是列表行：关开关 = 改造前形态（零回归）。
        for (skin in listOf(DefaultSkin.skin(), DefaultSkin.minimalSheetSkin())) {
            assertEquals(
                "「${skin.name}」关开关后应为列表行",
                SongListTemplate.DEFAULT,
                SkinShellResolver.resolve(skin).withGridLayout(false).librarySongListTemplate,
            )
        }
    }

    @Test
    fun `switcher exposes exactly two styles`() {
        // 样式切换只留「默认三页 / 极简双页」，其余（网格/黑胶/水墨/网易云式）已下线。
        assertEquals(
            "样式列表必须是 默认三页 + 极简双页 两套",
            listOf("默认三页", "极简双页"),
            SkinShellResolver.knownSkins.map { it.name },
        )
    }
}
