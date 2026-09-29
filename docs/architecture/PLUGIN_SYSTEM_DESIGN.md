# 心乐 UI 插件系统设计（T2 代码插件路线）

日期：2026-09-29　**状态：降级为逃生口（superceded by `PLUGIN_SHELL_DESIGN.md`）**

> ## ⚠️ 路线状态变更（2026-09-29 用户拍板）
>
> **主干已改为路线 B（声明式皮肤包）**，见 `PLUGIN_SHELL_DESIGN.md`。本文件描述的 T2 代码插件**不再是主路线**，判定为**逃生口**：仅当某个 L6/L7 特效（频谱、无限流首页等）确实做不成宿主零件时，才单点动用。
>
> 变更理由（两条，都已实测/确认）：
> 1. **用户明确「插件不需要新增功能，只需要调 UI」** —— 这抽掉了代码插件的存在理由。代码插件的全部价值是"加能力"，而代价（组合期崩溃无法隔离、Kotlin 升级即集体失效、需 Android Studio 编写）是实打实的。
> 2. **路线 B 在四个维度上同时更强且成本更低**：改完即时生效（无需构建）、单文件可分享、导入时可逐字段精确校验并说明失败原因、组合期零崩溃风险。
>
> **设备端编译源码**这条更远的路已被 `RUNTIME_COMPILE_FEASIBILITY.md` 实测封死（Android 平台库缺编译器硬依赖的 JDK 模块），且用户明确「不需要在手机上开发」。
>
> 本文件保留的原因：spike 的三条硬约束（DCL 只读、崩溃无法进程内隔离、资源只能走编译期 R 常量）与全部实测数据仍有价值 —— 若真要用逃生口，从这里出发。下方内容**原样保留，未按新结论改写**。

spike 已验证（见 `PLUGIN_SYSTEM_SPIKE_REPORT.md`），原计划从阶段 0 分阶段实施。

---

## 一、目标与边界

**目标**：心乐能通过"插件"调整 UI，甚至注入自定义页面；插件可离线分享给别人导入。

**边界（用户 2026-09-29 明确）**：
- 能力上限 = T2 代码插件（可注入 Compose 代码与新页面）
- 使用面 = 自己用 + 能导出分享给别人装（单一文件、离线可装、不需要在线市场）
- 痛点 = ①改 UI 要等构建+发 APK 太慢 ②想有多套皮肤随时切换 ③为扩展铺路 ④现有配色不够、别人也能做主题

**非目标（本期不做）**：在线主题市场、插件签名信任链、插件内独立 Activity、插件热重载调试器。

---

## 二、架构

```
:plugin:api        唯一对外契约（纯 Kotlin + compose-runtime/ui，零项目内依赖）
                   UiPlugin / UiSlot / ThemeTokens / PluginManifest / PluginContext(只读)
                   ★只有这个模块做 API 兼容承诺（apiVersion）
:plugin:host       加载器：落盘→读清单→闸门→挂资源→置只读→加载 dex→实例化→自愈状态
:core:ui           ThemePalette(internal) 上提为公开 ThemeTokens，内置 4 套预设改成其实例
:app               提供插槽实例（主题包裹 / 播放页背景 / 列表行 / 底栏 / 插件路由挂载）
```

依赖方向：`:app → :plugin:host → :plugin:api`，`:plugin:api → (compose only)`。
`:plugin:api` **禁止**依赖 `:core:model`、`:data`、`:domain`、`:player` —— 它必须自包含，否则插件作者被迫拖整条内部链。

插件方的编译依赖 = `:plugin:api`（`compileOnly` androidx）。

---

## 三、契约（初稿）

```kotlin
// :plugin:api
interface UiPlugin {
    val pluginId: String
    val apiVersion: Int          // 与宿主精确匹配，不等则拒绝
    val compilerVersion: String  // Kotlin/Compose 编译器版本，不等则拒绝
    fun slots(): Set<SlotId>

    @Composable fun ProvideTheme(content: @Composable () -> Unit)      // 主题插槽
    @Composable fun PlayerBackground(content: @Composable () -> Unit)  // 播放页背景插槽
    @Composable fun ListRow(song: PluginSong, default: @Composable () -> Unit) // 列表行插槽
    // 插件自定义页面（可选）
    @Composable fun Page(routeId: String, host: PluginPageHost)
}

// 宿主只读数据视图：插件永远拿不到 :core:model / :domain 内部类型
data class PluginSong(val id: Int, val title: String, val artist: String, val album: String, val durationMs: Long)

enum class SlotId { THEME, PLAYER_BACKGROUND, LIST_ROW }

// 插件页面回调（宿主提供实现）
interface PluginPageHost {
    fun navigateTo(route: String)
    fun showToast(message: String)
    fun currentTheme(): ThemeTokens
}

// 设计令牌：颜色/圆角/间距/字阶，全部是**值**，不是资源 id
data class ThemeTokens(
    val bg0: Int, val bg1: Int, val bg2: Int, val bg3: Int, val bg4: Int,
    val out1: Int, val out2: Int,
    val text1: Int, val text2: Int, val text3: Int,
    val accent: Int, val onAccent: Int, val accent2: Int, val onAccent2: Int,
    val isDark: Boolean,
    val cornerRadiusDp: Float, val listItemSpacingDp: Float, val fontScale: Float,
)
```

### ★契约禁令（由 spike 实测逼出来的，写进插件模板与校验）

1. **禁止**插件打包任何 `androidx.*` 类（`compileOnly` + 加载前解压 dex 扫前缀校验）。
2. **禁止**插件用运行期 `getIdentifier` 取自有资源 —— 一律返回 0（实测）。要资源就在插件内自己用编译期 `R` 常量读取，然后以**返回值**交出。
3. **禁止**把裸资源 id 传过 API 边界（插件 R id 与宿主同处 `0x7f` 命名空间，存在串号风险）。契约只传**值**与**常量**。
4. **禁止**插件在组合期做阻塞 IO / 抛未捕获异常（后果 = 用户 App 崩溃，见风险节）。
5. **禁止**插件注册 Activity/Service/Provider/Receiver（插件包只作数据容器，从不安装）。

---

## 四、加载时序（实测通过）

```
用户用 SAF 选插件包
  → 复制到 filesDir/plugins/<id>-<versionCode>.apk.tmp
  → rename 到最终名（原子；避免半写入被加载）
  → ZipFile 读 assets/plugin-manifest.json
  → 闸门① apiVersion 精确匹配
  → 闸门② compilerVersion 精确匹配
  → 闸门③ minHostVersionCode ≤ 宿主
  → 闸门④ 解压 classes.dex 扫 androidx. 前缀，非空即拒绝
  → 闸门⑤ 解压扫 Activity/Service 声明，非空即拒绝
  → ResourcesProvider.loadFromApk + ResourcesLoader.addProvider + resources.addLoaders
  → apk.setWritable(false, false)          ← Android 14+ DCL 强制要求
  → PathClassLoader(apkPath, context.classLoader).loadClass(entry)
  → newInstance() as? UiPlugin（失败即拒）
  → 自洽校验：代码自报 apiVersion/compilerVersion == 清单
  → 注册进插件注册表，Flow 吐给 UI
```

任一闸门失败 → 明确报错文案 + 保持内置 UI，**绝不加载**。

---

## 五、崩溃策略（spike 推翻了原方案）

**实测：Compose 编译器拒绝 `try/catch` 包裹 composable 调用（编译期报错），组合期异常直接崩进程，宿主无法接管。**

因此采用 **dirty-flag 安全模式**：

| 场景 | 处理 |
|---|---|
| 加载期异常（反射/类加载/资源） | 就地 `runCatching` 捕获，退回内置 UI —— 这层是可行的（非组合期） |
| 组合期异常 | **无法拦截**，进程崩溃 |
| 崩溃后 | 下次启动 `settleAfterStartup()` 发现 `armed` 未清 → `crashCount++` |
| 连续 2 次 | 自动禁用该插件、回退内置 UI、设置页显示明确提示与"手动重启用" |

配套要求：
- 渲染插件前 `arm(pluginId)`，`Activity.onStop`/正常退出 `disarm()`。
- **插槽数量刻意保持少**：插槽越多，崩溃面越大。首期只开`主题`+`播放页背景`。
- **绝不在启动首屏路径调用插件**（避免"打开 App 必崩"）。
- 设置页必备：插件列表 / 启用禁用 / 卸载 / 崩溃计数 / 安全模式提示。

---

## 六、分阶段实施（每阶段可独立交付、可停在上一阶段）

### 阶段 0：App 内参数调试面板（debug 构建，长按版本号进入）— 0.5 天
封面留白 / 列表间距 / 进度条粗细 / 圆角 / 字阶 / 配色 全做成实时滑条，可一键导出 `tokens.json`。
**这一阶段直接解决"改 dp 要等构建"的最痛问题**，且产出的字段就是插件契约的第一版。新增跨页列表记得放对 remember 层级。

### 阶段 1：令牌上提 + 纯数据外观包 — 1~2 天
- `ThemePalette` → 公开 `ThemeTokens`（内置 4 套预设改实例，**视觉零变化**）
- `ThemeVariant` enum → `id` 注册表（DataStore 仍存字符串，旧值天然兼容）
- 外观包（json + assets）导入/切换/导出分享，走现有 SAF 链路
- 设置页新增"外观"分组（遵守单屏不滚动原则）

### 阶段 2：`:plugin:api` + `:plugin:host` + 主题插槽 — 2~3 天
- 契约模块 + 加载器（含全部闸门与 dm 只读）+ 崩溃自愈状态
- 首个插槽：主题（`ProvideTheme`）
- 示例插件工程（对照 spike 已验证的写法）
- `-keep` 规则 + `verifyProductArchitecture` 新检查

### 阶段 3：插槽扩展 — 按需
播放页背景 → 列表行 → 插件自定义页面（routes 挂进 NavHost）。

### 版本线
插件系统 = 新功能 → **v3.8.0**（b+1，c 置 0）。插件兼容线独立：`apiVersion` 大改用 +1 并在发行说明写迁移。

---

## 七、风险登记

| 风险 | 影响 | 对策 |
|---|---|---|
| 组合期崩溃无法拦截 | 用户 App 崩一次 | 安全模式自愈 + 插槽最小化 + 不在首屏调用 |
| Compose/Kotlin 升级导致插件集体失效 | 插件全灭 | `compilerVersion` 闸门显式拒绝（优雅失败，不崩）+ 插件模板随版本更新 |
| 插件 R id 与宿主撞号 | 显示错资源 | 契约只传值不传裸 id（禁令 3） |
| R8 裁掉契约类 | 插件加载失败 | `-keep` 契约包 + 加载器 |
| 只读插件包无法重装 | 插件升级失败 | tmp+rename + 新版本落新文件名，加载后清理旧包 |
| 恶意插件（等同同权限程序） | 数据泄露 | 导入时明确风险告知 + 二次确认；不提供自动/静默加载 |
| 生态假设不成立（没人写插件） | 投入白费 | 阶段 0/1 已独立产生价值；阶段 2 只在 0/1 用顺后再上 |

---

## 八、当前决定点

spike 的三项硬骨头已验证完毕（含两项**推翻了原假设**的发现），下一步是从阶段 0 开始实施，还是先评审本设计。
