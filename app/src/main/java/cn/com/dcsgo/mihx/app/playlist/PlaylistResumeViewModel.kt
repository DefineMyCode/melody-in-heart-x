package cn.com.dcsgo.mihx.app.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.com.dcsgo.mihx.domain.model.PlaylistResume
import cn.com.dcsgo.mihx.domain.repository.PlaylistResumeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@HiltViewModel
class PlaylistResumeViewModel @Inject constructor(
    private val resumeRepository: PlaylistResumeRepository,
) : ViewModel() {
    fun observeResume(playlistId: Int): Flow<PlaylistResume?> = resumeRepository.observeResume(playlistId)

    fun record(playlistId: Int, songId: Int) {
        viewModelScope.launch {
            resumeRepository.record(playlistId, songId)
        }
    }

    fun clear(playlistId: Int) {
        viewModelScope.launch {
            resumeRepository.clear(playlistId)
        }
    }

    /**
     * 切换播放来源歌单。
     * 切换前若旧来源是某歌单且与新的不同,先以 [currentSongId](旧来源实际在播的歌曲)记录该歌单,
     * 再更新来源标记。非歌单播放([newSource] 为 null)同样会先结算旧歌单。
     *
     * 结算与写入由仓库在单次事务内完成,ViewModel 只做转发。
     */
    fun switchSource(newSource: Int?, currentSongId: Int?) {
        viewModelScope.launch {
            resumeRepository.switchSourcePlaylist(newSource, currentSongId)
        }
    }
}
