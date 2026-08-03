package cn.com.dcsgo.mihx.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import cn.com.dcsgo.mihx.core.common.AppLog
import cn.com.dcsgo.mihx.core.common.PerformanceTrace
import cn.com.dcsgo.mihx.core.model.AlbumEntry
import cn.com.dcsgo.mihx.core.model.ArtistEntry
import cn.com.dcsgo.mihx.core.model.LibraryCatalog
import cn.com.dcsgo.mihx.core.model.Playlist
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.data.local.dao.MelodyDao
import cn.com.dcsgo.mihx.data.local.migration.LegacyJsonMigration
import cn.com.dcsgo.mihx.data.util.AlbumArtExtractor
import cn.com.dcsgo.mihx.data.util.AudioFileUtils
import cn.com.dcsgo.mihx.data.util.AudioMetadataExtractor
import cn.com.dcsgo.mihx.domain.model.DeleteSongResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

private const val TAG = "MusicRepository"
private const val PREFS_NAME = "music_player_prefs"
private const val KEY_PLAYLISTS_JSON = "playlists_json"
private const val KEY_SONGS_JSON = "songs_json"

private sealed class BackingFileDeleteResult {
    data object Deleted : BackingFileDeleteResult()
    data object AlreadyMissing : BackingFileDeleteResult()
    data class Failure(val reason: String) : BackingFileDeleteResult()
}

class MusicRepository(
    private val context: Context? = null,
    private val legacyJsonMigration: LegacyJsonMigration? = null,
    private val melodyDao: MelodyDao? = null,
) {

    private val songs = mutableListOf<Song>()
    private val playlists = mutableListOf<Playlist>()
    private var nextId = 1
    private var nextPlaylistId = 1
    private val roomDataSource = melodyDao?.let(::RoomMusicLibraryDataSource)

    // 读写锁：保护 songs / playlists / nextId / nextPlaylistId 的并发访问。
    // 导入（IO 线程写）与 Compose recompose（主线程读）并发时防止 ConcurrentModificationException。
    private val lock = ReentrantReadWriteLock()

    // SharedPreferences 用于持久化歌单数据
    private val prefs: SharedPreferences? by lazy {
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // 回调：歌曲列表变更通知
    var onSongsChanged: (() -> Unit)? = null

    fun setSongsChangedListener(listener: (() -> Unit)?) {
        onSongsChanged = listener
    }

    // ─────────────────────────────────────────────────────────────
    // 启动恢复：歌单数据直接序列化/反序列化
    // ─────────────────────────────────────────────────────────────

    /** 启动时恢复歌单和歌曲（从 JSON 持久化数据） */
    suspend fun loadPersistedSongs() {
        withContext(Dispatchers.IO) {
            try {
                legacyJsonMigration?.migrateIfNeeded()
                if (melodyDao != null) {
                    restoreFromRoom(melodyDao)
                } else {
                    restoreSongs()
                    restorePlaylists()
                }
            } catch (e: Exception) {
                AppLog.error(TAG, "loadPersistedSongs: failed: ${e.message}", e)
            }
        }
    }

    suspend fun loadSongs(): List<Song> {
        loadPersistedSongs()
        return getSongs()
    }

    private suspend fun restoreFromRoom(dao: MelodyDao) {
        val restoredLibrary = RoomMusicLibraryDataSource(dao).restore()

        lock.write {
            songs.clear()
            songs.addAll(restoredLibrary.songs)
            playlists.clear()
            playlists.addAll(restoredLibrary.playlists)
            nextId = (songs.maxOfOrNull { it.id } ?: 0) + 1
            nextPlaylistId = (playlists.maxOfOrNull { it.id } ?: 0) + 1
        }
        AppLog.info(TAG, "restoreFromRoom: restored ${restoredLibrary.songs.size} songs, ${restoredLibrary.playlists.size} playlists")
    }

    /**
     * 异步校验所有歌曲的封面缓存，缓存文件不存在的重新提取。
     * 调用方应在 UI 已显示数据后再调用此方法，避免阻塞启动。
     * @param onFinished 完成回调
     */
    suspend fun refreshAllAlbumArtAsync(onFinished: (() -> Unit)? = null) {
        withContext(Dispatchers.IO) {
            try {
                refreshAllAlbumArtInternal()
            } catch (e: Exception) {
                AppLog.error(TAG, "refreshAllAlbumArtAsync: failed: ${e.message}", e)
            }
        }
        onFinished?.invoke()
    }

    /** 从 JSON 恢复歌曲列表 */
    private fun restoreSongs() {
        val json = prefs?.getString(KEY_SONGS_JSON, null) ?: return
        try {
            val jsonArray = JSONArray(json)
            lock.write {
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val id = obj.getInt("id")
                    val title = obj.getString("title")
                    val artist = obj.optString("artist", "未知艺术家")
                    val sampleRate = if (obj.has("sampleRate")) obj.getInt("sampleRate") else 0
                    val uriString = obj.getString("uri")
                    val uri = Uri.parse(uriString)
                    val albumArtUriString = if (obj.has("albumArtUri")) obj.getString("albumArtUri") else null
                    val albumArtUri = albumArtUriString?.let { Uri.parse(it) }
                    val lrcUriString = if (obj.has("lrcUri")) obj.getString("lrcUri") else null
                    val lrcUri = lrcUriString?.let { Uri.parse(it) }
                    val titleOverride = if (obj.has("titleOverride")) obj.optString("titleOverride", "").ifEmpty { null } else null
                    val album = if (obj.has("album")) obj.optString("album", "") else ""

                    songs.add(Song(id = id, title = title, artist = artist, album = album, sampleRate = sampleRate, uri = uri, albumArtUri = albumArtUri, lrcUri = lrcUri, titleOverride = titleOverride))
                    if (id >= nextId) nextId = id + 1
                }
            }
            AppLog.info(TAG, "restoreSongs: restored ${songs.size} songs")
        } catch (e: Exception) {
            AppLog.error(TAG, "restoreSongs: JSON parse failed: ${e.message}", e)
        }
    }

    /** 持久化所有歌曲数据到 JSON */
    private fun persistSongs() {
        val snapshot: List<Song>
        lock.read { snapshot = songs.toList() }
        val room = roomDataSource
        if (room != null) {
            val now = System.currentTimeMillis()
            runBlocking(Dispatchers.IO) {
                room.persistSongs(snapshot, importedAt = now)
            }
            AppLog.debug(TAG, "persistSongs: saved ${snapshot.size} songs to Room")
            return
        }
        val jsonArray = JSONArray()
        for (song in snapshot) {
            val obj = JSONObject()
            obj.put("id", song.id)
            obj.put("title", song.title)
            obj.put("artist", song.artist)
            obj.put("album", song.album)
            obj.put("sampleRate", song.sampleRate)
            obj.put("uri", song.uri.toString())
            song.albumArtUri?.let { obj.put("albumArtUri", it.toString()) }
            song.lrcUri?.let { obj.put("lrcUri", it.toString()) }
            song.titleOverride?.let { obj.put("titleOverride", it) }
            jsonArray.put(obj)
        }
        prefs?.edit()?.putString(KEY_SONGS_JSON, jsonArray.toString())?.apply()
        AppLog.debug(TAG, "persistSongs: saved ${snapshot.size} songs")
    }

    /** 从 JSON 恢复歌单（不依赖 folder name 解析） */
    private fun restorePlaylists() {
        val json = prefs?.getString(KEY_PLAYLISTS_JSON, null) ?: return
        try {
            val jsonArray = JSONArray(json)
            lock.write {
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val id = obj.getInt("id")
                    val name = obj.getString("name")
                    val songIdsArray = obj.getJSONArray("songIds")
                    val songIds = mutableListOf<Int>()
                    for (j in 0 until songIdsArray.length()) {
                        songIds.add(songIdsArray.getInt(j))
                    }
                    playlists.add(Playlist(id = id, name = name, songCount = songIds.size, songIds = songIds))
                    if (id >= nextPlaylistId) nextPlaylistId = id + 1
                }
            }
            AppLog.info(TAG, "restorePlaylists: restored ${playlists.size} playlists")
        } catch (e: Exception) {
            AppLog.error(TAG, "restorePlaylists: JSON parse failed: ${e.message}", e)
        }
    }

    /** 持久化所有歌单数据到 JSON */
    private fun persistPlaylists() {
        val snapshot: List<Playlist>
        lock.read { snapshot = playlists.toList() }
        val room = roomDataSource
        if (room != null) {
            val now = System.currentTimeMillis()
            runBlocking(Dispatchers.IO) {
                room.persistPlaylists(snapshot, updatedAt = now)
            }
            AppLog.debug(TAG, "persistPlaylists: saved ${snapshot.size} playlists to Room")
            return
        }
        val jsonArray = JSONArray()
        for (playlist in snapshot) {
            val obj = JSONObject()
            obj.put("id", playlist.id)
            obj.put("name", playlist.name)
            val songIdsArray = JSONArray()
            for (songId in playlist.songIds) {
                songIdsArray.put(songId)
            }
            obj.put("songIds", songIdsArray)
            jsonArray.put(obj)
        }
        prefs?.edit()?.putString(KEY_PLAYLISTS_JSON, jsonArray.toString())?.apply()
        AppLog.debug(TAG, "persistPlaylists: saved ${snapshot.size} playlists")
    }

    fun getSongs(): List<Song> = lock.read { songs.toList() }

    fun observeSongsSnapshot(): List<Song> = getSongs()

    /**
     * 从持久化的 artists 表查询歌手目录（按歌手名排序）。
     * 无 Room 时回退到内存歌曲派生。
     */
    suspend fun loadLibraryArtists(): List<ArtistEntry> {
        val dao = melodyDao ?: return LibraryCatalog.deriveArtists(getSongs())
        val rows = dao.artistCatalog()
        return rows.map { row ->
            ArtistEntry(
                name = row.name,
                songCount = row.songCount,
                albumCount = row.albumCount,
                coverUri = row.coverUri?.let(Uri::parse),
            )
        }
    }

    /**
     * 从持久化的 albums 表查询专辑目录（按专辑名排序）。
     * 无 Room 时回退到内存歌曲派生。
     */
    suspend fun loadLibraryAlbums(): List<AlbumEntry> {
        val dao = melodyDao ?: return LibraryCatalog.deriveAlbums(getSongs())
        val rows = dao.albumCatalog()
        val albumArtistNames = dao.albumArtistNames()
            .groupBy { it.albumId }
            .mapValues { (_, names) -> names.map { it.artistName } }
        return rows.map { row ->
            AlbumEntry(
                name = row.name,
                artistNames = albumArtistNames[row.albumId].orEmpty(),
                songCount = row.songCount,
                coverUri = row.coverUri?.let(Uri::parse),
            )
        }
    }

    /**
     * 按歌曲标题分组（同名歌曲归为一组）。
     *
     * 返回列表中每组至少包含一首歌曲。
     * 分组用于歌单列表和本地音乐列表的 UI 展示，避免显示重复项。
     *
     * @return 按原始顺序排列的分组列表，每组第一个元素为组代表
     */
    fun getSongGroups(): List<List<Song>> = lock.read {
        songs.filter { it.uri != null }
            .groupBy { it.groupKey }
            .values
            .toList()
    }

    /**
     * 查找与指定歌曲同名（同 groupKey）的所有歌曲。
     *
     * @param song 目标歌曲
     * @return 同名的所有歌曲列表（包含自身）
     */
    fun getSongsWithSameName(song: Song): List<Song> = lock.read {
        songs.filter { it.groupKey == song.groupKey && it.uri != null }
    }

    /**
     * 校验所有歌曲的封面缓存，缓存文件不存在的重新提取。
     * 使用 Semaphore 控制并发，避免 IO 过载。
     */
    suspend fun refreshAllAlbumArt() {
        withContext(Dispatchers.IO) {
            refreshAllAlbumArtInternal()
        }
    }

    private fun refreshAllAlbumArtInternal() {
        val ctx = context ?: return
        if (lock.read { songs.isEmpty() }) return

        AppLog.info(TAG, "refreshAllAlbumArt: checking ${songs.size} songs...")
        var refreshed = 0

        // 串行逐个检查即可，启动时执行一次
        lock.write {
            for (song in songs) {
                val newUri = AlbumArtExtractor.refreshAlbumArtIfNeeded(ctx, song)
                if (newUri != null && newUri != song.albumArtUri) {
                    // 封面发生了变化（之前为 null 或缓存失效被重新提取）
                    val idx = songs.indexOfFirst { it.id == song.id }
                    if (idx >= 0) {
                        songs[idx] = song.copy(albumArtUri = newUri)
                        refreshed++
                    }
                } else if (newUri == null && song.albumArtUri != null) {
                    // 之前有封面但现在提取不到了，清除无效引用
                    val idx = songs.indexOfFirst { it.id == song.id }
                    if (idx >= 0) {
                        songs[idx] = song.copy(albumArtUri = null)
                    }
                }
            }
        }

        if (refreshed > 0) {
            persistSongs()
            AppLog.info(TAG, "refreshAllAlbumArt: refreshed $refreshed covers")
        }
        // 通知 UI 刷新
        onSongsChanged?.invoke()
    }

    fun getPlaylists(): List<Playlist> = lock.read { playlists.toList() }

    // ─────────────────────────────────────────────────────────────
    // 导入整个文件夹（来自 OpenDocumentTree picker）
    // 支持 1000+ 文件，封面提取并行执行，不阻塞主线程
    // ─────────────────────────────────────────────────────────────
    /**
     * @param treeUri OpenDocumentTree 返回的 tree URI
     * @param onProgress 进度回调：(已处理数, 总数)
     * @return 实际新增的歌曲数量（已存在被跳过的不会计入）
     */
    suspend fun addFolder(
        treeUri: Uri,
        onProgress: ((processed: Int, total: Int) -> Unit)? = null
    ): Int = withContext(Dispatchers.IO) {
        val importStartedAt = PerformanceTrace.nowMs()
        val ctx = context ?: return@withContext 0

        // 持久化树形 URI 权限
        try {
            ctx.contentResolver.takePersistableUriPermission(
                treeUri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            AppLog.error(TAG, "addFolder: cannot persist permission", e)
            return@withContext 0
        }

        val rootDoc = DocumentFile.fromTreeUri(ctx, treeUri)
            ?: run { AppLog.error(TAG, "addFolder: cannot open selected tree"); return@withContext 0 }

        val folderName = rootDoc.name ?: treeUri.lastPathSegment ?: "新文件夹"
        AppLog.info(TAG, "addFolder: scanning selected folder")

        // 递归扫描音频和 LRC 文件（同步，很快）
        val audioFiles = mutableListOf<ScannedAudioFile>()
        val lrcFilesByKey = mutableMapOf<String, Uri>()
        val scanStartedAt = PerformanceTrace.nowMs()
        collectLibraryFiles(rootDoc, audioFiles, lrcFilesByKey, depth = 0, relativeDir = "")
        PerformanceTrace.log(
            operation = "music_import_scan",
            elapsedMs = PerformanceTrace.nowMs() - scanStartedAt,
            metadata = mapOf(
                "audioFileCount" to audioFiles.size,
                "lrcFileCount" to lrcFilesByKey.size,
            ),
        )
        AppLog.info(TAG, "addFolder: found ${audioFiles.size} audio files and ${lrcFilesByKey.size} lrc files")

        if (audioFiles.isEmpty()) return@withContext 0

        // 创建对应歌单（已在 IO 线程，无需额外同步，但锁内操作）
        val playlist = lock.write { createPlaylistInternal(folderName) }

        // 已存在 URI 集合，用于快速去重（O(1) 查找）
        val importUris = ConcurrentHashMap.newKeySet<Uri>().apply {
            addAll(lock.read { songs.mapNotNullTo(mutableSetOf()) { it.uri } })
        }

        // 封面提取并行度：最多同时 8 个，避免 ContentResolver 过载
        val semaphore = Semaphore(8)
        val processedCount = AtomicInteger(0)
        val addedCount = AtomicInteger(0)

        // 并行处理所有文件
        audioFiles.map { audioFile ->
            async(Dispatchers.IO) {
                semaphore.acquire()
                try {
                    val docFile = audioFile.file
                    val fileUri = docFile.uri

                    // 去重检查
                    if (!importUris.add(fileUri)) {
                        onProgress?.invoke(processedCount.incrementAndGet(), audioFiles.size)
                        return@async null
                    }

                    val displayName = docFile.name ?: "未知"
                    val fallbackTitle = AudioFileUtils.stripAudioExtension(displayName)

                    // 从音频元数据提取标题、艺术家、专辑和采样率
                    val metadata = AudioMetadataExtractor.extractMetadata(ctx, fileUri, fallbackTitle)

                    val songId: Int
                    val albumArtUri: Uri?

                    // 封面提取也在 IO 线程完成，避免主线程阻塞
                    lock.write { songId = nextId++ }
                    albumArtUri = AlbumArtExtractor.getAlbumArtUri(ctx, fileUri, songId)

                    val song = Song(
                        id = songId,
                        title = metadata.title,
                        artist = metadata.artist,
                        album = metadata.album,
                        sampleRate = metadata.sampleRate,
                        uri = fileUri,
                        albumArtUri = albumArtUri,
                        lrcUri = lrcFilesByKey[audioFile.matchKey],
                    )

                    lock.write {
                        songs.add(song)
                        addSongToPlaylistInternal(playlist.id, song.id)
                        processedCount.incrementAndGet()
                        addedCount.incrementAndGet()
                    }

                    onProgress?.invoke(processedCount.get(), audioFiles.size)
                    song
                } finally {
                    semaphore.release()
                }
            }
        }.awaitAll().filterNotNull()

        updatePlaylistSongCount(playlist.id)
        // 持久化歌曲和歌单数据
        persistSongs()
        persistPlaylists()
        val finalAddedCount = addedCount.get()
        AppLog.info(TAG, "addFolder: added $finalAddedCount songs (skipped ${audioFiles.size - finalAddedCount})")
        PerformanceTrace.log(
            operation = "music_import_folder",
            elapsedMs = PerformanceTrace.nowMs() - importStartedAt,
            metadata = mapOf(
                "audioFileCount" to audioFiles.size,
                "addedCount" to finalAddedCount,
                "skippedCount" to audioFiles.size - finalAddedCount,
            ),
        )
        finalAddedCount
    }

    suspend fun importFolder(
        treeUri: Uri,
        onProgress: (processed: Int, total: Int) -> Unit,
    ): Int = addFolder(treeUri, onProgress)

    /** 递归收集 DocumentFile 树中的音频和 LRC 文件，最多 3 层深度 */
    private fun collectLibraryFiles(
        dir: DocumentFile,
        audioFiles: MutableList<ScannedAudioFile>,
        lrcFilesByKey: MutableMap<String, Uri>,
        depth: Int,
        relativeDir: String,
    ) {
        if (depth > 3) return
        dir.listFiles().forEach { file ->
            val fileName = file.name
            when {
                file.isDirectory -> {
                    val childDir = listOf(relativeDir, fileName.orEmpty())
                        .filter { it.isNotBlank() }
                        .joinToString("/")
                    collectLibraryFiles(file, audioFiles, lrcFilesByKey, depth + 1, childDir)
                }
                file.isFile && AudioFileUtils.isAudioFile(file.type, fileName) -> {
                    audioFiles += ScannedAudioFile(file, lrcMatchKey(relativeDir, fileName))
                }
                file.isFile && fileName?.endsWith(".lrc", ignoreCase = true) == true -> {
                    lrcFilesByKey.putIfAbsent(lrcMatchKey(relativeDir, fileName), file.uri)
                }
            }
        }
    }

    private fun lrcMatchKey(relativeDir: String, fileName: String?): String {
        val baseName = fileName.orEmpty().substringBeforeLast(".").lowercase()
        return "$relativeDir/$baseName"
    }

    // ─────────────────────────────────────────────────────────────
    // 歌单管理
    // ─────────────────────────────────────────────────────────────

    fun createPlaylist(name: String): Playlist = lock.write {
        createPlaylistInternal(name)
    }

    /** 内部版本：不加锁，由调用方负责持有写锁 */
    private fun createPlaylistInternal(name: String): Playlist {
        // 防止重复创建同名歌单
        playlists.find { it.name == name }?.let { return it }
        val playlist = Playlist(id = nextPlaylistId++, name = name, songCount = 0)
        playlists.add(playlist)
        persistPlaylists()
        AppLog.info(TAG, "createPlaylist: id=${playlist.id}")
        return playlist
    }

    fun addSongToPlaylist(playlistId: Int, songId: Int): Boolean = lock.write {
        addSongToPlaylistInternal(playlistId, songId)
    }

    /** 内部版本：不加锁，由调用方负责持有写锁 */
    private fun addSongToPlaylistInternal(playlistId: Int, songId: Int): Boolean {
        val index = playlists.indexOfFirst { it.id == playlistId }
        if (index < 0) return false
        val playlist = playlists[index]
        if (playlist.songIds.contains(songId)) return false
        playlist.songIds.add(songId)
        updatePlaylistSongCount(playlistId)
        persistPlaylists()
        return true
    }

    /** 将歌曲从指定歌单中移除 */
    fun removeSongFromPlaylist(playlistId: Int, songId: Int): Boolean = lock.write {
        val index = playlists.indexOfFirst { it.id == playlistId }
        if (index < 0) return@write false
        val playlist = playlists[index]
        if (!playlist.songIds.remove(songId)) return@write false
        updatePlaylistSongCount(playlistId)
        persistPlaylists()
        AppLog.info(TAG, "removeSongFromPlaylist: song=$songId from playlist=$playlistId")
        true
    }

    /** 删除歌单（仅删除歌单本身，不删除歌曲） */
    fun deletePlaylist(playlistId: Int): Boolean = lock.write {
        val removed = playlists.removeIf { it.id == playlistId }
        if (removed) {
            persistPlaylists()
            AppLog.info(TAG, "deletePlaylist: id=$playlistId")
        }
        removed
    }

    /** 重命名歌单 */
    fun renamePlaylist(playlistId: Int, newName: String): Boolean = lock.write {
        val index = playlists.indexOfFirst { it.id == playlistId }
        if (index < 0) return@write false
        // 防止与其他歌单同名
        if (playlists.any { it.id != playlistId && it.name == newName }) return@write false
        val old = playlists[index]
        playlists[index] = old.copy(name = newName)
        persistPlaylists()
        AppLog.info(TAG, "renamePlaylist: id=$playlistId")
        true
    }

    /** 判断歌曲是否在指定歌单中 */
    fun isSongInPlaylist(playlistId: Int, songId: Int): Boolean = lock.read {
        val playlist = playlists.find { it.id == playlistId } ?: return@read false
        playlist.songIds.contains(songId)
    }

    fun getSongsByPlaylistId(playlistId: Int): List<Song> = lock.read {
        val playlist = playlists.find { it.id == playlistId } ?: return@read emptyList()
        playlist.songIds.mapNotNull { id -> songs.find { it.id == id } }
    }

    fun updatePlaylistSongCount(playlistId: Int) {
        // 注意：此方法通常在已持有写锁的上下文中调用，不额外加锁
        val index = playlists.indexOfFirst { it.id == playlistId }
        if (index < 0) return
        val old = playlists[index]
        playlists[index] = old.copy(songCount = old.songIds.size)
    }

    // ─────────────────────────────────────────────────────────────
    // 歌曲管理
    // ─────────────────────────────────────────────────────────────

    /**
     * 删除歌曲：
     * 1. 先删除 SAF 指向的物理文件
     * 2. 文件删除成功后，再从歌曲列表和所有歌单中移除
     * 3. 持久化更新后的数据
     */
    fun deleteSong(songId: Int): DeleteSongResult {
        val song = lock.read {
            songs.firstOrNull { it.id == songId }
        } ?: return DeleteSongResult.Failure("歌曲不存在或已被删除")

        val fileDeleteResult = deleteBackingFile(song)
        if (fileDeleteResult is BackingFileDeleteResult.Failure) {
            return DeleteSongResult.Failure(fileDeleteResult.reason)
        }

        return lock.write {
            val songIndex = songs.indexOfFirst { it.id == songId }
            if (songIndex < 0) return@write DeleteSongResult.Failure("歌曲不存在或已被删除")

            playlists.forEach { playlist ->
                if (playlist.songIds.remove(songId)) {
                    updatePlaylistSongCount(playlist.id)
                }
            }
            songs.removeAt(songIndex)

            persistSongs()
            persistPlaylists()
            AppLog.info(TAG, "deleteSong: deleted song id=$songId")
            DeleteSongResult.Success(
                song = song,
                message = when (fileDeleteResult) {
                    BackingFileDeleteResult.Deleted -> "已删除「${song.title}」"
                    BackingFileDeleteResult.AlreadyMissing -> "文件已不存在，已从列表移除「${song.title}」"
                    is BackingFileDeleteResult.Failure -> fileDeleteResult.reason
                }
            )
        }
    }

    private fun deleteBackingFile(song: Song): BackingFileDeleteResult {
        val uri = song.uri ?: return BackingFileDeleteResult.Failure("该歌曲没有可删除的本地文件")
        val ctx = context ?: return BackingFileDeleteResult.Failure("无法访问应用上下文，未删除文件")

        return try {
            val docFile = DocumentFile.fromSingleUri(ctx, uri)
                ?: return BackingFileDeleteResult.Failure("无法定位歌曲文件，未删除")
            if (!docFile.exists()) return BackingFileDeleteResult.AlreadyMissing
            if (!docFile.canWrite()) return BackingFileDeleteResult.Failure("没有权限删除该歌曲文件")

            if (docFile.delete()) {
                AppLog.info(TAG, "deleteBackingFile: deleted backing file")
                BackingFileDeleteResult.Deleted
            } else {
                BackingFileDeleteResult.Failure("系统拒绝删除该歌曲文件")
            }
        } catch (e: SecurityException) {
            AppLog.error(TAG, "deleteBackingFile: missing permission: ${e.message}", e)
            BackingFileDeleteResult.Failure("没有权限删除该歌曲文件")
        } catch (e: Exception) {
            AppLog.error(TAG, "deleteBackingFile: failed: ${e.message}", e)
            BackingFileDeleteResult.Failure("删除歌曲文件失败：${e.message ?: "未知错误"}")
        }
    }

    fun getFavoriteSongs(): List<Song> = lock.read {
        playlists.firstOrNull { it.name == "我的最爱" }
            ?.let { playlist ->
                playlist.songIds.mapNotNull { id -> songs.find { it.id == id } }
            }
            ?: emptyList()
    }

    // ─────────────────────────────────────────────────────────────
    // 版本归组管理
    // ─────────────────────────────────────────────────────────────

    /**
     * 更新歌曲的 titleOverride（版本归组键覆盖）。
     *
     * - 传入非 null 值：将该歌曲的 groupKey 覆盖为指定值，使其归属到对应分组。
     * - 传入 null：清除覆盖，恢复使用原始 title 作为 groupKey。
     *
     * @param songId        目标歌曲的 ID
     * @param titleOverride 新的分组键（null 表示清除覆盖）
     * @return true 表示修改成功
     */
    fun updateSongTitleOverride(songId: Int, titleOverride: String?): Boolean = lock.write {
        val index = songs.indexOfFirst { it.id == songId }
        if (index < 0) return@write false
        songs[index] = songs[index].copy(titleOverride = titleOverride)
        persistSongs()
        AppLog.info(TAG, "updateSongTitleOverride: song=$songId")
        true
    }

    private data class ScannedAudioFile(
        val file: DocumentFile,
        val matchKey: String,
    )
}
