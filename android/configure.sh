#!/usr/bin/env bash
# =============================================================================
# PocoS3 native core build orchestrator.
#
# Builds the upstream RPCS3 core (from third_party/rpcs3/) + PocoS3's
# android entry-point patches into libpocos3-core.so, ready to be packaged
# by the Gradle build.
#
# The resulting .so is dlopen()'d at runtime by libpocos3-glue.so.
#
# Usage:
#   ./android/configure.sh                 # debug build, defaults
#   ./android/configure.sh --release       # release build, stripped
#   ./android/configure.sh --pgo-generate  # PGO instrumentation build
#   ./android/configure.sh --pgo-use       # PGO-optimized build
#   ./android/configure.sh --target a15   # API 35 (default: a13 / API 33)
# =============================================================================

set -euo pipefail

# -----------------------------------------------------------------------------
# Defaults
# -----------------------------------------------------------------------------
VARIANT="debug"
TARGET_API="a13"
ANDROID_PLATFORM="android-33"
PGO_MODE="off"

# -----------------------------------------------------------------------------
# Arg parsing
# -----------------------------------------------------------------------------
while [[ $# -gt 0 ]]; do
    case "$1" in
        --release)        VARIANT="release"; shift ;;
        --debug)         VARIANT="debug"; shift ;;
        --pgo-generate)  PGO_MODE="generate"; VARIANT="release"; shift ;;
        --pgo-use)       PGO_MODE="use"; VARIANT="release"; shift ;;
        --target)        TARGET_API="$2"; shift 2 ;;
        --target=*)      TARGET_API="${1#--target=}"; shift ;;
        -h|--help)
            sed -n '2,20p' "$0"
            exit 0
            ;;
        *)
            echo "pocos3 configure: unknown option: $1" >&2
            exit 2
            ;;
    esac
done

case "$TARGET_API" in
    a13) ANDROID_PLATFORM="android-33" ;;
    a15) ANDROID_PLATFORM="android-35" ;;
    *)  echo "pocos3 configure: --target must be a13 or a15 (got $TARGET_API)" >&2; exit 2 ;;
esac

# -----------------------------------------------------------------------------
# Environment
# -----------------------------------------------------------------------------
: "${ANDROID_HOME:?Set ANDROID_HOME to your SDK root}"
: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to a specific NDK version directory}"

if [[ ! -d "$ANDROID_NDK_HOME" ]]; then
    echo "pocos3 configure: ANDROID_NDK_HOME=$ANDROID_NDK_HOME does not exist" >&2
    exit 1
fi

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RPCS3_DIR="$REPO_ROOT/third_party/rpcs3"
BUILD_DIR="$REPO_ROOT/build-android-$VARIANT-$TARGET_API"
PATCH_DIR="$REPO_ROOT/patches"

# -----------------------------------------------------------------------------
# Sanity checks
# -----------------------------------------------------------------------------
if [[ ! -d "$RPCS3_DIR/.git" ]]; then
    cat >&2 <<EOF
pocos3 configure: third_party/rpcs3 is not initialized.

Run, from the repo root:
    git submodule update --init --recursive

That pulls RPCS3 + LLVM + FFmpeg + ...; expect ~5 GB of fetch and a long
first checkout.
EOF
    exit 1
fi

if [[ ! -d "$RPCS3_DIR/3rdparty/ffmpeg" ]]; then
    cat >&2 <<EOF
pocos3 configure: third_party/rpcs3's submodules look uninitialized.

The .gitmodules exists but the submodules themselves are missing.
Run, from the repo root:
    cd third_party/rpcs3 && git submodule update --init --recursive
EOF
    exit 1
fi

# -----------------------------------------------------------------------------
# Apply PocoS3 patches to upstream RPCS3. These patches are additive only:
# they add the extern "C" entry points the JNI glue looks up via dlsym().
# -----------------------------------------------------------------------------
echo "==> Verifying PocoS3 patches against upstream RPCS3"
"$PATCH_DIR/apply.sh" "$RPCS3_DIR"

# -----------------------------------------------------------------------------
# CMake invocation. The build itself takes a long time; the first run is
# dominated by the LLVM compile.
# -----------------------------------------------------------------------------
echo "==> Configuring CMake (variant=$VARIANT, target=$TARGET_API, pgo=$PGO_MODE)"

CMAKE_BUILD_TYPE="Debug"
EXTRA_CMAKE_FLAGS=()
case "$VARIANT" in
    release)
        CMAKE_BUILD_TYPE="RelWithDebInfo"
        EXTRA_CMAKE_FLAGS+=("-DCMAKE_UNITY_BUILD=ON")
        ;;
    debug)
        CMAKE_BUILD_TYPE="Debug"
        EXTRA_CMAKE_FLAGS+=("-DCMAKE_UNITY_BUILD=OFF")
        ;;
esac

case "$PGO_MODE" in
    generate)
        EXTRA_CMAKE_FLAGS+=("-DPOCOS3_PGO=generate")
        ;;
    use)
        EXTRA_CMAKE_FLAGS+=("-DPOCOS3_PGO=use")
        EXTRA_CMAKE_FLAGS+=("-DPOCOS3_PGO_DATA_FILE=$REPO_ROOT/build-pgo/pocos3.profdata")
        ;;
esac

cmake -S "$REPO_ROOT" -B "$BUILD_DIR" -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM="$ANDROID_PLATFORM" \
    -DCMAKE_BUILD_TYPE="$CMAKE_BUILD_TYPE" \
    -DANDROID_STL=c++_shared \
    -DLLVM_TARGETS_TO_BUILD="AArch64;PowerPC;X86" \
    -DLLVM_ENABLE_ASSERTIONS=OFF \
    -DLLVM_BUILD_TOOLS=OFF \
    -DLLVM_INCLUDE_TESTS=OFF \
    -DLLVM_INCLUDE_BENCHMARKS=OFF \
    -DLLVM_INCLUDE_EXAMPLES=OFF \
    -DBUILD_SHARED_LIBS=OFF \
    -DUSE_PRECOMPILED_HEADERS=ON \
    "${EXTRA_CMAKE_FLAGS[@]}"

# -----------------------------------------------------------------------------
# Build the core target. The .so lands in
# $BUILD_DIR/android/libpocos3-core.so (unstripped).
# -----------------------------------------------------------------------------
echo "==> Building libpocos3-core.so (this is the slow part)"
cmake --build "$BUILD_DIR" --target pocos3-core -j"$(nproc)"

# -----------------------------------------------------------------------------
# Strip + place the .so where the Gradle module will package it.
# -----------------------------------------------------------------------------
STRIP="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$(uname -s)-$(uname -m)/bin/llvm-strip"
JNI_LIBS_DIR="$REPO_ROOT/android/pocos3-ui/app/src/main/jniLibs/arm64-v8a"
mkdir -p "$JNI_LIBS_DIR"

case "$VARIANT" in
    release)
        echo "==> Stripping libpocos3-core.so"
        "$STRIP" --strip-unneeded "$BUILD_DIR/android/libpocos3-core.so"
        ;;
    debug)
        # Keep symbols; useful for tombstone backtraces.
        ;;
esac

echo "==> Placing libpocos3-core.so at $JNI_LIBS_DIR"
cp "$BUILD_DIR/android/libpocos3-core.so" "$JNI_LIBS_DIR/"

cat <<EOF

==> Done. Next step:

    cd android/pocos3-ui && ./gradlew :app:assembleRelease

The APK will land at android/pocos3-ui/app/build/outputs/apk/release/.

EOF
