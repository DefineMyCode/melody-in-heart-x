package cn.com.dcsgo.mihx.domain.model

import cn.com.dcsgo.mihx.core.model.Song

/**
 * 同一物理文件（真实路径相同）被重复入库形成的重复组。
 *
 * 正常情况该组 [songs].size == 1；>1 即存在重复，应保留 [songs].first()，
 * 其余作为待清理项。
 */
data class DuplicateSongGroup(
    /** 归一化的真实路径，如 /storage/emulated/0/音乐/群歌.flac */
    val realPath: String,
    /** 组内全部歌曲（按 songId 升序），首条为建议保留项 */
    val songs: List<Song>,
) {
    /** 建议保留的一条（songId 最小，最早导入） */
    val keep: Song get() = songs.first()

    /** 待删除的重复项（除保留项外的其余） */
    val duplicates: List<Song> get() = songs.drop(1)

    val isDuplicate: Boolean get() = songs.size > 1
}
