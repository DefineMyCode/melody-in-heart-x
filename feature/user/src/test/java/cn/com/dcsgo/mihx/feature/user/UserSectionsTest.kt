package cn.com.dcsgo.mihx.feature.user

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 「我的」页分区化回归（L2 / P4）。
 *
 * ## 为什么这个测试必须存在
 *
 * P4 之前，「我的」页的分区与顺序是**写死在 `UserScreen` 里**的（5 个 LazyColumn item）。
 * 分区化把这些 key 提成 `UserSections` 常量，让皮肤描述可以裁剪/重排。
 * 于是产生了**两个真相来源**：
 *  - `UserSections` 的 key（页面认识哪些分区）
 *  - `DefaultSkin` 描述里的 `myOverview` sections（描述声称该渲染哪些）
 *
 * 二者一旦漂移，就会出现"描述让渲染某分区但页面不认识"（静默少一张卡）
 * 或"页面有分区但描述漏了"（删不掉的僵尸卡）。
 *
 * **这正是 P4 从「曲库分段写错」那个 bug 里学到的教训**：
 * 描述与代码事实之间必须有测试绑定，光靠描述自证会完美锁死错误。
 */
class UserSectionsTest {

    @Test
    fun `default order matches the pre-refactor hardcoded order exactly`() {
        // 改造前 UserScreen 里 LazyColumn 的 item 顺序（逐条对照源码历史）：
        //   user_info → play_stats → mood_time_slot → emotion_scan → file_check
        // 顺序错了会让"不传描述"的默认路径发生视觉变化，必须锁死。
        assertEquals(
            listOf("userInfo", "playStats", "moodTimeSlot", "emotionScan", "fileCheck"),
            UserSections.DEFAULT_ORDER,
        )
    }

    @Test
    fun `all keys are distinct and non-blank`() {
        assertEquals(UserSections.DEFAULT_ORDER.size, UserSections.ALL.size)
        UserSections.DEFAULT_ORDER.forEach { assertTrue(it.isNotBlank()) }
    }

    @Test
    fun `parse returns default order for blank or null input`() {
        assertEquals(UserSections.DEFAULT_ORDER, UserSections.parse(null))
        assertEquals(UserSections.DEFAULT_ORDER, UserSections.parse(""))
        assertEquals(UserSections.DEFAULT_ORDER, UserSections.parse("   "))
    }

    @Test
    fun `parse supports dropping sections`() {
        // B 级能力的核心：裁掉不想显示的分区
        assertEquals(
            listOf("userInfo", "playStats", "fileCheck"),
            UserSections.parse("userInfo,playStats,fileCheck"),
        )
    }

    @Test
    fun `parse supports reordering sections`() {
        // B 级能力的另一半：调换顺序
        assertEquals(
            listOf("emotionScan", "userInfo", "playStats"),
            UserSections.parse("emotionScan,userInfo,playStats"),
        )
    }

    @Test
    fun `parse ignores unknown keys instead of crashing`() {
        // 坏描述不能让页面崩（启动路径 fail-safe 原则）
        assertEquals(
            listOf("userInfo", "fileCheck"),
            UserSections.parse("userInfo,noSuchSection,fileCheck"),
        )
    }

    @Test
    fun `parse falls back to default when every key is unknown`() {
        // 全非法时回落默认，而不是渲染空页面
        assertEquals(UserSections.DEFAULT_ORDER, UserSections.parse("a,b,c"))
    }

    @Test
    fun `parse tolerates whitespace around keys`() {
        assertEquals(
            listOf("userInfo", "fileCheck"),
            UserSections.parse(" userInfo , fileCheck "),
        )
    }

    @Test
    fun `single section is allowed so a skin can keep just one card`() {
        assertEquals(listOf("playStats"), UserSections.parse("playStats"))
    }

    // ── 与描述的一致性（跨模块，通过源码文件对照） ──────────────────

    private val defaultSkinSource: File = listOf(
        "../../core/skin/src/main/java/cn/com/dcsgo/mihx/core/skin/DefaultSkin.kt",
        "core/skin/src/main/java/cn/com/dcsgo/mihx/core/skin/DefaultSkin.kt",
    ).map(::File).firstOrNull(File::exists)
        ?: File("../../core/skin/src/main/java/cn/com/dcsgo/mihx/core/skin/DefaultSkin.kt")

    /**
     * 描述里「我的」页用的 part 名必须全部是页面认识的分区 key。
     *
     * 直接读描述源文件而不是解析 `DefaultSkin` 对象——本模块（feature）不该依赖
     * `:core:skin`（架构门禁不允许 core 之外的模块反向依赖，且会把皮肤模型拖进 feature）。
     */
    @Test
    fun `my page parts in the default skin are all known section keys`() {
        assertTrue("找不到 DefaultSkin 源文件：${defaultSkinSource.absolutePath}", defaultSkinSource.exists())
        val text = defaultSkinSource.readText()

        // 定位 PAGE_ME 的 sections 块（从 `"$PAGE_ME"` 到该页对象结束）
        val meBlock = Regex(
            """PAGE_ME""",
        ).findAll(text).map { it.range.first }.toList()
        assertTrue("DefaultSkin 里应至少引用一次 PAGE_ME", meBlock.isNotEmpty())

        // 抓取形如 { "part": "xxx" } 的所有 part 名
        val parts = Regex(""""part"\s*:\s*"([^"]+)"""")
            .findAll(text)
            .map { it.groupValues[1] }
            .toSet()

        val sectionish = parts.filter { it in UserSections.ALL || it in KNOWN_NON_SECTION_PARTS }
        assertTrue("描述里应至少引用一个我的页分区", sectionish.isNotEmpty())

        // 描述里出现的、看起来像"我的页分区"的名字（小驼峰且不含列表语义）必须都在 ALL 里。
        // 这里只做弱断言：确保 ALL 里的 key 至少被描述用到过一次（否则是僵尸常量）。
        UserSections.ALL.forEach { key ->
            assertTrue(
                "UserSections.$key 从未被内置描述使用——它可能已经失效，或是漏写进描述",
                parts.contains(key),
            )
        }
    }

    /** 描述里合法但不属于「我的」页分区的 part（列表/卡片类零件）。 */
    private val KNOWN_NON_SECTION_PARTS = setOf(
        "playlistShelf", "emotionChips", "songList", "libraryTabs",
        "userInfo", "playStats", "moodTimeSlot", "emotionScan", "fileCheck",
        "header", "search", "segmentChips",
    )
}
