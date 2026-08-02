package cn.com.dcsgo.mihx.feature.user

import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.core.model.SongPlayStats
import cn.com.dcsgo.mihx.domain.repository.PlayStatsRepository
import cn.com.dcsgo.mihx.domain.repository.SongGroupOverrideRepository
import cn.com.dcsgo.mihx.domain.repository.SongRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 我的 screen use-cases (plan P5-C5 / P5-C6): playback statistics (top tracks, skips) and
 * same-title multi-version management. Thin boundary over the :domain repositories — the feature
 * layer never touches :data, satisfying the architecture gates (A2/A3).
 */
interface UserFacade {
    val songs: Flow<List<Song>>
    val playStats: Flow<List<SongPlayStats>>
    val groupOverrides: Flow<Map<String, Long>>

    suspend fun setPreferredSongId(groupKey: String, songId: Long)
    suspend fun clearPreferredSongId(groupKey: String)
}

@Singleton
class UserFacadeImpl @Inject constructor(
    private val songRepository: SongRepository,
    private val playStatsRepository: PlayStatsRepository,
    private val overrideRepository: SongGroupOverrideRepository,
) : UserFacade {
    override val songs: Flow<List<Song>> = songRepository.observeAll()
    override val playStats: Flow<List<SongPlayStats>> = playStatsRepository.observeStats()
    override val groupOverrides: Flow<Map<String, Long>> = overrideRepository.observeOverrides()

    override suspend fun setPreferredSongId(groupKey: String, songId: Long) =
        overrideRepository.setPreferredSongId(groupKey, songId)

    override suspend fun clearPreferredSongId(groupKey: String) =
        overrideRepository.clearPreferredSongId(groupKey)
}
