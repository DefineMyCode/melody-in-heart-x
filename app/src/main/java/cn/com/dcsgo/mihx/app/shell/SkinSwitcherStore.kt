package cn.com.dcsgo.mihx.app.shell

import android.content.Context

/**
 * 样式切换状态持久化（2026-09-30 用户拍板后替代原 P5 用户自定义皮肤）。
 *
 * ## 范围
 *  - 仅 [SkinShellResolver.knownSkins] 列表中**内置骨架**之间切换；
 *  - 删除了"导入任意 JSON 皮肤"路径（`onImportUserSkin` / `UserSkinStore` / `SkinIdDeriver` 已移除）。
 *
 * ## 存储
 *  - 单一 SharedPreferences 文件 `skin_switcher`：
 *    - `active_id` 当前选中的内置 skinId（String，缺省 = [SkinShellResolver.DEFAULT_SKIN_ID]）。
 *    - `panel_cover_size_<id>` / `panel_cover_corner_<id>` 当前选中样式的播放面板覆盖值。
 *  - 覆盖值按 skinId 分键，**保证切换样式时载入对应样式的覆盖**，不同样式的覆盖互不污染。
 *
 * ## 容错
 *  - 读取时类型不匹配 / 越界（< 0）一律返回 null → 调用方落到默认值，
 *    不会让"上次存坏的值"把播放面板搞崩。
 */
class SkinSwitcherStore(context: Context) {

    private val prefs = context.getSharedPreferences("skin_switcher", Context.MODE_PRIVATE)

    fun activeId(): String? = prefs.getString(KEY_ACTIVE_ID, null)

    fun setActiveId(id: String) {
        prefs.edit().putString(KEY_ACTIVE_ID, id).apply()
    }

    fun panelCoverSize(skinId: String): Float? = readPositiveFloat(panelSizeKey(skinId))

    fun setPanelCoverSize(skinId: String, value: Float) {
        prefs.edit().putFloat(panelSizeKey(skinId), value).apply()
    }

    fun panelCoverCorner(skinId: String): Float? = readPositiveFloat(panelCornerKey(skinId))

    fun setPanelCoverCorner(skinId: String, value: Float) {
        prefs.edit().putFloat(panelCornerKey(skinId), value).apply()
    }

    private fun readPositiveFloat(key: String): Float? {
        if (!prefs.contains(key)) return null
        val v = prefs.getFloat(key, -1f)
        // 旧 prefs 写入 NaN 或负值（防御编程）→ 当作"未设过"，回落默认。
        return if (v.isNaN() || v < 0f) null else v
    }

    private fun panelSizeKey(skinId: String): String = "panel_cover_size_$skinId"

    private fun panelCornerKey(skinId: String): String = "panel_cover_corner_$skinId"

    companion object {
        private const val KEY_ACTIVE_ID = "active_id"
    }
}
