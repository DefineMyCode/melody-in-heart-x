package cn.com.dcsgo.mihx.core.skin

/**
 * 零件库白名单（fail-closed）。
 *
 * 设计文档：`docs/architecture/PLUGIN_SHELL_DESIGN.md` §4.2
 *
 * **范围裁定（2026-09-29 用户拍板）**：范围线画在 **L5/L6 之间**——「把钱花在把 1–5 层做扎实」。
 * 因此本白名单只登记 L1–L5 零件；L6 表现层零件（频谱/波形进度/轮播）与 L7 全新交互**不登记**。
 *
 * 扩张纪律（四条，防止沦为"什么都能配"的万能配置）：
 *  1. 零件的定义是"引用"而非"定义"——若描述里出现宿主不认识的视觉概念，说明要写代码，不是加字段；
 *  2. 每个零件必须对应一个**已真实存在**的宿主页面/组件；
 *  3. 且**至少被一个真实皮肤用到**；造出来没人用就是负债；
 *  4. 未登记的 `part` 一律拒绝并给出明确原因，**不静默降级**。
 *
 * 覆盖率目标：登记的零件应在多套骨架间被复用（原型面板②的 `3/3` 绿 / `1/3` 橙）。
 * 只有单一皮肤用到的"骨架专属零件"需复议是否值得保留。
 */
object SkinPartCatalog {

    /** 零件分层，仅用于文档与诊断输出；校验只关心是否为合法名字。 */
    enum class Layer(val label: String) {
        L1_SHELL("L1 导航外壳"),
        L2_PAGE("L2 页面与分区"),
        L3_ROW("L3 行与排布"),
        L4_PLAYER("L4 播放页"),
        L5_RESOURCE("L5 资源包"),
    }

    /** 一个可用零件。 */
    data class Part(
        val name: String,
        val layer: Layer,
        /** 允许出现在 props 里的键；用于"未知字段忽略、已知字段校验"。 */
        val allowedProps: Set<String> = emptySet(),
    )

    // ── L1 导航外壳 ──────────────────────────────────────────────
    const val TAB_BAR = "tabBar"
    const val MINI_PLAYER = "miniPlayer"
    const val SHEET = "sheet"
    const val RAIL = "rail"

    // ── L2 页面与分区 ────────────────────────────────────────────
    const val LIBRARY_TABS = "libraryTabs"

    /**
     * 我的页分区（L2 分区化，2026-09-29 P4）。
     *
     * 改造前我的页靠一个 `myOverview` 零件整体渲染；现在拆成 5 个可独立裁剪/重排的分区。
     * 这组名字必须与 `feature/user` 的 `UserSections` 常量**逐字一致**，
     * 由 `UserSectionsMatchDescriptionTest` 对照回归。
     */
    const val USER_INFO = "userInfo"
    const val PLAY_STATS = "playStats"
    const val MOOD_TIME_SLOT = "moodTimeSlot"
    const val EMOTION_SCAN = "emotionScan"
    const val FILE_CHECK = "fileCheck"

    const val MY_OVERVIEW = "myOverview"
    const val SONG_LIST = "songList"

    /**
     * `songList.template` 字段的合法取值（L3 行模板化，2026-09-29）。
     *
     * 描述里 `template` 字段必须在该集合里，否则 [SkinParser] 拒绝该描述。
     * 装配侧 [cn.com.dcsgo.mihx.app.shell.SongListAssembler] 用 [cn.com.dcsgo.mihx.feature.playlist.SongListTemplate.fromId]
     * 做容错解析（未知值回落 default）——两边必须同步。
     */
    val SONG_LIST_TEMPLATES = setOf("default", "grid")
    const val SETTINGS = "settings"
    const val EMOTION_ANALYSIS = "emotionAnalysis"
    const val HEADER_WIDGET = "header"
    const val SEARCH = "search"
    const val SEGMENT_CHIPS = "segmentChips"
    const val SECTION_TITLE = "sectionTitle"

    // ── L3 行与排布 ──────────────────────────────────────────────
    const val PLAYLIST_SHELF = "playlistShelf"
    const val PLAYLIST_GRID = "playlistGrid"
    const val QUICK_GRID = "quickGrid"
    const val EMOTION_CHIPS = "emotionChips"

    // ── L4 播放页 ────────────────────────────────────────────────
    const val NOW_PLAYING = "nowPlaying"
    const val COVER = "cover"
    const val PROGRESS = "progress"
    const val CONTROLS = "controls"
    const val LYRICS = "lyrics"
    const val QUEUE = "queue"

    // ── L5 资源包 ────────────────────────────────────────────────
    const val FONT = "font"
    const val BACKGROUND = "background"
    const val ICON_SET = "iconSet"

    /** `songList` 的数据源。 */
    val SONG_LIST_SOURCES = setOf("all", "playlist", "artist", "album")

    /**
     * `libraryTabs` 的段名。
     *
     * **必须与 `feature/playlist` 的 `LibraryTab` enum 逐字一致**：
     * `歌单 / 歌手 / 专辑 / 情绪` 共 4 项。
     *
     * ⚠️ 2026-09-29 P4 修正：初版这里多写了「歌曲」，那是**错的**——
     * 曲库页没有「歌曲」分段（该词只出现在歌手/专辑**详情页**的子标签里）。
     * 起因是照抄原型草案而未回源码核对，且当时的测试只拿描述自证，
     * 把错误锁死了。`LibrarySegmentsMatchSourceTest` 现在直接对照源码做回归。
     */
    val LIBRARY_SEGMENTS = setOf("歌单", "歌手", "专辑", "情绪")

    /** 白名单：零件名 → 定义。 */
    val parts: Map<String, Part> = listOf(
        // L1
        Part(TAB_BAR, Layer.L1_SHELL),
        Part(MINI_PLAYER, Layer.L1_SHELL),
        Part(SHEET, Layer.L1_SHELL),
        Part(RAIL, Layer.L1_SHELL),
        // L2
        Part(LIBRARY_TABS, Layer.L2_PAGE, setOf("tabs", "rowTemplate")),
        Part(MY_OVERVIEW, Layer.L2_PAGE, setOf("sections")),
        // 我的页分区（P4 拆分，可独立裁剪/重排）
        Part(USER_INFO, Layer.L2_PAGE),
        Part(PLAY_STATS, Layer.L2_PAGE),
        Part(MOOD_TIME_SLOT, Layer.L2_PAGE),
        Part(EMOTION_SCAN, Layer.L2_PAGE),
        Part(FILE_CHECK, Layer.L2_PAGE),
        // songList.template 的合法值见 SONG_LIST_TEMPLATES —— 描述校验会拒掉任何不在该集合的取值
        Part(SONG_LIST, Layer.L2_PAGE, setOf("title", "hint", "source", "template", "count")),
        Part(SETTINGS, Layer.L2_PAGE),
        Part(EMOTION_ANALYSIS, Layer.L2_PAGE),
        Part(HEADER_WIDGET, Layer.L2_PAGE, setOf("title", "subtitle")),
        Part(SEARCH, Layer.L2_PAGE),
        Part(SEGMENT_CHIPS, Layer.L2_PAGE),
        Part(SECTION_TITLE, Layer.L2_PAGE, setOf("text")),
        // L3
        Part(PLAYLIST_SHELF, Layer.L3_ROW, setOf("title", "hint")),
        Part(PLAYLIST_GRID, Layer.L3_ROW, setOf("title", "hint")),
        Part(QUICK_GRID, Layer.L3_ROW, setOf("items")),
        Part(EMOTION_CHIPS, Layer.L3_ROW, setOf("title", "hint")),
        // L4
        Part(NOW_PLAYING, Layer.L4_PLAYER),
        Part(COVER, Layer.L4_PLAYER, setOf("shape")),
        Part(PROGRESS, Layer.L4_PLAYER),
        Part(CONTROLS, Layer.L4_PLAYER, setOf("style")),
        Part(LYRICS, Layer.L4_PLAYER),
        Part(QUEUE, Layer.L4_PLAYER),
        // L5
        Part(FONT, Layer.L5_RESOURCE),
        Part(BACKGROUND, Layer.L5_RESOURCE),
        Part(ICON_SET, Layer.L5_RESOURCE),
    ).associateBy { it.name }

    /** 住宿主图标表（**不是**裸资源 id：插件不得跨边界传资源 id）。 */
    val iconNames: Set<String> = setOf(
        "library", "play", "pause", "prev", "next", "me", "search", "filter",
        "plus", "sliders", "folder", "clock", "palette", "download", "heart",
        "list", "music", "shuffle", "close",
    )

    fun isKnownPart(name: String): Boolean = parts.containsKey(name)

    fun layerOf(name: String): Layer? = parts[name]?.layer
}
