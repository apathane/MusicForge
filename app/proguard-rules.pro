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

# ONNX Runtime's native code finds its Java classes by name (OrtSession.run -> OnnxTensor and co.),
# and its AAR ships no keep rules. Minified without this, the first Automix analysis aborts the
# process with "java_class == null in call to GetMethodID" (P5/P6 release builds).
-keep class ai.onnxruntime.** { *; }
