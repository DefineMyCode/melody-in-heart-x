# P5 交付说明：用户自定义皮肤导入（路线 B 闭环）

日期：2026-09-30　分支：`plugin-ui`　状态：**真机验收通过**
前置：P1 描述模型、P2 导航、P4 L2/L3——P5 是**路线 B 的最后一环（用户入口）**。
设计文档：`docs/architecture/PLUGIN_SHELL_DESIGN.md`

---

## 一、产品决策（用户拍板，2026-09-30）

| 决策点 | 用户选择 |
|---|---|
| Q1 导入方式 | **a. SAF 文件选择器** |
| Q2 导入失败 UX | **完整 issue 列表**（bottom-sheet/dialog 列全部错误） |
| Q3 与内置骨架的关系 | **另开一个**（独立入口卡 + 独立页面） |
| Q4 数量 | **装一个**（单槽位），**但能还原默认** |

---

## 二、交付清单

| 层 | 文件 | 作用 |
|---|---|---|
| :data | `repository/UserSkinStore.kt` | DataStore 单槽位存 JSON + id + name；`SkinIdDeriver`（sha256 前 16 位，`user.skin.` 前缀与内置不撞） |
| :data | `di/DataStoreModule.kt` | 提供 `UserSkinStore` 单例 |
| :core:skin | `SkinShellResolver.resolveUserSkin(json)`（在 :app） | 用户 JSON → AppShell，fail-safe |
| :app | `AppRoot.shell` 解析分支 | `user.skin.*` + json → `resolveUserSkin`；json 未到位短暂回落内置；其余走 `resolveById`（原行为） |
| :app | `AppRoutes.USER_SKIN` + `AppNavHost` composable + `RouteAffinity.USER_CHILDREN` | 独立页面路由（底栏高亮归属「我的」） |
| :app/debug+release | `UiTuningAccess` 扩展 | `userSkinId/userSkinName/userSkinJson` + `onImportUserSkin(suspend)` + `onRestoreDefaultSkin(suspend)`；release 恒空实现 |
| :feature:user | `ImportSkinResult`（sealed：Success/Failed/NotHandled/Error） | 导入结果模型 |
| :feature:user | `CustomSkinSection.kt` | 我的页分区卡（P5 新零件 `customSkin`，描述驱动可裁剪） |
| :feature:user | `UserSkinRoute.kt` | 独立页：状态卡 + SAF 导入 + 还原默认 + **完整 issue dialog** |
| :core:skin | `SkinPartCatalog.CUSTOM_SKIN` + 三套内置骨架 `sections` 注册 | 6 个内置分区 + customSkin |

**架构纪律**：`:feature:user` 不依赖 `:app` —— `ImportSkinResult` 落在 feature 层，
`:app` 的 tuning 回调以 suspend 函数穿过边界；`:feature:user` 加了 `:core:skin` 依赖（校验模型）。

---

## 三、★ 真机验收抓到的两个真 bug

### Bug 1：导入成功但外壳没切换（装配链路没接）

**症状**：导入合法 JSON 成功、状态卡变「已装」，但底栏还是内置 3 Tab。

**根因**：我写了 `SkinShellResolver.resolveUserSkin(json)` 但 **AppRoot 没调它**——
`resolveById("user.skin.xxx")` 在 `knownSkins`（3 个内置）里查不到 → 静默回落默认。
这是"接口建好但没接线"的典型：**存储层验收通过 ≠ 用户可见效果**。

**修法**：`AppRoot.shell` 改为三分支（`user.skin.*`+json → resolveUserSkin；json 未到位（冷启动
DataStore 异步读）→ 短暂回落内置后自动重算；其余 → resolveById）。

**防复发**：`UserSkinResolutionTest`（5 条）锁住这三条分支语义 + 坏 JSON fail-safe。

### Bug 2：主线程 runBlocking（黑屏元凶）

**症状**：还原默认后有一次整屏黑（只剩状态栏）。

**根因**：`onImportUserSkin` / `onRestoreDefaultSkin` 里用了
`kotlinx.coroutines.runBlocking { store.save/clear() }` —— **主线程阻塞等 DataStore IO**。

**修法**：两个回调都改成 **suspend**，调用方（UserSkinRoute）在 `scope.launch` 里调。
导入本就在协程中，直接挂起即可。

---

## 四、真机验收（tfl_smoke / API36，全过）

| # | 验收项 | 结果 |
|---|---|---|
| 1 | 我的页出现「自定义皮肤」新卡（第 6 分区） | ✅ |
| 2 | 进独立页，状态「未装用户皮肤」 | ✅ |
| 3 | SAF → Download → 导入**合法 JSON** → 状态变「已装」+ 皮肤名 + `user.skin.269a…` id | ✅ |
| 4 | **导入的皮肤真正生效**：底栏 2 Tab（曲库/我的）、起始页=曲库、**我的页按 JSON 裁到 2 卡**（心有乐章+自定义皮肤，其余 4 张消失） | ✅（Bug 1 修复后） |
| 5 | 导入**坏 JSON** → dialog「导入失败 (7 条)」：`TAB_MISSING_FIELD` / `INVALID_PROP_VALUE`×2 / `SEGMENT_INVALID` / `UNKNOWN_PART`… 结构化错误码+字段路径+消息，可滚动，「知道了」关闭 | ✅ Q2 |
| 6 | **还原默认** → 3 Tab、`skin_id=dcsgo.skin.builtin`、DataStore 清空、状态回「未装」、我的页恢复 6 卡 | ✅ Q4 |
| 7 | 冷启动保留用户皮肤（持久化） | ✅ |
| 8 | 零崩溃 | ✅（黑屏=Bug 2，已修） |

测试皮肤样本（验收用，已 push 到模拟器 Download）：
- `skin-good.json`：2 Tab + 我的页 2 分区（合法）
- `skin-bad.json`：缺 id、缺 icon、模板非法、段名非法、零件未知（一次触发 7 条 issue）

---

## 五、验证基线

- 全量单测 **457+ 条 0 失败**（新增 `UserSkinResolutionTest` 5 条 + `UserSkinCatalogConsistencyTest` 1 条）
- `verifyProductArchitecture` + `spotlessApply` 通过；debug + release 都编译通过
- ★ 跨模块一致性测试（`UserSkinCatalogConsistencyTest` 放 :app——架构门禁禁止 core/feature 互看，
  :app 是唯一能同时看到两边的地方）

---

## 六、本阶段没做的事

- ★ **release 包导入按钮是空转的（诚实声明）**：P5 借道了 debug 调参层的 `UiTuningAccess`
  承载导入/还原回调（P3 起皮肤状态就住在那层），release 桩恒为 `NotHandled` →
  正式版点导入只会提示"该功能在当前构建不可用"。
  **功能本身不是 debug-only**（用户入口、卡片、页面都在 release 存在），只是回调没接产品层。
  下一步：把皮肤状态从 debug tuning 层迁到产品层（DataStore 已在 :data，回调改走 ViewModel）。
  当前阶段用户全部在 debug 包验收，不影响本阶段验收结论。

- **Q3"另开"的另一个形态**：当前实现里用户皮肤在调参面板（debug）会与内置骨架**同列表**显示
  （`skinOptions` 合并），但**用户入口**（自定义皮肤页）是独立的。正式版没有调参面板，无所谓。
- **导入皮肤的预览/导出分享**（用户明确排除：分享是另一个功能）
- **多套皮肤并存**（Q4 = 单槽位，覆盖式）
- **皮肤描述的版本迁移**（`schemaVersion` 校验存在，但无跨版本迁移器——当前只有 v1）

---

## 七、版本与文件清单

- versionName `3.7.2-debug` / versionCode 35 / target API 36
- **APK 命名（用户 A 规则）**：`mihx-3.7.2-p5-import.apk`（**用户说"发"才发**）
