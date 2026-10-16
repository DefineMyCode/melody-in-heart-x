package cn.com.dcsgo.mihx.verify

import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * m-10（评审 2026-10-15）：架构校验规则的可测性回归。
 *
 * 规则本体抽到 [ProductArchitectureVerification] 后，用临时目录构造最小仓库样例，
 * 验证规则引擎：违规收集不中断、消息可定位、致命正则（release 块提取）在嵌套场景下不截断。
 */
class ProductArchitectureVerificationTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun write(relative: String, content: String) {
        val f = File(tmp.root, relative)
        f.parentFile.mkdirs()
        f.writeText(content)
    }

    private fun verify(): List<String> = ProductArchitectureVerification.verify(tmp.root)

    @Test
    fun `empty repo reports the leading required-file violations`() {
        val violations = verify()

        // 收集式：一次报出多条，且可按消息定位到具体规则
        assertTrue(
            "spotless catalog rule should fire: $violations",
            violations.any { it.contains("Spotless must stay in the version catalog") },
        )
        assertTrue(
            "module graph rule should fire: $violations",
            violations.any { it.contains("Product module graph is missing required modules") },
        )
        assertTrue(
            "gitignore rule should fire: $violations",
            violations.any { it.contains(".gitignore for generated Android/Gradle output") },
        )
        assertTrue(
            "hilt entry point rule should fire: $violations",
            violations.any { it.contains("must keep @HiltAndroidApp") },
        )
    }

    @Test
    fun `direct android util Log usage is flagged outside AppLogger`() {
        // 铺一条最短路径让规则走到 Log 检查：前面的规则会失败但收集不中断
        write(
            "feature/anything/src/main/java/x/Bad.kt",
            "package x\n\nimport android.util.Log\n\nfun p() { Log.d(\"t\", \"m\") }\n",
        )

        val violations = verify()

        assertTrue(
            "direct Log usage should be flagged: $violations",
            violations.any { it.contains("Use AppLog instead of direct android.util.Log calls") },
        )
    }

    @Test
    fun `feature module depending on data or player is flagged`() {
        write(
            "feature/leaky/build.gradle.kts",
            "dependencies { implementation(project(\":data\")) }\n",
        )

        val violations = verify()

        assertTrue(
            "feature -> :data dependency should be flagged: $violations",
            violations.any { it.contains("Feature modules must not depend directly on :data or :player") },
        )
    }

    @Test
    fun `stale data player package references are flagged in player module`() {
        // m-6：:player 实现类已迁到 cn.com.dcsgo.mihx.player，旧包引用应被视为回潮
        write(
            "player/src/main/java/cn/com/dcsgo/mihx/player/Stale.kt",
            "package cn.com.dcsgo.mihx.player\n\nimport cn.com.dcsgo.mihx.data.player.EmotionAnalyzer\n",
        )

        val violations = verify()

        assertTrue(
            "stale data.player reference should be flagged: $violations",
            violations.any { it.contains("stale cn.com.dcsgo.mihx.data.player references remain") },
        )
    }

    @Test
    fun `release block extraction survives nested braces`() {
        val build = """
            android {
                buildTypes {
                    release {
                        isMinifyEnabled = true
                        isShrinkResources = true
                        configure {
                            listOf("a").forEach { println(it) }
                        }
                    }
                }
            }
        """.trimIndent()
        val block = ProductArchitectureVerification.extractBracedBlock(build, "release")

        assertTrue("nested lambda braces must not truncate the block", block != null)
        assertTrue(block!!.contains("isMinifyEnabled = true"))
        assertTrue(block.contains("isShrinkResources = true"))
        assertTrue(block.contains("forEach { println(it) }"))
    }

    @Test
    fun `missing release block is reported`() {
        val block = ProductArchitectureVerification.extractBracedBlock("android { }", "release")

        assertTrue(block == null)
    }
}
