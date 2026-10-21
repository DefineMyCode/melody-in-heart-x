---
type: "Reference"
title: "Data Persistence: Room and DataStore"
openwiki_generated: true
sources:
  - id: openwiki-source-186e96b8d6739f3745947903
    resource: repo://app/src/main/AndroidManifest.xml
  - id: openwiki-source-5610fe170bf45c0b63fb5ac9
    resource: repo://app/src/main/java/cn/com/dcsgo/mihx/app/di/PlayerModule.kt
  - id: openwiki-source-bb00a8b0f0083ccce829d880
    resource: repo://app/src/main/java/cn/com/dcsgo/mihx/app/emotion/EmotionScanScheduler.kt
  - id: openwiki-source-81bdf5bdc16c1ff3afd5bcc8
    resource: repo://app/src/main/java/cn/com/dcsgo/mihx/app/emotion/EmotionScanWorker.kt
  - id: openwiki-source-254bbd50662077e2ffc872ad
    resource: repo://app/src/main/java/cn/com/dcsgo/mihx/MelodyApplication.kt
  - id: openwiki-source-89df878e34f12c0da5e5f9b3
    resource: repo://app/src/main/res/xml/backup_rules.xml
  - id: openwiki-source-fc3957012c09f3063a6016dc
    resource: repo://app/src/main/res/xml/data_extraction_rules.xml
  - id: openwiki-source-7d322a65ddca1cf4588fa5e3
    resource: repo://buildSrc/src/main/kotlin/cn/com/dcsgo/mihx/verify/ProductArchitectureVerification.kt
  - id: openwiki-source-a2371d6362e5db4bc834ad03
    resource: repo://CLAUDE.md
  - id: openwiki-source-c45f629879f8babdcb1f3397
    resource: repo://core/model/src/main/java/cn/com/dcsgo/mihx/core/model/WordPreference.kt
  - id: openwiki-source-0f1ea52b4adfffa8391b0e81
    resource: repo://data/build.gradle.kts
  - id: openwiki-source-561e2128ae9736ccc7b9d185
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/di/DatabaseModule.kt
  - id: openwiki-source-6531c6c666e8350847faf4ee
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/di/DataStoreModule.kt
  - id: openwiki-source-6aa5a20cd08decb4d3fe97d0
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/local/dao/MelodyDao.kt
  - id: openwiki-source-87853b629c04266c665c0123
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/local/entity/MigrationStateEntity.kt
  - id: openwiki-source-564e51f1ab9e88f1cd0a2e1a
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/local/entity/PlaybackEventEntity.kt
  - id: openwiki-source-7c5cdcb70575a03ec3017edc
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/local/entity/PlaylistSongCrossRef.kt
  - id: openwiki-source-2171009a4a060165d9540fcf
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/local/entity/SongArtistCrossRef.kt
  - id: openwiki-source-e1ce80f5abb863f1723b9ef6
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/local/entity/SongEntity.kt
  - id: openwiki-source-12a1d6d0e5553c5cb1517529
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/local/entity/WordPreferenceEntity.kt
  - id: openwiki-source-577b7687e8ff177b6a586197
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/local/MelodyDatabase.kt
  - id: openwiki-source-cb2e09c13e73e297a477dc2f
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/local/migration/LegacyJsonSnapshotParser.kt
  - id: openwiki-source-499bb11b283879bef3061aca
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/local/migration/LegacyMigrationContract.kt
  - id: openwiki-source-51949c79e3bf4e421e7ff171
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/local/migration/MelodyMigrations.kt
  - id: openwiki-source-c75fdeafeee7cf621f200c0f
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/local/migration/SharedPreferencesLegacyJsonMigration.kt
  - id: openwiki-source-12d5b8af1aa446b9573e2462
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/repository/EmotionFailureStore.kt
  - id: openwiki-source-b90ad5e1e20562c61d70b296
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/repository/MusicRepository.kt
  - id: openwiki-source-99854c1cb5a148365aba8ef6
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/repository/PlayerSettingsDataStore.kt
  - id: openwiki-source-29a14d2aa8324beadcd09368
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/repository/PlayerSettingsRepository.kt
  - id: openwiki-source-bccc40bc4f4db30340cd2659
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/repository/PlaylistResumeDataStore.kt
  - id: openwiki-source-d8c173fb82dbd4c82e845d11
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/repository/PlayStatsRepository.kt
  - id: openwiki-source-815e01d571755955a0c9b700
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/repository/QuickSkipSongsRepository.kt
  - id: openwiki-source-fde478f222951a2e504c17b0
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/repository/RoomMusicLibraryDataSource.kt
  - id: openwiki-source-20d823efa2e1a5f403b02d3e
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/repository/SongEmotionsRepository.kt
  - id: openwiki-source-1314bf2c6ff1d9d527a30d17
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/repository/SongRepositoryAdapter.kt
  - id: openwiki-source-f8d95aa838cf04a8eb5e5e43
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/repository/TimeSlotConfigStore.kt
  - id: openwiki-source-17d3dc7f59fe7e3d0b195222
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/repository/WordPreferencesRepository.kt
  - id: openwiki-source-556939dd1364840e33c6f8e5
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/util/SongPathResolver.kt
  - id: openwiki-source-f7013cd630cb7c02c18915a3
    resource: repo://data/src/test/java/cn/com/dcsgo/mihx/data/local/migration/SharedPreferencesLegacyJsonMigrationTest.kt
  - id: openwiki-source-c0c66ec2e840e0a6915e7bfc
    resource: repo://data/src/test/java/cn/com/dcsgo/mihx/data/repository/PlayerSettingsRepositoryTest.kt
  - id: openwiki-source-0579d59281dd88200fb3ae19
    resource: repo://data/src/test/java/cn/com/dcsgo/mihx/data/repository/PlaylistResumeDataStoreTest.kt
  - id: openwiki-source-6f066b97b53f49eacd4c62a0
    resource: repo://domain/src/main/java/cn/com/dcsgo/mihx/domain/model/DuplicateSongGroup.kt
  - id: openwiki-source-ee8d5713acafafdce234f04d
    resource: repo://domain/src/main/java/cn/com/dcsgo/mihx/domain/playback/MoodSlotResolver.kt
  - id: openwiki-source-31721788a95f08f3d7c7f198
    resource: repo://domain/src/main/java/cn/com/dcsgo/mihx/domain/playback/PlaybackRestoreCoordinator.kt
  - id: openwiki-source-4d8f1010a81a476f310b27e9
    resource: repo://domain/src/main/java/cn/com/dcsgo/mihx/domain/repository/EmotionFailureRepository.kt
  - id: openwiki-source-60845edcdfb224a7f059ee7a
    resource: repo://domain/src/main/java/cn/com/dcsgo/mihx/domain/repository/PlaylistResumeRepository.kt
  - id: openwiki-source-ec996dfca8a06fd74702893d
    resource: repo://feature/player/src/main/java/cn/com/dcsgo/mihx/feature/player/PlayerPersistenceGraph.kt
  - id: openwiki-source-f2a2fbd536b52485da3bdb2e
    resource: repo://feature/player/src/main/java/cn/com/dcsgo/mihx/feature/player/PlayerPlaybackStateAutosaver.kt
  - id: openwiki-source-8e3d8a1f18f53d37da8744f1
    resource: repo://feature/player/src/main/java/cn/com/dcsgo/mihx/feature/player/PlayerRandomQueueFacade.kt
  - id: openwiki-source-ff40146bae9715f1a3860309
    resource: repo://feature/player/src/main/java/cn/com/dcsgo/mihx/feature/player/PlayerRuntime.kt
  - id: openwiki-source-54ba4f7acb643769b197e402
    resource: repo://feature/player/src/main/java/cn/com/dcsgo/mihx/feature/player/PlayerSleepTimerCoordinator.kt
  - id: openwiki-source-c804d077edb941261a21dbf9
    resource: repo://player/src/main/java/cn/com/dcsgo/mihx/player/AppMediaSessionService.kt
  - id: openwiki-source-1703b70681186dcde3003cfc
    resource: repo://player/src/main/java/cn/com/dcsgo/mihx/player/EmotionAnalyzer.kt
  - id: openwiki-source-e0fe5f5dc62e806494659452
    resource: repo://player/src/main/java/cn/com/dcsgo/mihx/player/FfmpegPcmDecoder.kt
  - id: openwiki-source-de24a99de3caa35a84b73162
    resource: repo://player/src/main/java/cn/com/dcsgo/mihx/player/PlaybackStateSnapshotSerializer.kt
  - id: openwiki-source-b5fe68676096345c29cfbdba
    resource: repo://player/src/main/java/cn/com/dcsgo/mihx/player/PlaybackStateStore.kt
  - id: openwiki-source-91daceadd8472310d223fb0d
    resource: repo://player/src/test/java/cn/com/dcsgo/mihx/player/EmotionAnalyzerLimitsTest.kt
  - id: openwiki-source-186a6bc804c7677b8913c8ce
    resource: repo://player/src/test/java/cn/com/dcsgo/mihx/player/PlaybackStateSnapshotSerializerTest.kt
  - id: openwiki-source-ba9ccc172d5e5f490a763733
    resource: repo://player/src/test/java/cn/com/dcsgo/mihx/player/PlaybackStateStoreTest.kt
generated: { by: "openwiki/0.5.0", at: "2026-10-21T03:20:08.093Z" }
verified:
  - by: openwiki/0.5.0
    at: 2026-10-21T03:20:08.093Z
---


# Data Persistence: Room and DataStore

The app persists data through two complementary stacks: **Room** (`MelodyDatabase`, version 11, 14 entity tables) owns all structured data — library, playlists, stats, quick-skip, emotions, and the emotion word-preference vote table (`word_preferences`); **DataStore Preferences** (5 independent files) owns small-scale settings and state snapshots (player settings, playback snapshot, playlist resume, mood time-slot config JSON, emotion failure records). There are also three **legacy read-only SharedPreferences channels**: the one-shot `SharedPreferencesLegacyJsonMigration` that moves v1 JSON into Room, and the legacy-value fallbacks inside `PlayerSettingsRepository`/`PlaybackStateStore` — every legacy key is deleted as soon as the new storage is written, so legacy SharedPreferences survive only to serve the migration path.

```mermaid
flowchart LR
    MUSIC["MusicRepository"] --> DB[("Room melody.db v11")]
    STATS["PlayStatsRepository"] --> DB
    SKIP["QuickSkipSongsRepository"] --> DB
    EMO["SongEmotionsRepository"] --> DB
    WORDS["WordPreferencesRepository"] --> DB
    SETTINGS["PlayerSettingsRepository"] --> DS1[("DataStore player_settings")]
    RESUME["PlaylistResumeDataStore"] --> DS2[("DataStore playlist_resume")]
    SLOT["TimeSlotConfigStore"] --> DS3[("DataStore mood_time_slot")]
    FAIL["EmotionFailureStore"] --> DS4[("DataStore emotion_failures")]
    STATE["PlaybackStateStore in :player"] --> DS5[("DataStore playback_state")]
    LEGACY["Legacy SharedPreferences music_player_prefs / play_stats_prefs / quick_skip_songs_prefs"] -. read-only migration and fallback .-> MUSIC
    LEGACY -.-> SETTINGS
    LEGACY -.-> STATE
```

*Persistence map: Room is the single source of truth for structured data; the five DataStore domains are independent files; legacy SharedPreferences keeps only three read-only dashed paths (one-shot migration, settings fallback, playback snapshot fallback). `WordPreferencesRepository` is the sole source of the `word_preferences` table.*

Module ownership: Room and all DataStores except `playback_state` live in `:data` and are exposed narrowly through `:domain` repository interfaces; `PlaybackStateStore` lives in `:player` (it directly serves the Media3 session) and is wired through the `PlaybackStateStorage`/`PlaybackStateRepository` interfaces by `app/di/PlayerModule`. Dependency boundaries are covered in `/openwiki/architecture/module-graph.md`.

## Room: MelodyDatabase v11 (14 entity tables)

`MelodyDatabase` declares `version = 11, exportSchema = true` with 14 entities and a single DAO, `MelodyDao`. The database file is `melody.db`, built by `DatabaseModule` via `Room.databaseBuilder` (singleton) with all migrations v1→v11 registered through `MelodyMigrations.ALL`.

| Table | Entity | Purpose |
| --- | --- | --- |
| `songs` | `SongEntity` | Library master table: title/artist/album/sample rate/uri/lrcUri/durationMs and other import metadata; `uri` has a unique index |
| `playlists` | `PlaylistEntity` | Playlist catalog (id, name, created/updated timestamps) |
| `playlist_song_cross_ref` | `PlaylistSongCrossRef` | Playlist↔song many-to-many; `sortOrder` preserves order |
| `play_stats` | `PlayStatsEntity` | Per-song aggregate stats: effective play count, raw play count, accumulated duration, last played |
| `playback_events` | `PlaybackEventEntity` | One row per playback session (started at, duration, effective flag); supports day/week/month aggregation |
| `quick_skip_songs` | `QuickSkipSongEntity` | Quick-skipped songs |
| `quick_skip_short_play_counts` | `QuickSkipShortPlayEntity` | Short-play counters (accumulate to trigger auto quick-skip) |
| `song_group_overrides` | `SongGroupOverrideEntity` | Title overrides for same-name song groups |
| `migration_state` | `MigrationStateEntity` | Data-migration completion markers (name primary key + completedAt); currently only `shared_preferences_json_v1` |
| `artists` | `ArtistEntity` | Atomic artist rows (`name` unique; artists split from `/`-separated tags) |
| `albums` | `AlbumEntity` | Albums (`name` unique) |
| `song_artist_cross_ref` | `SongArtistCrossRef` | Song↔atomic-artist many-to-many |
| `song_emotions` | `SongEmotionEntity` | Emotion analysis results: V/A, per-window `curveJson`, `embeddingB64`, three user-calibration fields |
| `word_preferences` | `WordPreferenceEntity` | Emotion word listen-preference tallies (`word` primary key, `likeVotes`/`dislikeVotes`/`updatedAt`); per word-preference-vote row |

Note that entities declare **no Room foreign-key constraints**; all relationships (playlist↔song, song↔artist/album, and the songId-keyed stat tables) are logical, maintained by repository code rather than enforced by the schema.

```mermaid
erDiagram
    playlists ||--o{ playlist_song_cross_ref : "playlistId"
    songs ||--o{ playlist_song_cross_ref : "songId"
    artists ||--o{ song_artist_cross_ref : "artistId"
    songs ||--o{ song_artist_cross_ref : "songId"
    albums |o--o{ songs : "albumId"
    songs ||--o| play_stats : "songId"
    songs ||--o| quick_skip_songs : "songId"
    songs ||--o| quick_skip_short_play_counts : "songId"
    songs ||--o| song_group_overrides : "songId"
    songs ||--o| song_emotions : "songId"
    songs ||--o{ playback_events : "songId"
```
*Plus `word_preferences` is a standalone word->votes table keyed by word (no song FK).*

*Logical relationships among Room tables, joined on songId/albumId/artistId and maintained in code — not FK-enforced.*

### Room write path: MusicRepository and RoomMusicLibraryDataSource

`MusicRepository` holds the in-memory library (`songs`/`playlists`) and persists via **asynchronous full-table snapshots**: every change snapshots the current list into `persistScope` (a single-threaded IO scope, `Dispatchers.IO.limitedParallelism(1)`), where `RoomMusicLibraryDataSource.persistSongs`/`persistPlaylists` replace everything inside the `@Transaction` methods `replaceSongs`/`replacePlaylists`. Serializing snapshots on one thread guarantees the later snapshot always supersedes the earlier one (the final state is correct) while UI actions on the main thread never wait for disk.

`RoomMusicLibraryDataSource.restore()` rebuilds domain objects from `songs` + `song_group_overrides` + `playlists` + refs at startup and calls `MelodyDao.syncLibraryCatalog` to sync the artist/album catalogs: idempotently insert missing atomic artists and albums, rebuild all `song_artist_cross_ref` rows, batch-backfill `songs.albumId` (avoiding the early per-song N+1 query regression), then delete orphan artists/albums.

### File-check dedup scan and cleanup

Detecting and removing songs that point at the same physical file is a persistence-coordinated flow exposed through `SongRepository.scanDuplicateSongGroups()`/`deduplicateSongs()` (delegated by `SongRepositoryAdapter` to `MusicRepository`, both suspend and dispatched to IO):

- **Scan**: `scanDuplicateSongGroups()` snapshots the in-memory library and buckets each song by `SongPathResolver.resolveRealPath(context, uri)`, which normalizes a URI's real on-disk path (handling `file://`, MediaStore, and SAF document/tree URIs whose string prefixes differ even when they resolve to the same file). Unresolvable songs (null path) are excluded from any group so normalization failure never causes a false duplicate or deletion. Groups with `size > 1` return as `DuplicateSongGroup`s with songs sorted by ascending songId; `keep` is the earliest-imported (smallest id) entry.
- **Cleanup**: `deduplicateSongs(groups)` collects every `duplicates.id`, then under the library write lock removes those ids from each playlist's `songIds` (updating song counts) and from `songs`, queuing full-table `persistSongs`/`persistPlaylists` snapshots. It then calls `cleanupMissingSongAssociations`, which purges the removed ids' orphan records from `play_stats`, `playback_events`, `quick_skip_songs`, `quick_skip_short_play_counts`, and `song_emotions`, and deletes now-orphaned artists/albums. **The underlying physical file is never deleted** — the kept entry still points at it, so deleting the file would destroy the survivor too.

### Emotion word listen-preference tallies (word_preferences)

`WordPreferencesRepository` is the **only data source** for the `word_preferences` table (added in v11, 2026-10-20 explore-play). Each emotion word has a single row keyed by `word` with `likeVotes`/`dislikeVotes`/`updatedAt`. Playback outcomes **accumulate votes only** — they never overwrite an affinity directly — so a word can simultaneously hold votes on both sides; the domain-level `WordPreference.kind` (`core/model/WordPreference.kt`) decisively labels it `LIKE`/`DISLIKE`/`NEUTRAL` by which count is higher (a tie is neutral), making a single skip unable to flip a liked word (multiple evidence is required to flip). It is the "same song A B already liked, C D already disliked" conflict resolver.

- **Write path**: `recordSongOutcome(words, liked)` reads the current rows, computes `likeVotes`/`dislikeVotes` (+1 on the matching side), and bulk-`upsert`s via the DAO's `@Upsert` (the DAO only does whole-row replacement; vote accumulation lives in the repository). Empty input is a no-op, and words are deduplicated before tallying.
- **Read path**: `observeAll()` maps `MelodyDao.observeAllWordPreferences()` (Room invalidation-driven Flow, cached by `PlayerRuntime` for explore-play) and `getAll()` is a full-table blocking snapshot.
- **Consumer**: `PlayerRuntime.handleExploreSongSettled` decides per completed/skipped song (settlement is via `ExplorePlaybackPolicy.isSkipped` — <30s or <1/4 total duration is a skip) and calls `recordSongOutcome(words, liked = !skipped)`; `PlayerRandomQueueFacade` filters candidates by word preference kind, and the user screen renders the like/neutral/dislike buckets.
- **Tests**: `WordPreferencesRepositoryTest` locks the vote-accumulation semantics (single skip does not flip, tie is neutral, empty input ignored) and `MelodyDatabaseMigrationTest.migration10To11CreatesWordPreferencesTable`.

## The playback_events index design

The composite index `(startedAtMs, isEffectivePlay, songId)` on `playback_events` covers every time-range aggregation query: `totalDurationBetween` (duration), `distinctSongsBetween` (distinct songs), `dailyDurationsBetween` (per-day), and `playCountsBetween` (song TOP chart, fully index-covered). The v7→v8 migration replaced the single-column `startedAtMs` index created in v5→v6 with this composite index (the old one was a redundant prefix) and repaired the historical inconsistency of "fresh installs missing the index".

## Room schema change workflow (enforced)

Every entity/DAO change must follow this workflow (codified in `CLAUDE.md`, machine-enforced by `verifyProductArchitecture`):

1. **Change the entity/DAO** under `data/src/main/java/cn/com/dcsgo/mihx/data/local/{entity,dao}`.
2. **Bump the version**: `MelodyDatabase.VERSION` (currently 11 → next 12).
3. **Export the new schema JSON**: `:data` configures KSP with `room.schemaLocation = $projectDir/schemas` (`data/build.gradle.kts`); building generates `data/schemas/cn.com.dcsgo.mihx.data.local.MelodyDatabase/12.json` automatically.
4. **Register the migration**: add `Migration(N, N+1)` in `MelodyMigrations` (a dedicated object in `data/.../migration/MelodyMigrations.kt`, extracted from `DatabaseModule` so migration regression tests reference the same instances) and include it in `MelodyMigrations.ALL`; `DatabaseModule` registers the whole array via `addMigrations(*MelodyMigrations.ALL)`.
5. **Add a unit test** for any migration involving complex data movement.
6. **Never modify existing schema JSONs** — they are immutable historical snapshots.

`verifyProductArchitecture` (root `build.gradle.kts`, rule body moved to `buildSrc` `ProductArchitectureVerification` and unit-covered) hard-checks persistence invariants: schema v1 must not contain `quick_skip_short_play_counts`, v2 must contain it and must not contain `lrcUri`, v3 must contain `lrcUri` (version history is tamper-evident); `MelodyMigrations` must register `Migration(1, 2)`/`Migration(2, 3)` and `DatabaseModule` must call `addMigrations(*MelodyMigrations.ALL)`; both backup rule files must contain the full privacy exclude list (below). The full rule inventory lives in `/openwiki/operations/build-and-verification.md`.

### Migration history v1 → v11

| Migration | Content |
| --- | --- |
| 1→2 | Create `quick_skip_short_play_counts` |
| 2→3 | Add `lrcUri` to `songs` |
| 3→4 | Add `album` column to `songs` |
| 4→5 | Create `artists`/`albums`/`song_artist_cross_ref`; add `albumId` to `songs` |
| 5→6 | Create `playback_events` (no single-column `startedAtMs` index — the v6 entity declared no `@Index`, matching the exported schema; the `DROP` in 7→8 still keeps the historical old 5→6 index compatible) |
| 6→7 | Add `durationMs` to `songs` |
| 7→8 | Swap `playback_events` to the composite index (drop any single-column index) |
| 8→9 | Create `song_emotions` (V/A curve table) |
| 9→10 | Add `embeddingB64`/`userValence`/`userArousal`/`userTags` to `song_emotions` (personalization loop) |
| 10→11 | Create `word_preferences` (emotion word listen-preference tallies, explore play) |

`MelodyDatabaseMigrationTest` (Robolectric `MigrationTestHelper`) validates each hand-written migration against the stored schema snapshots (`:data` exposes the schemas dir as test assets).

## Legacy JSON migration: v1 SharedPreferences → Room

v1 stored the library and playlists as SharedPreferences JSON; a one-shot migration now moves it into Room:

- **Sources** (`SharedPreferencesLegacyJsonMigration`): `music_player_prefs` (`songs_json`, `playlists_json`), `play_stats_prefs` (`play_count_*`/`raw_play_count_*`/`play_duration_*` keys), and `quick_skip_songs_prefs` (`quick_skip_song_ids_json`, `short_play_count_*`).
- **Parsing** (`LegacyJsonSnapshotParser`): each JSON block is parsed in its own `runCatching`; a corrupted block degrades to empty without blocking the others. Playlist refs keep only songIds that match already-parsed `availableSongIds`; songs are deduplicated by `uri`.
- **Idempotency marker**: it writes `MigrationStateEntity(LEGACY_MIGRATION_NAME, completedAt)` (name = `shared_preferences_json_v1`) into `migration_state`; on subsequent launches the marker short-circuits `migrateIfNeeded()`.
- **Trigger point**: `MusicRepository.loadPersistedSongs()` calls `migrateIfNeeded()` **before** restoring from Room — the legacy JSON is consumed read-only once, and Room remains the single source of truth.
- **Tests**: `SharedPreferencesLegacyJsonMigrationTest` locks down three behaviors — full migration plus completion marker, stats still migrated (plus marker) when JSON is corrupt, and complete skip (no rewrite) when the marker already exists.

## The five DataStore domains

Each domain is its own `preferencesDataStore` file (independent key spaces). The design rule: **only small, non-relational state goes into DataStore** — the `TimeSlotConfigStore`/`EmotionFailureStore` comments explicitly cite "no Room schema change" as the selection rationale.

### 1. `player_settings` — PlayerSettingsDataStore

`PlayerSettingsRepository` reads and writes every key in `PlayerSettingsKeys`: `theme_mode`/`theme_variant` (`theme_variant` defaults to `MONO`), `screen_orientation_mode` (default `SENSOR_AUTO`), uniform-random toggle, Bluetooth playback monitoring, playback notification, lyric font scale, sleep timer (`sleep_timer_end_at_ms` + play-last-song), daily listening goal, emotion scan pause (`emotion_scan_paused`), the mood time-slot master switch (`mood_time_slot_enabled`), and local-music sorting (`song_sort_mode` — first enum matching or `IMPORT_ORDER` — plus `song_sort_ascending` defaulting to `true`). The sort dimension and direction persist orthogonally.

**Accessor split**: settings are exposed as reactive `Flow`s on the domain interface (e.g. `themeMode`, `songSortMode`, `songSortAscending`) plus suspend setters (`setThemeMode`, `setSongSortAscending`, …); reads and writes are all suspend/coroutine-friendly since the M-5 refactor removed the `currentXxx()`/`setXxxBlocking()` runBlocking families. Reads carry a **legacy fallback**: every boolean setting is emitted through `legacyBackedFlow`, which reads the legacy key once on the IO thread and merges it as the default into the DataStore Flow (`m-12`) — so when DataStore lacks a value the legacy `dark_theme`-style key is consulted (theme additionally falls back through an in-DataStore `dark_theme` boolean — the pre-flip key — then the SharedPreferences key). Once the user explicitly writes (e.g. `setThemeMode`), `setThemeMode`/`setGlobalUniformRandomEnabled`/`setBluetooth...`/`setPlaybackNotificationEnabled` delete the corresponding legacy keys — old keys decay with use. **Random-enhance mode**: the newer `RANDOM_ENHANCE_MODE` key is preferred; old users without it fall back to the `mood_time_slot_enabled` switch (true → `TIME_SLOT`, else `OFF`) and `setRandomEnhanceMode` writes both keys back in one transaction so the two read paths stay consistent (`moodTimeSlotEnabled` derives from `randomEnhanceMode == TIME_SLOT`). The sleep timer's end time and play-last-song flag persist so that `PlayerSleepTimerCoordinator.restore()` resurrects unexpired countdowns after restart (expired ones reset to zero).

### 2. `playback_state` — PlaybackStateStore + PlaybackStateSnapshotSerializer

The process-death recovery data, living in `:player`:

- **Keys**: `play_queue_json` (queue JSON), `play_position_ms`, `is_infinite_play`, `infinite_played_ids`, `is_explore_play`, `current_song_id`.
- **Save semantics**: an empty session (empty queue, no currentSongId, not infinite play) **never writes the queue JSON over an existing snapshot** and never deletes it — the UI-recreation window has a transient all-empty state, and the old clear behavior erased a valid snapshot written seconds earlier, leaving the queue permanently empty after restart (2026-09-03 regression). The empty-session guard is more nuanced: with an existing snapshot it keeps the queue and only updates `current_song_id` + position; with no existing snapshot but a present `currentSongId` it writes a single-song queue for restore-time correction; only a fully empty save with no prior snapshot writes nothing. Explicit clearing goes through `clearPlaybackState()`, and legacy keys are cleared on write only when DataStore actually changed. Save failures are logged and swallowed so playback control is never interrupted.
- **Serialization**: `PlaybackStateSnapshotSerializer` encodes the queue as `{songIds, currentIndex, playMode, playOrderIds}` JSON; decoding filters unavailable songs against the current library, clamps `currentIndex`, and repairs the play order; corrupt JSON returns null. Infinite-play played ids are filtered against available songs on decode too.
- **Restore decision**: `PlayerPersistenceGraph` lazily builds `PlaybackStateStore`, `PlaybackRestoreCoordinator`, `PlayerPersistenceFacade`, and `PlayerPlaybackStateAutosaver`. `restorePlaybackState()` reads and decodes the snapshot on an IO coroutine (in parallel with controller connection); `onControllerReady()` sets a `controllerReady` flag and both sides rendezvous in `maybeApplyRestore()`, which waits for `controllerReady` and a non-null `pendingRestore` before deciding via `hasLiveSession()` — if the service is still playing, only the UI queue shadow is backfilled (the live controller session is never overwritten, its snapshot sync sets `currentIndex` but not `songs`); on a cold start with no session, UI and controller queue are fully restored via `prepareControllerQueue` without autoplay. Restore also re-anchors the queue onto the stored `current_song_id` (`withCurrentSongId`), synthesizing a single-song queue if the song is absent from the saved queue. The full save/restore sequence is in `/openwiki/workflows/playback-session-lifecycle.md`.
- **Safety-net writes**: `PlayerPlaybackStateAutosaver` persists every 5 seconds (`DEFAULT_INTERVAL_MS = 5_000L`) on playback position; `AppMediaSessionService` writes the final snapshot asynchronously on `onDestroy`/`onTaskRemoved` via a process-level scope (`persistCurrentPlaybackSnapshot` suspend variant) and, in the same coroutine, settles the playlist resume record (`recordCurrentSource`).

<!-- openwiki: mermaid parse failed and this diagram was converted to a text fence so it does not break rendering. Fix the diagram source and restore the mermaid fence. Parser error: Heuristic: a semicolon inside a label breaks rendering; rephrase the label. -->
```text
flowchart TD
    A["startup restorePlaybackState"] --> B["read DataStore snapshot on IO coroutine"]
    B -->|"no play_queue_json"| C["return null, nothing to restore"]
    B -->|"snapshot exists"| D["decode and filter against current library"]
    D --> E["set pendingRestore; wait for controllerReady handshake"]
    E --> F{"hasLiveSession?"}
    F -->|"yes, service still playing"| G["backfill UI queue shadow only, never overwrite controller"]
    F -->|"no, cold start"| H["full restore of UI and controller queue, no autoplay"]
```

*Playback snapshot restore decision: snapshot read and controller connection run in parallel and rendezvous in `maybeApplyRestore` (after `onControllerReady`) to remove the race.*

### 3. `playlist_resume` — PlaylistResumeDataStore

Playlist resume records: one key per playlist (`resume_<id>`) holding `{"songId":N,"updatedAtMs":T}` JSON, plus a `source_playlist_id` marker for "the playlist the current queue came from". Two **transactional methods** are the core invariant:

- `switchSourcePlaylist(newSource, currentSongId)`: reading the old source and writing the new one must happen inside a single `edit` transaction — otherwise rapid playlist switching can settle a song onto the wrong playlist;
- `recordCurrentSource(songId)` (the playback service exit hook): read source, write the resume record, and clear the marker in one transaction, returning whether a record was made.

`PlaylistResumeDataStoreTest` covers the transactional semantics (settle the old playlist, same-playlist switch does not settle, switching to non-playlist source settles first then clears) and corrupt JSON tolerance (`observeResume` returns null).

### 4. `mood_time_slot` — TimeSlotConfigStore

Mood time-slot configs for contextual random playback: a single key `time_slot_configs` holds a JSONArray (`[{"id":1,"name":"深夜静谧","start":1320,"end":360,"tags":["静谧","禅"]}]`). With single-digit to low-double-digit slot counts, JSON serialization suffices and **no Room schema change is needed**.

- `save` validates through `MoodSlotResolver.validate` first (name/tags/zero-length/overlap); conflicts throw `IllegalArgumentException` with a message naming the conflicting slot; on success the whole key is rewritten sorted by start time.
- **Parse tolerance**: the outer `runCatching` returns an empty list on failure; the inner per-item `runCatching` skips a single corrupted entry (with a warning log) instead of losing the entire list.
- Runtime consumption: `PlayerRuntime` snapshots configs into `moodSlotConfigsCache` at startup (the settings page refreshes it via `refreshMoodSlots()`), and `PlayerRandomQueueFacade` evaluates the active slot on each random draw to filter the candidate pool — the decision semantics are documented in `/openwiki/concepts/mood-time-slot.md`.

### 5. `emotion_failures` — EmotionFailureStore

Emotion analysis failure records (same design as TimeSlotConfigStore): one key `emotion_failures` holds a JSONObject keyed by songId with `{reason, failedAt, attempts}`. `record` reads-modifies-writes in one transaction, accumulating `attempts` and overwriting `reason`/`failedAt` with the latest; parsing skips corrupted entries. The consumer is `EmotionScanWorker`: songs with `attempts >= MAX_ATTEMPTS(3)` stop being retried automatically (the detail page still allows manual retry via `clearForRetry`, which clears records and re-enqueues), otherwise every scan pass spins on the same broken files. A successful analysis clears the record via `clear(songId)`. The periodic scan is scheduled by `EmotionScanScheduler` in `MelodyApplication.onCreate` (charging + battery-not-low constraints, one pass every 6 hours, ≤40 songs per pass).

## The suspend persistence contract and residual blocking bridges

Most `:data` repositories now expose **suspend/`Flow` APIs that `withContext(Dispatchers.IO)`** the Room query rather than blocking: `SongEmotionsRepository` (get/getAll/upsert/saveCorrection…), `PlayStatsRepository` (the room read/writes plus atomic SQL upserts such as `incrementPlayCount`), and `QuickSkipSongsRepository` were refactored (M-5, 2026-10-15) from `runBlocking(IO)` bridges to `already-suspend` functions — the old 17-call-site blocking families (`currentXxx()`/`setXxxBlocking()` on `PlayerSettingsRepository`) were removed in the same refactor in favor of `Flow` observation plus suspend writes. This removed main-thread blocking from `PlayerRuntime.start`'s startup settings and the sleep-timer restore path.

**`runBlocking(IO)` remains only where a coroutine-less or synchronous domain signature demands it**:

- `PlaybackStateStore.save`/`restore`/`clear` (in `:player`) still `runBlocking(Dispatchers.IO)`, because `PlaybackStateStorage`/`PlaybackStateRepository` expose synchronous methods consumed by the Media3 session and service teardown; the suspend variant `persistCurrentPlaybackSnapshot` exists for non-blocking service-destroy paths.
- `PlayerSettingsKeys` legacy SharedPreferences reads (`readLegacyBoolean`) run `withContext(Dispatchers.IO)` and are merged into the DataStore Flow on the IO thread (`m-12`), never read synchronously inside the DataStore map on the collector thread.

**Contract for callers**: blocking/suspend bridges must not be called on the main thread — suspend paths offload via `Dispatchers.IO` internally or the caller switches (e.g. `AppMediaMetadataViewModel.saveEmotionCorrection` wraps the emotion repository calls in `withContext(Dispatchers.Default)` so the DataStore/Room writes do not jank the UI). For new repository methods prefer suspend/`Flow` APIs and `withContext(Dispatchers.IO)`; add a `runBlocking` bridge only when the domain contract (or a coroutine-less path such as service teardown) requires it, and document the thread requirement in the KDoc.

## Backup and privacy exclusions (release checklist)

`app/src/main/res/xml/backup_rules.xml` (`<full-backup-content>`, pre-Android 12) and `data_extraction_rules.xml` (both `cloud-backup` and `device-transfer` scenarios) must exclude the same set of data:

- Legacy SharedPreferences: `music_player_prefs.xml`, `play_stats_prefs.xml`, `quick_skip_songs_prefs.xml`;
- The full Room family: `melody.db`, `melody.db-journal`, `melody.db-shm`, `melody.db-wal`;
- The five DataStore files: `datastore/player_settings.preferences_pb`, `datastore/playback_state.preferences_pb`, `datastore/playlist_resume.preferences_pb`, `datastore/mood_time_slot.preferences_pb`, `datastore/emotion_failures.preferences_pb`;
- Album-art caches: `album_art_cache/`, `album_art/`, `cache/album_art/`.

**This is a release checklist item**: `verifyProductArchitecture` walks a `requiredPrivacyExcludes` list (legacy prefs, the Room family, the player_settings/playback_state DataStores, and `cache/album_art/`) against both rule files and fails on any missing entry. The restore semantics justify it — library/playback state is device-bound (SAF URIs die off-device, snapshots cannot match another device's library), so backing it up only adds restore payload and privacy surface. When adding a new persisted file (e.g. a new DataStore domain), add it to both rule files (and to `requiredPrivacyExcludes` where appropriate) or the architecture gate will fail.

## Emotion analysis memory budget (largeHeap + adaptive budget)

Emotion analysis is memory-heavy, and the app runs with `android:largeHeap="true"` (`app/src/main/AndroidManifest.xml`) so the device heap is larger than the default. Two decode paths share a **single adaptive soft cap** sourced from `EmotionAnalyzer.memoryBudgetForHeap(Runtime.getRuntime().maxMemory())`, computed once at construction (the heap max does not change at runtime):

- `EmotionAnalyzer` (the analysis entry) and `FfmpegPcmDecoder` (the FFmpeg soft-decode bridge) both read the budget as `memoryBudgetBytes = memoryBudgetForHeap(...)`.
- `memoryBudgetForHeap(max)` = `(maxHeap - RESERVED_BYTES)` clamped into `[96MB, 512MB]`, with a 64MB baseline reservation for the inference models + app baseline (does not scale with heap). A 256MB heap → 192MB (identical to the old fixed value, so low-end behavior is unchanged); 384MB → 320MB; 512MB → 448MB — larger heaps automatically allow longer 192 kHz files.
- The budget is a soft cap on the combined decode buffer + resample output (see `EmotionAnalyzerLimitsTest`). Because `ShortAccum` doubling can blow past the steady-state peak at sample rates above 64 kHz (`TRANSIENT_DOMINANT_RATE`), the cap constrains both the steady-state and transient peaks (2026-10-21 OOM regression). It also drives whether an oversized/high-rate song is reported as `TOO_LARGE_FOR_DEVICE` (memory budget hit first) versus `TOO_LONG` (20-minute cap hit first) — the two are distinguished so large files are not misreported.

## Key tests

| Test | Locked behavior |
| --- | --- |
| `PlaybackStateStoreTest` | Snapshot round trip (queue/position/mode/order/duplicates), restore filters unavailable songs, infinite-play state kept, **empty-session save keeps existing snapshot**, currentSongId corrects the queue, legacy fallback and next-save clears legacy keys |
| `PlaybackStateSnapshotSerializerTest` | Queue JSON round trip, corrupt JSON returns null, missing currentIndex returns null, optional-field defaults, infinite-play id filtering |
| `SharedPreferencesLegacyJsonMigrationTest` | Full migration + completion marker, stats still migrated on corrupt JSON, marker skips migration |
| `PlaylistResumeDataStoreTest` | Resume record/overwrite/per-playlist clear, corrupt JSON returns null, source marker and transactional settle semantics |
| `PlayerSettingsRepositoryTest` | Theme legacy fallback → DataStore write → legacy key cleared, defaults |
| `WordPreferencesRepositoryTest` | Vote accumulation on skip/full-listen, tie → neutral, empty input ignored |
| `MelodyDatabaseMigrationTest` | Each hand-written migration validated against stored schema snapshots (Robolectric), incl. 10→11 `word_preferences` |
| `EmotionAnalyzerLimitsTest` | Memory budget scales with heap (256→192MB, 512→448MB) and clamps; budget-vs-duration cutoff reason |
| `MusicRepositoryRoomTest` / `PlayStatsRepositoryRoomTest` / `QuickSkipSongsRepositoryRoomTest` | Room repository contracts against FakeDaos |

When changing persistence behavior (especially playback snapshot save/restore and legacy migration), these tests are the regression floor; schema-migration changes additionally require the migration unit test from step 5 above.
