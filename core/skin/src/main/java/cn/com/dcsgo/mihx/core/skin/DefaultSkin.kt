package cn.com.dcsgo.mihx.core.skin

/**
 * 内置默认骨架 —— **描述 = 当前心乐的 App 结构**。
 *
 * 这是 P1 的验收标准（见 `docs/architecture/PLUGIN_SHELL_DESIGN.md` §六）：
 * 这份描述必须能 **1:1 表达现状**，P2 之后用它驱动的 App 要与现状**逐页无差异**。
 *
 * 现状结构（已核实源码）：
 *  - `AppDestinations` 固定 3 项：曲库(`PLAYLIST`) / 播放(`HOME`) / 我的(`USER`)
 *  - 播放页是 `AppRoutes.HOME` 的**普通 Tab**（`AppNavHost:164`）
 *  - 迷你条只在**非 HOME 页**显示（`AppScaffold:155` 条件 `!= HOME`）→ 用 [SkinShell.miniPlayer]
 *    表达为"播放页自身不需要迷你条"，由播放器零件按 target 决定
 *  - 曲库页有 5 个分段：歌单 / 歌手 / 专辑 / 情绪 / 歌曲
 *  - 我的页是固定内容页，**一屏不滚**（用户定的单屏原则）
 *
 * 令牌取值来自 `ThemeTokens.VermilionNight` 的现状视觉 + 已交付的尺寸令牌默认值，
 * 且 `surface = 0`（心乐是"零实心块"风格，与网易云的"卡片墙"相对）。
 */
object DefaultSkin {

    /** 与现状视觉对齐的令牌默认值。 */
    val TOKENS = SkinTokens(
        theme = "VERMILION",
        dark = true,
        // 心乐是零实心块风格：容器不填充，靠留白与分组
        surface = 0f,
        cardRadiusDp = 16f,
        hairlineDp = 1f,
        rowGapDp = 10f,
        // 对齐已交付的 VisTokens：listCoverSizeDp = 44
        artSizeDp = 44f,
        artRadiusDp = 10f,
        // 对齐已交付的 PlaybackPanelTokens：coverCornerDp = 20
        nowPlayingRadiusDp = 20f,
    )

    /** 页面 key（描述内部标识，不是路由）。 */
    const val PAGE_LIBRARY = "library"
    const val PAGE_PLAYER = "player"
    const val PAGE_ME = "me"

    /** 内置默认骨架的 id。 */
    const val ID = "dcsgo.skin.builtin"

    /**
     * 默认骨架的 JSON 文本。
     *
     * 刻意用 JSON 字面量（而非 Kotlin 构造）来定义：这样"内置骨架"和"用户导入的皮肤"
     * 走**完全同一条**解析/校验链路，不存在"内置的代码路径绕过校验"这种隐患。
     */
    val JSON: String =
        """
        {
          "schemaVersion": 1,
          "id": "$ID",
          "name": "心乐（内置）",
          "tokens": {
            "theme": "VERMILION",
            "dark": true,
            "surface": 0,
            "cardRadiusDp": 16,
            "hairlineDp": 1,
            "rowGapDp": 10,
            "artSizeDp": 44,
            "artRadiusDp": 10,
            "nowPlayingRadiusDp": 20
          },
          "shell": {
            "bottomBar": [
              { "id": "tab-library", "label": "曲库", "icon": "library", "target": "$PAGE_LIBRARY" },
              { "id": "tab-player",  "label": "播放", "icon": "play",    "target": "$PAGE_PLAYER" },
              { "id": "tab-me",      "label": "我的", "icon": "me",      "target": "$PAGE_ME" }
            ],
            "miniPlayer": true
          },
          "pages": {
            "$PAGE_LIBRARY": {
              "header": {
                "title": "曲库",
                "subtitle": "本地音乐 · 情绪词条",
                "actions": [
                  { "icon": "filter", "title": "筛选" },
                  { "icon": "plus",   "title": "新建歌单" }
                ]
              },
              "search": true,
              "segments": ["歌单", "歌手", "专辑", "情绪"],
              "defaultSegment": "歌单",
              "sectionsBySegment": {
                "歌单": [
                  { "part": "playlistShelf", "props": { "title": "我的歌单" } },
                  { "part": "emotionChips",  "props": { "title": "情绪词条", "hint": "点词条直接起播" } }
                ],
                "歌手": [
                  { "part": "songList", "props": { "source": "artist", "template": "default" } }
                ],
                "专辑": [
                  { "part": "songList", "props": { "source": "album", "template": "default" } }
                ],
                "情绪": [
                  { "part": "emotionChips", "props": { "title": "按情绪起播" } }
                ]
              }
            },
            "$PAGE_PLAYER": {
              "header": null,
              "sections": []
            },
            "$PAGE_ME": {
              "fixedOneScreen": true,
              "header": {
                "title": "我的",
                "subtitle": "账号、扫描与播放偏好",
                "actions": [
                  { "icon": "sliders", "title": "设置" }
                ]
              },
              "sections": [
                { "part": "myOverview", "props": { "sections": "myHero,settingsGroup" } }
              ]
            }
          },
          "startPage": "$PAGE_PLAYER",
          "playerEntry": "tab",
          "nowPlaying": {
            "center": false,
            "coverShape": "square",
            "controlStyle": "solid",
            "showMood": true,
            "lyrics": false,
            "queue": false,
            "actions": [
              { "icon": "heart",  "label": "喜欢" },
              { "icon": "plus",   "label": "加入歌单" },
              { "icon": "music",  "label": "专辑" },
              { "icon": "clock",  "label": "定时" }
            ]
          }
        }
        """.trimIndent()

    /**
     * 解析内置骨架。
     *
     * 若这里返回非 [SkinValidation.Valid]，说明**内置描述与零件库/校验器不一致**——
     * 那是构建期就该暴露的 bug，因此用 [check] 立刻失败，而不是静默回落。
     */
    fun skin(): Skin = when (val result = SkinParser.parse(JSON)) {
        is SkinValidation.Valid -> result.skin
        is SkinValidation.Invalid -> error(
            "内置默认骨架未通过校验（说明描述与零件库不一致）：${result.issues.joinToString { "${it.code}: ${it.message} @${it.field}" }}",
        )
    }

    /**
     * 用户举例的那套骨架：**2 Tab（曲库/我的）+ 播放页为全局抽屉**。
     *
     * 这是 P3 的可运行样本——它证明"描述能表达抽屉型骨架"这一诉求已被满足，
     * 而不只是接口上留了个 `playerEntry = "sheet"` 的字段。
     *
     * 与内置骨架的差异仅三处：
     *  1. 底栏去掉「播放」（播放页从 Tab 移出）；
     *  2. `playerEntry` 改为 `sheet`；
     *  3. `startPage` 改为曲库页（播放页已不在底栏，落在那儿会没有高亮项）。
     *
     * 与原型 `mihx-plugin-shell/parts-library.html` 里的「极简双页」皮肤一致。
     */
    val MINIMAL_SHEET_JSON: String =
        """
        {
          "schemaVersion": 1,
          "id": "dcsgo.skin.minimal",
          "name": "极简双页 · 播放抽屉",
          "tokens": {
            "theme": "MONO",
            "dark": true,
            "surface": 0,
            "cardRadiusDp": 16,
            "hairlineDp": 1,
            "rowGapDp": 14,
            "artSizeDp": 52,
            "artRadiusDp": 14,
            "nowPlayingRadiusDp": 20
          },
          "shell": {
            "bottomBar": [
              { "id": "tab-library", "label": "曲库", "icon": "library", "target": "$PAGE_LIBRARY" },
              { "id": "tab-me",      "label": "我的", "icon": "me",      "target": "$PAGE_ME" }
            ],
            "miniPlayer": true
          },
          "pages": {
            "$PAGE_LIBRARY": {
              "header": {
                "title": "曲库",
                "subtitle": "本地音乐",
                "actions": [ { "icon": "search", "title": "搜索" } ]
              },
              "sections": []
            },
            "$PAGE_PLAYER": { "sections": [] },
            "$PAGE_ME": {
              "fixedOneScreen": true,
              "header": { "title": "我的", "actions": [ { "icon": "sliders", "title": "设置" } ] },
              "sections": []
            }
          },
          "startPage": "$PAGE_LIBRARY",
          "playerEntry": "sheet",
          "nowPlaying": {
            "center": false,
            "coverShape": "square",
            "controlStyle": "solid",
            "showMood": false,
            "lyrics": false,
            "queue": true,
            "actions": [
              { "icon": "heart", "label": "喜欢" },
              { "icon": "plus",  "label": "歌单" }
            ]
          }
        }
        """.trimIndent()

    /** 解析抽屉型样本骨架；供测试与将来的人工验收使用。 */
    fun minimalSheetSkin(): Skin = when (val result = SkinParser.parse(MINIMAL_SHEET_JSON)) {
        is SkinValidation.Valid -> result.skin
        is SkinValidation.Invalid -> error(
            "抽屉型样本骨架未通过校验：${result.issues.joinToString { "${it.code}: ${it.message} @${it.field}" }}",
        )
    }
}
