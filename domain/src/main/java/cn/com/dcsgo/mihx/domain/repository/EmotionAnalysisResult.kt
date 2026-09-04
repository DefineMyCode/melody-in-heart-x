package cn.com.dcsgo.mihx.domain.repository

import cn.com.dcsgo.mihx.core.model.SongEmotion

/**
 * 情绪分析结果：成功携带 [SongEmotion]，失败携带可展示的原因（与
 * "歌曲分析被跳过"清单一一对应）。
 *
 * 失败原因会持久化并在情绪详情页展示——否则失败歌曲每轮扫描被无意义重试，
 * 用户也看不到"为什么差几首没分析"。
 */
sealed class EmotionAnalysisResult {
    data class Success(val emotion: SongEmotion) : EmotionAnalysisResult()
    data class Failure(val reason: EmotionFailureReason) : EmotionAnalysisResult()
}
