#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LLVM_DIR="$REPO_ROOT/third_party/rpcs3/3rdparty/llvm/llvm"

if [ -d "$LLVM_DIR/.git" ] && [ -n "$(ls -A "$LLVM_DIR" 2>/dev/null)" ]; then
    echo "fetch-llvm.sh: $LLVM_DIR already populated; skipping."
    exit 0
fi

echo "fetch-llvm.sh: cloning LLVM (shallow, depth 1) into $LLVM_DIR"
mkdir -p "$(dirname "$LLVM_DIR")"

git clone --depth 1 --filter=blob:none --single-branch \
    https://github.com/llvm/llvm-project.git "$LLVM_DIR"

echo "fetch-llvm.sh: LLVM ready at $LLVM_DIR"
du -sh "$LLVM_DIR"
