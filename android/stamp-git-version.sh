#!/usr/bin/env bash
# =============================================================================
# Stamps version.txt with the current git SHA + short date.
# Used by the CMake build to embed version info in the .so and by the Gradle
# build to set versionName / versionCode.
# =============================================================================

set -euo pipefail
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$REPO_ROOT/build/version.txt"
mkdir -p "$(dirname "$OUT")"

GIT_SHA=$(git -C "$REPO_ROOT" rev-parse --short HEAD 2>/dev/null || echo "unknown")
GIT_BRANCH=$(git -C "$REPO_ROOT" rev-parse --abbrev-ref HEAD 2>/dev/null || echo "unknown")
GIT_DIRTY=$(git -C "$REPO_ROOT" status --porcelain 2>/dev/null | head -n1 || true)
DATE=$(date -u +%Y%m%d)

if [[ -n "$GIT_DIRTY" ]]; then
    GIT_SHA="${GIT_SHA}-dirty"
fi

cat > "$OUT" <<EOF
sha=$GIT_SHA
branch=$GIT_BRANCH
date=$DATE
EOF

echo "$OUT"
