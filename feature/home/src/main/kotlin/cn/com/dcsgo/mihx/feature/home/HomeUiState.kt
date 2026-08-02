package cn.com.dcsgo.mihx.feature.home

import cn.com.dcsgo.mihx.core.model.Song

/** UI state for the 首页曲库 screen (plan P5-A). */
data class HomeUiState(
    val songs: List<Song> = emptyList(),
    val query: String = "",
    val selectedIds: Set<Long> = emptySet(),
    val isImporting: Boolean = false,
    val importProgress: ImportProgress? = null,
)

/** `(done, total)` snapshot for the import progress bar. */
data class ImportProgress(val done: Int, val total: Int)
