package cn.com.dcsgo.mihx.ui.theme

import androidx.compose.runtime.Immutable

/**
 * 布局/视觉调参令牌（阶段 0：App 内参数调试面板的旋钮集合）。
 *
 * 设计要点：
 *  - **每个字段都必须对应一处已存在的硬编码 dp，且默认值 = 改造前的原值**（视觉零变化），
 *    因此不保留"看起来有用但当前无人消费"的占位字段；
 *  - release 构建不注入任何覆盖值（见 app/src/release 的 AppTuningProvider 空实现），
 *    一律走默认值，等于把硬编码常量换了个位置，行为与性能不变；
 *  - 这些字段同时是插件契约（尺寸部分）的第一版草稿：先用面板跑出"哪些旋钮真的会被反复调"，
 *    再决定哪些进正式契约（见 docs/architecture/PLUGIN_SYSTEM_DESIGN.md）。
 */

/** 歌曲列表行（[cn.com.dcsgo.mihx.ui.components.SongListItem] 默认样式） */
@Immutable
data class VisTokens(
    /** 行垂直内边距，原硬编码 10dp */
    val listRowVerticalPaddingDp: Float = 10f,
    /** 缩略图边长，原硬编码 44dp */
    val listCoverSizeDp: Float = 44f,
    /** 缩略图圆角，原硬编码 10dp */
    val listCoverCornerDp: Float = 10f,
)

/** 播放面板（首页 AlbumCoverSection） */
@Immutable
data class PlaybackPanelTokens(
    /** 专辑封面边长，原硬编码 252dp */
    val coverSizeDp: Float = 252f,
    /** 专辑封面圆角，原硬编码 20dp */
    val coverCornerDp: Float = 20f,
)

/** 全局区块间距 */
@Immutable
data class SpacingTokens(
    /** 首页 LazyColumn 区块纵向间距，原硬编码 24dp */
    val sectionSpacingDp: Float = 24f,
)

/** 底部迷你播放条（[cn.com.dcsgo.mihx.feature.player.MusicPlayerBottomBar]） */
@Immutable
data class MiniPlayerTokens(
    /** 封面边长，原硬编码 40dp */
    val coverSizeDp: Float = 40f,
    /** 封面圆角，原硬编码 8dp */
    val coverCornerDp: Float = 8f,
    /** 进度条粗细，原硬编码 2.5dp */
    val progressHeightDp: Float = 2.5f,
    /** 播放按钮直径，原硬编码 36dp */
    val playButtonSizeDp: Float = 36f,
)
