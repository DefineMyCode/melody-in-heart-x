package cn.com.dcsgo.mihx

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import cn.com.dcsgo.mihx.core.model.Playlist
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.feature.playlist.PlaylistScreen
import cn.com.dcsgo.mihx.ui.theme.MusicplayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

class PlaylistScreenComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyLibraryShowsPlaylistEmptyStateAndCreatesPlaylist() {
        val createdNames = mutableListOf<String>()

        composeRule.setContent {
            MusicplayerTheme(dynamicColor = false) {
                PlaylistScreen(
                    playlists = emptyList(),
                    songs = emptyList(),
                    selectedPlaylist = null,
                    onPlaylistClick = {},
                    onSongClick = {},
                    onBackClick = {},
                    onCreatePlaylist = { createdNames += it },
                    onDeletePlaylist = {},
                    onRenamePlaylist = { _, _ -> },
                    onAddSongToPlaylist = { _, _ -> },
                    onRemoveSongFromPlaylist = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("我的歌单").assertIsDisplayed()
        composeRule.onNodeWithText("还没有任何音乐").assertIsDisplayed()
        composeRule.onNodeWithText("去「我的」页面导入本地音乐文件，\n导入后就可以在这里看到啦~")
            .assertIsDisplayed()

        composeRule.onNodeWithContentDescription("创建歌单").performClick()
        composeRule.onNodeWithText("创建歌单").assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).performTextInput("晨跑")
        composeRule.onNodeWithText("创建").performClick()

        assertEquals(listOf("晨跑"), createdNames)
    }

    @Test
    fun playlistListSupportsOpenRenameAndDeleteActions() {
        val song = Song(id = 1, title = "晴天", artist = "周杰伦")
        val playlist = Playlist(id = 7, name = "通勤歌单", songCount = 1, songIds = mutableListOf(song.id))
        val emptyPlaylist = Playlist(id = 8, name = "新歌暂存", songCount = 0, songIds = mutableListOf())
        var openedPlaylist: Playlist? = null
        val renamed = mutableListOf<Pair<Playlist, String>>()
        val deleted = mutableListOf<Playlist>()
        val addedToPlaylist = mutableListOf<Pair<Song, Playlist>>()
        var backCount = 0

        composeRule.setContent {
            MusicplayerTheme(dynamicColor = false) {
                PlaylistScreen(
                    playlists = listOf(playlist, emptyPlaylist),
                    songs = listOf(song),
                    selectedPlaylist = null,
                    onPlaylistClick = { openedPlaylist = it },
                    onSongClick = {},
                    onBackClick = { backCount += 1 },
                    onCreatePlaylist = {},
                    onDeletePlaylist = { deleted += it },
                    onRenamePlaylist = { target, name -> renamed += target to name },
                    onAddSongToPlaylist = { targetSong, targetPlaylist -> addedToPlaylist += targetSong to targetPlaylist },
                    onRemoveSongFromPlaylist = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("歌单").assertIsDisplayed()
        composeRule.onNodeWithText("通勤歌单").assertIsDisplayed()
        composeRule.onNodeWithText("1 首歌曲").assertIsDisplayed()
        composeRule.onNodeWithText("新歌暂存").assertIsDisplayed()
        composeRule.onNodeWithText("暂无歌曲").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("添加到歌单").performClick()
        composeRule.onNodeWithText("添加到歌单").assertIsDisplayed()
        composeRule.onNodeWithText("选择歌单添加「晴天」").assertIsDisplayed()
        composeRule.onNodeWithText("已添加").assertIsDisplayed()
        composeRule.onNodeWithText("新歌暂存").performClick()
        assertEquals(listOf(song to emptyPlaylist), addedToPlaylist)

        composeRule.onNodeWithText("通勤歌单").performClick()
        assertSame(playlist, openedPlaylist)

        composeRule.onNodeWithContentDescription("更多").performClick()
        composeRule.onNodeWithText("重命名").performClick()
        composeRule.onNodeWithText("重命名歌单").assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("深夜歌单")
        composeRule.onNodeWithText("确定").performClick()
        assertEquals(listOf(playlist to "深夜歌单"), renamed)

        composeRule.onNodeWithContentDescription("更多").performClick()
        composeRule.onNodeWithText("删除歌单").performClick()
        composeRule.onNodeWithText("确定要删除「通勤歌单」吗？歌单内的歌曲不会被删除。")
            .assertIsDisplayed()
        composeRule.onNodeWithText("删除").performClick()

        assertEquals(listOf(playlist), deleted)
        assertEquals(1, backCount)
    }

    @Test
    fun playlistDetailExposesBatchPlaybackAndSongActions() {
        val songs = listOf(
            Song(id = 1, title = "夜曲", artist = "周杰伦"),
            Song(id = 2, title = "倒带", artist = "蔡依林"),
        )
        val playlist = Playlist(
            id = 8,
            name = "夜间收藏",
            songCount = songs.size,
            songIds = songs.mapTo(mutableListOf()) { it.id },
        )
        var backCount = 0
        var clickedSong: Song? = null
        var playAllCount = 0
        var playAllFromEndCount = 0
        var addAllToQueueCount = 0
        var addAllToNextCount = 0
        val queuedSongs = mutableListOf<Song>()
        val nextSongs = mutableListOf<Song>()
        val removed = mutableListOf<Pair<Song, Playlist>>()

        composeRule.setContent {
            MusicplayerTheme(dynamicColor = false) {
                PlaylistScreen(
                    playlists = listOf(playlist),
                    songs = songs,
                    selectedPlaylist = playlist,
                    currentSong = songs.first(),
                    isPlaying = true,
                    onPlaylistClick = {},
                    onSongClick = { clickedSong = it },
                    onBackClick = { backCount += 1 },
                    onCreatePlaylist = {},
                    onDeletePlaylist = {},
                    onRenamePlaylist = { _, _ -> },
                    onAddSongToPlaylist = { _, _ -> },
                    onRemoveSongFromPlaylist = { song, target -> removed += song to target },
                    onPlayAllInPlaylist = { _, _ -> playAllCount += 1 },
                    onPlayAllFromEndInPlaylist = { _, _ -> playAllFromEndCount += 1 },
                    onAddAllToQueueInPlaylist = { _, _ -> addAllToQueueCount += 1 },
                    onAddAllToNextPlayInPlaylist = { _, _ -> addAllToNextCount += 1 },
                    onAddSongToQueue = { queuedSongs += it },
                    onAddSongToNextPlay = { nextSongs += it },
                )
            }
        }

        composeRule.onNodeWithContentDescription("返回").assertIsDisplayed()
        composeRule.onNodeWithText("夜间收藏").assertIsDisplayed()
        composeRule.onNodeWithText("2 首歌曲").assertIsDisplayed()
        composeRule.onNodeWithText("歌曲列表").assertIsDisplayed()
        composeRule.onNodeWithText("夜曲").assertIsDisplayed()
        composeRule.onNodeWithText("倒带").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("正在播放").assertIsDisplayed()

        composeRule.onNodeWithText("从头播放 (2)").performClick()
        composeRule.onNodeWithText("从尾播放 (2)").performClick()
        composeRule.onNodeWithText("加入队尾 (2)").performClick()
        composeRule.onNodeWithText("下一首播放 (2)").performClick()

        assertEquals(1, playAllCount)
        assertEquals(1, playAllFromEndCount)
        assertEquals(1, addAllToQueueCount)
        assertEquals(1, addAllToNextCount)

        composeRule.onNodeWithText("倒带").performClick()
        assertEquals(songs[1], clickedSong)

        composeRule.onNodeWithContentDescription("加入播放队列").performClick()
        composeRule.onNodeWithContentDescription("下一首播放").performClick()
        assertEquals(listOf(songs.first()), queuedSongs)
        assertEquals(listOf(songs.first()), nextSongs)

        composeRule.onNodeWithContentDescription("从歌单移除").performClick()
        composeRule.onNodeWithText("从歌单移除").assertIsDisplayed()
        composeRule.onNodeWithText("移除").performClick()
        assertEquals(listOf(songs.first() to playlist), removed)

        composeRule.onNodeWithContentDescription("返回").performClick()
        assertEquals(1, backCount)
    }
}
