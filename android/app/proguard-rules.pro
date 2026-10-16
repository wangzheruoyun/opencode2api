# The gateway's config model is deserialized with kotlinx.serialization.
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.**
-keepclassmembers class com.opencode2api.android.data.model.** {
    *** Companion;
    <fields>;
}
-keepclasseswithmembers class com.opencode2api.android.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.opencode2api.android.data.model.**$$serializer { *; }
