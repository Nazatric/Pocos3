#!/usr/bin/env bash
# Wipes the build directories so the next configure is a clean build.
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
rm -rf "$REPO_ROOT/build-android-"*
rm -rf "$REPO_ROOT/android/pocos3-ui/app/build"
rm -rf "$REPO_ROOT/android/pocos3-ui/.gradle"
echo "Clean. Next configure.sh will be a full build."
