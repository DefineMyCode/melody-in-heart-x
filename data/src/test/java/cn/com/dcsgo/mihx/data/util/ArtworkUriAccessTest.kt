package cn.com.dcsgo.mihx.data.util

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import cn.com.dcsgo.mihx.core.common.ArtworkUriAccess
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 专辑封面跨进程读授权的契约（2026-10-27）。
 *
 * 平台 `MediaSessionRecord.sanitizeMediaMetadata()` 只**校验** artwork URI 是否可被授予，
 * **不负责授权**；授权责任在 App 身上。少了这一步，跨进程消费方（SystemUI 媒体控制卡片 /
 * 平台媒体通知）会拿到 `SecurityException: Permission Denial: reading ... requires the
 * provider be exported, or grantUriPermission()`。
 *
 * 本组用例锁住共享 helper（`:core:common` 的 `ArtworkUriAccess`）的关键行为：只授权
 * **自家 provider** 的 `content://`、同一 URI 只授权一次、批量授权内部去重。
 *
 * 放在 `:data` 而不是 `:core:common`：Robolectric 只在已收录依赖校验的模块里可用
 * （给 `:core:common` 加 Robolectric 会触发 dependency verification 失败）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ArtworkUriAccessTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    /** 自家封面 provider 产出的 URI。 */
    private fun ownArtworkUri(name: String): Uri =
        Uri.parse("content://${ArtworkUriAccess.authority(ctx)}/album_art/$name")

    @Before
    fun resetGrants() {
        ArtworkUriAccess.resetGrants()
    }

    @Test
    fun `own content uri is granted`() {
        ArtworkUriAccess.grantRead(ctx, ownArtworkUri("a.jpg"))

        assertEquals(1, ArtworkUriAccess.grantedUriCount)
    }

    @Test
    fun `the same uri is granted only once`() {
        val uri = ownArtworkUri("a.jpg")

        ArtworkUriAccess.grantRead(ctx, uri)
        ArtworkUriAccess.grantRead(ctx, uri)

        assertEquals(1, ArtworkUriAccess.grantedUriCount)
    }

    @Test
    fun `distinct uris are granted independently`() {
        ArtworkUriAccess.grantRead(ctx, ownArtworkUri("a.jpg"))
        ArtworkUriAccess.grantRead(ctx, ownArtworkUri("b.jpg"))

        assertEquals(2, ArtworkUriAccess.grantedUriCount)
    }

    @Test
    fun `null uri is ignored`() {
        ArtworkUriAccess.grantRead(ctx, null)

        assertEquals(0, ArtworkUriAccess.grantedUriCount)
    }

    @Test
    fun `foreign content uri is ignored`() {
        // MediaStore 的 albumart 本来就能被跨进程读，授权它既无意义又可能触发
        // 「非本 App 拥有的 provider」SecurityException 噪音
        ArtworkUriAccess.grantRead(ctx, Uri.parse("content://media/external/audio/albumart/42"))

        assertEquals(0, ArtworkUriAccess.grantedUriCount)
    }

    @Test
    fun `file uri is ignored`() {
        // 私有缓存路径跨进程读不到，URI 授权对 file:// 也不适用
        ArtworkUriAccess.grantRead(ctx, Uri.parse("file:///data/user/0/x/cache/album_art/a.jpg"))

        assertEquals(0, ArtworkUriAccess.grantedUriCount)
    }

    @Test
    fun `batch grant dedupes and skips nulls`() {
        val a = ownArtworkUri("a.jpg")
        val b = ownArtworkUri("b.jpg")

        ArtworkUriAccess.grantRead(ctx, listOf(a, b, a, null))

        assertEquals(2, ArtworkUriAccess.grantedUriCount)
    }
}
