package cn.com.dcsgo.mihx.feature.home

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Calendar

/**
 * 主屏顶部情境问候行（方案D「此刻」落地）。
 *
 * 「晚上好 · 今晚已听 14 首」——时段问候 + 当日已听歌数。
 * ponytail: 不做问候语配置/多语言/动画，纯静态文案，需要时再加。
 */
@Composable
fun ContextGreetingRow(
    todaySongCount: Int,
    modifier: Modifier = Modifier,
) {
    // ponytail: 不用定时刷新——问候语精确到"段"，用户切页自然重组
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when (hour) {
        in 5..10 -> "早上好"
        in 11..13 -> "中午好"
        in 14..17 -> "下午好"
        in 18..22 -> "晚上好"
        else -> "夜深了"
    }
    val text = when {
        todaySongCount <= 0 -> greeting
        hour < 5 || hour > 22 -> "$greeting · 今天已听 $todaySongCount 首"
        else -> "$greeting · 今晚已听 $todaySongCount 首"
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
