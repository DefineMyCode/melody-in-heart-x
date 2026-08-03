package cn.com.dcsgo.mihx.data.util

/**
 * 音频文件工具函数
 *
 * 统一提供音频格式判断和时间格式化等通用工具，避免各模块重复定义。
 */
object AudioFileUtils {

    /** 支持的音频扩展名集合 */
    private val AUDIO_EXTENSIONS = setOf(
        "mp3", "flac", "wav", "m4a", "ogg", "aac", "opus", "wma", "ape"
    )

    /**
     * 根据 MIME 类型或文件名判断是否为音频文件
     *
     * @param mimeType  文件 MIME 类型（可为 null）
     * @param name      文件名（可为 null）
     * @return 是音频文件则返回 true
     */
    fun isAudioFile(mimeType: String?, name: String?): Boolean {
        if (mimeType?.startsWith("audio/") == true) return true
        val ext = name?.substringAfterLast('.')?.lowercase() ?: return false
        return ext in AUDIO_EXTENSIONS
    }

    /**
     * 从带扩展名的文件名中去除音频扩展名
     *
     * @param fileName 文件名（含扩展名）
     * @return 去掉扩展名后的标题字符串
     */
    fun stripAudioExtension(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return if (ext in AUDIO_EXTENSIONS) fileName.substringBeforeLast('.').trim()
        else fileName.trim()
    }

    /**
     * 将毫秒时长格式化为 mm:ss 或 h:mm:ss 字符串
     *
     * @param timeMs 毫秒数
     * @return 格式化后的时间字符串，如 "3:45" 或 "1:02:30"
     */
    fun formatTime(timeMs: Long): String {
        if (timeMs <= 0) return "0:00"
        val totalSeconds = timeMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }
}
