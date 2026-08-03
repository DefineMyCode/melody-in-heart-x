package cn.com.dcsgo.mihx.core.common

sealed interface AppError {
    val message: String
}

data class ImportError(override val message: String) : AppError
data class PlaybackError(override val message: String) : AppError
data class PersistenceError(override val message: String) : AppError
data class PermissionError(override val message: String) : AppError
data class FileDeleteError(override val message: String) : AppError
