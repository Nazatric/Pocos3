#!/usr/bin/env bash
# =============================================================================
# PocoS3 APK variant builder.
#
# Produces four variants keyed on minSdk and debuggability:
#   debug-a13    : minSdk=33, debuggable,    unstripped .so
#   release-a13  : minSdk=33, not debuggable, stripped .so
#   debug-a15    : minSdk=35, debuggable,    unstripped .so
#   release-a15  : minSdk=35, not debuggable, stripped .so
#
# Usage:
#   ./android/build-variants.sh               # build all four
#   ./android/build-variants.sh release-a13   # build one
# =============================================================================

set -euo pipefail
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
UI_DIR="$REPO_ROOT/android/pocos3-ui"

VARIANTS=("debug-a13" "release-a13" "debug-a15" "release-a15")

if [[ $# -gt 0 ]]; then
    VARIANTS=("$1")
fi

for v in "${VARIANTS[@]}"; do
    case "$v" in
        debug-a13)    MIN_SDK=33; TARGET=debug;   GRADLE_TASK="assembleDebug" ;;
        release-a13) MIN_SDK=33; TARGET=release; GRADLE_TASK="assembleRelease" ;;
        debug-a15)    MIN_SDK=35; TARGET=debug;   GRADLE_TASK="assembleDebug" ;;
        release-a15) MIN_SDK=35; TARGET=release; GRADLE_TASK="assembleRelease" ;;
        *)
            echo "pocos3 build-variants: unknown variant '$v'" >&2
            exit 2
            ;;
    esac

    echo "==> Building variant=$v (minSdk=$MIN_SDK, target=$TARGET)"
    "$REPO_ROOT/android/configure.sh" --target "a${MIN_SDK:0:2}" \
        $( [[ "$TARGET" == "release" ]] && echo --release || echo --debug )

    cd "$UI_DIR"
    ./gradlew ":app:$GRADLE_TASK" \
        -Ppocos3.minSdk="$MIN_SDK" \
        -Ppocos3.variant="$TARGET"

    OUT_APK="$UI_DIR/app/build/outputs/apk/$TARGET/app-$TARGET.apk"
    OUT_DIR="$REPO_ROOT/build/dist"
    mkdir -p "$OUT_DIR"
    cp "$OUT_APK" "$OUT_DIR/pocos3-$v.apk"
    echo "==> $v: $OUT_DIR/pocos3-$v.apk"
done
