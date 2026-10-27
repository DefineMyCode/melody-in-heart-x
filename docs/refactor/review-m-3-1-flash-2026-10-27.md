# 代码评审报告 — melody-in-heart-x

| 项目 | 内容 |
|------|------|
| 评审日期 | 2026-10-27 |
| 评审模型 | MiniMax M3.1 Flash |
| 代码版本 | versionName 3.12.0 (versionCode 48)，`main` 分支工作区（基线提交 `58a365e`） |
| 评审范围 | 全部 15 个模块（`:app` / `:core:*` / `:domain` / `:data` / `:player` / `:feature:*` / `:benchmark` / `:buildSrc`），291 个主源码文件、52,613 行 Kotlin 主源码、105 个测试文件、695 个测试用例 |
| 评审重点 | **性能优化** 与 **代码质量**（用户指定） |
| 评审方法 | 逐文件静态审查 + 专项排查（协程/线程模型、Compose 重组、Room 索引与查询、内存分配）+ 构建配置与架构门禁审计 + 测试质量抽查 + **对每一条候选问题做源码级二次验证**（引用行号均已回读原文确认） |
| 上期对照 | `review-glm-5-3-flash-2026-09-03.md`（v3.5.1 / versionCode 29）——本次逐条复核其遗留项的整改状态，见 §5 |

> **结论摘要**：本期架构治理成效显著——上期标记为 Critical 的「导入无异常处理」「`produceState` 未捕获异常」两条已彻底修复；`runBlocking` 从 domain 契约层清零至仅剩 `:player` 一个类；每秒整壳重组已由窄流（narrow flow）方案根治；测试密度（695 用例 / 0 跳过）保持全项目领先。
> 债务形态已整体迁移到**新功能域**：情绪分析（2026-10 新增的批扫 / 探索播放 / 词条计票）贡献了本报告 **23 条 Major 中的 8 条**（M-1/M-2/M-3/M-4/M-11/M-15/M-22/M-23），且其中多条互为因果（整表重发 → 全库重解析 → 重复投票）。
> 本次共发现 **Critical 1 条 / Major 23 条 / Minor 22 条**，无一条来自旧功能域的回归。

---

## 0. 评审总览

| 维度 | 评级 | 一句话结论 |
|------|------|-----------|
| 1. 架构与设计模式 | A | 多模块 Clean Architecture + 机器门禁（`verifyProductArchitecture`）持续有效；facade 拆分彻底；**唯一硬伤是 `:core:model`/`:domain` 仍引 `android.*`，而门禁恰好不覆盖这一条** |
| 2. 可维护性与代码风格 | B+ | 注释密度与「为什么这么写」的沉淀极高（大量历次评审的踩坑记录）；扣分项是超长参数列表（46/34/31）、Route Actions 未标 `@Stable`、以及 4 处遗留的时序注释错误 |
| 3. 性能与内存 | B | **上期的三大热点（runBlocking 泛滥 / 锁内长 IO / 每秒整壳重组）已全部根治**；新债务集中在情绪链路：整表重发、全库重复解析、组合期 O(n) 扫描 |
| 4. 正确性与数据安全 | B+ | 原子 SQL 增量、`@Transaction`、`SupervisorJob` 等防护到位；仍存在 **2 处读改写丢更新** 与 **1 处 TOCTOU 可覆盖用户校准** |
| 5. 稳定性 | B+ | 崩溃路径基本收敛；**唯一 Critical 是 7 处 `LaunchedEffect` 未捕获 Room 异常**（其中 1 处已做防护，形成不一致） |
| 6. 可观测性 | C | **`PerformanceTrace` 的 release 白名单机制实际不产生任何输出，且全项目零调用点**——文档承诺的线上性能可观测性并不存在 |
| 7. 国际化与本地化 | D | **1,126 处硬编码中文 vs 全项目 4 条 `strings.xml` 资源**，`resConfigs("zh","en")` 中的 "en" 形同虚设（上期遗留，未动） |
| 8. 测试与健壮性 | A- | 695 用例、13,509 行测试代码、**0 个 `@Ignore`/`@Disabled`**；断言真实行为而非形式化；缺口在并发与异常路径 |
| 9. 构建与配置 | A- | 版本目录统一、R8 + 资源收缩 + ABI 裁剪 + Baseline Profile 到位；仅 Room 版本号文档漂移 |

**发现统计：Critical 1 项 / Major 23 项 / Minor 22 项**

---

## 1. Critical（必须立即修复）

### 🔴 C-1 `loadSongInfo` 在 7 处 `LaunchedEffect` 中未捕获异常 —— Room 异常直接崩溃进程

- **问题描述**：`loadSongInfo` 是一个挂起函数，其实现链末端有一次**未受保护的 Room 读**：

  ```kotlin
  // data/src/main/java/cn/com/dcsgo/mihx/data/repository/MediaMetadataRepository.kt:25-30
  override suspend fun songInfo(song: Song): SongInfo? = withContext(Dispatchers.IO) {
      song.uri?.let { uri ->
          AudioMetadataExtractor.extractFullMetadata(context, uri)
              ?.copy(emotion = emotionRepository.get(song.id))   // ← 这一行在 extractor 的 try/catch 之外
      }
  }
  ```

  ```kotlin
  // data/src/main/java/cn/com/dcsgo/mihx/data/repository/SongEmotionsRepository.kt:29-31
  override suspend fun get(songId: Int): SongEmotion? = withContext(Dispatchers.IO) {
      melodyDao.songEmotion(songId)?.toDomain()   // ← 无 catch，SQLiteException 直接上抛
  }
  ```

  `AudioMetadataExtractor.extractFullMetadata` 内部**自己**吞异常（`AudioMetadataExtractor.kt:211`），所以提取阶段是安全的；但 `.copy(emotion = ...)` 发生在它**返回之后**，完全裸露。`SQLiteFullException` / `SQLiteCorruptException` / 磁盘 IO 错误会从 `songInfo()` 一路抛到 `LaunchedEffect` 的协程里。

  `LaunchedEffect` 运行在组合作用域，其未捕获异常会交给主线程默认 handler → **进程崩溃**，不是被吞掉的错误。

- **不一致的证据**：同一个调用，项目自己在 app 壳层已经做了防护，注释里点名了风险：

  ```kotlin
  // app/src/main/java/cn/com/dcsgo/mihx/app/player/NowPlayingSurface.kt:66-71
  // m6（评审 2026-09-03）：底层走 Room runBlocking 桥，DB 异常会让协程崩溃，这里兜底。
  songInfo = runCatching { songForInfo?.let { loadSongInfo(it) } }
      .onFailure { AppLog.error("NowPlayingSurface", "loadSongInfo failed: ${it.message}", it) }
      .getOrNull()
  ```

  **8 个调用点里，只有这一个加了防护**，其余 7 处裸调：

  | # | 位置 | 代码 |
  |---|------|------|
  | 1 | `feature/playlist/.../PlaylistScreen.kt:166` | `songInfo = songForInfo?.let { loadSongInfo(it) }` |
  | 2 | `feature/playlist/.../LocalMusicManagementView.kt:141` | `songInfo = infoSong?.let { loadSongInfo(it) }` |
  | 3 | `feature/playlist/.../AlbumDetailScreen.kt:105` | `songInfo = songForInfo?.let { loadSongInfo(it) }` |
  | 4 | `feature/playlist/.../ArtistDetailScreen.kt:99` | `songInfo = songForInfo?.let { loadSongInfo(it) }` |
  | 5 | `feature/playlist/.../EmotionLibraryView.kt:324` | `if (infoSong != null) songInfo = loadSongInfo(infoSong)` |
  | 6 | `feature/user/.../VersionManagementScreen.kt:174` | `songInfo = songForInfo?.let { loadSongInfo(it) }` |
  | 7 | `feature/user/.../VersionComparisonScreen.kt:67` | `loadSongInfo(song)?.let { info -> put(song.id, info) }` |

- **影响范围**：用户点击任意歌曲行的「查看歌曲详情」即触发。触发条件是 Room 异常（磁盘满、DB 损坏），属于**低频但致命**——一旦发生是应用直接闪退，且崩溃点与用户操作相距很远，排查成本高。
- **修复建议**：
  1. 最小改动：7 处按 `NowPlayingSurface.kt:67` 的既有写法逐个包 `runCatching`。
  2. 彻底做法：把兜底下沉到**唯一的实现方**，让 `MediaMetadataRepository.songInfo` 自己吞掉 Room 异常并返回 `null`（详情页显示「无法读取元数据」即可）——这样 8 个调用点一次性免疫，且不会再有人新增裸调点。
  3. 配套：在 `core:ui` 提供 `rememberSongInfoLoader(loadSongInfo)` 共享 Composable，把「加载 + 兜底 + 日志」收敛到一处。

---

## 2. Major（高优先级）

### 性能类

#### 🔶 M-1 情绪表每次「单行写入」触发整表重发 + 全量曲线 JSON 重解析 —— 批扫期间 O(N²)

- **位置**：`data/.../repository/SongEmotionsRepository.kt:45-48`（消费端）、`:150-157`（解析）、`data/.../local/dao/MelodyDao.kt:413-418`（查询）
- **证据**：

  ```kotlin
  // SongEmotionsRepository.kt:45-48
  override fun observeAll(): Flow<Map<Int, SongEmotion>> =
      melodyDao.observeAllSongEmotions().map { rows ->
          rows.associate { it.songId to it.toDomainLite() }   // 每行都走 parseCurve()
      }
  ```
  ```kotlin
  // SongEmotionsRepository.kt:150-157 —— 每行一次 JSONArray 解析
  private fun parseCurve(curveJson: String): List<Pair<Float, Float>> = try {
      val arr = JSONArray(curveJson)
      List(arr.length() / 2) { i -> arr.getDouble(i * 2).toFloat() to arr.getDouble(i * 2 + 1).toFloat() }
  } catch (_: Exception) { emptyList() }
  ```
  DAO 注释本身就承认了这一点（`MelodyDao.kt:410`）：*「批扫每写一首都会触发整表重发」*。

- **影响**：Room 的失效跟踪器在 `song_emotions` 表任意一行变化时**重发整表**。批扫是「一首一首写」（`EmotionScanWorker.kt:100` 逐首 `upsert`），于是：
  - 手动批扫 300 首 × 曲库 1000 首 → **300 次整表读取 + 30 万次曲线 JSON 解析**；
  - 消费端 `PlayerRuntime.kt:447-464` 还要为每首歌算 **两份** 词条分布（匹配用 / 计票用），全在 `Dispatchers.Default` 上抢 CPU；
  - 这些 CPU 与 ExoPlayer 解码、以及用户正在听的歌**抢同一个进程资源**，表现为听歌卡顿、耗电上升。
- **修复建议**：
  1. 消费端加 `debounce(500)`（`PlayerRuntime` 侧或仓库侧），把连续写入合并成一次重算；
  2. 仓库侧维护「已解析曲线缓存」，键为 `songId + analyzedAt`，未变的行在重发时**直接复用**而不重新 `JSONArray` 解析；
  3. 更彻底：批扫 Worker 改为**批量写**（攒 N 首一次性 upsert），从源头把「N 次重发」降到「1 次重发」。

#### 🔶 M-2 批扫 Worker 每分析一首歌就复制一整份曲库 + 逐首读一次 DataStore

- **位置**：`app/.../emotion/EmotionScanWorker.kt:96`、`:111`、`:74`、`:143-147`
- **证据**：

  ```kotlin
  // EmotionScanWorker.kt:96 与 :111 —— 写在 per-song 循环体内部
  if (ep.musicRepository().observeSongsSnapshot().none { it.id == song.id }) {
      AppLog.info(TAG, "analyzed [$attempted]: ${song.title} gone from library, skip persist")
      continue
  }
  ```
  ```kotlin
  // data/.../repository/MusicRepository.kt:239-241
  fun getSongs(): List<Song> = lock.read { songs.toList() }        // ← 整库拷贝
  fun observeSongsSnapshot(): List<Song> = getSongs()
  ```
  ```kotlin
  // EmotionScanWorker.kt:74 + :143-147 —— 每首歌都重读一次 DataStore
  if (isPaused()) { ... }
  ...
  private suspend fun isPaused(): Boolean = runCatching {
      EntryPointAccessors.fromApplication(...).settingsRepository().emotionScanPaused.first()
  }.getOrDefault(false)
  ```

- **影响**：手动批扫 300 首 → **300 次整曲库 `toList()` 拷贝**（1000 首 ≈ 每次 8KB 引用数组 + 1000 次引用计数操作，累计约 24 万次）+ 每次一个 O(n) 的 `.none {}` 线性扫描 + **300 次 DataStore 磁盘读**。这与 M-1 叠加，使批扫期间的后台开销进一步放大。
- **修复建议**：
  1. 循环外取一次 `val libraryIds = songRepository.observeSongsSnapshot().mapTo(HashSet()) { it.id }`，循环内只做 O(1) 查表；
  2. `isPaused()` 改为在循环外 `settingsRepository.emotionScanPaused` 收集成一个局部 `var`，循环内读变量（协程式暂停本就是协作式，无需每首重读磁盘）；
  3. 更优：改用 `SongRepository.observeSongs()` 的 Flow 订阅，删除变化自动反映，无需轮询式快照。

#### 🔶 M-3 `EmotionViewModel` 扫描期间每 2 秒拉取两次「全表」

- **位置**：`app/.../emotion/EmotionViewModel.kt:140-145`（轮询）、`:163-173`（5 次仓库读）
- **证据**：

  ```kotlin
  // EmotionViewModel.kt:140-145
  flow {
      while (true) {
          emit(buildStatus(sig))
          delay(2_000)      // ← 2 秒一次
      }
  }
  ```
  ```kotlin
  // EmotionViewModel.kt:163-173 —— 每次轮询 5 次仓库读，其中 2 次是全表
  val versions = runCatching { emotionRepository.analyzedVersions() }.getOrDefault(emptyMap())
  val timeline = runCatching { emotionRepository.analyzedTimeline() }.getOrDefault(emptyList())
  ```
  对应 SQL（`MelodyDao.kt`）：
  ```sql
  SELECT songId, modelVersion FROM song_emotions
  SELECT analyzedAt FROM song_emotions WHERE windowsAnalyzed > 0 ORDER BY analyzedAt ASC
  ```

- **影响**：一次 300 首手动批扫若耗时 20 分钟 → **600 次轮询 × 2 次全表查询 = 1200 次整表扫描**，累计物化约 120 万行；同时 `:189` 还要对 1000 项的 Map 与 List 各算一次 `hashCode()` 作为指纹。数据量越大越贵，且**在用户听歌期间持续发生**。
- **修复建议**：
  1. 计数与校准数改用 `COUNT(*)` 聚合（`emotionCorrectionCount` 已经是了，`analyzedVersions` 应同样提供 `COUNT` 版本）；
  2. `timeline` 只用于算「平均单首耗时」，可改为只取最近 N 条（`ORDER BY analyzedAt DESC LIMIT 20`）；
  3. 轮询间隔从 2s 放宽到 5~10s（WorkManager 的 `setProgress` 本就会推 UI，2s 轮询其实是冗余的）。

#### 🔶 M-4 探索补歌池对**全库**做 3 遍词条派生，每次探索补歌触发一次

- **位置**：`feature/player/.../PlayerRandomQueueFacade.kt:312`、`:331`、`:340`；派发端 `PlayerRuntime.kt:505`
- **证据**：

  ```kotlin
  // PlayerRandomQueueFacade.kt:312 —— 第 1 遍：全库
  (songs.flatMap { moodEmotionTagsOf(it) }).distinct().forEach { word -> ... }
  ```
  ```kotlin
  // PlayerRandomQueueFacade.kt:330-333 —— 第 2 遍：候选池
  val likedAndNoDislike = candidates.filter { song ->
      val tags = moodEmotionTagsOf(song)                       // ← 再次调用
      tags.any { it in likedWords } && tags.none { it in dislikedWords }
  }
  // PlayerRandomQueueFacade.kt:339-341 —— 第 3 遍：候选池
  val noDislike = candidates.filter { song ->
      moodEmotionTagsOf(song).none { it in dislikedWords }     // ← 又一次
  }
  ```
  关键在于 `moodEmotionTagsOf` 的实现（`PlayerRuntime.kt:505`）：
  ```kotlin
  moodEmotionTagsOf = { song -> moodEmotionRatiosCache[song.id]?.keys?.toList().orEmpty() },
  ```
  —— 每次调用都**新建一个 List**，且底层 `emotionTagsOf` 会触发 `EmotionGroup.headlineTagsFor(emotion.compensatedCurve, ...)`，而 `compensatedCurve` 是个**未缓存的 getter**（`core/model/.../SongEmotion.kt:70-71`），每次访问都 `curve.map { (v,a) -> (v+shift) to a }` 重建整条曲线。
- **影响**：探索播放每约 5 首触发一次补歌 → 每次对全库做 3 遍「取词条 + 重建曲线 + 分配 List」。1000 首曲库 × 3 遍 × 约 100 窗曲线 = 单次补歌约 30 万次 Pair 分配。**这条路径在播放链路上**，用户能直接感到卡顿。
- **修复建议**：
  ```kotlin
  // 一次派生，全处复用
  val tagsBySong = songs.associate { it.id to moodEmotionTagsOf(it) }
  val likedWords = tagsBySong.values.flatten().distinct().filter { wordPreferenceOf(it).kind == LIKE }.toSet()
  // ...后续过滤全部走 tagsBySong[song.id]
  ```
  更根本：`compensatedCurve` 与 `emotionTagsOf` 应缓存到领域模型上（见 M-11 的同一根因）。

#### 🔶 M-5 `songs.albumId` 缺索引 —— 专辑目录与孤儿清理全表扫描

- **位置**：`data/.../local/entity/SongEntity.kt:7-10`；查询 `MelodyDao.kt:165-178`、`:302-303`
- **证据**：

  ```kotlin
  // SongEntity.kt:7-10 —— 只有 uri 一个索引
  @Entity(
      tableName = "songs",
      indices = [Index(value = ["uri"], unique = true)],
  )
  ```
  导出的 schema `data/schemas/.../16.json` 确认：`songs pk=[id] indices: uri`。
  ```sql
  -- MelodyDao.kt:173
  FROM albums al LEFT JOIN songs s ON s.albumId = al.albumId
  -- MelodyDao.kt:302
  DELETE FROM albums WHERE albumId NOT IN (SELECT DISTINCT albumId FROM songs WHERE albumId IS NOT NULL)
  ```
- **影响**：`albumCatalog()` 是曲库浏览页背后的查询；而 `deleteOrphanAlbums()` 在**每次 persist**（`MelodyDao.kt:379`）以及每次删歌 / 去重 / 文件校验清理时都会跑（`MusicRepository.kt:974-975`），每次都是 `songs` 表的全表扫描 + `DISTINCT`。
- **修复建议**：`SongEntity` 增加 `Index(value = ["albumId"])`，并补 `MIGRATION_16_17` 创建 `index_songs_albumId`（同时更新 `16.json` 之外新增 `17.json`，既有 JSON 保持不可变）。

#### 🔶 M-6 `playback_events.songId` 未建索引，且该表**永不清理**

- **位置**：`data/.../local/dao/MelodyDao.kt:278-279`
- **证据**：

  ```kotlin
  @Query("DELETE FROM playback_events WHERE songId IN (:songIds)")
  suspend fun deletePlaybackEventsForSongs(songIds: List<Int>)
  ```
  schema v16 显示 `playback_events` 的唯一索引是 `(startedAtMs, isEffectivePlay, songId)` —— `songId` 排在**第三位**，无法作为等值查找前缀使用。
- **影响**：`cleanupMissingSongAssociations` 在每次删歌 / 去重 / 文件校验时调用该删除。`playback_events` 是纯追加表且**全项目没有任何清理逻辑**，随应用生命周期无限增长，于是每次清理都要扫描**历史上记录过的全部播放事件行**。
- **修复建议**：
  1. `PlaybackEventEntity` 增加 `Index(value = ["songId"])` + 迁移；
  2. 增加保留策略（建议删除 12 个月前的事件），否则表增长会持续放大这个扫描成本。

#### 🔶 M-7 启动恢复把 `songs` 表物化 3 遍

- **位置**：`data/.../repository/RoomMusicLibraryDataSource.kt:22`、`:44`；`MelodyDao.kt:370`
- **证据**：

  ```kotlin
  // RoomMusicLibraryDataSource.kt:22
  val restoredSongs = dao.songs().map { entity -> ... }
  // RoomMusicLibraryDataSource.kt:44
  val albumIdBySongId = dao.songs().associate { it.id to it.albumId }   // ← 第二次全表
  // MelodyDao.kt:370（syncLibraryCatalog 内部）
  val songEntityById = songs().associateBy { it.id }                    // ← 第三次全表
  ```
- **影响**：`restore()` 位于**启动关键路径**（`MusicRepository.loadSongs` → `restoreFromRoom`，`MusicRepository.kt:148-190`），而「启动中…」页面正等待它。每次全表物化都要把每首歌的 `uri` / `albumArtCacheUri` / `lrcUri` 等全部列读进内存。1000 首曲库下这是三次可观的分配与 IO。
- **修复建议**：`:44` 的 `albumIdBySongId` 直接从 `:22` 已加载的 entity 列表派生；`syncLibraryCatalog(songs: List<Song>)` 改为接收已加载的 entity 列表（或 id→albumId 映射），消除 `MelodyDao.kt:370` 的第三次查询。

#### 🔶 M-8 `MusicRepository` 两处在**写锁内**做 O(n) 线性查找

- **位置**：`data/.../repository/MusicRepository.kt:336-344`、`:922-924`
- **证据**：

  ```kotlin
  // MusicRepository.kt:336-344
  for (update in updates) {
      val idx = songs.indexOfFirst { it.id == update.songId }   // ← O(n)，在写锁内
      if (idx >= 0) { songs[idx] = songs[idx].copy(albumArtUri = update.newUri) }
  }
  ```
  ```kotlin
  // MusicRepository.kt:922-924
  lock.write {
      val idx = songs.indexOfFirst { it.id == song.id }          // ← 同上
  ```
- **影响**：`refreshAllAlbumArtInternal` 在封面缓存被清空（重装应用）时会让每首歌封面失效 → 1000 × 1000 = **100 万次比较，且全程持有写锁**；而读路径（`getSongs()`，供 Compose 重组使用）会被整段阻塞。`MusicRepository.kt:319-321` 的注释本身就警告过「此路径不得阻塞读锁」，但算法违反了它。`refreshMetadata` 更甚——它对每首候选歌 `async` 一次，每次都抢一次写锁做线性扫描（`MusicRepository.kt:893`）。
- **修复建议**：循环前构建 `val indexById = songs.withIndex().associate { (i, s) -> s.id to i }`；`refreshMetadata` 改为先并发收集结果，最后一次性加写锁批量落库。

#### 🔶 M-9 封面缓存：同一哈希被并发写 + 落盘非原子

- **位置**：`data/.../util/AlbumArtExtractor.kt:154-160`
- **证据**：

  ```kotlin
  val hash = contentHash(artBytes)
  val artFile = File(cacheDir, "art_$hash.jpg")
  if (artFile.exists()) { cacheHitCounter.incrementAndGet(); return Uri.fromFile(artFile) }
  // ...（解码 + JPEG 编码）
  FileOutputStream(artFile).use { fos -> scaled.compress(Bitmap.CompressFormat.JPEG, 85, fos) }
  ```
  调用点在并行导入体内（`MusicRepository.kt:476`，`async(Dispatchers.IO)` 并发度 4~16）。
- **影响**：同一专辑的所有曲目内嵌**相同封面字节** → 相同 SHA-1 → **相同文件路径**。`exists()` 检查与写入之间没有原子性，于是 N 首歌各自解码、编码、覆写同一个文件——恰好把内容哈希缓存想省的活干了。更糟的是**写不是原子的**：文件以最终名字出现时可能还没写完，并发读者（UI 加载封面、或 `AlbumArtExtractor.kt:215-221` 的 `refreshAlbumArtIfNeeded`——它只 `file.exists()`、从不校验可解码性）可能读到**截断的 JPEG 且永不自愈**。
- **修复建议**：
  1. 写临时文件再 `renameTo`（同文件系统内原子），避免半成品以最终名可见；
  2. 用一个 `ConcurrentHashMap.newKeySet<String>()` 记录「正在写的哈希」，同一哈希只允许一个线程执行解码/编码，其余等待。

#### 🔶 M-10 `ControllerQueuePlanner` 把 O(n²) 的 `currentPlayOrderIndices()` 算了两遍

- **位置**：`domain/.../playback/ControllerQueuePlanner.kt:20`、`:23`、`:39-43`、`:45-54`；`core/model/.../PlayQueue.kt:313-331`
- **证据**：

  ```kotlin
  // ControllerQueuePlanner.kt
  val orderedSongs = orderedSongs(queue)                            // :20 → queue.currentPlayOrderIndices()
  val orderedStartIndex = orderedStartIndex(queue, requestedIndex)    // :23 → queue.currentPlayOrderIndices()  ← 又一遍
  ```
  ```kotlin
  // ControllerQueuePlanner.kt:50
  return queue.currentPlayOrderIndices()
      .indexOf(safeRequestedIndex)
  ```
  而 `currentPlayOrderIndices()` 在 SHUFFLE 模式下本身就是 O(n²)：
  ```kotlin
  // PlayQueue.kt:325
  else songs.indices.firstOrNull { !used[it] && songs[it].id == songId }   // 每个 order 条目都重扫一遍 songs
  ```
- **影响**：该 planner 在生产路径上——`player/.../window/PlaybackWindowPlanner.kt:13` 每次窗口规划（队列同步 / 切歌）都调用一次。SHUFFLE 模式下是 **2 × O(n²)** 次 id 比较；业务队列可以是整个曲库（1000 首 → 约 200 万次比较/次规划）。
- **修复建议**：
  1. `plan()` 内计算一次 `val orderIndices = queue.currentPlayOrderIndices()`，两个下游共用；
  2. SHUFFLE 分支改为先建 `Map<Int, ArrayDeque<Int>>`（songId → 下标队列），把 O(n²) 降到 O(n)。

#### 🔶 M-11 情绪展示区对同一条曲线做 3~4 遍投票，且逐帧扫描

- **位置**：`core/ui/.../components/SongEmotionSection.kt:146`、`:148`、`:208`、`:233`、`:271-274`、`:304`；`core/model/.../EmotionGroup.kt:266-271`；`core/model/.../SongEmotion.kt:70-71`
- **证据**：

  ```kotlin
  // SongEmotionSection.kt:146, :148, :208 —— 三次独立调用，每次都从头投票
  val tags = emotionTagsOf(shown)
  val tagRatios = emotionTagRatiosOf(shown)
  val confidenceLevel = emotionConfidenceLevelOf(shown)
  ```
  每次投票的输入曲线都是**未缓存的 getter**：
  ```kotlin
  // SongEmotion.kt:70-71
  val compensatedCurve: List<Pair<Float, Float>>
      get() = VocalCalibration.adjustedCurve(curve, vocalScore)   // 每次访问重建整条曲线
  ```
  投票本身每个窗口都有一次分配：
  ```kotlin
  // EmotionGroup.kt:266-267
  val nearest = entries.filter { it.auto }.minByOrNull { g ->   // ← 每窗分配一个新 List
      val p = anchors.getValue(g)                               // ← 每窗每候选一次 map 查找
  ```
  再加上逐帧路径：
  ```kotlin
  // SongEmotionSection.kt:271-274 —— 在 Canvas 的 draw lambda 内
  if (emotion.durationSec > 0f && emotion.windowsAnalyzed > 0 &&
      hasSignificantPeak(emotion.curve)                          // ← 每帧一次全曲线扫描 + List 分配
  ) { ... }
  // SongEmotionSection.kt:304 —— 同一计算又算一遍
  text = if (hasSignificantPeak(emotion.curve)) { ... }
  ```
- **影响**：一条 4 分钟歌曲约 96 窗。每次组合要跑 3~4 遍投票 ≈ 400 次窗口→分组评估 + 400 个 Pair 分配 + 3~4 条曲线重建，**全部在主线程**。`hasSignificantPeak` 更是跑在**绘制阶段**，每帧分配一个 `List<Float>` 并扫描两遍曲线。
- **修复建议**：
  1. 引入**单次投票、多处派生**：算一次 `vote`，再从它派生 `tags` / `tagRatios` / `confidence`（建议加一个领域入口一次性返回三者，防止重复回流）；
  2. `EmotionGroup.voteRatios` 把不变量提到循环外：`private val AUTO_GROUPS by lazy { entries.filter { it.auto } }`，并把 `anchors.getValue(g)` 预先解析成 `List<Pair<Group, Point>>`，消除每窗分配与 map 查找；
  3. `hasSignificantPeak` 与 `smoothCurve` 提到 Canvas 之外并 `remember`；`PathEffect.dashPathEffect` 提到文件级常量（`:240-242` 目前每帧新建）。

#### 🔶 M-12 「已在歌单中」判定是 O(歌数 × 歌单歌曲数)，且在每一行内重复

- **位置**：`core/ui/.../components/AddToPlaylistDialogs.kt:96`、`:233`；`core/model/.../Playlist.kt:17`
- **证据**：

  ```kotlin
  // AddToPlaylistDialogs.kt:96 —— 在 LazyColumn 的 item lambda 内
  val alreadyInCount = songs.count { it.id in playlist.songIds }   // `in` 对 List<Int> 是线性扫描
  ```
  ```kotlin
  // Playlist.kt:17
  val songIds: List<Int> = emptyList(),      // 普通 List → 成员判定 O(m)
  ```
  ```kotlin
  // AddToPlaylistDialogs.kt:233
  val alreadyIn = song.id in playlist.songIds
  ```
- **影响**：批量加 300 首歌到 2000 首的歌单 → **每个歌单行 300 × 2000 = 60 万次比较**，且**未 memoize**，每次重组重算。屏幕上 10 个歌单 → 单帧 600 万次比较。
- **修复建议**：把 id 集合提到行外并用 Set：
  ```kotlin
  val songIdSet = remember(songs) { songs.map { it.id }.toSet() }
  items(playlists, key = { "batch_dlg_${it.id}" }) { playlist ->
      val playlistIdSet = remember(playlist.songIds) { playlist.songIds.toSet() }
      val alreadyInCount = songIdSet.count { it in playlistIdSet }
  ```

#### 🔶 M-13 `Song.parsedArtists` 是未缓存的 getter，被用作排序键与库级过滤器输入

- **位置**：`core/model/.../Song.kt:51-56`（根因）；热点调用方 `feature/playlist/.../SongSorter.kt:29`、`feature/player/.../PlayerRuntime.kt:651`、`data/.../dao/MelodyDao.kt:344,363`
- **证据**：

  ```kotlin
  // Song.kt:51-56
  val parsedArtists: List<String>
      get() = artist
          .split('/')          // 每次访问都分配
          .map { it.trim() }
          .filter { it.isNotEmpty() }
          .ifEmpty { listOf(artist.trim()) }
  ```
  作为比较器键选择器：
  ```kotlin
  // SongSorter.kt:29
  SongSortMode.ARTIST -> compareBy<Song, String>(collator) { it.parsedArtists.firstOrNull().orEmpty() }
  ```
  库级过滤里同一个值被反复重算：
  ```kotlin
  // PlayerRuntime.kt:649-653
  .filter { song ->
      val sharedArtist = song.parsedArtists.any { artist -> artist in currentSong.parsedArtists }
      //                       ^^^^^^^^^^^^^^^^^^^^^^^ 每个歌手都重切一次 currentSong.artist
  }
  ```
- **影响**：`compareBy` 的键选择器**每次比较都调用一次**，按歌手排序 1000 首 ≈ 1 万次比较 × 约 4 次分配 ≈ 4 万个短命对象。`PlayerRuntime` 里则是 O(歌数 × 歌手数) 次对**同一字符串**的重复切割。
- **修复建议**：在 `Song` 中一次性算好并存为构造期计算属性（`private val` 不参与 `equals`/`hashCode`/`copy`，不破坏 data class 契约）：
  ```kotlin
  private val parsedArtistsCache: List<String> = artist.split('/').map { it.trim() }
      .filter { it.isNotEmpty() }.ifEmpty { listOf(artist.trim()) }
  val parsedArtists: List<String> get() = parsedArtistsCache
  ```
  同时把 `PlayerRuntime.kt:651` 的 `currentSong.parsedArtists` 提到内层 lambda 之外。

#### 🔶 M-14 Home 屏每秒重组 5 次，目标是 31 参数的 `SongInfoSection`

- **位置**：`feature/home/.../HomeScreen.kt:144`（消费点）、`:354-386`（`SongInfoSection`）；窄流订阅 `app/.../player/NowPlayingSurface.kt:54`；节拍源 `feature/player/.../PlayerPlaybackProgressTicker.kt:16`
- **证据**：

  ```kotlin
  // NowPlayingSurface.kt:54
  val positionMs by playerViewModel.positionMs.collectAsStateWithLifecycle()
  ```
  ```kotlin
  // PlayerPlaybackProgressTicker.kt:16
  private val intervalMs: Long = 200L,      // 5 fps
  ```
  ```kotlin
  // HomeScreen.kt:144 —— positionMs 一路传到 31 参数的 SongInfoSection
  item(key = "song_info") {
      SongInfoSection(currentSong = ..., currentPositionMs = currentPositionMs, ...)
  ```
- **影响**：窄流设计确实把**应用外壳**排除在外了，但订阅点仍在 `NowPlayingSurface` 顶层 → `NowPlayingSurface → HomeRoute → HomeScreen → SongInfoSection` 整条链每秒重跑 5 次。而 `SongInfoSection` 打包了复制按钮、版本选择器、整排播放控制、无限/探索 chip、定时关闭 chip 与「更多」下拉——其中 `HomeScreen.kt:640-676` 的 `moreActions` 列表**每次重组重建**。真正需要位置的只有进度条和两个时间标签。
- **修复建议**：照搬项目里已有的正确范式——`feature/player/.../MusicPlayerBottomBar.kt:76` 就是**在叶子节点内部**订阅 `positionMs`。抽出 `PlaybackProgressBar(positionMs: StateFlow<Long>, …)` 并在叶子内订阅，把 `currentPositionMs` 从 `HomeScreen` / `SongInfoSection` 的参数表里移除。这样每秒 5 次的重组范围收缩到进度条一个节点。

#### 🔶 M-15 时段配置页的「可随机数」是 O(时段数 × 已分析歌曲数)，且在组合期逐击键全库扫

- **位置**：`feature/user/.../MoodTimeSlotScreen.kt:450`、`:727-733`；`feature/user/.../MoodSlotEditDialog.kt:96-101`；数据源 `app/.../mood/MoodTimeSlotViewModel.kt:84-91`
- **证据**：

  ```kotlin
  // MoodTimeSlotScreen.kt:450 —— 在 LazyColumn 的 content lambda 内
  items(configs2Items(state), key = { it.config.id }) { item -> ... }
  ```
  ```kotlin
  // MoodTimeSlotScreen.kt:727-733
  private fun configs2Items(state: MoodTimeSlotRouteState): List<SlotItem> =
      state.configs.map { config -> SlotItem(config, state.poolCountOf(config.tags.toSet())) }
  ```
  ```kotlin
  // app/.../mood/MoodTimeSlotViewModel.kt:84-91
  fun matchCount(tags: Set<String>): Int =
      emotionRatios.values.count { ratios -> ratios.any { (word, ratio) -> ... } }
  ```
  编辑弹窗里更糟——`name` 是绑定到 `OutlinedTextField` 的 `mutableStateOf`：
  ```kotlin
  // MoodSlotEditDialog.kt:96-101
  val totalSongs = if (tags.isEmpty()) 0 else poolCountOf(tags)   // ← 每次击键重算
  ```
- **影响**：1100 首已分析曲库 × 5 个时段 ≈ 每次 5500 次 map 迭代；而编辑弹窗里是**每敲一个字符做一次全库扫描**（主线程）。
- **修复建议**：
  1. `configs2Items` 提到 `items()` 外面并 `remember`；
  2. 编辑弹窗用 `remember(tags, tagCounts) { poolCountOf(tags) }`；
  3. 根本解法：让 `MoodTimeSlotViewModel` 预先算好「每个词条 → 命中歌曲数」的映射并作为 `StateFlow` 发布，组合期只查表。

#### 🔶 M-16 曲库歌手/专辑列表每次按键过滤两遍，且列表实例每次都换

- **位置**：`feature/playlist/.../PlaylistScreen.kt:352-401`；二次过滤 `feature/playlist/.../LibraryBrowseViews.kt:194`、`:261`
- **证据**：

  ```kotlin
  // PlaylistScreen.kt:383-387
  albums = libraryAlbums.filter { album ->
      librarySearchQuery.isBlank() ||
          album.name.contains(librarySearchQuery, ignoreCase = true) ||
          album.artistNames.any { it.contains(librarySearchQuery, ignoreCase = true) }
  },
  ```
  ```kotlin
  // LibraryBrowseViews.kt:194 —— 对已过滤结果再过滤一遍
  val visibleArtists = if (hideSingleSongArtists) artists.filter { it.songCount > 1 } else artists
  ```
- **影响**：`librarySearchQuery` 是 `rememberSaveable` 的状态，每次击键让 `PlaylistScreen` 重组 → 2 遍全量过滤；且每次都产出**全新的 List 实例**，导致 `LazyColumn` 永远看到新数据集（无法复用 item 状态）。
- **修复建议**：合并成一次过滤并 `remember`：
  ```kotlin
  val visibleArtists = remember(libraryArtists, librarySearchQuery, hideSingleSongArtists) {
      libraryArtists.filter { artist ->
          (!hideSingleSongArtists || artist.songCount > 1) &&
          (librarySearchQuery.isBlank() || artist.name.contains(librarySearchQuery, true))
      }
  }
  ```
  并把结果直接传给视图，删除 `LibraryBrowseViews.kt:194/261` 的二次过滤。

#### 🔶 M-17 歌手详情点击 / 专辑详情进入时，各扫一遍全库

- **位置**：`feature/playlist/.../ArtistDetailScreen.kt:383-386`；`feature/playlist/.../AlbumDetailScreen.kt:83-86`
- **证据**：

  ```kotlin
  // ArtistDetailScreen.kt:383-386 —— 点击瞬间
  onSongClick = { song ->
      val context = state.songs.filter { state.artistName in it.parsedArtists }   // 全库 + 逐首切分
      actions.onSongClick(song, context)
  },
  ```
  ```kotlin
  // AlbumDetailScreen.kt:83-86 —— 为显示「其他专辑」而对全库分组排序
  val otherAlbums = remember(songs, albumName, albumArtists) {
      LibraryCatalog.deriveAlbums(songs)
          .filter { it.name != albumName && it.artistNames.any { artist -> artist in albumArtists } }
  }
  ```
  `deriveAlbums` 内部对**每首歌**调用 `parsedArtists`（`LibraryCatalog.kt:39`），即每首歌一次字符串切分。
- **影响**：用户点一下歌手歌单行 → 主线程全库扫描 + 1000 次字符串切分；进入专辑页 → 全库 groupBy + 全库切分 + 排序，只为显示几个「同歌手的其他专辑」。
- **修复建议**：
  1. `ArtistDetailScreen.kt:78` 已经有正确的集合（`remember(songs, artistName) { songs.filter { artistName in it.parsedArtists } }`），把它提升到 Route 层并直接传给 action；
  2. `AlbumDetailScreen` 改为从该专辑自己的歌曲 + 同歌手歌曲派生，而非全库；
  3. 配合 M-13 缓存 `parsedArtists`，消除重复切分。

#### 🔶 M-18 Route 的 Actions 袋未标 `@Stable`，且 lambda 每次重组重建 —— 路由层永远无法跳过

- **位置**：`feature/playlist/.../PlaylistRoute.kt:13-14` vs `:38`；`feature/home/.../HomeRoute.kt:14` vs `:30`
- **证据**：

  ```kotlin
  // PlaylistRoute.kt:13-14 —— State 标了
  @Stable
  data class PlaylistRouteState( ... )
  // PlaylistRoute.kt:38 —— Actions 没标
  data class PlaylistRouteActions(
      val onPlaylistClick: (Playlist) -> Unit,
      ...
  ```
  且包装 lambda 在每次重组重新创建：
  ```kotlin
  // HomeRoute.kt:110-135
  onTogglePlayMode = { val label = actions.onTogglePlayMode(); showToast("播放模式: $label") },
  onSwitchVersion = { song -> actions.onSwitchVersion(song); showToast("已切换到 …") },
  ```
- **影响**：全由函数类型构成的数据类会被 Compose 编译器推断为 **unstable**，因此 `PlaylistRoute(state, actions, …)` **永远无法跳过**。即便标了 `@Stable`，每次新建的 lambda 也会让相等性失效。项目内已有正确范式（`MoodTimeSlotRoute.kt:37` 就标了 `@Stable`），但约定没有统一。净效果：Route→Screen 边界提供了**零**跳过能力，上游任何状态变化都会重组整棵子树——这正是 M-14 重组范围过大的结构性原因。
- **修复建议**：
  1. 所有 `*RouteActions` 统一标 `@Stable`；
  2. 把包装 lambda 放进 `remember`，或更好——**直接透传 action 引用**，把 toast 逻辑下沉到 action 实现里；
  3. `*RouteState` 实例在 `:app` 侧用 `remember(...)` 构建，保证身份稳定。

#### 🔶 M-19 日志脱敏每次调用编译 **6 个** `Regex`

- **位置**：`core/common/.../AppLogger.kt:77-86`（被 `:43`、`:47`、`:52`、`:65` 与 `PerformanceTrace.kt:50` 调用）
- **证据**：

  ```kotlin
  internal fun String.redactSensitiveValuesForLog(): String {
      return replace(Regex("""content://[^\s)]+"""), "content://<redacted>")
          .replace(Regex("""file://[^\s)]+"""), "file://<redacted>")
          .replace(Regex("""(?i)\b([0-9A-F]{2}:){5}[0-9A-F]{2}\b"""), "<bluetooth-address-redacted>")
          .replace(Regex("""(?i)\b(device(Name)?|bluetooth(Name)?|bt(Name)?)=("[^"]*"|'[^']*'|[^\s,)]+)""")) { ... }
          .replace(Regex("""(?i)[A-Z]:\\[^\s)]+"""), "<path-redacted>")
          .replace(Regex("""(?<![:/])/[^\s)]+"""), "/<path-redacted>")
  }
  ```
  每个 `Regex(...)` 都是**新构造**：每次调用都要解析模式并编译一个 `java.util.regex.Pattern`。
- **影响**：模式编译比模式匹配本身贵一到两个数量级。**每条日志**、以及 `PerformanceTrace.log` 中的**每个 metadata 值**都要付 6 次编译。在 debug 构建里，播放/扫描路径上的高频日志会把 CPU 花在纯开销上。
  （已核查：6 个模式均无嵌套量词，lookbehind 为定宽，**不存在灾难性回溯风险**。）
- **修复建议**：把 6 个模式提到文件级 `private val`，各编译一次：
  ```kotlin
  private val CONTENT_URI_RE = Regex("""content://[^\s)]+""")
  private val FILE_URI_RE = Regex("""file://[^\s)]+""")
  // ...其余 4 个同理
  internal fun String.redactSensitiveValuesForLog(): String =
      replace(CONTENT_URI_RE, "content://<redacted>").replace(FILE_URI_RE, "file://<redacted>")...
  ```

#### 🔶 M-20 `PerformanceTrace` 的 release 白名单机制**实际不产生任何输出**，且全项目零调用点

- **位置**：`core/common/.../PerformanceTrace.kt:11-14`、`:25-27`、`:45`、`:54`；`core/common/.../AppLogger.kt:46-48`；`app/.../di/LoggerModule.kt:17`
- **证据**：

  ```kotlin
  // PerformanceTrace.kt:11-12 —— KDoc 明确承诺
  // - **release 白名单开关**：通过 [allow] 注册的关键操作（如核心播放链路），即使在 release 下仍输出性能数据，
  //   用于线上问题定位；其余操作保持静默。
  fun allow(operation: String) { allowList.add(operation) }
  ...
  if (!shouldTrace(operation)) return        // :45 —— 白名单只决定「是否计算」
  ...
  AppLog.info(PERFORMANCE_TAG, "operation=$operation ...")   // :54
  ```
  ```kotlin
  // AppLogger.kt:46-48 —— 最终出口被 debug 开关拦住
  override fun info(tag: String, message: String) {
      if (debugLoggingEnabled) Log.i(tag, message.redactSensitiveValuesForLog())
  }
  ```
  ```kotlin
  // app/.../di/LoggerModule.kt:17 —— release 下为 false
  fun provideAppLogger(): AppLogger = AndroidAppLogger(BuildConfig.DEBUG)
  ```
  且全项目 `src/main` 中 **`PerformanceTrace.allow(...)` / `disallow(...)` 零调用点**。
- **影响**：两个问题叠加——
  1. **文档承诺的能力不存在**：`allow()` 只控制「是否计算」，不控制「是否输出」；即使真的调用了 `allow()`，release 下消息依然被 `debugLoggingEnabled` 丢弃。项目以为自己在 release 有白名单可观测性，实际一条都没有。
  2. **单测给出虚假信心**：`core/common/src/test/.../PerformanceTraceTest.kt:47` 用的是捕获一切的 `RecordingLogger` fake，从未触碰真实的 `BuildConfig.DEBUG` 开关。
- **修复建议**：
  1. 把性能通道与 debug 开关**解耦**：给 `AndroidAppLogger` 增加独立的 `performanceLoggingEnabled`，并让 `PerformanceTrace` 走一条不受 `BuildConfig.DEBUG` 约束的专用方法（如 `AppLogger.performance(tag, msg)`）；
  2. 或让 `PerformanceTrace.log` 走一条 always-on 的通道；
  3. 补一个**使用真实 `AndroidAppLogger(debugLoggingEnabled = false)`** 的回归测试，断言 allowlisted 操作确实抵达了 platform-log sink。

#### 🔶 M-21 `:core:model` / `:domain` 引入 `android.*` 与 `androidx.compose.*`，而架构门禁**恰好不覆盖这条**

- **位置**：`core/model/.../Song.kt:3-4`、`ArtistEntry.kt:3-4`、`AlbumEntry.kt:3-4`、`PlayQueue.kt:3`、`Playlist.kt:3`、`SongInfo.kt:3`；`domain/.../importing/FolderImporter.kt:3`、`domain/.../repository/MusicImportRepository.kt:3`；`core/model/build.gradle.kts:187-191`
- **证据**：

  ```kotlin
  // core/model/.../Song.kt:3-4
  import android.net.Uri
  import androidx.compose.runtime.Stable
  ```
  ```kotlin
  // domain/.../repository/MusicImportRepository.kt:3-9
  import android.net.Uri
  interface MusicImportRepository {
      suspend fun importFolder(treeUri: Uri, onProgress: (processed: Int, total: Int) -> Unit): Int
  }
  ```
  门禁只检查资源与 R id，**没有**扫描 `:core/model` / `:domain` 源码里的 `^import (android|androidx)`（`buildSrc/.../ProductArchitectureVerification.kt:477-497` 只匹配 `R\.(drawable|string|...)`）。
- **影响**：CLAUDE.md 声明的「`:domain` 和 `:core:model` 不得有 Android 框架依赖」这条规则**既未被强制、也已被违反**。具体后果：
  - `:domain` 契约泄漏了 Android 框架类型 `android.net.Uri`，domain 端口只能在本机实现；
  - `:domain` / `:core:model` 的 JVM 单测无法构造真实 `Uri`（必须 Robolectric）；
  - `:core:model` 无法复用于非 Android 目标。
- **修复建议**：
  1. **先补门禁**（约 10 行）：扫描 `core/model/src/main/**` 与 `domain/src/main/**` 中的 `^import (android|androidx|kotlinx\.coroutines\.android)` 并让构建失败——这一步无论后续是否迁移都该先做；
  2. 把 `Uri` 换成纯值类型（`data class SongUri(val value: String)` 或直接 `String`），`Uri.parse(...)` 留在 `:data` / `:player`；
  3. Compose 注解可改用 `compileOnly(libs.androidx.compose.runtime)`（Compose 编译器在消费方读取稳定性注解，`compileOnly` 已足够），从而不再让 `:core:model` 依赖 Compose。

#### 🔶 M-22 两处读改写（read-modify-write）会丢更新

- **位置**：`data/.../repository/QuickSkipSongsRepository.kt:67-80`；`data/.../repository/WordPreferencesRepository.kt:36-62`
- **证据（短播放计数）**：

  ```kotlin
  override suspend fun incrementShortPlayCount(songId: Int): Int {
      val newCount = getShortPlayCount(songId) + 1        // ← 读
      withContext(Dispatchers.IO) {
          melodyDao.upsertQuickSkipShortPlay(              // ← 写（覆盖式 upsert）
              QuickSkipShortPlayEntity(songId = songId, count = newCount, ...)
          )
      }
      return newCount
  }
  ```
  **证据（词条投票）**：

  ```kotlin
  // WordPreferencesRepository.kt:36-62
  val existing = melodyDao.allWordPreferences().associateBy { it.word }
  // ...按 existing 的旧票数计算增量...
  melodyDao.upsertWordPreferences(updates)   // 整表覆盖
  ```
- **影响**：
  - **短播放计数**：两次并发自增读到同一个基数，后写覆盖先写 → 丢一次计数。而该计数驱动「累计 2 次自动加入秒切列表」的规则（`QuickSkipSongsRepository.kt:17`），丢计数会推迟自动加入。
  - **词条投票**：同一词条的「跳过」（不喜欢）与「听完」（喜欢）两次结算并发时，后写覆盖先写的增量。而票数决定探索播放的喜欢/不喜欢归属 → **偏好会静默漂移**。
  - 对比：同模块的 `play_stats` 已正确使用原子 SQL（`MelodyDao.kt:206-224`，`ON CONFLICT(songId) DO UPDATE SET playCount = playCount + 1`），说明正确范式在本项目里已经存在，只是没推广到这两张表。
- **修复建议**：
  ```kotlin
  // QuickSkipSongsRepository —— 新增原子查询
  @Query("INSERT INTO quick_skip_short_play_counts (songId, count, updatedAt) VALUES (:songId, 1, :now) " +
         "ON CONFLICT(songId) DO UPDATE SET count = count + 1, updatedAt = :now")
  suspend fun incrementQuickSkipShortPlay(songId: Int, now: Long)
  ```
  ```kotlin
  // WordPreferencesRepository —— 改为按行的原子增量
  @Query("INSERT INTO word_preferences (word, likeVotes, dislikeVotes, updatedAt) VALUES (:word, :like, :dislike, :now) " +
         "ON CONFLICT(word) DO UPDATE SET likeVotes = likeVotes + :like, dislikeVotes = dislikeVotes + :dislike, updatedAt = :now")
  suspend fun addVotes(word: String, like: Int, dislike: Int, now: Long)
  ```

### 正确性类

#### 🔶 M-23 情绪 upsert 可能覆盖用户刚保存的校准（TOCTOU）

- **位置**：`data/.../repository/SongEmotionsRepository.kt:58-75`
- **证据**：

  ```kotlin
  val old = melodyDao.songEmotion(emotion.songId)          // ← 读
  if (old != null && !old.userTags.isNullOrEmpty() && emotion.userTags.isEmpty()) {
      melodyDao.upsertSongEmotion(
          emotion.toEntity().copy(userTags = old.userTags, userTagsEdited = old.userTagsEdited)   // ← 用陈旧值写回
      )
  ```
- **影响**：若用户在校准弹窗保存的瞬间，正好有一首这首歌的分析任务结束并写入，`old.userTags`（陈旧值）会覆盖用户刚保存的内容——**用户的手工校准静默消失**。窗口虽窄，但失败后果是用户可见的数据丢失。
- **修复建议**：把合并下沉到 SQL（`UPDATE ... SET userTags = COALESCE(userTags, :tags)`），或把「读 + upsert」包进 DAO 的 `@Transaction`，让两者原子化。

---

## 3. Minor（建议改进）

### 性能类

- **N-1 · `PlaybackStateStore` 残留 5 处 `runBlocking(Dispatchers.IO)`，其中 2 处从主线程可达** — `player/.../PlaybackStateStore.kt:91, 136, 160, 199, 227`。`:player` 是全项目唯一还有 `runBlocking` 的模块（domain 契约层已彻底清零）。两条主线程路径：
  - `PlayerRuntime.onCleared()`（`PlayerRuntime.kt:1022`）→ `PlayerLifecycleFacade.onCleared()`（`:23-31`）→ `savePlaybackState()`（同步版，`PlayerRuntime.kt:1051`）→ `runBlocking` 阻塞主线程写 DataStore；
  - `PlayerQueueFacade.applyPlan` → `clearPlaybackState()`（`PlayerQueueFacade.kt:142`，`PlayerRuntime.kt:314` 绑定的是同步版）→ `PlaybackStateStore.clear()` → `runBlocking`。
  其余 3 处位于已经是 IO 上下文的调用链中（`savePlaybackStateAsync` → `scope.launch(dispatchers.io)`），属于**嵌套阻塞**——协程已在 IO 线程，却又 `runBlocking` 占住一个 IO 线程。建议：把这 5 处改为 `suspend`，调用侧用 `scope.launch(io)` 承接。
- **N-2 · `EmotionScanWorker` 每首歌重读一次 DataStore 判断暂停** — `app/.../emotion/EmotionScanWorker.kt:74` + `:143-147`。`emotionScanPaused` 是 DataStore 流，每次 `.first()` 都是一次真实磁盘读。300 首 = 300 次。建议循环外收集一次，循环内读本地变量。
- **N-3 · 探索/时段模式开关用 DataStore `.first()`，而同一值已被缓存** — `feature/player/.../PlayerRuntime.kt:504`（`moodSlotEnabled`）与 `:508`（`exploreModeEnabled`）每次都 `.first()` 读一次 DataStore；而 `PlayerRuntime.observeMoodSlotCaches()`（`:474-491`）**已经**把 `randomEnhanceMode` 持续订阅进 `_uiState.value.isExploreModeActive`。建议直接读缓存字段，零 IO。
- **N-4 · 补歌时按全库 id 列表查播放次数，并构造等长的 Map** — `feature/player/.../PlayerRandomQueueFacade.kt:126`、`:219` 调 `rawPlayCounts(candidates.map { it.id })`；`data/.../repository/PlayStatsRepository.kt:103-106` 返回 `songIds.associateWith { ... }`，为**每一首**未播放的歌也建一个 Map 条目。每次补歌 = 1 次 N 参数的 `IN (...)` 查询 + 一个 N 条目 Map。建议只返回有记录的项（缺失即 0）。
- **N-5 · `PlayQueue.withSongs` 的成员判定是 O(n·m)** — `core/model/.../PlayQueue.kt:275-279`，`playOrderIds = currentPlayOrderIds(newSongs).filter { id -> newSongs.any { it.id == id } }`。5000 首换队列 ≈ 2500 万次比较，且该路径由 `RandomQueuePlanner.planExploreRefill` / `planInfiniteRefill` 调用。建议先建 `HashSet`。
- **N-6 · `ControllerPlaybackStateSynchronizer` 每次同步都线性扫全库** — `domain/.../playback/ControllerPlaybackStateSynchronizer.kt:104-111`（`current.songs.firstOrNull { it.id == songId }`）与 `:40-44`（`sameNameSongs` 对全库 `filter + sortedByDescending`）。**说明**：本条经复核后判定为 **Minor 而非 Major** —— `sync()` 由 Media3 的离散事件与每 5 秒一次的 autosave 驱动，频率不高，1000 首库下每次约 1 毫秒量级，用户不可感知。仍值得改：传入 `songsById` 索引 + 按 `groupKey` 分组索引即可降到 O(1)。
- **N-7 · 情绪曲线每个采样点都装箱成 `Pair<Float, Float>`** — `data/.../repository/SongEmotionsRepository.kt:150-157`、`:188-204`；`core/model/.../SongEmotion.kt:14`。轻量投影已经剔除了 5.3KB/song 的 embedding，但曲线仍按「2 个装箱 Float + 1 个 Pair」计。1000 首 × 100 窗 ≈ 20 万个装箱 Float。建议改用 `FloatArray`（或扁平 `List<Float>`），`VocalCalibration.adjustedCurve` 同步加一个重载。
- **N-8 · 播放统计快照为了拿 id 集合而加载 `SELECT * FROM songs`** — `data/.../repository/PlayStatsRepository.kt:166`：`melodyDao.songs().mapTo(mutableSetOf()) { it.id }`。建议加 `@Query("SELECT id FROM songs")`。
- **N-9 · 排序路径把 `play_stats` 全表读了两遍** — `data/.../repository/PlayStatsRepository.kt:196-205`，`getPlayCountsForSort()` 与 `getLastPlayedAtForSort()` 各读一次，且两者在 `PlayerRuntime.kt:277` 被**同时**调用。建议合并为一次查询返回两列。
- **N-10 · 导入 N 首歌进歌单是 O(N²)** — `data/.../repository/MusicRepository.kt:571-573`：`playlist.songIds.contains(songId)` 是线性查找，`playlist.songIds + songId` 每次都复制整个列表。导入 1000 首 ≈ 50 万次槽位拷贝 + 1000 个中间列表。建议导入期用 builder 累积、最后一次性赋值。
- **N-11 · LRC 查找每首歌把同一目录列两遍** — `data/.../util/LrcParser.kt:111` 与 `:118`（SAF 路径）、`:277` 与 `:285`（文件路径）。两次 `listFiles()` 系统调用。建议取一次数组后跑两轮匹配。
- **N-12 · 封面哈希用 `String.format` 逐字节格式化** — `data/.../util/AlbumArtExtractor.kt:198-201`：`digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }`，每个封面 40 次 `String.format`（各自分配一个 `Formatter`），且未指定 Locale（locale 敏感）。建议用十六进制查表。
- **N-13 · 歌词每次进入歌词页都重新读文件、重新解析** — `data/.../util/LyricsExtractor.kt:30-57` 是无状态的；`feature/lyrics/.../LyricsRoute.kt:35-37` 的 `LaunchedEffect` 以歌曲 URI 为 key，离开再进入即重跑。建议在 `MediaMetadataRepository` 加一个按 songId + 文件指纹的 LRU 缓存。
- **N-14 · 单条标题覆盖改动会重建整张曲库表** — `data/.../repository/MusicRepository.kt:1003-1010` → `persistSongs` → `data/.../repository/RoomMusicLibraryDataSource.kt:58-64`：`DELETE FROM songs` + 全量重插 + 歌手交叉引用全量重建 + 两次孤儿扫描，只为改一个字段。建议单行场景改用 `@Upsert` 直接写覆盖表，全量重建只留给批量导入/对账。
- **N-15 · 情绪失败记录每次都重写整个 JSON blob** — `data/.../repository/EmotionFailureStore.kt:47-72`，每次 `record`/`clear` 都重新解析 + 序列化整张 map 并重写单个 DataStore key；map 无上限、永不清理。当前规模可接受，但需设上限与清理策略。
- **N-16 · `SongVersionComparer` 对同一行判据求值两次** — `domain/.../playback/SongVersionComparer.kt:138-141`，`it.cells.map { cell -> cell.display }.distinct().size > 1` 写了两遍、算了两遍，每次都分配一个 `List<String>` + 一个 `LinkedHashSet`。建议 `partition` 一次。
- **N-17 · 时间格式化用 `String.format`，且 locale 敏感** — `core/common/.../time/TimeFormatters.kt:13,15`（`"%d:%02d:%02d".format(...)`），`core/model/.../TimeSlotConfig.kt:45`，`feature/home/.../SleepTimerComponents.kt:263-274`。每次调用分配一个 `Formatter`；在非 ASCII 数字 locale 下（如 `ar-EG`）会渲染成 Eastern Arabic 数字，与整体中文 UI 不一致。建议直接字符串拼接或 `Locale.ROOT`。
- **N-18 · `LyricsView` 每行每次 tick 都新建一个 `TextStyle`** — `core/ui/.../lyrics/LyricsView.kt:363-367`（`MaterialTheme.typography.bodyLarge.copy(...)`），且 `:245` 的 key 用字符串插值构造。200ms tick 下整个歌词页每秒重组 5 次，每行每次重组都分配 `TextStyle` + `sp` 换算 + key 字符串。建议 `remember` 缓存样式，key 改用索引。
- **N-19 · `SongEmotion` 是含 `FloatArray?` 的 `data class`** — `core/model/.../SongEmotion.kt:10,21`。Kotlin 生成的 `equals` 对 `FloatArray` 走**引用相等**，因此内容相同的两条情绪不相等、hash 也不同；该类型也未标 `@Stable`，Compose 会一律视为不稳定。建议覆写 `equals`/`hashCode` 用 `contentEquals`，或把 `embedding` 从 data class 中拆出。
- **N-20 · `AppLogger` 没有级别开关，调用方只能提前拼字符串** — `core/common/.../AppLogger.kt:7-12`。接口只有 `fun debug(tag, message: String)`，没有 `isEnabled(level)`，也没有接收 lambda 的重载，于是任何带插值的调用都会**无条件**构造消息——即使 release 下 `AndroidAppLogger` 会丢弃它。建议加 `isEnabled(level)` + `inline fun info(tag: String, message: () -> String)`。
- **N-21 · `PlayerSettingsRepository` 是 30 成员的「上帝接口」** — `domain/.../repository/PlayerSettingsRepository.kt:19-71`，15 个 `Flow` 属性 + 15 个挂起 setter，横跨主题、蓝牙、通知、歌词字号、排序、睡眠定时、情绪扫描开关等无关联关注点。每个新设置都要改接口 + 改实现 + 每个测试 fake 都要实现 30 个成员；设置页收集多个设置时还会付 15 次 DataStore 读。建议按关注点拆分，或改为单一 `Flow<PlayerSettings>` 快照 + `update(transform)`。

### 架构与一致性类

- **N-22 · `PerformanceTrace` 无惰性 metadata 重载，且 `:core`/`:domain` 零埋点** — `core/common/.../PerformanceTrace.kt:57-72`。`measure(operation, metadata: Map, block)` 要求调用方**先构造好 Map** 再进入，即使追踪关闭也付了构造成本；全仓库 `PerformanceTrace` 调用点只在 `data/.../MusicRepository.kt:400,525` 与 `player/.../PlaybackController.kt:279,310,338,406,426`，而真正昂贵的策略层（`ControllerQueuePlanner.plan`、`ControllerPlaybackStateSynchronizer.sync`、`PlayQueue.currentPlayOrderIndices`）**没有任何锚点**——现有的锚点包在 controller 调用外面，无法区分「慢在 Media3」还是「慢在自家 O(n) 扫描」。建议加 `metadata: () -> Map<...>` 重载，并在两个 `:domain` 热点补锚点。

---

## 4. 专项核查：情绪分析链路（本轮新功能域，问题最集中）

情绪功能是 2026-10 的新战场，也是本轮问题密度最高的区域。上文 M-1 ~ M-4、M-11、M-15、M-22、M-23、N-2、N-3、N-7 全部落在这条链路上，且**互为因果**：

```
批扫逐首写入 (EmotionScanWorker:100)
      ↓  Room 失效 → 整表重发
observeAll() 整表读 + 逐行 JSON 解析曲线          ← M-1
      ↓
PlayerRuntime 算两份词条分布（匹配用/计票用）        ← M-11 同一投票算 3~4 遍
      ↓
探索补歌池对全库做 3 遍词条派生                    ← M-4
      ↓
每次补歌 = 全库规模的对象分配
```

**同一条链路上还有两处值得肯定的设计**：情绪数据在 Room 层已用**显式列投影**排除 5.3KB/song 的 `embeddingB64`（`MelodyDao.kt:401-418`），使整库重发的传输成本可控；`EmotionAnalyzer` 对解码内存的治理非常扎实（按时长 + 自适应堆预算双重熔断、`OutOfMemoryError` 单独归因、原始类型数组避免装箱——这些都是真机 OOM 教训换来的，见 `EmotionAnalyzer.kt:29-33`、`samplesLimits` 注释），是全项目工程质量最高的一段代码。

---

## 5. 上期（2026-09-03）遗留项整改状态复核

| 上期编号 | 上期问题 | 本次复核结论 |
|---------|----------|--------------|
| **C-1** | 导入流程无异常处理，UI 卡在"导入中" | ✅ **已修复** — `feature/player/.../PlayerImportFacade.kt:25-40` 已有 `try/catch(CancellationException)` + `Throwable` 兜底并复位 `isImporting` |
| **C-2** | `produceState` 中 suspend 调用未捕获异常 → 崩溃 | ✅ **已修复** — `app/.../AppNavHost.kt` 全部 6 处 `produceState`（`:438, :663, :692, :778, :799, :861`）均已 `runCatching` + `AppLog.error`；`:861` 的逐首查询也单独包了 `runCatching` |
| **M-1** | `runBlocking` 泛滥（约 50 处） | ⏳ **基本完成，剩尾部** — domain 契约层与 `:data` 已彻底清零（仅注释中提及）；**`:player/PlaybackStateStore` 仍剩 5 处**，见 N-1 |
| **M-13** | i18n：约 909 处硬编码中文 vs 4 条 `strings.xml` | ❌ **未处理，且数字上升** — 本次实测 **1,126 处**硬编码中文，`strings.xml` 仍只有 **4 条**（`app` 2 条 + `feature:user` 2 条）；`resConfigs("zh","en")` 中的 "en" 依然形同虚设 |
| **M-14** | lifecycle 版本脱节 / tensorflow-lite 迁移 | ✅ **已完成** — `player/.../EmotionAnalyzer.kt:13` 已切 LiteRT（`org.tensorflow.lite.Interpreter`，注释说明了包名保留原因）；lifecycle 2.8.7 / Compose BOM 2025.09.01 已对齐 |
| **m-7** | 每秒整壳重组 | ✅ **已根治** — `PlayerRuntime` 的 `positionMs` / `sleepTimerRemainingMs` **窄流**方案落地（`:188-197`），倒计时 tick 只写窄流不碰主 UiState（`PlayerSleepTimerCoordinator.kt:111-114`）。**遗留**：订阅点位置仍偏高（M-14） |
| **m-8** | `getSongsByPlaylistId` O(n²) find | ✅ **已修复** — `MusicRepository.kt:645-647` 改为先建 `associateBy` 索引 |
| **m-11** | `updatePlaylistSongCount` 公开且无锁 | ✅ **已修复** — 已改 `private` 并补写锁（`MusicRepository.kt:655`） |
| **m-12** | `startForegroundService` FGS 超时 ANR | ✅ **已修复** — `PlaybackController.kt:154-164` 改为 `context.startService(...)` 并附完整原因说明 |
| **C-2（上期 M-10）** | `Uri` / `String` 边界 | ⏳ **部分** — `domain` / `core:model` 仍用 `android.net.Uri`，且门禁不拦截，见 M-21 |

---

## 6. 检查通过项（已核实无问题）

- **架构门禁有效**：`verifyProductArchitecture` 的 LazyColumn key 规则（`ProductArchitectureVerification.kt:421-429`）经全量核对**无违规**；`feature/` 下每个 `items`/`itemsIndexed`/`item` 都带稳定 key。模块边界导入检查、`.gitignore` 覆盖、Room schema 不可变性、R8/shrink 设置、备份排除等规则均在位。
- **`:data` 分层干净**：`:data` 只 import `cn.com.dcsgo.mihx.core`（45 处）、`cn.com.dcsgo.mihx.domain`（39 处）与自身包（95 处），**零** `:feature`/`:app`/`:player` 引用——没有表现层类型泄漏，也没有为迁就实现而扭曲 domain 接口。
- **无资源泄漏**：`ContentResolver.query` / `openInputStream` / `openFileDescriptor` 全部使用 `use {}`；`MediaMetadataRetriever.release()` 三处调用点均在 `finally` 中。
- **阻塞 IO 已正确派发**：`:data` 全部 Room / DataStore 仓库方法均 `withContext(Dispatchers.IO)`；`:core`/`:domain` 完全不做 IO。
- **并发正确性防护到位**：`play_stats` 的计数与时长用 `ON CONFLICT DO UPDATE SET x = x + 1` 原子累加（`MelodyDao.kt:206-234`）；`replaceSongs` / `replacePlaylists` / `syncLibraryCatalog` 有 `@Transaction`；`PlayDurationTracker` 用 `SupervisorJob` + 显式 cancel 计时循环（`:47`、`:75`、`:140-143`，且有 `activeTrackingLoops` 探针与回归测试）；`MusicRepository` 的高水位查询刻意放在写锁**外**执行以避开 `IllegalMonitorStateException` 死锁（`:169-175`，注释详尽）。
- **落盘顺序保证**：`persistSongs` / `persistPlaylists` 走单线程 `limitedParallelism(1)` 作用域（`MusicRepository.kt:85-86`），后快照必定覆盖前快照，不会出现陈旧写最后落地。
- **权限流程合规**：通知与蓝牙权限请求**全部**由设置页用户操作触发（`feature/settings/.../SettingsScreen.kt:106` → `app/.../AppNavHost.kt:964` → `PermissionCoordinator`），启动路径无自动申请，符合架构规则。
- **Room schema 同步**：`data/schemas/` 导出的 16.json 与 `MelodyDatabase.VERSION = 16` 一致；`MIGRATION_1_2` … `MIGRATION_15_16` 连续无缺口；`ALL` 声明在最后（Kotlin 对象初始化顺序正确）。
- **迁移与 id 复用**：`maxKnownSongId()` 跨 6 张含 songId 的表取 `MAX(id)` 并集，防止删歌后 id 被复用而继承旧统计——设计周全。
- **测试质量优秀**：105 个测试文件、**695 个测试用例**、13,509 行测试代码、**0 个 `@Ignore`/`@Disabled`**；断言真实行为而非形式化；`EmotionAnalyzerLimitsTest`、`NarrowFlowSyncTest`、`PlayDurationTrackerTest` 等针对历史缺陷建立了回归锁定。
- **无遗留标记**：全部 `src/main` 中无真实的 `TODO` / `FIXME` / `HACK` / `XXX`（仅有 `toDouble` 等标识符的误命中）；无 `Thread.sleep`；`android.util.Log` 已完全替换为 `AppLog`。
- **皮肤解析安全**：`core/skin/.../SkinParser.kt` 使用 `org.json` 而非正则，**无正则回溯风险**；解析 fail-closed（未知 part 直接拒绝），一次性导入操作，不需要缓存。
- **`CoroutineDispatchers` 注入规范**：可注入的 `data class`，`main`/`io`/`default` 各自独立，`:core`/`:domain` 不直接使用 `Dispatchers.Main` 做 IO。

### 文档漂移

- **CLAUDE.md 的 Room 版本号过期** — 文中写「Schema export in `data/schemas/`（当前 v10）」，而实际 `MelodyDatabase.VERSION = 16`（`data/.../local/MelodyDatabase.kt:38`），已导出 `16.json`。建议同步。其余版本声明（versionName 3.12.0 / versionCode 48 / minSdk 33 / targetSdk 36 / `resConfigs("zh","en")` / release 仅 arm64-v8a）经核对**准确无误**。

---

## 7. 建议整改路线

按「投入产出比 × 风险」排序，建议分三波推进：

### 第一波：低风险、高收益（建议立即处理）

| 序号 | 条目 | 理由 |
|------|------|------|
| 1 | **C-1**（7 处 `loadSongInfo` 兜底） | 崩溃风险，改动最小；建议直接在 `MediaMetadataRepository.songInfo` 内兜底，一处改动免疫全部调用点 |
| 2 | **M-22**（两处丢更新改原子 SQL） | 两处都是小改动，直接消除数据正确性缺陷；同模块 `play_stats` 已有正确范式可照抄 |
| 3 | **M-19**（6 个 Regex 提为文件级常量） | 纯机械改动，立刻消除每条日志的 6 次模式编译 |
| 4 | **M-20**（PerformanceTrace 白名单） | 要么让它真的工作，要么把 KDoc 改对——当前状态是「文档承诺了不存在的能力」 |
| 5 | **N-1**（`PlaybackStateStore` 5 处 `runBlocking`） | 收尾上期 M-1 迁移，把「主源码零 runBlocking」这个不变量真正建立起来 |

### 第二波：情绪链路的性能收敛（建议作为一个专题一次性推进）

| 序号 | 条目 | 理由 |
|------|------|------|
| 6 | **M-1**（整表重发） + **M-3**（2s 轮询） | 两者叠加是批扫期间最大的 CPU 开销；M-1 用 `debounce` + 曲线解析缓存，M-3 用聚合查询 + 放宽轮询 |
| 7 | **M-4**（补歌池 3 遍派生）+ **M-13**（`parsedArtists` 缓存） | 两处共享根因：未缓存的派生属性。缓存 `parsedArtists` 与 `compensatedCurve` 后一并解决 |
| 8 | **M-2**（Worker 每首复制全库 + 逐首读 DataStore） | 改成循环外一次快照 + 循环外收集暂停标志 |
| 9 | **M-11**（情绪展示区重复投票 + 逐帧扫描） | 引入「一次投票多处派生」，并把逐帧计算提到 Canvas 之外 |
| 10 | **M-5 / M-6**（两个缺失索引 + 迁移 + `playback_events` 保留策略） | 加索引 + 迁移，同时给只增不减的事件表加清理策略 |

### 第三波：结构性优化（建议排入路线图）

| 序号 | 条目 | 理由 |
|------|------|------|
| 11 | **M-14 + M-18**（进度条叶子订阅 + Route `@Stable` + Action 透传） | 两者是同一个结构问题：Route/Action 边界不提供跳过能力。改完可同时收敛重组范围与参数列表长度 |
| 12 | **M-21**（`:core:model`/`:domain` 去 `android.*` + 补门禁） | 建议**先补门禁再迁移**——门禁先行会立刻让构建失败，这正是它应有的效果 |
| 13 | **M-15 / M-16 / M-17**（组合期全库扫描） | 统一改为「ViewModel 预计算 + 组合期只查表」的范式（`MoodTimeSlotViewModel` 已具备改造基础） |
| 14 | **M-12 / M-17**（超长参数列表：46 / 34 / 31） | 按项目已有的 Route(State + Actions) 范式重构，把三个模式拆成独立 composable |
| 15 | **N-21**（`PlayerSettingsRepository` 拆分） | 30 成员上帝接口的收益随设置项增长而放大，宜早拆 |
| 16 | **i18n**（1,126 处硬编码中文） | 上期遗留且数字上升。需要产品侧先定出「是否支持英文」的战略决策，否则投入产出不明——建议单独排期 |

### 建议补充的门禁规则

1. **`src/main` 禁止 `runBlocking`** — 目前 `:player/PlaybackStateStore` 尚有残留；机器强制比人工评审可靠。
2. **`:core/model` 与 `:domain` 禁止 `^import (android|androidx)`** — 填补 M-21 的门禁空洞。
3. **`LaunchedEffect` / `produceState` 中的挂起调用必须被 `runCatching` 包裹** — 直接固化 C-1 的修复成果，防止新增调用点回潮。
4. **`PerformanceTrace.allow()` 必须有调用点**，否则视为死代码 — 或者干脆移除该 API 并修正 KDoc（M-20 二选一）。

---

*本报告由 MiniMax M3.1 Flash 生成于 2026-10-27，基于 `58a365e` 的工作区快照。所有引用的 `file:line` 均已回读源文件确认；未采信任何未经源码验证的候选问题。*
