package cn.com.dcsgo.mihx.feature.player.di

import cn.com.dcsgo.mihx.feature.player.PlayerQueueFacade
import cn.com.dcsgo.mihx.feature.player.PlayerQueueFacadeImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Feature-level Hilt bindings. [PlayerQueueFacade] is an interface (plan P2-8); the binding lives
 * here (not in :player's PlayerModule) because the type belongs to :feature:player.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerFeatureModule {
    @Binds
    @Singleton
    abstract fun bindPlayerQueueFacade(impl: PlayerQueueFacadeImpl): PlayerQueueFacade
}
