#!/bin/bash
# Install a git post-commit hook that re-indexes changed markdown files via QMD.
# Runs asynchronously so commits stay fast.
#
# Idempotent: re-running this updates only the qmd-reindex block in the
# post-commit hook and leaves any other blocks (e.g. memory-sync) alone.
#
# Run once per machine: ./tools/setup-qmd-hook.sh

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
HOOK_PATH="$REPO_ROOT/.git/hooks/post-commit"

if ! command -v qmd >/dev/null 2>&1; then
  echo "ERROR: qmd not found in PATH. Install first: npm install -g @tobilu/qmd"
  exit 1
fi

mkdir -p "$(dirname "$HOOK_PATH")"

python3 - "$HOOK_PATH" <<'PY'
import os, sys, pathlib, re

hook_path = pathlib.Path(sys.argv[1])
BEGIN = "# === BEGIN: qmd-reindex (managed by tools/setup-qmd-hook.sh) ==="
END = "# === END: qmd-reindex ==="

block = """# === BEGIN: qmd-reindex (managed by tools/setup-qmd-hook.sh) ===
# QMD auto-reindex after commit. Runs in background so commits stay fast.
# Updates BM25 index + incrementally refreshes vector embeddings for changed files.
# Logs to /tmp/qmd-update.log
{
  qmd update 2>&1
  qmd embed 2>&1
} >> /tmp/qmd-update.log 2>&1 &
disown || true
# === END: qmd-reindex ==="""

if hook_path.exists():
    existing = hook_path.read_text()
else:
    existing = "#!/bin/bash\n"

# Strip any previous qmd-reindex block (between markers)
lines = existing.splitlines(keepends=False)
cleaned, skip = [], False
for ln in lines:
    if ln.strip() == BEGIN:
        skip = True
        continue
    if ln.strip() == END:
        skip = False
        continue
    if not skip:
        cleaned.append(ln)

# Also strip any LEGACY pre-marker QMD content (from before block markers existed)
text = "\n".join(cleaned)
legacy_pattern = re.compile(
    r"\n*# QMD auto-reindex after commit\..*?disown \|\| true\n?",
    re.DOTALL
)
text = legacy_pattern.sub("\n", text)
cleaned = text.splitlines()

# Ensure shebang
if not cleaned or not cleaned[0].startswith("#!"):
    cleaned.insert(0, "#!/bin/bash")

# Append qmd block at the end (memory-sync block, if present, stays at top — qmd runs after)
new_lines = cleaned + ["", block]

# Strip trailing blank-line runs
while len(new_lines) > 1 and new_lines[-1].strip() == "":
    new_lines.pop()

hook_path.write_text("\n".join(new_lines) + "\n")
os.chmod(hook_path, 0o755)
print(f"✓ Updated {hook_path}")
PY

echo ""
echo "  Auto-reindex will run after every commit (in background)."
echo "  Logs: /tmp/qmd-update.log"
