# 心有乐章 · UI 设计系统说明文档

> 版本 v2 · 2026-08-04
> 设计稿：`docs/ui-design/index.html`
> 目标平台：Android Jetpack Compose + Material3

---

## 一、概述

「心有乐章」(Melody in Heart) 是一款本地音乐播放器，设计系统围绕以下核心理念：

- **纯黑即熄灭** — 深色模式背景使用 `#000000`，OLED 屏幕像素完全不发光
- **克制** — "近白的暗"替代刺眼纯白，长时间听歌更舒适
- **乐章** — 从心跳中提炼一抹朱砂红，只做点缀、不做主色

共提供 **4 套主题**，支持跟随系统：

| 主题 | 标识 | 描述 |
|------|------|------|
| 墨 · 浅 | `mono-light` | 黑白灰亮色主题，浅灰底 `#F4F4F4` |
| 墨 · 深 (OLED) | `mono-dark` | 纯黑底 `#000000`，近白文字 |
| 朱砂 · 昼 | `vermilion-day` | 暖白底 `#FAF5F2`，沉稳红强调 |
| 朱砂 · 夜 | `vermilion-night` | 纯黑底，压暗暖红 `#C04F42` 点亮 |

---

## 二、色彩系统

### 2.1 设计令牌

所有颜色通过 CSS 自定义属性定义，4 套主题各自一套值。以下是每套主题的完整令牌表。

#### 墨 · 浅 (`mono-light`)

| 令牌 | 色值 | 用途 | M3 映射 |
|------|------|------|---------|
| `--bg0` | `#F4F4F4` | 页面底色 | `background` / `surface` |
| `--bg1` | `#FFFFFF` | 卡片、顶栏、迷你播放栏 | `surfaceContainerLowest` |
| `--bg2` | `#FAFAFA` | 搜索框、输入框、列表可选中态 | `surfaceContainerLow` |
| `--bg3` | `#EFEFEF` | 分段控件、代码块底色 | `surfaceContainer` |
| `--bg4` | `#E6E6E6` | 弹窗、底部面板 | `surfaceContainerHigh` |
| `--out1` | `#E7E7E7` | 分隔线、极淡边框 | `outlineVariant` |
| `--out2` | `#CFCFCF` | 输入框边框、图标描边 | `outline` |
| `--text1` | `#141414` | 标题与正文 | `onSurface` |
| `--text2` | `#575757` | 艺术家、说明、辅助信息 | `onSurfaceVariant` |
| `--text3` | `#989898` | 时间、计数、占位 | `onSurfaceVariant`（低对比） |
| `--accent` | `#161616` | 播放键、进度、FAB、高亮 | `primary` |
| `--on-accent` | `#FFFFFF` | 强调色上的图文 | `onPrimary` |
| `--accent2` | `#575757` | 渐变过渡色、柔和点缀 | `tertiary` |
| `--on-accent2` | `#FFFFFF` | 次强调色上的图文 | `onTertiary` |

辅助令牌：

| 令牌 | 值 | 用途 |
|------|-----|------|
| `--grad` | `linear-gradient(90deg, #161616, #6B6B6B)` | 标题渐变、Hero 渐变 |
| `--cover1` | `#E9E9E9` | 封面渐变起始色 |
| `--cover2` | `#C7C7C7` | 封面渐变结束色 |
| `--cover-note` | `#161616` | 封面上音符图标色 |
| `--scrim` | `rgba(20,20,20,.45)` | 遮罩/投影用 |
| `--hover` | `rgba(20,20,20,.06)` | 悬停态底色 |
| `--shadow` | `0 20px 44px -22px rgba(20,20,20,.28)` | 卡片/FAB 阴影 |

#### 墨 · 深 (`mono-dark`)

| 令牌 | 色值 | 说明 |
|------|------|------|
| `--bg0` | `#000000` | **纯黑**，OLED 像素熄灭 |
| `--bg1` | `#0A0A0A` | 卡片、播放栏微高于背景 |
| `--bg2` | `#121212` | 搜索框底色 |
| `--bg3` | `#1B1B1B` | 分段控件底色 |
| `--bg4` | `#252525` | 弹窗浮层 |
| `--out1` | `#1C1C1C` | 极淡分隔 |
| `--out2` | `#2C2C2C` | 边框/描边 |
| `--text1` | `#D0D0D0` | **近白的暗**，正文 |
| `--text2` | `#9A9A9A` | 次级文字 |
| `--text3` | `#6B6B6B` | 弱化文字 |
| `--accent` | `#E6E6E6` | 近白强调（墨色无红） |
| `--on-accent` | `#0A0A0A` | 深底白字 |
| `--accent2` | `#9A9A9A` | 次强调 |
| `--grad` | `linear-gradient(90deg, #E6E6E6, #9A9A9A)` | 渐变 |
| `--shadow` | `0 24px 50px -24px rgba(0,0,0,.9)` | 纯黑投影更重 |

#### 朱砂 · 昼 (`vermilion-day`)

| 令牌 | 色值 | 说明 |
|------|------|------|
| `--bg0` | `#FAF5F2` | 暖白基调，微红底色 |
| `--bg1` | `#FFFFFF` | 纯白卡片 |
| `--bg2` | `#FBF4F0` | 搜索框 |
| `--bg3` | `#F1E7E1` | 分段控件 |
| `--bg4` | `#E9DAD2` | 弹窗 |
| `--out1` | `#EFE0D9` | 淡暖灰分隔 |
| `--out2` | `#DFC4B9` | 暖灰边框 |
| `--text1` | `#2B1A16` | 深棕正文 |
| `--text2` | `#6B534B` | 暖棕次级文字 |
| `--text3` | `#A2877D` | 浅棕弱化文字 |
| `--accent` | `#A32E25` | **朱砂红**（昼） |
| `--on-accent` | `#FFFFFF` | 白字 |
| `--accent2` | `#D4786C` | 浅红过渡 |
| `--grad` | `linear-gradient(90deg, #A32E25, #D4786C)` | 红渐变 |
| `--shadow` | `0 20px 44px -22px rgba(163,46,37,.35)` | 红调阴影 |

#### 朱砂 · 夜 (`vermilion-night`)

| 令牌 | 色值 | 说明 |
|------|------|------|
| `--bg0` | `#000000` | 纯黑底 |
| `--bg1` | `#140B09` | 极暗暖黑卡片 |
| `--bg2` | `#1D110E` | 搜索框 |
| `--bg3` | `#251814` | 分段控件 |
| `--bg4` | `#2E1D18` | 弹窗 |
| `--out1` | `#2B1813` | 暗暖分隔 |
| `--out2` | `#3E241E` | 暗暖边框 |
| `--text1` | `#EADAD5` | 暖白正文 |
| `--text2` | `#B79A92` | 暖灰次级 |
| `--text3` | `#7F5E56` | 暗暖弱化 |
| `--accent` | `#C04F42` | **压暗朱砂红**（夜） |
| `--on-accent` | `#FFFFFF` | 白字 |
| `--accent2` | `#8F3B32` | 深红过渡 |
| `--grad` | `linear-gradient(90deg, #C04F42, #7E3028)` | 暗红渐变 |
| `--shadow` | `0 24px 50px -24px rgba(0,0,0,.9)` | 纯黑投影 |

### 2.2 背景层级关系

```
bg0 (页面底色)
 └─ bg1 (卡片/顶栏/迷你播放栏)          — 微高于 bg0
     └─ bg2 (搜索框/输入框)             — 微高于 bg1
         └─ bg3 (分段控件/菜单)          — 微高于 bg2
             └─ bg4 (弹窗/底部面板)       — 最高浮层
```

深色模式下共 6 级灰阶从纯黑递进到 `#252525`，层级靠灰不靠亮度。

### 2.3 文字层级

| 层级 | 墨 · 浅 | 墨 · 深 | 用途 |
|------|---------|---------|------|
| text1（主） | `#141414` | `#D0D0D0` | 标题、正文 |
| text2（次） | `#575757` | `#9A9A9A` | 艺术家、说明 |
| text3（弱） | `#989898` | `#6B6B6B` | 时间、计数、占位 |

---

## 三、字体系统

### 3.1 字体栈

```css
/* UI 字体 */
--font-ui: "SF Pro Text", -apple-system, "Segoe UI",
           "PingFang SC", "Hiragino Sans GB",
           "Microsoft YaHei", "Noto Sans CJK SC", sans-serif;

/* 等宽数字 */
--font-num: "SF Mono", "JetBrains Mono", Consolas, "Courier New", monospace;
```

优先级：系统原生 → 中文优化 → 通用后备。数字与时间统一使用等宽字体以确保对齐。

### 3.2 字体层级

| 层级 | 字号 | 字重 | 用途 |
|------|------|------|------|
| Display | 34sp | 800 | 品牌/大标题 |
| Title Large | 22sp | 700 | 页面标题 |
| Title Medium | 16sp | 600 | 卡片标题、歌单名 |
| Body Medium | 14sp | 400 | 正文、列表项 |
| Body Small | 12sp | 400 | 辅助信息、艺术家 |
| Label | 11sp | 600 | 徽章、时间戳 |
| Numeric | 等宽 | — | 时间码、数量、版本号 |
| Lyric Active | 20sp | 800 | 当前歌词高亮行 |

---

## 四、圆角与间距

### 4.1 圆角

| 令牌 | 值 | 用途 |
|------|-----|------|
| `--r-s` | 8px | 小元素：徽章、开关、小按钮 |
| `--r-m` | 14px | 卡片、搜索框、封面小图 |
| `--r-l` | 20px | 封面大图、弹窗 |
| `--r-xl` | 26px | Hero 板块、原则卡片 |

### 4.2 间距

页面最大宽度 `1280px`，左右内边距 `28px`（移动端 `16px`）。组件间距一般 `12px–18px`。

---

## 五、组件

### 5.1 按钮

| 样式 | CSS 类 | 描述 |
|------|--------|------|
| 实心 | `.btn.filled` | 强调按钮，背景 `--accent`，文字 `--on-accent`，圆角 19px，高度 38px |
| 描边 | `.btn.outlined` | 次要按钮，`--out2` 描边，`--text1` 文字 |
| 文字 | `.btn.text` | 纯文字按钮，`--accent` 色 |
| 图标 | `.icon-btn` | 圆形图标按钮 38×38px，hover 态 `--hover` |
| 图标小 | `.icon-btn.sm` | 28×28px |

### 5.2 开关

```css
.switch        /* 46×26px，圆角 13px */
.switch.on     /* 背景变 --accent，圆点右移 */
```

### 5.3 分段控件 (Segmented Control)

```css
.seg           /* 背景 --bg3，圆角 12px，内边距 4px */
.seg button.on /* 背景 --accent，文字 --on-accent，圆角 9px */
```

### 5.4 滑块

- 轨道 4px，背景 `--out1`
- 已完成部分背景 `--accent`
- 拖拽点 14px 圆形，带阴影

### 5.5 搜索框

- 高度 42px，圆角 14px
- 背景 `--bg2`，边框 `--out1`
- 左侧搜索图标 + 右侧键盘提示 `kbd`

### 5.6 Chip / 徽章

| 样式 | 描述 |
|------|------|
| `.chip` | 基础 chip：圆角 pill，`--out2` 描边 |
| `.chip.select` | 可选中 chip：粗体，带下拉箭头 |
| `.chip.toggle` | 开关 chip：`--bg3` 底，`--accent` 图标 |
| `.hires` | Hi-Res 徽章：`--accent` 色描边，`10.5px` 粗体 |

### 5.7 Toast

- 背景 `--text1`，文字 `--bg0`（反转）
- 圆角 10px，内边距 9px×15px
- 图标 `--accent` 色

### 5.8 弹窗 (Dialog)

- 背景 `--bg4`，圆角 `--r-l`(20px)
- 标题 16px 粗体
- 正文 13px `--text2`
- 操作按钮右对齐

### 5.9 封面

```css
.cover  /* 正方形，圆角 20px，径向渐变模拟光照 */
/* 尺寸：播放器大封面 252px，列表缩略图 44px，歌单详情 104px，迷你栏 40px */
```

封面使用 `radial-gradient` 模拟左上光源打在实体唱片上的效果，并通过 `::after` 伪元素叠加高光。

### 5.10 迷你播放栏

- 高度自适应，`--bg1` 背景，顶部 `--out1` 分隔
- 左侧 40px 封面缩略图
- 中间：歌名 (12.5px 粗体) + 进度条 (2.5px)
- 右侧：36px 圆形播放按钮

### 5.11 FAB (浮动操作按钮)

- 定位：右下角 `16px`，底部 `74px`（迷你栏上方）
- 高度 50px，圆角 25px（胶囊形）
- 圆形变体：52×52px
- 背景 `--accent`，文字 `--on-accent`，带 `--scrim` 阴影

### 5.12 播放控制

- 播放键 62×62px 实心圆，`--accent` 底 + 阴影
- 上下曲 46×46px 圆形，icon-only
- 随机/循环 22px 图标
- 间距 26px

### 5.13 列表项

- 高度自适应，左右内边距 18px
- 左侧 44px 圆角缩略图
- 中间：标题 14px 粗体 + 副标题 11.5px
- 右侧：等宽数字时间 + 更多按钮
- 激活态：标题 `--accent` 色，背景 `--hover`
- 播放中动画 EQ 指示器（4 根竖线，交替伸缩）

### 5.14 底部导航 / Navigation Rail

- 移动端：底部导航栏，图标+标签
- 大屏 (>600dp)：NavigationSuite 自适应切换到左侧 Navigation Rail (78px 宽)
- 选中项：`--accent` 色 + `--hover` 背景

### 5.15 歌词行

- 已唱过：`--text2` 色
- 当前行：`--accent` 色，20px 粗体，带柔和光晕
- 未唱：`--text3` 色
- 行间距 14px，居中对齐

---

## 六、页面布局

### 6.1 首页播放器

- 状态栏 + Hi-Res 徽章
- 大封面 (252px) + 同名版本选择 Chip
- 歌曲标题 (24px) + 艺术家 (14px) + 专辑信息
- 进度条 + 时间
- 播放控制 (随机/上曲/播放/下曲/循环)
- 辅助操作 (无限播放/队列/歌词)
- 右下角"随心播放"FAB
- 底部迷你播放栏

### 6.2 歌词页

- 顶栏：返回 + 歌曲信息 + 设置
- 全屏歌词区域，当前行朱砂高亮
- 支持字号调节
- 底部迷你播放栏

### 6.3 我的（曲库）

- 顶栏 + 搜索框
- 文件夹导入卡片（SAF 批量导入）
- 歌单横滑条
- 全部歌曲列表（带 EQ 动画标识当前播放）
- FAB 新建

### 6.4 歌单详情

- 顶栏（返回 + 歌单名 + 更多）
- 歌单头：封面 (104px) + 名称 + 歌曲数/时长 + 播放全部/随机按钮
- 歌曲列表
- FAB 定位当前歌曲

### 6.5 播放队列

- 顶栏（标题 + 歌曲数 + 定位 + 关闭）
- 列表项带序号 + 移除按钮
- 当前播放项 `--accent` 色 + EQ 动画
- 底部状态栏：当前位置 + 播放模式

### 6.6 设置

- 顶栏（返回 + 标题）
- 主题卡片：分段控件（跟随系统/浅色/深色）+ 主题色选择（墨色/朱砂）
- 开关行：全局均匀随机、无限播放
- 歌词字体大小滑块
- 动作行：蓝牙播放监听、播放通知控制（含"申请权限"按钮）
- 版本号

### 6.7 大屏适配

- Navigation Rail 左侧导航（首页/歌单/我的/设置）
- 双栏：左侧歌曲列表 + 右侧播放详情
- 封面缩小至 120px

---

## 七、设计原则

### 7.1 朱砂不超过 10%

朱砂红只用于"正在发生的动作"：播放键、当前歌词、进度条、高亮。大面积留黑白灰，红色才保有"心跳"的分量。

### 7.2 层级靠灰，不靠亮度

深色模式背景纯黑，浮层用 `#0A` → `#25` 的极低灰阶表达"抬升"。文字从 `#D0D0D0` 到 `#9A9A9A` 到 `#6B6B6B` 三级递进。

### 7.3 昼夜两套色温

- 朱砂 · 昼：沉稳红 `#A32E25` 压住白底
- 朱砂 · 夜：压暗暖红 `#C04F42` 点亮纯黑，不刺眼
- 同一情感，两种温度

### 7.4 信息克制，留白呼吸

一屏只突出一个焦点：封面或当前歌词。列表密度适中，封面、迷你播放栏始终触手可及。

---

## 八、关键对比度（WCAG）

| 配对 | 场景 | 比值 | 标准 |
|------|------|------|------|
| `#D0D0D0` / `#000` | 墨·深·正文 | 13.6:1 | AA 通过 |
| `#9A9A9A` / `#000` | 墨·深·次级文字 | 7.5:1 | AA 通过 |
| `#141414` / `#F4F4F4` | 墨·浅·正文 | 16.8:1 | AAA 通过 |
| `#A32E25` / `#FAF5F2` | 朱砂·昼·强调 | 6.2:1 | AA 通过 |
| `#C04F42` / `#000` | 朱砂·夜·强调 | 4.5:1 | AA 通过 |
| `#FFFFFF` / `#A32E25` | 朱砂·昼·按钮图文 | 7.2:1 | AA 通过 |

所有场景均达到 WCAG AA 标准。

---

## 九、品牌叙事

> 心是跳动的朱砂红，乐章是一道沉稳的光。
> 纯黑是夜的底色，近白的暗是留给耳朵的温柔——
> 一首歌，从心脏出发，在屏幕上留下一抹会呼吸的红。

- **Logo**：心跳心形 + 音符律动线条
- **品牌色**：朱砂红（昼 `#A32E25` / 夜 `#C04F42`）
- **风格**：单色强调、去琥珀金、深色模式压暗护眼
- **定位**：本地 · 无广告 · 离线优先

---

## 十、Compose Material3 落地指南

### 10.1 ColorScheme 映射

```kotlin
// 以 mono-dark 为例
darkColorScheme(
    background = Color(0xFF000000),        // --bg0
    surface = Color(0xFF0A0A0A),           // --bg1
    surfaceContainerLowest = Color(0xFF0A0A0A), // --bg1
    surfaceContainerLow = Color(0xFF121212),    // --bg2
    surfaceContainer = Color(0xFF1B1B1B),       // --bg3
    surfaceContainerHigh = Color(0xFF252525),   // --bg4
    outlineVariant = Color(0xFF1C1C1C),         // --out1
    outline = Color(0xFF2C2C2C),                // --out2
    onSurface = Color(0xFFD0D0D0),              // --text1
    onSurfaceVariant = Color(0xFF9A9A9A),       // --text2
    primary = Color(0xFFE6E6E6),                // --accent
    onPrimary = Color(0xFF0A0A0A),              // --on-accent
)
```

### 10.2 圆角映射

```kotlin
// Shape composable 或 Modifier.borderRadius
RoundedCornerShape(8.dp)   // --r-s  小元素
RoundedCornerShape(14.dp)  // --r-m  卡片/搜索框
RoundedCornerShape(20.dp)  // --r-l  封面/弹窗
RoundedCornerShape(26.dp)  // --r-xl Hero板块
```

### 10.3 已实现的主题与系统

项目中主题实现位于 `core:ui` 模块：
- `MusicplayerTheme` — 顶层主题 Composable，支持 `darkTheme + variant` 双参
- `Color.kt` — 4 套 `ThemePalette`（mono-light / mono-dark / vermilion-day / vermilion-night），色值与设计令牌逐项一致
- `Theme.kt` — `paletteToColorScheme` 将令牌精确映射到 Material3 `ColorScheme`（primary=accent、surface=bg0、outlineVariant=out1 等）
- `Shape.kt` — 圆角令牌 `UiShapes`（8 / 14 / 20 / 26dp）
- `Type.kt` — 设计字体层级（Display 34/800、Title Large 22/700、Title Medium 16/600、Body 14/400、Label 11/600 等）
- `ThemeMode` / `ThemeVariant` — 跟随系统/浅色/深色 × 墨色/朱砂
- 设置页已实现完整主题切换 UI

### 10.4 对齐状态

以下项已完成对齐：

- [x] 墨·深 背景色：`surface`/`background` 均为 `#000000`（OLED 纯黑）
- [x] 朱砂主题：`vermilion-day` / `vermilion-night` 两套 `ColorScheme` 已实现
- [x] 文字三级色值：`text1/text2/text3` 与令牌精确对齐
- [x] 封面光效：Compose 中以封面顶部高光渐变近似 `radial-gradient` 唱片光效
- [x] 字体层级、圆角令牌（`Shape.kt`）、Toast 配色、歌词三态色
- [x] 迷你播放栏：40dp 封面 / 线性进度条 / 36dp 播放按钮 / bg1 底
- [x] 列表播放中 EQ 动画指示器（`EqualizerIndicator`）
- [x] FAB：accent 底胶囊形（含文字）；设置分段/预设选中项 accent 底；卡片 bg1 + out1 边框

保留的刻意差异（非缺陷）：

- 底部/侧边导航保持原始实现：纯文字、无图标，仅 3 个目的地（曲库/播放/我的），设置仍从「我的」页进入
- 大屏导航用手写 `BoxWithConstraints`（≥600dp 切左侧文字导航），未用 `NavigationSuiteScaffold`
- 首页为全屏播放器，不再叠加迷你播放栏（避免重复显示播放控制）
- 设置页保留行式布局（主题/开关/动作行），未整体卡片化；搜索框保留 M3 `OutlinedTextField`

---

## 十一、相关文件

| 文件 | 描述 |
|------|------|
| `docs/ui-design/index.html` | 主设计稿（本文档源） |
| `docs/ui-design/playback-statistics.html` | 播放统计页设计稿 |
| `docs/ui-design/mood-time-slot.html` | 情境化随心播放增强设计稿（时段 × 情绪词条；设计依据 `docs/architecture/MOOD_TIME_SLOT_PLAYBACK.md`） |
| `core/ui/.../theme/` | Compose 主题实现 |
| `feature/settings/.../SettingsScreen.kt` | 设置页 UI（主题切换） |

---

> 本文档从 `docs/ui-design/index.html` 设计稿总结而成，服务于心有乐章 Android 客户端的 UI 实现参考。
