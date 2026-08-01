package cn.com.dcsgo.mihx.core.common.result

import cn.com.dcsgo.mihx.core.common.error.AppError

/** Result wrapper used by repository / use-case boundaries. */
sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}
