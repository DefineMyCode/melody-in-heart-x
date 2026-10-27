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
    // performanceLoggingEnabled = true：release 下普通日志全关，但
    // `PerformanceTrace` 白名单登记的操作仍要输出（评审 2026-10-27 · M-20）。
    fun provideAppLogger(): AppLogger =
        AndroidAppLogger(BuildConfig.DEBUG, performanceLoggingEnabled = true)
}
