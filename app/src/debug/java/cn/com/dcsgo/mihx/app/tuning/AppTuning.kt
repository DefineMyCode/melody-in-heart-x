package cn.com.dcsgo.mihx.app.tuning

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import cn.com.dcsgo.mihx.ui.theme.LocalMiniPlayerTokens
import cn.com.dcsgo.mihx.ui.theme.LocalPlaybackPanelTokens
import cn.com.dcsgo.mihx.ui.theme.LocalSpacingTokens
import cn.com.dcsgo.mihx.ui.theme.LocalVisTokens

/**
 * UI 调参容器。
 *
 * **必须挂在 [cn.com.dcsgo.mihx.ui.theme.MusicplayerTheme] 之内**——排版读取方（app 自身的
 * AppScaffold / feature:home / feature:player 的迷你条）会向上查找 LocalXxxTokens，
 * 若本容器在其外，调用点拿到的是默认实例而非这里的实时值。
 * （搭建期实踩：第一版包在主题外，导致所有调用点解析成未解析引用。）
 *
 * 该类同时是调试面板的呈现接口：面板打开标志 + 开关回调由 [AppRoot] 提供，
 * 令牌通过 [AppTuningProvider] 下发。
 */
class UiTuningController internal constructor(
    val tuning: UiTuning,
    val onTuningChange: (UiTuning) -> Unit,
    val showPanel: Boolean,
    val onShowPanelChange: (Boolean) -> Unit,
    val onExport: () -> Unit,
    val onReset: () -> Unit,
)

/** 需要读取调参状态的消费方：我的页「版本」行（长按打开面板 / 显示当前生效状态） */
data class UiTuningAccess(
    val enabled: Boolean,
    val onOpenPanel: () -> Unit,
    val onExport: () -> Unit,
    /** P3：当前骨架（皮肤）id，与 [UiTuningController.skinOptions] 配合用于验收切换。 */
    val currentSkinId: String? = null,
    val skinOptions: List<Pair<String, String>> = emptyList(),
    val onSkinChange: (String) -> Unit = {},
)

/**
 * 下发调参令牌的 Provider。
 *
 * 这里直接调用 release 就存在的 [LocalVisTokens] 等（而非声明新的 Local）：
 * 若声明成 debug-only 的 Local，release 下调用点会因找不到声明而**编译失败**；
 * 用现有的公开 Local + debug-only Provider，release 不注入任何值即自动回落默认值。
 */
@Composable
fun AppTuningProvider(
    controller: UiTuningController?,
    content: @Composable () -> Unit,
) {
    if (controller == null) {
        content()
        return
    }
    CompositionLocalProvider(
        LocalVisTokens provides controller.tuning.vis,
        LocalPlaybackPanelTokens provides controller.tuning.panel,
        LocalSpacingTokens provides controller.tuning.spacing,
        LocalMiniPlayerTokens provides controller.tuning.mini,
        content = content,
    )
}

/**
 * 调试构建可达：面板状态常驻内存（跨页面保持），可持久化、可导出 JSON。
 * 调参仅影响调试包，不影响正式包。
 */
@Composable
internal fun rememberDebugTuning(): Pair<UiTuningController?, UiTuningAccess> {
    val context = LocalContext.current
    val store = remember(context) { UiTuningStore(context) }
    var tuning by remember { mutableStateOf(store.load()) }
    var showPanel by remember { mutableStateOf(false) }
    // P3：骨架（皮肤）切换状态，仅 debug 可达。
    // 用 SharedPreferences 持久化，因为切换骨架后 App 常需重建导航图（进程内状态会丢）。
    val skinPrefs = remember(context) { context.getSharedPreferences("skin_debug", android.content.Context.MODE_PRIVATE) }
    var currentSkinId by remember {
        mutableStateOf(
            skinPrefs.getString("skin_id", null)
                ?: cn.com.dcsgo.mihx.app.shell.SkinShellResolver.DEFAULT_SKIN_ID,
        )
    }

    val controller = remember(tuning, showPanel, store) {
        UiTuningController(
            tuning = tuning,
            onTuningChange = {
                tuning = it
                store.save(it)
            },
            showPanel = showPanel,
            onShowPanelChange = { showPanel = it },
            onExport = {
                val file = store.export(context, tuning)
                android.widget.Toast.makeText(
                    context,
                    "已导出到 ${file.absolutePath}",
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            },
            onReset = {
                store.reset()
                tuning = UiTuning()
            },
        )
    }

    val skinOptions = remember {
        cn.com.dcsgo.mihx.app.shell.SkinShellResolver.knownSkins.map { it.id to it.name }
    }

    val access = remember(controller, currentSkinId, skinOptions) {
        UiTuningAccess(
            enabled = true,
            onOpenPanel = controller.onShowPanelChange.let { { it(true) } },
            onExport = controller.onExport,
            currentSkinId = currentSkinId,
            skinOptions = skinOptions,
            onSkinChange = { id ->
                currentSkinId = id
                skinPrefs.edit().putString("skin_id", id).apply()
            },
        )
    }
    return controller to access
}
