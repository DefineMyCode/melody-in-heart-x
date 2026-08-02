package cn.com.dcsgo.mihx.feature.settings.di

import cn.com.dcsgo.mihx.feature.settings.SettingsFacade
import cn.com.dcsgo.mihx.feature.settings.SettingsFacadeImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Feature-level Hilt bindings for 设置 (plan P5-C4). [SettingsFacade] is an interface (thin
 * boundary over [cn.com.dcsgo.mihx.domain.repository.PlayerSettingsRepository]); the binding lives
 * here because the type belongs to :feature:settings.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsFeatureModule {
    @Binds
    @Singleton
    abstract fun bindSettingsFacade(impl: SettingsFacadeImpl): SettingsFacade
}
