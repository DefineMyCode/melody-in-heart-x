package cn.com.dcsgo.mihx.feature.user

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import cn.com.dcsgo.mihx.core.model.Song

// ─────────────────────────────────────────────────────────────────
// 数据类型
// ─────────────────────────────────────────────────────────────────

/**
 * 多版本歌曲分组。
 *
 * 将同名歌曲（groupKey 相同）归为一组，用于在多版本管理界面展示。
 *
 * @property groupKey      分组键（来自 song.groupKey）
 * @property displayTitle  展示标题（取自组内任意一首歌曲的原始 title）
 * @property albumArtUri   封面 URI（取自组内第一首有封面的歌曲）
 * @property versions      该组下所有版本的歌曲列表（按采样率降序排列）
 */
data class SongVersionGroup(
    val groupKey: String,
    val displayTitle: String,
    val albumArtUri: android.net.Uri? = null,
    val versions: List<Song>
)

// ─────────────────────────────────────────────────────────────────
// 空状态提示
// ─────────────────────────────────────────────────────────────────

/**
 * 没有多版本歌曲时的空状态提示组件
 */
@Composable
fun NoMultiVersionHint() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.text_compare_24),
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.outlineVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "没有检测到多版本歌曲",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "同名歌曲的不同版本会在这里展示",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

// ─────────────────────────────────────────────────────────────────
// 多版本分组行（用于大列表，初始折叠以节省渲染开销）
// ─────────────────────────────────────────────────────────────────

/**
 * 多版本歌曲分组行。
 *
 * 设计目标：支持 1000+ 组、每组 10+ 版本时仍流畅滚动。
 * - 默认折叠：仅渲染标题行，展开时才渲染版本列表
 * - 通过 [forceExpanded] 支持从外部（定位到当前播放）强制展开某一组
 * - 展开/折叠使用 AnimatedVisibility 保持流畅动画
 *
 * @param group            多版本歌曲分组数据
 * @param currentSong      当前正在播放的歌曲（用于高亮和"播放中"标识）
 * @param isPlaying        播放器是否正在播放
 * @param forceExpanded    是否从外部强制展开（用于快速定位到正在播放的歌曲）
 * @param onPlayVersion    整行点击——立即播放该版本
 * @param onAddToQueue     添加到播放队列的回调
 * @param onDeleteVersion  删除某版本的回调（触发确认弹窗）
 * @param onCopyTitle      复制歌曲名到剪贴板的回调
 * @param onDetachVersion  将某版本移出当前分组的回调
 * @param onReassignVersion 将某版本关联到其他歌曲分组的回调
 */
@Composable
fun VersionGroupRow(
    group: SongVersionGroup,
    currentSong: Song?,
    isPlaying: Boolean,
    forceExpanded: Boolean = false,
    modifier: Modifier = Modifier,
    onPlayVersion: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onDeleteVersion: (Song) -> Unit,
    onCopyTitle: (String) -> Unit,
    onDetachVersion: (Song) -> Unit,
    onReassignVersion: (Song) -> Unit,
) {
    // 展开状态：外部 forceExpanded 优先，否则用户手动控制
    var userExpanded by remember(group.groupKey) { mutableStateOf(false) }
    val isExpanded = forceExpanded || userExpanded

    // 是否有版本正在播放（用于标题行高亮）
    val hasPlayingVersion = currentSong != null && isPlaying &&
        group.versions.any { it.id == currentSong.id }

    // 标题行背景色：有版本播放中时使用主色调浅色背景
    val headerBg by animateColorAsState(
        targetValue = if (hasPlayingVersion)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        else
            MaterialTheme.colorScheme.surfaceContainerLow,
        label = "headerBg"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = headerBg,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            // ── 分组标题行（点击展开/折叠，不播放）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { userExpanded = !userExpanded }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 封面缩略图
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (group.albumArtUri != null) {
                        AsyncImage(
                            model = group.albumArtUri,
                            contentDescription = "专辑封面",
                            modifier = Modifier.size(44.dp),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.text_compare_24),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // 歌曲标题 + 版本数 + 播放中标记
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = group.displayTitle,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (hasPlayingVersion) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (hasPlayingVersion) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "播放中",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Text(
                        text = "${group.versions.size} 个版本",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 展开/折叠箭头
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── 版本列表（AnimatedVisibility 保持展开动画）
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .padding(bottom = 8.dp)
                ) {
                    HorizontalDivider(
                        modifier = Modifier.padding(bottom = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    group.versions.forEachIndexed { index, song ->
                        VersionItemRow(
                            song = song,
                            isCurrentPlaying = isPlaying && currentSong?.id == song.id,
                            isLastItem = index == group.versions.lastIndex,
                            onPlay = { onPlayVersion(song) },
                            onAddToQueue = { onAddToQueue(song) },
                            onDelete = { onDeleteVersion(song) },
                            onCopyTitle = { onCopyTitle(song.title) },
                            onDetach = { onDetachVersion(song) },
                            onReassign = { onReassignVersion(song) }
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// 单个版本行
// ─────────────────────────────────────────────────────────────────

/**
 * 单个版本的歌曲行组件。
 *
 * 整行可点击播放。右侧图标按钮：添加到队列、复制歌曲名、更多（包含移出分组/关联/删除）。
 * 当前播放的版本有主色调高亮和"播放中"标识。
 *
 * @param song             歌曲数据
 * @param isCurrentPlaying 是否为当前正在播放的歌曲
 * @param isLastItem       是否为列表最后一项（不显示底部分割线）
 * @param onPlay           整行点击——播放该版本
 * @param onAddToQueue     添加到播放队列
 * @param onDelete         删除该版本
 * @param onCopyTitle      复制歌曲名
 * @param onDetach         将此版本移出当前分组（独立成单曲）
 * @param onReassign       将此版本关联到其他歌曲分组
 */
@Composable
fun VersionItemRow(
    song: Song,
    isCurrentPlaying: Boolean,
    isLastItem: Boolean,
    onPlay: () -> Unit,
    onAddToQueue: () -> Unit,
    onDelete: () -> Unit,
    onCopyTitle: () -> Unit,
    onDetach: () -> Unit,
    onReassign: () -> Unit,
) {
    val context = LocalContext.current
    var showMoreMenu by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                // 整行点击 = 播放
                .clickable(onClick = onPlay)
                .background(
                    if (isCurrentPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else androidx.compose.ui.graphics.Color.Transparent
                )
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 播放状态指示器（小圆点 / 播放图标）
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        if (isCurrentPlaying) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isCurrentPlaying) {
                    Icon(
                        painter = painterResource(R.drawable.pause_24),
                        contentDescription = "正在播放",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "播放",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // 采样率标签 + 歌曲艺术家 + titleOverride 说明
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 采样率标签
                    if (song.sampleRateDisplay.isNotEmpty()) {
                        Text(
                            text = song.sampleRateDisplay,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isCurrentPlaying) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (isCurrentPlaying) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    // 播放中标记
                    if (isCurrentPlaying) {
                        Text(
                            text = "播放中",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // 艺术家名
                Text(
                    text = song.artist.ifEmpty { "未知艺术家" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // 如果有 titleOverride 且与 title 不同（说明已重新归组），展示提示
                if (song.titleOverride != null && song.titleOverride != song.title) {
                    val isDetached = song.titleOverride == "${song.title}#${song.id}"
                    Text(
                        text = if (isDetached) "已移出分组" else "已关联到: ${song.titleOverride}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 添加到队列按钮
            IconButton(
                onClick = onAddToQueue,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.music_note_add_24),
                    contentDescription = "添加到队列",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 更多操作菜单（复制 / 移出分组 / 关联 / 删除）
            Box {
                IconButton(
                    onClick = { showMoreMenu = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "更多操作",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                androidx.compose.material3.DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false }
                ) {
                    // 复制歌曲名
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("复制歌曲名") },
                        onClick = {
                            showMoreMenu = false
                            copyToClipboard(context, song.title)
                            onCopyTitle()
                        },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.content_copy_24),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )

                    // 移出当前分组（独立成单曲）
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("移出当前分组") },
                        onClick = {
                            showMoreMenu = false
                            onDetach()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.CallSplit,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )

                    // 关联到其他歌曲
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("关联到其他歌曲") },
                        onClick = {
                            showMoreMenu = false
                            onReassign()
                        },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.swap_vert_24),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )

                    HorizontalDivider()

                    // 删除（红色危险操作）
                    androidx.compose.material3.DropdownMenuItem(
                        text = {
                            Text(
                                "删除此版本",
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            showMoreMenu = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }
            }
        }

        // 版本间分隔线（最后一项不显示）
        if (!isLastItem) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 46.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// 工具函数
// ─────────────────────────────────────────────────────────────────

/** 将文本复制到系统剪贴板 */
fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("歌曲名", text))
}
