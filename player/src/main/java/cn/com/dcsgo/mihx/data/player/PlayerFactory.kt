package cn.com.dcsgo.mihx.data.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl

object PlayerFactory {

    @OptIn(UnstableApi::class)
    fun create(
        context: Context,
        loadControl: LoadControl? = null,
        handleAudioFocus: Boolean = true,
        handleAudioBecomingNoisy: Boolean = true,
        preferExtensionRenderers: Boolean = true,
    ): ExoPlayer {
        val renderersFactory = DefaultRenderersFactory(context).apply {
            setEnableAudioFloatOutput(true)
            setEnableDecoderFallback(true)
            if (preferExtensionRenderers) {
                setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            }
        }

        val actualLoadControl = loadControl ?: DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                4000,
                10000,
                2000,
                2500
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        return ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(actualLoadControl)
            .build()
            .apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    handleAudioFocus
                )
                setHandleAudioBecomingNoisy(handleAudioBecomingNoisy)
                setPauseAtEndOfMediaItems(false)
            }
    }
}
