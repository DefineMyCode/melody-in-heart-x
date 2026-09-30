# P4-L3 交付说明：行模板化（songList 描述驱动 default/grid）

日期：2026-09-29　分支：`plugin-ui`　状态：**真机自验通过**
提交：`6e20533`
前置：`P4_SECTION_PARTITION_HANDOFF.md`（L2 分区化），设计文档：`docs/architecture/PLUGIN_SHELL_DESIGN.md`

---

## 一、本阶段做了什么

| 项 | 状态 |
|---|---|
| `songList.template` 描述字段真正可切（之前是死参数）| ✅ |
| 同零件加第二种实现 `grid`（2 列网格、封面为主、保留"隐藏 1 首歌"开关）| ✅ |
| 默认骨架**完全不变**（`template=default` = 改造前形态）| ✅ |
| 第三套内置骨架 `dcsgo.skin.grid`（"网格布局样本"），可在调参面板切换 | ✅ |
| `skin.debug.xml` prefs 持久化切换 | ✅ 沿用 P3 既有 |
| 12 条单测新增，455 条全绿 | ✅ |

---

## 二、★ 挖到一个真实保真 bug（双源漂移）

P1 时 catalog 里同时存在两份 `songList.template` 白名单：

- `ROW_TEMPLATES = {default, compact}`（旧，P1 留的死代码，**parser 在用**）
- `SONG_LIST_TEMPLATES = {default, grid}`（我新加的，**装配在用**）

这是 P1 的"伏笔 + 死代码"混合产物：
- P1 留了 `ROW_TEMPLATES = {default, compact}`，当时没有真正实现 compact，只是给后续预留
- 校验器按 `ROW_TEMPLATES` 拒非法值
- 我新加 `SONG_LIST_TEMPLATES = {default, grid}` 时，**装配用新集合、校验用旧集合**——会出现"grid 装配合法但校验拒"或反之的诡异错位

**若不加 `SongListTemplateCatalogTest` 兜底，grid 模板根本走不到真机自验那一步**。

修法：
- 移除 `ROW_TEMPLATES`
- parser 改用 `SONG_LIST_TEMPLATES`
- `SkinExpressivenessTest` 里 `compact` 测试夹具改成 `grid`

**负向对照验证**：临时把白名单改成 `{default, card}`，2 条测试立即失败；还原后恢复全绿。**证明它真在对照，不是又一个自证**。

---

## 三、真机自验（tfl_smoke / API36）通过

模拟器实测（用户原话："真机验收你先跑模拟器自己检查一下，我要 apk 时你再给我发"）：

| 检查 | 结果 |
|---|---|
| 默认骨架 → 曲库 → 歌手 → 2 行横排（封面+标题+副+右箭头）| ✅ 与改造前**逐像素一致** |
| 网格骨架 → 曲库 → 歌手 → **2×2 网格**（方形封面、封面为主）| ✅ |
| 网格骨架 → 曲库 → 专辑 → 单卡网格 | ✅（数据只有 1 张专辑） |
| 网格骨架 → 切回默认骨架 → 歌手 → 回到行布局 | ✅ **零回归** |
| 骨架切换不影响底栏/搜索/分段/迷你条 | ✅ |
| 零崩溃 | ✅ |

运行时诊断日志（已清理）：
```
I SkinShell: skin=dcsgo.skin.grid   resolved template=grid   (raw=grid)
I SkinShell: skin=dcsgo.skin.builtin resolved template=default (raw=default)
```

---

## 四、★ 教训（写进 skill）

**任何"多源同步"的常量都得有"两边逐项相等"测试**——本次双源漂移（P1 留的 `ROW_TEMPLATES` 死代码与我新加的 `SONG_LIST_TEMPLATES`）若不被 `SongListTemplateCatalogTest` 抓住，grid 模板根本走不到真机自验那一步。

**通用规则**：当一个值有 ≥2 个真相来源时（例如：描述层白名单 + 装配层 enum + 校验层硬编码），必须有一条**显式对照测试**强制它们同步，且需要**负向对照**验证该测试**真在对照**而不是自证。

---

## 五、本阶段没做的事

- **曲库页的歌单段**：`歌单` segment 用的是 `playlistShelf`，**没接 grid**——它本来就是横向卡片形态，与 grid 语义重复。先不做
- **L4 播放页形态**（黑胶 / 大封面 / 极简）——按 L3 节奏下一阶段再走
- **C 级能力**（改分区/零件的"长相"）——按用户裁定留作增量路线
- **资源包接入**（P5）——下一阶段

---

## 六、下一步建议

按用户"代办的都是要做的"+"你自己规划一下有序进行"的对话模式，排在 L3 之后的剩余待办：

| 待办 | 工作量 | 价值 |
|---|---|---|
| **L4 播放页形态**（先做一种，如黑胶） | 半天-1 天 | 播放器个性化最显眼的差异来源 |
| **P5 资源包**（用户导入皮肤 + 切换） | 1-2 天 | 整个插件系统的用户入口，路线 B 闭环 |
| 报告初版"目标 JDK = 9"那条落地 | 半天 | 一次性收尾 |

L4 节奏按 L3 模式：先做一种形态（黑胶），装上看差异感，再决定是否做其他两种。

---

## 七、版本与文件清单

- **versionName** 仍是 `3.7.2-debug`（versionCode 35）—— L3 没新增 feature,只是描述驱动已有页面的另一种渲染
- **双端 hash 一致**：`6e2053343b7ec39ef075f500fc7a8cd5770a8f66`
- **APK 命名（按用户 A 规则）**：`mihx-3.7.2-l3-grid.apk`（**用户说"发"才发**——按"我自己先跑模拟器检查，要 apk 时你再给我发"的约定）