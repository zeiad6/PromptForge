# PromptForge ProGuard rules
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Retrofit + OkHttp
-keepattributes Signature, Exceptions
-keepclasseswithmembers class * { @retrofit2.http.* <methods>; }
-dontwarn okhttp3.**
-dontwarn okio.**

# kotlinx.serialization
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.promptforge.**$$serializer { *; }
-keepclassmembers class com.promptforge.** { *** Companion; }
-keepclasseswithmembers class com.promptforge.** { kotlinx.serialization.KSerializer serializer(...); }
