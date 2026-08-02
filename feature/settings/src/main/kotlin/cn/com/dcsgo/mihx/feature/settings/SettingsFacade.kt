package cn.com.dcsgo.mihx.feature.settings

import cn.com.dcsgo.mihx.domain.repository.PlayerSettingsRepository
import cn.com.dcsgo.mihx.domain.repository.ThemeMode
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Settings use-cases (plan P5-C4). Thin boundary over [PlayerSettingsRepository] so the ViewModel
 * stays free of repository plumbing; every toggle exposes a [Flow] for live UI state plus a
 * suspend setter that persists to Preferences DataStore.
 *
 * The feature layer only sees the :domain repository interface — never :data — satisfying the
 * architecture gates (A2/A3).
 */
interface SettingsFacade {
    val uniformRandomEnabled: Flow<Boolean>
    val infinitePlayEnabled: Flow<Boolean>
    val bluetoothEnabled: Flow<Boolean>
    val notificationEnabled: Flow<Boolean>
    val themeMode: Flow<ThemeMode>
    val dynamicColorEnabled: Flow<Boolean>

    suspend fun setUniformRandomEnabled(enabled: Boolean)
    suspend fun setInfinitePlayEnabled(enabled: Boolean)
    suspend fun setBluetoothEnabled(enabled: Boolean)
    suspend fun setNotificationEnabled(enabled: Boolean)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDynamicColorEnabled(enabled: Boolean)
}

@Singleton
class SettingsFacadeImpl @Inject constructor(
    private val settings: PlayerSettingsRepository,
) : SettingsFacade {
    override val uniformRandomEnabled: Flow<Boolean> = settings.observeUniformRandomEnabled()
    override val infinitePlayEnabled: Flow<Boolean> = settings.observeInfinitePlayEnabled()
    override val bluetoothEnabled: Flow<Boolean> = settings.observeBluetoothEnabled()
    override val notificationEnabled: Flow<Boolean> = settings.observeNotificationEnabled()
    override val themeMode: Flow<ThemeMode> = settings.observeThemeMode()
    override val dynamicColorEnabled: Flow<Boolean> = settings.observeDynamicColorEnabled()

    override suspend fun setUniformRandomEnabled(enabled: Boolean) = settings.setUniformRandomEnabled(enabled)

    override suspend fun setInfinitePlayEnabled(enabled: Boolean) = settings.setInfinitePlayEnabled(enabled)

    override suspend fun setBluetoothEnabled(enabled: Boolean) = settings.setBluetoothEnabled(enabled)

    override suspend fun setNotificationEnabled(enabled: Boolean) = settings.setNotificationEnabled(enabled)

    override suspend fun setThemeMode(mode: ThemeMode) = settings.setThemeMode(mode)

    override suspend fun setDynamicColorEnabled(enabled: Boolean) = settings.setDynamicColorEnabled(enabled)
}
