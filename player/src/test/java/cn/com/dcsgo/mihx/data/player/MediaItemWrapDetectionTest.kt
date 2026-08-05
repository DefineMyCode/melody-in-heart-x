package cn.com.dcsgo.mihx.data.player

import androidx.media3.common.C
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaItemWrapDetectionTest {

    @Test
    fun wrapDetectedWhenJumpingFromLastIndexToZero() {
        // 窗口 71 首，从索引 70（最后一首）回绕到索引 0
        assertTrue(isMediaItemWrap(previousIndex = 70, newIndex = 0, mediaItemCount = 71))
    }

    @Test
    fun notWrapOnNormalForwardNavigation() {
        // 顺序下一首：新索引 +1，不可能是回绕
        assertFalse(isMediaItemWrap(previousIndex = 5, newIndex = 6, mediaItemCount = 71))
    }

    @Test
    fun notWrapOnPreviousOneStep() {
        // 上一首回到 0：但上一首并不是窗口最后一首（不是从尾部跳回）
        assertFalse(isMediaItemWrap(previousIndex = 1, newIndex = 0, mediaItemCount = 71))
    }

    @Test
    fun notWrapWhenPreviousIsUnknown() {
        // 首次切换、无上一索引时不判定回绕
        assertFalse(isMediaItemWrap(previousIndex = C.INDEX_UNSET, newIndex = 0, mediaItemCount = 71))
    }

    @Test
    fun notWrapWhenNewIndexNotZero() {
        // 回绕必然回到索引 0；回到其他位置不算
        assertFalse(isMediaItemWrap(previousIndex = 70, newIndex = 3, mediaItemCount = 71))
    }

    @Test
    fun tinyWindowBackwardIsTreatedAsWrap() {
        // 2 首窗口从索引 1 回到 0：索引判据无法区分“上一首”与“回绕”，
        // 无限播放下视为回绕触发补队列是无害的（补队列 planner 会自动去重）
        assertTrue(isMediaItemWrap(previousIndex = 1, newIndex = 0, mediaItemCount = 2))
    }

    @Test
    fun emptyOrSingleItemWindowCannotWrap() {
        assertFalse(isMediaItemWrap(previousIndex = 0, newIndex = 0, mediaItemCount = 1))
        assertFalse(isMediaItemWrap(previousIndex = -1, newIndex = -1, mediaItemCount = 0))
    }
}
