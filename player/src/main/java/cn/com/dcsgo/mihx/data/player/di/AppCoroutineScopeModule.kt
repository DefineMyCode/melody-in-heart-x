package cn.com.dcsgo.mihx.data.player.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 进程级协程作用域限定符。
 *
 * 用于「不应随组件(Service / ViewModel)销毁而被取消」的收尾任务,典型场景是服务销毁时的播放进度落盘:
 * 组件生命周期已经结束,但写盘仍需在后台跑完。切勿把常规业务协程挂到该作用域上。
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppCoroutineScopeModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
