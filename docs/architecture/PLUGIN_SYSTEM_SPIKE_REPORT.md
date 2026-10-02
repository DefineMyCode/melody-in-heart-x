# 心乐插件系统 Spike 实测报告

日期：2026-09-29　分支：`plugin-ui`　验证工程：`/root/mihx-plugin-spike`（独立 spike，未改动心乐主仓代码）

设备：Android 模拟器 `tfl_smoke`（API 36 / x86_64 / google_apis），宿主 `targetSdk 36`、`minSdk 33`。

---

## 一、结论速览

| # | 待验证假设 | 结果 | 证据 |
|---|---|---|---|
| 1 | 插件 APK 可被宿主加载 dex | ✅ 成立 | `PathClassLoader` 解析出 `cn.com.dcsgo.spikeplugin.SpikePlugin` |
| 2 | `instanceof UiPlugin` 跨 dex 成立（契约类型统一） | ✅ 成立 | 契约类来自 `base.apk`，插件类来自 `plugins/plugin.apk` |
| 3 | 插件可零 Compose 依赖（`compileOnly`），运行期借宿主 | ✅ 成立 | 插件 `classes.dex` 内 `androidx/compose` 类数 **= 0**（dexdump 统计） |
| 4 | 插件 Composable 能真实执行并绘制 | ✅ 成立 | 宿主内存标记 `markers=[PluginTheme, HeaderBadge]` |
| 5 | `apiVersion` 闸门可拒绝加载且不崩 | ✅ 成立 | 篡改 manifest 的真实变体被拒，宿主保持内置 UI |
| 6 | `compilerVersion` 闸门可拒绝加载且不崩 | ✅ 成立 | 同上 |
| 7 | 入口类不存在时优雅失败 | ✅ 成立 | `ClassNotFoundException` 被捕获 → 返回内置 UI |
| 8 | Android 14+ 动态加载 dex 必须只读 | ✅ 约束真实生效 | 可写 dex 被拒：`SecurityException: Writable dex file '...plugin.apk' is not allowed` |
| 9 | `ResourcesLoader` 能让插件用自带资源 | ⚠️ **部分成立** | `addProvider` 成功，但**运行期按名字查找全部失败**；**按插件编译期 R id 读取全部成功** |
| 10 | 插件组合期异常可被 try/catch 隔离 | ❌ **不成立** | Compose 编译器直接拒绝：`Try catch is not supported around composable function invocations` |
| 11 | （替代方案）崩溃自愈：下次启动结算 + 安全模式 | ✅ 成立 | 组合期异常 → 进程崩溃 → 下次启动检测到未结算 → 计数 → 连续 2 次自动禁用并提示用户 |
| 12 | 注入插件资源后宿主自有资源不被污染 | ✅ 成立 | 注入 4 份插件资源表后 `host_own_label` 仍读到 `HOST-OWN-9001` |
| 13 | 插件包可就地覆写（重装） | ❌ 不成立（已修） | `chmod 0444` 后同路径覆写报 `EACCES`；必须 **tmp + rename 原子替换** |
| 14 | 插件 R id 与宿主 id 空间不冲突 | ⚠️ **有风险** | 插件 R id 同样落在 `0x7f` 命名空间（实测 `color 0x7f010000` vs 宿主 `attr 0x7f010000` 同号不同型） |

**总体判断：T2 插件化（动态加载代码 + 注入 UI）在本项目上技术可行，已跑通完整链路。但有两条硬约束改变设计：崩溃无法进程内隔离（只能重启自愈），以及插件资源只能走编译期 R id 不能走运行期名字查找。**

---

## 二、可行链路（实测通过的完整步骤）

```
插件包(APK, 不安装)
  ├─ assets/plugin-manifest.json   → ZipFile 读取，做版本闸门
  ├─ classes.dex                    → PathClassLoader 加载
  └─ res/ (resources.arsc)          → ResourcesLoader/ResourcesProvider 挂载
```

1. **落盘**：插件包写入 `filesDir/plugins/`。必须 `tmp 写入 + rename 替换`——上一轮 `chmod 0444` 后同路径无法覆写（实测 `FileNotFoundException: open failed: EACCES`）。
2. **读清单**：用 `java.util.zip.ZipFile` 直接读 `assets/plugin-manifest.json`，**不走 Android 资源系统**，因此在任何闸门之前就能拿到元数据。
3. **闸门**（全部在加载任何代码之前完成）：`apiVersion` 精确匹配、`compilerVersion` 精确匹配、`minHostVersionCode` 下限、id 黑白名单。
4. **挂资源**：`ResourcesProvider.loadFromApk(pfd)` + `ResourcesLoader.addProvider()` + `resources.addLoaders(loader)`（公开 API，minSdk 33 满足；实测 `addProvider` 成功）。
5. **置只读**：`apk.setWritable(false,false)`，否则第 6 步必抛 `SecurityException`（Android 14+ DCL 要求）。
6. **加载 dex**：`PathClassLoader(apkPath, context.classLoader)`——parent 必须是宿主 classloader，插件对 `androidx.*` 的引用会被委托给宿主。
7. **实例化**：`clazz.getDeclaredConstructor().newInstance()`，`as? UiPlugin` 做类型校验。
   实测 `instanceof` 跨 dex 判定通过：插件类由 `PathClassLoader[DexPathList[[zip file .../plugins/plugin.apk]]]` 提供，契约类由 `base.apk` 提供。
8. **自洽校验**：插件代码自报的 `apiVersion/compilerVersion` 必须与清单一致（防"改了清单但代码没改"）。
9. **渲染**：宿主在自己的 composable 体内调用 `plugin.PluginTheme { ... }` / `plugin.HeaderBadge()`。实测标记落袋，说明插件 dex 内的 Composable 真的进入组合并被绘制。

### 为什么"插件能调 UI"这件事成立

`@Composable` 编译后在 JVM 层只是多了 `Composer`/`changed` 参数的普通方法。**调用点在宿主源码里**，所以 Composer 管线由宿主编译期生成；插件只负责提供方法实现。这也是为什么不能反过来（宿主反射调用插件 composable）——那样编译器没法生成管线。

---

## 三、必须绕开/接受的三条硬约束

### 约束 1：DCL 只读（可绕，已给方案）

`targetSdk ≥ 34` 起，动态加载的 dex 文件必须只读，否则：

```
java.lang.SecurityException: Writable dex file
  '/data/user/0/<pkg>/files/plugins/plugin.apk' is not allowed.
```

对应到心乐：targetSdk 36，命中。导入流程最后一步必须置只读，并且**重装该插件要走 tmp+rename**，不能就地覆写。

副作用提醒：文件一旦只读，后续 `delete()` 前要先 `setWritable(true, false)`；已被 classloader 持有的旧包在部分场景可能删不掉（句柄未释放），所以"插件升级"应落成**新文件名**（如 `<id>-<versionCode>.apk`）+ 加载后清理旧包，而不是原地替换。

### 约束 2：崩溃无法进程内隔离（不可绕，只能自愈）

**实测推翻了我原本的方案。** Compose 编译器明确拒绝：

```
e: HostActivity.kt:150:9 Try catch is not supported around composable function invocations.
```

也就是说，**没有**"给插件插槽套一层 `runCatching` 就安全"这回事。真实后果实测：

```
FATAL EXCEPTION: main
java.lang.IllegalStateException: 插件故意在组合期抛异常（崩溃隔离测试）
    at cn.com.dcsgo.spikeplugin.SpikePlugin.ThrowingBadge(SpikePlugin.kt:62)
    at androidx.compose.runtime.ComposerImpl.recomposeToGroupEnd(Composer.kt:2895)
→ 进程直接死亡（pidof 为空），宿主没有任何接管机会
```

在两台机器上验证过的唯一可行替代——**下次启动结算（dirty flag 安全模式）**：

- 渲染插件前 `arm(pluginId)` 落盘；正常退出前 `disarm()`。
- 启动时若发现仍是 armed 状态 → 说明上次带插件渲染时崩过 → `crashCount++`。
- 连续 2 次 → 自动禁用该插件、回退内置 UI，并给用户明确提示。

实测输出（点击崩溃按钮两次后重启）：

```
⚠ 安全模式：插件 [dcsgo.spike.vermilion-plus] 连续 2 次崩溃，已自动禁用（防止崩溃循环），可在设置里手动重启用它
```

**这对插件作者的约束是实质性的**：插件里任何未捕获异常都会让用户的 App 崩一次。所以：
- 插件必须**只在子线程做重活**，UI 组合路径要防御性写法；
- 宿主必须在设置页提供"插件安全模式记录 + 手动重启用 + 卸载"，否则用户遇到崩溃循环只能卸载 App；
- 组合期调用插件的入口要**尽量少**（插槽越少，崩溃面越小），且绝不在启动首屏路径上调用插件。

### 约束 3：插件资源只能按 R id 取（不可绕，改变插件写法）

实测结果非常明确：

```
STEP|6. 挂载插件资源|OK|ResourcesLoader.addProvider 成功（API 36，公开 API）| 隐藏 API addAssetPath 成功 cookie=13
STEP|12. 插件资源·按名字查找（表内可见性）|FAIL|
    getIdentifier(cn.com.dcsgo.spikeplugin:string/plugin_label)=0x0, plugin_second=0x0, color/plugin_accent=0x0
STEP|13. 插件资源·按编译期 R id 读取|OK|
    插件 R.string.plugin_label=0x7f020000 → "PLUGIN-RES-OK-7319"；
    plugin_second=0x7f020001 → "PLUGIN-SECOND-5521"；按名字取 → "<未找到 plugin_label>"
STEP|14. 插件资源·编译期 R color 读取|OK|插件 R.color.plugin_accent=0x7f010000 → 0xffff7a18 期望 0xffff7a18
```

即：**provider 注入的资源在运行期 `getIdentifier(name, type, pkg)` 查不到（返回 0x0），但用插件编译期的 `R.string.x` / `R.color.x` 常量直接读，值完全正确。** 两者是不同解析路径。

对插件写法的要求：
- 插件**必须自己编译 R 类**（`com.android.application`/`library` 均可），运行期直接用 `R.string.x` 这类**编译期常量**。
- 插件**不能**指望 `resources.getIdentifier("some_name", "string", "自己的包名")` 取自有资源——一律返回 0。
- 冻结结论：其他插件要拿资源，也一律走编译期 R 常量读取，不要用 getIdentifier。

**补充风险（防止 id 串号）**：插件 R id 与宿主同处 `0x7f` 命名空间。实测同一宿主内：

```
宿主:   resource 0x7f010000 attr/alpha
插件:   resource 0x7f010000 color/plugin_accent   ← 同号不同型，未冲突（type 段不同，各自表内解析）
宿主:   resource 0x7f020000 color/androidx_core_ripple_material_light
插件:   resource 0x7f020000 string/plugin_label   ← 同上
```

型别不同时系统按各自资源表解析，未观察到串号。但这条安全是**脆的**：不同 AGP/资源排序下 id 可能真的撞上同型资源（插件 `string` 与宿主 `string` 同号）。**对策**：插件协议不要暴露裸 R id 给宿主；插件一切资源（颜色/尺寸/图标）通过 API 方法**返回值或常量**传给宿主，宿主永远不拿裸 id 去查。这样即使 id 撞号也不会串。已写入设计文档的"禁令"清单。

---

## 四、给正式实现的设计改动（基于实测修正）

### 4.1 插件包契约

```json
{
  "id": "dcsgo.skin.vermilion-plus",
  "name": "朱砂+ 演示皮肤",
  "apiVersion": 1,
  "compilerVersion": "2.0.21",
  "minHostVersionCode": 35,
  "entry": "cn.com.dcsgo.spikeplugin.SpikePlugin",
  "slots": ["theme", "player.background"]
}
```

清单读法：`ZipFile` 直读 `assets/plugin-manifest.json`，不走资源系统。

### 4.2 闸门必须放在"加载任何代码之前" + 加载后的自洽校验

实测三负向场景（`apiVersion=99`、`compilerVersion=9.9.9`、入口类不存在）宿主均**保持内置 UI 且无崩溃**。

### 4.3 插件禁用/自愈状态

```
filesDir/plugins/<id>-<versionCode>.apk     ← 只读，新版本落新文件名
SharedPreferences/Datastore:
  armed_plugin      渲染时 arm，正常退出 disarm
  crash_count:<id>  启动结算累加
  disabled_plugin   达阈值写入
```

设置页必须有：插件列表、启用/禁用、卸载、崩溃计数展示、安全模式提示与手动重启用。

### 4.4 宿主侧护栏（R8 / 架构门禁）

- `-keep` 契约接口与实现类；插件包自身不开 minify。
- 插件**禁止打包 `androidx.*`**（实测 `compileOnly` 策略下 dex 内 Compose 类数 = 0，必须在插件模板与校验里强制：加载前解压 dex 扫 androidx 前缀，非空就拒绝）。
- `verifyProductArchitecture` 需新增模块与禁令检查（`:plugin:api` 存在、feature 不依赖 plugin host 等）。

### 4.5 心乐现有代码的改造点

- `ThemeVariant`（现为 `enum MONO/VERMILION`）→ 改 `id` 字符串 + 注册表，否则插件无法扩展主题。
- `core/ui/theme/Color.kt` 的 `ThemePalette`（现 `internal`）→ 上提为公开令牌，插件才能构造。
- 设置页新增"插件"分组；导入走现有 SAF 链路（`PersistableOpenDocumentTree`）。

---

## 五、工程可信度说明

- 所有 PASS 断言都带**运行期取样**：插件 composable 执行证据来自宿主内存标记（类由宿主 dex 提供，插件写入即证明代码真的跑了）。
- 负向对照使用**内容确实不同**的插件包（`zipfile` 重写 `assets/plugin-manifest.json` 生成 3 个变体），不是"在代码里假装拒绝"。
- 资源结论由**两条独立路径同时对照**（宿主 `getIdentifier` vs 插件 `R` 常量）得出，避免单路径误判。
- 崩溃行为由 `pidof` + `logcat -b crash` 双重取证。
- 复现命令见本报告附录 / 工程内 `tools/stage_plugin.sh`。

### 附录：spike 复现方式

```bash
cd /root/mihx-plugin-spike
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=/opt/android-sdk
./gradlew :host:assembleDebug --no-daemon          # 构建期自动产出 4 个插件包变体
adb install -r host/build/outputs/apk/debug/host-debug.apk
adb shell am start -n cn.com.dcsgo.pluginhost/.HostActivity
adb logcat -s SPIKESTEP:I | grep -E "RESULT\||RESPROBE\||STEP\|"
```

宿主台界面含 4 个场景断言 + 1 个"触发组合期异常"按钮（验证安全模式自愈）。
