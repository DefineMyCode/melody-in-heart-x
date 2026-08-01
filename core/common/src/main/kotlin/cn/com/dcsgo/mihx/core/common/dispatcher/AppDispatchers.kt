package cn.com.dcsgo.mihx.core.common.dispatcher

import kotlinx.coroutines.CoroutineDispatcher

/** Injectable dispatcher set. */
data class AppDispatchers(
    val main: CoroutineDispatcher,
    val io: CoroutineDispatcher,
    val default: CoroutineDispatcher,
    val unconfined: CoroutineDispatcher,
)
