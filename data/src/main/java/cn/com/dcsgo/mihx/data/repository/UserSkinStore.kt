package cn.com.dcsgo.mihx.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * P5：用户自定义皮肤的持久化（P5 范围按用户裁定:单槽位 + 还原默认）。
 *
 * ## 设计
 *
 * - **单槽位**：只存一份用户装的 JSON；新装覆盖旧装。
 * - **DataStore Preferences**：与 PlayerSettingsDataStore 同栈,避免引新依赖。
 * - **存的就是 raw JSON 字符串**：校验在装配时跑（fail-safe）；导入失败则
 *   **在调肤引擎里捕获并展示 issue**,**不写回 DataStore**——这条路径
 *   与"启动装配失败回落默认"严格分开,见 plugin-shell-contract.md §11。
 *
 * ## 何时失败
 *
 * 装配时机点（[SkinShellResolver.resolveUserSkinOrFallback]）会重新校验一次:
 *  - 正常情况不会失败（导入时已校验过）
 *  - 万一失败（DataStore 里的 JSON 与零件库版本漂移等）→ 装配层回落默认,
 *    并**清掉**这条 user skin 防止每次启动都重试失败
 *  - 永远不抛异常给 UI 层（fail-safe）
 */
private val Context.userSkinDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_skin",
)

class UserSkinStore(private val context: Context) {

    private val jsonKey = stringPreferencesKey("json")
    private val idKey = stringPreferencesKey("id")
    private val nameKey = stringPreferencesKey("name")

    /** 装配用的两件套: JSON + 计算出来的 id（id 由 [SkinIdDeriver] 算,装配侧不参与） */
    data class Stored(
        val json: String,
        val id: String,
        val name: String,
    )

    val current: Flow<Stored?> = context.userSkinDataStore.data.map { prefs ->
        val json = prefs[jsonKey] ?: return@map null
        val id = prefs[idKey] ?: return@map null
        val name = prefs[nameKey] ?: id
        Stored(json = json, id = id, name = name)
    }

    /**
     * 写入:覆盖之前的（Q4 = 单槽位）。
     * @return 写入结果（供 UI 层做后续切皮肤）
     */
    suspend fun save(json: String, id: String, name: String): Stored {
        val stored = Stored(json, id, name)
        context.userSkinDataStore.edit { prefs ->
            prefs[jsonKey] = json
            prefs[idKey] = id
            prefs[nameKey] = name
        }
        return stored
    }

    /** 还原默认（Q4）:清空 DataStore,装配侧自然回落内置默认。 */
    suspend fun clear() {
        context.userSkinDataStore.edit { prefs ->
            prefs.remove(jsonKey)
            prefs.remove(idKey)
            prefs.remove(nameKey)
        }
    }
}

/**
 * 单纯算 id —— 不依赖皮肤模型,纯 sha256。
 *
 * 为什么是 sha256 前 16 位而不是更复杂:皮肤描述够大,前 16 位足以区分;
 * 简单意味着"装一次算一次"无副作用,也不会和"内置 id 撞车"(内置都有
 * `dcsgo.skin.` 前缀,user skin 用 sha256 不会撞)。
 */
object SkinIdDeriver {
    fun derive(json: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(json.toByteArray(Charsets.UTF_8))
        // 取前 8 字节 = 16 个 hex 字符
        val hex = StringBuilder(16)
        for (i in 0..7) {
            val v = bytes[i].toInt() and 0xff
            hex.append("%02x".format(v))
        }
        return "user.skin.$hex"
    }

    /**
     * 从 JSON 中提取 "name" 字段作为皮肤展示名;失败回落 id。
     *
     * 故意写得宽松（catch Throwable）：解析阶段如果把"读 JSON 失败"作为异常抛出,
     * 不能让这里 panic——name 是 UI 友好的,不可得就降级。
     */
    fun extractName(json: String, fallback: String): String = try {
        val obj = org.json.JSONObject(json)
        obj.optString("name").takeIf { it.isNotBlank() } ?: fallback
    } catch (t: Throwable) {
        fallback
    }
}
