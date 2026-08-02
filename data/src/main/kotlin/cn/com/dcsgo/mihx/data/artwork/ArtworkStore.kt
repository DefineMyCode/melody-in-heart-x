package cn.com.dcsgo.mihx.data.artwork

/**
 * Extracts embedded album art from an audio file, downscales it to a bounded size, caches it on
 * disk and returns a `content://` URI that both Coil (in-app UI) and Media3 (lock-screen /
 * notification) can load across processes. The cache lives under `cacheDir/artwork` and is
 * therefore never backed up (see app `backup_rules.xml` note). Plan 5B.
 */
interface ArtworkStore {
    /**
     * Returns a loadable artwork URI for [songId], or null when the audio file has no embedded
     * picture. [audioUri] is the song's content URI; [songId] names the cached file so it can be
     * refreshed if the source changes.
     */
    suspend fun extractAndCache(audioUri: String, songId: Long): String?
}

/**
 * Authority of the FileProvider that serves [ArtworkStore] cached files. Keep in sync with the
 * `<provider android:authorities>` entry in `app/src/main/AndroidManifest.xml`.
 */
const val ARTWORK_FILE_PROVIDER_AUTHORITY = "cn.com.dcsgo.mihx.artworkprovider"
