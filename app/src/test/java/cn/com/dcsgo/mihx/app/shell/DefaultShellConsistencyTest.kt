package cn.com.dcsgo.mihx.app.shell

import cn.com.dcsgo.mihx.core.skin.DefaultSkin
import cn.com.dcsgo.mihx.core.skin.SkinParser
import cn.com.dcsgo.mihx.core.skin.SkinValidation
import cn.com.dcsgo.mihx.navigation.AppRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P2 的核心不变式：**两条路径必须给出同一个外壳**。
 *
 * - [DefaultShell.shell] —— 启动路径用的 Kotlin 常量（快、不可能失败）；
 * - [SkinShellResolver.resolve] 解析 `DefaultSkin.JSON` —— 描述驱动的路径（P5 用户皮肤走这条）。
 *
 * 二者一旦分歧，就意味着"内置骨架"和"描述能表达的结构"不是一回事——
 * 那么 P5 上线用户皮肤后行为会与现在不同，而这正是 P2 要杜绝的。
 *
 * 另外本测试还锁住 P1 发现的**启动页保真 bug**（曾错写为 library，实际启动在播放页）。
 */
class DefaultShellConsistencyTest {

    private val fromConstants = DefaultShell.shell
    private val fromDescription = DefaultShell.shellFromSkin()

    @Test
    fun `default skin json still parses cleanly`() {
        val result = SkinParser.parse(DefaultSkin.JSON)
        assertTrue("内置描述必须校验通过", result is SkinValidation.Valid)
        assertEquals(
            "内置描述不应有 warning",
            0,
            (result as SkinValidation.Valid).warnings.size,
        )
    }

    @Test
    fun `both paths produce the same tabs`() {
        assertEquals(fromConstants.tabs, fromDescription.tabs)
    }

    @Test
    fun `both paths produce the same start route`() {
        assertEquals(fromConstants.startRoute, fromDescription.startRoute)
    }

    @Test
    fun `both paths produce the same mini player and player entry settings`() {
        assertEquals(fromConstants.miniPlayer, fromDescription.miniPlayer)
        assertEquals(fromConstants.playerEntry, fromDescription.playerEntry)
    }

    @Test
    fun `start route is the player page not the library page`() {
        // P1 初版把 startPage 写成 library，与 AppNavHost:132 `startDestination = AppRoutes.HOME` 不符。
        // 这条断言防止该保真 bug 复发。
        assertEquals(AppRoutes.HOME, fromConstants.startRoute)
        assertEquals(AppRoutes.HOME, fromDescription.startRoute)
    }

    @Test
    fun `library page key resolves to the playlist route`() {
        // 描述里的页面 key 与路由的对应关系（:app 才知道），防止映射错位
        assertEquals(AppRoutes.PLAYLIST, fromConstants.tabs[0].route)
        assertEquals(AppRoutes.USER, fromConstants.tabs[2].route)
    }

    @Test
    fun `resolver falls back to the builtin shell when the description is invalid`() {
        // 启动路径必须 fail-safe：描述坏了也不能让 App 起不来
        val broken = SkinShellResolver.resolveFromJson("{ this is not a skin }")
        assertEquals(DefaultShell.shell, broken)
    }

    @Test
    fun `resolver skips tabs whose page key has no route instead of crashing`() {
        // 校验器只能保证 target 在 pages 里存在；"该页面 key 有没有路由"是 :app 的知识。
        // 遇到不认识的 key 应跳过该项而不是抛异常。
        val json =
            """
            {
              "schemaVersion": 1,
              "id": "t", "name": "t",
              "tokens": { "theme": "MONO", "dark": true },
              "shell": { "bottomBar": [
                { "id": "a", "label": "A", "icon": "library", "target": "library" },
                { "id": "b", "label": "B", "icon": "me",      "target": "me" },
                { "id": "c", "label": "C", "icon": "play",    "target": "unknownPageKey" }
              ] },
              "pages": {
                "library": { "sections": [] },
                "me": { "sections": [] },
                "unknownPageKey": { "sections": [] }
              },
              "startPage": "library"
            }
            """.trimIndent()
        val resolved = SkinShellResolver.resolveFromJson(json)
        assertEquals(2, resolved.tabs.size)
        assertEquals(listOf("A", "B"), resolved.tabs.map { it.label })
    }

    @Test
    fun `icon names in the default skin all resolve to a real drawable`() {
        // 底栏目前渲染纯文字，但 P4 的网格/快捷入口要用图标；缺映射不该崩
        val skin = DefaultSkin.skin()
        skin.shell.bottomBar.forEach { tab ->
            assertTrue(
                "图标 \"${tab.icon}\" 应能解析出资源 id",
                SkinShellResolver.iconRes(tab.icon) != 0,
            )
        }
    }
}
