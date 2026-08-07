package cn.com.dcsgo.mihx.feature.user

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.com.dcsgo.mihx.domain.model.LocalFileValidationResult

@Composable
fun UserScreen(
    onShowSettings: () -> Unit = {},
    todayDurationMs: Long = 0L,
    weekTotalMs: Long = 0L,
    onOpenPlaybackStats: () -> Unit = {},
    validationResult: LocalFileValidationResult? = null,
    isValidating: Boolean = false,
    onOpenFileCheck: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "user_info", contentType = "header") {
                UserInfoSection(onSettingsClick = onShowSettings)
            }

            item(key = "play_stats", contentType = "header") {
                PlayStatsSection(
                    todayDurationMs = todayDurationMs,
                    weekTotalMs = weekTotalMs,
                    onOpenPlaybackStats = onOpenPlaybackStats
                )
            }

            item(key = "file_check", contentType = "header") {
                FileCheckSection(
                    validationResult = validationResult,
                    isValidating = isValidating,
                    onOpenFileCheck = onOpenFileCheck
                )
            }
        }
    }
}
