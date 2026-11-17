package cn.com.dcsgo.mihx.core.model

/**
 * 音频规格档位（按文件实际参数分级，不依赖平台会员档位）。
 *
 * - 有损：按比特率分 标准/较高/极高
 * - 无损：FLAC/ALAC/WAV/AIFF/APE 等，16bit/44.1kHz 起
 * - 高解析：24bit 且采样率 ≥48kHz（DSD 直接归入）
 *
 * 「母带」「沉浸环绕声」不在这套分级里：前者是源文件来源、后者是空间音频渲染维度。
 */
enum class AudioQualityTier(val label: String, val rule: String) {
    STANDARD("标准", "有损，码率低于 192kbps"),
    HIGH("较高", "有损，192–319kbps"),
    VERY_HIGH("极高", "有损，320kbps 及以上"),
    LOSSLESS("无损", "无损编码，16bit / 44.1kHz 起"),
    HI_RES("高解析", "无损编码，24bit 且 48kHz 及以上；DSD 直接归入"),
    UNKNOWN("未知", "规格数据不足，建议执行深度校验"),
    ;

    companion object {
        /** 无损/未压缩编码（MediaExtractor 的 MIME 口径） */
        private val LOSSLESS_CODECS = setOf(
            "audio/flac",
            "audio/x-flac",
            "audio/alac",
            "audio/x-alac",
            "audio/raw", // Android 对 WAV 常报 audio/raw
            "audio/wav",
            "audio/x-wav",
            "audio/aiff",
            "audio/x-aiff",
            "audio/ape",
            "audio/x-ape",
            "audio/dsd",
            "audio/x-dsd",
        )

        /** 明确带对象音频/空间音频标识的编码 */
        fun isLossless(audioCodec: String): Boolean =
            audioCodec.lowercase() in LOSSLESS_CODECS

        /**
         * 是否识别为空间音频/Atmos。
         *
         * - E-AC-3 JOC：JOC 扩展本身就是对象音频标识
         * - AC-4：编码本身不等于 Atmos，2.0 的 AC-4 只算普通立体声；
         *   只有多声道（>2）才按空间音频展示
         *
         * 轻量识别，不解析 AC-4 的 presentation/对象元数据。
         */
        fun isSpatial(audioCodec: String, channelCount: Int): Boolean {
            val codec = audioCodec.lowercase()
            return when (codec) {
                // 只有带 JOC 扩展的 E-AC-3（Atmos）才是空间音频。普通 E-AC-3 / 杜比数字+
                // （audio/eac3、audio/ec+3）只展示多声道，不标空间音频 —— 与「我的」页说明一致。
                "audio/eac3-joc" -> true
                "audio/ac4" -> channelCount > 2
                else -> false
            }
        }

        /**
         * 按文件规格分级。
         *
         * 有损格式只看比特率；无损格式看位深与采样率。数据不足时返回 [UNKNOWN]，
         * 不猜、不把有损格式的“解码位深”当音质。
         */
        fun classify(
            audioCodec: String,
            bitRateKbps: Int,
            bitDepth: Int,
            sampleRate: Int,
        ): AudioQualityTier {
            val codec = audioCodec.lowercase()
            if (codec in LOSSLESS_CODECS) {
                // DSD 的 1bit/2.8MHz 是另一种规格体系，直接归入高解析
                if (codec == "audio/dsd" || codec == "audio/x-dsd") return HI_RES
                return when {
                    bitDepth >= 24 && sampleRate >= 48_000 -> HI_RES
                    bitDepth >= 16 && sampleRate >= 44_100 -> LOSSLESS
                    // 位深未知但采样率达到 CD 规格：仍可判无损
                    bitDepth == 0 && sampleRate >= 44_100 -> LOSSLESS
                    else -> UNKNOWN
                }
            }
            return when {
                bitRateKbps >= 320 -> VERY_HIGH
                bitRateKbps >= 192 -> HIGH
                bitRateKbps > 0 -> STANDARD
                else -> UNKNOWN
            }
        }
    }
}
