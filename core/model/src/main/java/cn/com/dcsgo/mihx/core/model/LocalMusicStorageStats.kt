package cn.com.dcsgo.mihx.core.model

/** 单个音质档位在本地音乐中的数量与占比。 */
data class AudioQualityShare(
    val tier: AudioQualityTier,
    val count: Int,
    /** 占本地音乐总数的比例，取值 0f..1f */
    val ratio: Float,
)

/**
 * 本地音乐存储与音质分布快照。
 *
 * 统计口径：
 * - 分母是「本地音乐」歌曲数（调用方需传入 `uri != null` 的歌曲）；
 * - 音质档位统一由 [Song.qualityTier] 判定，不落库、不另写阈值；
 * - 空间音频按 [AudioQualityTier.isSpatial] 的最新规则实时判定，不直接数可能过期的落库值；
 * - 文件大小以歌曲 id → 字节数映射为准，读取失败的歌曲计入 [unknownSizeSongs]。
 */
data class LocalMusicStorageStats(
    val totalSongs: Int,
    val measuredSizeSongs: Int,
    val unknownSizeSongs: Int,
    val totalBytes: Long,
    /** 按最新规则识别为空间音频的歌曲数 */
    val spatialAudioSongs: Int,
    /** 固定按 [AudioQualityTier] 枚举顺序排列，包含数量为 0 的档位 */
    val qualityShares: List<AudioQualityShare>,
) {
    val hasSongs: Boolean
        get() = totalSongs > 0

    /** 文件大小测量覆盖率，用于提示统计是否完整。 */
    val sizeCoverageRatio: Float
        get() = if (totalSongs <= 0) 0f else measuredSizeSongs.toFloat() / totalSongs

    /** 空间音频歌曲占本地音乐总数的比例，取值 0f..1f */
    val spatialAudioRatio: Float
        get() = if (totalSongs <= 0) 0f else spatialAudioSongs.toFloat() / totalSongs

    fun shareOf(tier: AudioQualityTier): AudioQualityShare =
        qualityShares.firstOrNull { it.tier == tier }
            ?: AudioQualityShare(tier = tier, count = 0, ratio = 0f)

    companion object {
        /**
         * 由歌曲列表与文件大小映射计算统计快照。
         *
         * [songs] 必须是本地音乐列表（`uri != null`）；[sizeBySongId] 只包含成功读取到
         * 大小的歌曲，缺失的歌曲按“大小未知”处理，不计入 [totalBytes]。
         */
        fun from(songs: List<Song>, sizeBySongId: Map<Int, Long>): LocalMusicStorageStats {
            val total = songs.size
            val countByTier = songs
                .groupingBy { it.qualityTier }
                .eachCount()

            val shares = AudioQualityTier.entries.map { tier ->
                val count = countByTier[tier] ?: 0
                AudioQualityShare(
                    tier = tier,
                    count = count,
                    ratio = if (total > 0) count.toFloat() / total else 0f,
                )
            }

            var measured = 0
            var totalBytes = 0L
            songs.forEach { song ->
                val size = sizeBySongId[song.id] ?: 0L
                if (size > 0L) {
                    measured += 1
                    totalBytes += size
                }
            }

            return LocalMusicStorageStats(
                totalSongs = total,
                measuredSizeSongs = measured,
                unknownSizeSongs = total - measured,
                totalBytes = totalBytes,
                spatialAudioSongs = songs.count { song ->
                    AudioQualityTier.isSpatial(song.audioCodec, song.channelCount)
                },
                qualityShares = shares,
            )
        }
    }
}