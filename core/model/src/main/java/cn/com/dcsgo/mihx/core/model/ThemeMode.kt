package cn.com.dcsgo.mihx.core.model

/**
 * 主题模式枚举
 *
 * 定义应用的主题外观策略。
 */
enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色"),
}
