# Keep the JNI glue's exported symbols (it has very few).
-keep class com.pocos3.runtime.PocoS3Core { *; }

# Keep the NativeActivity entry (we do not use it but Compose needs it).
-keep class android.app.NativeActivity { *; }

# Keep everything the Kotlin serialization runtime needs.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Compose-related reflection bits.
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }

# Don't warn about missing JNI methods; the JNI side is shipped as .so.
-dontwarn com.pocos3.**
