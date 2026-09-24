// =============================================================================
// PocoS3 app-side native shim.
//
// This file is what Gradle's externalNativeBuild compiles as part of the
// `pocos3-glue` target defined in ../../CMakeLists.txt. It contains only
// the public JNI entry points (the same ones declared in
// android/src/pocos3-android.cpp); Gradle's view of the target is just
// the few translation units it needs.
//
// In practice, the CMake target in android/CMakeLists.txt already pulls
// in android/src/pocos3-android.cpp + android/src/pocos3_saf.cpp. This
// file is kept in the app module so Android Studio's project view shows
// the JNI source alongside the Kotlin source.
// =============================================================================

// This file intentionally has no implementation; the actual JNI glue is
// in android/src/pocos3-android.cpp. This file's existence is to keep
// Android Studio's CMake integration happy and to document where the
// app-side JNI source lives.

#include <jni.h>

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad_pocos3_app(JavaVM* vm, void* reserved) {
    // Deliberately empty; the real JNI_OnLoad lives in pocos3-android.cpp.
    (void)vm;
    (void)reserved;
    return JNI_VERSION_1_6;
}
