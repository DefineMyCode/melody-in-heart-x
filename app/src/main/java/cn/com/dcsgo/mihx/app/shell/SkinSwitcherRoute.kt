package cn.com.dcsgo.mihx.app.shell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.com.dcsgo.mihx.ui.theme.PlaybackPanelTokens

/**
 * 「样式切换」独立页（2026-09-30 用户拍板，替代 P5 用户自定义皮肤）。
 *
 * ## 范围
 *  - 仅在 [SkinShellResolver.knownSkins] 中的**内置骨架**之间切换；
 *  - 用户导入任意 JSON 皮肤的能力已移除（CustomSkinSection / UserSkinRoute / UserSkinStore /
 *    SkinIdDeriver 全部下线）；
 *  - 删掉了原先 UI 参数调试页里的多组旋钮（行/区块/迷你条），**只保留**
 *    「播放面板封面边长」「播放面板封面圆角」两个旋钮，**整合到本页面底部**；
 *  - 歌手/专辑网格布局在 2026-09-30 下沉为**全局开关**（不分样式，两种样式共用），
 *    样式项只显示名称（`dcsgo.skin.*` id 小字已去掉）；
 *  - 「当前值」摘要块（coverSizeDp=…）同批移除——滑条右侧数字已是当前值。
 *
 * ## 覆盖值归属
 *  - 旋钮的当前值是「当前选中样式」的覆盖，由 AppRoot 持久化（按 skinId 分键）；
 *  - 切换样式时,AppRoot 会载入新样式的覆盖值,保证不同样式的覆盖互不污染。
 *
 * ## 与 UI 参数调试页的差别
 *  - 不再有「导出 JSON」「重置全局」等动作；
 *  - 旋钮范围收窄到仅"播放面板封面边长 / 圆角",与全局默认值 252/20 一致,
 *    实际生效即所见即所得(经 LocalPlaybackPanelTokens 实时下发)。
 */
@Composable
fun SkinSwitcherRoute(
    skinOptions: List<Pair<String, String>>,
    currentSkinId: String,
    panelCoverSizeOverride: Float?,
    panelCoverCornerOverride: Float?,
    onSkinSelected: (String) -> Unit,
    onPanelCoverSizeChange: (Float) -> Unit,
    onPanelCoverCornerChange: (Float) -> Unit,
    gridLayoutEnabled: Boolean,
    onGridLayoutChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val defaults = remember { PlaybackPanelTokens() }
    val effectiveSize = panelCoverSizeOverride ?: defaults.coverSizeDp
    val effectiveCorner = panelCoverCornerOverride ?: defaults.coverCornerDp

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        // 顶部 bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = "样式切换",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        HorizontalDivider()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionTitle("选择样式")
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    skinOptions.forEachIndexed { index, (id, name) ->
                        SkinRow(
                            name = name,
                            selected = id == currentSkinId,
                            onClick = { onSkinSelected(id) },
                        )
                        if (index < skinOptions.lastIndex) HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                }
            }

            SectionTitle("播放面板（当前样式）")
            // 当前选中样式 → 调面板以微调封面尺寸/圆角。
            // 未覆盖(null)时显示默认值;调过则从覆盖值出发,而不是"覆盖从 0 开始"。
            PanelSlider(
                label = "封面边长",
                range = 96f..320f,
                value = effectiveSize,
                onChange = onPanelCoverSizeChange,
            )
            PanelSlider(
                label = "封面圆角",
                range = 0f..40f,
                value = effectiveCorner,
                onChange = onPanelCoverCornerChange,
            )

            SectionTitle("歌手、专辑")
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "网格布局",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = "歌手、专辑以两列网格展示，所有样式通用",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = gridLayoutEnabled,
                        onCheckedChange = onGridLayoutChange,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
    )
}

@Composable
private fun SkinRow(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "当前",
                tint = MaterialTheme.colorScheme.primary,
            )
        } else {
            Spacer(modifier = Modifier.width(20.dp))
        }
    }
}

@Composable
private fun PanelSlider(
    label: String,
    range: ClosedFloatingPointRange<Float>,
    value: Float,
    onChange: (Float) -> Unit,
) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "%.1f".format(local),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )
        }
        Slider(
            value = local,
            valueRange = range,
            onValueChange = {
                local = it
                onChange(it)
            },
        )
    }
}
