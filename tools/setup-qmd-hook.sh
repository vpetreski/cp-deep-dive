#!/bin/bash
# Install a git post-commit hook that re-indexes changed markdown files via QMD.
# Runs asynchronously so commits stay fast.
# Run once: ./tools/setup-qmd-hook.sh

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
HOOK_PATH="$REPO_ROOT/.git/hooks/post-commit"

if ! command -v qmd >/dev/null 2>&1; then
  echo "ERROR: qmd not found in PATH. Install first: npm install -g @tobilu/qmd (or brew install qmd)"
  exit 1
fi

cat > "$HOOK_PATH" <<'HOOK'
#!/bin/bash
# QMD auto-reindex after commit. Runs in background so commits stay fast.
# Updates BM25 index + incrementally refreshes vector embeddings for changed files.
# Logs to /tmp/qmd-update.log

{
  qmd update 2>&1
  qmd embed 2>&1
} >> /tmp/qmd-update.log 2>&1 &
disown || true
HOOK

chmod +x "$HOOK_PATH"
echo "OK: Installed $HOOK_PATH"
echo "    Auto-reindex will run after every commit."
echo "    Logs: /tmp/qmd-update.log"
