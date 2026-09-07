package cn.com.dcsgo.mihx.domain.model

/**
 * 文件校验模式。
 *
 * 快速校验：对 URI 存活的歌曲按「文件大小 + 最后修改时间」预筛，
 * 只对指纹变化的歌曲重新提取元数据并更新（秒级完成，绝大多数歌没变则几乎零开销）。
 *
 * 深度校验：对 URI 存活的全部歌曲重新提取元数据并更新（全库扫描，耗时但彻底，
 * 适用于不依赖时间戳的改动场景，如换软件后 SAF 时间戳不可靠的情况）。
 */
enum class FileCheckMode {
    QUICK,
    DEEP,
}
