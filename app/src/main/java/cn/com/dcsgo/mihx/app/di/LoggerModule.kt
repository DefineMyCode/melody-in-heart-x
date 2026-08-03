package cn.com.dcsgo.mihx.app.di

import cn.com.dcsgo.mihx.BuildConfig
import cn.com.dcsgo.mihx.core.common.AndroidAppLogger
import cn.com.dcsgo.mihx.core.common.AppLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LoggerModule {
    @Provides
    @Singleton
    fun provideAppLogger(): AppLogger = AndroidAppLogger(BuildConfig.DEBUG)
}
