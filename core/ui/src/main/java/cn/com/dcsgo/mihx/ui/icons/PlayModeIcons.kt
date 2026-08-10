package cn.com.dcsgo.mihx.ui.icons

import androidx.annotation.DrawableRes
import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.core.ui.R

/**
 * 播放模式 → 图标资源的映射。
 *
 * 该映射属于表现层关注点，因此放在 `:core:ui`：
 * `:core:model` 只保留纯数据（[PlayMode.label] 等），不再引用 `R.drawable`，
 * 从而使模型层可以被 JVM 单测直接使用，也避免数据模型与资源打包耦合。
 */
@DrawableRes
fun PlayMode.iconRes(): Int = when (this) {
    PlayMode.SEQUENTIAL -> R.drawable.forward_media_24
    PlayMode.REVERSE -> R.drawable.replay_24
    PlayMode.SHUFFLE -> R.drawable.shuffle_24
}
