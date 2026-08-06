package cn.com.dcsgo.mihx.domain.playback

import cn.com.dcsgo.mihx.core.common.time.formatDurationTime
import cn.com.dcsgo.mihx.core.model.Song

/**
 * 同名多版本歌曲的差异比对（纯业务逻辑）。
 *
 * 将同一分组的多个版本按采样率降序排列，逐字段比对：
 * - 完全一致的字段归入 [ComparisonResult.commonFields]（「各版本一致」）
 * - 存在差异的字段归入 [ComparisonResult.rows]（规格对比表）
 * - 默认推荐（基准）取采样率最高者
 *
 * 只依赖 [Song] 上已有的持久化字段（采样率 / 时长 / 艺术家 / 专辑 / 歌词 / 封面），
 * 格式由 [Song.uri] 文件名扩展名推断（content:// 无扩展名时显示「未知」）。
 * 可通过 [compare] 的 [fileNameOf] 参数注入文件名以便单元测试。
 */
object SongVersionComparer {

    /** 无损音频扩展名（用于格式徽标与音质评分） */
    val LOSSLESS_EXTENSIONS = setOf("flac", "wav", "aiff", "aif", "ape", "alac", "wv", "tak")

    /** 单个版本在对比页的摘要卡数据 */
    data class VersionCard(
        val song: Song,
        /** 格式名（大写，如 "FLAC"），无法推断时为空字符串 */
        val format: String,
        /** 是否为无损格式 */
        val isLossless: Boolean,
        /** 0..1 综合音质评分（采样率 × 无损加权后归一化） */
        val qualityScore: Float,
        /** 是否为本组推荐（基准）版本 */
        val isRecommended: Boolean,
    )

    /** 对比表单元格 */
    data class SpecCell(
        /** 展示文本 */
        val display: String,
        /** 0..1 量级条（数值型行：采样率比例），null 表示无条 */
        val bar: Float? = null,
        /** 是否为无损格式（仅格式行使用，用于徽标样式） */
        val isLossless: Boolean? = null,
    )

    /** 对比表行（一个字段一行） */
    data class SpecRow(
        val label: String,
        val cells: List<SpecCell>,
    )

    /** 一组版本的完整比对结果 */
    data class ComparisonResult(
        val versions: List<VersionCard>,
        /** 推荐（默认基准）版本在 [versions] 中的下标 */
        val recommendedIndex: Int,
        /** 各版本一致的字段（label to value） */
        val commonFields: List<Pair<String, String>>,
        /** 存在差异的字段行 */
        val rows: List<SpecRow>,
        /** 一致字段数 */
        val sameCount: Int,
        /** 差异字段数 */
        val diffCount: Int,
    ) {
        val isEmpty: Boolean get() = versions.isEmpty()
    }

    fun compare(
        songs: List<Song>,
        fileNameOf: (Song) -> String = { song -> song.uri?.lastPathSegment ?: "" },
        bitRateOf: (Song) -> String = { "" },
        fileSizeOf: (Song) -> String = { "" },
    ): ComparisonResult {
        val sorted = songs.sortedByDescending { it.sampleRate }
        if (sorted.isEmpty()) {
            return ComparisonResult(emptyList(), -1, emptyList(), emptyList(), 0, 0)
        }

        val formats = sorted.associateWith { song -> formatOfName(fileNameOf(song)).uppercase() }
        val hasBitRate = sorted.any { bitRateOf(it).isNotBlank() }
        val hasFileSize = sorted.any { fileSizeOf(it).isNotBlank() }
        val maxSampleRate = sorted.maxOfOrNull { it.sampleRate } ?: 0
        val maxScore = sorted.maxOfOrNull { song -> qualityOf(song.sampleRate, formats[song] ?: "") } ?: 0f
        val versions = sorted.map { song ->
            val format = formats[song] ?: ""
            VersionCard(
                song = song,
                format = format,
                isLossless = format.isNotEmpty() && format.lowercase() in LOSSLESS_EXTENSIONS,
                qualityScore = if (maxScore > 0f) {
                    qualityOf(song.sampleRate, format) / maxScore
                } else {
                    0f
                },
                isRecommended = false,
            )
        }
        // 推荐 = 综合音质评分最高（采样率 × 无损加权）；评分相同再优先比特率高者
        val recommendedIndex = versions.indices.maxWithOrNull(
            compareBy<Int> { versions[it].qualityScore }
                .thenBy { bitRateKbpsOf(bitRateOf(versions[it].song)) },
        ) ?: 0
        val finalVersions = versions.mapIndexed { index, card ->
            card.copy(isRecommended = index == recommendedIndex)
        }

        val allRows = buildList {
            add(SpecRow("格式", finalVersions.map { SpecCell(it.format.ifEmpty { "未知" }, isLossless = it.isLossless) }))
            add(
                SpecRow(
                    "采样率",
                    finalVersions.map {
                        SpecCell(
                            display = it.song.sampleRateDisplay.ifEmpty { "—" },
                            bar = if (maxSampleRate > 0 && it.song.sampleRate > 0) {
                                it.song.sampleRate.toFloat() / maxSampleRate
                            } else {
                                null
                            },
                        )
                    },
                ),
            )
            if (hasBitRate) {
                add(SpecRow("比特率", finalVersions.map { SpecCell(bitRateOf(it.song).ifEmpty { "未知" }) }))
            }
            if (hasFileSize) {
                add(SpecRow("文件大小", finalVersions.map { SpecCell(fileSizeOf(it.song).ifEmpty { "未知" }) }))
            }
            add(SpecRow("时长", finalVersions.map { SpecCell(formatDurationTime(it.song.durationMs)) }))
            add(SpecRow("艺术家", finalVersions.map { SpecCell(it.song.artist.ifEmpty { "未知艺术家" }) }))
            add(SpecRow("专辑", finalVersions.map { SpecCell(it.song.album.ifEmpty { "未知专辑" }) }))
            add(SpecRow("歌词", finalVersions.map { SpecCell(if (it.song.lrcUri != null) "有" else "无") }))
            add(SpecRow("封面", finalVersions.map { SpecCell(if (it.song.albumArtUri != null) "有" else "无") }))
        }

        val rows = allRows.filter { it.cells.map { cell -> cell.display }.distinct().size > 1 }
        val commonFields = allRows
            .filterNot { it.cells.map { cell -> cell.display }.distinct().size > 1 }
            .map { it.label to (it.cells.firstOrNull()?.display.orEmpty()) }
            .sortedBy { (label, _) -> FIELD_ORDER.indexOf(label).takeIf { it >= 0 } ?: Int.MAX_VALUE }

        return ComparisonResult(
            versions = finalVersions,
            recommendedIndex = recommendedIndex,
            commonFields = commonFields,
            rows = rows,
            sameCount = commonFields.size,
            diffCount = rows.size,
        )
    }

    /**
     * 版本管理分组行的差异提示。
     * 如「采样率、歌词 不同」；全部一致时返回「规格一致」。
     */
    fun diffHint(
        songs: List<Song>,
        fileNameOf: (Song) -> String = { song -> song.uri?.lastPathSegment ?: "" },
    ): String {
        val result = compare(songs, fileNameOf)
        if (result.rows.isEmpty()) return "规格一致"
        val labels = result.rows.take(2).joinToString("、") { it.label }
        return if (result.rows.size > 2) "$labels 等 ${result.rows.size} 项不同" else "$labels 不同"
    }

    /** 从文件名推断格式（小写扩展名），无法推断返回空串 */
    fun formatOfName(fileName: String?): String =
        fileName?.substringAfterLast('.', "")?.trim()?.lowercase() ?: ""

    /** 从 URI 推断格式（兼容 UI 直接调用） */
    fun formatOf(song: Song): String = formatOfName(song.uri?.lastPathSegment)

    /** 综合音质分：采样率 × 无损加权 */
    private fun qualityOf(sampleRate: Int, format: String): Float {
        val rate = sampleRate.toFloat()
        if (rate <= 0f) return 0f
        val lossless = format.isNotEmpty() && format.lowercase() in LOSSLESS_EXTENSIONS
        return rate * if (lossless) 1.0f else 0.6f
    }

    /** 解析比特率字符串为 kbps 数值（如 "1411 kbps" → 1411f），无法解析返回 0 */
    private fun bitRateKbpsOf(bitRate: String): Float =
        bitRate.trim().substringBefore(" ").toFloatOrNull() ?: 0f

    /** 参与比对的字段顺序（用于稳定排序输出） */
    private val FIELD_ORDER = listOf("格式", "采样率", "比特率", "文件大小", "时长", "艺术家", "专辑", "歌词", "封面")
}
