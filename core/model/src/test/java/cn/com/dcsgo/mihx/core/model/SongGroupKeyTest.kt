package cn.com.dcsgo.mihx.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 多版本分组键（2026-11 规则调整）。
 *
 * 未手动调整的歌曲只有**歌名 + 歌手 + 专辑完全相同**才同组；
 * 手动关联沿用目标组键，手动移出使用 "标题#id" 独立成组。
 */
class SongGroupKeyTest {

    private fun song(
        id: Int = 1,
        title: String = "晴天",
        artist: String = "周杰伦",
        album: String = "叶惠美",
        titleOverride: String? = null,
    ) = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        titleOverride = titleOverride,
    )

    @Test
    fun `same title artist and album share default group`() {
        assertEquals(song(1).groupKey, song(2).groupKey)
    }

    @Test
    fun `different artist splits default group`() {
        assertNotEquals(song(1).groupKey, song(2, artist = "其他歌手").groupKey)
    }

    @Test
    fun `different album splits default group`() {
        assertNotEquals(song(1).groupKey, song(2, album = "其他专辑").groupKey)
    }

    @Test
    fun `manual reassign joins target group`() {
        val target = song(2, title = "Live", artist = "Live Artist", album = "Live Album")
        val moved = song(1, titleOverride = target.groupKey)

        assertEquals(target.groupKey, moved.groupKey)
        assertEquals("Live", moved.groupKeyLabel)
    }

    @Test
    fun `detached song gets its own group`() {
        val detached = song(1, titleOverride = "晴天#1")

        assertTrue(detached.isDetachedFromGroup)
        assertNotEquals(detached.groupKey, song(2).groupKey)
        assertEquals("晴天", detached.groupKeyLabel)
    }

    @Test
    fun `legacy override keeps original manual grouping`() {
        // 旧版手动关联只存纯标题：直接沿用，避免升级后旧关联丢失
        val legacy = song(1, titleOverride = "旧分组")

        assertFalse(legacy.isDetachedFromGroup)
        assertEquals("旧分组", legacy.groupKey)
        assertEquals("旧分组", legacy.groupKeyLabel)
    }
}