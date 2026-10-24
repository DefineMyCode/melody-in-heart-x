package cn.com.dcsgo.mihx.app.shell

/**
 * 播放抽屉内当前展示的内容。
 *
 * ## 为什么歌手/专辑详情也放在抽屉里
 *
 * 抽屉是**全局浮层**，不是导航目的地。若把详情页做成导航目的地，
 * "从详情页返回 → 回抽屉"就只能靠**拦截系统返回键**或**监听 entry 生命周期**：
 *
 *  - 拦截返回键抢不过 Navigation 2.8 的 `NavHost` 内部 `PredictiveBackHandler`
 *    （它在栈变化时会重新注册、变成"最后一个"从而优先）——真机实测系统返回回不到抽屉；
 *  - 监听 `ON_DESTROY` 也不行：弹栈时若转场动画还在跑，被弹的 entry 只走到
 *    `CREATED`（`NavControllerImpl`：`if (!transitioning) DESTROYED else CREATED`），
 *    `ON_DESTROY` 在组合还活着时不会触发。
 *
 * 改成和**歌词页一样**：详情页也是抽屉内的一层，导航发生在抽屉内部，
 * 返回键只需在"播放页 ↔ 详情/歌词"之间切——**完全不碰主导航栈**，
 * 系统返回（抽屉自己的窗口处理）与页面内返回按钮天然一致 ✓。
 *
 * ⚠️ 抽屉内容是**单层状态**、不是叠栈：详情与详情之间是**替换**——
 * 从"其他专辑"点进另一个专辑，返回回的是**播放抽屉**，不回上一个专辑。
 */
sealed interface PlayerSheetContent {
    /** 播放页（抽屉首页）。 */
    data object Player : PlayerSheetContent

    /** 歌词页（抽屉内嵌）。 */
    data object Lyrics : PlayerSheetContent

    /** 歌手详情。 */
    data class Artist(val name: String) : PlayerSheetContent

    /** 专辑详情。 */
    data class Album(val name: String) : PlayerSheetContent
}

/**
 * 抽屉内"返回"的下一步。
 *
 * ⚠️ 抽屉的内容是**单层状态**、不是叠栈：详情与详情之间是**替换**
 * （从"其他专辑"点进另一个专辑 → 返回回**播放抽屉**，不回上一个专辑）。
 *
 * @return 上一层内容（详情/歌词 → 播放页）；返回 `null` = 已在最外层（该关抽屉了）。
 *
 * 纯策略函数，可在 JVM 上直接单测。
 */
fun previousPlayerSheetContent(current: PlayerSheetContent): PlayerSheetContent? = when (current) {
    is PlayerSheetContent.Player -> null
    else -> PlayerSheetContent.Player
}
