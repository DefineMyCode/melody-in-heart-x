package cn.com.dcsgo.mihx.ui.components

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * 情绪校准保存控制器: 由 :app 在 NavHost 顶层 provide,
 * 所有渲染 SongInfoDialog/SongEmotionSection 的页面自动获得"不像？标记"能力,
 * 无需逐层透传回调.
 */
interface EmotionCorrectionController {
    /** 保存校准(词条集合). 返回 false 表示该歌尚未分析, 无法校准. */
    fun save(songId: Int, words: Set<String>): Boolean
}

val LocalEmotionCorrectionController: ProvidableCompositionLocal<EmotionCorrectionController?> =
    compositionLocalOf { null }
