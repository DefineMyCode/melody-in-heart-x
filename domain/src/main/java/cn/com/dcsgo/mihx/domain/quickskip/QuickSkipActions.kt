package cn.com.dcsgo.mihx.domain.quickskip

import cn.com.dcsgo.mihx.core.model.Song

/**
 * 秒切歌曲业务入口（域层）。
 * M-5（评审 2026-10-15）：与 QuickSkipRepository 一同改为 suspend，
 * 调用方（PlayerQuickSkipFacade/PlayerRuntime）负责调度，不再在组合期/主线程同步查库。
 */
interface QuickSkipActions {
    suspend fun getSongs(): List<Song>
    suspend fun add(songId: Int): Boolean
    suspend fun remove(songId: Int): Boolean
    suspend fun contains(songId: Int): Boolean
    suspend fun syncToPlaylist()
}
