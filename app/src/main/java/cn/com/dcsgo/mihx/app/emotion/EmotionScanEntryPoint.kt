package cn.com.dcsgo.mihx.app.emotion

import cn.com.dcsgo.mihx.data.player.EmotionAnalyzer
import cn.com.dcsgo.mihx.domain.repository.SongEmotionRepository
import cn.com.dcsgo.mihx.domain.repository.SongRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** WorkManager(非 Hilt 管理)取单例的桥. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface EmotionScanEntryPoint {
    fun musicRepository(): SongRepository
    fun emotionRepository(): SongEmotionRepository
    fun emotionAnalyzer(): EmotionAnalyzer
    fun settingsRepository(): cn.com.dcsgo.mihx.domain.repository.PlayerSettingsRepository
    fun emotionFailureRepository(): cn.com.dcsgo.mihx.domain.repository.EmotionFailureRepository
}
