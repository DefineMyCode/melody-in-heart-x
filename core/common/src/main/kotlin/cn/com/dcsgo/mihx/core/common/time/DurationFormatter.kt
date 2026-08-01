package cn.com.dcsgo.mihx.core.common.time

/** Format a millisecond duration to m:ss (or h:mm:ss). */
object DurationFormatter {
    fun format(durationMs: Long): String {
        if (durationMs < 0) return "0:00"
        val totalSeconds = durationMs / 1000
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }
}
