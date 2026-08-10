package cn.com.dcsgo.mihx.data.player

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cn.com.dcsgo.mihx.core.common.AppLog
import cn.com.dcsgo.mihx.core.common.AppLogger
import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.playback.PlaybackStateStorage
import cn.com.dcsgo.mihx.domain.playback.RestoredPlaybackState
import cn.com.dcsgo.mihx.domain.repository.PlaybackStateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

const val PLAYBACK_STATE_DATASTORE_NAME = "playback_state"
const val PLAYBACK_STATE_PREFS_NAME = "music_player_prefs"

val Context.playbackStateDataStore: DataStore<Preferences> by preferencesDataStore(
    name = PLAYBACK_STATE_DATASTORE_NAME,
)

class PlaybackStateStore(
    private val store: DataStore<Preferences>,
    private val legacyPrefs: SharedPreferences? = null,
    private val serializer: PlaybackStateSnapshotSerializer = PlaybackStateSnapshotSerializer(),
    private val logger: AppLogger = AppLog,
) : PlaybackStateStorage,
    PlaybackStateRepository {
    constructor(context: Context) : this(
        store = context.applicationContext.playbackStateDataStore,
        legacyPrefs = context.applicationContext.getSharedPreferences(
            PLAYBACK_STATE_PREFS_NAME,
            Context.MODE_PRIVATE,
        ),
    )

    fun save(queue: PlayQueue, positionMs: Long) {
        save(queue, positionMs, isInfinitePlay = false, infinitePlayedSongIds = emptySet(), currentSongId = null)
    }

    fun save(
        queue: PlayQueue,
        positionMs: Long,
        isInfinitePlay: Boolean,
        infinitePlayedSongIds: Set<Int>,
    ) {
        save(queue, positionMs, isInfinitePlay, infinitePlayedSongIds, currentSongId = null)
    }

    override fun save(
        queue: PlayQueue,
        positionMs: Long,
        isInfinitePlay: Boolean,
        infinitePlayedSongIds: Set<Int>,
        currentSongId: Int?,
    ) {
        try {
            val playbackSongId = currentSongId ?: queue.currentSong?.id
            if (queue.isEmpty && !isInfinitePlay && playbackSongId == null) {
                clear()
                return
            }

            runBlocking(Dispatchers.IO) {
                store.edit { preferences ->
                    preferences[PlaybackStateKeys.PLAY_QUEUE_JSON] = serializer.encodeQueue(queue)
                    preferences[PlaybackStateKeys.PLAY_POSITION_MS] = positionMs.coerceAtLeast(0L)
                    preferences[PlaybackStateKeys.IS_INFINITE_PLAY] = isInfinitePlay
                    preferences[PlaybackStateKeys.INFINITE_PLAYED_IDS] = serializer.encodeIds(infinitePlayedSongIds)
                    if (playbackSongId != null) {
                        preferences[PlaybackStateKeys.CURRENT_SONG_ID] = playbackSongId
                    } else {
                        preferences.remove(PlaybackStateKeys.CURRENT_SONG_ID)
                    }
                }
                clearLegacyPrefs()
            }

        } catch (e: Exception) {
            // 落盘失败不应打断播放控制,但必须留痕,否则「进度丢失」类问题无从排查。
            logger.error(TAG, "save playback state failed: songCount=${queue.songs.size}", e)
        }
    }

    override fun saveCurrentPlaybackSnapshot(songId: Int, positionMs: Long) {
        try {
            runBlocking(Dispatchers.IO) {
                writeCurrentPlaybackSnapshot(songId, positionMs)
            }
        } catch (e: Exception) {
            logger.error(TAG, "saveCurrentPlaybackSnapshot failed: song=$songId", e)
        }
    }

    /**
     * [saveCurrentPlaybackSnapshot] 的挂起版本。
     *
     * 供服务销毁等「不能阻塞调用线程」的路径使用:调用方先在主线程取好快照,再在后台作用域里落盘。
     */
    suspend fun persistCurrentPlaybackSnapshot(songId: Int, positionMs: Long) {
        try {
            withContext(Dispatchers.IO) {
                writeCurrentPlaybackSnapshot(songId, positionMs)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(TAG, "persistCurrentPlaybackSnapshot failed: song=$songId", e)
        }
    }

    private suspend fun writeCurrentPlaybackSnapshot(songId: Int, positionMs: Long) {
        val existingQueueJson = currentPreferences()[PlaybackStateKeys.PLAY_QUEUE_JSON]
            ?: legacyPrefs?.getString(KEY_PLAY_QUEUE_JSON, null)
            ?: serializer.encodeQueue(PlayQueue(songs = emptyList(), currentIndex = -1))
        store.edit { preferences ->
            preferences[PlaybackStateKeys.PLAY_QUEUE_JSON] = existingQueueJson
            preferences[PlaybackStateKeys.CURRENT_SONG_ID] = songId
            preferences[PlaybackStateKeys.PLAY_POSITION_MS] = positionMs.coerceAtLeast(0L)
        }
        clearLegacyPrefs()
    }

    override fun clear() {
        try {
            runBlocking(Dispatchers.IO) {
                store.edit { preferences ->
                    preferences.remove(PlaybackStateKeys.PLAY_QUEUE_JSON)
                    preferences.remove(PlaybackStateKeys.PLAY_POSITION_MS)
                    preferences.remove(PlaybackStateKeys.IS_INFINITE_PLAY)
                    preferences.remove(PlaybackStateKeys.INFINITE_PLAYED_IDS)
                    preferences.remove(PlaybackStateKeys.CURRENT_SONG_ID)
                }
                clearLegacyPrefs()
            }
        } catch (e: Exception) {
            logger.error(TAG, "clear playback state failed", e)
        }
    }

    override fun save(queue: PlayQueue, positionMs: Long, currentSongId: Int?) {
        save(
            queue = queue,
            positionMs = positionMs,
            isInfinitePlay = false,
            infinitePlayedSongIds = emptySet(),
            currentSongId = currentSongId,
        )
    }

    override fun restore(allSongs: List<Song>): RestoredPlaybackState? {
        return try {
            val restored = runBlocking(Dispatchers.IO) {
                val preferences = currentPreferences()
                val legacy = legacyPrefs
                val json = preferences[PlaybackStateKeys.PLAY_QUEUE_JSON]
                    ?: legacy?.getString(KEY_PLAY_QUEUE_JSON, null)
                    ?: return@runBlocking null
                val positionMs = (preferences[PlaybackStateKeys.PLAY_POSITION_MS]
                    ?: legacy?.getLong(KEY_PLAY_POSITION_MS, 0L)
                    ?: 0L).coerceAtLeast(0L)
                val isInfinitePlay = preferences[PlaybackStateKeys.IS_INFINITE_PLAY]
                    ?: legacy?.getBoolean(KEY_IS_INFINITE_PLAY, false)
                    ?: false
                val currentSongId = (preferences[PlaybackStateKeys.CURRENT_SONG_ID]
                    ?: legacy?.getInt(KEY_CURRENT_SONG_ID, -1)
                    ?: -1).takeIf { it >= 0 }
                val infinitePlayedIds = preferences[PlaybackStateKeys.INFINITE_PLAYED_IDS]
                    ?: legacy?.getString(KEY_INFINITE_PLAYED_IDS, null)
                StoredPlaybackSnapshot(
                    queueJson = json,
                    positionMs = positionMs,
                    isInfinitePlay = isInfinitePlay,
                    currentSongId = currentSongId,
                    infinitePlayedIdsJson = infinitePlayedIds,
                )
            } ?: return null
            val availableSongIds = allSongs.map { it.id }.toSet()
            val infinitePlayedSongIds = serializer.decodeInfinitePlayedIds(
                json = restored.infinitePlayedIdsJson,
                availableSongIds = availableSongIds,
            )
            val queue = serializer.decodeQueue(
                json = restored.queueJson,
                allSongs = allSongs,
                allowEmpty = restored.isInfinitePlay || restored.currentSongId != null,
            )
                ?.withCurrentSongId(restored.currentSongId, allSongs)
                ?: return null
            RestoredPlaybackState(queue, restored.positionMs, restored.isInfinitePlay, infinitePlayedSongIds)
        } catch (e: Exception) {
            logger.error(TAG, "restore playback state failed", e)
            null
        }
    }

    private suspend fun currentPreferences(): Preferences = store.data.first()

    private fun clearLegacyPrefs() {
        legacyPrefs?.edit()
            ?.remove(KEY_PLAY_QUEUE_JSON)
            ?.remove(KEY_PLAY_POSITION_MS)
            ?.remove(KEY_IS_INFINITE_PLAY)
            ?.remove(KEY_INFINITE_PLAYED_IDS)
            ?.remove(KEY_CURRENT_SONG_ID)
            ?.apply()
    }

    private fun PlayQueue.withCurrentSongId(songId: Int?, allSongs: List<Song>): PlayQueue {
        if (songId == null) return this
        if (currentSong?.id == songId) return this

        val queueIndex = songs.indexOfFirst { it.id == songId }
        if (queueIndex >= 0) {
            return copy(currentIndex = queueIndex)
        }

        val song = allSongs.firstOrNull { it.id == songId } ?: return this
        return PlayQueue().setQueue(listOf(song), startIndex = 0, mode = playMode)
    }

    private object PlaybackStateKeys {
        val PLAY_QUEUE_JSON = stringPreferencesKey(KEY_PLAY_QUEUE_JSON)
        val PLAY_POSITION_MS = longPreferencesKey(KEY_PLAY_POSITION_MS)
        val IS_INFINITE_PLAY = booleanPreferencesKey(KEY_IS_INFINITE_PLAY)
        val INFINITE_PLAYED_IDS = stringPreferencesKey(KEY_INFINITE_PLAYED_IDS)
        val CURRENT_SONG_ID = intPreferencesKey(KEY_CURRENT_SONG_ID)
    }

    companion object {
        private const val TAG = "PlaybackStateStore"
        private const val KEY_PLAY_QUEUE_JSON = "play_queue_json"
        private const val KEY_PLAY_POSITION_MS = "play_position_ms"
        private const val KEY_IS_INFINITE_PLAY = "is_infinite_play"
        private const val KEY_INFINITE_PLAYED_IDS = "infinite_played_ids"
        private const val KEY_CURRENT_SONG_ID = "current_song_id"
    }
}
