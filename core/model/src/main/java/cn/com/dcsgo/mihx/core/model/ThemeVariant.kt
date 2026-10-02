package cn.com.dcsgo.mihx.core.model

/**
 * 主题色变体枚举
 *
 * 定义应用的配色基调（在明/暗基础上叠加）。
 * 设置页「主题色」卡按 [entries] 自动遍历渲染，新增枚举即自动新增色块（无需改页面代码，
 * 但 `feature/settings` 的 `ThemeVariantCard` 需同步补 swatch 色板分支）。
 */
enum class ThemeVariant(val label: String) {
    MONO("墨色"),
    VERMILION("朱砂 · 心有乐章"),
    INDIGO("靛蓝静夜"),
    SAGE("苔藓森野"),
    AMBER("琥珀暖忆"),
    SKY("天空澄明"),
    FRESH("新叶青翠"),
    SUNRISE("晨光霞粉"),
}
