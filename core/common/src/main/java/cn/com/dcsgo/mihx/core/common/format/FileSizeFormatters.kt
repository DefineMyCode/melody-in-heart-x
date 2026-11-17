package cn.com.dcsgo.mihx.core.common.format

import java.util.Locale

/**
 * 文件大小格式化：字节 → B / KB / MB / GB。
 *
 * 与歌曲详情弹窗展示的规则保持一致（1 KB = 1024 B），便于「存储与音质统计」和
 * 单曲信息里的文件大小使用同一套口径。
 */
fun formatFileSize(bytes: Long): String = when {
    bytes < 0 -> "未知"
    bytes < 1024 -> "$bytes B"
    bytes < 1024L * 1024 -> String.format(Locale.CHINA, "%.1f KB", bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> String.format(Locale.CHINA, "%.1f MB", bytes / (1024.0 * 1024))
    else -> String.format(Locale.CHINA, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
}
