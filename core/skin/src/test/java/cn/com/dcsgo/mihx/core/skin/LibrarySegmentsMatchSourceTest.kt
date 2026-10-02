package cn.com.dcsgo.mihx.core.skin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **源码一致性回归**：描述里的分段必须与 `feature/playlist` 的 `LibraryTab` enum 逐字一致。
 *
 * ## 为什么需要这个测试（2026-09-29 P4 真实教训）
 *
 * P1 的 `DefaultSkinTest` 把曲库分段断言成 5 项 `歌单/歌手/专辑/情绪/歌曲`，
 * 但源码里 `LibraryTab` 只有 **4 项**（没有「歌曲」——该词只出现在歌手/专辑**详情页**）。
 *
 * 那个测试是**拿描述自证描述**（expected 与 actual 都来自 `DefaultSkin`），
 * 所以错误被完美锁死：测试全绿，描述却是错的。
 * 直到 P4 做分区化、真正去读源码时才暴露。
 *
 * 本测试改为**直接读源码文件**，让描述与代码事实对齐。这是"与现状逐页无差异"
 * 这条验收标准在**元数据层面**的落实。
 *
 * 注：读源码文件做断言在单测里不算优雅，但它拦住的是一类**单测自证**抓不到的错，
 * 值得。若将来 `LibraryTab` 移到别处，改这里的路径即可。
 */
class LibrarySegmentsMatchSourceTest {

    /**
     * 注意路径基准：Gradle 单测的工作目录是**模块目录**（`core/skin`），
     * 因此到仓库根要走 `../../`（实测 `../playlist/...` 会找不到文件）。
     * 同时容忍从仓库根运行的场景。
     */
    private val libraryTabSource: File = listOf(
        "../../feature/playlist/src/main/java/cn/com/dcsgo/mihx/feature/playlist/PlaylistComponents.kt",
        "feature/playlist/src/main/java/cn/com/dcsgo/mihx/feature/playlist/PlaylistComponents.kt",
    ).map(::File).firstOrNull(File::exists)
        ?: File(
            "../../feature/playlist/src/main/java/cn/com/dcsgo/mihx/feature/playlist/PlaylistComponents.kt",
        )

    /** 从 `LibraryTab` enum 里解析出真实的分段标签。 */
    private fun parseLibraryTabLabels(): List<String> {
        assertTrue(
            "找不到 LibraryTab 源文件（路径可能已变）：${libraryTabSource.absolutePath}",
            libraryTabSource.exists(),
        )
        val text = libraryTabSource.readText()
        val enumBody = Regex(
            """enum class LibraryTab\s*\([^)]*\)\s*\{(.*?)\}""",
            RegexOption.DOT_MATCHES_ALL,
        ).find(text)?.groupValues?.get(1)
            ?: error("未能从源码解析出 LibraryTab enum 主体")

        // 匹配形如 PLAYLISTS("歌单"),
        return Regex("""\w+\("([^"]+)"\)""")
            .findAll(enumBody)
            .map { it.groupValues[1] }
            .toList()
    }

    @Test
    fun `description segments match LibraryTab source exactly`() {
        val fromSource = parseLibraryTabLabels()
        val fromDescription = DefaultSkin.skin().pages.getValue(DefaultSkin.PAGE_LIBRARY).segments

        assertEquals(
            "描述里的曲库分段必须与 LibraryTab 源码逐字一致（含顺序）",
            fromSource,
            fromDescription,
        )
    }

    @Test
    fun `catalog whitelist matches LibraryTab source exactly`() {
        val fromSource = parseLibraryTabLabels().toSet()
        assertEquals(
            "零件库白名单必须与 LibraryTab 源码一致",
            fromSource,
            SkinPartCatalog.LIBRARY_SEGMENTS,
        )
    }

    @Test
    fun `source actually has four segments and no song segment`() {
        // 把 bug 的具体形状固定下来：曲库没有「歌曲」分段
        val fromSource = parseLibraryTabLabels()
        assertEquals(4, fromSource.size)
        assertTrue("曲库不应有「歌曲」分段（那是歌手/专辑详情页的子标签）", "歌曲" !in fromSource)
    }

    @Test
    fun `every segment in the description has sections defined`() {
        // 分段必须有对应分区，否则点进去是空白（校验器也拦这条，这里再兜一层）
        val library = DefaultSkin.skin().pages.getValue(DefaultSkin.PAGE_LIBRARY)
        library.segments.forEach { segment ->
            val sections = library.sectionsBySegment[segment]
            assertTrue(
                "分段「$segment」在描述里缺少 sectionsBySegment 定义",
                !sections.isNullOrEmpty(),
            )
        }
    }
}
