package cn.com.dcsgo.mihx.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * 顶部 Toast 通知系统
 *
 * 特性：
 * - 仅显示最新一条通知，新通知会替换旧通知
 * - 自动消失（默认 2 秒）
 * - 可手动关闭
 * - 通过 [rememberToastHost] 创建实例并可在任意位置触发
 *
 * 用法：
 * ```kotlin
 * val toastHost = rememberToastHost()
 * ToastHost(toastHost = toastHost) // 放在 UI 顶层
 * // 在任意位置调用
 * toastHost.showToast("已添加到播放队列")
 * ```
 */

/** 单条 Toast 数据 */
@Immutable
data class ToastEntry(
    val id: Long,
    val message: String,
)

@Stable
class ToastHostState {
    private val _entries = mutableStateListOf<ToastEntry>()
    val entries: List<ToastEntry> get() = _entries

    private var idCounter = 0L

    /**
     * 显示一条 Toast 通知
     *
     * 仅保留最新一条，新通知会替换当前显示中的旧通知。
     *
     * @param message  通知文本
     */
    fun showToast(message: String) {
        val id = ++idCounter
        _entries.clear()
        _entries.add(ToastEntry(id = id, message = message))
    }

    /**
     * 移除指定 Toast
     */
    fun dismiss(id: Long) {
        _entries.removeAll { it.id == id }
    }
}

@Composable
fun rememberToastHost(): ToastHostState = remember { ToastHostState() }

/**
 * Toast 通知容器
 *
 * 放置在 UI 顶层（如 Scaffold 外层），会从顶部弹出通知。
 * 仅显示最新一条通知。
 *
 * @param toastHost    Toast 状态管理
 * @param modifier     修饰符
 */
@Composable
fun ToastHost(
    toastHost: ToastHostState,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        // 仅显示最新一条
        val visibleEntries = toastHost.entries.takeLast(1)

        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            visibleEntries.forEach { entry ->
                ToastItem(
                    entry = entry,
                    onDismiss = { toastHost.dismiss(entry.id) },
                    modifier = Modifier.padding(top = 6.dp, bottom = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun ToastItem(
    entry: ToastEntry,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(200)) + slideInVertically(
            initialOffsetY = { -it },
            animationSpec = tween(250)
        ),
        exit = fadeOut(animationSpec = tween(200)) + slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(200)
        ),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = entry.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.background,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "关闭",
                    tint = MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/**
 * 自动移除 Toast 的副作用
 *
 * 在 Composable 树中调用，会自动监听 [toastHost] 的条目变化，
 * 在 [durationMs] 毫秒后自动移除每条 Toast。
 *
 * @param toastHost    Toast 状态管理
 * @param durationMs   自动消失时间（毫秒），默认 2 秒
 */
@Composable
fun AutoDismissToasts(
    toastHost: ToastHostState,
    durationMs: Long = 2000L,
) {
    toastHost.entries.forEach { entry ->
        val key = remember(entry.id) { entry.id }
        LaunchedEffect(key) {
            delay(durationMs)
            toastHost.dismiss(entry.id)
        }
    }
}
