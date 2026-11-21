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

    /**
     * 铺一个含 release 块的最小 app/build.gradle.kts。
     *
     * `rules` 中 `extractBracedBlock(appBuildText, "release")` 用 `failNow`（抛异常中断），
     * 若无此文件，后续规则（中文字面量棘轮、备份排除检查）将无法被执行到。
     */
    private fun writeMinimalAppBuild() {
        write(
            "app/build.gradle.kts",
            "release { isMinifyEnabled = true; isShrinkResources = true }",
        )
    }

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
    fun `bare loadSongInfo outside the safe loader is flagged`() {
        // C-1（评审 2026-10-27）：裸调 loadSongInfo 会把 Room 异常抛到组合协程 → 进程崩溃。
        // 修复后所有调用点都走 loadSongInfoSafely；本规则把裸调作为回潮拦下。
        write(
            "feature/anything/src/main/java/x/Bad.kt",
            "package x\n\nsuspend fun p(song: Song) { songInfo = loadSongInfo(song) }\n",
        )

        val violations = verify()

        assertTrue(
            "bare loadSongInfo should be flagged: $violations",
            violations.any { it.contains("loadSongInfo must be invoked via loadSongInfoSafely") },
        )
    }

    @Test
    fun `bare loadSongInfo inside the safe loader implementation is allowed`() {
        // SongInfoLoader.kt 是 loadSongInfoSafely 的实现本体，裸调是它自己的工作，应豁免。
        write(
            "core/ui/src/main/java/cn/com/dcsgo/mihx/ui/components/SongInfoLoader.kt",
            "package x\n\nsuspend fun f(loadSongInfo: suspend (Song) -> SongInfo?) { loadSongInfo(song) }\n",
        )

        val violations = verify()

        assertTrue(
            "safe-loader implementation must be exempt: $violations",
            violations.none { it.contains("loadSongInfo must be invoked via loadSongInfoSafely") },
        )
    }

    @Test
    fun `PerformanceTrace allow without a call site is flagged`() {
        // 门禁建议 4（评审 2026-10-27）：release 白名单 API 没有生产调用点就是死代码。
        val violations = verify()

        assertTrue(
            "missing PerformanceTrace.allow call site should be flagged: $violations",
            violations.any { it.contains("PerformanceTrace.allow(...) must have at least one real call site") },
        )
    }

    @Test
    fun `PerformanceTrace allow with a real call site passes`() {
        // 在 src/main 里登记白名单调用点后，该规则应放行。
        write(
            "app/src/main/java/cn/com/dcsgo/mihx/MelodyApplication.kt",
            "package x\n\nclass MelodyApplication {\n" +
                "    fun onCreate() { PerformanceTrace.allow(\"controller_sync_queue\") }\n" +
                "}\n",
        )

        val violations = verify()

        assertTrue(
            "allow call site should satisfy the rule: $violations",
            violations.none { it.contains("PerformanceTrace.allow(...) must have at least one real call site") },
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

    @Test
    fun `chinese literal count exceeding ratchet baseline is flagged`() {
        // M-12（评审 2026-11-20）：中文字面量棘轮基线 1500，超过即构建失败。
        writeMinimalAppBuild()
        val literals = (1..1501).joinToString("\n") { i -> "val s$i = \"中文测试$i\"" }
        write(
            "feature/anything/src/main/java/x/Many.kt",
            "package x\n\n$literals\n",
        )

        val violations = verify()

        assertTrue(
            "chinese literal ratchet should fire: $violations",
            violations.any { it.contains("中文字面量数量") && it.contains("超过棘轮基线") },
        )
    }

    @Test
    fun `chinese literal count below ratchet baseline passes`() {
        // 少量中文字面量不应触发棘轮规则。
        writeMinimalAppBuild()
        write(
            "feature/anything/src/main/java/x/Few.kt",
            "package x\n\nval s = \"少量中文\"\n",
        )

        val violations = verify()

        assertTrue(
            "chinese literal ratchet should not fire for small counts: $violations",
            violations.none { it.contains("中文字面量数量") && it.contains("超过棘轮基线") },
        )
    }

    @Test
    fun `backup rules missing playlist_resume exclude are flagged`() {
        // m-14（评审 2026-11-20）：playlist_resume 瞬态状态须排除。
        writeMinimalAppBuild()
        val backup = """<full-backup-content>
            <exclude domain="sharedpref" path="music_player_prefs.xml" />
            <exclude domain="sharedpref" path="play_stats_prefs.xml" />
            <exclude domain="sharedpref" path="quick_skip_songs_prefs.xml" />
            <exclude domain="database" path="melody.db" />
            <exclude domain="database" path="melody.db-journal" />
            <exclude domain="database" path="melody.db-shm" />
            <exclude domain="database" path="melody.db-wal" />
            <exclude domain="file" path="datastore/playback_state.preferences_pb" />
            <exclude domain="file" path="cache/album_art/" />
        </full-backup-content>"""
        write("app/src/main/res/xml/backup_rules.xml", backup)
        write("app/src/main/res/xml/data_extraction_rules.xml", backup.replace("full-backup-content", "data-extraction-rules"))

        val violations = verify()

        assertTrue(
            "missing playlist_resume exclude should be flagged: $violations",
            violations.any { it.contains("missing privacy excludes") && it.contains("playlist_resume.preferences_pb") },
        )
    }

    @Test
    fun `player_settings datastore is no longer required to be excluded`() {
        // m-14（评审 2026-11-20）：player_settings 用户偏好已纳入备份，不应再要求排除。
        writeMinimalAppBuild()
        val backup = """<full-backup-content>
            <exclude domain="sharedpref" path="music_player_prefs.xml" />
            <exclude domain="sharedpref" path="play_stats_prefs.xml" />
            <exclude domain="sharedpref" path="quick_skip_songs_prefs.xml" />
            <exclude domain="database" path="melody.db" />
            <exclude domain="database" path="melody.db-journal" />
            <exclude domain="database" path="melody.db-shm" />
            <exclude domain="database" path="melody.db-wal" />
            <exclude domain="file" path="datastore/playback_state.preferences_pb" />
            <exclude domain="file" path="datastore/playlist_resume.preferences_pb" />
            <exclude domain="file" path="cache/album_art/" />
        </full-backup-content>"""
        write("app/src/main/res/xml/backup_rules.xml", backup)
        write("app/src/main/res/xml/data_extraction_rules.xml", backup.replace("full-backup-content", "data-extraction-rules"))

        val violations = verify()

        assertTrue(
            "player_settings should not be required to be excluded: $violations",
            violations.none { it.contains("missing privacy excludes") && it.contains("player_settings.preferences_pb") },
        )
    }
}
