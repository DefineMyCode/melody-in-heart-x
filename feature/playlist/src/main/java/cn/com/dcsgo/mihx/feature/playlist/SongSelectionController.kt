package cn.com.dcsgo.mihx.feature.playlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cn.com.dcsgo.mihx.core.model.Song

/**
 * 歌曲列表的多选 / 搜索状态持有器
 *
 * 只负责状态，不持有歌曲列表：所有派生计算都接收当前列表作为参数，
 * 这样在列表变化（重排、移除、切换 tab）后不会读到脏数据。
 *
 * 供本地音乐页、歌单详情页、歌手详情页、专辑详情页共用。
 */
@Stable
class SongSelectionController {

    /** 是否处于页内搜索状态 */
    var isSearching by mutableStateOf(false)
        private set

    /** 当前搜索关键词 */
    var searchQuery by mutableStateOf("")
        private set

    /** 是否处于多选模式 */
    var isSelectMode by mutableStateOf(false)
        private set

    /** 已选中的歌曲 id */
    var selectedIds by mutableStateOf(setOf<Int>())
        private set

    /** 切换搜索状态；关闭搜索时清空关键词 */
    fun toggleSearch() {
        if (isSearching) {
            isSearching = false
            searchQuery = ""
        } else {
            isSearching = true
        }
    }

    fun onSearchQueryChange(query: String) {
        searchQuery = query
    }

    fun enterSelectMode() {
        isSelectMode = true
    }

    /** 退出多选模式并清空选中 */
    fun exitSelectMode() {
        isSelectMode = false
        selectedIds = emptySet()
    }

    /** 切换多选模式；退出时清空选中 */
    fun toggleSelectMode() {
        if (isSelectMode) {
            exitSelectMode()
        } else {
            enterSelectMode()
        }
    }

    /** 选中 / 取消选中单首歌曲 */
    fun toggleSelected(songId: Int) {
        selectedIds = if (songId in selectedIds) {
            selectedIds - songId
        } else {
            selectedIds + songId
        }
    }

    /** 全选 / 取消全选（已全部选中则清空） */
    fun setAllSelected(displaySongs: List<Song>) {
        selectedIds = if (isAllSelected(displaySongs)) {
            emptySet()
        } else {
            displaySongs.map { it.id }.toSet()
        }
    }

    fun clearSelection() {
        selectedIds = emptySet()
    }

    // ── 派生计算：均为纯函数，接收页面当前列表 ──

    /** 按关键词过滤（歌曲名 / 歌手，忽略大小写）；空关键词返回原列表 */
    fun filterSongs(songs: List<Song>): List<Song> {
        if (searchQuery.isBlank()) return songs
        return songs.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true)
        }
    }

    fun isSelected(songId: Int): Boolean = songId in selectedIds

    /** 是否全部显示条目均已选中 */
    fun isAllSelected(displaySongs: List<Song>): Boolean =
        displaySongs.isNotEmpty() && displaySongs.all { it.id in selectedIds }

    /** 已选中且仍在当前显示列表中的歌曲（被搜索过滤掉的选中歌曲不计入） */
    fun selectedSongs(displaySongs: List<Song>): List<Song> =
        displaySongs.filter { it.id in selectedIds }
}

/** 在 composable 中实例化 */
@Composable
fun rememberSongSelectionController(): SongSelectionController =
    remember { SongSelectionController() }
