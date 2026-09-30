package cn.com.dcsgo.mihx.core.skin

import org.json.JSONArray
import org.json.JSONObject

/**
 * 皮肤描述解析与校验（fail-closed）。
 *
 * 设计文档：`docs/architecture/PLUGIN_SHELL_DESIGN.md` §4
 *
 * 原则：
 *  - **未知 `part` 一律拒绝**并说明原因（不静默降级）——这是"提示安装失败并提示原因"的实现；
 *  - **未知字段忽略**（向前兼容：新皮肤在旧宿主上能跑，只是少了新特性）；
 *  - 一次返回**全部**问题，不是只报第一条；
 *  - 数值越界夹紧并记 warning（如圆角给 999 不致命），但**结构性错误一律拒绝**。
 */
object SkinParser {

    /** 当前宿主支持的描述版本。不匹配则拒绝（皮肤作者需按新版本更新）。 */
    const val SUPPORTED_SCHEMA_VERSION = 1

    /** 底栏项数范围。2 项=极简双页，5 项=上限。 */
    private const val MIN_TABS = 2
    private const val MAX_TABS = 5

    /** 令牌合法区间（越界夹紧 + warning，不拒绝加载）。 */
    private val TOKEN_RANGES = mapOf(
        "surface" to 0f..1f,
        "cardRadiusDp" to 0f..40f,
        "hairlineDp" to 0f..4f,
        "rowGapDp" to 0f..40f,
        "artSizeDp" to 24f..96f,
        "artRadiusDp" to 0f..32f,
        "nowPlayingRadiusDp" to 0f..40f,
    )

    /** 宿主已注册的主题 id（与 `ThemeVariant` 的 id 注册表对齐）。 */
    val KNOWN_THEMES = setOf("MONO", "VERMILION")

    fun parse(text: String): SkinValidation {
        val issues = mutableListOf<SkinIssue>()
        val warnings = mutableListOf<SkinIssue>()

        val root = runCatching { JSONObject(text) }.getOrNull()
            ?: return SkinValidation.Invalid(
                listOf(
                    SkinIssue(
                        SkinIssue.Code.MALFORMED_JSON,
                        "不是合法的 JSON 对象，无法读取",
                    ),
                ),
            )

        // ── schemaVersion ────────────────────────────────────────
        if (!root.has("schemaVersion")) {
            issues += SkinIssue(
                SkinIssue.Code.SCHEMA_VERSION_MISSING,
                "缺少 schemaVersion 字段",
                "schemaVersion",
            )
        } else {
            val version = root.optInt("schemaVersion", -1)
            if (version != SUPPORTED_SCHEMA_VERSION) {
                issues += SkinIssue(
                    SkinIssue.Code.SCHEMA_VERSION_UNSUPPORTED,
                    "描述版本 $version 不受支持，当前心乐支持版本 $SUPPORTED_SCHEMA_VERSION",
                    "schemaVersion",
                )
            }
        }

        // ── id / name ───────────────────────────────────────────
        val id = root.optString("id", "").trim()
        if (id.isEmpty()) {
            issues += SkinIssue(SkinIssue.Code.MISSING_ID, "缺少 id 字段（皮肤唯一标识）", "id")
        }
        val name = root.optString("name", "").trim()
        if (name.isEmpty()) {
            issues += SkinIssue(SkinIssue.Code.MISSING_NAME, "缺少 name 字段（皮肤显示名）", "name")
        }

        // ── tokens ──────────────────────────────────────────────
        val tokensObj = root.optJSONObject("tokens")
        if (tokensObj == null) {
            issues += SkinIssue(
                SkinIssue.Code.INVALID_PROP_VALUE,
                "缺少 tokens 字段（配色与结构令牌）",
                "tokens",
            )
        }
        val theme = tokensObj?.optString("theme", "")?.trim().orEmpty()
        if (theme.isNotEmpty() && theme !in KNOWN_THEMES) {
            issues += SkinIssue(
                SkinIssue.Code.UNKNOWN_THEME,
                "主题 \"$theme\" 不存在，可用主题：${KNOWN_THEMES.sorted().joinToString(" / ")}",
                "tokens.theme",
            )
        }
        val tokens = parseTokens(tokensObj, warnings)

        // ── pages（先解析，底栏 target 要引用它） ────────────────
        val pagesObj = root.optJSONObject("pages")
        val pages = linkedMapOf<String, SkinPage>()
        if (pagesObj == null) {
            issues += SkinIssue(
                SkinIssue.Code.PAGE_MISSING,
                "缺少 pages 字段（至少要有一个页面）",
                "pages",
            )
        } else {
            val keys = pagesObj.keys()
            while (keys.hasNext()) {
                val pageKey = keys.next()
                val pageObj = pagesObj.optJSONObject(pageKey) ?: continue
                pages[pageKey] = parsePage(
                    pageKey = pageKey,
                    obj = pageObj,
                    issues = issues,
                    warnings = warnings,
                )
            }
            if (pages.isEmpty()) {
                issues += SkinIssue(SkinIssue.Code.PAGE_MISSING, "pages 为空，至少要有一个页面", "pages")
            }
        }

        // ── shell.bottomBar ─────────────────────────────────────
        val shellObj = root.optJSONObject("shell")
        val tabs = mutableListOf<SkinTab>()
        if (shellObj == null) {
            issues += SkinIssue(
                SkinIssue.Code.BOTTOM_BAR_EMPTY,
                "缺少 shell 字段（至少要定义底栏）",
                "shell",
            )
        } else {
            val barArray = shellObj.optJSONArray("bottomBar")
            if (barArray == null || barArray.length() == 0) {
                issues += SkinIssue(
                    SkinIssue.Code.BOTTOM_BAR_EMPTY,
                    "底栏不能为空，至少要有 $MIN_TABS 个入口",
                    "shell.bottomBar",
                )
            } else {
                if (barArray.length() < MIN_TABS) {
                    issues += SkinIssue(
                        SkinIssue.Code.BOTTOM_BAR_TOO_FEW,
                        "底栏至少要有 $MIN_TABS 个入口，当前 ${barArray.length()} 个",
                        "shell.bottomBar",
                    )
                }
                if (barArray.length() > MAX_TABS) {
                    issues += SkinIssue(
                        SkinIssue.Code.BOTTOM_BAR_TOO_MANY,
                        "底栏最多 $MAX_TABS 个入口，当前 ${barArray.length()} 个",
                        "shell.bottomBar",
                    )
                }
                val seenTabIds = mutableSetOf<String>()
                for (i in 0 until barArray.length()) {
                    val item = barArray.optJSONObject(i) ?: continue
                    val tabId = item.optString("id", "").trim()
                    val label = item.optString("label", "").trim()
                    val icon = item.optString("icon", "").trim()
                    val target = item.optString("target", "").trim()
                    val at = "shell.bottomBar[$i]"

                    if (tabId.isEmpty() || label.isEmpty() || icon.isEmpty() || target.isEmpty()) {
                        issues += SkinIssue(
                            SkinIssue.Code.TAB_MISSING_FIELD,
                            "底栏第 ${i + 1} 项缺少字段（id/label/icon/target 都必填）",
                            at,
                        )
                        continue
                    }
                    if (!seenTabIds.add(tabId)) {
                        issues += SkinIssue(
                            SkinIssue.Code.TAB_DUPLICATE_ID,
                            "底栏 id \"$tabId\" 重复，每项 id 必须唯一",
                            "$at.id",
                        )
                    }
                    if (icon !in SkinPartCatalog.iconNames) {
                        issues += SkinIssue(
                            SkinIssue.Code.UNKNOWN_ICON,
                            "图标 \"$icon\" 不存在，可用图标：${SkinPartCatalog.iconNames.sorted().joinToString(" / ")}",
                            "$at.icon",
                        )
                    }
                    if (!pages.containsKey(target)) {
                        issues += SkinIssue(
                            SkinIssue.Code.TAB_UNKNOWN_TARGET,
                            "底栏指向的页面 \"$target\" 在 pages 里不存在",
                            "$at.target",
                        )
                    }
                    tabs += SkinTab(id = tabId, label = label, icon = icon, target = target)
                }
            }
        }

        // ── startPage ───────────────────────────────────────────
        val startPage = root.optString("startPage", "").trim()
        if (startPage.isEmpty()) {
            issues += SkinIssue(
                SkinIssue.Code.PAGE_MISSING,
                "缺少 startPage 字段（启动页）",
                "startPage",
            )
        } else if (!pages.containsKey(startPage)) {
            issues += SkinIssue(
                SkinIssue.Code.TAB_UNKNOWN_TARGET,
                "启动页 \"$startPage\" 在 pages 里不存在",
                "startPage",
            )
        }

        // ── nowPlaying / playerEntry ────────────────────────────
        val playerEntryRaw = root.optString("playerEntry", PlayerEntry.TAB.id)
        val playerEntry = PlayerEntry.fromId(playerEntryRaw)
        if (playerEntry == null) {
            issues += SkinIssue(
                SkinIssue.Code.INVALID_PROP_VALUE,
                "playerEntry \"$playerEntryRaw\" 非法，只能是 tab 或 sheet",
                "playerEntry",
            )
        }
        val nowPlaying = parseNowPlaying(root.optJSONObject("nowPlaying"), issues)

        // 抽屉型皮肤：底栏不应再放一个指向"播放页"的 tab（否则与抽屉语义冲突）
        if (playerEntry == PlayerEntry.SHEET && tabs.any { it.target == "player" }) {
            warnings += SkinIssue(
                SkinIssue.Code.UNSUPPORTED_CONCEPT,
                "playerEntry 为 sheet（播放是全局抽屉），但底栏仍有指向 player 页的入口；建议移除该 Tab",
                "shell.bottomBar",
            )
        }

        if (issues.isNotEmpty()) return SkinValidation.Invalid(issues)

        return SkinValidation.Valid(
            skin = Skin(
                schemaVersion = SUPPORTED_SCHEMA_VERSION,
                id = id,
                name = name,
                tokens = tokens,
                shell = SkinShell(
                    bottomBar = tabs,
                    miniPlayer = shellObj?.optBoolean("miniPlayer", false) ?: false,
                ),
                pages = pages,
                startPage = startPage,
                nowPlaying = nowPlaying,
                playerEntry = playerEntry ?: PlayerEntry.TAB,
            ),
            warnings = warnings,
        )
    }

    private fun parseTokens(obj: JSONObject?, warnings: MutableList<SkinIssue>): SkinTokens {
        val defaults = DefaultSkin.TOKENS
        if (obj == null) return defaults
        fun read(key: String, fallback: Float): Float {
            val raw = obj.optDouble(key, fallback.toDouble()).toFloat()
            if (raw.isNaN()) return fallback
            val range = TOKEN_RANGES[key] ?: return raw
            if (raw !in range) {
                warnings += SkinIssue(
                    SkinIssue.Code.TOKEN_OUT_OF_RANGE,
                    "令牌 $key 取值 $raw 超出范围 ${range.start}~${range.endInclusive}，已夹紧",
                    "tokens.$key",
                )
                return raw.coerceIn(range.start, range.endInclusive)
            }
            return raw
        }
        return SkinTokens(
            theme = obj.optString("theme", defaults.theme),
            dark = obj.optBoolean("dark", defaults.dark),
            surface = read("surface", defaults.surface),
            cardRadiusDp = read("cardRadiusDp", defaults.cardRadiusDp),
            hairlineDp = read("hairlineDp", defaults.hairlineDp),
            rowGapDp = read("rowGapDp", defaults.rowGapDp),
            artSizeDp = read("artSizeDp", defaults.artSizeDp),
            artRadiusDp = read("artRadiusDp", defaults.artRadiusDp),
            nowPlayingRadiusDp = read("nowPlayingRadiusDp", defaults.nowPlayingRadiusDp),
        )
    }

    private fun parsePage(
        pageKey: String,
        obj: JSONObject,
        issues: MutableList<SkinIssue>,
        warnings: MutableList<SkinIssue>,
    ): SkinPage {
        val at = "pages.$pageKey"

        // header
        val headerObj = obj.optJSONObject("header")
        val header = headerObj?.let { h ->
            val actionsArray = h.optJSONArray("actions")
            val actions = mutableListOf<SkinHeaderAction>()
            if (actionsArray != null) {
                for (i in 0 until actionsArray.length()) {
                    val a = actionsArray.optJSONObject(i) ?: continue
                    val icon = a.optString("icon", "").trim()
                    val title = a.optString("title", "").trim()
                    if (icon.isEmpty() || title.isEmpty()) {
                        issues += SkinIssue(
                            SkinIssue.Code.TAB_MISSING_FIELD,
                            "头部按钮第 ${i + 1} 项缺少 icon 或 title",
                            "$at.header.actions[$i]",
                        )
                        continue
                    }
                    if (icon !in SkinPartCatalog.iconNames) {
                        issues += SkinIssue(
                            SkinIssue.Code.UNKNOWN_ICON,
                            "图标 \"$icon\" 不存在",
                            "$at.header.actions[$i].icon",
                        )
                    }
                    actions += SkinHeaderAction(icon = icon, title = title)
                }
            }
            SkinPageHeader(
                title = h.optString("title", "").trim(),
                subtitle = h.optString("subtitle", "").trim().ifEmpty { null },
                actions = actions,
            )
        }

        // segments
        val segmentsArray = obj.optJSONArray("segments")
        val segments = mutableListOf<String>()
        if (segmentsArray != null) {
            for (i in 0 until segmentsArray.length()) {
                val s = segmentsArray.optString(i, "").trim()
                if (s.isEmpty()) continue
                if (s in segments) {
                    issues += SkinIssue(
                        SkinIssue.Code.SEGMENT_INVALID,
                        "分段页签 \"$s\" 重复",
                        "$at.segments[$i]",
                    )
                    continue
                }
                segments += s
            }
        }

        // sections：无分段用顶层 sections，有分段用 sectionsBySegment
        val sections = parseSections(obj.optJSONArray("sections"), "$at.sections", issues, warnings)
        val bySegment = linkedMapOf<String, List<SkinSection>>()
        val bySegObj = obj.optJSONObject("sectionsBySegment")
        if (bySegObj != null) {
            val keys = bySegObj.keys()
            while (keys.hasNext()) {
                val segKey = keys.next()
                val arr = bySegObj.optJSONArray(segKey)
                bySegment[segKey] = parseSections(arr, "$at.sectionsBySegment.$segKey", issues, warnings)
            }
        }

        val defaultSegment = obj.optString("defaultSegment", "").trim().ifEmpty { null }
        if (defaultSegment != null && segments.isNotEmpty() && defaultSegment !in segments) {
            issues += SkinIssue(
                SkinIssue.Code.SEGMENT_INVALID,
                "defaultSegment \"$defaultSegment\" 不在 segments 列表中",
                "$at.defaultSegment",
            )
        }

        // 有分段却没有对应分区 → 作者漏写，明确报错（否则该段点进去是空白）
        segments.forEach { seg ->
            if (!bySegment.containsKey(seg)) {
                issues += SkinIssue(
                    SkinIssue.Code.SEGMENT_INVALID,
                    "分段 \"$seg\" 没有对应的 sectionsBySegment 分区，点进去会是空白",
                    "$at.sectionsBySegment.$seg",
                )
            }
        }

        return SkinPage(
            fixedOneScreen = obj.optBoolean("fixedOneScreen", false),
            header = header,
            search = obj.optBoolean("search", false),
            segments = segments,
            defaultSegment = defaultSegment,
            sections = sections,
            sectionsBySegment = bySegment,
        )
    }

    private fun parseSections(
        array: JSONArray?,
        at: String,
        issues: MutableList<SkinIssue>,
        warnings: MutableList<SkinIssue>,
    ): List<SkinSection> {
        if (array == null) return emptyList()
        val out = mutableListOf<SkinSection>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val partName = item.optString("part", "").trim()
            val itemAt = "$at[$i]"
            if (partName.isEmpty()) {
                issues += SkinIssue(
                    SkinIssue.Code.UNKNOWN_PART,
                    "分区 ${i + 1} 缺少 part 字段（零件名）",
                    itemAt,
                )
                continue
            }
            // ★ fail-closed：未登记的零件一律拒绝
            val def = SkinPartCatalog.parts[partName]
            if (def == null) {
                issues += SkinIssue(
                    SkinIssue.Code.UNKNOWN_PART,
                    "零件 \"$partName\" 不在心乐支持的零件库内" +
                        "（可用零件见设计文档 PLUGIN_SHELL_DESIGN.md §4.2）",
                    "$itemAt.part",
                )
                continue
            }

            // props：只取白名单内的键，未知键忽略并记 warning
            val props = linkedMapOf<String, String>()
            val propsObj = item.optJSONObject("props")
            if (propsObj != null && def.allowedProps.isNotEmpty()) {
                val keys = propsObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = propsObj.optString(k, "").trim()
                    if (k in def.allowedProps) {
                        props[k] = v
                    } else {
                        warnings += SkinIssue(
                            SkinIssue.Code.UNSUPPORTED_CONCEPT,
                            "零件 \"$partName\" 的参数 \"$k\" 不被支持，已忽略",
                            "$itemAt.props.$k",
                        )
                    }
                }
            }

            // 值域校验：songList.template / source / count
            if (partName == SkinPartCatalog.SONG_LIST) {
                props["template"]?.let { t ->
                    if (t !in SkinPartCatalog.SONG_LIST_TEMPLATES) {
                        issues += SkinIssue(
                            SkinIssue.Code.INVALID_PROP_VALUE,
                            "songList.template \"$t\" 非法，可用：${SkinPartCatalog.SONG_LIST_TEMPLATES.sorted().joinToString(" / ")}",
                            "$itemAt.props.template",
                        )
                    }
                }
                props["source"]?.let { s ->
                    val head = s.substringBefore(":")
                    if (head !in SkinPartCatalog.SONG_LIST_SOURCES) {
                        issues += SkinIssue(
                            SkinIssue.Code.INVALID_PROP_VALUE,
                            "songList.source \"$s\" 非法，可用：${SkinPartCatalog.SONG_LIST_SOURCES.sorted().joinToString(" / ")}" +
                                "（带参数形式如 playlist:86）",
                            "$itemAt.props.source",
                        )
                    }
                }
                props["count"]?.let { c ->
                    val n = c.toIntOrNull()
                    if (n == null || n <= 0) {
                        issues += SkinIssue(
                            SkinIssue.Code.INVALID_PROP_VALUE,
                            "songList.count \"$c\" 非法，必须是正整数",
                            "$itemAt.props.count",
                        )
                    }
                }
            }

            // libraryTabs 的 tabs 段名必须合法
            if (partName == SkinPartCatalog.LIBRARY_TABS) {
                props["tabs"]?.let { raw ->
                    val bad = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        .filterNot { it in SkinPartCatalog.LIBRARY_SEGMENTS }
                    if (bad.isNotEmpty()) {
                        issues += SkinIssue(
                            SkinIssue.Code.INVALID_PROP_VALUE,
                            "libraryTabs.tabs 含未知分段 ${bad.joinToString(" / ")}，" +
                                "可用：${SkinPartCatalog.LIBRARY_SEGMENTS.joinToString(" / ")}",
                            "$itemAt.props.tabs",
                        )
                    }
                }
            }

            out += SkinSection(part = partName, props = props)
        }
        return out
    }

    private fun parseNowPlaying(obj: JSONObject?, issues: MutableList<SkinIssue>): NowPlayingConfig {
        val defaults = NowPlayingConfig()
        if (obj == null) return defaults

        val coverRaw = obj.optString("coverShape", defaults.coverShape.id)
        val coverShape = CoverShape.fromId(coverRaw)
        if (coverShape == null) {
            issues += SkinIssue(
                SkinIssue.Code.INVALID_PROP_VALUE,
                "封面形状 \"$coverRaw\" 非法，只能是 square / vinyl / none",
                "nowPlaying.coverShape",
            )
        }

        val styleRaw = obj.optString("controlStyle", defaults.controlStyle.id)
        val controlStyle = ControlStyle.fromId(styleRaw)
        if (controlStyle == null) {
            issues += SkinIssue(
                SkinIssue.Code.INVALID_PROP_VALUE,
                "控制区样式 \"$styleRaw\" 非法，只能是 solid / plain",
                "nowPlaying.controlStyle",
            )
        }

        val actionsArray = obj.optJSONArray("actions")
        val actions = mutableListOf<SkinNowPlayingAction>()
        if (actionsArray != null) {
            for (i in 0 until actionsArray.length()) {
                val a = actionsArray.optJSONObject(i) ?: continue
                val icon = a.optString("icon", "").trim()
                val label = a.optString("label", "").trim()
                if (icon.isEmpty() || label.isEmpty()) {
                    issues += SkinIssue(
                        SkinIssue.Code.TAB_MISSING_FIELD,
                        "播放页动作第 ${i + 1} 项缺少 icon 或 label",
                        "nowPlaying.actions[$i]",
                    )
                    continue
                }
                if (icon !in SkinPartCatalog.iconNames) {
                    issues += SkinIssue(
                        SkinIssue.Code.UNKNOWN_ICON,
                        "图标 \"$icon\" 不存在",
                        "nowPlaying.actions[$i].icon",
                    )
                }
                actions += SkinNowPlayingAction(icon = icon, label = label)
            }
        }

        return NowPlayingConfig(
            center = obj.optBoolean("center", defaults.center),
            coverShape = coverShape ?: defaults.coverShape,
            controlStyle = controlStyle ?: defaults.controlStyle,
            showMood = obj.optBoolean("showMood", defaults.showMood),
            lyrics = obj.optBoolean("lyrics", defaults.lyrics),
            queue = obj.optBoolean("queue", defaults.queue),
            actions = actions,
        )
    }
}
