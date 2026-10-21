---
type: Testing
title: "Test Map: Behavior Locks and Verification Paths"
description: Maps the repository's tests onto the behaviors they lock — planner/mood-slot/explore-play/word-preference-voting/queue-window/state-machine-persistence/data-layer(/migration v10→v11)/permission-common tests plus the v3.10.2 skin-description, app-shell, and playlist template suites — and the JVM / instrumented / benchmark three-layer verification split, with narrow commands to run before changing each subsystem.
tags: [testing, unit-tests, regression, verification, gradle, player, data, explore, migration, skin, shell, template]
sources:
  - id: openwiki-source-8037e2358a2c4f9b2c722a11
    resource: repo://AGENTS.md
  - id: openwiki-source-3e7209a00de8aa3c66577a46
    resource: repo://app/src/androidTest/java/cn/com/dcsgo/mihx/HomeScreenComposeTest.kt
  - id: openwiki-source-c2a1f3775d7b70925516b87d
    resource: repo://app/src/test/java/cn/com/dcsgo/mihx/app/permissions/RuntimePermissionPolicyTest.kt
  - id: openwiki-source-fe58f035f4ebb58587ed5451
    resource: repo://app/src/test/java/cn/com/dcsgo/mihx/app/shell/AppShellEquivalenceTest.kt
  - id: openwiki-source-d7e0a0a9877af2b505d93f02
    resource: repo://app/src/test/java/cn/com/dcsgo/mihx/app/shell/DefaultShellConsistencyTest.kt
  - id: openwiki-source-19c9e7ab9fa30ad12ef03742
    resource: repo://app/src/test/java/cn/com/dcsgo/mihx/app/shell/GridLayoutSwitchTest.kt
  - id: openwiki-source-7e9709c357ded7bcfa8b4a1b
    resource: repo://app/src/test/java/cn/com/dcsgo/mihx/app/shell/LibrarySongListTemplateTest.kt
  - id: openwiki-source-eeca4833ad0dc66b19ad5e7d
    resource: repo://app/src/test/java/cn/com/dcsgo/mihx/app/shell/LuckyPlayEntryPolicyTest.kt
  - id: openwiki-source-0040b304dd9434d71728bd0c
    resource: repo://app/src/test/java/cn/com/dcsgo/mihx/app/shell/SheetSkeletonTest.kt
  - id: openwiki-source-9a67b3936e37eeadf78005b1
    resource: repo://app/src/test/java/cn/com/dcsgo/mihx/app/shell/SkinSwitcherCatalogConsistencyTest.kt
  - id: openwiki-source-89edf2ad9545cf58b7898621
    resource: repo://app/src/test/java/cn/com/dcsgo/mihx/app/shell/SongListTemplateCatalogTest.kt
  - id: openwiki-source-9fec4be977076779f99d3b5d
    resource: repo://app/src/test/java/cn/com/dcsgo/mihx/ResolveResumeSongTest.kt
  - id: openwiki-source-f90eb2ce767b9455d448efd3
    resource: repo://benchmark/src/main/java/cn/com/dcsgo/mihx/benchmark/StartupBenchmark.kt
  - id: openwiki-source-2a9daaac1604f238ef4c63fb
    resource: repo://build.gradle.kts
  - id: openwiki-source-7d322a65ddca1cf4588fa5e3
    resource: repo://buildSrc/src/main/kotlin/cn/com/dcsgo/mihx/verify/ProductArchitectureVerification.kt
  - id: openwiki-source-a2371d6362e5db4bc834ad03
    resource: repo://CLAUDE.md
  - id: openwiki-source-7324a01dfc39c0e1f1a18882
    resource: repo://core/common/src/test/java/cn/com/dcsgo/mihx/core/common/AppLoggerTest.kt
  - id: openwiki-source-9beb42baf70537bff2b8c477
    resource: repo://core/common/src/test/java/cn/com/dcsgo/mihx/core/common/PerformanceTraceTest.kt
  - id: openwiki-source-c45f629879f8babdcb1f3397
    resource: repo://core/model/src/main/java/cn/com/dcsgo/mihx/core/model/WordPreference.kt
  - id: openwiki-source-131bcb2cdaccf1fede890a49
    resource: repo://core/model/src/test/java/cn/com/dcsgo/mihx/core/model/LyricsHighlightLeadTest.kt
  - id: openwiki-source-3320f8290c308322a2fc8b78
    resource: repo://core/skin/src/test/java/cn/com/dcsgo/mihx/core/skin/DefaultSkinTest.kt
  - id: openwiki-source-e56879bb149cff0c441fc3bd
    resource: repo://core/skin/src/test/java/cn/com/dcsgo/mihx/core/skin/LibrarySegmentsMatchSourceTest.kt
  - id: openwiki-source-4144580dff4473f29853b10a
    resource: repo://core/skin/src/test/java/cn/com/dcsgo/mihx/core/skin/SkinExpressivenessTest.kt
  - id: openwiki-source-452d07c1780ac74346af3f26
    resource: repo://core/skin/src/test/java/cn/com/dcsgo/mihx/core/skin/SkinParserValidationTest.kt
  - id: openwiki-source-c545006ce2a4ed14097a125f
    resource: repo://core/ui/src/test/java/cn/com/dcsgo/mihx/ui/components/SongEmotionSectionTest.kt
  - id: openwiki-source-0f1ea52b4adfffa8391b0e81
    resource: repo://data/build.gradle.kts
  - id: openwiki-source-12a1d6d0e5553c5cb1517529
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/local/entity/WordPreferenceEntity.kt
  - id: openwiki-source-17d3dc7f59fe7e3d0b195222
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/repository/WordPreferencesRepository.kt
  - id: openwiki-source-efd5a146b89d9b58600e9264
    resource: repo://data/src/test/java/cn/com/dcsgo/mihx/data/local/migration/LegacyJsonSnapshotParserTest.kt
  - id: openwiki-source-3a86bbe47450daa347044117
    resource: repo://data/src/test/java/cn/com/dcsgo/mihx/data/local/migration/MelodyDatabaseMigrationTest.kt
  - id: openwiki-source-f7013cd630cb7c02c18915a3
    resource: repo://data/src/test/java/cn/com/dcsgo/mihx/data/local/migration/SharedPreferencesLegacyJsonMigrationTest.kt
  - id: openwiki-source-d6a5dd7057c478d41355d6b9
    resource: repo://data/src/test/java/cn/com/dcsgo/mihx/data/repository/MusicRepositoryRoomTest.kt
  - id: openwiki-source-c0c66ec2e840e0a6915e7bfc
    resource: repo://data/src/test/java/cn/com/dcsgo/mihx/data/repository/PlayerSettingsRepositoryTest.kt
  - id: openwiki-source-0579d59281dd88200fb3ae19
    resource: repo://data/src/test/java/cn/com/dcsgo/mihx/data/repository/PlaylistResumeDataStoreTest.kt
  - id: openwiki-source-0400beb19fd463f37b3357bd
    resource: repo://data/src/test/java/cn/com/dcsgo/mihx/data/repository/PlayStatsRepositoryRoomTest.kt
  - id: openwiki-source-f373a5122289300027e96a77
    resource: repo://data/src/test/java/cn/com/dcsgo/mihx/data/repository/WordPreferencesRepositoryTest.kt
  - id: openwiki-source-29a9a46f0a09ec4b0f103e27
    resource: repo://data/src/test/java/cn/com/dcsgo/mihx/data/util/LrcParserOffsetTest.kt
  - id: openwiki-source-be4ba2c439dc1645bd326cb9
    resource: repo://docs/architecture/MOOD_TIME_SLOT_PLAYBACK.md
  - id: openwiki-source-517665524969e49ec1ee1552
    resource: repo://docs/refactor/PRODUCT_REFACTOR_AUDIT.md
  - id: openwiki-source-39979cc6e8c7e5b52ecabd5f
    resource: repo://domain/src/main/java/cn/com/dcsgo/mihx/domain/playback/ExplorePlaybackPolicy.kt
  - id: openwiki-source-ee8d5713acafafdce234f04d
    resource: repo://domain/src/main/java/cn/com/dcsgo/mihx/domain/playback/MoodSlotResolver.kt
  - id: openwiki-source-6ba6772083c7d0700d91cf6d
    resource: repo://domain/src/test/java/cn/com/dcsgo/mihx/domain/playback/ControllerPlaybackStateSynchronizerTest.kt
  - id: openwiki-source-b4bc1edf76b445fe973cfb36
    resource: repo://domain/src/test/java/cn/com/dcsgo/mihx/domain/playback/ControllerQueuePlannerTest.kt
  - id: openwiki-source-e7d94c70dbc924e5dc00e7fe
    resource: repo://domain/src/test/java/cn/com/dcsgo/mihx/domain/playback/ExplorePlaybackPolicyTest.kt
  - id: openwiki-source-f4cb724793cbd4ea152ee5a8
    resource: repo://domain/src/test/java/cn/com/dcsgo/mihx/domain/playback/MoodSlotResolverTest.kt
  - id: openwiki-source-ae79105f57f81d276c7f020d
    resource: repo://domain/src/test/java/cn/com/dcsgo/mihx/domain/playback/PlaybackRestoreCoordinatorTest.kt
  - id: openwiki-source-1a8af5eb0d99bd094670269e
    resource: repo://domain/src/test/java/cn/com/dcsgo/mihx/domain/playback/RandomQueuePlannerTest.kt
  - id: openwiki-source-0612dc00d9b1dcddcd4e8895
    resource: repo://domain/src/test/java/cn/com/dcsgo/mihx/domain/playback/UniformRandomPlannerTest.kt
  - id: openwiki-source-17a2bf34bb0967a68454eab1
    resource: repo://feature/player/src/test/java/cn/com/dcsgo/mihx/feature/player/NarrowFlowSyncTest.kt
  - id: openwiki-source-1065c62af23dd6e4c7057fdb
    resource: repo://feature/player/src/test/java/cn/com/dcsgo/mihx/feature/player/PlayerControllerQueueFacadeTest.kt
  - id: openwiki-source-4712a58a73a521c17f95b5f0
    resource: repo://feature/player/src/test/java/cn/com/dcsgo/mihx/feature/player/PlayerMediaEventFacadeTest.kt
  - id: openwiki-source-ca2d7e504c1ad19cbaff9046
    resource: repo://feature/player/src/test/java/cn/com/dcsgo/mihx/feature/player/PlayerPersistenceFacadeTest.kt
  - id: openwiki-source-6cbc32290e81c1e4b344fe2b
    resource: repo://feature/player/src/test/java/cn/com/dcsgo/mihx/feature/player/PlayerPersistenceGraphTest.kt
  - id: openwiki-source-847a1078428a6fbac760c85c
    resource: repo://feature/player/src/test/java/cn/com/dcsgo/mihx/feature/player/PlayerPlaybackStateAutosaverTest.kt
  - id: openwiki-source-b5318c575454b025c4e1b854
    resource: repo://feature/player/src/test/java/cn/com/dcsgo/mihx/feature/player/PlayerQueueFacadeTest.kt
  - id: openwiki-source-2406bafab2e742d99aa82d73
    resource: repo://feature/player/src/test/java/cn/com/dcsgo/mihx/feature/player/PlayerRandomQueueFacadeTest.kt
  - id: openwiki-source-0d78469d6fc56d18dbc3f026
    resource: repo://feature/player/src/test/java/cn/com/dcsgo/mihx/feature/player/PlayerSleepTimerCoordinatorTest.kt
  - id: openwiki-source-448ff49caeaad696d5651add
    resource: repo://feature/playlist/src/test/java/cn/com/dcsgo/mihx/feature/playlist/SongListTemplateTest.kt
  - id: openwiki-source-c8a858b5bdf7aa5c3003c0fb
    resource: repo://feature/playlist/src/test/java/cn/com/dcsgo/mihx/feature/playlist/SongSelectionControllerTest.kt
  - id: openwiki-source-edd43d204b6c984f231decbf
    resource: repo://feature/user/src/test/java/cn/com/dcsgo/mihx/feature/user/UserSectionsTest.kt
  - id: openwiki-source-d4431e382635a7a27ca87c72
    resource: repo://player/src/main/java/cn/com/dcsgo/mihx/player/PlaybackController.kt
  - id: openwiki-source-0b8d1ad8689331e8a8a22ee1
    resource: repo://player/src/main/java/cn/com/dcsgo/mihx/player/window/PlaybackWindowPlanner.kt
  - id: openwiki-source-76e85af0436f0961550204ec
    resource: repo://player/src/test/java/cn/com/dcsgo/mihx/player/ControllerQueuePlannerTest.kt
  - id: openwiki-source-1335b79015e69d8ed73b7fd8
    resource: repo://player/src/test/java/cn/com/dcsgo/mihx/player/MediaItemWrapDetectionTest.kt
  - id: openwiki-source-186a6bc804c7677b8913c8ce
    resource: repo://player/src/test/java/cn/com/dcsgo/mihx/player/PlaybackStateSnapshotSerializerTest.kt
  - id: openwiki-source-ba9ccc172d5e5f490a763733
    resource: repo://player/src/test/java/cn/com/dcsgo/mihx/player/PlaybackStateStoreTest.kt
  - id: openwiki-source-27adc205f45612b85a8b7022
    resource: repo://player/src/test/java/cn/com/dcsgo/mihx/player/SingleItemLoopRewindDetectionTest.kt
  - id: openwiki-source-83429aa1bcb1d70e11b69723
    resource: repo://player/src/test/java/cn/com/dcsgo/mihx/player/window/ControllerWindowSynchronizerTest.kt
  - id: openwiki-source-8a4ae96c838986d7eae43292
    resource: repo://player/src/test/java/cn/com/dcsgo/mihx/player/window/PlaybackWindowPerformanceShapeTest.kt
  - id: openwiki-source-9d7c39bdfe272c3574ef83bb
    resource: repo://player/src/test/java/cn/com/dcsgo/mihx/player/window/PlaybackWindowPlannerTest.kt
generated: { by: "openwiki/0.5.0", at: "2026-10-21T03:20:08.093Z" }
verified:
  - by: openwiki/0.5.0
    at: 2026-10-21T03:20:08.093Z
---

# Test Map: Behavior Locks and Verification Paths

This page answers two questions: **which invariant is locked by which test class**, and **which narrow verification to run before changing a subsystem**. The repository's tests are predominantly JVM unit tests (pure JUnit 4 + hand-written fakes, no mocking framework); every planner and resolver takes shuffle / `isPlayable` / clock as injected parameters, so the core business decisions are verified without a device. The pre-commit gate is aggregated by `check` (formatting + `verifyProductArchitecture` + all submodule unit tests); the full command matrix lives on `/openwiki/operations/build-and-verification.md`, and the behavior each test locks is elaborated on `/openwiki/player/random-and-infinite.md`, `/openwiki/player/queue-architecture.md`, and `/openwiki/architecture/data-persistence.md`.

## 1. Three-layer verification split

| Layer | Command | Device | Responsibility |
| --- | --- | --- | --- |
| **JVM unit tests** | `.\gradlew.bat :domain:test`, `:player:test`, `:data:test`, `:feature:player:test`, `:core:common:test`, `:core:skin:test`, `:feature:playlist:test`, `:feature:user:test`, `:app:test` (Windows; `./gradlew` on macOS/Linux) | Not needed | First gate for logic changes. Covers all planner/resolver/coordinator/facade orchestration/repository persistence semantics/snapshot serialization plus the skin-description, shell-equivalence, and playlist-template suites. **Almost every behavior regression should be caught here** |
| **Instrumented tests** | `.\gradlew.bat :app:connectedAndroidTest` | Needs device/emulator | Compose UI layer (`HomeScreenComposeTest` / `PlaylistScreenComposeTest` / `SettingsScreenComposeTest`), verifying empty-state, playback controls, copy, and contentDescription actually render |
| **Macrobenchmarks** | `.\gradlew.bat :benchmark:connectedCheck` | Needs device/emulator | Performance regression and Baseline Profile generation. `StartupBenchmark` (cold-start, 5 iterations) and `ScrollBenchmark` are **device-run macrobenchmarks, not JVM unit tests**; `BaselineProfileGenerator` writes `app/src/main/baselineProfiles/baseline-prof.txt`, which release builds bundle via `profileinstaller` |

```mermaid
flowchart TD
    CHANGE["code change"] --> KIND{"which subsystem?"}
    KIND -->|"planner and slot decisions"| R1[":domain:test with --tests class filter"]
    KIND -->|"facade orchestration"| R2[":feature:player:test narrow filter"]
    KIND -->|"controller window and queue"| R3[":player:test player.window package"]
    KIND -->|"persistence and data layer"| R4[":player:test or :data:test"]
    KIND -->|"logging trace permissions"| R5[":core:common:test or :app:test"]
    KIND -->|"skin shell template"| R6[":core:skin:test plus :app:test shell package"]
    R1 --> GREEN{"narrow all green?"}
    R2 --> GREEN
    R3 --> GREEN
    R4 --> GREEN
    R5 --> GREEN
    R6 --> GREEN
    GREEN -->|"before commit"| CHECK["gradlew check: spotless + verifyProductArchitecture + all submodule unit tests"]
    CHECK --> DEVICE["device needed: :app:connectedAndroidTest (Compose UI)"]
    CHECK --> BENCH["device needed: :benchmark:connectedCheck (performance and Baseline Profile)"]
```

*Three verification paths: JVM unit tests are the daily narrow gate, `check` is the full pre-commit gate, and the device layer only enters for UI and performance assertions.*

`check` aggregates the root-level `spotlessCheck`, the root-level `verifyProductArchitecture`, and every subproject's own `check` (each of which runs that subproject's unit tests) at the end of root `build.gradle.kts`; a failure can therefore be formatting, an architecture assertion, or a unit test — read the `GradleException` message first.

## 2. Regression baseline: random, mood-slot, and explore-play tests

The contextual shuffle (slot × mood words) design's §7 checklist item 7 is an explicit regression floor: **with the switch off, shuffle behavior must be bit-identical to the pre-enhancement build, and all existing `RandomQueuePlanner` tests must stay untouched and green**. This means:

- Every assertion in `RandomQueuePlannerTest` and `UniformRandomPlannerTest` defines "current behavior", not refactorable implementation detail; the enhancement may only occur at the candidate-pool entry (`PlayerRandomQueueFacade`-side filtering), never change the planner's filtering/layering/elimination semantics.
- The non-mood-prefixed cases of `PlayerRandomQueueFacadeTest` (e.g. `playRandomQueueStartsSequentialQueueAndLeavesInfiniteMode`) carry the same "switch off = current behavior" regression duty; `moodFilterDisabledKeepsOriginalBehavior` and `moodFilterInactiveOutsideSlotTime` directly assert "switch off / slot time missed ⇒ behavior identical to current".
- A PR that edits these three test files (or turns them red) is by default a behavior change and must declare it explicitly in the PR description.

The v3.11.0 explore-play work (2026-10-20) adds a second, newer regression family beside mood slots inside the same facade: random-pool filtering now also consults **word-listening preferences**, so `PlayerRandomQueueFacadeTest`'s explore group and the `PlayerMediaEventFacadeTest` explore group are equally baseline-locking (they exercise the exploration start/refill/settle chain). Explore decisions are split into a pure `:domain` policy (`ExplorePlaybackPolicy`) and facade-side pool selection, keeping them JVM-testable without a device.

## 3. Test anchors grouped by subsystem

### 3.1 Random (`RandomQueuePlanner` / `UniformRandomPlanner`)

| Test class | Locked behavior |
| --- | --- |
| `domain/.../RandomQueuePlannerTest` | Random-queue planning semantics: `recentSongIds` elimination (eliminated songs never enter a batch, picked songs join the recent set); when candidates are exhausted the history is cleared and re-drawn with `resetHistory = true`; with uniform random on, low-play-count songs are still preferred after recent-filtering; `planInfiniteStart` only records **queue-playable** songs into the coverage set; `planInfiniteRefill` excludes songs already in queue and keeps the existing exclusion set; returns `null` when no song is playable |
| `domain/.../UniformRandomPlannerTest` | Layered preemption: switch off degrades to the injected shuffle; on, layers are built dynamically from raw play counts with the low layer preferred, low layer never exceeded when it has enough slots, and the high layer filling shortfall only; extra-tier layering stays effective even when all counts exceed any fixed threshold (the threshold comes from the distribution, not a constant); equal counts degrade to pure random; `orderSongs` groups by count and shuffles within a group; `buildPlayOrderIds` puts the current song first in `SHUFFLE` mode and layers the rest |

Uniform-random compatibility is a design requirement: the emotion-filtered candidate pool feeds straight into `selectSongs` with zero changes to the layering logic, and the above tests are the proof of that compatibility.

### 3.2 Mood slots (`MoodSlotResolver` / facade filtering and degradation)

| Test class | Locked behavior |
| --- | --- |
| `domain/.../MoodSlotResolverTest` | Full-corner slot decisions: normal ranges are left-closed/right-open (start on-the-hour hits, end on-the-hour does not); across-midnight (`end <= start`, 22:00–06:00 hits at 23:41/00:00/05:59, not at 06:00 or 21:59); a legal config hits at most one slot in an all-day per-minute scan, illegal multi-hit data deterministically picks the earliest start; switch off / empty config never applies; validation error taxonomy (empty name, overlong name `NAME_MAX_LENGTH=20`, no words, zero length, overlapping an existing slot returns `Conflict` naming the conflicting party, abutting endpoints are not overlaps, same-id edit is not self-conflict); `overlaps` circular-timeline semantics (across-midnight × morning intersects, two across-midnight slots always intersect, an all-day slot intersects any non-zero slot) |
| `feature/player/.../PlayerRandomQueueFacadeTest` (mood group) | Facade-side filtering and degradation: when a slot hits, the random pool narrows to word-tagged songs; a 0-song word combination **falls back to full-library random** and logs "fall back to full library" (never fails to start on an empty pool); when the slot is missed or the switch is off the behavior is identical to current; stacked with uniform random, filtering still goes through layered preemption (a 0-count song ranks before a 10-count one) |

Slot decisions and validation are pure `:domain` functions (`MoodSlotResolver` + `MoodSlotPolicy` constants); the config-edit UI and persistence share the same `validate`/`overlaps` — the tests are the contract, and the UI must not write a second overlap implementation.

### 3.2b Explore playback and word-listening preferences (v3.11.0, 2026-10-20)

The explore-play feature replaces plain lucky-random with a **10-song initial batch** filtered by mood emotion tags and per-word listening votes, an automatic **5-song refill** once the just-ended song is second-to-last, and a **settle step** that votes each word of a finished song up or down based on `ExplorePlaybackPolicy`. Local singling out to factory-pure pieces keeps all of it JVM-testable.

| Test class | Locked behavior |
| --- | --- |
| `domain/.../ExplorePlaybackPolicyTest` + `domain/.../ExplorePlaybackPolicy` | Pure skip-vs-complete judgment `isSkipped(playedMs, durationMs)`: skipped when played **< 30s OR < 1/4 of duration** — a 4-min song played 20s is skipped, an 8-min song played 60s (>30s but <1/4=120s) is skipped, a 4-min song played 70s (>30s and >1/4=60s) is a full listen; a short song fully played under 30s is still counted skipped (`MIN_LISTEN_MS=30_000`); duration `<=0` (unknown) falls back to the 30s rule only. Exposes the explore constants `INITIAL_BATCH_SIZE=10` and `REFILL_COUNT=5` |
| `feature/player/.../PlayerRandomQueueFacadeTest` (explore group) | Facade-side explore start and refill: `playRandomQueue` under explore mode produces **exactly 10 songs**, sets `isExplorePlay`, exits infinite mode (`isInfinitePlay=false`, cleared `infinitePlayedSongIds`), and logs `explore=true`; an active explore session **blocks `startInfinitePlay`** (logs "blocked by explore session") while a session that ended (user manually replaced the queue, `isExplorePlay=false` but mode still on) allows infinite play again. `refillExplorePlayQueue` prefers songs carrying **liked words and no disliked words**; if none exist it falls back to the "no disliked words only" pool (logs "no liked match"), and if every song carries a disliked word it falls back to the full library (logs "fall back to full library"); refill does nothing when not in an explore session; the regenerate regression (2026-10-20) uses `startedSongId` to correct the business-queue `currentIndex` so a stale index never syncs back to replay the just-ended song |
| `feature/player/.../PlayerRandomQueueFacadeTest` (word-filter precedence) | Detailed vote-driven candidate narrowing: a song tagged with a disliked word is excluded, a song with **no liked word** is excluded from the top "liked" pool but can enter the no-dislike fallback pool, `NEUTRAL` (tie) words act as neither liked nor disliked, and the filtered pool is bounded by the refill cap (5) |
| `feature/player/.../PlayerMediaEventFacadeTest` (explore group) | Explore tail-handling on `handleMediaItemEnded`: when the just-ended song was **second-to-last** (new `remainingItems == 0` after the ended song becomes current) it settles the ended song (calls `onExploreSongSettled(songId, playedMs)` with the tracked song and its played position) **and** triggers `refillExplorePlayQueue`; when the ended song was third-to-last (`remainingItems == 1`) or the queue still has enough songs, no refill happens (avoids re-pulling to replay); a `null` tracked song skips the settle (but still refills the queue) |
| `data/.../WordPreferencesRepositoryTest` + `data/.../WordPreferencesRepository` | Vote counting over the Room `word_preferences` table: `recordSongOutcome(words, liked=true)` increments each word's `likeVotes` and `recordSongOutcome(false)` its `dislikeVotes`; votes accumulate across sessions without flipping a word's ownership on a single skip (`3` like vs `2` dislike stays `LIKE`, a new word reaching `0` like vs `1` dislike is `DISLIKE`); a tie (`1`/`1`) is `NEUTRAL`; empty word lists are ignored; `clearAll` empties the table and the in-memory map |
| `:feature:player` `PlayerQueueFacadeTest` (explore session lifecycle) | `setPlayQueue` (a user manual queue replacement — playlist/top-hits tap) **exits** the explore session (`isExplorePlay=false`, `isInfinitePlay=false`); merely calling `addToPlayQueue` (append) keeps the explore session alive |
| `:feature:player` `PlayerPersistenceFacadeTest` + `PlayerPersistenceGraphTest` + `PlaybackStateStoreTest` (explore persistence) | The `isExplorePlay` flag and explore mode traverse save/restore so a killed process resumes into the same explore state: persistence models carry `isExplorePlay` through snapshot save/restore in the same-named facade/graph tests, and the snapshot store's live-session and empty-session guards apply equally to an explore queue (an empty save with only `currentSongId` must not wipe a restored explore queue) |

The `WordPreferenceKind` ownership rule (`like > dislike → LIKE`, `dislike > like → DISLIKE`, tie → `NEUTRAL`) lives in `core/model` `WordPreference.kind`; the `WordPreferencesRepository` is deliberately vote-only and never computes `kind` itself, so single-skip outcomes cannot accidentally flip a thoroughly-liked word (the A/B-in-like, C/D-in-dislike conflict case documented on the model).

### 3.3 Queue and window (the domain and player planners, synchronizer, performance shape, wrap detection)

| Test class | Locked behavior |
| --- | --- |
| `domain/.../ControllerQueuePlannerTest` | Playable-order expansion: empty queue returns `null`; `SEQUENTIAL` keeps order with `startIndex` pointing at the requested song; `REVERSE` reverses; `SHUFFLE` expands stably by `playOrderIds`; when the requested song is unplayable it jumps to the next playable item |
| `player/.../ControllerQueuePlannerTest` (`:player`-side same-name class) | Controller planning semantics: `remainingAfterStart` tail-batch detection; **duplicate songs fully preserved** (`1,2,2,3` is not deduplicated, and a duplicate id's startIndex resolves to the requested occurrence); after "add as next", controller order is `1,2,5,3`; a play-mode change rebuilds controller order; all-unplayable returns `null` |
| `player/window/PlaybackWindowPlannerTest` | Window slicing: `DEFAULT_PREVIOUS_COUNT=20` / `DEFAULT_NEXT_COUNT=50` clamped at queue boundaries; window-index ↔ full-queue-index mapping (`controllerStartIndex` / `fullQueueStartIndex`); in `REVERSE` mode the window follows reverse-play order |
| `player/window/ControllerWindowSynchronizerTest` | Window caching: a non-forced plan reuses the window but re-anchors `startIndex` to the current song; a forced plan (like after `addSongAsNext`) invalidates the cache and recomputes; empty queue returns `null`; stable shuffle / reverse-play order enters the window |
| `player/window/PlaybackWindowPerformanceShapeTest` | Product-level performance shape: for 100 / 500 / 1000-song queues the controller window always stays **≤ 71 and ≥ 51 items** (checked at queue head/middle/tail, including that a 1000-song stable shuffle window does not balloon to the whole queue). **`verifyProductArchitecture` locks this file's existence and keywords** (`100`, `500`, `1_000`, `71`, `WindowedControllerQueuePlanner`), so deleting the file or renaming keywords fails the architecture gate |
| `player/.../MediaItemWrapDetectionTest` | Wrap decisions: window tail (index 70) → 0 counts as wrap; normal forward, one `previous` step, first switch (`C.INDEX_UNSET`), and a new non-zero index do not; **a backward step in a 2-song small window counts as wrap** (harmless: the refill planner dedups); empty/single-item windows cannot wrap. The decision must use indices, not the transition reason, because Media3 reports tail wraps under `REPEAT_MODE_ALL` as `SEEK` |
| `player/.../SingleItemLoopRewindDetectionTest` | Single-item loop wrap (0→0 under `REPEAT_MODE_ALL` fires no `onMediaItemTransition`, so it is recognized only via AUTO discontinuity + position rewind-back): with known duration, "old position near the end" decides precisely; unknown duration falls back to a conservative threshold (old position < 30s treated as buffering jitter, not completion); a `SEEK` reason (manual drag to start) must not count as finished (anti-gaming); multi-song queues, index changes, and a non-zero new position never count; threshold boundary values still decide |

### 3.4 State machine and persistence (state sync, restore coordination, snapshot store and serialization, same-name facade tests)

| Test class | Locked behavior |
| --- | --- |
| `domain/.../ControllerPlaybackStateSynchronizerTest` | Media3 snapshot → UI state mapping: `mediaId` (`= Song.id.toString()`) maps back to the business queue and updates `currentIndex`, producing a duration update and a playback-start event; an unknown `mediaId` leaves the current song alone; a tracked song does not emit playback-start twice; a buffering pause is not a real pause (`isPlayingTransition` three-state); `QueueManager.restorePlayModeAfterNextSong` restores the pre-next-song play mode only for the relevant song, and the play-order builder is injectable |
| `domain/.../PlaybackRestoreCoordinatorTest` | Startup restore: storage with no state → `null`; the current song missing or unplayable → restored queue but no playable session; the happy path yields a playable session carrying the restore position and the same-name song group (sorted by sample rate descending); **the infinite-play flag and coverage set traverse restore untouched** |
| `player/.../PlaybackStateStoreTest` | DataStore snapshot store: save/restore round-trips keep queue, `currentIndex`, mode, `playOrderIds`, duplicate items, and infinite-play state; restore filters songs that no longer exist; `saveCurrentPlaybackSnapshot` corrects index/position from the live current song and can even create a queue for a song that was not in the saved queue; **an empty-session save (transient all-empty state during UI rebuild) must not erase an existing snapshot** (2026-09-03 regression, named in the test); an empty save with no prior snapshot writes nothing; an empty save carrying only a `currentSongId` keeps the existing queue but updates position (2026-10-06 live-session regression), or restores a single-song queue when no queue existed; restore falls back to legacy SharedPreferences and the next successful save clears the legacy keys |
| `player/.../PlaybackStateSnapshotSerializerTest` | Snapshot JSON robustness: round-trip preserves duplicates/index/mode/play order; corrupt JSON and a missing required `currentIndex` decode to `null` (no exception); absent optional fields take defaults; malformed/unusable entries in `infinitePlayedIds` are silently dropped |
| `:feature:player` same-name facade tests | 18 facades, each with a same-named JVM test that records side effects via a fake callable object to verify pure-planner orchestration above. Representative anchors: `PlayerQueueFacadeTest` (setPlayQueue updates state and triggers persistence); `PlayerControllerQueueFacadeTest` (`remainingMediaItems` prefers controller info, falls back to the planned queue when there are no items); `PlayerMediaEventFacadeTest` (tail/wrap triggers refill, non-infinite mode does not, and "add as next" restores play mode and consumes a one-shot marker); `PlayerPersistenceFacadeTest` (captures the current position **before** the async save; live-session restore skips the controller but restores the UI queue); `PlayerPlaybackStateAutosaverTest` (save throttling: one immediate save then throttled by interval, `reset` releases the next save) |
| `feature/player/.../PlayerPersistenceFacadeTest` + `PlayerPersistenceGraphTest` | Persistence handshake regression: the `controllerReady` and `pendingRestore` flags converge in either order; a live session only refills the UI queue while an empty session fully restores; a connection failure fallback releases; no snapshot means no restore. This logic is the site of four past regressions (4fdb2ae/0cce0de/53af2cc/2026-10-06); the graph test uses `Dispatchers.Unconfined` so its internal `launch(io)/withContext(main)` runs synchronously without a coroutines-test dependency |
| `feature/player/.../NarrowFlowSyncTest` + `PlayerSleepTimerCoordinatorTest` | Narrow-flow regression baselines (2026-09-03 progress-bar rewind fix): discrete `uiState` updates must not write stale position/reset countdown back into the narrow flow (the comparison baseline must be the pre-update `uiState` old value); real position changes (seek), the restore path, and timer start must propagate to the narrow flow; a cancel reset must go through the explicit reset entry `resetSleepTimerNarrowFlow` / `resetRemainingMs` rather than relying on a `uiState` value diff |

### 3.5 Data layer (Room repositories, settings repository, DB migration, legacy migration, lyrics parsing)

The data-layer unit tests **do not start Android**: Room-repository tests run against a hand-written in-memory `FakeMelodyDao` per test file, and settings/snapshot tests run a real DataStore via `PreferenceDataStoreFactory` + a temp file; `:data` explicitly pulls in the real `org.json` test dependency (`testImplementation(libs.org.json)`) because the Android SDK's org.json is a stub that throws on the JVM. The **schema migration** tests, by contrast, are Robolectric-instrumented (they need Room's `MigrationTestHelper` + the exported `data/schemas/` JSON) and run under `sdk = [34]`.

| Test class | Locked behavior |
| --- | --- |
| `data/local/migration/MelodyDatabaseMigrationTest` | **Hand-written migration regression (m-4, 2026-10-15)** for v1→v10, now extended to v10→v11. `MigrationTestHelper.runMigrationsAndValidate` walks every migration step and validates the post-migration schema against the target-version exported schema JSON (columns/indexes/PRIMARY KEY all aligned), so any hand-written SQL drifting from the `@Entity` definitions fails. Data-integrity assertions insert old-shape rows before a step and check survival/new-column defaults after: v1→10 keeps inserted songs and fills per-version defaults (`album NOT NULL DEFAULT ''` from 3→4, `durationMs NOT NULL DEFAULT 0` from 6→7, nullable `lrcUri` stays NULL from 2→3); 1→2 creates `quick_skip_short_play_counts`; 4→5 creates the `artists`/`albums`/`song_artist_cross_ref` catalog; 5→6 creates `playback_events`; 8→9 creates `song_emotions` and 9→10 adds the four personalization columns (`embeddingB64`, `userValence`, `userArousal`, `userTags`) with all-NULL "not calibrated" defaults; **10→11 creates the `word_preferences` table (word `PRIMARY KEY`, `likeVotes`, `dislikeVotes`, `updatedAt`)** and an inserted row round-trips |
| `data/repository/WordPreferencesRepositoryTest` | Vote accumulation and neutrality over a `FakeMelodyDao` in-memory `word_preferences` table (`observeAllWordPreferences`/`allWordPreferences`/`upsertWordPreferences`/`clearWordPreferences`): see §3.2b — skip adds a dislike vote without flipping an already-liked word, full listen adds a like vote, ties are `NEUTRAL`, empty word lists are ignored, and `clearAll` empties both the map and DAO |
| `data/repository/MusicRepositoryRoomTest` | Library repository: startup rebuilds playlist cross-references from Room (by `sortOrder`) and filters orphan references; adding a song to a playlist persists the cross-reference and rejects duplicates; local file verification is fully preserved without a context; `titleOverride` group-override write/clear round-trips and drives `groupKey` on restore |
| `data/repository/PlayStatsRepositoryRoomTest` | Play stats: `increment` / `incrementRawPlayCount` / `updatePlayDuration` persist incrementally and stamp `lastPlayedAt`; batch reads default missing songs to 0; `getRankedCounts` sorts deterministically (tie-break by id ascending) and `useRawCounts` switches the metric |
| `data/repository/QuickSkipSongsRepositoryRoomTest` + `PlaylistResumeDataStoreTest` | Quick-skip member decisions/dedup/short-play-count reset; playlist-resume DataStore record/overwrite/single-song single-clear semantics |
| `data/repository/PlayerSettingsRepositoryTest` | Settings persistence: defaults when nothing is stored (theme `SYSTEM`, variant `MONO`, lyric font scale 1, global uniform random on, Bluetooth monitoring off, playback notification off, daily listening goal 120 minutes); per-key legacy SharedPreferences fallback happens exactly once — after the first DataStore write the legacy key is deleted (separate assertions for the dark-theme, global-uniform-random, Bluetooth-monitoring, and notification paths); non-legacy keys like `lyricFontScale` and `dailyListeningGoalMinutes` persist directly to DataStore. **v3.11.0 explore-mode settings** lock the `RandomEnhanceMode` tri-state (`OFF`/`TIME_SLOT`/`EXPLORE`): nothing stored defaults to `OFF`; a legacy `mood_time_slot_enabled=true` without a new key falls back to `TIME_SLOT`; `setRandomEnhanceMode(EXPLORE)` writes `RANDOM_ENHANCE_MODE=EXPLORE` and **syncs the legacy switch to false** (explore is not a time-slot mode), while `TIME_SLOT` syncs the legacy switch to true — both read paths agree. (The `themeVariant` and `screenOrientationMode` settings exist on `PlayerSettingsRepository`, but only `themeVariant` is exercised here; orientation is not covered by this file's current cases.) |
| `data/local/migration/LegacyJsonSnapshotParserTest` | v1 JSON snapshot parsing: song/playlist/group-override/play-stat prefix keys (`play_count_*`, `raw_play_count_*`, `play_duration_*`)/quick-skip songs and short-play counts; optional-field absence takes Chinese defaults; corrupt JSON recovers per-segment instead of failing wholesale; duplicate URIs dedup while URI-less songs are kept; playlist references point only at migrated songs and `sortOrder` is re-ordered |
| `data/local/migration/SharedPreferencesLegacyJsonMigrationTest` | One-shot migration orchestration: all data writes to `FakeMelodyDao` and writes the `MigrationStateEntity` completion marker; corrupt JSON still migrates stats and still writes the marker (no retry); **an existing completion marker means a full skip** (zero writes) |
| `data/util/LrcParserOffsetTest` | LRC `[offset:]` tag regression (2026-09-03 lyrics-live optimization, previously the offset was dropped): a positive value advances everything (`timeMs += offset`), a negative delays it, a missing/invalid value keeps the original timestamp, it applies to every timestamp of a multi-timestamp line, and blank content returns `null` |

### 3.6 `:core:skin` description lock (v3.10.2)

The skin-description suite locks the "description-driven shell == the current app structure, and the contract can express radically different shells" invariant. Several of these tests deliberately read source files rather than self-proving against the description, because the P1 lesson was that a self-proving test can lock in a wrong description (the library was once asserted to have 5 segments including a spurious 「歌曲」 that only exists on the artist/album detail pages).

| Test class | Locked behavior |
| --- | --- |
| `core/skin/.../DefaultSkinTest` | The built-in skin JSON == today's app structure: 3 bottom tabs (曲库/播放/我的) matching `AppDestinations`; player is a normal tab, not a sheet; library page has exactly 4 segments (`LibraryTab`: 歌单/歌手/专辑/情绪) with a default of 歌单 and a search entry; every library segment has sections so none renders blank; the me page is fixed to one screen; surface token is `0` ("zero solid blocks" style); art size is `44dp` matching the delivered `VisTokens`; `startPage` is the player page because the app launches there. This is the P1 groundwork locked before P2 |
| `core/skin/.../SkinParserValidationTest` | Fail-closed validation with precise reasons: every bad input must give a typed `SkinIssue.Code` plus the offending field — rejects non-JSON (`MALFORMED_JSON`), missing schema version, unsupported schema version (states which version is supported, currently `1`), **unknown part is rejected and fails closed** (never silently degraded; even a real androidx widget like `AndroidView` not in the catalog is rejected), unknown icon, tab targeting a missing page, duplicate tab ids, a single-entry bottom bar, missing start page, invalid player entry, invalid cover shape, invalid row template, invalid song-list source, negative song count, and a segment without sections so the page cannot render blank |
| `core/skin/.../SkinExpressivenessTest` | The contract can express shells radically different from current: a NetEase-style skin (3 tabs 我的/发现/正在播放, card wall with surface=1, vinyl player page) and a minimal two-page skin (2 tabs + player as a global sheet with a persistent mini bar). Also locks the **scope boundary at L5/L6**: all three skins use only L1–L5 `SkinPartCatalog` parts and need no L6 presentation parts, and the catalog itself contains no L6/L7 entries. `songList` with `template: grid` proves one part renders differently per template |
| `core/skin/.../NeteaseSkinJsonTest` | The shipped NetEase user-skin JSON (`dcsgo.skin.netease.json` under `/tmp/skinpack/`) parses cleanly through `SkinParser` (no bad-prop/bad-page/bad-section), and its parsed fields match design intent: `playerEntry = sheet`, 3 tabs labeled 我的/发现/云村, `surface = 1` (card wall, complementary to mihx default 0), and grid template on the artist/album song-list segments |
| `core/skin/.../LibrarySegmentsMatchSourceTest` | Metadata-level lock: the description's library segments must match the `LibraryTab` enum **exactly** (including order) by parsing `feature/playlist`'s `PlaylistComponents.kt` directly; the `SkinPartCatalog.LIBRARY_SEGMENTS` whitelist must equal the `LibraryTab` labels; the source really has 4 segments and no 「歌曲」; and every described segment has `sectionsBySegment` defined. This catches the self-proving-test bug class the P4 correction exposed |

### 3.7 `:app` shell and navigation (built-in skeleton/template/section catalogs locked against the app structure)

These live in `:app` because cross-module consistency — `:core:skin` vs a feature module — cannot be tested in either module (architecture gates forbid core→feature back-deps, and a feature cannot see `:core:skin`); `:app` sees both and acts as the assembly layer.

| Test class | Locked behavior |
| --- | --- |
| `app/shell/AppShellEquivalenceTest` | Runtime-shell equivalence with the pre-refactor app: the built-in `DefaultShell.shell` keeps the three original tabs in the original order (曲库/播放/我的) mapped to `AppRoutes.PLAYLIST/HOME/USER`; tab ids are stable and unique (a duplicate id caused a dirty-tab rendering-key collision in the prototype); the start route is `AppRoutes.HOME` matching `AppNavHost`'s original `startDestination`; the mini player shows on the library page when a song is loaded and hides on the player page itself. Each assertion names the pre-refactor source it cross-checks |
| `app/shell/DefaultShellConsistencyTest` | The two shells must agree: `DefaultShell.shell` (the startup Kotlin constants) and `SkinShellResolver.resolve(DefaultSkin.JSON)` (the description-driven path user skins will take) produce the same tabs, start route, mini-player, and player-entry settings; the built-in JSON still parses cleanly with zero warnings; and the start route is the player page, not the library (locks the P1 fidelity bug where `startPage` was wrongly written as library) |
| `app/shell/SheetSkeletonTest` | The drawer-type skeleton is actually expressible and assemblable: the minimal sheet sample skin (2 tabs 曲库/我的, no player tab, `playerEntry = SHEET`, mini player as the drawer handle, start on the library page) passes validation with zero warnings — proving the description layer and shell layer genuinely support the user-requested "2 pages + global drawer" shape, not just an interface field |
| `app/shell/LibrarySongListTemplateTest` | L3 template resolution: the default skeleton resolves to `SongListTemplate.DEFAULT` (zero visual change vs pre-refactor), the grid sample resolves to `SongListTemplate.GRID` (for device A/B acceptance), a skeleton with no songList part falls back to `DEFAULT`, and an unknown template value is rejected by the validator (fail-closed: a template is not silently ignored at runtime but strictly rejected at import) |
| `app/shell/GridLayoutSwitchTest` | The global "artist/album grid layout" switch → shell mapping: switch on forces `GRID` regardless of skin across every `SkinShellResolver.knownSkins`; switch off keeps the skin's own template (must not overwrite the style's `songList.template`); and both builtin skins are list rows by default when the switch is off. Guards against the switch being mistaken for "per-style keyed" and against closing the switch overwriting the style's own parsed template |
| `app/shell/LuckyPlayEntryPolicyTest` | The 「随心播放」 entry-bar display policy `shouldShowLuckyPlayEntry`: shows on the sheet shell when no song is drawn, never shows while a song is playing (and is mutually exclusive with `shouldShowMiniPlayer`), and never shows on the tab shell (default three pages) because the player page already has the FAB. Guards against an inverted condition, a missing `SHEET` limitation (default three pages showing it would duplicate the FAB), and non-mutual-exclusion with the mini player |
| `app/shell/SongListTemplateCatalogTest` | L3 whitelist ↔ enum consistency: `SkinPartCatalog.SONG_LIST_TEMPLATES` must match the `SongListTemplate` enum's ids exactly (adding a template requires changing both sides), the whitelist contains `default` and `grid`, and has no duplicates. Prevents the "description legal but assembly breaks" / "assembly supports but validation rejects" mismatch between the `:core:skin` whitelist and the `:feature:playlist` enum |
| `app/shell/SkinSwitcherCatalogConsistencyTest` | Cross-module section key: `UserSections.SKIN_SWITCHER` must equal `SkinPartCatalog.SKIN_SWITCHER` literally, so the user-page skin-switcher key and the description part catalog cannot drift apart (the same trap class as the L3 template-whitelist drift) |

### 3.8 Playlist template and selection controller (`:feature:playlist`)

| Test class | Locked behavior |
| --- | --- |
| `feature/playlist/.../SongListTemplateTest` | The L3 row-template enum contracts: `DEFAULT`/`GRID` ids equal the catalog whitelist strings (`default`/`grid`); `fromId` returns `DEFAULT` for unknown values in a fail-safe manner (null, empty, unknown, and case-sensitive `"GRID"` all degrade to `DEFAULT`); `fromId("grid")` returns `GRID`; all template ids are distinct and non-blank. Pure Kotlin, no Android/Compose dependency |
| `feature/playlist/.../SongSelectionControllerTest` | Song-selection controller state logic: `filterSongs` returns all songs on a blank query and matches title or artist case-insensitively otherwise; `toggleSearch` closes and clears the query; `enterSelectMode`/`exitSelectMode` manage the multi-select selection set (toggle add/remove, exit clears selection) |

### 3.9 Permissions and common (`core:common`, `core:ui`, `core:model`, `app`)

| Test class | Locked behavior |
| --- | --- |
| `app/.../RuntimePermissionPolicyTest` | Version gates: the notification permission returns the `POST_NOTIFICATIONS` spec only on API 33+ (`TIRAMISU`); `BLUETOOTH_CONNECT` only on API 31+ (`S`); lower versions return `null` (each with its Chinese rejection copy) |
| `core/common/.../AppLoggerTest` | Log redaction: `content://`, `file://` URIs, POSIX/Windows paths, Bluetooth addresses, and device names/`bluetoothName` fields replaced with placeholders; a `Throwable`'s message and stack are redacted too. This is the behavioral backing for rule 14 (no direct `android.util.Log`) |
| `core/common/.../PerformanceTraceTest` | Tracing: `measure` passes through the block's return value; `log` writes via `AppLog` with metadata redacted first (`uri=content://<redacted>`); release silence is controlled by `isEnabled`, and `allow`/`disallow` whitelisting keeps critical operations logging in production |
| `core/ui/.../SongEmotionSectionTest` | Emotion pure-function layer: `smoothCurve` preserves length and noise-reduces, short lists pass through; the `lowConfidence` indeterminate band (\|value\| < 0.15 on either axis); `hasSignificantPeak` significance; **vocabulary closure** (10 groups, 39 distinct words, the ABYSS group has 3, headline is the group's first word); per-window voting with near-zero abstention, taking the top `MAX_TAGS` groups by proportion; the WITTY group is excluded from automatic voting (V-A-center vacuum fix) but the manual-tag `groupOf` chain is unaffected |

`core/model`'s `LyricsHighlightLeadTest` (the lyric-line highlight lead `HIGHLIGHT_LEAD_MS` pre-scroll compensation) and `app`'s `ResolveResumeSongTest` (playlist-resume song resolution: playability/existence/empty-resume `null` branches) belong to the same "behavior is the contract" family of small regression locks. `feature/user`'s `UserSectionsTest` locks the me-page section keys against history and the fallback order (5 hardcoded items then an optional `skinSwitcher`).

## 4. Narrow verification command examples

Principle (AGENTS.md): **prefer the narrowest quiet verification** — change one module, run that module's tests, and use `--tests` class filters to converge on the specific behavior; leave the full `check` for pre-commit. **Preserve failure output in full** (do not truncate, do not paste only a summary): `verifyProductArchitecture` failures name the offending file, and unit-test failures give the full assertion diff.

```bash
# ① changing a planner / slot decision (the random-and-mood-slot regression floor)
.\gradlew.bat :domain:test --tests "cn.com.dcsgo.mihx.domain.playback.RandomQueuePlannerTest" --tests "cn.com.dcsgo.mihx.domain.playback.UniformRandomPlannerTest" --tests "cn.com.dcsgo.mihx.domain.playback.MoodSlotResolverTest"

# ② changing facade orchestration (incl. mood filter/degrade, explore start/refill, infinite-play refill)
.\gradlew.bat :feature:player:test --tests "cn.com.dcsgo.mihx.feature.player.PlayerRandomQueueFacadeTest"
.\gradlew.bat :feature:player:test --tests "cn.com.dcsgo.mihx.feature.player.PlayerMediaEventFacadeTest" --tests "cn.com.dcsgo.mihx.feature.player.PlayerQueueFacadeTest"
.\gradlew.bat :feature:player:test --tests "cn.com.dcsgo.mihx.feature.player.PlayerPersistenceGraphTest" --tests "cn.com.dcsgo.mihx.feature.player.PlayerSleepTimerCoordinatorTest"

# ②b changing explore-skip policy / word-listening votes / explore-mode settings
.\gradlew.bat :domain:test --tests "cn.com.dcsgo.mihx.domain.playback.ExplorePlaybackPolicyTest"
.\gradlew.bat :data:test --tests "cn.com.dcsgo.mihx.data.repository.WordPreferencesRepositoryTest"
.\gradlew.bat :data:test --tests "cn.com.dcsgo.mihx.data.repository.PlayerSettingsRepositoryTest"

# ③ changing the controller window / sync
.\gradlew.bat :player:test --tests "cn.com.dcsgo.mihx.player.window.*"
.\gradlew.bat :domain:test --tests "cn.com.dcsgo.mihx.domain.playback.ControllerQueuePlannerTest"

# ④ changing playback-state persistence (snapshot store, serializer, restore)
.\gradlew.bat :player:test --tests "cn.com.dcsgo.mihx.data.player.PlaybackStateStoreTest" --tests "cn.com.dcsgo.mihx.data.player.PlaybackStateSnapshotSerializerTest"
.\gradlew.bat :domain:test --tests "cn.com.dcsgo.mihx.domain.playback.PlaybackRestoreCoordinatorTest"

# ⑤ changing the data layer / migration
.\gradlew.bat :data:test
# DB schema migration (Robolectric + MigrationTestHelper; needs the data/schemas export)
.\gradlew.bat :data:test --tests "cn.com.dcsgo.mihx.data.local.migration.MelodyDatabaseMigrationTest"

# ⑥ changing logging / tracing / permissions
.\gradlew.bat :core:common:test
.\gradlew.bat :app:test

# ⑦ changing the skin description / shell / template catalogs (v3.10.2)
.\gradlew.bat :core:skin:test
.\gradlew.bat :app:test --tests "cn.com.dcsgo.mihx.app.shell.*"
.\gradlew.bat :feature:playlist:test --tests "cn.com.dcsgo.mihx.feature.playlist.*"

# ⑧ pre-commit full gate (formatting + architecture assertion + all module unit tests)
.\gradlew.bat check

# ⑨ device layer (needs real device/emulator; UI assertions and performance regressions)
.\gradlew.bat :app:connectedAndroidTest
.\gradlew.bat :benchmark:connectedCheck
```

Operational notes:

- One Gradle invocation accepts several `--tests` filters; the wildcard `--tests "cn.com.dcsgo.mihx.player.window.*"` covers a whole package.
- When changing `MusicRepository` import/scans or a `PlaybackController` queue path, besides unit tests confirm the `PerformanceTrace` anchors were not renamed (e.g. `music_import_scan`) — `verifyProductArchitecture` fails directly on that.
- After changing the startup path or home-list implementation, run `:benchmark:connectedCheck` once to regenerate the Baseline Profile; macrobenchmarks on MIUI device hardware can fail to complete auto-authorization/frame confirmation because of ROM limits.
- For fast compile feedback (no tests) use a single-module compile task like `.\gradlew.bat :player:compileDebugKotlin`.

## Related pages

- `/openwiki/operations/build-and-verification.md` — command matrix, every `verifyProductArchitecture` rule, benchmark and Baseline Profile operations
- `/openwiki/architecture/app-shell-navigation.md` — the shell/navigation structure the `:app` shell-equivalence tests verify
- `/openwiki/architecture/data-persistence.md` — Room/DataStore persistence and migration history (the §3.5 anchor background)
- `/openwiki/architecture/module-graph.md` — module boundaries that force the cross-module consistency tests into `:app`
- `/openwiki/player/random-and-infinite.md` — full derivation of the locked random-chain behavior
- `/openwiki/player/queue-architecture.md` — dual-queue model and the window pipeline's locked invariants
- `/openwiki/player/runtime-facades.md` — the facade runtime side of the same-name facade tests
