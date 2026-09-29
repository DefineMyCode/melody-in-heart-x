package cn.com.dcsgo.mihx.core.skin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 校验器回归：**每种坏输入都必须给出精确原因**。
 *
 * 用户明确要求："不满足要求的插件应用会提示安装失败并提示原因"。
 * 因此每个 case 不只断言"失败"，还断言**失败原因的类型与定位字段**——
 * 否则"给了原因"是句空话。
 */
class SkinParserValidationTest {

    /** 取内置骨架做基底，再针对性破坏某一处；这样每个 case 只考一个点。 */
    private fun mutated(mutate: (String) -> String): SkinValidation =
        SkinParser.parse(mutate(DefaultSkin.JSON))

    private fun issuesOf(result: SkinValidation): List<SkinIssue> {
        assertTrue("期望校验失败，实际通过：$result", result is SkinValidation.Invalid)
        return (result as SkinValidation.Invalid).issues
    }

    private fun assertCode(result: SkinValidation, code: SkinIssue.Code) {
        val issues = issuesOf(result)
        assertTrue(
            "期望包含 $code，实际是 ${issues.map { it.code }}",
            issues.any { it.code == code },
        )
    }

    // ── 结构层：必须拒绝 ────────────────────────────────────────

    @Test
    fun `rejects non json input`() {
        val result = SkinParser.parse("this is not json at all {")
        assertCode(result, SkinIssue.Code.MALFORMED_JSON)
    }

    @Test
    fun `rejects missing schema version`() {
        assertCode(
            mutated { it.replace("\"schemaVersion\": 1,", "") },
            SkinIssue.Code.SCHEMA_VERSION_MISSING,
        )
    }

    @Test
    fun `rejects unsupported schema version and states which is supported`() {
        val issues = issuesOf(mutated { it.replace("\"schemaVersion\": 1,", "\"schemaVersion\": 99,") })
        val issue = issues.first { it.code == SkinIssue.Code.SCHEMA_VERSION_UNSUPPORTED }
        // 原因必须写清"支持哪个版本"，否则作者不知道该怎么改
        assertTrue("原因应说明支持的版本号：${issue.message}", issue.message.contains("1"))
    }

    @Test
    fun `rejects unknown part and fails closed`() {
        // 最关键的一条：未登记的零件一律拒绝，不静默降级
        val issues = issuesOf(mutated { it.replace("\"part\": \"playlistShelf\"", "\"part\": \"nuclearReactor\"") })
        val issue = issues.first { it.code == SkinIssue.Code.UNKNOWN_PART }
        assertTrue("原因应点名非法零件：${issue.message}", issue.message.contains("nuclearReactor"))
    }

    @Test
    fun `rejects part name that is a real androidx widget but not in catalog`() {
        // 防"描述里出现宿主不认识的视觉概念"——应引导作者去写代码或改宿主，而不是加字段
        assertCode(
            mutated { it.replace("\"part\": \"playlistShelf\"", "\"part\": \"AndroidView\"") },
            SkinIssue.Code.UNKNOWN_PART,
        )
    }

    @Test
    fun `rejects unknown icon`() {
        assertCode(
            mutated { it.replace("\"icon\": \"library\"", "\"icon\": \"definitely_not_an_icon\"") },
            SkinIssue.Code.UNKNOWN_ICON,
        )
    }

    @Test
    fun `rejects tab pointing at missing page`() {
        assertCode(
            mutated { it.replace("\"target\": \"library\"", "\"target\": \"nowhere\"") },
            SkinIssue.Code.TAB_UNKNOWN_TARGET,
        )
    }

    @Test
    fun `rejects duplicate tab ids`() {
        // 重复 id 会造成渲染 key 冲突（原型期实踩过：脏 tab 残留）
        assertCode(
            mutated { it.replace("\"id\": \"tab-me\"", "\"id\": \"tab-library\"") },
            SkinIssue.Code.TAB_DUPLICATE_ID,
        )
    }

    @Test
    fun `rejects bottom bar with a single entry`() {
        val withoutPlayerAndMe =
            """{ "id": "tab-library", "label": "曲库", "icon": "library", "target": "library" }"""
        val result = mutated { json ->
            json.replace(
                Regex("""\{\s*"id": "tab-library".*?\}\s*,\s*\{\s*"id": "tab-player".*?\}\s*,\s*\{\s*"id": "tab-me".*?\}""", RegexOption.DOT_MATCHES_ALL),
                withoutPlayerAndMe,
            )
        }
        assertCode(result, SkinIssue.Code.BOTTOM_BAR_TOO_FEW)
    }

    @Test
    fun `rejects missing start page field`() {
        // 用正则按 key 删除，避免依赖字段的具体取值（P2 修正 startPage 时曾因此误伤）
        assertCode(
            mutated { it.replace(Regex(""""startPage"\s*:\s*"[^"]*",?"""), "") },
            SkinIssue.Code.PAGE_MISSING,
        )
    }

    @Test
    fun `rejects invalid player entry value`() {
        assertCode(
            mutated { it.replace("\"playerEntry\": \"tab\"", "\"playerEntry\": \"hovering\"") },
            SkinIssue.Code.INVALID_PROP_VALUE,
        )
    }

    @Test
    fun `rejects invalid cover shape`() {
        assertCode(
            mutated { it.replace("\"coverShape\": \"square\"", "\"coverShape\": \"hexagon\"") },
            SkinIssue.Code.INVALID_PROP_VALUE,
        )
    }

    @Test
    fun `rejects invalid row template`() {
        assertCode(
            mutated { it.replace("\"template\": \"default\"", "\"template\": \"fancy\"") },
            SkinIssue.Code.INVALID_PROP_VALUE,
        )
    }

    @Test
    fun `rejects invalid song list source`() {
        assertCode(
            mutated { it.replace("\"source\": \"all\"", "\"source\": \"telepathy\"") },
            SkinIssue.Code.INVALID_PROP_VALUE,
        )
    }

    @Test
    fun `rejects negative song count`() {
        assertCode(
            mutated { it.replace("\"source\": \"all\", \"template\": \"default\"", "\"source\": \"all\", \"template\": \"default\", \"count\": \"-3\"") },
            SkinIssue.Code.INVALID_PROP_VALUE,
        )
    }

    @Test
    fun `rejects segment without sections so page cannot render blank`() {
        // 段名声明了「情绪」却没有对应分区 → 点进去会是空白，必须拒绝。
        // 刻意用独立的极小 JSON（而非对内置骨arch 做字符串替换）——替换受缩进影响，脆。
        val json =
            """
            {
              "schemaVersion": 1,
              "id": "t",
              "name": "t",
              "tokens": { "theme": "MONO", "dark": true },
              "shell": { "bottomBar": [
                { "id": "a", "label": "A", "icon": "library", "target": "p1" },
                { "id": "b", "label": "B", "icon": "me",      "target": "p2" }
              ] },
              "pages": {
                "p1": {
                  "segments": ["歌单", "情绪"],
                  "sectionsBySegment": { "歌单": [ { "part": "playlistShelf" } ] }
                },
                "p2": { "sections": [] }
              },
              "startPage": "p1"
            }
            """.trimIndent()
        assertCode(SkinParser.parse(json), SkinIssue.Code.SEGMENT_INVALID)
    }

    @Test
    fun `rejects unknown theme and lists available ones`() {
        val issues = issuesOf(mutated { it.replace("\"theme\": \"VERMILION\"", "\"theme\": \"NEON\"") })
        val issue = issues.first { it.code == SkinIssue.Code.UNKNOWN_THEME }
        assertTrue("原因应列出可用主题：${issue.message}", issue.message.contains("VERMILION"))
    }

    // ── 容错层：不该拒绝 ────────────────────────────────────────

    @Test
    fun `unknown props are ignored with a warning not a failure`() {
        val result = mutated { it.replace("\"title\": \"我的歌单\"", "\"title\": \"我的歌单\", \"sprinkleGlitter\": \"yes\"") }
        assertTrue("未知 props 应被忽略而非拒绝", result is SkinValidation.Valid)
        val warnings = (result as SkinValidation.Valid).warnings
        assertEquals(1, warnings.size)
        assertEquals(SkinIssue.Code.UNSUPPORTED_CONCEPT, warnings.first().code)
    }

    @Test
    fun `unknown top level fields are ignored for forward compatibility`() {
        val result = mutated { it.replace("\"schemaVersion\": 1,", "\"schemaVersion\": 1, \"futureFeature\": { \"a\": 1 },") }
        assertTrue("未知顶层字段应被忽略（新皮肤在旧宿主上仍可用）", result is SkinValidation.Valid)
    }

    @Test
    fun `out of range token is clamped with a warning not rejected`() {
        val result = mutated { it.replace("\"cardRadiusDp\": 16", "\"cardRadiusDp\": 9999") }
        assertTrue("令牌越界应夹紧而非拒绝加载", result is SkinValidation.Valid)
        val valid = result as SkinValidation.Valid
        assertEquals("越界必须记 warning，不能静默", 1, valid.warnings.size)
        assertEquals(SkinIssue.Code.TOKEN_OUT_OF_RANGE, valid.warnings.first().code)
        assertEquals("夹紧到上界", 40f, valid.skin.tokens.cardRadiusDp, 0.001f)
    }

    @Test
    fun `sheet skin with a player tab gets a warning not a failure`() {
        // 抽屉型皮肤底栏还留着 player 入口：语义冗余但不致命，提示作者即可
        val result = mutated { it.replace("\"playerEntry\": \"tab\"", "\"playerEntry\": \"sheet\"") }
        assertTrue(result is SkinValidation.Valid)
        val warnings = (result as SkinValidation.Valid).warnings
        assertTrue(
            "应提示底栏与抽屉语义冲突",
            warnings.any { it.code == SkinIssue.Code.UNSUPPORTED_CONCEPT },
        )
    }

    @Test
    fun `reports all problems at once instead of only the first`() {
        val broken = DefaultSkin.JSON
            .replace("\"theme\": \"VERMILION\"", "\"theme\": \"NEON\"")
            .replace("\"icon\": \"library\"", "\"icon\": \"bogus\"")
        val issues = issuesOf(SkinParser.parse(broken))
        assertTrue("应一次报出全部问题，便于作者一轮改完；实际 ${issues.size} 条", issues.size >= 2)
        assertTrue(issues.any { it.code == SkinIssue.Code.UNKNOWN_THEME })
        assertTrue(issues.any { it.code == SkinIssue.Code.UNKNOWN_ICON })
    }

    @Test
    fun `every issue carries a field locator for actionable errors`() {
        val issues = issuesOf(
            SkinParser.parse(
                DefaultSkin.JSON
                    .replace("\"theme\": \"VERMILION\"", "\"theme\": \"NEON\"")
                    .replace("\"icon\": \"library\"", "\"icon\": \"bogus\""),
            ),
        )
        issues.forEach { issue ->
            assertTrue(
                "每条问题都应带字段定位，便于作者定位；${issue.code} 的 field 为空",
                issue.field.isNotEmpty(),
            )
        }
    }
}
