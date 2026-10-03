package cn.com.dcsgo.mihx.core.model

/**
 * 屏幕方向模式（设置页可配置）。
 *
 * - [SENSOR_AUTO]：跟随系统/传感器自动旋转（Android 默认行为）
 * - [LANDSCAPE]：强制横屏
 * - [PORTRAIT]：强制竖屏
 */
enum class ScreenOrientationMode(val label: String) {
    SENSOR_AUTO("自动旋转"),
    LANDSCAPE("横屏"),
    PORTRAIT("竖屏"),
}