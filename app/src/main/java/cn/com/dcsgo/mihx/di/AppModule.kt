package cn.com.dcsgo.mihx.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    // Phase 1+: bind player/queue/repository dependencies here.
}
