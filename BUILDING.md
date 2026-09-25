PocoS3 Build Guide
==================

Build matrix
------------

| Component        | Minimum       | Recommended      |
|------------------|---------------|------------------|
| Android NDK       | r27           | r27c             |
| CMake              | 3.30          | 3.31             |
| JDK                | 17            | 21 LTS           |
| AGP                | 8.7           | 8.9              |
| Kotlin             | 2.0           | 2.1              |
| Android SDK        | 35            | 35               |
| minSdk             | 33            | 33               |
| targetSdk          | 35            | 35               |

Native ABI: arm64-v8a only. ARMv8.2-A or later.

Prerequisites
-------------

1. Android Studio (or a standalone Android SDK + NDK + CMake).
2. JDK 17+. Verify: `java -version`.
3. CMake 3.30+. The NDK's bundled 3.22 is NOT sufficient.
4. Ninja: `apt install ninja-build` or `brew install ninja`.
5. `git`, `python3`, `curl`.

Environment variables:

    export ANDROID_HOME=$HOME/Android/Sdk
    export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/27.2.12479018
    export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which javac))))

`ANDROID_NDK_HOME` must point at a specific NDK version directory.

First-time setup
----------------

    git clone --recursive https://github.com/<your-org>/PocoS3.git
    cd PocoS3

    # Two deps that aren't git submodules:
    git clone --depth 1 https://github.com/SnowflakePowered/librashader 3rdparty/librashader
    git clone --depth 1 https://github.com/bylaws/libadrenotools android/pocos3-ui/app/src/main/cpp/libadrenotools

    # The gradle wrapper jar is committed, so this works without a
    # pre-installed Gradle:
    cd android/pocos3-ui && ./gradlew --version

Architecture
------------

PocoS3 has TWO native libraries:

1. **libpocos3-glue.so** (small, ~500 KB): the JNI shim. Built by
   Gradle's `externalNativeBuild` from
   `android/pocos3-ui/app/src/main/cpp/CMakeLists.txt` + `native-lib.cpp`.
   Exports the JNI methods that Kotlin calls.

2. **libpocos3-core.so** (huge, ~1.3 GB unstripped, ~80 MB stripped):
   the upstream RPCS3 core + PocoS3's entry points. Built by
   `android/configure.sh` (a standalone CMake + Ninja invocation). The
   entry points (`_pocos3_*` extern "C" symbols) live in
   `android/src/pocos3-core.cpp`. The CMake target in
   `android/CMakeLists.txt` compiles that file + the input handlers +
   links against `rpcs3_emu` (the upstream RPCS3 object library).

At runtime, libpocos3-glue.so dlopen()s libpocos3-core.so, resolves the
`_pocos3_*` symbols via dlsym(), and delegates JNI calls to them.

Building the native core
------------------------

    ./android/configure.sh --release

Or, manually:

    cmake -S . -B build-android-release-a13 -G Ninja \
      -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake \
      -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-33 \
      -DCMAKE_BUILD_TYPE=RelWithDebInfo \
      -DANDROID_STL=c++_shared \
      -DLLVM_TARGETS_TO_BUILD="AArch64;PowerPC;X86" \
      -DLLVM_BUILD_TOOLS=OFF -DLLVM_INCLUDE_TESTS=OFF -DLLVM_INCLUDE_BENCHMARKS=OFF \
      -DBUILD_SHARED_LIBS=OFF -DUSE_PRECOMPILED_HEADERS=ON

    cmake --build build-android-release-a13 --target pocos3-core -j$(nproc)

Strip + place where Gradle expects it:

    $ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip \
      --strip-unneeded build-android-release-a13/android/libpocos3-core.so

    cp build-android-release-a13/android/libpocos3-core.so \
       android/pocos3-ui/app/src/main/jniLibs/arm64-v8a/

Building the APK
----------------

    cd android/pocos3-ui
    ./gradlew :app:assembleRelease

The APK lands in `app/build/outputs/apk/release/app-release.apk`.

Build variants
--------------

    ./android/build-variants.sh [variant]

| Variant      | minSdk | Stripped | Debuggable |
|--------------|--------|---------|------------|
| debug-a13    | 33     | no      | yes        |
| release-a13  | 33     | yes     | no         |
| debug-a15    | 35     | no      | yes        |
| release-a15  | 35     | yes     | no         |

`minSdk` and the API level the core was compiled against MUST agree —
an APK that installs below its core's target is a `dlopen` failure at boot.

CI
--

Two workflows run on push/PR:

1. `.github/workflows/android.yml` — full build (45-75 min on first run,
   8-15 min after caches hit).
2. `.github/workflows/gradle-smoke.yml` — Gradle sync test only (~5 min).

Signing
-------

The CI produces an unsigned APK by default. To sign:

1. Create a keystore locally:
   ```
   keytool -genkeypair -keystore pocos3.keystore -alias pocos3 \
       -keyalg RSA -keysize 4096 -validity 10000
   ```
2. Base64-encode it and add as a repo secret `POCOS3_SIGNING_KEY`:
   ```
   base64 -w 0 pocos3.keystore > pocos3.keystore.b64
   # paste into GitHub: Settings → Secrets → Actions → New repository secret
   ```
3. Also set `POCOS3_KEY_ALIAS`, `POCOS3_KEYSTORE_PASSWORD`,
   `POCOS3_KEY_PASSWORD`.
4. Push a tag `v0.1.0` — CI signs + creates a GitHub release draft.

Running
-------

PocoS3 needs PS3 firmware, which is NOT included. Install it once via
the onboarding flow (Settings → Reinstall Firmware).

License: GPL-2.0-only, same as upstream RPCS3.
