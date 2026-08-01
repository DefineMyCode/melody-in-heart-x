package cn.com.dcsgo.mihx.core.common.di

import cn.com.dcsgo.mihx.core.common.dispatcher.AppDispatchers
import cn.com.dcsgo.mihx.core.common.dispatcher.Dispatcher
import cn.com.dcsgo.mihx.core.common.dispatcher.MihxDispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
object CommonModule {

    @Provides
    @Dispatcher(MihxDispatcher.IO)
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Dispatcher(MihxDispatcher.DEFAULT)
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Dispatcher(MihxDispatcher.MAIN)
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main

    @Provides
    @Dispatcher(MihxDispatcher.UNCONFINED)
    fun provideUnconfinedDispatcher(): CoroutineDispatcher = Dispatchers.Unconfined

    @Provides
    fun provideAppDispatchers(
        @Dispatcher(MihxDispatcher.MAIN) main: CoroutineDispatcher,
        @Dispatcher(MihxDispatcher.IO) io: CoroutineDispatcher,
        @Dispatcher(MihxDispatcher.DEFAULT) default: CoroutineDispatcher,
        @Dispatcher(MihxDispatcher.UNCONFINED) unconfined: CoroutineDispatcher,
    ): AppDispatchers = AppDispatchers(main, io, default, unconfined)
}
