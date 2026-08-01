package cn.com.dcsgo.mihx.player.di

import cn.com.dcsgo.mihx.player.DefaultPlayerFactory
import cn.com.dcsgo.mihx.player.PlayerFactory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Player DI bindings. [PlayerFactory] is bound to [DefaultPlayerFactory] (plan P1-2).
 * Other player types ([PlaybackController], [SessionTokenProvider],
 * [cn.com.dcsgo.mihx.player.mapper.SongMediaItemMapper], [PlayerPlaybackProgressTicker]) use
 * `@Inject` constructors and need no explicit binding.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerModule {

    @Binds
    @Singleton
    abstract fun bindPlayerFactory(impl: DefaultPlayerFactory): PlayerFactory
}
