package cn.com.dcsgo.mihx.data.repository

import cn.com.dcsgo.mihx.data.database.dao.MelodyDao
import cn.com.dcsgo.mihx.data.database.entity.SongGroupOverrideEntity
import cn.com.dcsgo.mihx.domain.repository.SongGroupOverrideRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Room-backed [SongGroupOverrideRepository] (plan P5-C5). */
@Singleton
class SongGroupOverrideRepositoryImpl @Inject constructor(
    private val dao: MelodyDao,
) : SongGroupOverrideRepository {

    override suspend fun getPreferredSongId(groupKey: String): Long? =
        dao.getGroupOverride(groupKey)?.preferredSongId

    override suspend fun setPreferredSongId(groupKey: String, songId: Long) {
        dao.upsertGroupOverride(
            SongGroupOverrideEntity(
                groupKey = groupKey,
                preferredSongId = songId,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun clearPreferredSongId(groupKey: String) {
        dao.deleteGroupOverride(groupKey)
    }

    override fun observeOverrides(): Flow<Map<String, Long>> =
        dao.observeGroupOverrides()
            .map { rows -> rows.associate { it.groupKey to it.preferredSongId } }
}
