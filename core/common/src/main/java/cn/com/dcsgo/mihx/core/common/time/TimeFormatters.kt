package cn.com.dcsgo.mihx.core.common.time

/**
 * Format a duration in milliseconds as m:ss or h:mm:ss for compact playback UI.
 */
fun formatDurationTime(timeMs: Long): String {
    if (timeMs <= 0) return "0:00"
    val totalSeconds = timeMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/**
 * 将时长格式化为中文「x小时 y分」。
 * 示例：1小时 05分 / 40分 / 2小时 / 0分。
 */
fun formatHoursMinutes(timeMs: Long): String {
    if (timeMs <= 0) return "0分"
    val totalMinutes = timeMs / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}小时 %02d分".format(minutes)
        hours > 0 -> "${hours}小时"
        else -> "${minutes}分"
    }
}
