package cn.com.dcsgo.mihx.core.common.dispatcher

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Dispatcher(val dispatcher: MihxDispatcher)

enum class MihxDispatcher { MAIN, IO, DEFAULT, UNCONFINED }
