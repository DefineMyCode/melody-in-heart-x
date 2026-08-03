package cn.com.dcsgo.mihx.data.util

import android.content.Context
import android.net.Uri
import cn.com.dcsgo.mihx.core.common.AppLog
import cn.com.dcsgo.mihx.core.model.Lyrics

private const val TAG = "LyricsExtractor"

/**
 * 歌词提取器（门面）
 *
 * 统一入口，按优先级尝试两种歌词来源：
 * 1. 音频文件嵌入式歌词（EmbeddedLyricsParser）
 * 2. 同名 .lrc 文件（LrcParser）
 *
 * 具体解析逻辑分别见：
 * - [EmbeddedLyricsParser]：ID3v2 USLT/SYLT、FLAC Vorbis Comment
 * - [LrcParser]：LRC 文件查找与解析
 */
object LyricsExtractor {

    /**
     * 获取歌曲的歌词
     *
     * @param context 上下文
     * @param songUri 歌曲文件 URI
     * @return 歌词对象，如果没有找到歌词则返回 Lyrics.EMPTY
     */
    fun getLyrics(context: Context, songUri: Uri, lrcUri: Uri? = null): Lyrics {
        AppLog.debug(TAG, "Getting lyrics for: $songUri")

        if (lrcUri != null) {
            val importedLrcLyrics = LrcParser.parseLrcUri(context, lrcUri)
            if (importedLrcLyrics != null && importedLrcLyrics.lines.isNotEmpty()) {
                AppLog.debug(TAG, "Found lyrics from imported .lrc URI: ${importedLrcLyrics.lines.size} lines")
                return importedLrcLyrics
            }
        }

        // 首先尝试从音频文件的 embedded lyrics 提取
        val embeddedLyrics = EmbeddedLyricsParser.extract(context, songUri)
        if (embeddedLyrics != null && embeddedLyrics.lines.isNotEmpty()) {
            AppLog.debug(TAG, "Found embedded lyrics: ${embeddedLyrics.lines.size} lines")
            return embeddedLyrics
        }

        // 其次尝试查找同名 .lrc 文件
        val lrcLyrics = LrcParser.findAndParseLrcFile(context, songUri)
        if (lrcLyrics != null && lrcLyrics.lines.isNotEmpty()) {
            AppLog.debug(TAG, "Found lyrics from .lrc file: ${lrcLyrics.lines.size} lines")
            return lrcLyrics
        }

        AppLog.debug(TAG, "No lyrics found")
        return Lyrics.EMPTY
    }
}
