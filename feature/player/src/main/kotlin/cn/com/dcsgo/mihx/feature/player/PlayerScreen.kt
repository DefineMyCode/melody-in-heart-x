@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(ExperimentalMaterial3Api::class)

package cn.com.dcsgo.mihx.feature.player

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.com.dcsgo.mihx.core.ui.toast.LocalToastController
import cn.com.dcsgo.mihx.feature.player.component.QueuePanel

@Composable
fun PlayerScreen(viewModel: PlayerViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // 500ms progress tick; collected while the screen is at least STARTED (plan P1-7).
    val tick by viewModel.progressFlow().collectAsStateWithLifecycle(0L)

    // P3-7: load the library only after the media-read permission is granted (API 33+ needs
    // READ_MEDIA_AUDIO at runtime, otherwise TempMediaStoreSource returns nothing).
    val context = LocalContext.current
    // `LocalToastController.current` must be read in composable scope; hoist it here so the
    // non-composable permission callback lambda can call the plain `show(...)` function.
    val toastController = LocalToastController.current
    val mediaPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            viewModel.loadLibrary()
        } else {
            // P3-8: surface a degraded copy instead of failing silently with an empty library.
            toastController.show("未授权读取音乐，曲库为空；可在系统设置中授予“读取音乐”权限")
        }
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = context.checkSelfPermission(android.Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (granted) viewModel.loadLibrary() else mediaPermissionLauncher.launch(android.Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            viewModel.loadLibrary()
        }
    }

    var showQueue by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = "当前: ${state.currentMediaId ?: "无曲目"}")
            Slider(
                value = if (state.isDragging) state.sliderPositionMs.toFloat() else tick.toFloat(),
                valueRange = 0f..(if (state.durationMs > 0) state.durationMs.toFloat() else 1f),
                onValueChange = { viewModel.onSeekDrag(it.toLong()) },
                onValueChangeFinished = viewModel::onSeekDragEnded,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = viewModel::onPrevious) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "上一首")
                }
                Spacer(Modifier.width(16.dp))
                IconButton(onClick = viewModel::onPlayPause) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "播放/暂停",
                    )
                }
                Spacer(Modifier.width(16.dp))
                IconButton(onClick = viewModel::onNext) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "下一首")
                }
            }
        }

        FloatingActionButton(
            onClick = { showQueue = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "播放队列")
        }
    }

    if (showQueue) {
        ModalBottomSheet(
            onDismissRequest = { showQueue = false },
            sheetState = sheetState,
        ) {
            val ordered = remember(state.queue) { state.queue.orderedSongs() }
            QueuePanel(
                songs = ordered,
                currentIndex = state.highlightIndex,
                playMode = state.queue.playMode,
                onItemClick = { viewModel.onJumpTo(it) },
                onRemoveClick = { viewModel.onRemoveAt(it) },
                onModeChange = { viewModel.onSwitchMode(it) },
            )
        }
    }
}
