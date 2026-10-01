package cn.com.dcsgo.mihx.app.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.com.dcsgo.mihx.R

/**
 * 「随心播放」入口条（2026-10-01 用户拍板：双页样式、播放队列为空时充当迷你条的位置）。
 *
 * 位置与视觉完全对齐迷你播放条 [cn.com.dcsgo.mihx.feature.player.MusicPlayerBottomBar]：
 * 同样的顶部 0.5dp 分隔线 + surfaceContainerLowest 底 + 40dp 左图标 + 36dp 右圆形按钮，
 * 只是内容从「歌曲信息 + 播放控制」换成「shuffle 图标 + 随心播放」。
 *
 * 显示条件由纯策略函数 [shouldShowLuckyPlayEntry] 决定（可 JVM 单测），
 * 本组件只负责渲染与回调，不持有任何状态。
 *
 * 图标统一用随心播放的 shuffle（`R.drawable.shuffle_24`），与播放页 FAB / 我的页入口卡同款。
 */
@Composable
fun LuckyPlayEntryBar(
    onLuckyPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        // 顶部 out1 分隔线（与迷你条同一处理）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onLuckyPlayClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左侧：40dp 圆角图标容器（对齐迷你条 40dp 封面位）
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.shuffle_24),
                    contentDescription = "随心播放",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "随心播放",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            // 右侧：36dp 圆形按钮（对齐迷你条播放按钮位）
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.shuffle_24),
                    contentDescription = "开始随心播放",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}
