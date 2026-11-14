package cn.com.dcsgo.mihx.feature.user

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.com.dcsgo.mihx.ui.components.SpatialAudioIcon

/**
 * 音质分级与空间音频说明（点击「我的」页 Logo 打开）。
 *
 * 颜色全部来自 MaterialTheme，自动适配深浅色主题。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioQualityGuideSheet(
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "音质说明",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "音质分级与空间音频",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "按文件实际轨道规格判断。扩展名（如 .m4a）只是容器，不代表音质。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            GuideSectionLabel("音质档位")
            GuideCard {
                GuideQualityRow("标准", "有损，码率低于 192kbps")
                GuideQualityRow("较高", "有损，192–319kbps")
                GuideQualityRow("极高", "有损，320kbps 及以上")
                GuideQualityRow("无损", "无损编码，16bit / 44.1kHz 起")
                GuideQualityRow("高解析", "无损编码，24bit 且 48kHz 及以上；DSD 直接归入")
            }

            GuideSectionLabel("编码类型")
            GuideCard {
                GuideQualityRow("有损", "MP3 · AAC · Opus · Vorbis · WMA（常见有损） · E-AC-3 / DDP / AC-4")
                GuideQualityRow("无损", "FLAC · ALAC · WAV/PCM · AIFF · APE · DSD")
                GuideBullet("扩展名只是容器：.m4a 可能装 AAC，也可能装 ALAC；以实际轨道编码为准。")
            }

            GuideSectionLabel("空间音频") {
                SpatialAudioIcon(modifier = Modifier.size(16.dp))
            }
            GuideCard {
                GuideRuleRow("E-AC-3 JOC", "audio/eac3-joc · 直接识别为空间音频")
                GuideRuleRow("AC-4", "audio/ac4 · 仅多声道（大于 2 声道）才算；2.0 AC-4 不算")
                GuideRuleRow("E-AC-3", "audio/eac3 · 只展示多声道，不标空间音频")
            }

            GuideCard {
                GuideBullet("只做识别与展示，不承诺 Atmos 对象音频回放。")
                GuideBullet("老数据需要执行一次「深度校验」，才会回填库内音质与空间音频信息。")
                GuideBullet("不支持空间音频时，列表和版本切换不会显示图标。")
                GuideBullet("详情弹窗始终实时读取文件，不受库内旧数据影响。")
            }

            Spacer(modifier = Modifier.height(2.dp))
            FilledTonalButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("知道了")
            }
        }
    }
}

@Composable
private fun GuideSectionLabel(
    text: String,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        trailing?.invoke()
    }
}

@Composable
private fun GuideCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = RoundedCornerShape(14.dp),
            )
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun GuideQualityRow(
    label: String,
    rule: String,
) {
    val isHiRes = label == "高解析"
    val isLossless = isHiRes || label == "无损"

    val background = when {
        isHiRes -> MaterialTheme.colorScheme.primary
        isLossless -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val foreground = when {
        isHiRes -> MaterialTheme.colorScheme.onPrimary
        isLossless -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = foreground,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(54.dp)
                .background(color = background, shape = RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 3.dp),
        )
        Text(
            text = rule,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun GuideRuleRow(
    name: String,
    rule: String,
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(5.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = rule,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GuideBullet(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "·",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}
