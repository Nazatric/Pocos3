# =============================================================================
# Idempotent patch applier. Run from the repo root:
#     ./patches/apply.sh third_party/rpcs3
#
# Exits 0 if all patches apply cleanly OR are already applied.
# Exits non-zero if a patch fails to apply.
# =============================================================================

#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PATCH_DIR="$REPO_ROOT/patches"
TARGET="${1:-$REPO_ROOT/third_party/rpcs3}"

if [[ ! -d "$TARGET/.git" ]]; then
    echo "patches/apply.sh: target $TARGET is not a git repo" >&2
    exit 1
fi

# Every patch that exists in this directory (excluding this script and docs).
PATCHES=()
while IFS= read -r f; do
    case "$f" in
        *.patch) PATCHES+=("$f") ;;
    esac
done < <(find "$PATCH_DIR" -maxdepth 1 -type f -name '*.patch' | sort)

if [[ ${#PATCHES[@]} -eq 0 ]]; then
    echo "patches/apply.sh: no .patch files present (only .patch.template)"
    echo "patches/apply.sh: see patches/README.md for how to generate real patches"
    echo "patches/apply.sh: marking as applied (stub mode)"
    touch "$TARGET/.pocos3-patches-applied"
    exit 0
fi

echo "==> Applying ${#PATCHES[@]} PocoS3 patches to $TARGET"

applied_any=0
for patch_file in "${PATCHES[@]}"; do
    name="$(basename "$patch_file")"
    # Idempotency: if `git apply --check` succeeds, the patch is NOT yet applied
    # and we apply it. If it fails, the patch is already applied (or has
    # conflicts); we check the conflict case by trying `git apply --3way`.
    if (cd "$TARGET" && git apply --check "$patch_file" 2>/dev/null); then
        echo "  applying: $name"
        (cd "$TARGET" && git apply --verbose "$patch_file")
        applied_any=1
    else
        # Could be: (a) already applied, (b) conflicting. Distinguish by
        # reverse-checking: if `git apply --check -R` succeeds, the patch is
        # already applied and we skip. Otherwise it's a real conflict.
        if (cd "$TARGET" && git apply --check -R "$patch_file" 2>/dev/null); then
            echo "  already applied: $name (skipping)"
        else
            echo "  CONFLICT: $name" >&2
            echo "  resolve manually:" >&2
            echo "    cd $TARGET && git apply --3way $patch_file" >&2
            exit 1
        fi
    fi
done

if [[ $applied_any -eq 1 ]]; then
    echo "==> Patches applied; marking as such"
    touch "$TARGET/.pocos3-patches-applied"
else
    echo "==> All patches already applied; nothing to do"
fi

exit 0
