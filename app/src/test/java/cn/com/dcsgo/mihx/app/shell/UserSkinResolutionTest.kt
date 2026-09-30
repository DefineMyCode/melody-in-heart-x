package cn.com.dcsgo.mihx.app.shell

import cn.com.dcsgo.mihx.core.skin.DefaultSkin
import cn.com.dcsgo.mihx.core.skin.SkinParser
import cn.com.dcsgo.mihx.core.skin.SkinValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P5: 用户导入皮肤的外壳解析回归。
 *
 * ★ 真机验收抓到的 bug: 导入成功、DataStore 存了、currentSkinId 也切成
 * `user.skin.*`,但 AppRoot 只查 knownSkins(3 个内置) → user.skin.* 查不到
 * → 回落内置外壳,导入的皮肤**看起来没生效**(还是 3 Tab)。
 * 根因是"装配链路没接":我写了 resolveUserSkin(json) 但没人调它。
 *
 * 本测试锁住 AppRoot 现在的解析分支语义(与 AppRoot.shell 相同逻辑):
 *  - user.skin.* + json 有 → resolveUserSkin(json)
 *  - 其余 → resolveById(内置, 原行为)
 */
class UserSkinResolutionTest {

    /** 与 AppRoot.shell 相同的解析逻辑(分支测试)。 */
    private fun resolveShell(id: String?, json: String?): AppShell =
        if (id != null && id.startsWith("user.skin.") && json != null) {
            SkinShellResolver.resolveUserSkin(json)
        } else {
            SkinShellResolver.resolveById(id)
        }

    private val builtinJson = DefaultSkin.JSON

    @Test
    fun `user skin json resolves to user shell not builtin fallback`() {
        // 用户皮肤样本: 2 Tab + 我的页只 2 个分区
        val userJson = """
            {
              "schemaVersion": 1,
              "id": "test.skin.valid",
              "name": "测试有效皮肤",
              "tokens": { "theme": "MONO", "dark": true },
              "shell": {
                "bottomBar": [
                  { "id": "t1", "label": "曲库", "icon": "library", "target": "library" },
                  { "id": "t2", "label": "我的", "icon": "me", "target": "me" }
                ],
                "miniPlayer": true
              },
              "pages": {
                "library": { "header": null, "sections": [] },
                "player": { "header": null, "sections": [] },
                "me": {
                  "fixedOneScreen": true,
                  "header": null,
                  "sections": [ { "part": "userInfo" }, { "part": "customSkin" } ]
                }
              },
              "startPage": "library",
              "playerEntry": "tab"
            }
        """.trimIndent()
        // 描述必须合法(不合法说明测试数据本身错了)
        assert(SkinParser.parse(userJson) is SkinValidation.Valid)

        val shell = resolveShell("user.skin.abc123", userJson)
        assertEquals(
            "用户皮肤应解析出 2 Tab(曲库/我的), 而不是回落内置的 3 Tab",
            2,
            shell.tabs.size,
        )
        assertEquals(
            "我的页分区应为 [userInfo, customSkin]",
            listOf("userInfo", "customSkin"),
            shell.myPageSectionOrder,
        )
    }

    @Test
    fun `user skin id with null json falls back to builtin`() {
        // 冷启动 DataStore 异步读未到位时短暂回落内置,不能崩
        val shell = resolveShell("user.skin.abc123", null)
        assertEquals(3, shell.tabs.size)
    }

    @Test
    fun `builtin id keeps original behavior`() {
        val shell = resolveShell(DefaultSkin.ID, null)
        assertEquals(3, shell.tabs.size)
        assertTrue(shell.myPageSectionOrder.isNotEmpty())
    }

    @Test
    fun `null id resolves to default shell`() {
        assertEquals(3, resolveShell(null, null).tabs.size)
    }

    @Test
    fun `resolveUserSkin with invalid json falls back silently`() {
        // fail-safe: 坏 JSON 不崩(AppRoot 每次启动都会跑这行)
        val shell = SkinShellResolver.resolveUserSkin("{ not json at all")
        assertEquals(3, shell.tabs.size)
    }
}
