package cn.com.dcsgo.mihx.feature.playlist

/**
 * `songList` 模板（描述驱动，L3 行模板化，2026-09-29 P5 落地）。
 *
 * ## 为什么是 enum 而不是字符串
 *
 * 描述里 template 字段的合法值由 [:core:skin] 的校验器白名单约束，
 * 但**装配侧仍需要一个 Kotlin 侧的解析器**——既为了类型安全，也为了把
 * "default / grid" 这两个常量的语义集中在这一处。
 *
 * ## 模板的"差异感"
 *
 * - **default**:沿用改造前 `SongListItem` 的横向行（封面 + 歌名 + 歌手 + 时长）。
 *   **与现状字节级一致**——视觉差异 = 零。
 * - **grid**:2 列垂直网格，每张卡**封面为主**（占卡高 70%）+ 底部两行小字（标题 + 副）。
 *   一眼能识别"这不是行，是画廊"。
 *
 * ## 添加新模板
 *
 * 在这里加一个常量 + 在 [core/skin/SkinPartCatalog] 的 SONG_LIST_TEMPLATES
 * 白名单里登记对应字符串，并在 [:app/shell/SongListAssembler] 里加一个 when 分支。
 * 三处必须同时改，缺一会被校验器/装配器拦下——这是设计。
 */
enum class SongListTemplate(val id: String) {
    /** 默认横向行，与改造前逐像素一致。 */
    DEFAULT("default"),

    /** 2 列垂直网格，封面为主。 */
    GRID("grid");

    companion object {
        /**
         * 容错解析：未知值/缺省回落 [DEFAULT]。
         *
         * 启动路径 fail-safe 原则（与 P3/P4 一致）：
         * 坏描述不能让页面崩。
         */
        fun fromId(id: String?): SongListTemplate =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
