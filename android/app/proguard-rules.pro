# ProGuard rules for Calyth Android

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.calyth.app.**$$serializer { *; }
-keepclassmembers class com.calyth.app.** { *** Companion; }
-keepclasseswithmembers class com.calyth.app.** { kotlinx.serialization.KSerializer serializer(...); }

# Keep data classes
-keep class com.calyth.app.ModelInfo { *; }
-keep class com.calyth.app.ApiClient { *; }
