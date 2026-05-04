#!/bin/bash
# Install a git post-commit hook that mirrors Claude Code project memory
# from the canonical location (~/.claude/projects/<slug>/memory) into the
# repo at <repo>/claude-memory/ for git-based versioning.
#
# Why claude-memory/ and not .claude/memory/:
#   - .claude/* is a HARDCODED protected path in Claude Code: any Edit/Write
#     to it triggers a "sensitive file" prompt regardless of permissions.allow,
#     defaultMode: bypassPermissions, --dangerously-skip-permissions, or
#     PreToolUse auto-approve hooks. Documented behavior, not configurable.
#   - The auto-memory subsystem writes to canonical (~/.claude/projects/...)
#     which is OUTSIDE the project working tree → no protected-path prompt.
#   - This hook then mirrors canonical → <repo>/claude-memory/ for git
#     portability. The mirror destination is OUTSIDE .claude/ so manual
#     edits and rsync writes don't trigger the protected-path check either.
#
# Direction is one-way: canonical → repo (canonical is source of truth,
# repo is a versioned mirror). Manual memory edits should go to canonical
# (~/.claude/projects/<slug>/memory/), not the in-repo mirror.
#
# Idempotent: re-running this updates only the memory-sync block in the
# post-commit hook and leaves any other blocks (e.g. QMD reindex) alone.
#
# Usage: ./tools/setup-memory-hook.sh

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
HOOK_PATH="$REPO_ROOT/.git/hooks/post-commit"
SLUG="$(echo "$REPO_ROOT" | sed 's|/|-|g')"
SRC="$HOME/.claude/projects/$SLUG/memory"

mkdir -p "$(dirname "$HOOK_PATH")"

python3 - "$HOOK_PATH" <<'PY'
import os, sys, pathlib

hook_path = pathlib.Path(sys.argv[1])
BEGIN = "# === BEGIN: memory-sync (managed by tools/setup-memory-hook.sh) ==="
END = "# === END: memory-sync ==="

block = """# === BEGIN: memory-sync (managed by tools/setup-memory-hook.sh) ===
{
  REPO_ROOT_HOOK="$(git rev-parse --show-toplevel 2>/dev/null)" || exit 0
  SLUG_HOOK="$(echo "$REPO_ROOT_HOOK" | sed 's|/|-|g')"
  SRC_HOOK="$HOME/.claude/projects/$SLUG_HOOK/memory"
  DST_HOOK="$REPO_ROOT_HOOK/claude-memory"
  if [ -d "$SRC_HOOK" ]; then
    mkdir -p "$DST_HOOK"
    rsync -a --delete "$SRC_HOOK/" "$DST_HOOK/" 2>/dev/null || true
    cd "$REPO_ROOT_HOOK"
    git add claude-memory/ 2>/dev/null || true
  fi
} >/dev/null 2>&1
# === END: memory-sync ==="""

if hook_path.exists():
    existing = hook_path.read_text()
else:
    existing = "#!/bin/bash\n"

# Strip any previous memory-sync block
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

# Ensure shebang
if not cleaned or not cleaned[0].startswith("#!"):
    cleaned.insert(0, "#!/bin/bash")

# Insert block right after shebang
new_lines = [cleaned[0], "", block, ""] + cleaned[1:]

# Strip trailing blank-line runs to keep file tidy
while len(new_lines) > 1 and new_lines[-1].strip() == "":
    new_lines.pop()

hook_path.write_text("\n".join(new_lines) + "\n")
os.chmod(hook_path, 0o755)
print(f"OK Updated {hook_path}")
PY

echo ""
echo "  Memory source: $SRC"
echo "  Mirrors to:    $REPO_ROOT/claude-memory/ on every commit"
echo "  No permission prompts: canonical write is outside the working tree,"
echo "  and the in-repo mirror is outside .claude/ (the protected prefix)."
