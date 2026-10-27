package cn.com.dcsgo.mihx.data.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 专辑封面跨进程访问契约的回归（2026-10-27）。
 *
 * 背景：平台 `MediaSessionRecord.sanitizeMediaMetadata()` 只对 `content://` 的 artworkUri
 * 做 URI 授权共享，`file://` 被直接跳过 → 控制器读不到 App 私有目录 →
 * `MediaDataManager E Unable to load bitmap` + `FileNotFoundException`。
 * 本组用例锁住「本地文件 ↔ content:// URI」的双向映射，以及对外来 URI 的拒绝。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlbumArtProviderTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    /**
     * androidx.core 的 `FileProvider` 用静态 `sCache` 缓存「authority → 根目录」映射。
     * Robolectric 每条用例都是独立的临时目录，静态缓存一旦被上一条用例写入，
     * 后续用例的 `getUriForFile` 就会因为「文件不在已缓存的根目录下」抛
     * `Failed to find configured root`。这里反射清掉它（仅测试需要）。
     */
    @Before
    fun clearFileProviderRootCache() {
        val field = FileProvider::class.java.getDeclaredField("sCache")
        field.isAccessible = true
        (field.get(null) as MutableMap<*, *>).clear()
    }

    @Test
    fun `authority is package name plus suffix`() {
        assertEquals(ctx.packageName + ".artworkprovider", AlbumArtProvider.authority(ctx))
    }

    @Test
    fun `provider uri is a content uri under our authority`() {
        val file = cacheFile("art_scheme.jpg")

        val uri = AlbumArtProvider.toProviderUri(ctx, file)

        assertNotNull("FileProvider 应可用（:data 的 manifest 已声明 provider）", uri)
        assertEquals("content", uri!!.scheme)
        assertEquals(AlbumArtProvider.authority(ctx), uri.authority)
    }

    @Test
    fun `provider uri round trips back to the cache file`() {
        val file = cacheFile("art_roundtrip.jpg")

        val uri = AlbumArtProvider.toProviderUri(ctx, file)

        assertNotNull(uri)
        assertEquals(file, AlbumArtProvider.fileOf(ctx, uri!!))
    }

    @Test
    fun `media store album art uri is not mapped to a local file`() {
        val mediaStoreArt = Uri.parse("content://media/external/audio/albumart/42")

        assertNull(AlbumArtProvider.fileOf(ctx, mediaStoreArt))
        assertNull(AlbumArtProvider.legacyCacheFileOf(ctx, mediaStoreArt))
    }

    @Test
    fun `legacy file uri inside our cache dir is recognized`() {
        val file = cacheFile("art_legacy.jpg")

        assertEquals(file, AlbumArtProvider.legacyCacheFileOf(ctx, Uri.fromFile(file)))
    }

    @Test
    fun `legacy file uri outside our cache dir is rejected`() {
        val outside = File(ctx.filesDir, "not_album_art.jpg")
        outside.writeBytes(byteArrayOf(1, 2, 3))

        assertNull(AlbumArtProvider.legacyCacheFileOf(ctx, Uri.fromFile(outside)))
    }

    @Test
    fun `missing legacy file is still recognized so callers can clear the stale uri`() {
        val missing = File(AlbumArtProvider.cacheDir(ctx), "art_gone.jpg")

        // 文件不存在，但路径仍在自家缓存目录下 → 必须能被识别出来（由调用方决定摘掉悬空 URI）
        assertEquals(missing, AlbumArtProvider.legacyCacheFileOf(ctx, Uri.fromFile(missing)))
    }

    private fun cacheFile(name: String): File {
        val dir = AlbumArtProvider.cacheDir(ctx)
        dir.mkdirs()
        val file = File(dir, name)
        file.writeBytes(byteArrayOf(1, 2, 3))
        return file
    }
}
