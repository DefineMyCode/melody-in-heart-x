package cn.com.dcsgo.mihx.feature.lyrics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide lyrics font-size scale. Kept outside [LyricsViewModel] (which is re-created on
 * every navigation into the lyrics screen) so the size survives leaving and re-entering the page.
 * Applied to every lyric line (active and inactive) via [LyricsView.fontScale].
 */
@Singleton
class LyricsDisplaySettings @Inject constructor() {

    private val _fontScale = MutableStateFlow(DEFAULT_SCALE)
    val fontScale: StateFlow<Float> = _fontScale.asStateFlow()

    /** Increases the lyric font size by one step, capped at [MAX_SCALE]. */
    fun enlarge() = _fontScale.update { (it + STEP).coerceAtMost(MAX_SCALE) }

    /** Decreases the lyric font size by one step, floored at [MIN_SCALE]. */
    fun shrink() = _fontScale.update { (it - STEP).coerceAtLeast(MIN_SCALE) }

    /** Restores the default (1×) lyric font size. */
    fun reset() {
        _fontScale.value = DEFAULT_SCALE
    }

    private companion object {
        const val DEFAULT_SCALE = 1f
        const val MIN_SCALE = 0.8f
        const val MAX_SCALE = 1.5f
        const val STEP = 0.1f
    }
}
