package cn.com.dcsgo.mihx.app

import androidx.lifecycle.ViewModel
import cn.com.dcsgo.mihx.core.model.Lyrics
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.core.model.SongInfo
import cn.com.dcsgo.mihx.domain.repository.LyricsRepository
import cn.com.dcsgo.mihx.domain.repository.SongMetadataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AppMediaMetadataViewModel @Inject constructor(
    private val lyricsRepository: LyricsRepository,
    private val songMetadataRepository: SongMetadataRepository,
) : ViewModel() {
    suspend fun lyricsFor(song: Song): Lyrics = lyricsRepository.lyricsFor(song)

    suspend fun songInfo(song: Song): SongInfo? = songMetadataRepository.songInfo(song)
}
