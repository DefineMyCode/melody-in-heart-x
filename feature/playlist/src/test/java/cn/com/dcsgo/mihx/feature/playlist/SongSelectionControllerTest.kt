package cn.com.dcsgo.mihx.feature.playlist

import cn.com.dcsgo.mihx.core.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SongSelectionControllerTest {

    private fun song(id: Int, title: String, artist: String = "Artist"): Song =
        Song(id = id, title = title, artist = artist)

    // ── 搜索 ──

    @Test
    fun `filterSongs returns all when query blank`() {
        val controller = SongSelectionController()
        val songs = listOf(song(1, "A"), song(2, "B"))
        assertEquals(songs, controller.filterSongs(songs))
    }

    @Test
    fun `filterSongs matches title or artist case-insensitively`() {
        val controller = SongSelectionController()
        val songs = listOf(song(1, "Hello", "Alpha"), song(2, "World", "Beta"))

        controller.onSearchQueryChange("hello")
        assertEquals(listOf(songs[0]), controller.filterSongs(songs))

        controller.onSearchQueryChange("beta")
        assertEquals(listOf(songs[1]), controller.filterSongs(songs))

        controller.onSearchQueryChange("no-match")
        assertEquals(emptyList<Song>(), controller.filterSongs(songs))
    }

    @Test
    fun `toggleSearch closes and clears query`() {
        val controller = SongSelectionController()
        controller.toggleSearch()
        assertTrue(controller.isSearching)
        controller.onSearchQueryChange("abc")

        controller.toggleSearch()
        assertFalse(controller.isSearching)
        assertEquals("", controller.searchQuery)
    }

    // ── 多选模式 ──

    @Test
    fun `exitSelectMode clears selection`() {
        val controller = SongSelectionController()
        controller.enterSelectMode()
        controller.toggleSelected(1)
        controller.toggleSelected(2)
        assertTrue(controller.isSelectMode)
        assertEquals(setOf(1, 2), controller.selectedIds)

        controller.exitSelectMode()
        assertFalse(controller.isSelectMode)
        assertEquals(emptySet<Int>(), controller.selectedIds)
    }

    @Test
    fun `toggleSelectMode enters and exits`() {
        val controller = SongSelectionController()
        controller.toggleSelectMode()
        assertTrue(controller.isSelectMode)
        controller.toggleSelectMode()
        assertFalse(controller.isSelectMode)
    }

    @Test
    fun `toggleSelected adds then removes`() {
        val controller = SongSelectionController()
        controller.enterSelectMode()
        controller.toggleSelected(7)
        assertTrue(controller.isSelected(7))
        controller.toggleSelected(7)
        assertFalse(controller.isSelected(7))
    }

    // ── 全选 ──

    @Test
    fun `setAllSelected selects all then clears when already all selected`() {
        val controller = SongSelectionController()
        val display = listOf(song(1, "A"), song(2, "B"), song(3, "C"))

        controller.setAllSelected(display)
        assertEquals(setOf(1, 2, 3), controller.selectedIds)
        assertTrue(controller.isAllSelected(display))

        controller.setAllSelected(display)
        assertEquals(emptySet<Int>(), controller.selectedIds)
        assertFalse(controller.isAllSelected(display))
    }

    @Test
    fun `isAllSelected is false for empty list`() {
        val controller = SongSelectionController()
        assertFalse(controller.isAllSelected(emptyList()))
    }

    // ── 选中派生 ──

    @Test
    fun `selectedSongs filters out ids not in current display list`() {
        val controller = SongSelectionController()
        controller.enterSelectMode()
        controller.toggleSelected(1)
        controller.toggleSelected(2) // 2 已不在显示列表中（例如被搜索过滤掉）
        val display = listOf(song(1, "A"), song(3, "C"))

        assertEquals(listOf(song(1, "A")), controller.selectedSongs(display))
    }
}
