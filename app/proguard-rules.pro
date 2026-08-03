# Keep entry points referenced from the manifest and Media3 session binding.
-keep class cn.com.dcsgo.mihx.MelodyApplication { *; }
-keep class cn.com.dcsgo.mihx.MainActivity { *; }
-keep class cn.com.dcsgo.mihx.data.player.AppMediaSessionService { *; }

# Preserve metadata used by Hilt, Room, Kotlin serialization-style reflection, and Media3 callbacks.
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault

# Room generated code has consumer rules, but keeping database/entity boundaries makes release builds easier to diagnose.
-keep class cn.com.dcsgo.mihx.data.local.MelodyDatabase { *; }
-keep class cn.com.dcsgo.mihx.data.local.entity.** { *; }
