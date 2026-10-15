# 代码评审报告 — melody-in-heart v3.10.2

| 项目 | 内容 |
|------|------|
| 评审日期 | 2026-10-15 |
| 评审模型 | Claude |
| 代码版本 | versionName 3.10.2 (versionCode 44)，HEAD `2fbcf65`，工作区仅 CLAUDE.md 与本报告有改动 |
| 评审方法 | 静态阅读 + grep 规则扫描 + 对疑点做逻辑模拟。**未运行**构建/测试/`verifyProductArchitecture`（本机 shell 无 JDK） |

> **对本报告初版（同日早些时候）的更正**：
> - 初版 M-5「`feature/home/src/...` 异常目录」**撤回**：这是工具输出层把 `feature/home` 下的 `src` 目录路径显示成 `Users/alex` 的假象，`git ls-files` 中并不存在 `feature/Users` 路径，仓库结构正常。
> - 初版 M-4「EmotionAnalyzer 的 `!!`」**降级**：逐处核对后均有前置保证或被外层 `catch` 兜住，仅是风格问题（见 m-7）。
> - 初版称「非 git 仓库」有误，仓库是 git 管理的。`runBlocking` 总数应为 49 处而非 48。
> - 同步更新了 CLAUDE.md（版本、模块、Room 实体、堆大小等已与代码对齐）。

## 0. 阅读覆盖范围

| 范围 | 深度 |
|------|------|
| `:player` 全部 | 逐文件通读 |
| `:domain` 播放/队列/时段/仓库接口 | 通读 |
| `:data` 的 MusicRepository、DAO、实体、迁移、各 Repository/Store、元数据/封面/歌词/路径工具 | 通读（`LrcParser`、`EmbeddedLyricsParser` 通读） |
| `:core:common`、`:core:model`（Song/PlayQueue/LibraryCatalog） | 通读 |
| `:app`（Application、DI、AppRoot、AppNavHost、Scaffold、Worker、各 ViewModel、权限、备份规则、Manifest） | 通读 |
| `:feature:player`（Runtime、持久化/随机/媒体事件/定时/库 Facade 等） | 通读 |
| `:feature:home/playlist/user/settings/lyrics` UI 正文、`:core:ui`、`:core:skin` 解析器后半、`:benchmark`、根 `build.gradle.kts` 校验任务正文、测试文件正文 | **仅 grep 抽样，未通读** |

## 1. 总览

| 维度 | 评级 | 结论 |
|------|------|------|
| 架构与分层 | A- | 模块边界清晰，Facade/Graph 拆分落地；`MusicRepository` 仍是 1179 行的内存+落盘一体化大类 |
| 正确性 | B- | 发现 1 处统计计时泄漏、1 处功能性失效（时段配置不刷新）、1 处歌词解析缺陷、1 处 ID 复用隐患 |
| 并发/性能 | C+ | 49 处 `runBlocking` 同步桥未收敛，且有多处落在主线程；导入时歌单被反复全量重写 |
| 安全/隐私 | A- | 无网络层；日志脱敏；备份排除完整；密钥未入库；CI 动作按 SHA 固定 |
| 测试 | B- | `feature:player`(25)、`domain`(13) 覆盖好；计时、解码、歌词、迁移、多数 UI 模块无测试 |
| 国际化 | D | 约 1064 处中文字面量，`strings.xml` 仅 2 条（9 月评审时 909 处，在增加） |
| 构建/CI | B | 发版流水线不跑任何校验 |

**发现统计：Critical 0 / Major 6 / Minor 12**

---

## 2. Major

### M-1 `PlayDurationTracker`：每次切歌泄漏一个计时协程（已核对源码，未复现）

`PlayDurationTracker.kt:75-93, 121-128, 210-227`。`startPlayback()` 先调 `stopPlayback()`（置 `isTracking=false`），随即 `startTracking()` 用 `compareAndSet(false,true)` 再启动新循环。旧循环此时正在 `delay(1000)`，醒来后 `while (isTracking.get())` 看到 `true`，**继续运行**。结果：播放中每直接切歌一次（点歌、切队列）就多一个永不退出的循环，直到最终 `stopPlayback()`。

后果：协程数随切歌线性增长；多个循环对 `lastUpdateTimeMs` 做非原子的读-改-写，偶发重复累加，会抬高「有效播放」（90% / 5 分钟）与 `playback_events` 时长。该类**没有任何测试**。
建议：保存 `Job`，`stopPlayback()` 里 `cancel()`；或给循环加会话代数（generation）校验。补一个切歌后循环数/累计时长的单测。

### M-2 时段配置与情绪词条缓存不会刷新，「情境化随心播放」改配置后不生效（已核对）

`PlayerRuntime.refreshMoodSlots()`（:400）没有任何调用方；`MoodTimeSlotViewModel.onConfigsChanged`（:109）全仓库**无人赋值**，`save/delete` 里 `?.invoke()` 恒为空操作。`moodSlotConfigsCache` / `moodEmotionTagsCache` 只在 `start()` 时加载一次。

场景：用户启动应用后才新建「深夜」时段并打开开关，点随心播放 → `moodSlotConfigs()` 仍是启动时的空列表 → 静默退化为全库随机，也不弹归因 toast；重启后才生效。新分析/校准出的情绪词条同理。
建议：让 `MoodTimeSlotViewModel` 观察的是同一份数据源（直接让 `PlayerRuntime` collect `observeConfigs()`），不要靠手动回调；情绪缓存监听 `songsChanged`/校准事件。

### M-3 内嵌歌词（ID3 USLT）解析错误（逻辑模拟已验证）

`EmbeddedLyricsParser.parseUSLTFrame`：
1. 无论编码，都用「连续两个 0x00」寻找描述符结束。对 ISO-8859-1(0) 与 UTF-8(3) 的空描述符（单个 0x00），扫描会一路走到帧尾，只解出**最后 1 个字节**。我用同样的循环逻辑模拟（enc=3，空描述符，8 个汉字）：解码起点落在 body 末尾，仅 1 字节。
2. 编码 3（UTF-8）被按 `ISO_8859_1` 解码，中文必乱码。
3. 返回的 `Lyrics.lines` 非空，`LyricsExtractor` 因此**不再回退**到同名 `.lrc`。

只影响「无导入 `.lrc` 且仅靠内嵌歌词」的 MP3；UTF-16 标签不受影响。另有一批同类问题：ID3v2.4 帧长是 syncsafe 却按普通大端读取；只读文件前 128KB，封面（APIC）在前且超过 128KB 时后面的 USLT 读不到；`InputStream.read/skip` 未循环到读满（FLAC 路径 `parseFlacVorbisComments` 同样有）；SYLT 未跳过描述符。该类**无测试**。
建议：按编码分别寻找终止符（0/3 用单字节，1/2 用双字节），3 用 UTF-8；考虑换成成熟的标签库或 `MediaMetadataRetriever` 之外的专用解析，并补解析单测。

### M-4 删歌后统计不清理 + `nextId` 重启复用，新歌会继承旧歌统计（已核对）

`MusicRepository.restoreFromRoom` 令 `nextId = max(id)+1`，`SongEntity.id` 不是自增。`deleteSong()` 只清情绪行，**不清** `play_stats`、`playback_events`、`quick_skip_songs`、`quick_skip_short_play_counts`（这些仅在 `validateAndCleanupLocalFiles`/`deduplicateSongs` 里清理）。若被删的是 id 最大的歌，重启后新导入歌曲会拿到同一 id，继承其播放次数、是否在秒切列表等；`playlist_resume`、播放快照中的旧 id 也同理。另外周榜/月榜在 `take(N)` 之后才按曲库过滤，遗留的孤儿行会挤占名额。
建议：`deleteSong` 复用 `cleanupMissingSongAssociations`；id 改为持久化的高水位或 `autoGenerate`（需迁移）。

### M-5 `runBlocking` 同步桥仍未收敛，且有明确的主线程调用点（9 月 M-1 遗留）

共 49 处：`PlayerSettingsRepository` 17、`SongEmotionsRepository` 9、`PlayStatsRepository` 9、`QuickSkipSongsRepository` 7、`PlaybackStateStore` 5、`app` 层 2。根因是 domain 接口把本可 `suspend` 的操作声明成同步。已确认的主线程路径：

- `AppNavHost.kt:756`：`QuickSkipSongsRoute(state = ...getQuickSkipSongs())` 在**组合期**同步查库，每次重组两次 `runBlocking`。
- `PlayerRuntime.playRandomQueue()`（点击回调）→ `rawPlayCounts()` → `getRawPlayCounts()`。
- `setGlobalUniformRandomEnabled / setDailyListeningGoalMinutes / setPlaybackNotificationEnabled / startSleepTimer / cancelSleepTimer` 全是 `...Blocking` 写 DataStore，在 UI 点击线程执行。
- `PlayerRuntime.onCleared()` → `PlaybackStateStore.save()` 同步写。
- `PlayDurationTracker.settlePlayback` 在 IO 协程里调同步仓库，再嵌套 `runBlocking(IO)`，白占线程。

建议：优先改 `QuickSkip`/`PlayStats`/`PlayerSettings` 的 domain 接口为 `suspend`；设置项用 `stateIn` 缓存最新值，`current*()` 直接读 `.value`。

### M-6 发版流水线不跑任何校验

`.github/workflows/release.yml` 只有 `assembleRelease`，没有 `test`、`spotlessCheck`、`verifyProductArchitecture`，也没有 PR 触发的 CI。CLAUDE.md 要求「提交前跑 `check`」，目前完全靠自觉。另外 `run:` 里直接插值 `${{ inputs.tag }}`，有 workflow_dispatch 权限的人可注入 shell（需仓库写权限，风险低，但应改走 `env:`）。
建议：加 PR 工作流跑 `./gradlew check`；发版 job 在 `assembleRelease` 前依赖它。

---

## 3. Minor

- **m-1 导入时对歌单 O(N²) 落盘**：`addFolder` 每加一首歌调用 `addSongToPlaylistInternal → persistPlaylists()`，每次都排入一次「删光所有歌单引用再整表重插」（`MusicRepository.kt:480-485, 683`）。导入 1000 首约 1000 次整表重写，且 `playlist.songIds + songId` 每次复制列表。循环结束后其实已有一次统一落盘（:498）。建议导入期间跳过逐首持久化。
- **m-2 国际化**：1064 处中文字面量（grep 估值，含少量误报），`strings.xml` 2 条；`resConfigs("zh","en")` 与注释「应用仅中文界面」自相矛盾。需产品决策：不出海就删 `en`。
- **m-3 `MusicRepository` 过大**：1179 行，`context`/`melodyDao` 为可空只为迁就测试，导致大量 `?: return`；内存列表为事实来源 + 全量快照写回。隐式约定也多：秒切歌单、「我的最爱」靠**名称**识别，用户重命名后同步会另建新歌单。
- **m-4 缺少迁移与关键类测试**：9 个手写 Migration 没有 `MigrationTestHelper` 测试（我对照 `10.json` 与 `DatabaseModule` 的建表语句逐表比对，目前一致）；`feature:user`(6344 行) 仅 1 个测试，`home/settings/lyrics` 为 0；`EmotionAnalyzer`、`FfmpegPcmDecoder`、`PlaybackController` 接线、`Bluetooth*`、`SongPathResolver` 无测试。
- **m-5 死代码**：`BluetoothAudioQualityManager.handleAudioInterruption/requestAudioFocus/audioInterrupted` 无调用方；`BluetoothStateManager.detectAudioQuality` 恒返回 NORMAL、`signalStrength` 从不赋值；`AppRoot` 里有空的 `LaunchedEffect(activeRoute) {}`；`AppMediaSessionService` 的监听器只打日志。
- **m-6 包名与模块不一致**：`:player` 的类在 `cn.com.dcsgo.mihx.data.player`。校验任务的导入正则只匹配 `data.(repository|local)`，所以当前没漏，但包名误导。
- **m-7 EmotionAnalyzer**：`onProgress` 只在最后调用一次，等于无效；20 分钟上限在 44.1kHz 下 `ShortAccum`(≈106MB，扩容时双倍) + 重采样(≈77MB) 可超 256MB 堆，靠捕获 `OutOfMemoryError` 兜底，不稳；几处 `!!` 实际安全，改成显式分支更清楚。
- **m-8 一键去重无确认**：`FileCheckScreen` 的「一键清理全部重复」直接执行；被移除副本的播放统计被直接清掉，而不是并入保留项。
- **m-9 `QuickSkipCoordinator.syncToPlaylist`**：逐首删除再逐首添加，每步一次整表落盘，期间歌单短暂为空。
- **m-10 校验任务脆弱**：`verifyProductArchitecture` 大量靠正则（如 `items(` 之后 160 字符内找 `key =`、`release { }` 取首个闭括号）。`app/build.gradle.kts` 为迁就它还专门把签名逻辑挪出 `release` 块并写了注释。建议改成基于 AST/Detekt 自定义规则，或至少补测试。
- **m-11 无障碍**：172 处 `Icon(` 中 87 处 `contentDescription = null`，其中可点击图标需逐个确认。24 处硬编码 `Color(0x...)` 绕开主题。
- **m-12 `PlayerSettingsRepository.themeMode`** 在 DataStore 的 `map` 里读 SharedPreferences（首次会同步读盘）。

## 4. 正向观察

- 日志统一走 `AppLog`，`android.util.Log` 仅在封装层；release 默认关闭 debug/info 并脱敏 URI/路径/蓝牙地址。
- Room 无 `fallbackToDestructiveMigration`；schema 全量导出；迁移与 v10 schema 一致。
- 备份/数据提取规则覆盖 Room、5 个 DataStore、SharedPreferences、封面缓存；`allowBackup` 风险可控。
- 密钥：`keystore.properties`、`local.properties` 均在 `.gitignore`，无密钥文件入库；CI 用 secrets 还原，Action 按 commit SHA 固定。
- `PlaybackStateStore` 对「空队列覆盖有效快照」的两类回归做了清晰防护并有测试；`PlayDurationTracker`/`PlaybackController` 的并发注释详尽。
- 解码熔断（时长前置 + 采样率换算上限 + 看门狗）、FFmpeg 优先避开厂商软解 native 崩溃，工程上很扎实。
- `collectAsState()` 无非生命周期用法；`GlobalScope` 为 0；窄流（position/sleepTimer）避免整壳重组；Application 级作用域单独注入。
- 模块依赖方向与 CLAUDE.md 一致；feature 模块没有 `runBlocking`。

## 5. 建议优先级

1. **M-2**（功能失效，改动小）与 **M-1**（计时泄漏，改动小）。
2. **M-3**（歌词解析）并补单测；**M-4**（ID 复用与孤儿数据）。
3. **M-6**：先加 PR 级 CI，再谈其余。
4. **M-5**：按仓库逐个把 domain 接口改 `suspend`，先从 `QuickSkip`/`PlayStats` 开始。
5. m-1、m-8 等体验类，随相关功能顺手修。

## 6. 局限

未运行构建与测试，所有「已核对」均基于读代码与对关键逻辑的独立模拟，M-1、M-2、M-4 未在设备上复现。`feature` 各模块 UI 正文与 `core:ui` 未逐行审阅，可能存在本报告未覆盖的 Compose 重组/状态问题；建议下一轮由有 JDK 的环境跑 `./gradlew check` 与 lint 作为补充。
