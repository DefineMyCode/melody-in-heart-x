# 安卓端运行期编译 Kotlin + Jetpack Compose 源码并渲染 —— 可行性调研报告

日期：2026-09-29　分支：`plugin-ui`　调研方式：**文献检索 + 本机可复现实验**
实验环境：Linux x86_64 / OpenJDK 21.0.12 / Android SDK `android-36`（android.jar 6227 个类）/ D8 36.0.0
实验产物目录：`/tmp/feas`（脚本与日志保留，可复跑）
相关前置文档：[`PLUGIN_SYSTEM_SPIKE_REPORT.md`](./PLUGIN_SYSTEM_SPIKE_REPORT.md)（插件化 T2 路线，已跑通）

> 本文区分两类证据：
> **【实测】**= 本次在本机真实跑出来的；**【文献】**= 来自公开资料/官方文档，附链接。
> 凡是文献与实测冲突或无法验证的，明确标注「未找到可靠来源」。

---

## 一、结论速览

> **2026-09-29 真机复验更新**：本报告初版的所有实测量都在桌面 JVM 上完成（报告自己
> 标注过这个弱点）。拿到可用模拟器后，**在真安卓运行时（ART / API 36 / x86_64）上
> 复跑了同一批实验**，见 §九。复验**强化了主结论（❌ 不可行）**，但**推翻了初版的
> 两条具体论断**，且**发现真正的第一阻断点与初版预测不同**。下表已按真机结果修订。

| # | 结论 | 证据强度 |
|---|---|---|
| **1** | **不可行。** 运行期在原 App 进程内编译 Compose 源码，这条路被平台层阻断，不是工程量问题 | **【真机实测】硬阻断** —— 编译器在**初始化阶段**就崩（§九） |
| **2** | **技术上「能跑」的唯一形态是"把一整个 JDK 搬进 APK 并当子进程拉起来"。** 代价约 +60MB+ 产物、被 Google Play W^X 政策排除、无法内嵌进现有 App | **【实测】+【文献】** |
| **3** | **正确解法是另一条路：不要编译。** Google 官方的 androidx RemoteCompose 与 A2UI 正是为"运行期动态 UI 且不做源码编译"设计的，且官方明说 A2UI 的目标是 `without executing arbitrary code` | **【文献】官方一手** |

### ★ 真机复验推翻了初版的两条论断（详见 §九）

| 初版论断 | 真机事实 | 影响 |
|---|---|---|
| 「安卓运行时**没有** `LambdaMetafactory`」 | ❌ **错**。ART 运行时**有**，且 `metafactory`/`altMetafactory` 两个方法都在。它只是不在 `android.jar` 这个**编译期 stub** 里 | 论证方式要改：不能拿 `android.jar` 反向推断运行时能力 |
| 「崩在 `VirtualFileManagerImpl.<init>`，读源码之前」 | ⚠️ **不准确**。那是桌面 JVM 削模块时的崩点；**真机上越过了它**，真正第一堵墙是 `sun.misc.Unsafe.copyMemory` 的**方法签名缺失** | 阻断点更靠后、也更本质：**类存在但方法签名不同**（类级探测查不出） |

**主结论不变且更强**：真机上编译器仍无法完成初始化。而且暴露了一个初版没有的
**方法论陷阱**——只看「类在不在」会得出错误结论，因为安卓的 `sun.misc.Unsafe`
**类在、55 个方法在，但缺 `copyMemory(Object,long,Object,long,long)` 这个 5 参重载**（安卓只有 3 参 `(long,long,long)`）。详见 §九。

> **关于「目标 JDK = 9」那条初版推测的处理**——初版在没有 JVM 运行时上下文的前提下，
> 推测"如果能跑编译器，安卓很可能是按 JDK 9 之类的兼容层提供"。真机实测给出的真值是
> `java.specification.version = "0.9"`（§9.4），这是 ART 的 Dalvik 兼容标识
> （[Dalvik Bytecode Specification §"Versioning"](https://source.android.com/devices/tech/dalvik/dex-format#dex-file-magic)），
> **不是任何标准 JDK 版本号**。所以这条初版推测**被否证了**，但方向刚好相反——
> 比想象的更低（0.9 vs 9），意味着任何按"标准 JDK 9/11/21"分支的代码都会走错路。
> 详见 §9.6。

### 对「是否可作为主路线」的判断

> **不能作为主路线，也不建议作为支线。**

三条理由，按否决力度排序：

1. **平台层否决（不可绕）**：Kotlin 编译器是 IntelliJ 平台应用（24941 个类），它的初始化路径依赖 `sun.misc.Unsafe`、`javax.swing`、`java.desktop`、`java.compiler`（`javax.tools`/`javax.lang.model`）等**安卓运行时根本没有的 JDK 模块**。实测：给足 `jdk.unsupported`+`java.compiler`+`java.management` 后仍失败，最后一道硬门槛是 `java.desktop`；把 JDK 模块图削减到安卓等效面（`java.base+logging+xml`）时**在虚拟机初始化阶段（`VirtualFileManagerImpl.<init>`）就崩了**，连一行源码都没读到。
2. **产品层否决（不可绕）**：活下来的形态必须 `targetSdk` 压到 28 以下（因为 Android 10+ 禁止在 app 私有目录 `execve()`），而 Google Play 自 2026-08-31 起要求新应用/更新 `target API 36`。也就是说这条路**只存在于侧载场景，进不了 Play**。且它是"外挂一个 IDE"，**不是**你 App 里的一个功能。
3. **收益/成本完全不成比例**：即便把上面两条都忍了，得到的是一台冷启动约 12 秒、需搬运 63MB 产物、且会持续引入"编译失败"用户工单的机器；而你要的"运行期改 UI"用 RemoteCompose/A2UI 就能拿到，且是 Google 在 AndroidX 里维护的正规 API。

**唯一值得留的口子**：如果未来诉求变成「用户在设备上写代码、编译、装 APK」这种**独立工具型 App**（本质是 AndroidIDE / Termux 的形态），那它是可行的，但那是**另一个产品**，与心乐的插件/UI 定制诉求无关，也不该塞进心乐主 App。

---

## 二、先厘清：这个问题的四种解读，难度天差地别

"运行期编译 Kotlin + Compose" 实际上混着四个不同难度的问题，必须拆开，否则永远吵不清：

| 解读 | 含义 | 可行性 |
|---|---|---|
| **A. 运行期编译任意 Compose 源码** | App 里塞编译器，读用户/下发的 `.kt`，编译成 dex 并执行 | ❌ **本报告否决的就是这个** |
| **B. 运行期生成 Compose UI（不编译）** | 用数据（JSON/二进制文档）描述 UI，交给预置渲染器画 | ✅ **现实可行**（RemoteCompose / A2UI） |
| **C. 安装期外挂编译** | 目标 App 之外，另一个进程/App 编译，产物装进来 | ⚠️ 可行但属独立工具，见 AndroidIDE/Termux |
| **D. 运行期加载预编译 dex** | 插件包里已经是 dex，只是加载 | ✅ **心乐已跑通**，见 `PLUGIN_SYSTEM_SPIKE_REPORT.md` |

**心乐当前的需求（插件化 UI 定制）落在 B 和 D 的区间，压根不需要 A。** 这份报告把 A 彻底否掉，是为了让 B/D 的选择站得更稳。

---

## 三、为什么 A 不可能：三条硬阻断

### 阻断 1：Kotlin 编译器依赖的 JDK 模块，安卓一个都没有

Kotlin 编译器的实现是 **IntelliJ 平台应用**，不是"一个能嵌进 App 的小库"。

**【实测】层次一：类引用统计**（解析 class 文件常量池，比对 `android-36/android.jar`）

| 构件 | 自身类数 | 引用的 JDK 类 | 安卓缺失的 JDK 类 | 涉及缺失类的编译器类数 |
|---|---|---|---|---|
| `kotlin-compiler-embeddable-2.0.21.jar` | **24 941** | 612 | **146** | **2 889** |
| `kotlin-compose-compiler-plugin-embeddable-2.0.21.jar` | 274 | 51 | 1 | 25 |

缺失的 JDK 类按包分布（编译器侧引用该类的地方越多，越致命）：

| 缺失的 JDK 包 | 引用它的编译器类数 | 安卓为何没有 |
|---|---|---|
| `java/lang/invoke/LambdaMetafactory` | **2 442** | ⚠️ **初版此处论断已被真机推翻**：ART 运行时**有**这个类（见 §九）。此处 0 命中是因为 `android.jar` 是**编译期 stub**，不含实现类——**不能拿它反推运行时能力** |
| `com/sun/tools/javac/*`（JCTree 等） | 181 | 安卓不带 `jdk.compiler` |
| `javax/lang/model/*` | 116 | 安卓不带 `java.compiler`（**文献二**） |
| `javax/xml/stream/*`（StAX） | 23 | 安卓 `java.xml` 不含 StAX |
| `com/sun/source/*` | 19 | 同上 |
| `javax/swing/Icon` | 17 | 安卓不带 `java.desktop`（**文献三**） |
| `java/lang/management/*` | 13 | 安卓不带 `java.management` |
| `javax/annotation/processing/*` | 11 | 安卓不带 `java.compiler` |

**【实测】层次二：直接查 android.jar（android-36，6227 个类）**

```
javax/tools              0        ← java.compiler 缺失
javax/lang/model         0        ← java.compiler 缺失
javax/script             0        ← JSR-223 缺失
javax/swing              0        ← java.desktop 缺失
javax/xml/stream         0        ← StAX 缺失
java/lang/management     0        ← java.management 缺失
com/sun/tools            0
com/sun/source           0
sun/misc                 0        ← jdk.unsupported 缺失
java/lang/invoke         18       ← 有 MethodHandle；无 LambdaMetafactory（仅限本 stub！）
java/util/logging        18       ← 有（这一项安卓是有的）
java/beans               6        ← 有 6 个 PropertyChange* 类（部分有）
```

### 阻断 2：把 JDK 模块图削到"安卓等效面"，编译器在初始化阶段就崩

**【实测】手法**：用 `--limit-modules` 把 JVM 的平台模块图削减到安卓 libcore 的实际等效面，观察 Kotlin 编译器+Compose 插件还能不能跑。这个手法**同时暴露了"编译器依赖哪些平台模块"和"缺了会怎样"**。

| 提供的平台模块 | 结果 | 首个错误 |
|---|---|---|
| 完整 JDK | ✅ 编译成功（基线） | — |
| `java.base,java.logging,java.xml`（≈安卓 libcore） | ❌ | `ExceptionInInitializerError` → `sun.misc.Unsafe` 不存在 |
| 上面 + `jdk.unsupported` | ❌ | `NoClassDefFoundError: javax.swing.Icon` |
| 上面 + `java.compiler` | ❌ | `NoClassDefFoundError: javax.swing.Icon` |
| 上面 + `java.management` | ❌ | `NoClassDefFoundError: javax.swing.Icon` |
| 上面 + **`java.desktop`** | ✅ **成功** | — |

**最小可用模块集：`java.base, java.logging, java.xml, jdk.unsupported, java.compiler, java.management, java.desktop`。**
按上表，安卓提供的只有前两项（`java.base` 部分能力 + `java.logging` + `java.xml` 部分）。**缺 5 项，且每一项都是不可协商的初始化依赖。**

失败时的真实栈（削减到安卓等效面时）：

```
exception: java.lang.ExceptionInInitializerError
  at org.jetbrains.kotlin.com.intellij.util.EventDispatcher.createMulticaster
  at ...openapi.vfs.impl.VirtualFileManagerImpl.<init>
  at ...core.JavaCoreApplicationEnvironment.<init>
  at ...cli.jvm.compiler.KotlinCoreApplicationEnvironment.<init>
```

注意栈的位置：**`VirtualFileManagerImpl.<init>` —— 虚拟机文件系统初始化，发生在读取任何 `.kt` 文件之前。** 而 `java.desktop` 是最后一道门槛，缺了它编译器要求的 JDK 表面为 7 项、安卓提供 2 项。

### 阻断 3：把 JDK 塞进 APK 也不成立（三条独立理由）

理论上你可以自带 `java.desktop` 等缺失模块。实测了这个方向：

**【实测】自动 stub 投喂实验**（在安卓等效模块集下，缺什么就生成什么 stub）

- 第 1 轮：需要 `sun.misc.Unsafe` → 已生成。
- 第 2 轮：**立即死在 stub 保真度上**：`RuntimeException: Could not find 'theUnsafe' field in the Unsafe class`。

也就是说，编译器通过**反射读私有字段**（`Unsafe.theUnsafe`）来工作。这意味着要自带 JDK，你需要的是**行为完全一致的 JDK 实现**，不是 API stub。而 `sun.misc.Unsafe` 在安卓上就算有实现也**语义不可用**（下面的文献四说明原因）。这条链断在最前面，不是"多补几个类就能过"。

**【文献】三条独立否决**：

- **文献一（LambdaMetafactory）**：Jake Wharton《Android's Java 8 Support》原文：*"If you look at the Android documentation for `java.lang.invoke` or the AOSP source code for `java.lang.invoke`, though, you'll notice this class isn't present in the Android runtime. This is why desugaring always happens at compile-time regardless of your minimum API level."*
  <https://jakewharton.com/androids-java-8-support/>
  → **Kotlin 编译器自身的 2442 个类引用 `LambdaMetafactory`（源码里的 lambda），而安卓运行时没有这个类。** 这是最深的一条：它意味着编译器必须被 D8 脱糖后运行，而编译器又需要 JDK 反射语义，两者互相矛盾。
  AOSP 侧确认：`LambdaMetafactory` 只存在于构建期使用的 stub jar —— <https://android.googlesource.com/platform/libcore/+/master/ojluni/src/lambda/java/java/lang/invoke/LambdaMetafactory.java>

- **文献二 / 文献三（javax.lang.model / javax.swing 在安卓不可用）**：Rhino 项目 #1149，因引入 `javax.lang.model.SourceVersion` 导致 Rhino **在安卓上彻底不可用**，最后只能靠「自己伪造一个 `javax.lang.model.SourceVersion`」这种被作者本人称为 *"feels very hacky"* 的土办法绕过。报告里的原始理由一句话概括：*"`javax.lang` is not available on Android"*。
  <https://github.com/mozilla/rhino/issues/1149>
  → **一个只用了 `javax.lang.model` 里一个类的库都会挂，而 Kotlin 编译器用了 22 个。**

- **文献四（`sun.misc.Unsafe` 语义不可用）**：无直接的"安卓上 Unsafe 不可用"官方文档，但可从 AOSP libcore 的 `ojluni/src/main/java` 目录**不含** `sun/misc/Unsafe.java`、且 Unidroid/Jake Wharton 系列均未把 `jdk.unsupported` 列为安卓可用模块推断得到。**此处标注为间接推断，未找到一条权威直述链接。**

---

## 四、「能把编译器跑起来」的那条路：AndroidIDE / Termux 模式，及其代价

这是**唯一真实存在**的形态。搞清它的机制，就知道为什么它帮不上心乐。

**【实测】关键一步是成立的**：用 D8 把编译器 dex 化，成功了。

| 输入 | 结果 |
|---|---|
| Compose 编译器插件（0.9MB）+ stdlib | ✅ 3.3MB dex（无报错） |
| 编译器全套 8 个 jar（63MB） | ✅ 6 个 dex，共 **62MB** |

**【实测】D8 对缺失 JDK 类无警告**：`LambdaMetafactory`、`javax/lang/model/element/Element`、`com/sun/tools/javac`、`javax/swing/Icon` 等**全部原样留在 dex 里**（D8 只做脱糖/转换，不做类存在性校验）。所以"D8 成功"骗不了人——**问题不在 dex 化，在运行期**。

**【文献】AndroidIDE 的真实架构**（官方文档 <https://docs.androidide.com/tutorials/get-started.html>）：

- 它不是一个 App 内嵌编译器，而是**装一个真正的 OpenJDK**：`idesetup` 脚本下载并安装 `openjdk-17` 包（或 21），以及完整 Android SDK。
- 官方最低要求原文：**"A minimum of 1.5GB - 2GB free RAM"**、**"A minimum of 4GB free storage space"**、安装后基础占用约 **1GB**。
- 编译通过 **Termux 终端**里的 `javac`/Gradle 以**独立进程**跑，不是在你的 App 进程里。
- 它的 `javac`（`nb-javac-android`，现已并入 AndroidIDE）是**专门 patch 过的 OpenJDK javac 分支** —— <https://github.com/AndroidIDEOfficial/nb-javac-android>
- 它有 **`openjdk-17-android` 这个专门的 OpenJDK 安卓移植仓**（`itsaky/openjdk-17-android`）—— <https://github.com/itsaky/openjdk-17-android>
  这两个仓的存在本身就是结论：**"在安卓上跑 JDK/编译器"需要单独移植一整个 OpenJDK，不是一个依赖引入的事。**

**【文献】这条路必须付的三项代价**：

1. **必须绕开 Android 10+ 的 W^X 限制**。官方行为变更原文：*"Untrusted apps that target Android 10 cannot invoke `execve()` directly on files within the app's home directory."*
   <https://developer.android.com/about/versions/10/behavior-changes-10>
2. **代价就是把 `targetSdk` 压在 28 以下 → 进不了 Google Play。** Termux 就是活案例：为绕开这项限制保持 **targetSdk 28**，直接被 Play 政策挡在门外，只能改走 F-Droid。
   <https://github.com/termux/termux-play-store> ／ <https://www.xda-developers.com/termux-terminal-linux-google-play-updates-stopped/>
   而 Play 的现行要求：**2026-08-31 起新应用与更新必须 target API 36**
   <https://support.google.com/googleplay/android-developer/answer/11926878>
   → *"Termux 保持 targetSdk 28 才避开这项限制"* 为**社区一手说明**（Play Store 组织 README）；Google 官方文档只直述了 execve 限制与 API 36 要求，**未找到 Google 官方文档明说"降 targetSdk 是标准绕过手段"**。
3. **第三方后台复杂度高**：Termux 上构建需手动覆盖 `aapt2`（`android.aapt2FromMavenOverride`）、且 aapt2 版本受限（*"your project should target SDK 34 or lower"*）。
   <https://stackoverflow.com/questions/76495183/aapt2-error-when-building-an-android-app-using-gradle-on-termux>
   （Stack Overflow 页面本次被反爬拦住，该引文来自搜索结果摘要，**未取得页面全文**——标注为**弱证据**。）

**【实测】构建产物成本**：编译器全套 **63MB**（`kotlin-compiler-embeddable` 56MB 独占大头）。这是**未压缩**的 Maven 产物；打进 APK 后 dex 侧实测 **62MB**（6 个 dex）。加上 AndroidIDE 场景要带 SDK/JDK，与官方"4GB 存储、1.5–2GB 空闲内存"的要求互相印证。

**【实测】编译耗时与内存**：本机（5 核 / x86_64 / 桌面 JVM）编译**一个** 8 行的 `@Composable` 函数：

| 堆设置 | 耗时 | 结果 |
|---|---|---|
| `-Xmx256m` | 12.69s | ✅ 成功 |
| `-Xmx384m` | 11.64s | ✅ 成功 |
| `-Xmx512m` | 11.85s | ✅ 成功 |
| `-Xmx2g` | 13.37s | ✅ 成功 |

**内存下限不错（256MB 够），但冷启动 ~12 秒是纯开销**（JVM 启动 + IntelliJ 平台初始化 + 编译器前端），且**这是 x86_64 桌面 CPU 的数字**。参考 Kotlin 团队 yole 的官方表态（**文献五**）：

> *"the Kotlin compiler has fairly high CPU and memory requirements, which means that compiling even small files on old Android devices could take minutes."*
> <https://discuss.kotlinlang.org/t/how-to-use-kotlin-compiler-on-android/6513>

同一帖子里的历史结论同样值得记录：要在 Android 上跑该编译器，实践者当时的做法是**把 JRE 搬到 `/system/bin` 以获取执行权限**（root 或终端模拟器），另有实践者明确说 *"the conditions (terminal simulator and root access) are not possible for my project"*。**即在真机上跑成这件事的历史路径普遍需要 root / 终端环境。**

> **注意**：上述 AndroidIDE 数据与 yole 表态均为**文献**；本报告**未在真实安卓设备上复现** AndroidIDE 或真机编译（本机无 Android 设备/模拟器可用，`adb` 未安装）。所以「真机上能否跑通」这一环，我**只做了机制与文献层面的确认，没有实测**。这是本报告最大的证据缺口，明确声明。

---

## 五、正确解法：不编译，而是「传输 UI」

结论 3 的支撑，是 Google 官方在 AndroidX 里给的两条正规路径。**关键点是二者都刻意不做源码编译**：

### 路径① androidx RemoteCompose（官方，推荐）

**【文献】官方发布页**：<https://developer.android.com/jetpack/androidx/releases/compose-remote>
截至 **2026-09-23 为 `1.0.0-alpha20`**（仍是 alpha，无 stable/beta）。

官方描述：*"Remote Compose is a framework to create UI for remote surfaces"*。

机制（把 Compose 的**绘制操作**序列化成可传输的二进制文档，客户端只执行绘制指令，不需要知道"按钮"是什么）：

| 端 | 构件 | 说明 |
|---|---|---|
| 创建侧 | `remote-creation-core` / `-jvm` / `-android` / `-compose` | 可在**纯 JVM（服务端）**跑，不需要 Android SDK |
| 播放侧 | `remote-core` / `remote-player-core` / `remote-player-view` | **在安卓端渲染** |

【实测】我核对了 Maven 元数据确认上述构件真实存在且版本齐至 alpha20：
`https://dl.google.com/dl/android/maven2/androidx/compose/remote/group-index.xml`

注意架构含义：**"生成 UI 文档"发生在服务端 JVM，"渲染"发生在设备端**。设备端**不编译任何源码**。这正是"运行期动态 UI"的正解形态。

### 路径② Compose A2UI renderer（官方一手，措辞最直接）

**【文献】官方文档**：<https://developer.android.com/develop/ui/compose/agentic>（最后更新 2026-09-25）

官方原文（这句直接就是本报告的答案）：

> *"The Jetpack Compose agent-to-UI (A2UI) renderer provides an implementation of the A2UI protocol, enabling AI agents to generate rich, interactive user interfaces that render native Compose components —— **without executing arbitrary code**."*

> *"Every A2UI interface is driven by a **component catalog**. Rather than having an agent generate arbitrary UI code or invent unregistered components, the catalog acts as a **contract** that defines the specific UI elements, properties, and functions available to the agent."*

构件：`androidx.a2ui.compose:compose-runtime` / `compose-ui` / `androidx.a2ui:a2ui-model` / `a2ui-engine` / `androidx.compose.material3:material3-a2ui`；支持 A2UI 规范 **0.9.1**。

**Google 明确选择了"目录 + 契约"而不是"让模型生成代码"**，理由是安全与可验证性。这就是本报告否定"运行期编译"这一诉求的**产品层依据**：**连 Google 自己在做"AI 动态生成 UI"时都刻意回避了运行期编译代码。**

### 路径③（社区，作为对照）JSON → Compose 渲染器

- `lmee/A2UI-Android`：A2UI 协议的 Compose 渲染器，20+ 组件、数据绑定、校验，Android 5.0+。<https://github.com/lmee/A2UI-Android>
- `vvsdevs/RemoteCompose`：JSON 配置驱动动态布局。<https://github.com/vvsdevs/AndroidDynamicJetpackCompose>

两者机制相同：**预置组件集合 + 数据描述**，不编译。

### 路径④ Zipline（若要的是"下发热更代码"而非"UI"）

**【文献】** Cash App 的 Zipline：*"Zipline works by embedding the QuickJS JavaScript engine in your Kotlin/JVM or Kotlin/Native program"*，让**运行期下发的 Kotlin/JS** 在 Android 与 iOS 上跑。
<https://github.com/cashapp/zipline> ／ <https://code.cash.app/zipline>

**但关键限制**：它是 **Kotlin/JS + QuickJS**，能做到"下发逻辑热更"，**做不到"下发并渲染原生 Compose UI"**（拿不到 Composer 管线）。官方自述也明确：*"we plan to switch to WebAssembly as our dynamic code platform once Kotlin support is stable"*。**对心乐的 UI 定制诉求不适用。**

---

## 六、如果非要强行推进，四条路线的代价表

| 路线 | 机制 | 可行性 | 主要代价 | 进得了 Play？ |
|---|---|---|---|---|
| **A. 内嵌编译器** | APK 里带 kotlinc，进程内编译 → dex → 加载 | ❌ **实测否决** | 崩在 `VirtualFileManagerImpl.<init>`；缺 5 个 JDK 模块 | — |
| **B. 外挂 JDK + 子进程**（AndroidIDE/Termux 形态） | 装完整 OpenJDK，独立进程跑 javac/Gradle | ✅ 可行 | +63MB 编译产物、+1GB 安装、4GB 存储、1.5–2GB 内存、冷启动 ~12s、**且是"另一个 App"** | ❌ 需 targetSdk≤28 |
| **C. 服务端预编译 dex 下发** | 服务端编译成 dex，设备端只加载 | ✅ **已跑通** | 需 dex 版本闸门；崩溃无法进程内隔离（见前置报告） | ✅ |
| **D. 数据驱动 UI（不编译代码）** | RemoteCompose / A2UI 文档 → 预置渲染器 | ✅ **官方推荐** | RemoteCompose 仍 alpha（1.0.0-alpha20），API 会变 | ✅ |

**心乐的正解区间是 C + D**：C 已在 `PLUGIN_SYSTEM_SPIKE_REPORT.md` 中跑通（插件 APK 动态加载 + 渲染）；想要"更动态"就叠加 D（RemoteCompose/A2UI），而不是滑向 A。

---

## 七、给心乐的具体建议

1. **不做运行期源码编译。** 本文档即可作为否决依据，避免后续重复讨论。
2. **插件化继续走 C 路线**（预编译 dex 加载），沿用已有 spike 结论与两条硬约束（DCL 只读、崩溃无法进程内隔离）。
3. **要"运行期生成 UI"就走 D**。优先评估 RemoteCompose 的 **player 侧**（`remote-core` + `remote-player-core`），创建侧放在你自己的服务端。**注意它还是 alpha20，API 会变，不要拿它做长期稳定契约**——建议先按 spike 方式小范围验证，别直接进主链路。
4. **如果诉求实为「AI 生成界面」**，直接对齐 A2UI 协议与 catalog 思路（Google 的做法：**目录即契约**），这条路天然免疫"用户代码把我们 App 搞崩"的问题——而这正是前置 spike 里"崩溃无法进程内隔离"那条硬约束的根治办法。
5. **本节所有结论仅来自文献，未包含真机实测**（见第四节末的声明）。若要把 D 作为规划依据，建议先补一次 RemoteCompose 的**设备端 spike**。

---

## 八、证据索引

### 一手来源（本次实际抓取/运行）

| 来源 | 链接 | 用途 |
|---|---|---|
| AndroidX Remote Compose 发布页 | <https://developer.android.com/jetpack/androidx/releases/compose-remote> | 结论 3；alpha20 / 2026-09-23 |
| Compose A2UI renderer 官方文档 | <https://developer.android.com/develop/ui/compose/agentic> | 结论 3；"without executing arbitrary code" 原文 |
| Android 10 行为变更（execve/W^X） | <https://developer.android.com/about/versions/10/behavior-changes-10> | 阻断 3 / 路线 B 代价 |
| Google Play target API 要求 | <https://support.google.com/googleplay/android-developer/answer/11926878> | 路线 B 的 Play 否决 |
| AndroidIDE 官方文档 | <https://docs.androidide.com/tutorials/get-started.html> | 路线 B 机制与资源要求 |
| Jake Wharton：Android's Java 8 Support | <https://jakewharton.com/androids-java-8-support/> | LambdaMetafactory 不在 Android 运行时 |
| Rhino #1149 | <https://github.com/mozilla/rhino/issues/1149> | javax.lang.model 在安卓不可用 |
| Kotlin 论坛：How to use Kotlin compiler on Android | <https://discuss.kotlinlang.org/t/how-to-use-kotlin-compiler-on-android/6513> | yole 官方表态（CPU/内存、无现成库） |
| Kotlin 论坛：Compile kotlin source dynamically | <https://discuss.kotlinlang.org/t/compile-kotlin-source-dynamically/21109> | K2JVMCompiler 在安卓崩于 javax |
| nb-javac-android | <https://github.com/AndroidIDEOfficial/nb-javac-android> | javac 需专门移植 |
| openjdk-17-android | <https://github.com/itsaky/openjdk-17-android> | 同上 |
| Termux on Google Play | <https://github.com/termux/termux-play-store> | targetSdk 28 与 Play 的取舍 |
| Cash App Zipline | <https://github.com/cashapp/zipline> | 路径④ 的适用边界 |
| androidx remote 构件清单（实测抓取） | <https://dl.google.com/dl/android/maven2/androidx/compose/remote/group-index.xml> | 构件存在性核验 |

### 本次实验产物（可复跑）

脚本与原始日志已随文档一并入库：`docs/architecture/evidence-runtime-compile/`
（实验时的原始工作目录为 `/tmp/feas`，jar 依赖可按下文 Maven 坐标重新拉取）

| 文件 | 作用 |
|---|---|
| `evidence-runtime-compile/cpscan.py` | 常量池精确扫描：编译器 jar 对 android.jar 的类引用差集 |
| `evidence-runtime-compile/count2.py` | 统计"有多少编译器类引用了缺失的 JDK 类" |
| `evidence-runtime-compile/definitive.py` | **决定性实验**：程序化调用 Compose 插件，javap 对比前后字节码 |
| `evidence-runtime-compile/simulate2.py` / `probe2.py` | 用 `--limit-modules` 削减平台面，定位缺失模块 |
| `evidence-runtime-compile/vendor_faithful.py` | 自动 stub 投喂，测"自带 JDK"的可行性上限 |
| `evidence-runtime-compile/timing.py` | 冷启动耗时与内存下限 |
| `evidence-runtime-compile/vendor_faithful3.log` / `timing.log` / `full_failure.txt` | 原始日志 |

复跑所需 Maven 坐标（Kotlin 2.0.21）：`kotlin-compiler-embeddable`、
`kotlin-compose-compiler-plugin-embeddable`、`kotlin-stdlib`、`kotlin-reflect`、
`kotlin-script-runtime`、`kotlin-daemon-embeddable`、
`org.jetbrains.intellij.deps:trove4j:1.0.20200330`、
`org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.8.1`；
Compose 运行时取 `androidx.compose.runtime:runtime-android:1.7.5` 的 `classes.jar`。

### 关键「未找到可靠来源」声明

- ❌ **未找到** Google/JetBrains 官方"明确禁止在安卓上运行 Kotlin 编译器"的直述文档。本文的否决结论**由实测硬阻断 + 官方能力边界文档共同支撑**，而非某一条禁令。
- ❌ **未找到** 任何**开源项目实现"运行期编译 Compose 源码并在本进程渲染"**。检索到的同类项目（AndroidIDE、Termux、Jdroid、A2UI-Android、RemoteCompose）**无一采用该形态**，全部走"数据驱动"或"独立进程 + 完整 JDK"。这一"无人做成"本身是强旁证，但**不能替代直接证据**。
- ✅ **`sun.misc.Unsafe` 在安卓的可用性 —— 已由真机实测取代间接推断**（初版此处标为"未找到权威来源"）：真机上 `sun.misc.Unsafe` **类存在且声明了 55 个方法**，但**缺 `copyMemory(Object,long,Object,long,long)` 5 参重载**（安卓只有 `copyMemory(long,long,long)`），这正是编译器的第一阻断点。见 §九。
- ⚠️ **Termux 的 aapt2 限制引文**：页面被反爬拦截，仅取到搜索摘要，**未获全文**。
- ~~⚠️ **本报告未在真实安卓设备上实测**~~ → **已于 2026-09-29 补做**：在 tfl_smoke 模拟器（API 36 / x86_64 / ART）上用 `app_process` 直接拉起真实编译器复验，**推翻了本报告的两条论断**（§九）。这是本报告最重要的修正。

---

---

## 九、真机复验（2026-09-29 补做）—— 推翻了初版两条论断

初版所有实测量都在桌面 JVM 上完成，报告自己把它列为弱点。拿到可用模拟器后
（**tfl_smoke / API 36 / x86_64 / ART**），用 `app_process` 直接把真实产物拉起来复验。

方法（不需要装 App，绕开一切打包干扰）：

```bash
# 1) 把 kotlin-compiler-embeddable 2.0.21 用 D8 转成 dex（6 个 dex，共 62MB）
# 2) 打成一个 jar，推进设备
adb push kc.jar /data/local/tmp/kc.jar
# 3) 直接用 ART 拉起编译器本体
adb shell "CLASSPATH=/data/local/tmp/kc.jar app_process /system/bin \
  org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-jdk -no-stdlib \
  -d /data/local/tmp/out /data/local/tmp/src/Hello.kt"
```

### 9.1 ★ 推翻一：`LambdaMetafactory` 在安卓运行时**是存在的**

初版据 `android.jar` 里查不到该类，断言"ART/libcore 未实现"。**真机实测推翻**：

```
LambdaMetafactory 方法:
  public static CallSite LambdaMetafactory.altMetafactory(Lookup,String,MethodType,Object[])
  public static CallSite LambdaMetafactory.metafactory(Lookup,String,MethodType,MethodType,MethodHandle,MethodType)
```

**根因（方法论错误）**：`android.jar` 是给编译器看的 **stub jar**，只含公开 SDK 面，
**不等于运行时能力**。拿它反推"运行时有没有"在原理上就是错的。
（注：Jake Wharton 关于脱糖的那篇仍是正确的——它讲的是**编译期脱糖策略**，不是运行时类缺失。）

### 9.2 ★ 推翻二：真正的第一阻断点**不是** `VirtualFileManagerImpl`

初版预测"崩在 `VirtualFileManagerImpl.<init>`，读任何源码之前"。真机上**越过了它**，
停在更靠后、也更本质的位置：

```
error: no class roots are found in the JDK path: /apex/com.android.art     ← 先撞 JDK 检测
（加 -no-jdk -no-stdlib 绕过后）
java.lang.Error: java.lang.NoSuchMethodException: copyMemory [Object, long, Object, long, long]
    at com.intellij.util.ConcurrentLongObjectHashMap.<clinit>
    at com.intellij.util.Java11Shim$Companion$INSTANCE$1.createConcurrentLongObjectMap
    at com.intellij.core.CoreApplicationEnvironment.createProgressIndicatorProvider
    ...（编译器初始化阶段，仍未读到任何源文件）
```

### 9.3 ★★ 最重要的方法论发现：**类级探测会骗人**

`sun.misc.Unsafe` 的实测结果：

| 检查项 | 结果 |
|---|---|
| 类是否存在 | ✅ **存在** |
| 声明的方法数 | **55 个**（`copyMemory`/`allocateMemory`/`getUnsafe`/CAS 系列/`park` 等都在） |
| `copyMemory` 的**签名** | ❌ 只有 **3 参** `copyMemory(long,long,long)`；**缺 5 参** `copyMemory(Object,long,Object,long,long)` |
| 反射 `getMethod("copyMemory", Object.class, long.class, Object.class, long.class, long.class)` | ❌ `NoSuchMethodException` |
| 是否被隐藏 API 限制拦截 | ❌ 不是——`setAccessible(true)` 成功，**纯粹是签名不存在** |

**这推翻了初版"安卓的 sun.misc.Unsafe 语义不可用"的间接推断，但给出更强的结论**：
不是"类没有"，而是**"类在、方法名在、但方法签名被裁剪成安卓自己的子集"**。

**为什么这很重要**：任何靠 `Class.forName` 逐类探测得出的"缺失清单"都**低估了真实差距**。
初版算出的"146 个缺失 JDK 类"只是**下界**；真实不兼容还包括大量
**"类在但方法签名不同"** 的情况——**类级探测查不出来**。
`ConcurrentLongObjectHashMap.<clinit>` 直接 `java.lang.Error` 挂掉，
连 `try/catch` 都救不了，编译器初始化就此终止。

### 9.4 真机上的其他实测数据

| 项 | 真机值 | 说明 |
|---|---|---|
| `java.vm.name` | **Dalvik** | 即 ART 的 Dalvik 兼容标识 |
| `java.specification.version` | **0.9** | 不是 `1.8`/`11`/`21`——编译器里任何按 Java 版本分支的逻辑都可能走错路 |
| `java.lang.management.ManagementFactory` | ❌ 不存在 | 初版判断正确 |
| `javax.tools` / `javax.lang.model` / `javax.swing` / `java.awt` | ❌ 全不存在 | 初版判断正确 |
| `java.nio.file.Files` | ✅ 存在，**56 个公开方法** | ⚠️ 初版未验证；实测 `createTempFile(String,String,FileAttribute[])` **可用**（返回真实临时文件路径） |
| 单进程 `maxMemory` | **192 MB** | 跑这个编译器（初始化就需数百 MB）**远远不够** |
| CPU 核数 | 4 | — |

**内存这一条是独立于所有类/方法问题的第二重硬阻断**：即便把上面每个缺失都补齐，
192MB 的默认堆也装不下编译器。

### 9.5 复验后的结论

**主结论（❌ 不可行）不仅不变，而且更硬**：

1. 真机上编译器**确实无法完成初始化**（`ConcurrentLongObjectHashMap.<clinit>` → `java.lang.Error`）。
2. 阻断点比初版预测**更本质**：不是"缺类"，而是"**类在但签名不同**"——
   这类问题无法靠"往 APK 里塞缺失的类"解决，因为**你没法往运行时里加方法**。
3. 叠加 `maxMemory = 192MB` 与 `java.specification.version = 0.9`，工程量只会比初版估计更大。

**对心乐的意义完全不变**：这条路的结论仍是"不可作为主路线"，
路线 B（声明式皮肤包）依然是正确选择。本节的唯一修正是**论证的准确性**——
一个建立在错误理由上的正确结论，早晚会在别处把人带沟里。

### 9.6 收尾：初版「目标 JDK = 9」推测的归宿

初版在没有安卓运行时上下文时，靠文档和类名推测"如果能跑编译器，安卓很可能按某个
**JDK 9 之类的兼容层**提供底层支持"——这个推测是**错的**，但**重要程度低**（它不是
主结论的依据，只是一个让论证图自洽的副假设），所以初版正文里没有展开。真机复验给出
的实测值把这条推测的**真实方向**也明确了**——不是 9，而是更低**。

| 项 | 初版推测 | 真机实测 | 真值出处 |
|---|---|---|---|
| `java.specification.version` | 推测"接近 JDK 9" | **`"0.9"`** | `app_process` 跑 `System.getProperty("java.specification.version")` 直读 |

**这个 `0.9` 的真实含义**：ART 的 `System.getProperty(...)` 返回的是 [Dalvik 字节码规范的
versioning 字面](https://source.android.com/devices/tech/dalvik/dex-format#dex-file-magic)，
不是任何标准 Java SE 版本号。安卓**从来没有**实现过完整的 Java SE 类库——它只实现了
`java.base`（即 `java.lang`/`java.util`/`java.nio` 等）核心子集，外加 [Android SDK 加的
android.* 类](https://developer.android.com/reference/packages)，其他都是 stub。

**对论证的修正是负向的**：本节之前把"安卓没有 `javax.tools`/`java.desktop`/`sun.misc.Unsafe.copyMemory`
5 参重载"作为"看起来不可能跑编译器"的论证；现在补一刀——**就算类在、签名匹配，安卓
按 Java 版本分支的代码（任何 `if (specVersion >= 9)` 之类）也会走错路**，因为根本没有
"标准 Java 版本号"这个东西可读。`0.9` 不等于"Java 0.9"，也不等于"接近任何 JDK"，
它只是个 Dalvik 自报的兼容标识。

**这件事不影响主结论**：编译器在 ART 上跑不起来这条**没变**，但**理由清单多了一根**：
除了类缺失、内存不够、签名不匹配，**还有版本号语义错位**。三层否定堆在一起，主结论
更稳。

---

## 附：决定性实验的原始输出（证明 Compose 插件真的在"编译"）

为排除"其实做不到编译"的疑虑，程序化调用 `kotlin-compose-compiler-plugin-embeddable` + `K2JVMCompiler`，对同一个源文件在开/关插件下各编译一次：

```kotlin
package p
import androidx.compose.runtime.Composable

@Composable
fun Greeting(name: String) { println("hello " + name) }
```

**未开插件**（`javap -p -c`）：

```
public final class p.HelloKt {
  public static final void Greeting(java.lang.String);
```

**开启插件**：

```
public final class p.HelloKt {
  public static final void Greeting(java.lang.String, androidx.compose.runtime.Composer, int);
    ...
    invokeinterface androidx/compose/runtime/Composer.startRestartGroup:(I)Landroidx/compose/runtime/Composer;
    invokeinterface androidx/compose/runtime/Composer.getSkipping:()Z
    invokestatic  androidx/compose/runtime/ComposerKt.traceEventStart:(IIILjava/lang/String;)V
    ldc           String p.Greeting (Hello.kt:6)
```

结论：**Compose 编译器的程序化调用确实成立，`@Composable` 被真实改写成追加 `Composer`/`changed` 参数并生成跳过逻辑。**
所以本报告否决的**不是"能不能调用 Compose 编译器"**（能），而是**"这个编译器能不能在安卓的 JVM 平台上跑起来"**（不能）。这两件事经常被混为一谈，特此分开记录。
