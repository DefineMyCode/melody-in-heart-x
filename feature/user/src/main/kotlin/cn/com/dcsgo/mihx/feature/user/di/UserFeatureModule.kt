package cn.com.dcsgo.mihx.feature.user.di

import cn.com.dcsgo.mihx.feature.user.UserFacade
import cn.com.dcsgo.mihx.feature.user.UserFacadeImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Feature-level Hilt bindings for 我的 (plan P5-C5/C6). */
@Module
@InstallIn(SingletonComponent::class)
abstract class UserFeatureModule {
    @Binds
    @Singleton
    abstract fun bindUserFacade(impl: UserFacadeImpl): UserFacade
}
