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
            if (queue.isEmpty && !isInfinitePlay) {
                // 空会话保存：**绝不把「空队列」写进快照覆盖既有队列**。
                //
                // 背景一（2026-09-03 真机回归）：UI 重建窗口（新 ViewModel 尚未完成
                // restore/初始数据加载）存在瞬时「全空」状态（queue=0, currentSongId=null），
                // autosaver/事件保存在这个窗口一拍，若按旧逻辑 clear()，会把之前 5s 落盘的
                // 有效快照删掉，随后 restore 读到「无快照」、播放队列恒为空。
                //
                // 背景二（live-session 重连回归）：服务端仍在播、进程未死时重建 ViewModel，
                // 首帧 controller 快照同步先置 currentSong + isPlaying（playQueue 尚未恢复），
                // 进度 ticker 启动后 autosaver 每 5s 保存一拍「空队列 + 有 currentSong」。
                // 旧 guard 只挡「空队列 + 无 currentSongId」，这一拍会把队列 JSON 写成空数组，
                // 覆盖掉 5s 前落盘的有效队列；随后 restore（allowEmpty=currentSongId!=null）
                // 「成功」恢复出空队列，之后每次杀进程重启都拿到空队列、队列与进度丢失。
                //
                // 语义：空队列一律不写队列 JSON——
                // - 已有快照：保留队列，仅更新当前歌曲与位置（restore 端 withCurrentSongId 校正索引）；
                // - 无快照但有当前歌：写空队列 + 歌曲/位置，restore 按 currentSongId 兜底单曲队列；
                // - 全空且无快照：不写任何键。
                // 显式清空（用户清队列/结束播放）走 clearPlaybackState()，语义不受影响。
                runBlocking(Dispatchers.IO) {
                    var wroteSomething = false
                    store.edit { preferences ->
                        val existingQueueJson = preferences[PlaybackStateKeys.PLAY_QUEUE_JSON]
                        when {
                            existingQueueJson != null && playbackSongId != null -> {
                                // 保留既有队列，只更新当前歌曲与位置
                                preferences[PlaybackStateKeys.CURRENT_SONG_ID] = playbackSongId
                                preferences[PlaybackStateKeys.PLAY_POSITION_MS] = positionMs.coerceAtLeast(0L)
                                wroteSomething = true
                                logger.info(
                                    TAG,
                                    "save skip empty session: kept queue, " +
                                        "updated currentSong=$playbackSongId position=${positionMs}ms"
                                )
                            }
                            existingQueueJson == null && playbackSongId != null -> {
                                preferences[PlaybackStateKeys.PLAY_QUEUE_JSON] = serializer.encodeQueue(queue)
                                preferences[PlaybackStateKeys.CURRENT_SONG_ID] = playbackSongId
                                preferences[PlaybackStateKeys.PLAY_POSITION_MS] = positionMs.coerceAtLeast(0L)
                                wroteSomething = true
                                logger.info(
                                    TAG,
                                    "save empty session: no existing queue, " +
                                        "wrote currentSong=$playbackSongId position=${positionMs}ms"
                                )
                            }
                            else -> {
                                // 全空且无既有快照：不写任何键，也保留 legacy 回退（旧迁移路径）
                                logger.info(
                                    TAG,
                                    "save skip empty session: existingSnapshot=${existingQueueJson != null}" +
                                        if (existingQueueJson != null) " (kept)" else ""
                                )
                            }
                        }
                    }
                    // 仅在确实写入 DataStore 后才清除 legacy 键，避免把尚未迁移的 legacy 队列丢掉
                    if (wroteSomething) {
                        clearLegacyPrefs()
                    }
                }
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
            if (queue.songs.isEmpty()) {
                logger.info(
                    TAG,
                    "restore decoded EMPTY queue: availableSongs=${allSongs.size}, " +
                        "currentSongId=${restored.currentSongId}, json=${restored.queueJson}"
                )
            }
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
