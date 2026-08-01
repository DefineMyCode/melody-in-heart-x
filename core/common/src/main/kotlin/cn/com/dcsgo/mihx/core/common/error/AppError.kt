package cn.com.dcsgo.mihx.core.common.error

/** Sealed domain error type. */
sealed interface AppError {
    data class IO(val message: String?) : AppError
    data class Decode(val message: String?) : AppError
    data class Unknown(val throwable: Throwable?) : AppError
}
