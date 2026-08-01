package cn.com.dcsgo.mihx.player.di

import cn.com.dcsgo.mihx.domain.queue.DefaultRandomQueuePlanner
import cn.com.dcsgo.mihx.domain.queue.DefaultUniformRandomPlanner
import cn.com.dcsgo.mihx.domain.queue.PlaybackWindowPlanner
import cn.com.dcsgo.mihx.domain.queue.RandomQueuePlanner
import cn.com.dcsgo.mihx.domain.queue.UniformRandomPlanner
import cn.com.dcsgo.mihx.player.DefaultPlayerFactory
import cn.com.dcsgo.mihx.player.PlayerFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Player DI bindings. [PlayerFactory] is bound to [DefaultPlayerFactory] (plan P1-2).
 * Other player types ([PlaybackController], [SessionTokenProvider],
 * [cn.com.dcsgo.mihx.player.mapper.SongMediaItemMapper], [PlayerPlaybackProgressTicker]) use
 * `@Inject` constructors and need no explicit binding.
 *
 * The [UniformRandomPlanner] / [RandomQueuePlanner] interfaces (declared in :domain) are bound to
 * their default implementations here; [PlaybackWindowPlanner] is provided explicitly because its
 * constructor carries primitive defaults that Hilt cannot supply.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerModule {

    @Binds
    @Singleton
    abstract fun bindPlayerFactory(impl: DefaultPlayerFactory): PlayerFactory

    @Binds
    @Singleton
    abstract fun bindUniformRandomPlanner(impl: DefaultUniformRandomPlanner): UniformRandomPlanner

    @Binds
    @Singleton
    abstract fun bindRandomQueuePlanner(impl: DefaultRandomQueuePlanner): RandomQueuePlanner

    companion object {
        @Provides
        @Singleton
        fun providePlaybackWindowPlanner(): PlaybackWindowPlanner = PlaybackWindowPlanner()
    }
}
