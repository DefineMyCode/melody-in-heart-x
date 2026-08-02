# 锁屏媒体控件与后台保活技术文档

---

## 一、整体方案

项目采用 **Jetpack Media3（ExoPlayer + MediaSession）** 官方方案，核心由一个 `MediaSessionService` 承担三件事：

1. 渲染锁屏 / 通知栏媒体控件（由系统根据 `MediaSession` 自动生成，App 不自绘 RemoteViews）。
2. 以**前台服务**身份运行，挂一条媒体通知，避免进程被系统回收。
3. 对外暴露 `MediaController`，供 UI 层发送 play / pause / seek / 队列等命令。

进程被极端回收时，再通过 `PlaybackStateStore` 持久化的播放快照恢复现场。

---

## 二、锁屏媒体控件的实现

### 2.1 MediaSessionService 主体

`AppMediaSessionService` 继承自 `androidx.media3.session.MediaSessionService`，内部持有 `ExoPlayer` 与 `MediaSession`。注释明确写道：「relies on MediaSessionService's default notification lifecycle and lets Media3 handle standard transport commands」。

```kotlin
// AppMediaSessionService.kt
@AndroidEntryPoint
class AppMediaSessionService : MediaSessionService() {

    companion object {
        private const val TAG = "AppMediaSessionService"
    }

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null
    private var playerListener: Player.Listener? = null

    @Inject
    lateinit var logger: AppLogger

    @Inject
    lateinit var playbackStateStore: PlaybackStateStore

    override fun onCreate() {
        super.onCreate()
        logger.info(TAG, "Service onCreate")
        createExoPlayer()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }
}
```

### 2.2 创建 ExoPlayer 与 MediaSession

`createMediaSession()` 中通过 `MediaSession.Builder(this, player)` 将 `ExoPlayer` 暴露给系统；并通过 `setSessionActivity(sessionActivity)` 设置点击通知回到 App 的 `PendingIntent`。

```kotlin
// AppMediaSessionService.kt
private fun createExoPlayer() {
    exoPlayer = PlayerFactory.create(this)
    createMediaSession()
    setupPlayerListener()
    logger.info(TAG, "ExoPlayer and Media3 MediaSession created")
}

private fun createMediaSession() {
    val player = exoPlayer ?: return
    val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        ?.apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP }
    val sessionActivity = PendingIntent.getActivity(
        this,
        0,
        launchIntent ?: Intent(Intent.ACTION_MAIN).setPackage(packageName),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    mediaSession = MediaSession.Builder(this, player)
        .setSessionActivity(sessionActivity)
        .build()
}
```

### 2.3 ExoPlayer 的音频语义

`PlayerFactory` 为 ExoPlayer 设置了音乐类型的 `AudioAttributes`，并开启音频焦点抢占与「拔耳机自动暂停」语义，符合系统对媒体会话的预期，是系统能正确识别并渲染锁屏控件的前提。

```kotlin
// PlayerFactory.kt
return ExoPlayer.Builder(context, renderersFactory)
    .setLoadControl(actualLoadControl)
    .build()
    .apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            handleAudioFocus
        )
        setHandleAudioBecomingNoisy(handleAudioBecomingNoisy)
        setPauseAtEndOfMediaItems(false)
    }
```

### 2.4 UI 侧通过 MediaController 接入

UI 层（`PlaybackController`）使用 `MediaController.Builder(context, SessionToken(...)).buildAsync()` 连接到 Service 的 `MediaSession`，并以 `Player.Listener` 监听状态变化。这是 Media3 推荐的「控制器—服务」分离架构。

```kotlin
// PlaybackController.kt
override fun connect(onConnected: (ControllerPlaybackSnapshot) -> Unit) {
    val sessionToken = SessionToken(
        context,
        ComponentName(context, serviceClass)
    )
    val future = MediaController.Builder(context, sessionToken).buildAsync()
    controllerFuture = future
    future.addListener(
        {
            try {
                val connectedController = future.get()
                controller = connectedController
                connectedController.addListener(listener)
                drainPendingActions(connectedController)
                onConnected(connectedController.toPlaybackSnapshot())
                AppLog.info(TAG, "MediaController connected")
            } catch (e: ExecutionException) {
                AppLog.error(TAG, "MediaController connection failed", e.cause ?: e)
            } catch (e: Exception) {
                AppLog.error(TAG, "MediaController connection failed", e)
            }
        },
        ContextCompat.getMainExecutor(context)
    )
}
```

> 小结：锁屏 / 通知栏 / 蓝牙 / Android Auto / Wear 等所有标准媒体控件均由系统读取 `MediaSession` 的元数据与播放状态自动渲染，App 不需要任何自定义 RemoteViews。

---

## 三、后台播放不被杀进程的实现

### 3.1 Service 声明：前台服务 + mediaPlayback 类型

`player/src/main/AndroidManifest.xml` 中声明 `AppMediaSessionService` 为前台服务，并指定 `foregroundServiceType="mediaPlayback"`（Android 14+ 跑媒体播放前台服务的强制要求），同时注册 `MediaSessionService` 的 intent-filter。

```xml
<!-- player/src/main/AndroidManifest.xml -->
<service
    android:name="com.dcsgo.data.player.AppMediaSessionService"
    android:exported="false"
    android:foregroundServiceType="mediaPlayback">
    <intent-filter>
        <action android:name="androidx.media3.session.MediaSessionService" />
    </intent-filter>
</service>
```

### 3.2 权限声明

`app/src/main/AndroidManifest.xml` 中声明前台服务与通知所需权限：

```xml
<!-- app/src/main/AndroidManifest.xml -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
```

### 3.3 前台通知由 Media3 自动挂载

`MediaSessionService` 父类在播放开始时会**自动调用 `startForeground(...)` 并挂一条媒体通知**——这是 Media3 内置行为，因此 `AppMediaSessionService` 中看不到任何 `startForeground` 调用。挂了前台通知后，系统在内存紧张时不会轻易回收该进程。

### 3.4 播放状态持久化兜底

即便前台服务被极端回收，项目也做了快照持久化以便重启后恢复现场：

- `onTaskRemoved()` 与 `onDestroy()` 均调用 `saveCurrentPlaybackSnapshot()`，将 `songId` 与 `positionMs` 写入 `PlaybackStateStore`。
- 进程重启后由 domain 层的 `PlaybackRestoreCoordinator` 读取快照恢复队列。

```kotlin
// AppMediaSessionService.kt
override fun onDestroy() {
    logger.info(TAG, "Service onDestroy")
    saveCurrentPlaybackSnapshot()
    playerListener?.let { exoPlayer?.removeListener(it) }
    playerListener = null
    mediaSession?.release()
    mediaSession = null
    exoPlayer?.release()
    exoPlayer = null
    super.onDestroy()
}

override fun onTaskRemoved(rootIntent: Intent?) {
    logger.info(TAG, "Service onTaskRemoved")
    saveCurrentPlaybackSnapshot()
    super.onTaskRemoved(rootIntent)
}

private fun saveCurrentPlaybackSnapshot() {
    val player = exoPlayer ?: return
    val songId = player.currentMediaItem?.mediaId?.toIntOrNull() ?: return
    playbackStateStore.saveCurrentPlaybackSnapshot(
        songId = songId,
        positionMs = player.currentPosition,
    )
    logger.debug(TAG, "saveCurrentPlaybackSnapshot: song=$songId position=${player.currentPosition}ms")
}
```

### 3.5 依赖注入装配

`PlayerModule`（Hilt）将 `PlaybackStateStore` 与 `AppMediaSessionService::class.java` 注入到 `PlaybackController`，完成「控制器—服务—持久化」的装配。

```kotlin
// PlayerModule.kt
@Provides
@Singleton
fun providePlaybackStateStore(
    @ApplicationContext context: Context,
): PlaybackStateStore = PlaybackStateStore(context)

@Provides
fun providePlaybackControllerPortFactory(
    @ApplicationContext context: Context,
): PlaybackControllerPortFactory {
    return PlaybackControllerPortFactory { callbacks ->
        PlaybackController(
            context = context,
            serviceClass = AppMediaSessionService::class.java,
            callbacks = callbacks,
        )
    }
}
```

---

## 四、总结

| 能力 | 实现方式 |
|------|----------|
| 锁屏 / 通知栏媒体控件 | `MediaSessionService` + `MediaSession`，由系统自动渲染 |
| 点击通知回到 App | `MediaSession.Builder.setSessionActivity(PendingIntent)` |
| 音频语义正确识别 | `AudioAttributes(MUSIC, USAGE_MEDIA)` + 音频焦点 + 拔耳机暂停 |
| UI 控制播放 | `MediaController` 连接 `MediaSession`，发送命令并监听状态 |
| 后台不被杀进程 | 前台服务 `foregroundServiceType="mediaPlayback"` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` 权限 |
| 前台通知挂载 | `MediaSessionService` 父类在播放开始时自动 `startForeground` |
| 进程被杀后恢复 | `PlaybackStateStore` 持久化 `songId` / `positionMs`，重启后由 `PlaybackRestoreCoordinator` 恢复 |

一句话概括：**Media3 `MediaSessionService` 一站式承担「锁屏控件渲染 + 前台服务保活 + 通知栏媒体控件」三件事**，App 只需正确声明 Service 类型与权限，并把 ExoPlayer 实例交给 MediaSession；持久化快照则是额外兜底，应对极端情况下进程仍被回收的场景。
