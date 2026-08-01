package cn.com.dcsgo.mihx.domain.version

import cn.com.dcsgo.mihx.core.model.Song

/** Resolves which version of a same-title song to prefer (higher sample rate). */
object SongVersionResolver {
    fun prefer(sameTitle: List<Song>): Song? =
        sameTitle.maxByOrNull { it.sampleRate }
}
