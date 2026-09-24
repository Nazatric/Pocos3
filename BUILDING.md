PocoS3 Build Guide
==================

Build matrix
------------

PocoS3 is built and tested against the following versions. Older combinations
are not supported and will not be debugged.

| Component        | Minimum       | Recommended      | Notes |
|------------------|---------------|------------------|-------|
| Android NDK       | r27           | r27c             | r28 works; r26 lacks C++23 features the core needs |
| CMake              | 3.30          | 3.31             | Needed for `LINKER_LANGUAGE` on object libraries |
| JDK                | 17            | 21 LTS           | Android Studio's bundled JBR is fine |
| Android Gradle Plugin | 8.7       | 8.9              | Compose compiler is bundled with the Kotlin Gradle plugin from Kotlin 2.0+ |
| Kotlin             | 2.0           | 2.1              | Compose compiler plugin is used (no `kotlin-compiler-extensions` repo needed) |
| Android SDK        | API 35 (SDK 35) | API 35        | `compileSdk = 35` |
| minSdk             | 33            | 33               | Below 33 lacks `ANativeWindow_setFrameRate` |
| targetSdk          | 35            | 35               | Required for Android 15 foreground-service rules |

Native ABI: **arm64-v8a** only. PocoS3 does not build for ARMv7, x86, or x86_64.

Hardware target: ARMv8.2-A or later with NEON, FP16, and dot-product. The
Dimensity 8400-Ultra in the POCO X7 Pro is ARMv9.2-A and supports all of the
above plus SVE2; the core does not require SVE2.


Prerequisites
-------------

1. Android Studio (or a standalone Android SDK + NDK + CMake + cmdline-tools).
2. JDK 17 or newer. Verify with `java -version`.
3. CMake 3.30 or newer (the NDK's bundled CMake is 3.22 — install a real one).
4. Ninja (`apt install ninja-build` on Debian, `brew install ninja` on macOS).
5. `git`, `python3`, `curl` for fetching the source tree.

Environment variables you will want to set:

    export ANDROID_HOME=$HOME/Android/Sdk
    export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/27.2.12479018   # use your installed version
    export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which javac))))

`ANDROID_NDK_HOME` must point at a specific NDK version directory, not the
`ndk/` root.


First-time setup
----------------

    git clone --recursive https://github.com/<your-org>/PocoS3.git
    cd PocoS3

Two dependencies are deliberately not submodules because their upstream repos
do not use a layout that `git submodule` cleanly tracks:

    git clone https://github.com/SnowflakePowered/librashader 3rdparty/librashader
    git clone https://github.com/bylaws/libadrenotools android/pocos3-ui/app/src/main/cpp/libadrenotools

Apply the PocoS3 patches to the upstream RPCS3 source so the Android entry
points resolve at `dlopen` time. These patches are *additive* — they add an
`extern "C"` Android surface to RPCS3's existing `main.cpp` / `Emu/System.cpp`
without altering existing semantics:

    ./patches/apply.sh third_party/rpcs3

If the patches fail to apply, see [`patches/README.md`](patches/README.md) for
how to resolve rebase conflicts after an upstream RPCS3 update.


Building the native core
------------------------

The native core is built **outside Gradle**, exactly like ARMSX3. Gradle only
builds the JNI glue (`android/src/`). The core itself is a prebuilt
`libpocos3-core.so` that the glue `dlopen`s at runtime. This is deliberate: it
keeps a `./gradlew assembleRelease` from dragging LLVM into every sync.

The convenience script wraps a CMake + Ninja invocation:

    ./android/configure.sh --release

Or invoke CMake directly:

    cmake -B build-android -G Ninja \
      -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake \
      -DANDROID_ABI=arm64-v8a \
      -DANDROID_PLATFORM=android-33 \
      -DCMAKE_BUILD_TYPE=RelWithDebInfo \
      -DCMAKE_UNITY_BUILD=ON
    cmake --build build-android --target pocos3-core -j$(nproc)

Strip and place the library where Gradle expects it:

    $ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip \
      --strip-unneeded build-android/android/libpocos3-core.so

    cp build-android/android/libpocos3-core.so \
       android/pocos3-ui/app/src/main/jniLibs/arm64-v8a/

Re-run this step whenever anything under `third_party/rpcs3/` or
`android/src/` changes.


Building the APK
----------------

    cd android/pocos3-ui
    ./gradlew :app:assembleRelease

The APK lands in `app/build/outputs/apk/release/app-release.apk`. You will
need to sign it; for personal sideload, `apksigner` with a self-created
keystore is fine. For the GitHub Actions CI build, the workflow signs with a
debug key by default and produces a `app-release-unsigned.apk` that you must
re-sign locally before installing on a real device.


Build variants
--------------

`android/build-variants.sh` produces four APK variants keyed on `minSdk` and
debuggability:

| Variant      | minSdk | Stripped | Debuggable | Suitable for           |
|--------------|--------|---------|------------|------------------------|
| debug-a13    | 33     | no      | yes        | local development       |
| release-a13  | 33     | yes     | no         | POCO X7 Pro ship        |
| debug-a15    | 35     | no      | yes        | Android 15 dev          |
| release-a15  | 35     | yes     | no         | Android 15+ production  |

The `minSdk` and the API level the core was compiled against **must agree** —
an APK that installs below its core's target is a `dlopen` failure at boot.


CI
--

`.github/workflows/android.yml` reproduces the above on a GitHub-hosted Ubuntu
runner with caching for the NDK and Gradle caches. The first run takes
roughly 45–75 minutes (mostly LLVM compile); subsequent runs are 8–15 minutes
if caches hit.
