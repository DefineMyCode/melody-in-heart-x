// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.spotless)
}

spotless {
    kotlinGradle {
        target("*.gradle.kts", "gradle/**/*.gradle.kts")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

subprojects {
    apply(plugin = "com.diffplug.spotless")

    spotless {
        kotlin {
            target("src/**/*.kt")
            trimTrailingWhitespace()
            endWithNewline()
        }
        kotlinGradle {
            target("*.gradle.kts")
            trimTrailingWhitespace()
            endWithNewline()
        }
        format("androidXml") {
            target("src/**/*.xml")
            trimTrailingWhitespace()
            endWithNewline()
        }
    }

    tasks.configureEach {
        if (name == "check") {
            dependsOn("spotlessCheck")
        }
    }
}

tasks.register("verifyProductArchitecture") {
    group = "verification"
    description = "Checks module boundaries and release privacy rules for the product architecture."

    doLast {
        fun fail(message: String): Nothing = throw GradleException(message)

        fun projectFiles(
            root: File,
            extensions: Set<String>,
        ): Sequence<File> = root.walkTopDown()
            .onEnter { dir -> dir.name !in setOf("build", ".gradle", ".git", ".idea") }
            .filter { file -> file.isFile && file.extension in extensions }

        val textFiles = projectFiles(rootDir, setOf("kt", "kts", "java", "xml", "toml")).toList()

        if (!file("gradle/libs.versions.toml").readText().contains("""spotless = { id = "com.diffplug.spotless"""")) {
            fail("Spotless must stay in the version catalog so formatting can be enabled consistently.")
        }
        val rootBuildText = file("build.gradle.kts").readText()
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
        val settingsText = file("settings.gradle.kts").readText()
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
            "player/src/main/java/cn/com/dcsgo/mihx/data/player/AppMediaSessionService.kt" to "@AndroidEntryPoint",
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
            }
            val text = source.readText()
            if (!text.contains("@Module") || !text.contains("@InstallIn") || !text.contains("SingletonComponent::class") || !text.contains(moduleName)) {
                fail("$path must remain a SingletonComponent Hilt module named $moduleName.")
            }
        }

        val playbackStateMachineDoc = file("PLAYBACK_STATE_MACHINE.md")
        if (!playbackStateMachineDoc.exists()) {
            fail("Playback state machine documentation is required for Media3/controller/UI synchronization.")
        }
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
        val productAudit = file("PRODUCT_REFACTOR_AUDIT.md")
        if (!productAudit.exists()) {
            fail("PRODUCT_REFACTOR_AUDIT.md must track product refactor completion evidence and remaining risks.")
        }
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
        val benchmarkBuildFile = file("benchmark/build.gradle.kts")
        val benchmarkStartupTest = file("benchmark/src/main/java/cn/com/dcsgo/mihx/benchmark/StartupBenchmark.kt")
        if (!benchmarkBuildFile.exists() || !benchmarkStartupTest.exists()) {
            fail("A real :benchmark Macrobenchmark module and startup benchmark test are required.")
        }
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

        val performanceTraceSource = file("core/common/src/main/java/cn/com/dcsgo/mihx/core/common/PerformanceTrace.kt")
        if (!performanceTraceSource.exists()) {
            fail("PerformanceTrace is required for product performance observability.")
        }
        val performanceAnchors = mapOf(
            "data/src/main/java/cn/com/dcsgo/mihx/data/repository/MusicRepository.kt" to listOf(
                "music_import_scan",
                "music_import_folder",
            ),
            "player/src/main/java/cn/com/dcsgo/mihx/data/player/PlaybackController.kt" to listOf(
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
        }
        val playbackWindowPerformanceText = playbackWindowPerformanceTest.readText()
        listOf("100", "500", "1_000", "71", "WindowedControllerQueuePlanner").forEach { requiredTerm ->
            if (!playbackWindowPerformanceText.contains(requiredTerm)) {
                fail("PlaybackWindowPerformanceShapeTest must cover product queue sizing term: $requiredTerm")
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
        if (playerStartupFacade.readText().contains("Bluetooth")) {
            fail("Bluetooth playback monitoring must be user-triggered from settings, not initialized during app startup.")
        }
        val settingsScreen = file("feature/settings/src/main/java/cn/com/dcsgo/mihx/feature/settings/SettingsScreen.kt").readText()
        if (!settingsScreen.contains("蓝牙播放监听") || !settingsScreen.contains("申请蓝牙权限")) {
            fail("Settings must expose a user-triggered Bluetooth playback monitoring control.")
        }
        val playerSettingsRepository = file("domain/src/main/java/cn/com/dcsgo/mihx/domain/repository/PlayerSettingsRepository.kt").readText()
        val playerSettingsDataStore = file("data/src/main/java/cn/com/dcsgo/mihx/data/repository/PlayerSettingsDataStore.kt").readText()
        val playerUiState = file("feature/player/src/main/java/cn/com/dcsgo/mihx/feature/player/PlayerUiState.kt").readText()
        if (!playerSettingsRepository.contains("bluetoothPlaybackMonitoringEnabled") ||
            !playerSettingsDataStore.contains("BLUETOOTH_PLAYBACK_MONITORING_ENABLED") ||
            !playerUiState.contains("bluetoothPlaybackMonitoringEnabled")
        ) {
            fail("Bluetooth playback monitoring must be a persisted PlayerSettingsRepository/DataStore setting surfaced through PlayerUiState.")
        }
        if (!playerSettingsRepository.contains("playbackNotificationEnabled") ||
            !playerSettingsDataStore.contains("PLAYBACK_NOTIFICATION_ENABLED") ||
            !playerUiState.contains("playbackNotificationEnabled")
        ) {
            fail("Playback notification control must be a persisted PlayerSettingsRepository/DataStore setting surfaced through PlayerUiState.")
        }
        val appNavHost = file("app/src/main/java/cn/com/dcsgo/mihx/app/AppNavHost.kt").readText()
        val appRoot = file("app/src/main/java/cn/com/dcsgo/mihx/app/AppRoot.kt").readText()
        val appShellText = appNavHost + "\n" + appRoot
        if (Regex("""remember\s*\{[^}]*bluetoothPlaybackMonitoring""").containsMatchIn(appShellText)) {
            fail("Bluetooth playback monitoring state must come from PlayerUiState, not app shell local remember state.")
        }
        if (Regex("""remember\s*\{[^}]*playbackNotification""").containsMatchIn(appShellText)) {
            fail("Playback notification state must come from PlayerUiState, not app shell local remember state.")
        }
        val permissionCoordinator = file("app/src/main/java/cn/com/dcsgo/mihx/app/permissions/PermissionCoordinator.kt").readText()
        if (!permissionCoordinator.contains("requestNotificationPermission(onGranted") ||
            !permissionCoordinator.contains("onGranted = onGranted")
        ) {
            fail("Notification permission requests must support a granted callback for user-triggered settings.")
        }
        val startupPathFiles = listOf(
            file("app/src/main/java/cn/com/dcsgo/mihx/MainActivity.kt"),
            file("app/src/main/java/cn/com/dcsgo/mihx/app/AppRoot.kt"),
            file("feature/player/src/main/java/cn/com/dcsgo/mihx/feature/player/PlayerRuntime.kt"),
            file("feature/player/src/main/java/cn/com/dcsgo/mihx/feature/player/PlayerStartupFacade.kt"),
        )
        val startupPermissionRequests = startupPathFiles.filter { source ->
            val text = source.readText()
            text.contains("requestNotificationPermission(") ||
                text.contains("requestBluetoothConnectPermission(") ||
                text.contains("POST_NOTIFICATIONS") ||
                text.contains("BLUETOOTH_CONNECT")
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
        val musicRepositoryText = file("data/src/main/java/cn/com/dcsgo/mihx/data/repository/MusicRepository.kt").readText()
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
        val windowLayerImplementationImports = file("player/src/main/java/cn/com/dcsgo/mihx/player/window")
            .takeIf { it.exists() }
            ?.let { windowDir ->
                projectFiles(windowDir, setOf("kt", "java")).filter { file ->
                    file.readText().contains("cn.com.dcsgo.mihx.data.player")
                }.toList()
            }
            .orEmpty()
        if (windowLayerImplementationImports.isNotEmpty()) {
            fail("Windowed playback planners must depend on domain playback policy, not data.player implementations:\n" +
                windowLayerImplementationImports.joinToString("\n") { it.relativeTo(rootDir).path })
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

        val appManifest = file("app/src/main/AndroidManifest.xml").readText()
        if (appManifest.contains("AppMediaSessionService")) {
            fail("AppMediaSessionService must be declared by the :player manifest, not the :app manifest.")
        }
        listOf(
            "android.permission.READ_MEDIA_AUDIO",
            "android.permission.READ_EXTERNAL_STORAGE",
        ).forEach { permission ->
            if (appManifest.contains(permission)) {
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

        val appBuildFile = file("app/build.gradle.kts")
        val appBuildText = appBuildFile.readText()
        val releaseBlock = Regex("""release\s*\{([\s\S]*?)\n\s*\}""")
            .find(appBuildText)
            ?.groupValues
            ?.get(1)
            ?: fail("app release build type is missing.")
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
            file("app/src/main/res/xml/backup_rules.xml"),
            file("app/src/main/res/xml/data_extraction_rules.xml"),
        )
        privacyRuleFiles.forEach { ruleFile ->
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
        }
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
        val databaseModuleText = file("data/src/main/java/cn/com/dcsgo/mihx/data/di/DatabaseModule.kt").readText()
        if (!databaseModuleText.contains("Migration(1, 2)") ||
            !databaseModuleText.contains("Migration(2, 3)") ||
            !databaseModuleText.contains("addMigrations(MIGRATION_1_2, MIGRATION_2_3)")
        ) {
            fail("MelodyDatabase must register the Room migrations from version 1 to 2 and 2 to 3.")
        }
    }
}

tasks.configureEach {
    if (name == "check") {
        dependsOn(
            "spotlessCheck",
            "verifyProductArchitecture",
        )
        dependsOn(subprojects.map { "${it.path}:check" })
    }
}
