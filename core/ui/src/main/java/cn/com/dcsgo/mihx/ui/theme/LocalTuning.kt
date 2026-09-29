package cn.com.dcsgo.mihx.ui.theme

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * 调参令牌的 CompositionLocal。
 *
 * 为什么用 [compositionLocalOf]（非 static）：阶段 0 的调试面板要求"拖动滑条立即生效"，
 * 非 static 版本值变化时只重组读取方；release 构建不提供任何 provider，
 * 读取方永远拿到默认值（等同原硬编码常量），因此没有额外开销。
 */
val LocalVisTokens: ProvidableCompositionLocal<VisTokens> = compositionLocalOf { VisTokens() }

/** 播放页（首页播放面板）令牌 */
val LocalPlaybackPanelTokens: ProvidableCompositionLocal<PlaybackPanelTokens> =
    compositionLocalOf { PlaybackPanelTokens() }

/** 全局区块间距令牌 */
val LocalSpacingTokens: ProvidableCompositionLocal<SpacingTokens> =
    compositionLocalOf { SpacingTokens() }

/** 底部迷你播放条令牌 */
val LocalMiniPlayerTokens: ProvidableCompositionLocal<MiniPlayerTokens> =
    compositionLocalOf { MiniPlayerTokens() }
