#!/usr/bin/env bash
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LLVM_DIR="$REPO_ROOT/third_party/rpcs3/3rdparty/llvm/llvm"
if [ -d "$LLVM_DIR/.git" ] && [ -n "$(ls -A "$LLVM_DIR" 2>/dev/null)" ]; then
    echo "fetch-llvm.sh: already populated; skipping."
    exit 0
fi
echo "fetch-llvm.sh: cloning LLVM at RPCS3 expected commit ca7933e"
mkdir -p "$(dirname "$LLVM_DIR")"
git clone --depth 1 --single-branch https://github.com/llvm/llvm-project.git "$LLVM_DIR"
cd "$LLVM_DIR"
git fetch --depth 1 origin ca7933e47d3a3451d81e72ac174dcb5aa28b59d1
git checkout ca7933e47d3a3451d81e72ac174dcb5aa28b59d1
echo "fetch-llvm.sh: LLVM ready (commit ca7933e)"
du -sh "$LLVM_DIR"
