# kotlinx.serialization models used by the vendored innertube module
-keepattributes *Annotation*, InnerClasses, Signature
-keepclassmembers @kotlinx.serialization.Serializable class ** { *** Companion; *; }
-keep class com.metrolist.innertube.models.** { *; }
-keep class com.metrolist.innertubex.** { *; }
-dontwarn org.slf4j.**

# Preserve network stack and serialization for security analysis
-keep class okhttp3.** { *; }
-keep class retrofit2.** { *; }
-keepclassmembers class * {
    @java.lang.annotation.** *;
}
