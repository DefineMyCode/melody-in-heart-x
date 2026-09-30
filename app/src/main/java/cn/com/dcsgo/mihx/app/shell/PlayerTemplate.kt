package cn.com.dcsgo.mihx.app.shell

/**
 * 播放页渲染模板（L4 播放页形态，2026-09-30）。
 *
 * ## 与 [SongListTemplate] 的同源设计
 * - L3 在 [:feature:playlist] 建了 `SongListTemplate` enum,L4 这里用同款套路;
 * - **描述侧白名单** 由 [:core:skin] 的 [cn.com.dcsgo.mihx.core.skin.SkinPartCatalog.PLAYER_TEMPLATES] 约束,
 *   **装配侧解析** 由这里的 [fromId] 容错回落到 [CLASSIC](「未知值」不能崩——启动路径 fail-safe 原则)。
 *
 * ## 模板差异
 * - **CLASSIC**:沿用改造前 [cn.com.dcsgo.mihx.app.player.NowPlayingSurface](classic 形态)。
 *   与现状字节级一致——视觉差异 = 零。
 * - **VINYL**:黑胶形态,旋转封面 + 唱针 + 进度环 + 最简控制条。
 *   一眼能识别"这不是手机 App 默认播放器"。
 * - **SUMI**:水墨青形态,2026-09-30 L4 同层交付。米白底 + 墨青字 + 竖排标题 + 水墨晕染占位封面。
 *   内置皮肤 `dcsgo.skin.sumi` 走它——一眼能识别"这不是现代 App",是国风音乐软件的味道。
 * - **NETEASE**:网易云式,2026-09-30 L4 同层交付。黑底 + 白字 + 大封面 + 封面取色背景 + 红 CTA 心形。
 *   内置皮肤 `dcsgo.skin.netease` 走它。模仿 2026 网易云音乐播放页:大封面居中、上下取色渐变背景、
 *   顶栏左收起 + 右更多、歌名居中大字、副标小字、喜欢数 + 心形、底部 5 控件条。
 *
 * ## 添加新模板
 * 在这里加一个常量 + 在 [:core:skin] 的 `PLAYER_TEMPLATES` 白名单里登记对应字符串,
 * 并在 [:app/AppNavHost] 的 HOME composable 的 when 分支里加一个分支。
 * 三处必须同时改,缺一会被校验器/装配器拦下——这是设计。
 *
 * ## 范围线
 * 黑胶版**仅**含上述四项;**不做频谱/不做波形**(L6 表现层零件,用户拍板不做)。
 * 网易云式同样守这条线:不做频谱/波形,只做"近似观感"——精确到像素不在 L4 这一层能做。
 */
enum class PlayerTemplate(val id: String) {
    /** 默认播放器形态,沿用 [NowPlayingSurface]。 */
    CLASSIC("classic"),

    /** 黑胶形态:旋转封面 + 唱针 + 进度环 + 最简控制条。 */
    VINYL("vinyl"),

    /** 水墨青形态:米白底 + 墨青字 + 竖排标题 + 水墨晕染占位封面 + 细墨线进度条。 */
    SUMI("sumi"),

    /** 网易云式形态:黑底 + 大封面 + 封面取色背景 + 心形 + 5 控件底栏(近似观感,非像素级还原)。 */
    NETEASE("netease");

    companion object {
        /**
         * 容错解析:未知值/缺省回落 [CLASSIC]。
         *
         * 与 P3/P4 一致:坏描述不能让页面崩。
         */
        fun fromId(id: String?): PlayerTemplate =
            entries.firstOrNull { it.id == id } ?: CLASSIC
    }
}
