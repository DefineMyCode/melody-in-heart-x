package cn.com.dcsgo.mihx.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * 圆角令牌（对齐 UI 设计文档 §4.1）
 *
 * | 令牌 | 值 | 用途 |
 * |------|-----|------|
 * | [small] | 8dp | 小元素：徽章、开关、小按钮 |
 * | [medium] | 14dp | 卡片、搜索框、封面小图 |
 * | [large] | 20dp | 封面大图、弹窗 |
 * | [xlarge] | 26dp | Hero 板块、原则卡片 |
 */
object UiShapes {
    val small = RoundedCornerShape(8.dp)
    val medium = RoundedCornerShape(14.dp)
    val large = RoundedCornerShape(20.dp)
    val xlarge = RoundedCornerShape(26.dp)
}
