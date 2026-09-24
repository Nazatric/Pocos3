#!/usr/bin/env bash
# =============================================================================
# Idempotent patch applier. Currently a no-op: PocoS3's entry points live
# in android/src/pocos3-core.cpp, NOT as patches against upstream RPCS3.
# This script exists so configure.sh + the GitHub Actions workflow can
# call it unconditionally.
#
# To add a real patch in the future:
#   1. Drop a new .patch file in this directory (named
#      <area>-<short-description>.patch).
#   2. The script below will pick it up automatically and apply it via
#      `git apply` to the target (third_party/rpcs3).
# =============================================================================

set -euo pipefail

TARGET="${1:-$(cd "$(dirname "$0")/.." && pwd)/third_party/rpcs3}"

if [[ ! -d "$TARGET/.git" ]]; then
    echo "patches/apply.sh: target $TARGET is not a git repo (this is OK if you don't have submodules initialised yet)"
    exit 0
fi

PATCH_DIR="$(cd "$(dirname "$0")" && pwd)"
PATCHES=()
while IFS= read -r f; do
    case "$f" in
        *.patch) PATCHES+=("$f") ;;
    esac
done < <(find "$PATCH_DIR" -maxdepth 1 -type f -name '*.patch' | sort)

if [[ ${#PATCHES[@]} -eq 0 ]]; then
    echo "patches/apply.sh: no .patch files to apply (entry points live in android/src/pocos3-core.cpp)"
    exit 0
fi

echo "==> Applying ${#PATCHES[@]} PocoS3 patches to $TARGET"
for patch_file in "${PATCHES[@]}"; do
    name="$(basename "$patch_file")"
    if (cd "$TARGET" && git apply --check "$patch_file" 2>/dev/null); then
        echo "  applying: $name"
        (cd "$TARGET" && git apply --verbose "$patch_file")
    elif (cd "$TARGET" && git apply --check -R "$patch_file" 2>/dev/null); then
        echo "  already applied: $name (skipping)"
    else
        echo "  CONFLICT: $name" >&2
        echo "  resolve manually:" >&2
        echo "    cd $TARGET && git apply --3way $patch_file" >&2
        exit 1
    fi
done

exit 0
