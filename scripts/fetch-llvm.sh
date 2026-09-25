#!/usr/bin/env bash
# =============================================================================
# Fetch LLVM (the one giant 3rdparty dep we can't ship in the repo).
#
# Run by .github/workflows/android.yml BEFORE the CMake configure step.
# Idempotent: skips if the directory already has files.
# =============================================================================

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LLVM_DIR="$REPO_ROOT/third_party/rpcs3/3rdparty/llvm/llvm"

# Skip if already populated.
if [ -d "$LLVM_DIR/.git" ] && [ -n "$(ls -A "$LLVM_DIR" 2>/dev/null)" ]; then
    echo "fetch-llvm.sh: $LLVM_DIR already populated; skipping."
    exit 0
fi

echo "fetch-llvm.sh: cloning LLVM (shallow, depth 1) into $LLVM_DIR"
mkdir -p "$(dirname "$LLVM_DIR")"

# Use depth 1 + filter=blob:none for the smallest possible fetch.
git clone --depth 1 --filter=blob:none --single-branch \
    https://github.com/llvm/llvm-project.git "$LLVM_DIR"

# If a pin file exists, check out that specific SHA.
PIN_FILE="$REPO_ROOT/patches/LLVM_PIN.txt"
if [ -f "$PIN_FILE" ]; then
    PIN_SHA=$(cat "$PIN_FILE" | head -1 | tr -d '[:space:]')
    if [ -n "$PIN_SHA" ] && [ "$PIN_SHA" != "main" ]; then
        echo "fetch-llvm.sh: fetching pinned LLVM commit $PIN_SHA"
        cd "$LLVM_DIR"
        git fetch --depth 1 origin "$PIN_SHA" 2>/dev/null || \
            git fetch --unshallow 2>/dev/null || true
        git checkout "$PIN_SHA" 2>&1 | tail -1
    fi
fi

echo "fetch-llvm.sh: LLVM ready at $LLVM_DIR"
du -sh "$LLVM_DIR"
