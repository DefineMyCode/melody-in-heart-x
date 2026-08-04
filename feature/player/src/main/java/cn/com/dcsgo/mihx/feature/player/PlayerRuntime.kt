package cn.com.dcsgo.mihx.feature.player

import android.net.Uri
import cn.com.dcsgo.mihx.core.common.AppLog
import cn.com.dcsgo.mihx.core.common.CoroutineDispatchers
import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Playlist
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.playback.BluetoothPlaybackMonitorFactory
import cn.com.dcsgo.mihx.domain.playback.ControllerPlaybackStateSynchronizer
import cn.com.dcsgo.mihx.domain.playback.PlaybackDurationMonitorFactory
import cn.com.dcsgo.mihx.domain.model.DeleteSongResult
import cn.com.dcsgo.mihx.domain.playback.ControllerQueuePlannerPort
import cn.com.dcsgo.mihx.domain.playback.PlaybackControllerPortFactory
import cn.com.dcsgo.mihx.domain.playback.PlaybackStateStorageFactory
import cn.com.dcsgo.mihx.domain.playback.PlayerQueueServicesFactory
import cn.com.dcsgo.mihx.domain.repository.AlbumArtRepository
import cn.com.dcsgo.mihx.domain.repository.PlayStatsRepository
import cn.com.dcsgo.mihx.domain.repository.PlayerSettingsRepository
import cn.com.dcsgo.mihx.domain.repository.SongRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "PlayerViewModel"

class PlayerRuntimeFactory @Inject constructor(
    private val songRepository: SongRepository,
    private val albumArtRepository: AlbumArtRepository,
    private val playStatsRepository: PlayStatsRepository,
    private val playerSettingsRepository: PlayerSettingsRepository,
    private val controllerQueuePlanner: ControllerQueuePlannerPort,
    private val playbackControllerPortFactory: PlaybackControllerPortFactory,
    private val playbackDurationMonitorFactory: PlaybackDurationMonitorFactory,
    private val playbackStateStorageFactory: PlaybackStateStorageFactory,
    private val bluetoothPlaybackMonitorFactory: BluetoothPlaybackMonitorFactory,
    private val playerQueueServicesFactory: PlayerQueueServicesFactory,
    private val dispatchers: CoroutineDispatchers,
) {
    internal fun create(scope: CoroutineScope): PlayerRuntime {
        return PlayerRuntime(
            scope = scope,
            songRepository = songRepository,
            albumArtRepository = albumArtRepository,
            playStatsRepository = playStatsRepository,
            playerSettingsRepository = playerSettingsRepository,
            controllerQueuePlanner = controllerQueuePlanner,
            playbackControllerPortFactory = playbackControllerPortFactory,
            playbackDurationMonitorFactory = playbackDurationMonitorFactory,
            playbackStateStorageFactory = playbackStateStorageFactory,
            bluetoothPlaybackMonitorFactory = bluetoothPlaybackMonitorFactory,
            playerQueueServicesFactory = playerQueueServicesFactory,
            dispatchers = dispatchers,
        )
    }
}

internal class PlayerRuntime(
    private val scope: CoroutineScope,
    private val songRepository: SongRepository,
    private val albumArtRepository: AlbumArtRepository,
    val playStatsRepository: PlayStatsRepository,
    private val playerSettingsRepository: PlayerSettingsRepository,
    private val controllerQueuePlanner: ControllerQueuePlannerPort,
    private val playbackControllerPortFactory: PlaybackControllerPortFactory,
    private val playbackDurationMonitorFactory: PlaybackDurationMonitorFactory,
    private val playbackStateStorageFactory: PlaybackStateStorageFactory,
    private val bluetoothPlaybackMonitorFactory: BluetoothPlaybackMonitorFactory,
    private val playerQueueServicesFactory: PlayerQueueServicesFactory,
    private val dispatchers: CoroutineDispatchers,
) {
    private val mediaControllerGraph: PlayerMediaControllerGraph by lazy {
        PlayerMediaControllerGraph(
            playbackControllerPortFactory = playbackControllerPortFactory,
            controllerStateAdapter = controllerStateAdapter,
            handleMediaItemEnded = mediaEventFacade::handleMediaItemEnded,
            handlePlaybackEnded = mediaEventFacade::handlePlaybackEnded,
        )
    }
    private val playbackController by lazy { mediaControllerGraph.playbackController }
    private val queueGraph = PlayerQueueGraph(
        playerQueueServicesFactory = playerQueueServicesFactory,
    )
    private val importCoordinator get() = queueGraph.importCoordinator
    private val playlistManager get() = queueGraph.playlistManager
    private val songGroupCoordinator get() = queueGraph.songGroupCoordinator
    private val songDeletionCoordinator get() = queueGraph.songDeletionCoordinator
    private val quickSkipCoordinator get() = queueGraph.quickSkipCoordinator
    private val playOrderBuilder get() = queueGraph.playOrderBuilder
    private val controllerStateSynchronizer = ControllerPlaybackStateSynchronizer()

    private var trackedSongId: Int? = null

    private val playDurationTracker = playbackDurationMonitorFactory.create()

    private val bluetoothGraph by lazy {
        PlayerBluetoothGraph(
            bluetoothPlaybackMonitorFactory = bluetoothPlaybackMonitorFactory,
            isPlaying = { _uiState.value.isPlaying },
            pausePlayback = { playbackBridgeFacade.pausePlayback() },
        )
    }

    private val persistenceGraph by lazy {
        PlayerPersistenceGraph(
            playbackStateStorageFactory = playbackStateStorageFactory,
            scope = scope,
            dispatchers = dispatchers,
            state = { _uiState.value },
            updateState = { transform -> _uiState.update(transform) },
            syncPlaybackState = { mediaControllerGraph.syncCurrentPlaybackState() },
            currentPlaybackPositionMs = { playbackBridgeFacade.currentPlaybackPositionMs() },
            prepareControllerQueue = { queue, index, positionMs ->
                playbackBridgeFacade.prepareControllerQueue(queue, index, positionMs)
            },
            log = { message -> AppLog.debug(TAG, message) },
        )
    }

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    val playQueue: PlayQueue get() = _uiState.value.playQueue
    val currentSong: Song? get() = _uiState.value.currentSong
    val currentPlayMode: PlayMode get() = _uiState.value.playQueue.playMode

    private val playbackProgressTicker = PlayerPlaybackProgressTicker(
        scope = scope,
        isPlaying = { _uiState.value.isPlaying },
        currentPositionMs = { playbackBridgeFacade.currentPlaybackPositionMs() },
        updatePosition = { positionMs ->
            _uiState.update { it.copy(currentPositionMs = positionMs) }
            persistenceGraph.onPlaybackPosition(positionMs)
        },
    )

    private val libraryFacade = PlayerLibraryFacade(
        updateState = { transform -> _uiState.update(transform) },
        loadPersistedSongs = { songRepository.loadSongs() },
        loadLibraryCatalog = {
            songRepository.loadLibraryArtists() to songRepository.loadLibraryAlbums()
        },
        refreshAllAlbumArt = { onFinished ->
            albumArtRepository.refreshAllAlbumArt()
            onFinished?.invoke()
        },
        snapshot = playlistManager::snapshot,
        setSongsChangedListener = songRepository::setSongsChangedListener,
        catalogScope = scope,
    )
    private val importFacade = PlayerImportFacade(
        updateState = { transform -> _uiState.update(transform) },
        importer = importCoordinator,
        launch = { task -> scope.launch { task() } },
    )
    private val playlistFacade = PlayerPlaylistFacade(
        state = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
        playlistManager = playlistManager,
    )
    private val songDeletionFacade = PlayerSongDeletionFacade(
        songDeletionCoordinator = songDeletionCoordinator,
        removeFromPlayQueue = ::removeFromPlayQueue,
        refreshPlaylists = playlistFacade::refresh,
    )
    private val quickSkipFacade = PlayerQuickSkipFacade(
        quickSkipActions = quickSkipCoordinator,
        refreshPlaylists = playlistFacade::refresh,
        launch = { task -> scope.launch { task() } },
    )
    private val controllerQueueFacade = PlayerControllerQueueFacade(
        state = { _uiState.value },
        controllerQueueInfo = { mediaControllerGraph.controllerQueueInfo() },
        clearPlaylist = { playbackController.clearPlaylist() },
        syncQueue = { plan -> playbackController.syncQueue(plan) },
        controllerQueuePlanner = controllerQueuePlanner,
    )
    private val queueFacade = PlayerQueueFacade(
        state = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
        playFromQueue = { queue, index -> playbackBridgeFacade.playFromQueue(queue, index) },
        syncPlayerQueue = { queue -> playbackBridgeFacade.syncPlayerQueue(queue) },
        clearControllerPlaylist = { playbackBridgeFacade.clearControllerPlaylist() },
        clearPlaybackState = ::clearPlaybackState,
        savePlaybackState = ::savePlaybackStateAsync,
        log = { message -> AppLog.debug(TAG, message) },
        planner = queueGraph.queueActionPlanner,
    )
    private val controllerStateFacade = PlayerControllerStateFacade(
        state = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
        synchronizer = controllerStateSynchronizer,
        trackedSongId = { trackedSongId },
        setTrackedSongId = { trackedSongId = it },
        updateDuration = playDurationTracker::updateDuration,
        startPlayback = playDurationTracker::startPlayback,
        pausePlaybackTracking = playDurationTracker::pausePlayback,
        resumePlaybackTracking = playDurationTracker::resumePlayback,
        savePlaybackState = ::savePlaybackStateAsync,
        onIsPlayingChanged = playbackProgressTicker::updateRunningState,
        onControllerPlaybackSynced = { synced ->
            if (!synced.playQueue.isEmpty &&
                !_uiState.value.isInfinitePlay &&
                playbackBridgeFacade.remainingMediaItems() <= PlayerMediaEventFacade.DEFAULT_REFILL_THRESHOLD
            ) {
                playbackBridgeFacade.syncPlayerQueue(synced.playQueue)
            }
        },
    )
    private val controllerStateAdapter = PlayerControllerStateAdapter(
        syncControllerPlaybackState = controllerStateFacade::syncControllerPlaybackState,
        handleControllerIsPlayingChanged = controllerStateFacade::handleControllerIsPlayingChanged,
    )
    private val playbackFacade = PlayerPlaybackFacade(
        state = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
        hasCurrentMediaItem = { playbackController.hasCurrentMediaItem },
        setPlayQueue = { songs, startIndex -> setPlayQueue(songs, startIndex) },
        playQueueItem = ::playQueueItem,
        playFromQueue = { queue, index -> playbackBridgeFacade.playFromQueue(queue, index) },
        playSingle = { song -> playbackBridgeFacade.startControllerSinglePlayback(song) },
        pausePlayback = { playbackBridgeFacade.pausePlayback() },
        resumePlayback = { playbackBridgeFacade.resumePlayback() },
        playPreviousInController = { playbackController.playPrevious() },
        playNextInController = { playbackController.playNext() },
        remainingMediaItems = { playbackBridgeFacade.remainingMediaItems() },
        refillInfinitePlayQueue = { playbackBridgeFacade.refillInfinitePlayQueue() },
        log = { message -> AppLog.debug(TAG, message) },
    )
    private val mediaEventFacade = PlayerMediaEventFacade(
        state = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
        stopPlaybackTracking = playDurationTracker::stopPlayback,
        clearTrackedSong = { trackedSongId = null },
        remainingMediaItems = { playbackBridgeFacade.remainingMediaItems() },
        refillInfinitePlayQueue = { startedSongId -> playbackBridgeFacade.refillInfinitePlayQueue(startedSongId) },
        syncPlayerQueue = { queue -> playbackBridgeFacade.syncPlayerQueue(queue) },
        log = { message -> AppLog.debug(TAG, message) },
        playOrderBuilder = playOrderBuilder,
    )
    private val versionFacade = PlayerVersionFacade(
        state = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
        songGroupCoordinator = songGroupCoordinator,
        savePlaybackState = ::savePlaybackStateAsync,
        playFromQueue = { queue, index -> playbackBridgeFacade.playFromQueue(queue, index) },
        playNext = ::playNext,
        playOrderBuilder = playOrderBuilder,
    )
    private val playbackSessionGraph by lazy {
        PlayerPlaybackSessionGraph(
            scope = scope,
            controller = playbackController,
            durationTracker = playDurationTracker,
            state = { _uiState.value },
            updateState = { transform -> _uiState.update(transform) },
            setTrackedSongId = { trackedSongId = it },
            updateSameNameSongs = versionFacade::updateSameNameSongs,
            savePlaybackState = ::savePlaybackStateAsync,
            onPlaybackStateChanged = playbackProgressTicker::updateRunningState,
            planControllerQueue = controllerQueuePlanner::plan,
        )
    }
    private val randomQueueFacade = PlayerRandomQueueFacade(
        state = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
        setPlayQueue = { songs, startIndex, mode -> setPlayQueue(songs, startIndex, mode) },
        syncPlayerQueue = { queue -> playbackBridgeFacade.syncPlayerQueue(queue) },
        rawPlayCounts = playStatsRepository::getRawPlayCounts,
        log = { message -> AppLog.debug(TAG, message) },
    )
    private val lifecycleFacade = PlayerLifecycleFacade(
        startMediaSessionService = { mediaControllerGraph.startService() },
        syncControllerPlaybackState = { mediaControllerGraph.syncCurrentPlaybackState() },
        savePlaybackState = ::savePlaybackState,
        releasePlaybackController = { mediaControllerGraph.release() },
        releaseBluetoothPlayback = bluetoothGraph::release,
        releasePlayDurationTracker = playDurationTracker::release,
        stopPlaybackProgressTicker = playbackProgressTicker::stop,
        logInfo = { message -> mediaControllerGraph.logInfo(message) },
        logError = { message, error -> mediaControllerGraph.logError(message, error) },
    )
    private val errorFacade = PlayerErrorFacade(
        updateState = { transform -> _uiState.update(transform) },
        startQueuePlayback = playbackSessionGraph::startQueuePlayback,
    )
    private val playbackBridgeFacade: PlayerPlaybackBridgeFacade by lazy {
        PlayerPlaybackBridgeFacade(
            remainingMediaItems = controllerQueueFacade::remainingMediaItems,
            pausePlayback = playbackSessionGraph::pausePlayback,
            resumePlayback = playbackSessionGraph::resumePlayback,
            currentPlaybackPositionMs = playbackSessionGraph::currentPlaybackPositionMs,
            clearControllerPlaylist = controllerQueueFacade::clearControllerPlaylist,
            startControllerQueuePlayback = playbackSessionGraph::startQueuePlayback,
            prepareControllerQueue = playbackSessionGraph::prepareQueue,
            startControllerSinglePlayback = playbackSessionGraph::startSinglePlayback,
            playFromQueue = errorFacade::playFromQueue,
            syncPlayerQueue = controllerQueueFacade::syncPlayerQueue,
            refillInfinitePlayQueue = { startedSongId -> randomQueueFacade.refillInfinitePlayQueue(startedSongId) },
        )
    }
    private val startupFacade = PlayerStartupFacade(
        startService = lifecycleFacade::startService,
        connectMediaController = { mediaControllerGraph.connect() },
        loadInitialData = ::loadInitialData,
        listenForSongChanges = libraryFacade::listenForSongChanges,
        restorePlaybackState = ::restorePlaybackState,
    )

    fun start() {
        val globalUniformRandomEnabled = playerSettingsRepository.currentGlobalUniformRandomEnabled()
        val bluetoothPlaybackMonitoringEnabled = playerSettingsRepository.currentBluetoothPlaybackMonitoringEnabled()
        val playbackNotificationEnabled = playerSettingsRepository.currentPlaybackNotificationEnabled()
        _uiState.update {
            it.copy(
                globalUniformRandomEnabled = globalUniformRandomEnabled,
                bluetoothPlaybackMonitoringEnabled = bluetoothPlaybackMonitoringEnabled,
                playbackNotificationEnabled = playbackNotificationEnabled,
            )
        }
        startupFacade.start()
        if (bluetoothPlaybackMonitoringEnabled) {
            bluetoothGraph.initialize()
        }
    }

    fun getQuickSkipSongs(): List<Song> {
        return quickSkipFacade.getQuickSkipSongs()
    }

    fun addToQuickSkipSongs(songId: Int) {
        quickSkipFacade.addToQuickSkipSongs(songId)
    }

    fun removeFromQuickSkipSongs(songId: Int) {
        quickSkipFacade.removeFromQuickSkipSongs(songId)
    }

    fun isInQuickSkipSongs(songId: Int): Boolean {
        return quickSkipFacade.isInQuickSkipSongs(songId)
    }

    fun syncQuickSkipSongsToPlaylist() {
        quickSkipFacade.syncQuickSkipSongsToPlaylist()
    }

    fun switchToVersion(targetSong: Song) {
        versionFacade.switchToVersion(targetSong)
    }

    fun getGroupedSongs(songs: List<Song>): List<List<Song>> {
        return versionFacade.getGroupedSongs(songs)
    }

    fun getSongsWithSameName(song: Song, songs: List<Song>): List<Song> {
        return versionFacade.getSongsWithSameName(song, songs)
    }

    fun setPlayQueue(
        songs: List<Song>,
        startIndex: Int = 0,
        mode: PlayMode? = null,
        exitInfinitePlay: Boolean = true,
    ) {
        queueFacade.setPlayQueue(songs, startIndex, mode, exitInfinitePlay)
    }

    /**
     * 关联播放：清空播放队列，仅保留当前歌曲，再把其关联的专辑歌曲 + 关联歌手的所有歌曲，
     * 去重后随机追加到队列。不改变无限随机播放状态。
     *
     * @return 实际追加的关联歌曲数量
     */
    fun playRelatedSongs(currentSong: Song): Int {
        val allSongs = _uiState.value.songs
        val related = allSongs
            .filter { it.id != currentSong.id }
            .filter { song ->
                val sameAlbum = currentSong.album.isNotBlank() && song.album == currentSong.album
                val sharedArtist = song.parsedArtists.any { artist -> artist in currentSong.parsedArtists }
                sameAlbum || sharedArtist
            }
            .distinctBy { it.id }
            .shuffled()

        setPlayQueue(
            songs = listOf(currentSong) + related,
            startIndex = 0,
            mode = PlayMode.SEQUENTIAL,
            // 不退出无限随机播放
            exitInfinitePlay = false,
        )
        // 若处于无限随机播放，同步新队列的已播放记录，保证后续补充从队列外歌曲开始
        if (_uiState.value.isInfinitePlay) {
            _uiState.update {
                it.copy(
                    infinitePlayedSongIds = (listOf(currentSong) + related)
                        .map { song -> song.id }
                        .toSet(),
                )
            }
        }
        return related.size
    }

    fun addToPlayQueue(song: Song): Boolean {
        return queueFacade.addToPlayQueue(song)
    }

    fun addSongToNextPlay(song: Song) {
        queueFacade.addSongToNextPlay(song)
    }

    fun addSongsToNextPlay(songs: List<Song>): Int {
        return queueFacade.addSongsToNextPlay(songs)
    }

    fun addToPlayQueue(songs: List<Song>): Int {
        return queueFacade.addToPlayQueue(songs)
    }

    fun removeFromPlayQueue(songId: Int) {
        queueFacade.removeFromPlayQueue(songId)
    }

    fun removeFromPlayQueueAt(index: Int) {
        queueFacade.removeFromPlayQueueAt(index)
    }

    fun clearPlayQueue() {
        queueFacade.clearPlayQueue()
    }

    fun togglePlayMode() {
        queueFacade.togglePlayMode()
    }

    fun setPlayMode(mode: PlayMode) {
        queueFacade.setPlayMode(mode)
    }

    fun playQueueItem(index: Int): Boolean {
        return queueFacade.playQueueItem(index)
    }

    fun playSong(song: Song) {
        playbackFacade.playSong(song)
    }

    fun playSongFromContext(song: Song, contextSongs: List<Song>) {
        playbackFacade.playSongFromContext(song, contextSongs)
    }

    fun togglePlayPause() {
        playbackFacade.togglePlayPause()
    }

    fun playPrevious() {
        playbackFacade.playPrevious()
    }

    fun playNext() {
        playbackFacade.playNext()
    }

    fun startSeeking() {
        playbackSessionGraph.startSeeking()
    }

    fun endSeeking(positionMs: Long) {
        playbackSessionGraph.endSeeking(positionMs)
    }

    fun seekTo(positionMs: Long) {
        playbackSessionGraph.seekTo(positionMs)
    }

    fun clearError() {
        errorFacade.clearError()
    }

    fun importFolder(treeUri: Uri, onResult: (Int) -> Unit) {
        importFacade.importFolderAsync(treeUri, onResult)
    }

    fun getSongsByPlaylist(playlist: Playlist): List<Song> {
        return playlistFacade.getSongsByPlaylist(playlist)
    }

    fun createPlaylist(name: String): Playlist? {
        return playlistFacade.createPlaylist(name)
    }

    fun deletePlaylist(playlistId: Int) {
        playlistFacade.deletePlaylist(playlistId)
    }

    fun renamePlaylist(playlistId: Int, newName: String): Boolean {
        return playlistFacade.renamePlaylist(playlistId, newName)
    }

    fun addSongToPlaylist(playlistId: Int, songId: Int): Boolean {
        return playlistFacade.addSongToPlaylist(playlistId, songId)
    }

    fun removeSongFromPlaylist(playlistId: Int, songId: Int) {
        playlistFacade.removeSongFromPlaylist(playlistId, songId)
    }

    fun isSongInPlaylist(playlistId: Int, songId: Int): Boolean {
        return playlistFacade.isSongInPlaylist(playlistId, songId)
    }

    fun deleteSong(songId: Int): DeleteSongResult {
        return songDeletionFacade.deleteSong(songId)
    }

    fun detachSongFromGroup(song: Song): Boolean {
        return versionFacade.detachSongFromGroup(song)
    }

    fun reassignSongToGroup(song: Song, targetSong: Song): Boolean {
        return versionFacade.reassignSongToGroup(song, targetSong)
    }

    fun resetSongGroupKey(song: Song): Boolean {
        return versionFacade.resetSongGroupKey(song)
    }

    fun playRandomQueue() {
        randomQueueFacade.playRandomQueue()
    }

    fun startInfinitePlay() {
        randomQueueFacade.startInfinitePlay()
    }

    fun stopInfinitePlay() {
        randomQueueFacade.stopInfinitePlay()
    }

    fun setGlobalUniformRandomEnabled(enabled: Boolean) {
        playerSettingsRepository.setGlobalUniformRandomEnabledBlocking(enabled)
        _uiState.update { it.copy(globalUniformRandomEnabled = enabled) }
    }

    fun initializeBluetoothPlayback() {
        playerSettingsRepository.setBluetoothPlaybackMonitoringEnabledBlocking(true)
        _uiState.update { it.copy(bluetoothPlaybackMonitoringEnabled = true) }
        bluetoothGraph.initialize()
    }

    fun releaseBluetoothPlayback() {
        playerSettingsRepository.setBluetoothPlaybackMonitoringEnabledBlocking(false)
        _uiState.update { it.copy(bluetoothPlaybackMonitoringEnabled = false) }
        bluetoothGraph.release()
    }

    fun setPlaybackNotificationEnabled(enabled: Boolean) {
        playerSettingsRepository.setPlaybackNotificationEnabledBlocking(enabled)
        _uiState.update { it.copy(playbackNotificationEnabled = enabled) }
    }

    fun onCleared() {
        lifecycleFacade.onCleared()
    }

    private fun loadInitialData(afterInitialSnapshot: () -> Unit) {
        scope.launch {
            libraryFacade.loadInitialData(afterInitialSnapshot)
        }
    }

    private fun savePlaybackStateAsync() {
        persistenceGraph.savePlaybackStateAsync()
    }

    private fun savePlaybackState(positionMs: Long = playbackBridgeFacade.currentPlaybackPositionMs()) {
        persistenceGraph.savePlaybackState(positionMs)
    }

    private fun clearPlaybackState() {
        persistenceGraph.clearPlaybackState()
    }

    private fun restorePlaybackState() {
        persistenceGraph.restorePlaybackState()
    }
}
