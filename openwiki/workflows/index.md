# Files

- [工作流：情绪分析全链路（批扫调度 → 端侧推理 → 落库 → 展示与校准回写）](emotion-analysis-pipeline.md) - 端到端讲清情绪数据从哪来、到哪去：WorkManager 批扫调度与失败重试、EmotionAnalyzer TFLite 推理管线与内存纪律、Room upsert、展示与用户校准回写，以及词表统计供情绪时段随心播放过滤。
- [Playback Session Lifecycle](playback-session-lifecycle.md) - End-to-end walk through one complete playback session: cold-start assembly and main-thread discipline, MediaController connect with the snapshot-restore live-session decision, queue setup and playback start, Media3 song-change translation with PlayDurationTracker settlement, infinite refill, in-session bypaths (sleep timer, Bluetooth disconnect, single-item loop rewind), and the recovery loop after process death.
