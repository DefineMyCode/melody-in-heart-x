package cn.com.dcsgo.mihx.player.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Player DI module. Phase 1 adds [PlayerFactory], [PlaybackController],
 * windowed-queue planners and bluetooth listeners here.
 */
@Module
@InstallIn(SingletonComponent::class)
object PlayerModule
