package cn.com.dcsgo.mihx.feature.player

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Playlist
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.model.DeleteSongResult
import cn.com.dcsgo.mihx.domain.model.LocalFileValidationResult
import cn.com.dcsgo.mihx.domain.repository.PlaybackStatsSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * Main ViewModel for the music player screen.
 *
 * This class exposes the player API used by Compose and delegates behavior to
 * independently testable player components.
 *
 * UI code should call this public API instead of holding Repository or Media3
 * controller instances directly.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    runtimeFactory: PlayerRuntimeFactory,
) : ViewModel() {

    private val runtime = runtimeFactory.create(viewModelScope)

    private val playStatsRepository = runtime.playStatsRepository
    val uiState: StateFlow<PlayerUiState> = runtime.uiState

    /** 播放位置（毫秒）窄流：播放中每 ~500ms 更新，仅供进度条/歌词等局部订阅，避免整壳重组。 */
    val positionMs: StateFlow<Long> = runtime.positionMs

    /** 定时关闭剩余毫秒窄流：倒计时每秒更新，仅供定时关闭 Chip 局部订阅，避免整壳重组（M-6）。 */
    val sleepTimerRemainingMs: StateFlow<Long> = runtime.sleepTimerRemainingMs

    /** 本地歌曲文件校验结果（未确认前保留，供结果页重复进入）。 */
    val validationResult: StateFlow<LocalFileValidationResult?> = runtime.validationResult

    /** 本地歌曲文件校验是否正在后台运行。 */
    val isValidating: StateFlow<Boolean> = runtime.isValidating

    /** Current playback queue. */
    val playQueue: PlayQueue get() = runtime.playQueue

    /** Current song. */
    val currentSong: Song? get() = runtime.currentSong

    /** Current play mode. */
    val currentPlayMode: PlayMode get() = runtime.currentPlayMode

    init {
        runtime.start()
    }

    /** Returns songs currently marked for quick skip. */
    fun getQuickSkipSongs(): List<Song> = runtime.getQuickSkipSongs()

    /** Adds a song to the quick-skip list. */
    fun addToQuickSkipSongs(songId: Int) {
        runtime.addToQuickSkipSongs(songId)
    }

    /** Removes a song from the quick-skip list. */
    fun removeFromQuickSkipSongs(songId: Int) {
        runtime.removeFromQuickSkipSongs(songId)
    }

    /** Returns whether a song is in the quick-skip list. */
    fun isInQuickSkipSongs(songId: Int): Boolean = runtime.isInQuickSkipSongs(songId)

    /** Synchronizes quick-skip songs to their playlist using replacement mode. */
    fun syncQuickSkipSongsToPlaylist() {
        runtime.syncQuickSkipSongsToPlaylist()
    }

    /** Switches to another version of the same song. */
    fun switchToVersion(targetSong: Song) {
        runtime.switchToVersion(targetSong)
    }

    /** Groups songs by title/version key without touching repository state. */
    fun getGroupedSongs(songs: List<Song>): List<List<Song>> = runtime.getGroupedSongs(songs)

    /** Finds same-name songs from a supplied immutable list. */
    fun getSongsWithSameName(song: Song, songs: List<Song>): List<Song> {
        return runtime.getSongsWithSameName(song, songs)
    }

    /** Replaces the playback queue and starts playback at [startIndex]. */
    fun setPlayQueue(songs: List<Song>, startIndex: Int = 0, mode: PlayMode? = null) {
        runtime.setPlayQueue(songs, startIndex, mode)
    }

    /** Adds a song to the end of the queue, ignoring duplicates. */
    fun addToPlayQueue(song: Song): Boolean = runtime.addToPlayQueue(song)

    /** Inserts a song immediately after the current song and temporarily switches to sequential playback. */
    fun addSongToNextPlay(song: Song) {
        runtime.addSongToNextPlay(song)
    }

    /** Inserts songs immediately after the current song, moving existing items and ignoring duplicates in the input. */
    fun addSongsToNextPlay(songs: List<Song>): Int = runtime.addSongsToNextPlay(songs)

    /** Adds songs to the queue tail and returns the number actually added. */
    fun addToPlayQueue(songs: List<Song>): Int = runtime.addToPlayQueue(songs)

    /** Removes a song from the queue and advances playback if it was current. */
    fun removeFromPlayQueue(songId: Int) {
        runtime.removeFromPlayQueue(songId)
    }

    /** Removes a queue item at the supplied queue index. */
    fun removeFromPlayQueueAt(index: Int) {
        runtime.removeFromPlayQueueAt(index)
    }

    /** Clears the playback queue. */
    fun clearPlayQueue() {
        runtime.clearPlayQueue()
    }

    /** Cycles through the available play modes. */
    fun togglePlayMode() {
        runtime.togglePlayMode()
    }

    /** Sets a specific play mode. */
    fun setPlayMode(mode: PlayMode) {
        runtime.setPlayMode(mode)
    }

    /** Plays the song at a queue index. */
    fun playQueueItem(index: Int): Boolean = runtime.playQueueItem(index)

    /** Plays a song, preferring the current queue when the song is already queued. */
    fun playSong(song: Song) {
        runtime.playSong(song)
    }

    /** Plays a song from a supplied context list by replacing the active queue. */
    fun playSongFromContext(song: Song, contextSongs: List<Song>) {
        runtime.playSongFromContext(song, contextSongs)
    }

    /** Toggles playback. */
    fun togglePlayPause() {
        runtime.togglePlayPause()
    }

    /** Moves to the previous item. */
    fun playPrevious() {
        runtime.playPrevious()
    }

    /** Moves to the next item. */
    fun playNext() {
        runtime.playNext()
    }

    /** Marks the start of a seek gesture. */
    fun startSeeking() {
        runtime.startSeeking()
    }

    /** Ends a seek gesture and jumps to the requested position. */
    fun endSeeking(positionMs: Long) {
        runtime.endSeeking(positionMs)
    }

    /** Seeks to the requested playback position. */
    fun seekTo(positionMs: Long) {
        runtime.seekTo(positionMs)
    }

    /** Clears the current error message after it is shown. */
    fun clearError() {
        runtime.clearError()
    }

    /** 在后台校验本地歌曲文件有效性并清理失效数据。 */
    fun validateLocalFiles() {
        runtime.validateLocalFiles()
    }

    /** 用户确认校验结果后清除（结果页徽标消失）。 */
    fun acknowledgeValidationResult() {
        runtime.acknowledgeValidationResult()
    }

    /**
     * 加载播放统计快照（周/月 Top、总时长等），供 USER / 播放统计 / 歌曲榜单路由展示。
     */
    suspend fun loadPlaybackStatsSnapshot(): PlaybackStatsSnapshot =
        playStatsRepository.playbackStatsSnapshot()

    /**
     * 加载按播放次数降序排序的歌曲计数列表（songId, count）。
     * @param useRawCounts true 取原始播放次数，false 取有效播放次数。
     */
    suspend fun loadRankedCounts(useRawCounts: Boolean): List<Pair<Int, Int>> =
        playStatsRepository.getRankedCounts(useRawCounts = useRawCounts, descending = true)

    /**
     * Imports a folder from an SAF tree URI and reports progress to UI.
     *
     * @param treeUri tree URI returned by OpenDocumentTree
     * @param onResult callback with the number of newly added songs
     */
    fun importFolder(treeUri: Uri, onResult: (Int) -> Unit) {
        runtime.importFolder(treeUri, onResult)
    }

    /** Returns songs from a playlist using the current UI snapshot. */
    fun getSongsByPlaylist(playlist: Playlist): List<Song> = runtime.getSongsByPlaylist(playlist)

    /** Creates a playlist. */
    fun createPlaylist(name: String): Playlist? = runtime.createPlaylist(name)

    /** Deletes a playlist. */
    fun deletePlaylist(playlistId: Int) {
        runtime.deletePlaylist(playlistId)
    }

    /** Renames a playlist. */
    fun renamePlaylist(playlistId: Int, newName: String): Boolean {
        return runtime.renamePlaylist(playlistId, newName)
    }

    /** Adds a song to a playlist. */
    fun addSongToPlaylist(playlistId: Int, songId: Int): Boolean {
        return runtime.addSongToPlaylist(playlistId, songId)
    }

    /** Removes a song from a playlist. */
    fun removeSongFromPlaylist(playlistId: Int, songId: Int) {
        runtime.removeSongFromPlaylist(playlistId, songId)
    }

    /** Reorders songs within a playlist and persists the order. */
    fun reorderPlaylist(playlistId: Int, orderedSongIds: List<Int>) {
        runtime.reorderPlaylist(playlistId, orderedSongIds)
    }

    /** Returns whether a song is in a playlist. */
    fun isSongInPlaylist(playlistId: Int, songId: Int): Boolean {
        return runtime.isSongInPlaylist(playlistId, songId)
    }

    /** Deletes a song and removes it from UI state after the physical file is deleted. */
    suspend fun deleteSong(songId: Int): DeleteSongResult = runtime.deleteSong(songId)

    /** Detaches a song from its current version group by assigning a unique title override. */
    fun detachSongFromGroup(song: Song): Boolean = runtime.detachSongFromGroup(song)

    /** Reassigns a song to another song's version group. */
    fun reassignSongToGroup(song: Song, targetSong: Song): Boolean {
        return runtime.reassignSongToGroup(song, targetSong)
    }

    /** Clears a song title override and restores its natural group key. */
    fun resetSongGroupKey(song: Song): Boolean = runtime.resetSongGroupKey(song)

    /**
     * Builds a random queue and starts playback immediately.
     * @return true 表示已生成随机队列并开始播放；false 表示库中无可播放歌曲，未开始播放。
     */
    fun playRandomQueue(): Boolean {
        return runtime.playRandomQueue()
    }

    /**
     * 关联播放：清空播放队列，仅保留当前歌曲，再追加其关联专辑歌曲与关联歌手的所有歌曲（去重随机）。
     * @return 实际追加的关联歌曲数量
     */
    fun playRelatedSongs(currentSong: Song): Int {
        return runtime.playRelatedSongs(currentSong)
    }

    /**
     * Starts infinite random playback.
     * @return true 表示已开启无限随机播放；false 表示库中无可播放歌曲，未开启。
     */
    fun startInfinitePlay(): Boolean {
        return runtime.startInfinitePlay()
    }

    /** Stops infinite playback mode. */
    fun stopInfinitePlay() {
        runtime.stopInfinitePlay()
    }

    /** 开始定时关闭。 */
    fun startSleepTimer(durationMinutes: Int, playLastSong: Boolean) {
        runtime.startSleepTimer(durationMinutes, playLastSong)
    }

    /** 取消定时关闭。 */
    fun cancelSleepTimer() {
        runtime.cancelSleepTimer()
    }

    /** Enables or disables global uniform random playback. */
    fun setGlobalUniformRandomEnabled(enabled: Boolean) {
        runtime.setGlobalUniformRandomEnabled(enabled)
    }

    fun setDailyListeningGoalMinutes(minutes: Int) {
        runtime.setDailyListeningGoalMinutes(minutes)
    }

    fun initializeBluetoothPlayback() {
        runtime.initializeBluetoothPlayback()
    }

    fun releaseBluetoothPlayback() {
        runtime.releaseBluetoothPlayback()
    }

    fun setPlaybackNotificationEnabled(enabled: Boolean) {
        runtime.setPlaybackNotificationEnabled(enabled)
    }

    override fun onCleared() {
        super.onCleared()
        runtime.onCleared()
    }
}
