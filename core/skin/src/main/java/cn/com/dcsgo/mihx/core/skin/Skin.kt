package cn.com.dcsgo.mihx.core.skin

/**
 * 皮肤描述模型（路线 B：声明式结构 + 零件库）。
 *
 * 设计文档：`docs/architecture/PLUGIN_SHELL_DESIGN.md`
 *
 * 核心主张：皮肤**不含任何可执行代码**，只是一份 JSON 描述；宿主按描述**装配既有零件**。
 * 因此皮肤在组合期零崩溃风险，且导入时可逐字段校验并给出精确失败原因。
 *
 * 本文件只放**数据模型**。解析与校验在 [SkinParser]/[SkinValidation]，内置默认骨架在
 * [DefaultSkin]。
 *
 * 词汇：
 *  - `part`     宿主零件库里的一个可视零件名（白名单见 [SkinPartCatalog]）
 *  - `target`   底栏项指向的页面 key
 *  - `pageKey`  描述内自定义的页面标识（不是路由，路由由宿主按 key 映射）
 */

/** 皮肤描述根对象。 */
data class Skin(
    val schemaVersion: Int,
    val id: String,
    val name: String,
    val tokens: SkinTokens,
    val shell: SkinShell,
    val pages: Map<String, SkinPage>,
    val startPage: String,
    val nowPlaying: NowPlayingConfig = NowPlayingConfig(),
    /** 播放页的进入方式：[PlayerEntry.TAB] 普通 Tab / [PlayerEntry.SHEET] 全局抽屉。 */
    val playerEntry: PlayerEntry = PlayerEntry.TAB,
)

/** 结构 + 视觉令牌。 */
data class SkinTokens(
    /** 主题变体 id，对应 `ThemeVariant` 的 id 注册表（MONO / VERMILION）。 */
    val theme: String,
    /** 是否深色。 */
    val dark: Boolean,
    /**
     * 容器填充：**0 = 零实心块**（心乐现状），1 = 实心卡片（网易云式）。
     *
     * 这是"看起来像另一个 App"的关键令牌之一——已实测色板本身改不出差异感
     * （墨色 #121212 vs 网易云深色 #1A1A1A 仅差 8 级灰）。
     */
    val surface: Float,
    /** 卡片/封面圆角（dp）。 */
    val cardRadiusDp: Float,
    /** 描边粗细（dp），0 = 无描边。 */
    val hairlineDp: Float,
    /** 列表行之间的垂直间距（dp）。 */
    val rowGapDp: Float,
    /** 列表行封面边长（dp）。 */
    val artSizeDp: Float,
    /** 列表行封面圆角（dp）。 */
    val artRadiusDp: Float,
    /** 播放页封面圆角（dp）。 */
    val nowPlayingRadiusDp: Float,
)

/** 外壳：底栏 / 迷你条 / 播放进入方式。 */
data class SkinShell(
    val bottomBar: List<SkinTab>,
    val miniPlayer: Boolean = false,
)

/** 底栏的一项。 */
data class SkinTab(
    val id: String,
    val label: String,
    /** 图标名，映射到宿主图标表（不是裸资源 id——见 [IconNames]）。 */
    val icon: String,
    /** 指向 [Skin.pages] 里的页面 key。 */
    val target: String,
)

/** 一个页面：由若干分区顺序装配而成。 */
data class SkinPage(
    /** 固定内容页（一屏不滚）；动态列表页为 false。 */
    val fixedOneScreen: Boolean = false,
    val header: SkinPageHeader? = null,
    val search: Boolean = false,
    /** 分段页签（如有）；每段各自有分区列表。 */
    val segments: List<String> = emptyList(),
    val defaultSegment: String? = null,
    /** 无分段时的分区；有分段时用 [sectionsBySegment]。 */
    val sections: List<SkinSection> = emptyList(),
    /** 分段页签 → 分区列表。 */
    val sectionsBySegment: Map<String, List<SkinSection>> = emptyMap(),
)

/** 页面头部。 */
data class SkinPageHeader(
    val title: String,
    val subtitle: String? = null,
    val actions: List<SkinHeaderAction> = emptyList(),
)

/** 头部按钮。 */
data class SkinHeaderAction(
    val icon: String,
    val title: String,
)

/** 一个分区 = 引用一个零件 + 它的参数。 */
data class SkinSection(
    val part: String,
    val props: Map<String, String> = emptyMap(),
)

/** 播放页配置。 */
data class NowPlayingConfig(
    val center: Boolean = false,
    val coverShape: CoverShape = CoverShape.SQUARE,
    val controlStyle: ControlStyle = ControlStyle.SOLID,
    val showMood: Boolean = true,
    val lyrics: Boolean = false,
    val queue: Boolean = false,
    val actions: List<SkinNowPlayingAction> = emptyList(),
)

/** 播放页动作项。 */
data class SkinNowPlayingAction(
    val icon: String,
    val label: String,
)

/** 封面形状。 */
enum class CoverShape(val id: String) {
    SQUARE("square"),
    VINYL("vinyl"),
    NONE("none"),
    ;

    companion object {
        fun fromId(id: String?): CoverShape? = entries.firstOrNull { it.id == id }
    }
}

/** 主播放按钮样式。 */
enum class ControlStyle(val id: String) {
    SOLID("solid"),
    PLAIN("plain"),
    ;

    companion object {
        fun fromId(id: String?): ControlStyle? = entries.firstOrNull { it.id == id }
    }
}

/** 播放页进入方式。 */
enum class PlayerEntry(val id: String) {
    TAB("tab"),
    SHEET("sheet"),
    ;

    companion object {
        fun fromId(id: String?): PlayerEntry? = entries.firstOrNull { it.id == id }
    }
}
