package cn.com.dcsgo.mihx.feature.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.com.dcsgo.mihx.core.model.Song
import kotlinx.coroutines.delay

/** Hi-Res 判定阈值：采样率 ≥ 88200 Hz */
private const val HI_RES_THRESHOLD_HZ = 88200

// ─────────────────────────────────────────────────────────────────────────────
// 一键复制按钮（歌曲名 / 艺术家名）
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 歌曲信息行：文字 + 一键复制按钮
 *
 * 用于首页播放区的歌曲名和艺术家名旁，点击复制图标可复制文字到剪贴板。
 * 复制成功后图标短暂变为 ✓ 提示。
 *
 * @param text        要显示的文字
 * @param isTitle     是否为歌曲名（影响字体大小和粗细）
 * @param onClick     点击文字的回调（可空，用于跳转歌手/专辑详情等）
 * @param onCopied    复制成功后的回调（可用于触发 Toast）
 */
@Composable
fun CopyableText(
    text: String,
    isTitle: Boolean = false,
    onClick: (() -> Unit)? = null,
    onCopied: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var justCopied by remember { mutableStateOf(false) }

    // 复制后 1.5 秒恢复图标
    LaunchedEffect(justCopied) {
        if (justCopied) {
            delay(1500L)
            justCopied = false
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        Text(
            text = text,
            style = if (isTitle) MaterialTheme.typography.headlineMedium
                    else MaterialTheme.typography.bodyMedium,
            fontWeight = if (isTitle) FontWeight.Bold else FontWeight.Normal,
            color = if (isTitle) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .then(
                    if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
                )
        )
        Spacer(modifier = Modifier.width(4.dp))
        // 复制按钮：点击复制文字到剪贴板
        Icon(
            painter = painterResource(
                id = if (justCopied) R.drawable.check_24 else R.drawable.content_copy_24
            ),
            contentDescription = if (justCopied) "已复制" else "复制",
            modifier = Modifier
                .size(18.dp)
                .clickable {
                    copyToClipboard(context, text)
                    justCopied = true
                    onCopied(text)
                },
            tint = if (justCopied) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

/** 将文本复制到系统剪贴板 */
private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Copied text", text))
}

// ─────────────────────────────────────────────────────────────────────────────
// 同名歌曲版本选择下拉菜单
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 同名歌曲版本选择器
 *
 * 当当前播放的歌曲存在同名版本时，显示版本数量和一个下拉选择器，
 * 列出所有同名版本及其采样率，用户可点击切换到不同版本。
 * 只有一首同名歌曲时不显示版本切换部分（无需切换）。
 *
 * @param sameNameSongs    同名歌曲列表（按采样率降序排列，包含当前歌曲）
 * @param currentSongId    当前播放歌曲的 ID（用于高亮标识）
 * @param onVersionSelect  切换版本的回调
 */
@Composable
fun VersionSelector(
    sameNameSongs: List<Song>,
    currentSongId: Int,
    onVersionSelect: (Song) -> Unit
) {
    val hasMultipleVersions = sameNameSongs.size > 1

    // 不需要显示时直接跳过
    if (!hasMultipleVersions) return

    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 版本数量提示
        Text(
            text = "${sameNameSongs.size} 个版本",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.width(4.dp))

        // 版本切换按钮 + 下拉菜单
        Box {
            Icon(
                painter = painterResource(id = R.drawable.text_compare_24),
                contentDescription = "切换版本",
                modifier = Modifier
                    .size(18.dp)
                    .clickable { expanded = true },
                tint = MaterialTheme.colorScheme.primary
            )

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                sameNameSongs.forEach { song ->
                    val isCurrent = song.id == currentSongId
                    val label = buildVersionLabel(song)
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = label,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = {
                            expanded = false
                            if (!isCurrent) onVersionSelect(song)
                        },
                        trailingIcon = {
                            if (isCurrent) {
                                Text(
                                    text = "当前",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Hi-Res 徽章
 *
 * 当采样率 ≥ 88200 Hz 时显示 Hi-Res 音频徽章。
 *
 * @param sampleRate 采样率（Hz）
 * @param modifier 修饰符
 */
@Composable
fun HiResBadge(
    sampleRate: Int,
    modifier: Modifier = Modifier
) {
    if (sampleRate < HI_RES_THRESHOLD_HZ) return

    Icon(
        painter = painterResource(id = R.drawable.hi_res),
        contentDescription = "Hi-Res 音频（${sampleRate / 1000}kHz）",
        modifier = modifier.size(40.dp),
        tint = androidx.compose.ui.graphics.Color.Unspecified  // 保留原始颜色
    )
}

/**
 * 构建版本标签文字
 *
 * 格式：采样率（如 "48kHz"）
 */
private fun buildVersionLabel(song: Song): String {
    return if (song.sampleRate > 0) "${song.sampleRate / 1000}kHz" else "未知采样率"
}
