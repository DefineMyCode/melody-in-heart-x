package cn.com.dcsgo.mihx

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.feature.home.HomeScreen
import cn.com.dcsgo.mihx.ui.theme.MusicplayerTheme
import org.junit.Rule
import org.junit.Test

class HomeScreenComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyLibraryShowsHomeEmptyStateAndLuckyPlayAction() {
        composeRule.setContent {
            MusicplayerTheme(dynamicColor = false) {
                HomeScreen(
                    currentSong = null,
                    isPlaying = false,
                    currentPositionMs = 0L,
                    durationMs = 0L,
                    playMode = PlayMode.SEQUENTIAL,
                    onPlayPauseClick = {},
                    onPreviousClick = {},
                    onNextClick = {},
                    onStartSeeking = {},
                    onEndSeeking = {},
                    onSeekTo = {},
                    onQueueClick = {},
                    onShowLyrics = {},
                    onTogglePlayMode = {},
                )
            }
        }

        composeRule.onNodeWithText("还没有音乐可播放").assertIsDisplayed()
        composeRule.onNodeWithText("前往「我的」页面，\n添加本地音乐文件或导入文件夹吧~").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("随机播放低播放量歌曲").assertIsDisplayed()
    }

    @Test
    fun playingSongShowsPlaybackControlsAndSongMetadata() {
        composeRule.setContent {
            MusicplayerTheme(dynamicColor = false) {
                HomeScreen(
                    currentSong = Song(
                        id = 42,
                        title = "夜曲",
                        artist = "周杰伦",
                        sampleRate = 96_000,
                    ),
                    isPlaying = true,
                    currentPositionMs = 12_000L,
                    durationMs = 240_000L,
                    playMode = PlayMode.SEQUENTIAL,
                    onPlayPauseClick = {},
                    onPreviousClick = {},
                    onNextClick = {},
                    onStartSeeking = {},
                    onEndSeeking = {},
                    onSeekTo = {},
                    onQueueClick = {},
                    onShowLyrics = {},
                    onTogglePlayMode = {},
                )
            }
        }

        composeRule.onNodeWithText("夜曲").assertIsDisplayed()
        composeRule.onNodeWithText("周杰伦").assertIsDisplayed()
        composeRule.onNodeWithText("Hi-Res").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("播放队列").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("上一首").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("暂停").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("下一首").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("播放模式: 顺序播放").assertIsDisplayed()
        composeRule.onNodeWithText("无限播放").assertIsDisplayed()
    }
}
