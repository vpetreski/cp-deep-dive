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
HOOK_PATH="$(git -C "$REPO_ROOT" rev-parse --path-format=absolute --git-path hooks/post-commit)"

if ! command -v ai-qmd >/dev/null 2>&1; then
  echo "ERROR: ai-qmd not found in PATH. Install the personal QMD maintenance tool from dotfiles first."
  exit 1
fi

mkdir -p "$(dirname "$HOOK_PATH")"

python3 - "$HOOK_PATH" <<'PY'
import os, sys, pathlib, re

hook_path = pathlib.Path(sys.argv[1])
BEGIN = "# === BEGIN: qmd-reindex (managed by tools/setup-qmd-hook.sh) ==="
END = "# === END: qmd-reindex ==="

block = """# === BEGIN: qmd-reindex (managed by tools/setup-qmd-hook.sh) ===
# Persist source-generation evidence, then let the shared updater coalesce and serialize work.
# No native indexing runs in this post-commit process. Failures remain visible in ~/.qmd-broken
# and private ~/.local/state/ai-workspace/qmd/{failure.json,native.log}; retry on the next request.
REPO_DIR="$(git rev-parse --show-toplevel 2>/dev/null)"
if ! ai-qmd request --root "$REPO_DIR" --event post-commit >/dev/null; then
  echo "WARNING: QMD maintenance request failed; run ai-qmd status --json" >&2
fi
# === END: qmd-reindex ==="""

if hook_path.exists():
    existing = hook_path.read_text()
else:
    existing = "#!/bin/bash\n"

# A malformed existing managed block is unavailable, never permission to drop its tail.
for begin, end in [(BEGIN, END)]:
    if existing.count(begin) != existing.count(end) or existing.count(begin) > 1:
        raise SystemExit("Malformed managed hook markers; preserve and repair the existing hook first")
    if begin in existing and existing.index(begin) > existing.index(end):
        raise SystemExit("Reversed managed hook markers; preserve the existing hook")

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
echo "  Commits queue QMD maintenance; the shared updater coalesces and serializes pending requests."
echo "  Status: ai-qmd status --json; private log: ~/.local/state/ai-workspace/qmd/native.log"

# Refresh declared shared instructions and native discovery; hooks stay global.
if command -v ai-workspace >/dev/null 2>&1; then
  ai-workspace sync --root "$REPO_ROOT"
else
  echo 'Agent adapters: install ai-workspace, then run ai-workspace sync and ai-workspace check in this repository.' >&2
fi
