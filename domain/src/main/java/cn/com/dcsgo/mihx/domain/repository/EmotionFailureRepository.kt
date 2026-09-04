package cn.com.dcsgo.mihx.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * 情绪分析失败原因（与设计文档"歌曲分析被跳过"清单一一对应）。
 *
 * 失败会持久化并在情绪详情页向用户展示——否则失败歌曲每轮扫描被无意义重试，
 * 用户也看不到"为什么差几首没分析"。
 */
enum class EmotionFailureReason(val userMessage: String) {
    /** SAF 权限被回收 / 文件被移动删除 / 容器损坏 */
    EXTRACT_FAILED("文件无法读取（权限被回收、文件丢失或已损坏）"),

    /** 容器内无音频轨（Kotlin 块注释可嵌套，注释里不能出现裸的星号斜杠序列） */
    NO_AUDIO_TRACK("文件中没有音频轨"),

    /** 解码后 PCM 不足一个分析窗（约 <5 秒） */
    TOO_SHORT("歌曲太短（不足 5 秒）"),

    /** 超长熔断（>20 分钟，防解码烧内存） */
    TOO_LONG("歌曲超过 20 分钟"),

    /** 单曲解码看门狗 60s 超时 */
    DECODE_TIMEOUT("解码超时"),

    /** FFmpeg-only mime 禁回退 / 无对应解码器——转码为 AAC/FLAC 后可分析 */
    DECODE_UNSUPPORTED_FORMAT("音频格式不受支持（可转码为 AAC/FLAC 后重新导入）"),

    /** TFLite 推理异常 */
    INFERENCE_ERROR("分析引擎异常"),
}

/** 单首歌曲的分析失败记录 */
data class EmotionFailure(
    val songId: Int,
    val reason: EmotionFailureReason,
    /** 最近一次失败时间（epoch 毫秒） */
    val failedAt: Long,
    /** 累计失败次数；>=3 时不再自动重试（仍可手动重试） */
    val attempts: Int,
)

/**
 * 情绪分析失败记录仓库（域层接口）。
 * 实现：:data EmotionFailureStore（DataStore JSON，量级为个别歌曲）。
 */
interface EmotionFailureRepository {
    fun observeFailures(): Flow<Map<Int, EmotionFailure>>

    suspend fun currentFailures(): Map<Int, EmotionFailure>

    /** 记录/累计一次失败（attempts+1，reason/failedAt 覆盖为最新） */
    suspend fun record(songId: Int, reason: EmotionFailureReason)

    /** 分析成功（或手动重试触发）后清除该歌的失败记录 */
    suspend fun clear(songId: Int)

    /** 歌曲被删除时清理对应记录 */
    suspend fun clearAll(songIds: List<Int>)

    /** 手动重试：attempts 归零但保留 reason 供展示？不——重试入口直接清除记录重新入队 */
    suspend fun clearForRetry(songIds: List<Int>)
}
