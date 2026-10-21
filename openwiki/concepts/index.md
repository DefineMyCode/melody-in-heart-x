# Files

- [情绪领域模型与词表（SongEmotion / EmotionGroup / WordPreference）](emotion-model.md) - 讲解情绪功能的分层双轨领域模型：SongEmotion 的 V/A 曲线与用户校准字段、EmotionGroup 10 组 V-A 锚定中文词表（含 auto=false 的 WITTY 组）、emotionTagsOf 展示词条判定、Room song_emotions 与 word_preferences 表的存储形态（DB v11/14 实体）、重扫/校准写语义，以及 v3.11.0 新增的情绪词条收听偏好计票模型（WordPreferenceKind，票多者胜/平票中立）。
- [Mood Time-Slot Anything-Play (Time-of-Day × Emotion Tags)](mood-time-slot.md) - Contextual anything-play: TimeSlotConfig storage in DataStore JSON, MoodSlotResolver half-open slot resolution and validation, the config UI data flow, and how PlayerRuntime's continuously observed caches feed planner-entry candidate filtering with attribution toasts. TIME_SLOT is one of three mutually-exclusive RandomEnhanceMode states (OFF / TIME_SLOT / EXPLORE).
