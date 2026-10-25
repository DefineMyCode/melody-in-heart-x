import java.io.File
import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// ── 情绪模型规格生成（2026-10-25）────────────────────────────────────────
//
// core/model/emotion_model_spec.properties 是情绪坐标系版本的**单一真源**。
// 本任务做两件事:
//   1) 校验两个 tflite 资产的 SHA-256 与清单一致 —— **换资产忘记改清单 -> 构建失败**,
//      从此"换了模型但老数据静默留存、永远不重扫"这件事不会发生。
//   2) 算出参数指纹/完整指纹, 生成 EmotionModelSpec.kt 常量, 全仓只此一份。
//
// 为什么 :core:model 要读 :player 的资产:
//   坐标系的身份**由模型资产定义**, 指纹是坐标系身份的一部分, 必须和锚点表
//   待在同一个模块里(:core:model 是零依赖的纯模型模块, :player/:data 都依赖它,
//   常量因此天然可见, 不需要任何反向依赖)。
//   :core:model 因此不能脱离 :player 的资产单独构建 —— 这是刻意的:
//   资产和坐标定义必须一起被校验。

val emotionSpecFile = layout.projectDirectory.file("emotion_model_spec.properties")
val emotionAssets = listOf(
    rootProject.layout.projectDirectory.file("player/src/main/assets/emotion_yamnet.tflite"),
    rootProject.layout.projectDirectory.file("player/src/main/assets/emotion_va_head.tflite"),
)
val generatedSpecDir = layout.buildDirectory.dir("generated/emotionModelSpec/kotlin")

val generateEmotionModelSpec by tasks.registering {
    group = "build"
    description = "校验情绪模型资产哈希并生成 EmotionModelSpec.kt（坐标系版本的单一真源）"

    inputs.file(emotionSpecFile)
    inputs.files(emotionAssets)
    outputs.dir(generatedSpecDir)

    doLast {
        fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }

        fun sha256Hex(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(1 shl 16)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        val props = Properties().apply {
            emotionSpecFile.asFile.inputStream().use { stream -> load(stream) }
        }
        fun required(key: String): String =
            props.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }
                ?: throw GradleException("core/model/emotion_model_spec.properties 缺少必填项: $key")

        val label = required("label")
        val dbFloor = required("dbFloor")
        val dbCeil = required("dbCeil")
        val vCalBias = required("vCalBias")
        val vCalScale = required("vCalScale")
        val vocalCompShift = required("vocalCompShift")
        val vocalCompThreshold = required("vocalCompThreshold")
        val yamnetHash = required("yamnetSha256")
        val headHash = required("headSha256")

        // ① 资产哈希校验 —— 换资产忘改清单, 在这里就断掉
        val violations = mutableListOf<String>()
        emotionAssets.forEach { asset ->
            val key = if (asset.asFile.name.endsWith("yamnet.tflite")) "yamnetSha256" else "headSha256"
            val expected = props.getProperty(key).trim()
            val actual = sha256Hex(asset.asFile)
            if (!actual.equals(expected, ignoreCase = true)) {
                violations += "${asset.asFile.name} 哈希不符\n  清单 = $expected\n  实际 = $actual"
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "情绪模型资产与 core/model/emotion_model_spec.properties 不一致:\n\n" +
                    violations.joinToString("\n\n") +
                    "\n\n换了模型资产必须同步做三件事:\n" +
                    "  1) 更新清单里的资产哈希\n" +
                    "  2) 若坐标系语义也变了 -> 同时改 label, 并重标定锚点表\n" +
                    "     (锚点表是按 label+参数标定的, 见 EmotionGroupAnchorTest)\n" +
                    "  3) 曲库会自动重扫(指纹变了就触发), 但语义变了必须人工评估锚点表",
            )
        }

        // ② 指纹: 参数指纹守锚点表, 完整指纹守"要不要重扫"
        val paramsMaterial = listOf(
            label, dbFloor, dbCeil, vCalBias, vCalScale,
            vocalCompShift, vocalCompThreshold,
        ).joinToString("\n")
        val fullMaterial = listOf(paramsMaterial, yamnetHash, headHash).joinToString("\n")
        val paramsFingerprint = sha256Hex(paramsMaterial.toByteArray()).take(8)
        val fingerprint = sha256Hex(fullMaterial.toByteArray()).take(8)

        val generated = """
            |// 本文件是**生成物**, 勿手改。
            |// 来源: core/model/emotion_model_spec.properties
            |// 生成: :core:model 的 generateEmotionModelSpec 任务
            |package cn.com.dcsgo.mihx.core.model
            |
            |/**
            | * 情绪模型规格 —— 坐标系版本的单一真源(生成物)。
            | *
            | * PARAMS_FINGERPRINT: 坐标定义(label + A/B 参数)的哈希。
            | *   锚点表是按这组参数标定的, EmotionGroupAnchorTest 用它守住
            | *   "改了参数没重算锚点表" 这种错 —— 改了参数而没重标定, 单测会红。
            | *
            | * FINGERPRINT: 参数 + 资产哈希。modelVersion = "<label>@<FINGERPRINT>",
            | *   锚点表按 label 选(指纹不影响投票), 重扫判定比整个 modelVersion。
            | */
            |object EmotionModelSpec {
            |    /** 坐标系语义标签(人可读) */
            |    const val LABEL = "$label"
            |
            |    /** 坐标定义指纹: 锚点表标定时必须与之一致 */
            |    const val PARAMS_FINGERPRINT = "$paramsFingerprint"
            |
            |    /** 完整指纹: 坐标定义 + 模型资产 */
            |    const val FINGERPRINT = "$fingerprint"
            |
            |    /** 写入 song_emotions.modelVersion 的值 */
            |    const val MODEL_VERSION = "$label@$fingerprint"
            |
            |    /** A: arousal 真实响度包络的 dB -> [-1,1] 映射区间 */
            |    const val DB_FLOOR = $dbFloor
            |    const val DB_CEIL = $dbCeil
            |
            |    /** B: valence 零点/斜率标定 v' = (v - V_CAL_BIAS) / V_CAL_SCALE */
            |    const val V_CAL_BIAS = ${vCalBias}f
            |    const val V_CAL_SCALE = ${vCalScale}f
            |    /**
            |     * C: 人声成分补偿 —— 器乐组(vocalScore <= VOCAL_COMP_THRESHOLD)的
            |     *    valence 读时上移量。用来剥掉 valence 里"在测有没有人唱"的那部分。
            |     */
            |    const val VOCAL_COMP_SHIFT = ${vocalCompShift}f
            |    const val VOCAL_COMP_THRESHOLD = ${vocalCompThreshold}f
            |}
            |
        """.trimMargin()

        val outputFile = generatedSpecDir.get().asFile
            .resolve("cn/com/dcsgo/mihx/core/model/EmotionModelSpec.kt")
        outputFile.parentFile.mkdirs()
        outputFile.writeText(generated, Charsets.UTF_8)
        logger.lifecycle("emotion model spec: label=$label fingerprint=$fingerprint params=$paramsFingerprint")
    }
}

android {
    namespace = "cn.com.dcsgo.mihx.core.model"
    compileSdk = 36

    defaultConfig {
        minSdk = 33
    }

    sourceSets {
        getByName("main") {
            java.srcDir(generatedSpecDir)
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn(generateEmotionModelSpec)
}

dependencies {
    // 仅引入 compose-runtime 的 @Stable/@Immutable 注解，用于模型类的稳定性标注
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)
    testImplementation(libs.junit)
}
