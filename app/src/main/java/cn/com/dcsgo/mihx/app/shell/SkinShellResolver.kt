package cn.com.dcsgo.mihx.app.shell

import cn.com.dcsgo.mihx.core.skin.DefaultSkin
import cn.com.dcsgo.mihx.core.skin.Skin
import cn.com.dcsgo.mihx.core.skin.SkinParser
import cn.com.dcsgo.mihx.core.skin.SkinPartCatalog
import cn.com.dcsgo.mihx.core.skin.SkinValidation
import cn.com.dcsgo.mihx.feature.playlist.SongListTemplate
import cn.com.dcsgo.mihx.feature.user.UserSections
import cn.com.dcsgo.mihx.navigation.AppRoutes

/**
 * 皮肤描述 → 运行期外壳（P2 的桥）。
 *
 * 设计文档：`docs/architecture/PLUGIN_SHELL_DESIGN.md` §六 P2
 *
 * 职责分工：
 *  - `:core:skin` 负责**描述模型与校验**（不含 Android 资源、不含路由知识）；
 *  - 本文件在 `:app` 里把描述里的**符号**（`part` / `icon` / 页面 key）解析成
 *    **宿主的具体物**（路由、drawable 资源 id）。
 *
 * 之所以放到 `:app` 而不是 `:core:skin`：`R.drawable.*` 与 `AppRoutes` 都是
 * app 层的知识，`core` 下的模块拿到它们会破坏模块依赖方向（架构门禁会拦）。
 */
object SkinShellResolver {

    /**
     * 当前可选的骨架集合（皮肤）。
     *
     * 登记的内置骨架用于验收与开关测试：默认骨架（3 Tab）/ 抽屉型骨架（2 Tab + 播放为全局抽屉）。
     */
    val knownSkins: List<Skin> by lazy { listOf(DefaultSkin.skin(), DefaultSkin.minimalSheetSkin()) }

    /** 内置骨架 id；未知 id 一律回落它（fail-safe）。 */
    const val DEFAULT_SKIN_ID: String = DefaultSkin.ID

    /** 按皮肤 id 解析外壳；id 未知时回落默认骨架而不是崩。 */
    fun resolveById(id: String?): AppShell {
        val skin = knownSkins.firstOrNull { it.id == id } ?: return DefaultShell.shell
        return resolve(skin)
    }

    /**
     * 描述里的页面 key → 顶级路由。
     *
     * 目前是**固定映射**：P2 只支持"这几种页面被放在底栏"，
     * 页面内容的装配（`sections` / `part`）属于 P4。
     */
    private val PAGE_KEY_TO_ROUTE = mapOf(
        "library" to AppRoutes.PLAYLIST,
        "player" to AppRoutes.HOME,
        "me" to AppRoutes.USER,
    )

    /**
     * 把校验通过的描述解析成运行期外壳。
     *
     * 解析失败**不得崩溃**：回落到 [DefaultShell.shell]（内置骨架），
     * 这样即使描述出问题也不会让 App 起不来（fail-safe，而非 fail-closed——
     * fail-closed 用于**导入**场景：导入失败要明确拒绝；启动时则必须能用）。
     */
    fun resolve(skin: Skin): AppShell {
        val tabs = skin.shell.bottomBar.mapNotNull { tab ->
            val route = PAGE_KEY_TO_ROUTE[tab.target]
            // 底栏指向的页面 key 不认识 → 跳过该项而不是崩（校验器已保证 target 存在于 pages，
            // 但"页面 key 无对应路由"是 :app 才知道的事）。
            route?.let {
                AppTab(
                    id = tab.id,
                    route = it,
                    label = tab.label,
                )
            }
        }.ifEmpty { DefaultShell.shell.tabs }

        // 启动页：描述里 startPage 指向的页面 key 转成路由；
        // 与改造前一致地兜底到 HOME（原 startDestination = AppRoutes.HOME）。
        val startRoute = PAGE_KEY_TO_ROUTE[skin.startPage] ?: AppRoutes.HOME

        return AppShell(
            tabs = tabs,
            startRoute = startRoute,
            miniPlayer = skin.shell.miniPlayer,
            playerEntry = PlayerEntry.fromId(skin.playerEntry.id),
            myPageSectionOrder = resolveMyPageSectionOrder(skin),
            librarySongListTemplate = resolveLibrarySongListTemplate(skin),
        )
    }

    /**
     * 从描述里解析「我的」页的分区顺序。
     *
     * 描述里 `pages.me.sections` 的 `part` 名即分区 key。返回空列表表示"未指定"，
     * 页面会回落到改造前的写死顺序（因此不写这段描述时行为零变化）。
     *
     * 只保留页面**认识**的 key（由 `feature/user` 的 `UserSections` 定义）：
     * 描述里写了未知 key 时，校验器已在导入期拒绝；这里再过滤一次是纵深防御。
     *
     * 2026-09-30：保留对老 P5 描述 `"customSkin"` 的字面兼容——
     * 已交付的皮肤包 zip 写的就是 `customSkin`，装配时统一映射到
     * [UserSections.SKIN_SWITCHER] 渲染（视觉上是同一种样式切换卡）。
     */
    private fun resolveMyPageSectionOrder(skin: Skin): List<String> {
        val sections = skin.pages[DefaultSkin.PAGE_ME]?.sections ?: return emptyList()
        return sections.map { it.part }
            .map { if (it == SkinPartCatalog.CUSTOM_SKIN) UserSections.SKIN_SWITCHER else it }
            .filter(UserSections::isKnown)
    }

    /**
     * 从描述里解析曲库页 songList 的渲染模板（L3 行模板化，2026-09-29）。
     *
     * 在 `pages.library.sectionsBySegment` 里遍历所有分段，找到第一个
     * `part == "songList"` 的 section，取其 `template` props。未找到或值为空时回落到 DEFAULT。
     *
     * 这样默认骨架（含 songList.template="default"）解析结果 = DEFAULT，**与改造前一致**；
     * 网格样本骨架含 songList.template="grid" 解析为 GRID。
     *
     * 容错：[SongListTemplate.fromId] 对未知值回落 DEFAULT（不会让页面崩）。
     */
    private fun resolveLibrarySongListTemplate(skin: Skin): SongListTemplate {
        val library = skin.pages[DefaultSkin.PAGE_LIBRARY] ?: return SongListTemplate.DEFAULT
        // 先在 sectionsBySegment 里找；老结构（直接 sections）也兜一下
        val sections = library.sectionsBySegment.values.flatten().ifEmpty { library.sections }
        val songListSection = sections.firstOrNull { it.part == SkinPartCatalog.SONG_LIST }
            ?: return SongListTemplate.DEFAULT
        return SongListTemplate.fromId(songListSection.props["template"])
    }

    /**
     * 从描述文本解析外壳。
     *
     * 供将来"用户在设置里导入皮肤"调用（P5）；目前只有内置骨架走这条路。
     */
    fun resolveFromJson(json: String): AppShell =
        when (val result = SkinParser.parse(json)) {
            is SkinValidation.Valid -> resolve(result.skin)
            is SkinValidation.Invalid -> DefaultShell.shell
        }
}

/**
 * 内置默认外壳 —— **改造前的硬编码行为，逐项搬到这里**。
 *
 * 与 `DefaultSkin` 的对应关系（P2 的验收依据）：
 *  | 项 | 改造前 | 这里 |
 *  |---|---|---|
 *  | 底栏 | `AppDestinations.entries`（PLAYLIST/HOME/USER） | `AppRoutes.PLAYLIST/HOME/USER`，同顺序 |
 *  | 启动页 | `NavHost(startDestination = AppRoutes.HOME)` | `startRoute = AppRoutes.HOME` |
 *  | 迷你条 | `AppScaffold` 里 `!= HOME` 硬编码 | `miniPlayer = true` + [RouteAffinity] 判定 |
 *  | 播放页 | `AppRoutes.HOME` 普通 Tab | `playerEntry = TAB` |
 *
 * **注意**：这里刻意用 Kotlin 常量而非解析 `DefaultSkin.JSON`，因为启动路径上
 * 任何解析失败都会让 App 起不来；内置骨架是"编译期确定"的，不该依赖运行期解析。
 * `DefaultShellTest` 会断言两条路径的结论一致。
 */
object DefaultShell {
    val shell: AppShell = AppShell(
        tabs = listOf(
            AppTab(
                id = "tab-library",
                route = AppRoutes.PLAYLIST,
                label = "曲库",
            ),
            AppTab(
                id = "tab-player",
                route = AppRoutes.HOME,
                label = "播放",
            ),
            AppTab(
                id = "tab-me",
                route = AppRoutes.USER,
                label = "我的",
            ),
        ),
        startRoute = AppRoutes.HOME,
        miniPlayer = true,
        playerEntry = PlayerEntry.TAB,
    )

    /** 供测试断言"描述解析出的外壳 == 内置外壳"（两条路径必须同结论）。 */
    fun shellFromSkin(): AppShell = SkinShellResolver.resolve(DefaultSkin.skin())
}
