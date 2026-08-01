# Melody in Heart — release obfuscation rules

# Keep Media3 / ExoPlayer (reflection-heavy)
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }

# Keep Jellyfin FFmpeg decoder renderer (loaded reflectively via EXTENSION_RENDERER_MODE_PREFER)
-keep class org.jellyfin.media3.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# Data classes used in snapshot serialization
-keep class cn.com.dcsgo.mihx.data.datastore.** { *; }
