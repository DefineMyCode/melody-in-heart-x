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
                { "part": "userInfo" },
                { "part": "playStats" },
                { "part": "moodTimeSlot" },
                { "part": "emotionScan" },
                { "part": "fileCheck" },
                { "part": "skinSwitcher" }
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
              "sections": [
                { "part": "userInfo" },
                { "part": "playStats" },
                { "part": "emotionScan" },
                { "part": "skinSwitcher" }
              ]
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

    /**
     * 第三套骨架样本（L3 grid 演示，2026-09-29）。
     *
     * 结构与 [skin] 完全相同，只是"歌手/专辑"分段从 default 模板**切到 grid**——
     * 让你能在同一份 App 里 A/B 对比两种模板的实际渲染差异。
     *
     * 接入方式：调参面板"骨架"开关里登记 `dcsgo.skin.grid`，由 [cn.com.dcsgo.mihx.app.shell.SkinShellResolver.knownSkins] 暴露。
     */
    private val GRID_SAMPLE_JSON = """{
        "schemaVersion": 1,
        "id": "dcsgo.skin.grid",
        "name": "网格布局样本",
        "tokens": { "theme": "VERMILION", "dark": false },
        "shell": {
          "bottomBar": [
            { "id": "tab-library", "label": "曲库", "icon": "library", "target": "library" },
            { "id": "tab-player",  "label": "播放", "icon": "play",     "target": "player" },
            { "id": "tab-me",      "label": "我的", "icon": "me",       "target": "me" }
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
            "search": true,
            "segments": ["歌单", "歌手", "专辑", "情绪"],
            "defaultSegment": "歌单",
            "sectionsBySegment": {
              "歌单": [
                { "part": "playlistShelf", "props": { "title": "我的歌单" } },
                { "part": "emotionChips",  "props": { "title": "情绪词条", "hint": "点词条直接起播" } }
              ],
              "歌手": [
                { "part": "songList", "props": { "source": "artist", "template": "grid" } }
              ],
              "专辑": [
                { "part": "songList", "props": { "source": "album", "template": "grid" } }
              ],
              "情绪": [
                { "part": "emotionChips", "props": { "title": "按情绪起播" } }
              ]
            }
          },
          "$PAGE_PLAYER": { "header": null, "sections": [] },
          "$PAGE_ME": {
            "fixedOneScreen": true,
            "header": { "title": "我的", "actions": [ { "icon": "sliders", "title": "设置" } ] },
            "sections": [
              { "part": "userInfo" },
              { "part": "playStats" },
              { "part": "emotionScan" },
              { "part": "skinSwitcher" }
            ]
          }
        },
        "startPage": "$PAGE_LIBRARY",
        "playerEntry": "tab"
      }""".trimIndent()

    /** 解析网格样本骨架；默认皮肤不变（仍走 default 模板），这套用于 debug 面板验收 grid。 */
    fun gridSampleSkin(): Skin = when (val result = SkinParser.parse(GRID_SAMPLE_JSON)) {
        is SkinValidation.Valid -> result.skin
        is SkinValidation.Invalid -> error(
            "网格样本骨架未通过校验：${result.issues.joinToString { "${it.code}: ${it.message} @${it.field}" }}",
        )
    }

    /**
     * 黑胶播放页样本骨架（L4 播放页形态，2026-09-30）。
     *
     * 与 [gridSampleSkin] 同思路——结构与 [skin] 几乎相同（3 Tab + 全部 L3 网格 + 4 分段我的页），
     * **唯一的差异是 `pages.player.template = "vinyl"`**，让同一份 App 能 A/B 对比
     * 黑胶形态与传统现在播放形态的实际渲染差异。
     *
     * 接入方式：[cn.com.dcsgo.mihx.app.shell.SkinShellResolver.knownSkins] 把它登记进可切列表。
     */
    private val VINYL_SAMPLE_JSON = """{
        "schemaVersion": 1,
        "id": "dcsgo.skin.vinyl",
        "name": "黑胶播放页样本",
        "tokens": { "theme": "VERMILION", "dark": false },
        "shell": {
          "bottomBar": [
            { "id": "tab-library", "label": "曲库", "icon": "library", "target": "library" },
            { "id": "tab-player",  "label": "播放", "icon": "play",     "target": "player" },
            { "id": "tab-me",      "label": "我的", "icon": "me",       "target": "me" }
          ],
          "miniPlayer": true
        },
        "pages": {
          "$PAGE_LIBRARY": {
            "header": { "title": "曲库", "subtitle": "本地音乐" },
            "search": true,
            "segments": ["歌单", "歌手", "专辑", "情绪"],
            "defaultSegment": "歌单",
            "sectionsBySegment": {
              "歌单": [
                { "part": "playlistShelf", "props": { "title": "我的歌单" } },
                { "part": "emotionChips",  "props": { "title": "情绪词条" } }
              ],
              "歌手": [ { "part": "songList", "props": { "source": "artist", "template": "default" } } ],
              "专辑": [ { "part": "songList", "props": { "source": "album",  "template": "default" } } ],
              "情绪": [ { "part": "emotionChips", "props": { "title": "按情绪起播" } } ]
            }
          },
          "$PAGE_PLAYER": {
            "header": null,
            "template": "vinyl",
            "sections": []
          },
          "$PAGE_ME": {
            "fixedOneScreen": true,
            "header": { "title": "我的" },
            "sections": [
              { "part": "userInfo" },
              { "part": "playStats" },
              { "part": "skinSwitcher" }
            ]
          }
        },
        "startPage": "$PAGE_PLAYER",
        "playerEntry": "tab"
      }""".trimIndent()

    /** 解析黑胶播放页样本骨架；默认皮肤不变（仍走 classic），这套用于 debug 面板验收 vinyl。 */
    fun vinylSampleSkin(): Skin = when (val result = SkinParser.parse(VINYL_SAMPLE_JSON)) {
        is SkinValidation.Valid -> result.skin
        is SkinValidation.Invalid -> error(
            "黑胶样本骨架未通过校验：${result.issues.joinToString { "${it.code}: ${it.message} @${it.field}" }}",
        )
    }

    /**
     * 水墨青播放页样本骨架（L4 播放页形态第三套，2026-09-30）。
     *
     * 思路与 [vinylSampleSkin] 一致——结构与 [skin] 几乎相同（3 Tab + 全部 L3 网格 + 4 分段我的页），
     * 唯一差异是 `pages.player.template = "sumi"`。
     *
     * 但**视觉令牌**与默认骨架不同：水墨青的「色板」不走 ThemeVariant（MONO/VERMILION 二选一都会污染其它页），
     * 而是让 NowPlayingSumiSurface 用硬编码色板——这是 L4 这一层「按形态定色」的妥协：
     * 其它页面仍走 ThemeVariant；只有 sumi 的播放页是「套自己的色」。
     * 故 `tokens.theme` 与 `dark` 在这套里**不生效**，仅作占位（描述校验要求必须存在）。
     */
    private val SUMI_SAMPLE_JSON = """{
        "schemaVersion": 1,
        "id": "dcsgo.skin.sumi",
        "name": "水墨青",
        "tokens": { "theme": "MONO", "dark": false },
        "shell": {
          "bottomBar": [
            { "id": "tab-library", "label": "曲库", "icon": "library", "target": "library" },
            { "id": "tab-player",  "label": "播放", "icon": "play",     "target": "player" },
            { "id": "tab-me",      "label": "我的", "icon": "me",       "target": "me" }
          ],
          "miniPlayer": true
        },
        "pages": {
          "$PAGE_LIBRARY": {
            "header": { "title": "曲库", "subtitle": "本地音乐" },
            "search": true,
            "segments": ["歌单", "歌手", "专辑", "情绪"],
            "defaultSegment": "歌单",
            "sectionsBySegment": {
              "歌单": [
                { "part": "playlistShelf", "props": { "title": "我的歌单" } },
                { "part": "emotionChips",  "props": { "title": "情绪词条" } }
              ],
              "歌手": [ { "part": "songList", "props": { "source": "artist", "template": "default" } } ],
              "专辑": [ { "part": "songList", "props": { "source": "album",  "template": "default" } } ],
              "情绪": [ { "part": "emotionChips", "props": { "title": "按情绪起播" } } ]
            }
          },
          "$PAGE_PLAYER": {
            "header": null,
            "template": "sumi",
            "sections": []
          },
          "$PAGE_ME": {
            "fixedOneScreen": true,
            "header": { "title": "我的" },
            "sections": [
              { "part": "userInfo" },
              { "part": "playStats" },
              { "part": "skinSwitcher" }
            ]
          }
        },
        "startPage": "$PAGE_PLAYER",
        "playerEntry": "tab"
      }""".trimIndent()

    /** 解析水墨青播放页样本骨架；默认皮肤不变（仍走 classic），这套用于 debug 面板验收 sumi。 */
    fun sumiSampleSkin(): Skin = when (val result = SkinParser.parse(SUMI_SAMPLE_JSON)) {
        is SkinValidation.Valid -> result.skin
        is SkinValidation.Invalid -> error(
            "水墨青样本骨架未通过校验：${result.issues.joinToString { "${it.code}: ${it.message} @${it.field}" }}",
        )
    }

    /**
        * 网易云式内置样本骨架（2026-09-30，仿照网易云音乐视觉）。
        *
        * 模拟器自验结论（来自真机截屏）：
        *  - 底色 #2C2722 朱红黑；本骨架走 MONO.dark（结构令牌驱动差异,无需新主题变体）
        *  - 红色 CTA #C20C0C（结构令牌暂未支持；用户在 onPrimary 上靠 VERMILION 调色抵消）
        *  - 卡片墙（surface=1） + 大圆角（cardRadiusDp=12） + 极细发丝边（hairlineDp=0.5）
        *  - 4 Tab:首页 / 搜索 / 笔记 / 我的（首页和搜索指 library,笔记指 me — page key 受限,
        *    通过底栏 label + 装配保持接近的视觉；非 page key 级的 1:1 还原）
        *  - 播放为 sheet 全局抽屉 + pages.player.template = "netease"（NowPlayingNeteaseSurface）
        *  - 启动页 = library（抽屉型骨架下从底栏打开 sheet 即可播放）
        *
        * **不走新 ThemeVariant**：用户原话"仿造网易云"取的是结构差异（卡片墙/抽屉/网格/圆角），
        * 颜色走 MONO.dark 即可与心乐现成主题拉开差距;真要精确色再开新工单加 NETEASE 主题变体。
        */
       private val NETEASE_SAMPLE_JSON = """{
           "schemaVersion": 1,
           "id": "dcsgo.skin.netease",
           "name": "网易云式",
           "tokens": {
               "theme": "MONO",
               "dark": true,
               "surface": 1,
               "cardRadiusDp": 12,
               "hairlineDp": 0.5,
               "rowGapDp": 12,
               "artSizeDp": 56,
               "artRadiusDp": 8,
               "nowPlayingRadiusDp": 12
           },
           "shell": {
               "bottomBar": [
                   { "id": "tab-home",    "label": "首页", "icon": "library", "target": "library" },
                   { "id": "tab-search",  "label": "搜索", "icon": "search",  "target": "library" },
                   { "id": "tab-cloud",   "label": "云村", "icon": "list",    "target": "library" },
                   { "id": "tab-me",      "label": "我的", "icon": "me",      "target": "me" }
               ],
               "miniPlayer": true
           },
           "pages": {
               "$PAGE_LIBRARY": {
                   "header": { "title": "发现", "subtitle": "本地音乐 · 情绪词条" },
                   "search": true,
                   "segments": ["歌单", "歌手", "专辑", "情绪"],
                   "defaultSegment": "歌单",
                   "sectionsBySegment": {
                       "歌单": [
                           { "part": "playlistShelf", "props": { "title": "我的歌单" } },
                           { "part": "emotionChips",  "props": { "title": "按情绪起播" } }
                       ],
                       "歌手": [
                           { "part": "songList", "props": { "source": "artist", "template": "grid" } }
                       ],
                       "专辑": [
                           { "part": "songList", "props": { "source": "album", "template": "grid" } }
                       ],
                       "情绪": [
                           { "part": "emotionChips", "props": { "title": "情绪广场" } }
                       ]
                   }
               },
               "$PAGE_PLAYER": {
                   "header": null,
                   "template": "netease",
                   "sections": []
               },
               "$PAGE_ME": {
                   "fixedOneScreen": true,
                   "header": { "title": "我的" },
                   "sections": [
                       { "part": "userInfo" },
                       { "part": "playStats" },
                       { "part": "moodTimeSlot" },
                       { "part": "emotionScan" },
                       { "part": "fileCheck" },
                       { "part": "skinSwitcher" }
                   ]
               }
           },
           "startPage": "$PAGE_LIBRARY",
           "playerEntry": "sheet",
           "nowPlaying": {
               "center": true,
               "coverShape": "square",
               "controlStyle": "solid",
               "showMood": true,
               "lyrics": false,
               "queue": true,
               "actions": [
                   { "icon": "heart",  "label": "喜欢" },
                   { "icon": "plus",   "label": "加入歌单" },
                   { "icon": "music",  "label": "专辑" },
                   { "icon": "clock",  "label": "定时" }
               ]
           }
       }""".trimIndent()

    /** 解析网易云式样本骨架。默认皮肤不变(仍走 builtin)。 */
    fun neteaseSampleSkin(): Skin = when (val result = SkinParser.parse(NETEASE_SAMPLE_JSON)) {
        is SkinValidation.Valid -> result.skin
        is SkinValidation.Invalid -> error(
            "网易云式样本骨架未通过校验：${result.issues.joinToString { "${it.code}: ${it.message} @${it.field}" }}",
        )
    }
}
