package cn.com.dcsgo.mihx.data.player

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SingleItemLoopRewindDetectionTest {

    private val auto = Player.DISCONTINUITY_REASON_AUTO_TRANSITION

    @Test
    fun detectsSingleItemLoopRewind() {
        // 4 分钟单项队列播完自然回绕（时长未知回退保守阈值）：应识别为一次播完
        assertTrue(
            isSingleItemLoopRewind(
                oldIndex = 0, newIndex = 0, reason = auto,
                mediaItemCount = 1, oldPositionMs = 240_000L, newPositionMs = 0L,
                durationMs = -1L,
            )
        )
    }

    @Test
    fun detectsShortSongLoopRewindWithKnownDuration() {
        // 30 秒短歌播完回绕：按 duration 精确判定接近结尾
        assertTrue(
            isSingleItemLoopRewind(
                oldIndex = 0, newIndex = 0, reason = auto,
                mediaItemCount = 1, oldPositionMs = 28_000L, newPositionMs = 0L,
                durationMs = 30_000L,
            )
        )
    }

    @Test
    fun ignoresShortSongMidpositionRewind() {
        // duration 已知但旧位置远未到结尾：不是自然播完
        assertFalse(
            isSingleItemLoopRewind(
                oldIndex = 0, newIndex = 0, reason = auto,
                mediaItemCount = 1, oldPositionMs = 10_000L, newPositionMs = 0L,
                durationMs = 300_000L,
            )
        )
    }

    @Test
    fun ignoresMultiItemQueue() {
        // 多首队列回绕会正常触发 mediaItemTransition，不应由本路径处理
        assertFalse(
            isSingleItemLoopRewind(
                oldIndex = 0, newIndex = 0, reason = auto,
                mediaItemCount = 5, oldPositionMs = 240_000L, newPositionMs = 0L,
                durationMs = 240_000L,
            )
        )
    }

    @Test
    fun ignoresIndexChange() {
        // 索引变化说明切到了不同项，属于正常 transition 场景
        assertFalse(
            isSingleItemLoopRewind(
                oldIndex = 0, newIndex = 1, reason = auto,
                mediaItemCount = 1, oldPositionMs = 240_000L, newPositionMs = 0L,
                durationMs = 240_000L,
            )
        )
    }

    @Test
    fun ignoresManualSeekToStart() {
        // 用户手动拖回开头（SEEK 原因）不得计为播完，防刷
        assertFalse(
            isSingleItemLoopRewind(
                oldIndex = 0, newIndex = 0, reason = Player.DISCONTINUITY_REASON_SEEK,
                mediaItemCount = 1, oldPositionMs = 240_000L, newPositionMs = 0L,
                durationMs = 240_000L,
            )
        )
    }

    @Test
    fun ignoresEarlyRewindFromSmallOldPosition() {
        // 时长未知时，刚开播（<30s）就回跳视为缓冲抖动，不计播完
        assertFalse(
            isSingleItemLoopRewind(
                oldIndex = 0, newIndex = 0, reason = auto,
                mediaItemCount = 1, oldPositionMs = 5_000L, newPositionMs = 0L,
                durationMs = -1L,
            )
        )
    }

    @Test
    fun ignoresJumpToNonZeroNewPosition() {
        // 新位置未回到开头，不是回绕
        assertFalse(
            isSingleItemLoopRewind(
                oldIndex = 0, newIndex = 0, reason = auto,
                mediaItemCount = 1, oldPositionMs = 240_000L, newPositionMs = 90_000L,
                durationMs = 240_000L,
            )
        )
    }

    @Test
    fun boundaryPositionsStillDetected() {
        // 阈值边界：新位置恰为上限、旧位置恰为「时长-容差」下限，仍算回绕
        assertTrue(
            isSingleItemLoopRewind(
                oldIndex = 0, newIndex = 0, reason = auto,
                mediaItemCount = 1, oldPositionMs = 235_000L, newPositionMs = 2_000L,
                durationMs = 240_000L,
            )
        )
    }
}
