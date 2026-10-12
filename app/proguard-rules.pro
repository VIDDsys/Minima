# Minima release — keep models / serialization / Room / Hilt / OkHttp
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, AnnotationDefault
-keepattributes *Annotation*

# Kotlin serialization
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class com.minima.chat.**$$serializer { *; }
-if class **$$serializer { *; }
-keepclassmembers class com.minima.chat.** {
    *** Companion;
}
-keepclasseswithmembers class com.minima.chat.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# OkHttp / SSE
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Hilt / Dagger
-dontwarn dagger.**
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# WorkManager
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keepclassmembers class * extends androidx.work.Worker {
    public <init>(android.content.Context,androidx.work.WorkerParameters);
}
-keepclassmembers class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context,androidx.work.WorkerParameters);
}

# EncryptedSharedPreferences / Tink
-dontwarn com.google.crypto.tink.**
-keep class com.google.crypto.tink.** { *; }

# App DTOs used reflectively / JSON
-keep class com.minima.chat.core.network.dto.** { *; }
-keep class com.minima.chat.core.agent.mcp.** { *; }
-keep class com.minima.chat.core.data.db.** { *; }
-keep class com.minima.chat.core.data.model.** { *; }

# Minima v1.4 — export / deep links / workers
-keep class com.minima.chat.core.data.export.** { *; }
-keep class com.minima.chat.deeplink.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
# Keep FGS entry
-keep class com.minima.chat.core.agent.service.AgentForegroundService { *; }
# Avoid stripping SSE EventSource reflective bits
-keep class okhttp3.sse.** { *; }

# Minima v2.6 — Hilt EntryPoint used from FGS + ChatRunGate
-keep interface com.minima.chat.core.agent.di.AgentRunEntryPoint { *; }
-keep class com.minima.chat.core.agent.di.** { *; }
-keep class com.minima.chat.core.agent.run.** { *; }
-keep class com.minima.chat.core.agent.schedule.ScheduledNoteWorker { *; }
# kotlinx.serialization polymorphic / generated
-keepnames class kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
