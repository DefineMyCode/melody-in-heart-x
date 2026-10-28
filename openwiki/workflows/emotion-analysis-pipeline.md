---
type: Workflow
title: "Emotion Analysis Pipeline"
description: "End-to-end offline emotion analysis on Android: batch-scan scheduling (periodic + manual WorkManager chains), EmotionAnalyzer TFLite inference decoding via FFmpeg/MediaCodec to 16 kHz mono with RMS arousal, calibrated valence and vocal score, heap-adaptive decode memory budget for high-sample-rate songs, Room song_emotions persistence with user-calibration preservation, failure tracking, and consumption by mood time-slot shuffle."
tags: [emotion, workmanager, tflite, yamnet, background-work, room, android, ffmpeg]
sources:
  - id: openwiki-source-96607d29d5086ea5d14045e9
    resource: repo://app/src/main/java/cn/com/dcsgo/mihx/app/AppMediaMetadataViewModel.kt
  - id: openwiki-source-c997f8c2a81730fe7eb841ae
    resource: repo://app/src/main/java/cn/com/dcsgo/mihx/app/AppNavHost.kt
  - id: openwiki-source-4ca5f9c29b016f072d8ed57c
    resource: repo://app/src/main/java/cn/com/dcsgo/mihx/app/AppRoot.kt
  - id: openwiki-source-6c077957a0a332d123650e6b
    resource: repo://app/src/main/java/cn/com/dcsgo/mihx/app/emotion/EmotionScanEntryPoint.kt
  - id: openwiki-source-bb00a8b0f0083ccce829d880
    resource: repo://app/src/main/java/cn/com/dcsgo/mihx/app/emotion/EmotionScanScheduler.kt
  - id: openwiki-source-81bdf5bdc16c1ff3afd5bcc8
    resource: repo://app/src/main/java/cn/com/dcsgo/mihx/app/emotion/EmotionScanWorker.kt
  - id: openwiki-source-0b357d42d1a4c0a891d0af57
    resource: repo://app/src/main/java/cn/com/dcsgo/mihx/app/emotion/EmotionViewModel.kt
  - id: openwiki-source-ea74e07321c7cd79b5bf1c80
    resource: repo://app/src/main/java/cn/com/dcsgo/mihx/app/mood/MoodTimeSlotViewModel.kt
  - id: openwiki-source-254bbd50662077e2ffc872ad
    resource: repo://app/src/main/java/cn/com/dcsgo/mihx/MelodyApplication.kt
  - id: openwiki-source-7ff4d599f076fc6bdd21d6a1
    resource: repo://core/model/src/main/java/cn/com/dcsgo/mihx/core/model/EmotionTags.kt
  - id: openwiki-source-9beda722f532888c0035eea0
    resource: repo://core/ui/src/main/java/cn/com/dcsgo/mihx/ui/components/EmotionCorrection.kt
  - id: openwiki-source-6c1a934c65ef428191c8ca98
    resource: repo://core/ui/src/main/java/cn/com/dcsgo/mihx/ui/components/SongEmotionSection.kt
  - id: openwiki-source-c545006ce2a4ed14097a125f
    resource: repo://core/ui/src/test/java/cn/com/dcsgo/mihx/ui/components/SongEmotionSectionTest.kt
  - id: openwiki-source-12d5b8af1aa446b9573e2462
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/repository/EmotionFailureStore.kt
  - id: openwiki-source-20d823efa2e1a5f403b02d3e
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/repository/SongEmotionsRepository.kt
  - id: openwiki-source-be4ba2c439dc1645bd326cb9
    resource: repo://docs/architecture/MOOD_TIME_SLOT_PLAYBACK.md
  - id: openwiki-source-4d8f1010a81a476f310b27e9
    resource: repo://domain/src/main/java/cn/com/dcsgo/mihx/domain/repository/EmotionFailureRepository.kt
  - id: openwiki-source-8e3d8a1f18f53d37da8744f1
    resource: repo://feature/player/src/main/java/cn/com/dcsgo/mihx/feature/player/PlayerRandomQueueFacade.kt
  - id: openwiki-source-ff40146bae9715f1a3860309
    resource: repo://feature/player/src/main/java/cn/com/dcsgo/mihx/feature/player/PlayerRuntime.kt
  - id: openwiki-source-2406bafab2e742d99aa82d73
    resource: repo://feature/player/src/test/java/cn/com/dcsgo/mihx/feature/player/PlayerRandomQueueFacadeTest.kt
  - id: openwiki-source-b2f8c0ac0fee8a3771c8d58e
    resource: repo://feature/playlist/src/main/java/cn/com/dcsgo/mihx/feature/playlist/EmotionLibraryView.kt
  - id: openwiki-source-30a89ac5759650d08397217e
    resource: repo://feature/user/src/main/java/cn/com/dcsgo/mihx/feature/user/EmotionAnalysisScreen.kt
  - id: openwiki-source-1703b70681186dcde3003cfc
    resource: repo://player/src/main/java/cn/com/dcsgo/mihx/player/EmotionAnalyzer.kt
  - id: openwiki-source-e0fe5f5dc62e806494659452
    resource: repo://player/src/main/java/cn/com/dcsgo/mihx/player/FfmpegPcmDecoder.kt
  - id: openwiki-source-d98063755708e1d4ef8fe15b
    resource: repo://player/src/main/java/cn/com/dcsgo/mihx/player/ShortAccum.kt
  - id: openwiki-source-91daceadd8472310d223fb0d
    resource: repo://player/src/test/java/cn/com/dcsgo/mihx/player/EmotionAnalyzerLimitsTest.kt
generated: { by: "openwiki/0.5.0", at: "2026-10-27T08:49:20.803Z" }
verified:
  - by: openwiki/0.5.0
    at: 2026-10-27T08:49:20.803Z
---

# Emotion Analysis Pipeline

The emotion feature's data lifecycle is a closed on-device loop: **scheduling** (periodic charge-triggered scan + manual immediate scan) → **batch-scan Worker** (filters songs awaiting analysis) → **inference pipeline** (`EmotionAnalyzer`: decode + YAMNet + VA head, with RMS arousal and vocal score) → **persistence** (Room `song_emotions` upsert / DataStore failure records) → **display & calibration** (detail curve section, library emotion tab, calibration write-back) → **consumption** (mood time-slot shuffle filtering on emotion tags).

This page focuses on flow and failure semantics; the domain model and vocabulary live in `/openwiki/concepts/emotion-model.md`, the storage schema in `/openwiki/architecture/data-persistence.md`, and playback-side decisions in `/openwiki/concepts/mood-time-slot.md`.

## End-to-end overview

<!-- openwiki: mermaid parse failed and this diagram was converted to a text fence so it does not break rendering. Fix the diagram source and restore the mermaid fence. Parser error: Heuristic: an unescaped angle bracket inside a label breaks rendering; rephrase the label. -->
```text
sequenceDiagram
    participant App as MelodyApplication
    participant WM as WorkManager
    participant VM as EmotionViewModel
    participant W as EmotionScanWorker
    participant AN as EmotionAnalyzer
    participant RE as SongEmotionsRepository
    participant FS as EmotionFailureStore

    App->>WM: onCreate calls schedule
    Note over WM: periodic emotion_periodic_scan<br/>6h round, charging + battery-not-low
    VM->>WM: startManualScan (clear pause flag first, then enqueue)
    Note over WM: manual emotion_manual_scan<br/>APPEND chain, up to 300 songs/round
    WM->>W: doWork
    W->>W: pick pending (version stale / missing vocal, has uri, failed < 3 times)
    loop per song (periodic 40 / manual 300 per round)
        W->>AN: analyze(songId, uri, MODEL_VERSION)
        alt success and song still in current library snapshot
            AN-->>W: Success (per-window curve + peak + embedding)
            W->>RE: upsert (inherits existing user calibration)
            W->>FS: clear(songId)
        else failure and song still in current library snapshot
            AN-->>W: Failure (user-presentable reason)
            W->>FS: record(songId, reason)
        end
    end
    opt manual task and gap remains and not whole-batch-failure, not paused midway
        W->>WM: enqueueUniqueManualScan (APPEND next round)
    end
```

*Emotion batch-scan loop: two trigger chains converge on the same Worker; success upserts + clears failure records, failure accumulates records; the manual chain re-enqueues via APPEND until convergence.*

## Batch-scan scheduling: periodic and manual trigger chains

**Periodic chain** is armed at app startup: `MelodyApplication.onCreate` calls `EmotionScanScheduler.schedule(this)`. The scheduler enqueues a `PeriodicWorkRequestBuilder<EmotionScanWorker>` under the unique name `emotion_periodic_scan`, period 6 hours, constraints `setRequiresCharging(true)` + `setRequiresBatteryNotLow(true)` and no network requirement, initial delay 30 minutes. Policy is `ExistingPeriodicWorkPolicy.KEEP` (repeated calls do not replace an existing task). The whole enqueue is wrapped in `runCatching` — a WorkManager failure only logs, never blocks startup.

**Manual chain** is triggered by the "My Library" entry card: `AppNavHost.onEmotionScanNow` calls `EmotionViewModel.startManualScan()` and toasts "scan started, you may leave this page; it continues in the background". `startManualScan` ordering matters: **`setEmotionScanPaused(false)` first to clear the pause flag, then enqueue** — preventing the Worker from reading a stale pause value and skipping at startup. The manual task is `enqueueUniqueManualScan()` (`EmotionScanWorker.kt`'s extension function): one-time job, input `KEY_MANUAL=true`, unique name `emotion_manual_scan`, default `ExistingWorkPolicy.KEEP` (won't double-enqueue if already running/queued), **not subject to charging constraints**.

Batch sizes differ: periodic uses `BATCH_SONGS = 40` per round (incremental convergence), manual uses `MANUAL_BATCH_SONGS = 300` per round to scan a large library in one sweep (WorkManager's 10-minute per-task cap is covered by re-enqueue, below).

## EmotionScanWorker: batch loop and re-enqueue rules

The Worker is a `CoroutineWorker`, fetching dependencies via `EntryPointAccessors.fromApplication(applicationContext, EmotionScanEntryPoint)` — `EmotionScanEntryPoint` is the bridge for WorkManager (not Hilt-managed) to access singletons, exposing the music/emotion/failure repositories, the analyzer, and the settings repository.

**Pending filtering** (start of `doWork`) applies three filters to the whole library:

1. `analyzed[songId] != EmotionAnalyzer.MODEL_VERSION` **or** `songId in songIdsMissingVocalScore()` — not yet analyzed under the current model version, or analyzed before `vocalScore` existed (2026-10-25); either requeues (a model upgrade automatically requeues old data, and a missing vocal score gets one backfill scan);
2. `it.uri != null` — has a locally readable file;
3. songs whose failure record `attempts >= MAX_ATTEMPTS(3)` are **excluded** — otherwise every scan round spins on the same broken files, and the user always sees "a few songs still unanalyzed" that never finish.

An empty pending list returns `Result.success()` directly (logging that the library is up to date, and that some songs were skipped due to repeated failure).

**Per-song loop** iterates `pending.take(batch)`: first checks `isStopped` (system stop / timeout truncation) and the paused flag (cooperative; read from a subscribed `AtomicBoolean` — M-2, one DataStore subscription for the whole loop, zero per-song IO), then `setProgress(workDataOf(KEY_PROGRESS_CURRENT to song.title))` to push the current song title to the UI, then `runCatching { analyzer.analyze(...) }` — even a thrown exception is caught and turned into `Failure(INFERENCE_ERROR)`. **Orphan guard before persistence (v3.10.2)**: the user may delete the song mid-batch, so before writing success/failure records it re-checks `musicRepository().containsSong(song.id)` (allocation-free O(n) membership, M-2 — no full snapshot copy); if the current library no longer contains the `songId`, **persistence is skipped** (no emotion row, no failure record) to avoid orphan emotion/failure rows. Otherwise success does `emotionRepository().upsert(result.emotion)` + `failureRepo.clear(song.id)`; failure does `failureRepo.record(song.id, result.reason)`. **A single-song failure is recorded only, never thrown — it does not abort the batch.**

**Re-enqueue rules** are the anti-infinite-loop core (only the manual task re-enqueues):

<!-- openwiki: mermaid parse failed and this diagram was converted to a text fence so it does not break rendering. Fix the diagram source and restore the mermaid fence. Parser error: Heuristic: a semicolon inside a label breaks rendering; rephrase the label. -->
```text
flowchart TD
    A["A batch round ends"] --> B{"manual task?"}
    B -->|"no (periodic)"| Z["End, wait for next 6h round"]
    B -->|"yes"| C{"attempted > 0 and done == 0?"}
    C -->|"yes, whole batch failed"| Z2["analyzer judged broken, no re-enqueue, avoid loop"]
    C -->|"no"| D{"pausedMidway (user pause mid-run)?"}
    D -->|"yes"| Z3["No re-enqueue; detail-page Continue button restarts"]
    D -->|"no"| E{"pending minus done still > 0?"}
    E -->|"yes"| F["APPEND next 300-song round"]
    E -->|"no"| G["Scan complete"]
```

*Manual batch re-enqueue decision: whole-batch failure does not re-enqueue, mid-run pause does not re-enqueue; any other remaining gap is chained via APPEND.*

`isStopped` truncation (the 10-minute cap) also lands in the re-enqueue path to fill the gap.

## Inference pipeline: EmotionAnalyzer

`EmotionAnalyzer` (`@Singleton`, `:player` module, package `cn.com.dcsgo.mihx.player`) turns any audio Uri into a per-window `(valence, arousal)` curve. The pipeline matches server-side training/evaluation bit-for-bit (probe measured maxDiff=6e-6):

1. **Decode**: `MediaExtractor` selects an audio track → prefers `FfmpegPcmDecoder` (same FFmpeg extension as the playback chain), falling back to vendor `MediaCodec` only for mime-safelisted formats → downmix to mono s16 → linear resample to 16 kHz → `FloatArray` PCM;
2. **Sliding window**: 5s window (`WINDOW_SEC = 5.0`, 80000 samples) / 2.5s hop (`HOP_SAMPLES = 40000`);
3. **In-window inference**: feeds 16384-sample frames (`FRAME`, YAMNet 0.975s @16k) into `emotion_yamnet.tflite`, averaging the 1024-d embeddings of all frames in the window, and accumulating the vocal-class sigmoid scores (`YamnetClassMap.vocalScoreOf`, sum of AudioSet vocal-indices scores) for the whole-song `vocalScore`;
4. **VA head + recalibration**: the mean vector feeds `emotion_va_head.tflite`, emitting a raw `(V, A)`; **valence** is then recalibrated `v' = (v − 0.25) / 0.85` clamped to [-1,1] (zero/slope calibration, v3); **arousal** is **not** taken from the VA head — it is the window RMS loudness mapped `20·log10(rms) ∈ [-30, -5] dBFS → [-1,1]` (physical energy definition, so absolute-energy words like 「燃」/「静谧」 stay reachable);
5. **Summary**: whole-song mean V/A, peak `peakSec = argmax(window arousal) * HOP_SEC` (**peak = positive A/energy peak**), `windowsAnalyzed`, `durationSec`, the whole-song mean YAMNet embedding (1024-d, feedstock for future supervised VA-head retraining), and `vocalScore` (mean of the vocal-class scores across all frames).

The curve stores **raw per-window values** — the raw valence is the recalibrated `v'` sequence; smoothing belongs to the renderer, and the read-time **vocal-component compensation** (`SongEmotion.compensatedCurve`, `VocalCalibration`) further shifts the stored valence by `+0.091` for instrumental songs (`vocalScore ≤ 0.010`) so voting and drawing use a curve with the "is there a singer" component removed (zero-rescan, applied at read). Model files are `player/src/main/assets/emotion_yamnet.tflite` and `player/src/main/assets/emotion_va_head.tflite`; `EmotionAnalyzer.MODEL_VERSION` is generated from `core/model/emotion_model_spec.properties` → `EmotionModelSpec.MODEL_VERSION = "yamnet-va-v5-vocal@edbf6fe5"` (label + asset-fingerprint), the version stamp that drives rescan decisions and selects the anchor table (`EmotionGroup.anchorsFor`).

**Dynamic output-index location**: `embIdx` picks the first output of `yam.outputTensorCount` whose `shape().last() == 1024` — no hard-coded index, so YAMNet export-version or TF-runtime output-order changes require no code change. The two other outputs (`embArr`/`scoreArr`) are reused for the whole song; the vocal scores come from the same `scoreArr` batch (521-class AudioSet sigmoid per frame) that is otherwise discarded, so vocal detection adds zero decode/inference cost.

**Single-lock inference**: decoding runs outside the lock (pure CPU, no shared state); the whole sliding-window inference runs inside `synchronized(lock)`. The reason is documented in a comment: periodic and manual are two distinct unique tasks, WorkManager can run two Workers concurrently, and TFLite `Interpreter` is not thread-safe — concurrent `invoke` would crash natively. Inference and `close()` share the same `lock` to serialize.

### Decode details: FFmpeg first, circuit breaker, watchdog

- **FFmpeg first**: on some ROMs the vendor soft-decode component native-crashes/loops (2026-08-31 real-device SIGSEGV: Xiaomi Snapdragon `c2.qti.alac.sw.decoder` race suicides); the playback chain never touches vendor soft-decode because ExoPlayer prefers the FFmpeg extension decoder via `EXTENSION_RENDERER_MODE_PREFER`. The analysis chain drives the same FFmpeg directly. If `FfmpegPcmDecoder.handles(mime)` matches, FFmpeg is tried first.
- **No-fallback whitelist**: after FFmpeg failure, `mediaCodecFallbackSafe(mime)` permits fallback to MediaCodec only for aac/flac/opus/vorbis; alac/mp3/g711 (the mimes only the vendor soft-decoder can run, exactly where the SIGSEGV/loop arose) return `DECODE_UNSUPPORTED_FORMAT` instead of risking the vendor decoder. Before fallback, `extractor.seekTo(0)` rewinds, since the FFmpeg path already consumed some samples.
- **audio/raw fallback track**: a raw track with no corresponding decoder (some containers sniff true audio as raw/cover-track pitfall) is read directly via `readRawPcm` as s16.
- **Over-length breaker (pre-decode)**: if track `KEY_DURATION` exceeds `MAX_DURATION_US` (20 minutes) → `TOO_LONG` directly without starting decode; `durationUs <= 0` (unknown) is not blocked and is left to mid-decode truncation and OOM fallback.
- **Memory budget circuit breaker (v3.11.0)**: `maxSamplesForSrc(srcSr, memoryBudgetBytes)` caps `ShortAccum` capacity per source sample rate. `ShortAccum` silently drops samples at the cap; the caller rejects the song via `truncationReason`, which distinguishes **"too long"** (`TOO_LONG`) from **"memory over budget"** (`TOO_LARGE_FOR_DEVICE`) by which of the two limits is tighter. The old static 192 kHz worst-case constant never fired at 44.1 kHz and a 36-minute compilation OOMed first (2026-09-04 device log).
- **Watchdog**: the decode loop has a 60s (`MAX_DECODE_MS`) wall-clock deadline → `DECODE_TIMEOUT`, abandoning the song.

### Heap-adaptive decode memory budget (v3.11.0)

Both `EmotionAnalyzer` and `FfmpegPcmDecoder` share the same heap-adaptive budget, computed once at construction:

```
memoryBudgetForHeap(maxHeapBytes) =
    (maxHeapBytes - 64MB reserved).coerceIn(96MB, 512MB)
```

- 256 MB heap → 192 MB (same as the old fixed value, so low-end behavior is unchanged);
- 384 MB heap → 320 MB; 512 MB heap → 448 MB — larger heaps automatically relax the cap to fit longer 192 kHz files;
- clamped to a `[96MB, 512MB]` range against extreme heap values.

The budget governs **decode buffer + resampled output combined**, with two sample limits (`samplesLimits`):

1. **Duration limit**: 20 minutes × source sample rate (the true "too long" boundary);
2. **Memory limit**: steady-state peak — `ShortAccum` (2 B/sample) + 16 kHz resampled output (4 B / 16 k samples) = samples × (2 + 64000/rate) bytes.

For sample rates **above 64 kHz** (`TRANSIENT_DOMINANT_RATE`), the doubling **transient peak** also binds: the final `copyOf` doubling keeps the old array (samples × 1 B) and the new array (samples × 2 B) alive together = samples × 3 B, which exceeds steady state when `3 > 2 + 64000/rate` (i.e. rate > 64 kHz). That transient peak was the root cause of the 192 kHz decode OOM (real device `copyOf` allocating 172 MB). When the transient dominates, the memory limit takes `min(steadyLimit, transientLimit)` with a safety margin (×2/7 ≈ /3.5) covering reserved-base deviations. At 192 MB budget: 44.1 k/48 k still bound by the 20-minute duration limit (behavior unchanged), 96 k → 7.5 min, 192 k → 3.3 min deterministic cutoff, no OOM.

The budget's intent: a 192 kHz file under 20 minutes that would previously have been misreported as "too long" (or worse, OOMed) is now deterministically classified as `TOO_LARGE_FOR_DEVICE`.

### OOM caught as Error

The outermost `catch (oom: OutOfMemoryError)` in `analyze` must be separate from `catch (e: Exception)` — OOM is an `Error`, not an `Exception`. The 2026-09-04 device log showed a long compilation whose `ShortAccum` grow of 245 MB failed and was misreported by the Worker fallback as `INFERENCE_ERROR`. **v3.11.0 now classifies it as `TOO_LARGE_FOR_DEVICE`** (not `TOO_LONG`), since with pre-decode duration/memory gating an OOM reaching this catch usually means a file with unknown `durationUs` or an extreme high-sample-rate file.

## Memory discipline (real-device OOM 2026-08-30, hard constraint)

> **PCM data must always live in primitive-type arrays; boxed collections are forbidden. This is a hard constraint when modifying the decode path.**

The lesson is recorded verbatim in the `EmotionAnalyzer` and `ShortAccum` KDoc: a former `ArrayList<Short>` (initial capacity srcSr×200) boxed sample-by-sample; 4 min @ 44.1 k stereo = 10.58 million Short objects (≈16 B each) + 70 MB reference array ≈ 240 MB, which combined with the inference output arrays blew the 256 MB heap; after GC recovered nothing, even a 2.5 KB allocation on the ExoPlayer thread OOMed and killed the process.

Current implementation: the decode side uses `ShortAccum` uniformly — a primitive `ShortArray` with doubling growth, `maxCapacity` passed at construction (per `maxSamplesForSrc`), silently dropping samples at the cap with a `truncated` flag for the caller to reject the song. The inference side follows the same discipline: output arrays (`embArr`/`scoreArr`) are reused once for the whole song (the old per-frame `new` allocated ~6 MB of short-lived objects per song, amplifying GC pauses under heap pressure), and the whole-song embedding accumulator is a `DoubleArray`. The resample output `FloatArray` math uses Long to prevent overflow. Any boxed collection introduced into a new decode path re-buries this OOM regression.

## Failure handling: EmotionFailureStore and the "cannot analyze" section

The `EmotionFailureReason` enum maps 1:1 to the design doc's "song analysis skipped" list, each with a user-readable message:

| Reason | Meaning |
| --- | --- |
| `EXTRACT_FAILED` | SAF permission revoked / file moved or deleted / container damaged |
| `NO_AUDIO_TRACK` | no audio track in the container |
| `TOO_SHORT` | decoded PCM shorter than one analysis window (≈ <5 s) |
| `TOO_LONG` | over-length breaker (>20 min, prevents decode memory burn) |
| `TOO_LARGE_FOR_DEVICE` | sample rate too high (e.g. 192 kHz) or file too large; decode memory exceeds the on-device budget (v3.11.0) — transcode to a lower sample rate and re-import |
| `DECODE_TIMEOUT` | single-song decode watchdog 60s timeout |
| `DECODE_UNSUPPORTED_FORMAT` | FFmpeg-only mime with no fallback / no matching decoder (transcode to AAC/FLAC and re-import) |
| `INFERENCE_ERROR` | TFLite inference exception |

**Storage**: `:data`'s `EmotionFailureStore` (DataStore preferences, single-key JSON: `{"<songId>":{"reason":..,"failedAt":..,"attempts":..}}`, corrupted entries skipped by parse). `record` increments `attempts+1` and overwrites reason/failedAt; `clear` removes an entry after success; `clearForRetry` wipes entries (attempts restart from zero). DataStore is used over Room because the volume is just individual songs — no schema change.

**Retry policy**: a song with `MAX_ATTEMPTS = 3` cumulative failures is no longer retried automatically (pending filtering excludes it), but the **detail page can still retry manually** — `EmotionViewModel.retryFailedSongs()` clears all failure records + clears the pause flag + `enqueueUniqueManualScan(APPEND)`; the Worker's pending filter naturally re-admits those songs.

**UI**: the analysis detail page's "cannot analyze" section lists failed songs: reason text + failure count, a "manually tagged" check-badge and marked words, per-song "tag/modify" entry (reusing `EmotionCalibrateDialog`'s words-only mode), plus "add to playlist" and "retry failed songs" buttons. The badge state is read via `EmotionViewModel.calibratedTagsOf(songId)`.

**R1-risk linkage**: low analysis coverage directly shrinks the mood time-slot candidate pool (see consumption); failed songs stay out of the pool until fixed or manually tagged — the other side of the coverage signal.

## State machine & display: EmotionViewModel

`EmotionViewModel` (`@HiltViewModel`, shared single instance between the My-library emotion card and the detail page) is a **pure-Flow state machine**, not dependent on "query on page entry":

- **Lazy observation (idempotent `observe()`)**: previously `init` collected unconditionally, so cold startup made emotion table queries even when no emotion page was open (2026-10-06: raced `PlayerViewModel`'s `loadSongs`, breaking playback queue/progress recovery). Now pages consuming `status`/`rows` call idempotent `observe()` on visibility (returns immediately if a job is already active); once any emotion page is opened, it stays resident in VM scope.
- **Signal source**: `combine(tick, manual-task WorkInfo flow, periodic-task WorkInfo flow, emotionScanPaused DataStore flow)`. For the manual task, **`RUNNING` or `ENQUEUED` both count as "scanning"** — clicking gives instant feedback (RUNNING-only would make the button feel unresponsive); after a process-kill restart, WorkManager restores queued/running state and pushes here automatically — this is the foundation of "process-killed auto-resume push". The current song title comes from the last `RUNNING` task's `progress[KEY_PROGRESS_CURRENT]`.
- **Cadence**: `scanSignal.flatMapLatest` — while scanning, rebuild status every 2 s (workInfo/progress pushes also trigger instantly); when idle, build once.
- **Repository reads all on `Dispatchers.Default`**: `SongEmotionsRepository` (and siblings) bridge to `Dispatchers.IO`; direct main-thread calls would block the UI. Read items are **aggregates only** (M-3, O(1) per poll instead of materializing whole tables): library total (`countSongs`), `tableStat()` (row count + max analyze time), `recentAnalyzedTimestamps(20)`, `correctionCount()`, `currentFailures()`.
- **Duration stats**: adjacent-success timestamp deltas approximate per-song duration; gaps >10 minutes are treated as inter-batch idle and excluded; yields `lastSongMs`/`avgSongMs` (avg over the recent window).
- **Rows rebuild fingerprint**: `total|stat.count|correctedCount|paused|tick|contentFingerprint` (content hash = row count + max analyze time + failures hash); unchanged fingerprint does not rebuild the flattened list (analyzed songs + display words + calibration markers); a failed build keeps the old key so the next tick retries.
- **Operations**: `pauseScan` (set pause flag + `cancelUniqueWork(emotion_manual_scan)` cancel the whole chain), `resumeScan` (i.e. `startManualScan`), `retryFailedSongs`, `refresh()` (manual refresh trigger after data changes like calibration saves).

**Pause semantics (cooperative)**: `emotionScanPaused` settings flow is subscribed once at batch start and observed in-loop via an `AtomicBoolean`; the Worker checks the flag before the batch and before each song — after a pause **the in-flight song finishes, then the scan stops** (UI hints "will pause after the current song finishes"); queued re-scan tasks are canceled directly (`cancelUniqueWork(emotion_manual_scan)`); resuming is triggered by the detail page's "Continue" button calling `startManualScan`.

## Consumption

### Library "Emotions" tab

`EmotionViewModel.rows` (song + emotion + display words + calibration marker) is flattened again via `AppNavHost` into `EmotionSongUiRow` passed to `PlaylistScreen`'s `LibraryTab.EMOTIONS` → `EmotionLibraryView`: word-chip multi-select filtering (options derived from all rows' words, de-duplicated and sorted), analyzed/manually-tagged section counts, multi-select batch add-to-playlist, sort by "calibrated first → has-words first → title"; the empty state guides "go to My Library → Song Emotion Analysis → Scan Now".

### Song detail: SongEmotionSection

`SongEmotionSection` renders the V/A dual curve (blue = valence/cold-warm, orange = arousal/energy):

- **Smoothing**: `smoothCurve` window size 5, shrink-window at boundaries, length <3 returned as-is — removes single-window noise;
- **Peak red dot**: only when `hasSignificantPeak` (max A > A mean + `PEAK_MARGIN = 0.5`; the threshold is calibrated against 20 measured songs: flat/always-energetic ≤ 0.49, real-peak songs ≥ 0.52) — positioned by `peakSec/durationSec` for x and `peakSec/2.5` for window index into the smoothed value; if not significant, shows "peak: not obvious";
- **user-only rows** (manual tags on an analysis-failed song: empty curve but `userTags` set) are **not early-returned** — shows the word list with the note "tagged by you (automatic analysis failed; your tags take precedence)", only the curve graphic is replaced by words;
- **No analysis row** shows "emotion analysis: not yet done (analyzes the library automatically while charging)".

### User calibration write-back chain

The app-wide "not fitting? tag" entry uses a `CompositionLocal`: `AppRoot` provides `LocalEmotionCorrectionController` at NavHost top via `CompositionLocalProvider`, so any page rendering a song-detail dialog (library/artist/album/local-music/player/detail) gets the capability automatically. The implementation object delegates save to `AppMediaMetadataViewModel.saveEmotionCorrection(songId, words)`:

1. **Empty words = clear the mark**: requires an existing analysis row (returns false otherwise), via `clearCorrection` (user three fields set NULL; a fully-empty user-only row is deleted so it is not mistaken for analyzed);
2. **Non-empty words**: `EmotionGroup.groupOf` validates each word (out-of-vocabulary words return false — `avgOfWords` is not used as validator because it silently drops invalid words), then `saveCorrection` — **only the words are persisted** (coordinates are derived read-time from the row's own curve coordinate system, so they don't go stale on a coordinate change); the DAO does an atomic `UPDATE` of the user fields first (never touching model columns), and only if no row exists INSERTs a user-only row (`curveJson = "[]"`, `windowsAnalyzed = 0`, `modelVersion = "user-only"`) — analysis-failed songs can still be manually tagged;
3. **`edited` flag**: `isUserCorrection(modelTagsOf(it), words)` detects whether the picked words differ from the model's automatic tags (using `modelTagsOf`, not `emotionTagsOf` — the latter returns the user's own words on a calibrated song, so the comparison would always read "unchanged"); only true (distinct) choices count as independent corrections for future supervised retraining.

On save success the callback calls `emotionViewModel.refresh()` to trigger Flow refresh; on failure it toasts "this song hasn't finished analysis yet". `SongEmotionSection` also does local override (the dialog shows the new words immediately after save).

**Rescan does not swallow calibration**: `SongEmotionsRepository.upsert` uses an atomic `UPDATE ... PreservingUserTags` SQL (merging is sunk into SQL to close a TOCTOU window — a user calibration landing between the read and the write can never be silently overwritten), inheriting the old `userValence/userArousal/userTags` when the new record carries no user calibration and the old row was calibrated — a model-upgrade rescan never wipes user conclusions. It INSERTs only when the songId has no row at all.

**Read path never decodes embedding (P0)**: the bulk consumers `getAll`/`observeAll` read a lightweight projection (`SongEmotionLite`) that sets `embedding = null` — embedding is still persisted at analysis time as the future supervised VA-head retraining feedstock (~5.3 KB/song base64+float32) and is only materialized by the single-row `get(songId)` via `SongEmotionEntity.toDomain()`. Curve parsing for these bulk reads is memoized in a `parsedCurveCache` keyed by `analyzedAt` (Room re-emits the whole table on any change; only the newly-written row re-parses its `curveJson`).

### Feeding mood time-slot shuffle

Calibration and analysis results eventually feed the mood time-slot shuffle decision data:

- `PlayerRuntime` continuously subscribes to `songEmotionRepository.observeAll()` and maps each row through `emotionMatchRatiosOf` into volatile `moodEmotionRatiosCache` (plus a vote version `exploreVoteRatiosCache`), exposed to the mood-slot planner via `moodEmotionTagsOf` — **user calibration therefore flows through automatically** (`emotionMatchRatiosOf`/`emotionTagsOf` prioritize user words);
- `PlayerRandomQueueFacade` at the random-queue entry uses `filterByMoodTags` to restrict the candidate pool to songs carrying any word of the active slot; zero matches returns null → falls back to full-library random (§4.3 degradation); pool < `MoodSlotPolicy.POOL_WARN_THRESHOLD(10)` logs a warning that it will cycle;
- `MoodTimeSlotViewModel.loadStats` (computed once on entering the config page) uses the same `emotionMatchRatiosOf`/`emotionTagsOf` to count songs per word (chip badges) and matches against `MoodSlotPolicy.MATCH_MIN_RATIO` — **this is the config page's coverage signal**: low analysis coverage directly shrinks the candidate pool, matching design-doc risk R1 (config page shows per-word song counts + small-pool fallback + guides the user to run emotion analysis first).

## Constants & operations quick reference

| Item | Value | Location |
| --- | --- | --- |
| Periodic task unique name | `emotion_periodic_scan` (6h, KEEP) | `EmotionScanScheduler` |
| Manual task unique name | `emotion_manual_scan` (one-time, KEEP/APPEND) | `EmotionScanWorker` |
| Periodic / manual batch | 40 / 300 songs per round | `EmotionScanWorker` |
| Auto-retry cap | `MAX_ATTEMPTS = 3` (detail page can still retry) | `EmotionScanWorker` |
| Model version | `yamnet-va-v5-vocal@edbf6fe5` (rescan verdict stamp; generated `EmotionModelSpec`) | `EmotionAnalyzer` / `EmotionGroup` |
| Sampling params | 16k mono, frame 16384, window 5s, hop 2.5s | `EmotionAnalyzer` |
| Valence calibration | `v' = (v − 0.25) / 0.85`, clamp [-1,1] | `EmotionModelSpec.V_CAL_BIAS/SCALE` |
| Arousal mapping | `20·log10(rms)` dB → `[-30, -5] dBFS → [-1,1]` | `EmotionModelSpec.DB_FLOOR/DB_CEIL` |
| Vocal compensation | valence reads `+0.091` shift when `vocalScore ≤ 0.010` | `VocalCalibration` |
| Auto-rescan causes | stale `modelVersion` **or** missing `vocalScore` | `EmotionScanWorker` |
| Over-length breaker | 20 min (pre-decode durationUs + mid-decode per-sample-rate) | `EmotionAnalyzer` |
| Decode watchdog | 60s (`MAX_DECODE_MS`) | `EmotionAnalyzer` |
| Decode memory budget | `(maxHeap − 64MB).coerceIn(96MB, 512MB)`; transient binds >64kHz | `memoryBudgetForHeap` |
| Peak verdict | A peak > A mean + `PEAK_MARGIN = 0.5` | `SongEmotionSection` |
| Calibration cap | `EmotionGroup.MAX_TAGS = 4` words | `EmotionGroup` |
| Small-pool warning | `POOL_WARN_THRESHOLD = 10` | `MoodSlotPolicy` |

Measured inference cost is ~120 ms/window; `analyze` is CPU-intensive and the caller must run it on a background thread (the Worker's `CoroutineWorker` context satisfies this).

## Tests

- `core/ui`'s `SongEmotionSectionTest` locks the display-layer pure-function contracts: `smoothCurve` length-preserving noise-reduction and short-table passthrough, `hasSignificantPeak` significance boundaries, `EmotionGroup` voting/anchor math/WITTY exclusion from auto-vote, `emotionTagsOf` user-words override the model;
- `feature/player`'s `PlayerRandomQueueFacadeTest` locks the consumer-side word filtering: matching a slot only picks from tagged songs, zero-match falls back to full library (log line "fall back to full library"), and behavior is unchanged when the slot is inactive;
- `player`'s `EmotionAnalyzerLimitsTest` (v3.11.0) locks the decode memory-budget math: low sample rates (44.1k/48k) bound by the 20-minute duration limit, high sample rates (96k/192k) bound by memory before duration, `samplesCutoffReason`/`truncationReason` correctly distinguishing `TOO_LONG` vs `TOO_LARGE_FOR_DEVICE`, `memoryBudgetForHeap` clamping across heaps, and the doubling-transient binding at 192 kHz;
- The batch Worker and `EmotionFailureStore` still have no dedicated unit tests — decisions tying the Worker to the analyzer/failure store are indirectly bracketed by these end-to-end tests, so decode/scheduling changes still need on-device regression, especially for OOM/long-compilation/192 kHz scenarios (historically found twice via real-device log forensics).

## Related pages

- `/openwiki/concepts/emotion-model.md` — SongEmotion domain model, vocabulary voting & calibration write semantics
- `/openwiki/architecture/data-persistence.md` — Room database & migrations overview (`song_emotions` table)
- `/openwiki/concepts/mood-time-slot.md` — mood time-slot shuffle configuration & decision
- `/openwiki/player/service-and-controller.md` — playback chain (same FFmpeg extension decoder)
