package cn.com.dcsgo.mihx.ui.components

import cn.com.dcsgo.mihx.core.common.AppLog
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.core.model.SongInfo
import kotlinx.coroutines.CancellationException

private const val TAG = "SongInfoLoader"

/**
 * 加载歌曲元数据，**兜住一切非取消异常**。
 *
 * 评审 2026-10-27 · C-1：`loadSongInfo` 的实现末端有一次未受保护的 Room 读
 * （`MediaMetadataRepository.readEmotionSafely` 之前的写法），Room 异常
 * （SQLiteFull / SQLiteCorrupt / 磁盘 IO）会一路抛到调用方的 `LaunchedEffect`
 * 协程里——**未捕获异常 = 进程崩溃**，而不是被吞掉的错误。
 *
 * 同一调用曾有 8 处，其中 7 处裸调、仅 app 壳层 1 处做了防护，形成不一致。
 * 现在：
 * - 实现侧（`:data`）已下沉兜底 → 生产路径免疫；
 * - 调用侧统一走本函数 → 任何注入进来的实现都不会再把协程带崩。
 *
 * `CancellationException` 必须原样上抛（协程取消语义），不得被吞成 null。
 */
suspend fun loadSongInfoSafely(
    loadSongInfo: suspend (Song) -> SongInfo?,
    song: Song?,
): SongInfo? {
    if (song == null) return null
    return try {
        loadSongInfo(song)
    } catch (ce: CancellationException) {
        throw ce
    } catch (t: Throwable) {
        AppLog.error(TAG, "loadSongInfo failed for song=${song.id}: ${t.message}", t)
        null
    }
}
