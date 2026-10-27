package cn.com.dcsgo.mihx.data.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executors

/**
 * 封面缓存并发与原子性回归（评审 2026-10-27 · M-9）。
 *
 * 原先两个问题：
 * 1. **非原子落盘** —— 直接写最终名 `art_<hash>.jpg`，文件以最终名出现时可能还没写完，
 *    并发读者会读到**截断的 JPEG，且永远不自愈**（缓存文件不会被修复）。
 * 2. **同哈希重复劳动** —— 同一专辑的 N 首歌内嵌同一张封面字节 → 相同哈希 →
 *    同一个缓存文件。原先 N 个线程各自解码 + JPEG 压缩 + 覆写同一文件，
 *    恰好把「按内容哈希去重」想省的活又干了一遍。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AlbumArtExtractorCacheTest {

    @Test
    fun `concurrent same-hash writes produce one complete cache file`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val bytes = jpegBytes()
        // 非 media authority → 跳过 MediaStore 分支，直接走内容哈希缓存
        val songUri = Uri.parse("content://com.example/song/1")

        AlbumArtExtractor.resetCacheStats()
        val pool = Executors.newFixedThreadPool(8)
        val uris = try {
            (1..8)
                .map { pool.submit<Uri?> { AlbumArtExtractor.getAlbumArtUri(ctx, songUri, bytes) } }
                .map { it.get() }
        } finally {
            pool.shutdownNow()
        }

        // 全部拿到同一个缓存文件
        assertTrue(uris.all { it != null })
        assertEquals(1, uris.distinct().size)

        // M-9 的核心断言：同一哈希**只写一次**，其余线程复用产物
        assertEquals(1, AlbumArtExtractor.cacheWriteCount)

        // 没有残留的半截 .tmp（写失败也要清干净）
        val cacheDir = File(ctx.cacheDir, "album_art")
        assertTrue(cacheDir.listFiles()?.none { it.name.endsWith(".tmp") } == true)

        // 原子落盘：读者看到的是**完整可解码**的 JPEG，而不是写了一半的文件
        val cached = cacheDir.listFiles()?.firstOrNull { it.name.endsWith(".jpg") }
        assertNotNull("缓存目录里应有且只有一个封面文件", cached)
        val decoded = BitmapFactory.decodeFile(cached!!.absolutePath)
        assertNotNull("缓存封面必须可完整解码，实际文件=${cached.absolutePath}", decoded)
        assertTrue(decoded!!.width > 0)
    }

    @Test
    fun `second call for the same content hits the cache without rewriting`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val bytes = jpegBytes()
        val songUri = Uri.parse("content://com.example/song/2")

        AlbumArtExtractor.resetCacheStats()
        val first = AlbumArtExtractor.getAlbumArtUri(ctx, songUri, bytes)
        val second = AlbumArtExtractor.getAlbumArtUri(ctx, songUri, bytes)

        assertNotNull(first)
        assertEquals(first, second)
        assertEquals(1, AlbumArtExtractor.cacheWriteCount)
        assertEquals(1, AlbumArtExtractor.cacheHitCount)
    }

    /** 造一张真图 → JPEG 字节（需要真实解码，故启用 NATIVE 图形模式）。 */
    private fun jpegBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        bitmap.recycle()
        return out.toByteArray()
    }
}
