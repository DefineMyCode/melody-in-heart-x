# Files

- [工作流：情绪分析全链路（批扫调度 → 端侧推理 → 落库 → 展示与校准回写）](emotion-analysis-pipeline.md) - 端到端讲清情绪数据从哪来、到哪去：WorkManager 批扫调度与失败重试、EmotionAnalyzer TFLite 推理管线与内存纪律、Room upsert、展示与用户校准回写，以及词表统计供情绪时段随心播放过滤。
- [工作流：播放会话生命周期](playback-session-lifecycle.md) - 端到端讲一次完整播放会话：冷启动装配与主线程纪律、MediaController 连接与快照恢复的 live-session 决策、设置队列与开播、Media3 切歌事件翻译与 PlayDurationTracker 结算、无限补队列、定时关闭/蓝牙断连/单项回绕等会话内旁路，以及进程被杀后的恢复闭环。
