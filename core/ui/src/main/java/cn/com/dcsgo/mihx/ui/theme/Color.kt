package cn.com.dcsgo.mihx.ui.theme

/**
 * 主题配色定义已迁移（2026-09-29，插件系统阶段 1 前置改造）。
 *
 * 原 `internal data class ThemePalette` 与四套预设（MonoLight/MonoDark/VermilionDay/VermilionNight）
 * 已上提为**公开**的 [ThemeTokens]（ARGB Int 字段），原因：
 *  - 插件系统（T2 代码插件路线）需要跨模块引用配色，`internal` 无法被插件侧访问；
 *  - 用 Int ARGB 而非 compose `Color`，使未来 `:plugin:api` 不必依赖 compose-ui 就能承载配色契约。
 *
 * 四套内置预设现位于 [ThemeTokens.Companion]（MonoLight / MonoDark / VermilionDay / VermilionNight），
 * 数值与原实现逐字段一致，视觉无变化。
 */
