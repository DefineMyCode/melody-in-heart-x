# 项目文档索引

Melody in Heart（心有乐章）的文档按主题归入 `docs/` 子目录。仓库根目录的 `CLAUDE.md` 是给 Claude Code 的编码指引；本文件是全部文档的入口。

## 架构（`docs/architecture/`）

- [播放队列架构](architecture/PLAYBACK_QUEUE_ARCHITECTURE.md)：`PlayQueue` / `MediaController` / 窗口化规划层三者的职责边界与同步规则，避免队列各走各路。
- [播放状态机](architecture/PLAYBACK_STATE_MACHINE.md)：Media3 与 UI / 持久化之间的产品级状态模型（`idle/preparing/ready/playing/paused/buffering/ended/error`）及转移规则。
- [情境化随心播放增强](architecture/MOOD_TIME_SLOT_PLAYBACK.md)：时段 × 情绪词条的随心播放增强功能设计（产品评审结论 + 一期技术方案）。

## 重构与产品化（`docs/refactor/`）

- [产品化重构审计](refactor/PRODUCT_REFACTOR_AUDIT.md)：本轮产品化重构的完成度证据与剩余风险清单（由 `verifyProductArchitecture` 校验其内容）。
- [项目评审与重构建议](refactor/review.md)：重构**之前**的单模块架构评审快照，多数建议已落实，作为历史依据保留。

## UI 设计（`docs/ui-design/`）

- [设计系统](ui-design/DESIGN_SYSTEM.md)：心有乐章 UI 实现参考（颜色 / 字体 / 组件规范）。
- [主设计稿](ui-design/index.html)：Compose 实现对照的 HTML 设计稿。
- [播放统计页设计稿](ui-design/playback-statistics.html)。

## 编码指引（仓库根目录）

- [CLAUDE.md](../CLAUDE.md)：Claude Code 与后续维护者的构建 / 测试 / 架构约定，含 `verifyProductArchitecture` 强制规则。
