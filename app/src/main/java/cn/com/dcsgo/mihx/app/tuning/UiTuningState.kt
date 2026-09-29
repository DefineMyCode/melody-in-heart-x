package cn.com.dcsgo.mihx.app.tuning

import android.content.Context
import cn.com.dcsgo.mihx.ui.theme.MiniPlayerTokens
import cn.com.dcsgo.mihx.ui.theme.PlaybackPanelTokens
import cn.com.dcsgo.mihx.ui.theme.SpacingTokens
import cn.com.dcsgo.mihx.ui.theme.VisTokens
import org.json.JSONObject
import java.io.File

/**
 * UI 调参状态（阶段 0：App 内参数调试面板）。
 *
 * 目的：解决"改一个 dp 要等构建 + 发 APK"的痛点——把散落各页的硬编码尺寸收敛成一组令牌，
 * 面板里实时可调、可持久化、可导出 JSON。
 *
 * 导出的 JSON 就是后续「外观包 / 插件契约」的第一版草稿：先用面板跑出哪些旋钮真的会被反复调，
 * 再决定哪些进正式契约（见 docs/architecture/PLUGIN_SYSTEM_DESIGN.md）。
 *
 * 仅在 debug 构建可达（见 [ProvideUiTuning]）；release 恒为默认值，等同原硬编码。
 */
data class UiTuning(
    val vis: VisTokens = VisTokens(),
    val panel: PlaybackPanelTokens = PlaybackPanelTokens(),
    val spacing: SpacingTokens = SpacingTokens(),
    val mini: MiniPlayerTokens = MiniPlayerTokens(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("schemaVersion", SCHEMA_VERSION)
        put("vis", vis.toJson())
        put("playbackPanel", panel.toJson())
        put("spacing", spacing.toJson())
        put("miniPlayer", mini.toJson())
    }

    fun prettyJson(): String = toJson().toString(2)

    companion object {
        const val SCHEMA_VERSION = 1

        private fun VisTokens.toJson() = JSONObject().apply {
            put("listRowVerticalPaddingDp", listRowVerticalPaddingDp.toDouble())
            put("listCoverSizeDp", listCoverSizeDp.toDouble())
            put("listCoverCornerDp", listCoverCornerDp.toDouble())
        }

        private fun PlaybackPanelTokens.toJson() = JSONObject().apply {
            put("coverSizeDp", coverSizeDp.toDouble())
            put("coverCornerDp", coverCornerDp.toDouble())
        }

        private fun SpacingTokens.toJson() = JSONObject().apply {
            put("sectionSpacingDp", sectionSpacingDp.toDouble())
        }

        private fun MiniPlayerTokens.toJson() = JSONObject().apply {
            put("coverSizeDp", coverSizeDp.toDouble())
            put("coverCornerDp", coverCornerDp.toDouble())
            put("progressHeightDp", progressHeightDp.toDouble())
            put("playButtonSizeDp", playButtonSizeDp.toDouble())
        }

        /** 容错解析：字段缺失/类型不符一律回落默认值，保证不会因为一份坏 JSON 崩掉调试面板 */
        fun fromJson(text: String): UiTuning? = runCatching {
            val root = JSONObject(text)
            if (root.optInt("schemaVersion", -1) != SCHEMA_VERSION) return null
            val defaults = UiTuning()
            fun obj(key: String) = root.optJSONObject(key)
            val visObj = obj("vis")
            val panelObj = obj("playbackPanel")
            val spacingObj = obj("spacing")
            val miniObj = obj("miniPlayer")
            UiTuning(
                vis = VisTokens(
                    listRowVerticalPaddingDp = visObj.optDoubleOr("listRowVerticalPaddingDp", defaults.vis.listRowVerticalPaddingDp),
                    listCoverSizeDp = visObj.optDoubleOr("listCoverSizeDp", defaults.vis.listCoverSizeDp),
                    listCoverCornerDp = visObj.optDoubleOr("listCoverCornerDp", defaults.vis.listCoverCornerDp),
                ),
                panel = PlaybackPanelTokens(
                    coverSizeDp = panelObj.optDoubleOr("coverSizeDp", defaults.panel.coverSizeDp),
                    coverCornerDp = panelObj.optDoubleOr("coverCornerDp", defaults.panel.coverCornerDp),
                ),
                spacing = SpacingTokens(
                    sectionSpacingDp = spacingObj.optDoubleOr("sectionSpacingDp", defaults.spacing.sectionSpacingDp),
                ),
                mini = MiniPlayerTokens(
                    coverSizeDp = miniObj.optDoubleOr("coverSizeDp", defaults.mini.coverSizeDp),
                    coverCornerDp = miniObj.optDoubleOr("coverCornerDp", defaults.mini.coverCornerDp),
                    progressHeightDp = miniObj.optDoubleOr("progressHeightDp", defaults.mini.progressHeightDp),
                    playButtonSizeDp = miniObj.optDoubleOr("playButtonSizeDp", defaults.mini.playButtonSizeDp),
                ),
            )
        }.getOrNull()

        /** 可空接收者：字段缺失/NaN/负数一律回落 fallback，保证坏 JSON 不会让面板崩 */
        private fun JSONObject?.optDoubleOr(key: String, fallback: Float): Float {
            val self = this ?: return fallback
            val value = self.optDouble(key, fallback.toDouble())
            return if (value.isNaN() || value < 0.0) fallback else value.toFloat()
        }
    }
}

/**
 * 调参状态持久化：调试面板调完的值重启后仍在（否则每次开 App 都要重调）。
 * 单独一个 prefs 文件，release 构建永远不会写入它。
 */
class UiTuningStore(context: Context) {
    private val prefs = context.getSharedPreferences("ui_tuning_debug", Context.MODE_PRIVATE)

    fun load(): UiTuning {
        val text = prefs.getString(KEY_JSON, null) ?: return UiTuning()
        return UiTuning.fromJson(text) ?: UiTuning()
    }

    fun save(tuning: UiTuning) {
        prefs.edit().putString(KEY_JSON, tuning.toJson().toString()).apply()
    }

    fun reset() {
        prefs.edit().remove(KEY_JSON).apply()
    }

    /** 导出到应用外部私有目录，便于 adb pull 或文件管理器取用 */
    fun export(context: Context, tuning: UiTuning): File {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(dir, "ui-tuning.json")
        file.writeText(tuning.prettyJson())
        return file
    }

    companion object {
        private const val KEY_JSON = "tuning_json"
    }
}
