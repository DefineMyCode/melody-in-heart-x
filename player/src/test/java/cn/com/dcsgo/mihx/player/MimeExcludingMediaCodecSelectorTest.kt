package cn.com.dcsgo.mihx.player

import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 覆盖「平台解码器候选筛选」的选择与委派行为。
 *
 * 这条链路的价值在于：渲染器模式改成平台优先后，ALAC 必须被排除回 FFmpeg 扩展，
 * 否则等于撤掉了 2026-08-31 小米 ALAC 厂商软解 SIGSEGV 之后验证过的规避措施。
 */
class MimeExcludingMediaCodecSelectorTest {

    /** 记录调用参数的假选择器。 */
    private class RecordingSelector : MediaCodecSelector {
        val calls = mutableListOf<Triple<String, Boolean, Boolean>>()

        override fun getDecoderInfos(
            mimeType: String,
            requiresSecureDecoder: Boolean,
            requiresTunneling: Boolean,
        ): List<MediaCodecInfo> {
            calls += Triple(mimeType, requiresSecureDecoder, requiresTunneling)
            return emptyList()
        }
    }

    @Test
    fun `excluded mime yields no platform decoder candidates`() {
        val delegate = RecordingSelector()
        val selector = MimeExcludingMediaCodecSelector(delegate, setOf("audio/alac"))

        val result = selector.getDecoderInfos("audio/alac", false, false)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `excluded mime never reaches the delegate`() {
        val delegate = RecordingSelector()
        val selector = MimeExcludingMediaCodecSelector(delegate, setOf("audio/alac"))

        selector.getDecoderInfos("audio/alac", false, false)

        assertTrue(delegate.calls.isEmpty())
    }

    @Test
    fun `non excluded mime is delegated with identical arguments`() {
        val delegate = RecordingSelector()
        val selector = MimeExcludingMediaCodecSelector(delegate, setOf("audio/alac"))

        selector.getDecoderInfos("audio/eac3-joc", true, false)

        assertEquals(1, delegate.calls.size)
        val (mime, secure, tunneling) = delegate.calls.single()
        assertEquals("audio/eac3-joc", mime)
        assertEquals(true, secure)
        assertEquals(false, tunneling)
    }

    @Test
    fun `exclusion is exact match and does not leak to similar mimes`() {
        val delegate = RecordingSelector()
        val selector = MimeExcludingMediaCodecSelector(delegate, setOf("audio/alac"))

        // "audio/alac" 不应误伤 "audio/alac-extra" 之类的前后缀变体
        selector.getDecoderInfos("audio/alac-extra", false, false)

        assertEquals(1, delegate.calls.size)
        assertEquals("audio/alac-extra", delegate.calls.single().first)
    }

    @Test
    fun `all excluded mimes are filtered independently`() {
        val delegate = RecordingSelector()
        val excluded = setOf("audio/alac", "audio/x-lg-alac")
        val selector = MimeExcludingMediaCodecSelector(delegate, excluded)

        assertTrue(selector.getDecoderInfos("audio/alac", false, false).isEmpty())
        assertTrue(selector.getDecoderInfos("audio/x-lg-alac", false, false).isEmpty())
        assertTrue(delegate.calls.isEmpty())
    }
}
