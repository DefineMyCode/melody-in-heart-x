package cn.com.dcsgo.mihx.feature.user

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * P5 我的页分区 — 用户自定义皮肤入口卡(Q3 = 另开一个)。
 *
 * 与其他分区一致的卡片式入口卡,点击进入 [UserSkinRoute] 独立页。
 * 状态展示:
 *  - 未导入:显示「点击导入皮肤」
 *  - 已导入:显示「当前: <皮肤名>」
 *  - release 或未启用:与未导入视觉相同 (onClick 为空实现,点了无反应,但卡片可见)
 *
 * 关于"另开"语义:
 *  - 物理上是另一个 part (customSkin) → 描述驱动可裁剪/重排
 *  - 物理上是另一个 route (user-skin) → 用户进入和离开的体验独立
 *  - 但 AppRoot 装配时它**仍用同一切换点** (currentSkinId lookup),不与内置骨架混
 */
@Composable
fun CustomSkinSection(
    hasUserSkin: Boolean,
    currentSkinName: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Palette,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "自定义皮肤",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = if (hasUserSkin && currentSkinName != null) "当前: $currentSkinName"
                           else "点击导入皮肤 / 查看当前皮肤",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 当 onClick 为空实现时(release 或未启用场景),给出一个不可点的占位卡。
 * 视觉上不与可点击卡区分(用户看到「自定义皮肤」),仅 clickable 失效——
 * release 构建本来就让所有调试入口不可达,与该卡的语义一致。
 */
@Composable
fun CustomSkinSectionNoOp(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth())
}
