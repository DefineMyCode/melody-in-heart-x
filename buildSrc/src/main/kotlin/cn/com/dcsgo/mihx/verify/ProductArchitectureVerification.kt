package cn.com.dcsgo.mihx.verify

import java.io.File

/**
 * m-10（评审 2026-10-15）：verifyProductArchitecture 的规则本体，从根 build.gradle.kts
 * 抽出为可单测的纯逻辑——任务只负责收集文件与报错，规则在此以 [File] 树为输入、
 * 违规列表为输出（收集全部违规一次性报出，不再首错即停）。
 *
 * 测试：ProductArchitectureVerificationTest 用临时目录构造仓库样例验证规则可触发、
 * 消息可定位。规则与线上仓库的耦合点只有相对路径与内容断言。
 */
object ProductArchitectureVerification {

    class VerificationFailure(message: String? = null) : RuntimeException(message)

    /** 供根任务调用：返回全部架构违规（空列表 = 通过） */
    fun verify(rootDir: File): List<String> {
        val violations = LinkedHashSet<String>()
        return try {
            rules(rootDir, violations)
            violations.toList()
        } catch (e: VerificationFailure) {
            // failNow 抛出前已把消息并入 violations
            violations.toList()
        }
    }

    private fun rules(rootDir: File, violations: LinkedHashSet<String>) {
        /** 记录违规，继续执行后续规则（收集式：一次报出全部问题） */
        fun fail(message: String) {
            violations += message
        }

        /** 记录违规并立即中断（用于无法继续执行的场景，如缺块取不到返回值） */
        fun failNow(message: String): Nothing {
            violations += message
            throw VerificationFailure()
        }

        fun file(relative: String): File = rootDir.resolve(relative)

        /** 读必需文件；缺失记违规并返回空串，避免裸 readText 抛非受控异常中断收集 */
        fun requiredText(relative: String): String {
            val f = rootDir.resolve(relative)
            if (!f.exists()) {
                fail("Required file is missing: $relative")
                return ""
            }
            return f.readText()
        }

        fun projectFiles(
            root: File,
            extensions: Set<String>,
        ): Sequence<File> = root.walkTopDown()
            // buildSrc 自身源码含规则文本（如 PlayerViewModelComponents 反例注释），排除防自指
            .onEnter { dir -> dir.name !in setOf("build", ".gradle", ".git", ".idea", "buildSrc") }
            .filter { file -> file.isFile && file.extension in extensions }

        val textFiles = projectFiles(rootDir, setOf("kt", "kts", "java", "xml", "toml")).toList()

        if (!requiredText("gradle/libs.versions.toml").contains("""spotless = { id = "com.diffplug.spotless"""")) {
            fail("Spotless must stay in the version catalog so formatting can be enabled consistently.")
        }
        val rootBuildText = requiredText("build.gradle.kts")
        if (!rootBuildText.contains("alias(libs.plugins.spotless)")) {
            fail("Root build must declare and apply the Spotless plugin alias.")
        }
        if (!Regex("""dependsOn\([^)]*"spotlessCheck"""").containsMatchIn(rootBuildText)) {
            fail("The check lifecycle task must run spotlessCheck.")
        }

        val requiredModules = listOf(
            ":app",
            ":core:model",
            ":core:common",
            ":core:ui",
            ":core:skin",
            ":domain",
            ":data",
            ":player",
            ":feature:home",
            ":feature:playlist",
            ":feature:user",
            ":feature:lyrics",
            ":feature:player",
            ":feature:settings",
            ":benchmark",
        )
        val settingsText = requiredText("settings.gradle.kts")
        val missingModules = requiredModules.filterNot(settingsText::contains)
        if (missingModules.isNotEmpty()) {
            fail("Product module graph is missing required modules: ${missingModules.joinToString()}")
        }
        requiredModules
            .map { modulePath -> modulePath.removePrefix(":").replace(":", "/") }
            .forEach { moduleDir ->
                val moduleGitignore = file("$moduleDir/.gitignore")
                if (!moduleGitignore.exists()) {
                    fail("Every product module must keep a local .gitignore for generated Android/Gradle output: $moduleDir/.gitignore")
                    return@forEach
                }
                val moduleGitignoreText = moduleGitignore.readText()
                listOf("/build/", "/.cxx/", "/.externalNativeBuild/", "/captures/").forEach { requiredPattern ->
                    if (!moduleGitignoreText.contains(requiredPattern)) {
                        fail("$moduleDir/.gitignore is missing required ignore pattern: $requiredPattern")
                    }
                }
            }

        val hiltEntryPoints = mapOf(
            "app/src/main/java/cn/com/dcsgo/mihx/MelodyApplication.kt" to "@HiltAndroidApp",
            "app/src/main/java/cn/com/dcsgo/mihx/MainActivity.kt" to "@AndroidEntryPoint",
            "player/src/main/java/cn/com/dcsgo/mihx/player/AppMediaSessionService.kt" to "@AndroidEntryPoint",
            "feature/player/src/main/java/cn/com/dcsgo/mihx/feature/player/PlayerViewModel.kt" to "@HiltViewModel",
        )
        hiltEntryPoints.forEach { (path, annotation) ->
            val source = file(path)
            if (!source.exists() || !source.readText().contains(annotation)) {
                fail("$path must keep $annotation for Hilt product assembly.")
            }
        }

        val hiltModules = mapOf(
            "app/src/main/java/cn/com/dcsgo/mihx/app/di/CoroutineModule.kt" to "CoroutineModule",
            "app/src/main/java/cn/com/dcsgo/mihx/app/di/LoggerModule.kt" to "LoggerModule",
            "app/src/main/java/cn/com/dcsgo/mihx/app/di/PlayerModule.kt" to "PlayerModule",
            "data/src/main/java/cn/com/dcsgo/mihx/data/di/RepositoryModule.kt" to "RepositoryModule",
            "data/src/main/java/cn/com/dcsgo/mihx/data/di/DatabaseModule.kt" to "DatabaseModule",
            "data/src/main/java/cn/com/dcsgo/mihx/data/di/DataStoreModule.kt" to "DataStoreModule",
        )
        hiltModules.forEach { (path, moduleName) ->
            val source = file(path)
            if (!source.exists()) {
                fail("Required Hilt module is missing: $path")
                return@forEach
            }
            val text = source.readText()
            if (!text.contains("@Module") || !text.contains("@InstallIn") || !text.contains("SingletonComponent::class") || !text.contains(moduleName)) {
                fail("$path must remain a SingletonComponent Hilt module named $moduleName.")
            }
        }

        val playbackStateMachineDoc = file("docs/architecture/PLAYBACK_STATE_MACHINE.md")
        if (!playbackStateMachineDoc.exists()) {
            fail("Playback state machine documentation is required for Media3/controller/UI synchronization.")
        } else {
            val playbackStateMachineText = playbackStateMachineDoc.readText()
            listOf(
                "idle",
                "preparing",
                "ready",
                "playing",
                "paused",
                "buffering",
                "ended",
                "error",
                "AppMediaSessionService",
                "PlaybackController",
                "ControllerPlaybackStateSynchronizer",
                "PlayerControllerStateFacade",
                "PlayerMediaEventFacade",
            ).forEach { requiredTerm ->
                if (!playbackStateMachineText.contains(requiredTerm)) {
                    fail("PLAYBACK_STATE_MACHINE.md must document playback state/sync term: $requiredTerm")
                }
            }
        }
        val productAudit = file("docs/refactor/PRODUCT_REFACTOR_AUDIT.md")
        if (!productAudit.exists()) {
            fail("PRODUCT_REFACTOR_AUDIT.md must track product refactor completion evidence and remaining risks.")
        } else {
            val productAuditText = productAudit.readText()
            listOf(
                "Verified",
                "Still Not Fully Proven",
                ".\\gradlew.bat test",
                ":app:assembleRelease",
                ":app:assembleBenchmark",
                "Macrobenchmark",
                ":benchmark",
            ).forEach { requiredTerm ->
                if (!productAuditText.contains(requiredTerm)) {
                    fail("PRODUCT_REFACTOR_AUDIT.md must track audit term: $requiredTerm")
                }
            }
        }
        val benchmarkBuildFile = file("benchmark/build.gradle.kts")
        val benchmarkStartupTest = file("benchmark/src/main/java/cn/com/dcsgo/mihx/benchmark/StartupBenchmark.kt")
        if (!benchmarkBuildFile.exists() || !benchmarkStartupTest.exists()) {
            fail("A real :benchmark Macrobenchmark module and startup benchmark test are required.")
        } else {
            val benchmarkBuildText = benchmarkBuildFile.readText()
            if (!benchmarkBuildText.contains("libs.plugins.android.test") ||
                !benchmarkBuildText.contains("""targetProjectPath = ":app"""") ||
                !benchmarkBuildText.contains("androidx.benchmark.macro.junit4")
            ) {
                fail(":benchmark must use the Android test plugin, target :app, and depend on Macrobenchmark.")
            }
            val benchmarkStartupText = benchmarkStartupTest.readText()
            listOf(
                "MacrobenchmarkRule",
                "StartupTimingMetric",
                "StartupMode.COLD",
                "startActivityAndWait",
                "packageName = \"cn.com.dcsgo.mihx\"",
            ).forEach { requiredTerm ->
                if (!benchmarkStartupText.contains(requiredTerm)) {
                    fail("StartupBenchmark must cover Macrobenchmark startup term: $requiredTerm")
                }
            }
        }

        val performanceTraceSource = file("core/common/src/main/java/cn/com/dcsgo/mihx/core/common/PerformanceTrace.kt")
        if (!performanceTraceSource.exists()) {
            fail("PerformanceTrace is required for product performance observability.")
        }
        val performanceAnchors = mapOf(
            "data/src/main/java/cn/com/dcsgo/mihx/data/repository/MusicRepository.kt" to listOf(
                "music_import_scan",
                "music_import_folder",
            ),
            "player/src/main/java/cn/com/dcsgo/mihx/player/PlaybackController.kt" to listOf(
                "controller_play_queue",
                "controller_prepare_queue",
                "controller_sync_queue",
                "play_next_command",
            ),
        )
        performanceAnchors.forEach { (path, operations) ->
            val source = file(path)
            if (!source.exists()) {
                fail("$path is required for performance observability checks.")
                return@forEach
            }
            val sourceText = source.readText()
            operations.forEach { operation ->
                if (!sourceText.contains(operation)) {
                    fail("$path must keep PerformanceTrace operation: $operation")
                }
            }
        }
        val playbackWindowPerformanceTest = file("player/src/test/java/cn/com/dcsgo/mihx/player/window/PlaybackWindowPerformanceShapeTest.kt")
        if (!playbackWindowPerformanceTest.exists()) {
            fail("Playback window performance shape tests are required for 100/500/1000-song product queues.")
        } else {
            val playbackWindowPerformanceText = playbackWindowPerformanceTest.readText()
            listOf("100", "500", "1_000", "71", "WindowedControllerQueuePlanner").forEach { requiredTerm ->
                if (!playbackWindowPerformanceText.contains(requiredTerm)) {
                    fail("PlaybackWindowPerformanceShapeTest must cover product queue sizing term: $requiredTerm")
                }
            }
        }

        val legacyModelPackage = listOf("com", "dcsgo", "data", "model").joinToString(".")
        val legacyModelReferences = textFiles.filter { file ->
            file.readText().contains(legacyModelPackage)
        }
        if (legacyModelReferences.isNotEmpty()) {
            fail("Legacy model package references remain:\n" + legacyModelReferences.joinToString("\n") { it.relativeTo(rootDir).path })
        }

        val legacyComponentsName = "PlayerViewModel" + "Components"
        val legacyFactoryName = "PlayerViewModel" + "ComponentFactory"
        val legacyPlayerViewModelComponents = textFiles.filter { file ->
            val text = file.readText()
            file.name != "build.gradle.kts" &&
                (text.contains(legacyComponentsName) || text.contains(legacyFactoryName))
        }
        if (legacyPlayerViewModelComponents.isNotEmpty()) {
            fail("$legacyComponentsName was the old manual assembly hub; use PlayerRuntime and smaller injected graphs instead:\n" +
                legacyPlayerViewModelComponents.joinToString("\n") { it.relativeTo(rootDir).path })
        }

        val featureBuildLeaks = file("feature").walkTopDown()
            .filter { it.isFile && it.name == "build.gradle.kts" }
            .filter { file ->
                val text = file.readText()
                text.contains("project(\":data\")") || text.contains("project(\":player\")")
            }
            .toList()
        if (featureBuildLeaks.isNotEmpty()) {
            fail("Feature modules must not depend directly on :data or :player:\n" + featureBuildLeaks.joinToString("\n") { it.relativeTo(rootDir).path })
        }
        val featureToFeatureLeaks = file("feature").walkTopDown()
            .filter { it.isFile && it.name == "build.gradle.kts" }
            .filter { file ->
                val text = file.readText()
                Regex("""project\(":feature:""").containsMatchIn(text)
            }
            .toList()
        if (featureToFeatureLeaks.isNotEmpty()) {
            fail("Feature modules must not depend on each other directly; route through :app or shared :core/:domain APIs:\n" +
                featureToFeatureLeaks.joinToString("\n") { it.relativeTo(rootDir).path })
        }
        val lyricsRouteFile = file("feature/lyrics/src/main/java/cn/com/dcsgo/mihx/feature/lyrics/LyricsRoute.kt")
        val lyricsScreenFile = file("feature/lyrics/src/main/java/cn/com/dcsgo/mihx/feature/lyrics/LyricsScreen.kt")
        if (!lyricsRouteFile.exists() || !lyricsScreenFile.exists()) {
            fail(":feature:lyrics must own a Route and Screen instead of being an empty module shell.")
        }
        val settingsRouteFile = file("feature/settings/src/main/java/cn/com/dcsgo/mihx/feature/settings/SettingsRoute.kt")
        val settingsScreenFile = file("feature/settings/src/main/java/cn/com/dcsgo/mihx/feature/settings/SettingsScreen.kt")
        if (!settingsRouteFile.exists() || !settingsScreenFile.exists()) {
            fail(":feature:settings must own a Route and Screen instead of being rendered through the app overlay host.")
        }
        val playStatsRouteFile = file("feature/home/src/main/java/cn/com/dcsgo/mihx/feature/home/PlayStatsRoute.kt")
        val playStatsScreenFile = file("feature/home/src/main/java/cn/com/dcsgo/mihx/feature/home/PlayStatsScreen.kt")
        val quickSkipRouteFile = file("feature/home/src/main/java/cn/com/dcsgo/mihx/feature/home/QuickSkipSongsRoute.kt")
        val quickSkipScreenFile = file("feature/home/src/main/java/cn/com/dcsgo/mihx/feature/home/QuickSkipSongsScreen.kt")
        val versionManagementRouteFile = file("feature/user/src/main/java/cn/com/dcsgo/mihx/feature/user/VersionManagementRoute.kt")
        val versionManagementScreenFile = file("feature/user/src/main/java/cn/com/dcsgo/mihx/feature/user/VersionManagementScreen.kt")
        if (!playStatsRouteFile.exists() || !playStatsScreenFile.exists()) {
            fail("Play statistics must use a feature-owned Route and Screen instead of an AppOverlay branch.")
        }
        if (!quickSkipRouteFile.exists() || !quickSkipScreenFile.exists()) {
            fail("Quick skip songs must use a feature-owned Route and Screen instead of an AppOverlay branch.")
        }
        if (!versionManagementRouteFile.exists() || !versionManagementScreenFile.exists()) {
            fail("Version management must use a feature-owned Route and Screen instead of an AppOverlay branch.")
        }
        val overlayFiles = listOf(
            file("app/src/main/java/cn/com/dcsgo/mihx/navigation/AppOverlay.kt"),
            file("app/src/main/java/cn/com/dcsgo/mihx/app/AppOverlayHost.kt"),
            file("app/src/main/java/cn/com/dcsgo/mihx/app/routes/OverlayRoute.kt"),
        )
        val existingOverlayFiles = overlayFiles.filter { it.exists() }
        if (existingOverlayFiles.isNotEmpty()) {
            fail("App overlay routing has been replaced by Navigation Compose feature routes; remove:\n" +
                existingOverlayFiles.joinToString("\n") { it.relativeTo(rootDir).path })
        }
        val homeFeatureSource = file("feature/home")
        val homeLyricsUiImports = projectFiles(homeFeatureSource, setOf("kt", "java"))
            .filter { file -> file.readText().contains("cn.com.dcsgo.mihx.ui.lyrics") }
            .toList()
        if (homeLyricsUiImports.isNotEmpty()) {
            fail(":feature:home must navigate to :feature:lyrics instead of rendering lyrics UI directly:\n" +
                homeLyricsUiImports.joinToString("\n") { it.relativeTo(rootDir).path })
        }
        val playerStartupFacade = file("feature/player/src/main/java/cn/com/dcsgo/mihx/feature/player/PlayerStartupFacade.kt")
        if (playerStartupFacade.exists() && playerStartupFacade.readText().contains("Bluetooth")) {
            fail("Bluetooth playback monitoring must be user-triggered from settings, not initialized during app startup.")
        }
        val settingsScreen = file("feature/settings/src/main/java/cn/com/dcsgo/mihx/feature/settings/SettingsScreen.kt")
        if (settingsScreen.exists() && !settingsScreen.readText().contains("蓝牙播放监听") || settingsScreen.exists() && !settingsScreen.readText().contains("申请蓝牙权限")) {
            fail("Settings must expose a user-triggered Bluetooth playback monitoring control.")
        }
        val playerSettingsRepository = file("domain/src/main/java/cn/com/dcsgo/mihx/domain/repository/PlayerSettingsRepository.kt")
        val playerSettingsDataStore = file("data/src/main/java/cn/com/dcsgo/mihx/data/repository/PlayerSettingsDataStore.kt")
        val playerUiState = file("feature/player/src/main/java/cn/com/dcsgo/mihx/feature/player/PlayerUiState.kt")
        listOf(
            "domain/src/main/java/cn/com/dcsgo/mihx/domain/repository/PlayerSettingsRepository.kt",
            "data/src/main/java/cn/com/dcsgo/mihx/data/repository/PlayerSettingsDataStore.kt",
            "feature/player/src/main/java/cn/com/dcsgo/mihx/feature/player/PlayerUiState.kt",
        ).forEach { relative ->
            if (!file(relative).exists()) {
                fail("Required file is missing: $relative")
            }
        }
        if (playerSettingsRepository.exists() && playerSettingsDataStore.exists() && playerUiState.exists()) {
            val repoText = playerSettingsRepository.readText()
            val dataStoreText = playerSettingsDataStore.readText()
            val uiStateText = playerUiState.readText()
            if (!repoText.contains("bluetoothPlaybackMonitoringEnabled") ||
                !dataStoreText.contains("BLUETOOTH_PLAYBACK_MONITORING_ENABLED") ||
                !uiStateText.contains("bluetoothPlaybackMonitoringEnabled")
            ) {
                fail("Bluetooth playback monitoring must be a persisted PlayerSettingsRepository/DataStore setting surfaced through PlayerUiState.")
            }
            if (!repoText.contains("playbackNotificationEnabled") ||
                !dataStoreText.contains("PLAYBACK_NOTIFICATION_ENABLED") ||
                !uiStateText.contains("playbackNotificationEnabled")
            ) {
                fail("Playback notification control must be a persisted PlayerSettingsRepository/DataStore setting surfaced through PlayerUiState.")
            }
        }
        val appNavHost = file("app/src/main/java/cn/com/dcsgo/mihx/app/AppNavHost.kt")
        val appRoot = file("app/src/main/java/cn/com/dcsgo/mihx/app/AppRoot.kt")
        if (appNavHost.exists() && appRoot.exists()) {
            val appShellText = appNavHost.readText() + "\n" + appRoot.readText()
            if (Regex("""remember\s*\{[^}]*bluetoothPlaybackMonitoring""").containsMatchIn(appShellText)) {
                fail("Bluetooth playback monitoring state must come from PlayerUiState, not app shell local remember state.")
            }
            if (Regex("""remember\s*\{[^}]*playbackNotification""").containsMatchIn(appShellText)) {
                fail("Playback notification state must come from PlayerUiState, not app shell local remember state.")
            }
        }
        val permissionCoordinator = file("app/src/main/java/cn/com/dcsgo/mihx/app/permissions/PermissionCoordinator.kt")
        if (permissionCoordinator.exists()) {
            val permissionText = permissionCoordinator.readText()
            if (!permissionText.contains("requestNotificationPermission(onGranted") ||
                !permissionText.contains("onGranted = onGranted")
            ) {
                fail("Notification permission requests must support a granted callback for user-triggered settings.")
            }
        }
        val startupPathFiles = listOf(
            file("app/src/main/java/cn/com/dcsgo/mihx/MainActivity.kt"),
            file("app/src/main/java/cn/com/dcsgo/mihx/app/AppRoot.kt"),
            file("feature/player/src/main/java/cn/com/dcsgo/mihx/feature/player/PlayerRuntime.kt"),
            file("feature/player/src/main/java/cn/com/dcsgo/mihx/feature/player/PlayerStartupFacade.kt"),
        )
        val startupPermissionRequests = startupPathFiles.filter { source ->
            source.exists() && source.readText().let { text ->
                text.contains("requestNotificationPermission(") ||
                    text.contains("requestBluetoothConnectPermission(") ||
                    text.contains("POST_NOTIFICATIONS") ||
                    text.contains("BLUETOOTH_CONNECT")
            }
        }
        if (startupPermissionRequests.isNotEmpty()) {
            fail("Notification and Bluetooth permissions must be requested from user-triggered settings, not startup paths:\n" +
                startupPermissionRequests.joinToString("\n") { it.relativeTo(rootDir).path })
        }

        val composeSourceFiles = listOf("app", "core", "feature")
            .flatMap { dirName ->
                val dir = file(dirName)
                if (!dir.exists()) emptyList() else projectFiles(dir, setOf("kt")).toList()
            }
        val unkeyedLazyItems = composeSourceFiles.filter { source ->
            val text = source.readText()
            Regex("""\bitems(?:Indexed)?\s*\((?![\s\S]{0,160}\bkey\s*=)""").containsMatchIn(text) ||
                Regex("""\bitem\s*\{""").containsMatchIn(text)
        }
        if (unkeyedLazyItems.isNotEmpty()) {
            fail("LazyColumn item/items calls must use stable keys for product-scale lists:\n" +
                unkeyedLazyItems.joinToString("\n") { it.relativeTo(rootDir).path })
        }

        val implementationImportRegex = Regex("""import\s+cn\.com\.dcsgo\.mihx\.data\.(repository|local)\.""")
        val implementationImports = listOf("feature", "domain", "player")
            .flatMap { dirName ->
                val dir = file(dirName)
                if (!dir.exists()) emptyList() else projectFiles(dir, setOf("kt", "java")).filter { file ->
                    implementationImportRegex.containsMatchIn(file.readText())
                }.toList()
            }
        if (implementationImports.isNotEmpty()) {
            fail("Implementation-layer imports leaked across module boundaries:\n" + implementationImports.joinToString("\n") { it.relativeTo(rootDir).path })
        }
        val musicRepositoryText = requiredText("data/src/main/java/cn/com/dcsgo/mihx/data/repository/MusicRepository.kt")
        listOf(
            "SongRepository",
            "PlaylistRepository",
            "MusicImportRepository",
            "AlbumArtRepository",
        ).forEach { domainRepositoryName ->
            if (Regex(""":\s*[\s\S]*\b$domainRepositoryName\b""").containsMatchIn(musicRepositoryText)) {
                fail("MusicRepository must stay an internal storage coordinator; bind $domainRepositoryName through a narrow adapter instead.")
            }
        }
        listOf(
            "SongRepositoryAdapter.kt" to "SongRepository",
            "PlaylistRepositoryAdapter.kt" to "PlaylistRepository",
            "MusicImportRepositoryAdapter.kt" to "MusicImportRepository",
            "AlbumArtRepositoryAdapter.kt" to "AlbumArtRepository",
        ).forEach { (adapterFileName, domainRepositoryName) ->
            val adapterFile = file("data/src/main/java/cn/com/dcsgo/mihx/data/repository/$adapterFileName")
            if (!adapterFile.exists() || !adapterFile.readText().contains(": $domainRepositoryName")) {
                fail("$adapterFileName must provide the narrow data-layer implementation for $domainRepositoryName.")
            }
        }

        // m-6（评审 2026-10-15）：:player 模块的实现类已从 cn.com.dcsgo.mihx.data.player
        // 迁到 cn.com.dcsgo.mihx.player（与模块 namespace 一致）。该包不再存在，
        // 此规则升级为全 player 模块禁止引用旧包，防止回潮。
        val playerModuleSource = file("player/src")
        val staleDataPlayerReferences = projectFiles(playerModuleSource, setOf("kt", "java", "xml"))
            .filter { it.readText().contains("cn.com.dcsgo.mihx.data.player") }
            .toList()
        if (staleDataPlayerReferences.isNotEmpty()) {
            fail(":player classes must live in cn.com.dcsgo.mihx.player; stale cn.com.dcsgo.mihx.data.player references remain:\n" +
                staleDataPlayerReferences.joinToString("\n") { it.relativeTo(rootDir).path })
        }

        val coreModelResFiles = file("core/model/src/main/res")
            .takeIf { it.exists() }
            ?.let { resDir -> projectFiles(resDir, setOf("xml")).toList() }
            .orEmpty()
        if (coreModelResFiles.isNotEmpty()) {
            fail(":core:model must stay a pure data-model module without Android resources; move them to :core:ui or the owning feature:\n" +
                coreModelResFiles.joinToString("\n") { it.relativeTo(rootDir).path })
        }
        val uiResourceIdRegex = Regex("""\bR\.(drawable|string|color|dimen|raw)\.|\bandroid\.R\.""")
        val coreModelResourceIds = file("core/model/src/main")
            .takeIf { it.exists() }
            ?.let { modelDir ->
                projectFiles(modelDir, setOf("kt", "java")).filter { source ->
                    uiResourceIdRegex.containsMatchIn(source.readText())
                }.toList()
            }
            .orEmpty()
        if (coreModelResourceIds.isNotEmpty()) {
            fail(":core:model must not carry UI resource ids; map them in :core:ui (e.g. PlayMode.iconRes()) or the owning feature:\n" +
                coreModelResourceIds.joinToString("\n") { it.relativeTo(rootDir).path })
        }

        // ── 分层不变量：:core:model 与 :domain 不得依赖 Android 框架 ──
        //
        // 评审 2026-10-27 · M-21：此前门禁只查「资源 / R id」，**没有**查源码里的
        // `import android.*` —— 于是 CLAUDE.md 声明的「:domain 与 :core:model 不得有
        // Android 框架依赖」既没被强制、也已被违反（android.net.Uri 泄漏进域边界）。
        //
        // 本规则按**棘轮（ratchet）**方式落地：现存违规列入基线白名单（不会立刻把
        // 构建打红），但**新增**任何一处 `import android.*` 都会让构建失败。
        // 待 Uri → String 的机械迁移完成后，把基线清空即可升级为完全禁止。
        //
        // 说明：`androidx.compose.runtime.{Stable,Immutable}` **不在本规则范围内** ——
        // 它们是编译期稳定性注解（无运行时行为），且每个 Compose 消费模块自己都直接
        // 依赖 compose-runtime，Compose 编译器在消费方编译期照常读取，稳定性推断不受影响。
        val androidFrameworkImportRegex = Regex("""^\s*import\s+android\.[a-zA-Z.]+""", RegexOption.MULTILINE)
        val pureLayerBaseline = setOf(
            // ── 基线：待 Uri → String 迁移后移除 ──
            "core/model/src/main/java/cn/com/dcsgo/mihx/core/model/AlbumEntry.kt",
            "core/model/src/main/java/cn/com/dcsgo/mihx/core/model/ArtistEntry.kt",
            "core/model/src/main/java/cn/com/dcsgo/mihx/core/model/Song.kt",
            "domain/src/main/java/cn/com/dcsgo/mihx/domain/importing/FolderImporter.kt",
            "domain/src/main/java/cn/com/dcsgo/mihx/domain/repository/MusicImportRepository.kt",
        )
        val pureLayerDirs = listOf("core/model/src/main", "domain/src/main")
        val pureLayerAndroidImports = pureLayerDirs
            .map { dirName -> file(dirName) }
            .filter { it.exists() }
            .flatMap { dir -> projectFiles(dir, setOf("kt", "java")).toList() }
            .filter { source ->
                androidFrameworkImportRegex.containsMatchIn(source.readText())
            }
            .map { it.relativeTo(rootDir).path.replace(File.separatorChar, '/') }
            .filterNot { it in pureLayerBaseline }
            .sorted()
        if (pureLayerAndroidImports.isNotEmpty()) {
            fail(
                ":core:model and :domain must not depend on the Android framework (import android.*); " +
                    "use plain value types (e.g. String instead of android.net.Uri) and parse at the :data/:player boundary:\n" +
                    pureLayerAndroidImports.joinToString("\n")
            )
        }

        val directAndroidLogRegex = Regex("""\bLog\.(d|i|w|e|v|wtf)\s*\(""")
        val directAndroidLogs = textFiles.filter { file ->
            val relativePath = file.relativeTo(rootDir).path.replace(File.separatorChar, '/')
            relativePath != "core/common/src/main/java/cn/com/dcsgo/mihx/core/common/AppLogger.kt" &&
                directAndroidLogRegex.containsMatchIn(file.readText())
        }
        if (directAndroidLogs.isNotEmpty()) {
            fail("Use AppLog instead of direct android.util.Log calls:\n" + directAndroidLogs.joinToString("\n") { it.relativeTo(rootDir).path })
        }

        // ── 不变量：src/main 不得出现 runBlocking ──
        //
        // 评审 2026-10-27 · N-1：runBlocking 把挂起的磁盘 IO 传染给调用线程，
        // 主线程可达的路径（ViewModel onCleared、用户「清空播放队列」）会**同步阻塞 UI**。
        // domain 契约层与 :data 已彻底清零；`:player/PlaybackStateStore` 也已挂起化。
        // 本规则防止回潮。
        val runBlockingRegex = Regex("""\brunBlocking\s*\(""")
        val runBlockingInMain = textFiles.filter { file ->
            val relativePath = file.relativeTo(rootDir).path.replace(File.separatorChar, '/')
            if (!relativePath.contains("/src/main/")) return@filter false
            // 只看**代码**，不看注释：历史说明里常提到 runBlocking（如「旧实现 17 处
            // runBlocking 桥」），那些是叙述不是调用。
            file.readLines().any { line ->
                val trimmed = line.trimStart()
                val isCommentLine = trimmed.startsWith("//") ||
                    trimmed.startsWith("*") ||
                    trimmed.startsWith("/*")
                if (isCommentLine) {
                    false
                } else {
                    runBlockingRegex.containsMatchIn(line.substringBefore("//"))
                }
            }
        }
        if (runBlockingInMain.isNotEmpty()) {
            fail(
                "src/main must not use runBlocking (it blocks the calling thread on disk IO); " +
                    "use suspend functions and let the caller pick the dispatcher:\n" +
                    runBlockingInMain.joinToString("\n") { it.relativeTo(rootDir).path }
            )
        }

        // ── 不变量：loadSongInfo 必须经 loadSongInfoSafely 调用 ──
        //
        // 评审 2026-10-27 · C-1 + 门禁建议 3：`loadSongInfo` 的实现末端有一次未受保护的
        // Room 读（`emotionRepository.get`），裸调时 Room 异常会从 LaunchedEffect /
        // produceState 的协程里一路抛到主线程默认 handler → **进程崩溃**。修复把全部
        // 调用点收敛到 `:core:ui` 的 `loadSongInfoSafely`（内部 runCatching + 取消异常
        // 原样上抛）。本规则**直接固化该修复**：src/main 中只允许 SongInfoLoader.kt
        // （loadSongInfoSafely 的实现本体）出现裸 `loadSongInfo(` 调用，其余任何裸调
        // 都是回潮 —— 防止新增调用点再次把协程带崩。
        val bareLoadSongInfoRegex = Regex("""\bloadSongInfo\s*\(""")
        val bareLoadSongInfoFiles = textFiles.filter { file ->
            val relativePath = file.relativeTo(rootDir).path.replace(File.separatorChar, '/')
            if (!relativePath.contains("/src/main/")) return@filter false
            if (relativePath.contains("SongInfoLoader.kt")) return@filter false
            bareLoadSongInfoRegex.containsMatchIn(file.readText())
        }
        if (bareLoadSongInfoFiles.isNotEmpty()) {
            fail(
                "loadSongInfo must be invoked via loadSongInfoSafely (:core:ui), not called bare " +
                    "(C-1: an uncaught Room exception in a LaunchedEffect/produceState coroutine crashes the process):\n" +
                    bareLoadSongInfoFiles.joinToString("\n") { it.relativeTo(rootDir).path }
            )
        }

        // ── 不变量：PerformanceTrace.allow() 必须有真实调用点 ──
        //
        // 评审 2026-10-27 · M-20 + 门禁建议 4：`allow()` 只决定"是否计算"，输出已改走
        // 独立的 `performance` 通道（不受 `debugLoggingEnabled` 约束）。若生产代码里
        // 没有 `allow()` 调用点，白名单 API 就是死代码 —— 规则要求 src/main 里至少
        // 出现一处 `PerformanceTrace.allow(`（核心播放链路在 MelodyApplication 启动时登记）。
        val performanceAllowHasCallSite = textFiles.any { file ->
            val relativePath = file.relativeTo(rootDir).path.replace(File.separatorChar, '/')
            relativePath.contains("/src/main/") &&
                file.readText().contains("PerformanceTrace.allow(")
        }
        if (!performanceAllowHasCallSite) {
            fail(
                "PerformanceTrace.allow(...) must have at least one real call site in src/main " +
                    "(register the core playback path in MelodyApplication.onCreate); otherwise the " +
                    "release whitelist API is dead code."
            )
        }

        val appManifest = file("app/src/main/AndroidManifest.xml")
        if (appManifest.exists() && appManifest.readText().contains("AppMediaSessionService")) {
            fail("AppMediaSessionService must be declared by the :player manifest, not the :app manifest.")
        }
        listOf(
            // READ_MEDIA_AUDIO 仅用于加速导入扫描(2026-10-06 用户拍板接受): 已授权时 java.io.File
            // 直遍历快几个数量级, SAF 文档树仍是主路径与兜底, 拒绝时静默回退 SAF。不走 READ_EXTERNAL_STORAGE。
            "android.permission.READ_EXTERNAL_STORAGE",
        ).forEach { permission ->
            if (appManifest.exists() && appManifest.readText().contains(permission)) {
                fail("$permission must not be declared for SAF folder import; request explicit document-tree access instead.")
            }
        }

        val forbiddenAndroidThingsDependency = listOf("com", "google", "android", "things").joinToString(".")
        val forbiddenDependencies = textFiles.filter { file ->
            file.readText().contains(forbiddenAndroidThingsDependency)
        }
        if (forbiddenDependencies.isNotEmpty()) {
            fail("Forbidden Android Things dependency/reference remains:\n" + forbiddenDependencies.joinToString("\n") { it.relativeTo(rootDir).path })
        }

        val appBuildText = requiredText("app/build.gradle.kts")
        // m-10：release 块提取改用括号配对（旧「取首个 \n}」在块内出现嵌套 lambda 时会截断）
        val releaseBlock = extractBracedBlock(appBuildText, "release")
            ?: failNow("app release build type is missing.")
        if (!releaseBlock.contains("isMinifyEnabled = true")) {
            fail("app release build type must enable R8 minification.")
        }
        if (!releaseBlock.contains("isShrinkResources = true")) {
            fail("app release build type must enable resource shrinking.")
        }
        if (!appBuildText.contains("""create("benchmark")""")) {
            fail("app benchmark build type is missing.")
        }
        if (!appBuildText.contains("""initWith(getByName("release"))""")) {
            fail("app benchmark build type must inherit release settings.")
        }

        val requiredPrivacyExcludes = listOf(
            "music_player_prefs.xml",
            "play_stats_prefs.xml",
            "quick_skip_songs_prefs.xml",
            "melody.db",
            "melody.db-journal",
            "melody.db-shm",
            "melody.db-wal",
            "datastore/player_settings.preferences_pb",
            "datastore/playback_state.preferences_pb",
            "cache/album_art/",
        )
        val privacyRuleFiles = listOf(
            "app/src/main/res/xml/backup_rules.xml",
            "app/src/main/res/xml/data_extraction_rules.xml",
        )
        privacyRuleFiles.forEach { relative ->
            val ruleFile = file(relative)
            if (!ruleFile.exists()) {
                fail("$relative is missing (privacy exclude rules are required).")
                return@forEach
            }
            val text = ruleFile.readText()
            val missing = requiredPrivacyExcludes.filterNot(text::contains)
            if (missing.isNotEmpty()) {
                fail("${ruleFile.relativeTo(rootDir).path} is missing privacy excludes: ${missing.joinToString()}")
            }
        }

        val schemaDir = file("data/schemas/cn.com.dcsgo.mihx.data.local.MelodyDatabase")
        val schemaV1 = schemaDir.resolve("1.json")
        val schemaV2 = schemaDir.resolve("2.json")
        val schemaV3 = schemaDir.resolve("3.json")
        if (!schemaV1.exists() || !schemaV2.exists() || !schemaV3.exists()) {
            fail("Room schema history must include MelodyDatabase versions 1, 2, and 3.")
        } else {
            val schemaV1Text = schemaV1.readText()
            val schemaV2Text = schemaV2.readText()
            val schemaV3Text = schemaV3.readText()
            if (schemaV1Text.contains("quick_skip_short_play_counts")) {
                fail("Room schema v1 must remain an immutable snapshot before quick_skip_short_play_counts was added.")
            }
            if (!schemaV2Text.contains("quick_skip_short_play_counts")) {
                fail("Room schema v2 must include quick_skip_short_play_counts.")
            }
            if (schemaV2Text.contains("lrcUri")) {
                fail("Room schema v2 must remain an immutable snapshot before lrcUri was added.")
            }
            if (!schemaV3Text.contains("lrcUri")) {
                fail("Room schema v3 must include imported LRC URI support.")
            }
        }

        // m-4（评审 2026-10-15）：迁移本体移至 MelodyMigrations（供迁移回归测试引用），
        // DatabaseModule 保留注册职责；规则随结构更新。
        val melodyMigrationsText = requiredText("data/src/main/java/cn/com/dcsgo/mihx/data/local/migration/MelodyMigrations.kt")
        val databaseModuleText = requiredText("data/src/main/java/cn/com/dcsgo/mihx/data/di/DatabaseModule.kt")
        if (!melodyMigrationsText.contains("Migration(1, 2)") ||
            !melodyMigrationsText.contains("Migration(2, 3)") ||
            !databaseModuleText.contains("addMigrations(*MelodyMigrations.ALL)")
        ) {
            fail("MelodyDatabase must register the Room migrations from version 1 to 2 and 2 to 3.")
        }
    }

    /**
     * m-10：按大括号配对提取 `name { ... }` 块内容（旧正则「非贪婪到首个换行右括号」
     * 在块内出现嵌套 lambda/compose 闭包时会截断，属评审点名的脆弱点）。
     * 返回块内文本（不含外层花括号）；找不到返回 null。
     */
    internal fun extractBracedBlock(text: String, name: String): String? {
        val opener = Regex("""\b$name\s*\{""").find(text) ?: return null
        var depth = 0
        var index = opener.range.last + 1 // 指向 '{'
        val start = index + 1
        while (index < text.length) {
            when (text[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, index)
                }
            }
            index++
        }
        return null
    }
}
