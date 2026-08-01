package cn.com.dcsgo.mihx.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import cn.com.dcsgo.mihx.core.common.log.AppLogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the single app-wide [ExoPlayer] used by [cn.com.dcsgo.mihx.player.service.AppMediaSessionService].
 *
 * Per plan P1-2 the FFmpeg extension renderer is preferred (not hard-referenced):
 * [DefaultRenderersFactory.setExtensionRendererMode] in `EXTENSION_RENDERER_MODE_PREFER` lets
 * Media3 discover `org.jellyfin.media3.ffmpeg.FfmpegAudioRenderer` on the classpath, and
 * [loadFfmpegLibrary] initializes the native library via reflection so this module keeps no
 * compile-time dependency on it (the renderer class is kept by the release ProGuard rule).
 */
interface PlayerFactory {
    fun create(context: Context): ExoPlayer
}

@Singleton
class DefaultPlayerFactory @Inject constructor() : PlayerFactory {

    @UnstableApi
    override fun create(context: Context): ExoPlayer {
        loadFfmpegLibrary(context)
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        return ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            // handleAudioFocus = true: the system manages audio focus. ExoPlayer also pauses
            // automatically on ACTION_AUDIO_BECOMING_NOISY for USAGE_MEDIA (plan P3-5 double-safety).
            .setAudioAttributes(audioAttributes, true)
            .build()
    }

    /**
     * Reflectively invokes `FfmpegLibrary.initialize(context)` so the module never references
     * `org.jellyfin.media3.ffmpeg` at compile time. Failure is non-fatal: Media3 falls back to
     * its built-in decoders and the controller logs which renderer was actually selected (P1-11).
     */
    private fun loadFfmpegLibrary(context: Context) {
        try {
            val clazz = Class.forName("org.jellyfin.media3.ffmpeg.FfmpegLibrary")
            val method = clazz.getMethod("initialize", Context::class.java)
            method.invoke(null, context.applicationContext)
            AppLogger.d(TAG, "FFmpeg extension library initialized (reflective).")
        } catch (e: ReflectiveOperationException) {
            AppLogger.w(TAG, "FFmpeg extension unavailable; using built-in decoders.")
        } catch (e: Exception) {
            AppLogger.w(TAG, "FFmpeg extension init failed; using built-in decoders.")
        }
    }

    companion object {
        private const val TAG = "DefaultPlayerFactory"
    }
}
