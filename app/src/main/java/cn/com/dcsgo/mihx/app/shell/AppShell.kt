package cn.com.dcsgo.mihx.app.shell

import cn.com.dcsgo.mihx.feature.playlist.SongListTemplate
import cn.com.dcsgo.mihx.navigation.AppRoutes

/**
 * 运行期应用外壳（P2）。
 *
 * 设计文档：`docs/architecture/PLUGIN_SHELL_DESIGN.md` §六 P2
 *
 * 背景：此前 `AppDestinations` 是**编译期 enum**（固定 3 项），底栏项目数写死，
 * 无法被描述驱动。P2 把它替换为**运行期**的 tab 列表，来源是皮肤描述
 * （`cn.com.dcsgo.mihx.core.skin.Skin`）。
 *
 * 关键约束：内置默认骨架（`DefaultSkin`）解析出的 shell 必须与改造前的行为**完全一致**，
 * 因此 [AppShell] 刻意不携带任何"新功能"——它只是把原先写死的三件事变成数据：
 *  1. 底栏有哪些项、顺序如何（原先 `AppDestinations.entries`）
 *  2. 启动落在哪个页面（原先 `NavHost(startDestination = AppRoutes.HOME)`）
 *  3. 迷你播放条是否常驻（原先 `AppScaffold` 里 `!= HOME` 的硬编码判断）
 */
data class AppShell(
    /** 底栏项，顺序即展示顺序。 */
    val tabs: List<AppTab>,
    /** 启动页路由（P2 前写死为 [AppRoutes.HOME]，即播放页）。 */
    val startRoute: String,
    /**
     * 迷你播放条是否作为常驻外壳元素。
     *
     * 注意这**不是**"是否显示"：真正的显示条件是"有当前歌曲 且 当前页不是播放页本身"，
     * 后者由 [RouteAffinity] 判定（见 [AppScaffold]）。此项表达的是"这个骨架是否使用迷你条"。
     */
    val miniPlayer: Boolean,
    /** 播放页进入方式；`SHEET` 时播放页不再是 Tab（P3 落地容器）。 */
    val playerEntry: PlayerEntry,
    /**
     * 「我的」页的分区顺序（L2 分区化，2026-09-29 P4）。
     *
     * 空列表 = 未指定 → 由页面回落到改造前的写死顺序（行为零变化）。
     * 元素是**页面自己认识的分区 key**（见 `feature/user` 的 `UserSections`）。
     *
     * 放在这里而不是让 AppNavHost 去读描述，是为了保持 AppNavHost 不感知皮肤模型——
     * 解析与归一化统一由 [SkinShellResolver] 完成。
     */
    val myPageSectionOrder: List<String> = emptyList(),
    /**
     * 曲库页 songList 渲染模板（L3 行模板化，2026-09-29）。
     *
     * 默认 DEFAULT = 改造前形态；皮肤描述里 `songList.template` 切到 grid 时，
     * 歌手/专辑分段会以网格形态渲染。
     *
     * 仍放在 [AppShell] 里而非让 AppNavHost 读描述——同 [myPageSectionOrder] 一样，
     * 解析与归一化统一由 [SkinShellResolver] 完成。
     */
    val librarySongListTemplate: SongListTemplate = SongListTemplate.DEFAULT,
    /**
     * 播放页渲染模板(L4 播放页形态,2026-09-30)。
     *
     * 默认 CLASSIC = 改造前形态;皮肤描述里 `player.template` 切到 vinyl 时,
     * HOME composable 会走 [cn.com.dcsgo.mihx.app.player.NowPlayingVinylSurface] 渲染黑胶版。
     *
     * 仍放在 [AppShell] 里而非让 AppNavHost 读描述——同 [myPageSectionOrder] / [librarySongListTemplate] 一样,
     * 解析与归一化统一由 [SkinShellResolver] 完成。
     */
    val playerTemplate: PlayerTemplate = PlayerTemplate.CLASSIC,
)

/**
 * 叠加「歌手/专辑网格布局」全局开关（2026-09-30 用户拍板：由样式下沉为开关）。
 *
 *  - [enabled] = true → 歌手/专辑段强制走两列网格（[SongListTemplate.GRID]），
 *    **不看样式的 `songList.template`**——所以两种样式（默认三页/极简双页）都能用；
 *  - [enabled] = false → 保留样式自身的解析结果（两套内置样式默认都是列表行）。
 *
 * 抽成函数是为了让「开关 → 外壳」这条映射能被单测锁住（原先内联在 AppRoot 的
 * `remember` 里，测不到）。
 */
fun AppShell.withGridLayout(enabled: Boolean): AppShell =
    if (enabled) copy(librarySongListTemplate = SongListTemplate.GRID) else this

/** 播放页进入方式（与皮肤描述对齐）。 */
enum class PlayerEntry(val id: String) {
    TAB("tab"),
    SHEET("sheet"),
    ;

    companion object {
        fun fromId(id: String): PlayerEntry = entries.firstOrNull { it.id == id } ?: TAB
    }
}

/** 底栏一项（运行期）。 */
data class AppTab(
    val id: String,
    /** 该 tab 指向的顶级路由。 */
    val route: String,
    val label: String,
    /**
     * 图标资源 id。
     *
     * 说明：改造前底栏是**纯文字**（`TextBottomBar` 只渲染 label），图标字段虽存在于
     * `AppDestinations` 却从未被使用。这里保留该字段以免描述里的 `icon` 丢失信息，
     * 但**底栏仍渲染纯文字**——这是"与现状逐页无差异"的一部分。
     */
    val iconResId: Int,
)

/**
 * 路由 → 所属顶级 Tab 的归属关系（纯函数，可单测）。
 *
 * 这段逻辑**逐条搬运**自原 `AppDestinations.fromRoute`，保持行为完全一致：
 * 子页面（设置、统计、歌手详情……）要把底栏高亮映射回它所属的顶级 Tab，
 * 并且驱动转场方向与横滑翻页。
 *
 * 与改造前的唯一差别：结论从"编译期 enum 常量"变成"运行期路由字符串"，
 * 于是 tab 数量可变（2 项/3 项/5 项都行）。
 */
object RouteAffinity {

    /** 「我的」分支下的子页面。 */
    private val USER_CHILDREN = setOf(
        AppRoutes.USER,
        AppRoutes.SKIN_SWITCHER,
        AppRoutes.SETTINGS,
        AppRoutes.PLAYBACK_STATS,
        AppRoutes.FILE_CHECK,
        AppRoutes.RAW_PLAY_STATS,
        AppRoutes.EFFECTIVE_PLAY_STATS,
        AppRoutes.SONG_TOP_LIST_FULL,
        AppRoutes.EMOTION_ANALYSIS,
        AppRoutes.MOOD_TIME_SLOT,
    )

    /** 「曲库」分支下的精确子页面（前缀匹配的另见 [PLAYLIST_PREFIXES]）。 */
    private val PLAYLIST_CHILDREN = setOf(
        AppRoutes.PLAYLIST,
        AppRoutes.VERSION_MANAGEMENT,
        AppRoutes.QUICK_SKIP_SONGS,
    )

    /** 「曲库」分支下按前缀归属的子页面。 */
    private val PLAYLIST_PREFIXES = listOf(
        "${AppRoutes.PLAYLIST}/",
        "artist/",
        "album/",
        "version-comparison/",
    )

    /**
     * 求 [route] 归属的**顶级路由**。
     *
     * 返回 [AppRoutes.HOME] 作为兜底——与改造前 `else -> HOME` 一致
     * （全屏歌词页也是走这个兜底）。
     */
    fun owningTopLevelRoute(route: String?): String = when {
        route == null -> AppRoutes.HOME
        route in USER_CHILDREN -> AppRoutes.USER
        route == AppRoutes.PLAYLIST || route in PLAYLIST_CHILDREN -> AppRoutes.PLAYLIST
        PLAYLIST_PREFIXES.any { route.startsWith(it) } -> AppRoutes.PLAYLIST
        else -> AppRoutes.HOME
    }
}

/** 在给定 tab 列表下定位当前路由所属的 Tab 序号；找不到时返回 0（而非 -1，避免调用方处理负索引）。 */
fun List<AppTab>.indexOfRoute(route: String?): Int {
    if (isEmpty()) return 0
    val owning = RouteAffinity.owningTopLevelRoute(route)
    val exact = indexOfFirst { it.route == owning }
    if (exact >= 0) return exact
    // 骨架里没有该顶级路由（例如极简双页把播放页移出底栏）：
    // 退化为"路由精确命中某项"再兜底到 0，避免高亮丢失。
    val bySelf = indexOfFirst { it.route == route }
    return if (bySelf >= 0) bySelf else 0
}

/** 在给定 tab 列表下求当前路由所属的 Tab。 */
fun List<AppTab>.tabForRoute(route: String?): AppTab? = getOrNull(indexOfRoute(route))

/** 按索引取 Tab（越界返回 null，供横滑翻页做边界判断）。 */
fun List<AppTab>.tabAt(index: Int): AppTab? = getOrNull(index)

/**
 * 播放页路由在该骨架下的取值。
 *
 * 兜底到 `"home"`（[AppRoutes.HOME] 的字面值）——与改造前的硬编码一致。
 * 刻意不直接引用 `AppRoutes` 以免 `shell` 包依赖导航常量表；
 * 二者的一致性由 `DefaultShellConsistencyTest` 断言。
 */
val AppShell.playerRoute: String
    get() = tabs.firstOrNull { it.id == PLAYER_TAB_ID }?.route ?: DEFAULT_PLAYER_ROUTE

/** 默认骨架里播放页 Tab 的 id（[DefaultShell] 用的就是它）。 */
const val PLAYER_TAB_ID: String = "tab-player"

/** 兜底播放页路由字面值。 */
private const val DEFAULT_PLAYER_ROUTE = "home"

/**
 * 迷你播放条是否应显示。**纯策略函数，可在 JVM 上直接单测。**
 *
 * 改造前实现（`AppScaffold`）：`currentDestination != AppDestinations.HOME && currentSong != null`。
 *
 * **关键正确性差别**：原实现比较**所属 Tab**，这里比较**当前路由**。
 * 在默认骨架下二者等价（播放页自身路由 == 其所属 Tab 的顶级路由），
 * 但在"播放页不在底栏"的骨架（抽屉型）下，只有按路由判定才能保持
 * "在播放页隐藏迷你条"这一既有行为——按 Tab 判定会因找不到而兜底到 home，
 * 恰好在**其他页面**误判为"在播放页"，把迷你条错误隐藏掉。
 */
fun shouldShowMiniPlayer(
    shell: AppShell,
    activeRoute: String?,
    hasCurrentSong: Boolean,
): Boolean = hasCurrentSong && shell.miniPlayer && activeRoute != shell.playerRoute
