package cn.com.dcsgo.mihx.core.skin

/**
 * 皮肤导入的失败原因。
 *
 * 用户明确要求：**「不满足要求的插件应用会提示安装失败并提示原因」**。
 * 因此这里把失败做成**结构化**的（而不是一句话字符串），便于：
 *  - UI 直接渲染可读原因；
 *  - 单测逐条断言（每种坏输入对应一个 case）；
 *  - 将来做本地化。
 *
 * [field] 指向描述里的具体位置（如 `pages.library.sections[2].part`），
 * 让作者能定位问题而不是盲猜。
 */
data class SkinIssue(
    val code: Code,
    val message: String,
    val field: String = "",
) {
    enum class Code {
        /** 不是合法 JSON，或根不是对象。 */
        MALFORMED_JSON,

        /** schemaVersion 缺失/类型不符/与宿主不兼容。 */
        SCHEMA_VERSION_MISSING,
        SCHEMA_VERSION_UNSUPPORTED,

        /** id / name 缺失或为空。 */
        MISSING_ID,
        MISSING_NAME,

        /** 主题 id 不在宿主注册表内。 */
        UNKNOWN_THEME,

        /** 底栏为空，或项数超出 2~5。 */
        BOTTOM_BAR_EMPTY,
        BOTTOM_BAR_TOO_MANY,
        BOTTOM_BAR_TOO_FEW,

        /** 底栏项字段缺失。 */
        TAB_MISSING_FIELD,
        /** 底栏项 id 重复（会导致渲染 key 冲突）。 */
        TAB_DUPLICATE_ID,
        /** 底栏 target 指向不存在的页面。 */
        TAB_UNKNOWN_TARGET,

        /** 图标名不在宿主图标表内。 */
        UNKNOWN_ICON,

        /** 页面缺失或页面 key 非法。 */
        PAGE_MISSING,

        /** 引用了未登记的零件——fail-closed 的核心。 */
        UNKNOWN_PART,

        /** 零件出现在不允许的层（如把 L6 零件写进描述）。 */
        PART_LAYER_NOT_ALLOWED,

        /** 分区 props 取值非法（如 songList.template 不在允许集合）。 */
        INVALID_PROP_VALUE,

        /** 令牌数值越界。 */
        TOKEN_OUT_OF_RANGE,

        /** 分段页签为空/重复。 */
        SEGMENT_INVALID,

        /** 描述里出现了宿主不认识的视觉概念（保留给未来的零件）。 */
        UNSUPPORTED_CONCEPT,
    }
}

/** 校验结果。 */
sealed interface SkinValidation {
    /** 校验通过。 */
    data class Valid(
        val skin: Skin,
        /**
         * 非致命提示：未知字段被忽略、骨架专属零件等。
         * 用户可见文案不需要展示，但导出/诊断时有用。
         */
        val warnings: List<SkinIssue> = emptyList(),
    ) : SkinValidation

    /** 校验失败，附带**全部**问题（不是只报第一条，便于作者一次改完）。 */
    data class Invalid(val issues: List<SkinIssue>) : SkinValidation {
        /** 给用户看的一句话原因（取首条，或概括条数）。 */
        fun summary(): String = when (issues.size) {
            0 -> "皮肤文件校验失败"
            1 -> issues.first().let { if (it.field.isEmpty()) it.message else "${it.message}（${it.field}）" }
            else -> "${issues.first().message}（共 ${issues.size} 处问题）"
        }
    }
}
