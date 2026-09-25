#!/usr/bin/env bash
# Inspects libpocos3-core.so to verify the pocos3_* entry points are exported.
# Use this after building to confirm dlsym() will find every symbol the
# JNI glue declares as REQUIRED in android/src/pocos3-android.cpp.
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SO="$REPO_ROOT/android/pocos3-ui/app/src/main/jniLibs/arm64-v8a/libpocos3-core.so"
if [[ ! -f "$SO" ]]; then
    echo "pocos3-core.so not built yet. Run ./android/configure.sh first." >&2
    exit 1
fi
: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME}"
READELF="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$(uname -s)-$(uname -m)/bin/llvm-readelf"

echo "==> Exported pocos3_* symbols:"
"$READELF" --dyn-syms "$SO" | grep pocos3_ | awk '{print $8}' | sort -u

echo ""
echo "==> Required-by-glue (from android/src/pocos3-android.cpp POCOS3_RESOLVE_REQUIRED):"
grep POCOS3_RESOLVE_REQUIRED "$REPO_ROOT/android/src/pocos3-android.cpp" \
    | sed -n 's/.*pocos3_\([a-zA-Z0-9_]*\).*/pocos3_\1/p' | sort -u
