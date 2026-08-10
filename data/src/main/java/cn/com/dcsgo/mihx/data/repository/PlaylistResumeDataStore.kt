package cn.com.dcsgo.mihx.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cn.com.dcsgo.mihx.domain.model.PlaylistResume
import cn.com.dcsgo.mihx.domain.repository.PlaylistResumeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private const val PLAYLIST_RESUME_DATASTORE_NAME = "playlist_resume"

val Context.playlistResumeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = PLAYLIST_RESUME_DATASTORE_NAME,
)

/** 歌单续播记录存储:每个歌单一条记录,值存 JSON {"songId":N,"updatedAtMs":T};另有"当前队列来源歌单"标记。 */
class PlaylistResumeDataStore(
    private val store: DataStore<Preferences>,
) : PlaylistResumeRepository {

    constructor(context: Context) : this(
        store = context.applicationContext.playlistResumeDataStore,
    )

    private fun keyFor(playlistId: Int) = stringPreferencesKey("resume_$playlistId")

    private val sourcePlaylistKey = intPreferencesKey("source_playlist_id")

    private fun MutablePreferences.writeResume(playlistId: Int, songId: Int) {
        this[keyFor(playlistId)] = JSONObject()
            .put("songId", songId)
            .put("updatedAtMs", System.currentTimeMillis())
            .toString()
    }

    private fun MutablePreferences.writeSourcePlaylist(playlistId: Int?) {
        if (playlistId != null) {
            this[sourcePlaylistKey] = playlistId
        } else {
            remove(sourcePlaylistKey)
        }
    }

    override fun observeResume(playlistId: Int): Flow<PlaylistResume?> = store.data.map { preferences ->
        preferences[keyFor(playlistId)]?.let { json ->
            runCatching {
                JSONObject(json).let { o -> PlaylistResume(o.getInt("songId"), o.getLong("updatedAtMs")) }
            }.getOrNull()
        }
    }

    override suspend fun record(playlistId: Int, songId: Int) {
        store.edit { preferences ->
            preferences.writeResume(playlistId, songId)
        }
    }

    override suspend fun clear(playlistId: Int) {
        store.edit { preferences ->
            preferences.remove(keyFor(playlistId))
        }
    }

    override suspend fun currentSourcePlaylistId(): Int? = store.data.first()[sourcePlaylistKey]

    override suspend fun setSourcePlaylist(playlistId: Int?) {
        store.edit { preferences ->
            preferences.writeSourcePlaylist(playlistId)
        }
    }

    override suspend fun switchSourcePlaylist(newSource: Int?, currentSongId: Int?) {
        // 读旧来源与写新来源必须同事务,否则连续快速切歌单时可能把歌曲结算到错误的歌单上。
        store.edit { preferences ->
            val oldSource = preferences[sourcePlaylistKey]
            if (oldSource != null && oldSource != newSource && currentSongId != null) {
                preferences.writeResume(oldSource, currentSongId)
            }
            preferences.writeSourcePlaylist(newSource)
        }
    }

    override suspend fun recordCurrentSource(songId: Int): Boolean {
        // 读来源与写记录必须在同一次 edit 事务内完成,否则并发切换来源时可能把歌曲记到错误的歌单上。
        var recorded = false
        store.edit { preferences ->
            val source = preferences[sourcePlaylistKey]
            recorded = source != null
            if (source != null) {
                preferences.writeResume(source, songId)
                preferences.remove(sourcePlaylistKey)
            }
        }
        return recorded
    }
}
