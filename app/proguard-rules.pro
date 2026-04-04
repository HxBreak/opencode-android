# kotlinx.serialization — keep serializer classes
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class me.xiaok.opencode.domain.model.**$$serializer { *; }
-keepclassmembers class me.xiaok.opencode.domain.model.** {
    *** Companion;
}
-keepclasseswithmembers class me.xiaok.opencode.domain.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor Client — keep serializable request/response classes
-keep class me.xiaok.opencode.data.api.dto.** { *; }

# OkHttp SSE
-dontwarn okio.**
-dontwarn org.conscrypt.**

# Sealed class polymorphism
-keep class * extends me.xiaok.opencode.domain.model.Part { *; }
-keep class * extends me.xiaok.opencode.domain.model.Message { *; }
-keep class * extends me.xiaok.opencode.domain.model.ToolState { *; }
-keep class * extends me.xiaok.opencode.domain.model.SseEvent { *; }

# Hilt — keep injected classes
-dontwarn dagger.hilt.**
-keep class dagger.hilt.** { *; }

# Coil — keep image loaders
-dontwarn coil3.**

# Coroutines
-dontwarn kotlinx.coroutines.**

# Room — keep entity and dao classes
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep class * extends androidx.room.Dao

# AndroidX Security Crypto
-dontwarn androidx.security.**

# Google Error Prone (transitive dep of Tink via security-crypto)
-dontwarn com.google.errorprone.annotations.**
