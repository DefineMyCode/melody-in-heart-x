package cn.com.dcsgo.mihx.feature.user

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import coil.compose.AsyncImage
import cn.com.dcsgo.mihx.core.model.Song

// ─────────────────────────────────────────────────────────────────
// 用户信息区域
// ─────────────────────────────────────────────────────────────────

/**
 * 用户页面顶部的用户信息展示区域
 * 显示应用图标、名称和简介
 */
@Composable
fun UserInfoSection(
    onSettingsClick: () -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Icon(
                painter = painterResource(R.drawable.settings_24),
                contentDescription = "设置",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
            ) {
                Image(
                    painter = painterResource(R.drawable.dcsgo_logo),
                    contentDescription = "个人中心",
                    modifier = Modifier
                        .size(100.dp)
                        .align(Alignment.Center)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.app_introduction),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// 文件管理区域
// ─────────────────────────────────────────────────────────────────

/**
 * 文件管理卡片，包含文件夹导入按钮、多版本管理入口和导入进度展示
 *
 * @param isImporting 是否正在导入中
 * @param importProgress 当前已处理文件数
 * @param importTotal 总文件数
 * @param onAddFolderClick 点击"添加文件夹"按钮的回调
 * @param onVersionManagementClick 点击"多版本管理"按钮的回调
 */
@Composable
fun FileManagementSection(
    isImporting: Boolean = false,
    importProgress: Int = 0,
    importTotal: Int = 0,
    onAddFolderClick: () -> Unit,
    onVersionManagementClick: () -> Unit = {},
    onQuickSkipSongsClick: () -> Unit = {},
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "文件管理",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // 导入进度条（导入中显示）
            if (isImporting) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    val animatedProgress by animateFloatAsState(
                        targetValue = if (importTotal > 0) importProgress.toFloat() / importTotal else 0f,
                        animationSpec = tween(durationMillis = 150, easing = LinearEasing),
                        label = "progress"
                    )
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (importTotal > 0) "正在导入 $importProgress / $importTotal ..." else "正在扫描文件夹 ...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 功能按钮区：添加文件夹 + 多版本管理 + 秒切歌曲 并排显示
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FileManagementButton(
                    icon = R.drawable.music_note_add_24,
                    label = "添加文件夹",
                    description = "批量导入",
                    onClick = onAddFolderClick,
                    enabled = !isImporting,
                    modifier = Modifier.weight(1f)
                )

                FileManagementButton(
                    icon = R.drawable.text_compare_24,
                    label = "多版本管理",
                    description = "查看与修复",
                    onClick = onVersionManagementClick,
                    enabled = !isImporting,
                    modifier = Modifier.weight(1f)
                )

                FileManagementButton(
                    icon = R.drawable.skip_next_24,
                    label = "秒切歌曲",
                    description = "记录秒切的歌曲",
                    onClick = onQuickSkipSongsClick,
                    enabled = !isImporting,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * 文件管理操作按钮组件
 *
 * @param icon 按钮图标资源 ID
 * @param label 按钮标题
 * @param description 按钮描述文字
 * @param onClick 点击回调
 * @param enabled 按钮是否可用
 * @param modifier 外部 Modifier（用于权重分配等）
 */
@Composable
fun FileManagementButton(
    icon: Int,
    label: String,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { if (enabled) onClick() }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (enabled) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = label,
                modifier = Modifier.size(22.dp),
                tint = if (enabled) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Text(
            text = description,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

// ─────────────────────────────────────────────────────────────────
// 播放统计区域
// ─────────────────────────────────────────────────────────────────

/**
 * 播放统计卡片，预留播放次数与有效播放次数入口。
 */
@Composable
fun PlayStatsSection(
    onPlayCountClick: () -> Unit = {},
    onEffectivePlayCountClick: () -> Unit = {},
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "播放统计",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FileManagementButton(
                    icon = R.drawable.bar_chart_4_bars_24,
                    label = "播放次数",
                    description = "统计入口",
                    onClick = onPlayCountClick,
                    modifier = Modifier.weight(1f)
                )

                FileManagementButton(
                    icon = R.drawable.bar_chart_24,
                    label = "有效播放次数",
                    description = "统计入口",
                    onClick = onEffectivePlayCountClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// 本地歌曲列表项（支持多选模式）
// ─────────────────────────────────────────────────────────────────

/**
 * 本地歌曲列表项组件
 *
 * 支持三种状态：
 * - 普通模式：显示封面、歌名、艺术家、更多操作菜单
 * - 多选模式：显示选择框替代更多菜单
 * - 正在播放高亮：封面背景变为主色、标题加粗
 *
 * @param song 歌曲数据
 * @param isCurrentPlaying 是否为当前正在播放的歌曲
 * @param isSelectMode 是否处于多选模式
 * @param isSelected 是否已选中（多选模式下）
 * @param onClick 点击回调
 * @param onShowInfo 查看歌曲信息回调
 * @param onAddToPlaylist 添加到歌单回调
 * @param onDelete 删除歌曲回调
 */
@Composable
fun LocalSongItem(
    song: Song,
    isCurrentPlaying: Boolean = false,
    isSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onShowInfo: (Song) -> Unit = {},
    onAddToPlaylist: (Song) -> Unit = {},
    onDelete: (Song) -> Unit = {},
) {
    val cornerShape = remember { RoundedCornerShape(8.dp) }
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 多选模式：显示复选框
        if (isSelectMode) {
            Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "已选中",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AddCircleOutline,
                        contentDescription = "未选中",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        // 封面
        SongAlbumArt(
            song = song,
            isCurrentPlaying = isCurrentPlaying,
            cornerShape = cornerShape
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrentPlaying) FontWeight.Bold else FontWeight.Medium,
                color = if (isCurrentPlaying) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 非多选模式时显示更多按钮
        if (!isSelectMode) {
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "更多",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("歌曲信息") },
                        onClick = {
                            showMenu = false
                            onShowInfo(song)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("添加到歌单") },
                        onClick = {
                            showMenu = false
                            onAddToPlaylist(song)
                        },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.list_alt_add_24),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                "删除",
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            showMenu = false
                            onDelete(song)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// 封面图组件
// ─────────────────────────────────────────────────────────────────

/**
 * 歌曲封面缩略图组件
 *
 * 有封面时显示封面图，无封面时根据播放状态显示播放/暂停图标。
 * 单独提取为独立 Composable，song 不变时可跳过 recomposition。
 *
 * @param song 歌曲数据（用于获取封面 URI）
 * @param isCurrentPlaying 是否正在播放
 * @param cornerShape 封面圆角形状
 * @param size 封面尺寸，默认 48.dp
 */
@Composable
fun SongAlbumArt(
    song: Song,
    isCurrentPlaying: Boolean,
    cornerShape: RoundedCornerShape,
    size: Int = 48
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(cornerShape)
            .background(
                if (isCurrentPlaying) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.primaryContainer
            ),
        contentAlignment = Alignment.Center
    ) {
        if (song.albumArtUri != null) {
            AsyncImage(
                model = song.albumArtUri,
                contentDescription = "专辑封面",
                modifier = Modifier.size(size.dp),
                contentScale = ContentScale.Crop,
            )
        } else {
            val iconSize = (size * 0.46).toInt()
            if (isCurrentPlaying) {
                Icon(
                    painter = painterResource(id = R.drawable.pause_24),
                    contentDescription = "正在播放",
                    modifier = Modifier.size(iconSize.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// 底部操作栏（添加到歌单 / 清除选择）
// ─────────────────────────────────────────────────────────────────

/**
 * 多选模式下的底部操作栏
 *
 * 显示已选数量，提供"取消"和"添加到歌单"两个操作按钮。
 *
 * @param selectedCount 已选中的歌曲数量
 * @param onAddToPlaylist 点击"添加到歌单"的回调
 * @param onClear 点击"取消"的回调
 */
@Composable
fun SurfaceBar(
    selectedCount: Int,
    onAddToPlaylist: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "已选 $selectedCount 首",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onClear) {
                Text("取消")
            }
            FilledTonalButton(onClick = onAddToPlaylist) {
                Icon(
                    painter = painterResource(R.drawable.list_alt_add_24),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("添加到歌单")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// 空状态提示
// ─────────────────────────────────────────────────────────────────

/**
 * 本地音乐为空时的空状态提示
 */
@Composable
fun EmptyMusicHint() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outlineVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "还没有本地音乐",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "点击「添加文件夹」导入本地音乐",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}
