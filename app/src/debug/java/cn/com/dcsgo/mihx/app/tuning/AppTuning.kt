package cn.com.dcsgo.mihx.app.tuning

import cn.com.dcsgo.mihx.feature.user.ImportSkinResult

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
    // P5：用户导入/还原皮肤的回调。空实现是 release/未启用场景下的兜底；
    // 入口本身在 [cn.com.dcsgo.mihx.feature.user.UserSkinRoute] 拼装。
    //
    // onImportUserSkin 收 JSON 文本，返回 [ImportSkinResult]。**导入失败时返回 [ImportSkinResult.Failed]**
    // 含完整 issue 列表，由调用方在 bottom sheet 展示(Q2)。
    val onImportUserSkin: suspend (String) -> ImportSkinResult = { _: String -> ImportSkinResult.NotHandled },
    val onRestoreDefaultSkin: suspend () -> Unit = {},
    // P5：用户皮肤当前快照(id + name + raw json),AppRoot 用 json 解析外壳。
    // release 恒为 null。Has 与否决定 CustomSkinSection 显示"已装"/"未装"。
    val userSkinId: String? = null,
    val userSkinName: String? = null,
    val userSkinJson: String? = null,
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

    // P5：用户皮肤存储。注意：rememberDebugTuning 是 @Composable internal,
    // 直接 @Inject 不优雅；为保持原架构简洁,这里直接用 context 实例化。
    // 该 store 内部用 applicationContext,不会泄漏 Activity。
    val userSkinStore = remember(context) {
        cn.com.dcsgo.mihx.data.repository.UserSkinStore(context.applicationContext)
    }
    // currentSkinId 也要把"user.skin.*"算进来。读取存储 + 解析后赋 id。
    var userSkinId by remember { mutableStateOf<String?>(null) }
    var userSkinName by remember { mutableStateOf<String?>(null) }
    var userSkinJson by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        userSkinStore.current.collect { stored ->
            userSkinId = stored?.id
            userSkinName = stored?.name
            userSkinJson = stored?.json
        }
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

    // P5：内置 + 用户装的合并展示（Q3 = 另开, 但 AppRoot 拼装时仍按"同一切换点"调用）
    // 用 run {} 强类型,避免 userSkinId/userSkinName 是 String? 让整张表被推成 Pair<String?, String?>
    val skinOptions: List<Pair<String, String>> = remember(userSkinId, userSkinName) {
        val builtin: List<Pair<String, String>> =
            cn.com.dcsgo.mihx.app.shell.SkinShellResolver.knownSkins.map { it.id to it.name }
        val uid: String? = userSkinId
        val uname: String? = userSkinName
        if (uid != null && uname != null) {
            builtin + (uid!! to uname!!)
        } else builtin
    }

    val access = remember(controller, currentSkinId, skinOptions, userSkinId, userSkinJson) {
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
            // P5：导入。Route 里读 JSON 文本后调这里;校验 + 存 DataStore + 切皮肤。
            // 失败返回 Failed(issues) → bottom sheet 展示(Q2)。
            onImportUserSkin = { json: String ->
                try {
                    when (val result = cn.com.dcsgo.mihx.core.skin.SkinParser.parse(json)) {
                        is cn.com.dcsgo.mihx.core.skin.SkinValidation.Valid -> {
                            val id = cn.com.dcsgo.mihx.data.repository.SkinIdDeriver.derive(json)
                            val name = cn.com.dcsgo.mihx.data.repository.SkinIdDeriver.extractName(
                                json,
                                fallback = id,
                            )
                            // 调用方(UserSkinRoute)已在协程里, 直接挂起, 不阻塞主线程
                            userSkinStore.save(json, id, name)
                            // 切到 user skin
                            currentSkinId = id
                            skinPrefs.edit().putString("skin_id", id).apply()
                            ImportSkinResult.Success(id = id, name = name)
                        }
                        is cn.com.dcsgo.mihx.core.skin.SkinValidation.Invalid ->
                            ImportSkinResult.Failed(issues = result.issues)
                    }
                } catch (t: Throwable) {
                    ImportSkinResult.Error(message = t.message ?: "未知错误")
                }
            },
            userSkinId = userSkinId,
            userSkinName = userSkinName,
            userSkinJson = userSkinJson,
            // P5：还原默认(Q4)。清 DataStore + 把 currentSkinId 切回内置默认,
            // 强制 AppRoot 重新解析。
            onRestoreDefaultSkin = {
                try {
                    userSkinStore.clear()   // suspend:调用方已在协程里
                    val defaultId = cn.com.dcsgo.mihx.app.shell.SkinShellResolver.DEFAULT_SKIN_ID
                    currentSkinId = defaultId
                    skinPrefs.edit().putString("skin_id", defaultId).apply()
                } catch (_: Throwable) { /* noop:还原失败不清空当前选择,避免半状态 */ }
            },
        )
    }
    return controller to access
}
