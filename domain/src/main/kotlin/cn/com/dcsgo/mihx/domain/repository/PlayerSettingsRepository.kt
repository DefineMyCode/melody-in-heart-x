package cn.com.dcsgo.mihx.domain.repository

interface PlayerSettingsRepository {
    suspend fun isUniformRandomEnabled(): Boolean
    suspend fun setUniformRandomEnabled(enabled: Boolean)
}
