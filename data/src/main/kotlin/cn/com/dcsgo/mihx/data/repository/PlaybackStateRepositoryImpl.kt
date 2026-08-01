package cn.com.dcsgo.mihx.data.repository

import androidx.datastore.preferences.core.edit
import cn.com.dcsgo.mihx.data.datastore.PlaybackStateDataStore
import cn.com.dcsgo.mihx.data.serialization.PlaybackStateSnapshotSerializer
import cn.com.dcsgo.mihx.domain.model.PlaybackStateSnapshot
import cn.com.dcsgo.mihx.domain.repository.PlaybackStateRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class PlaybackStateRepositoryImpl @Inject constructor(
    private val dataStore: PlaybackStateDataStore,
) : PlaybackStateRepository {

    private val ds = dataStore.dataStore

    override suspend fun saveSnapshot(snapshot: PlaybackStateSnapshot) {
        val json = PlaybackStateSnapshotSerializer.serialize(snapshot)
        ds.edit { it[PlaybackStateDataStore.SNAPSHOT] = json }
    }

    override suspend fun loadSnapshot(): PlaybackStateSnapshot? {
        val raw = ds.data.first()[PlaybackStateDataStore.SNAPSHOT] ?: return null
        return PlaybackStateSnapshotSerializer.deserialize(raw)
    }
}
