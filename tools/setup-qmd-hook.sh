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
import os, re, sys, pathlib

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

# Validate and remove with the same whole-line parser. Quoted marker text is ordinary
# shell content, not a boundary, and so is a whole-line marker inside a heredoc body or
# an open multi-line quote; blocks must be unique, paired and nonoverlapping.
# Complete validation before writing anything, so malformed blocks preserve the hook.
# Split on newline sequences only; other Unicode line separators inside a line are content.
lines = re.split(r"\r\n|\r|\n", existing)
def shell_contexts(lines):
    """Pair each line with whether it starts inside a context that continues across lines.

    Tracked forms: heredoc bodies opened at command level (<<WORD, quoted or escaped
    words, <<- with tab-stripped terminators, several on one line), single, double
    and ANSI-C ($'...') quotes, $(...) and backtick substitutions including quotes
    opened inside them, and a backslash line continuation. A whole line equal to a
    managed marker in such a context is ordinary content; rewriting around it would
    delete unrelated text. This is not a shell parser: an unbalanced parenthesis
    inside a substitution or other untracked grammar can misjudge later lines in
    either direction, so keep hooks simple; a wrong "nested" verdict only preserves
    the hook for manual review. Known safe-direction over-rejections: "<<" inside
    arithmetic such as $(( x << 2 )) is read as a heredoc opener, and a backslash
    continuation on a heredoc opener line also marks the first line after the
    body as continued.
    """
    pending, active, stack, continued = [], None, [], False
    for ln in lines:
        if active is not None:
            yield ln, True
            word, strip_tabs = active
            if (ln.lstrip("\t") if strip_tabs else ln) == word:
                active = pending.pop(0) if pending else None
            continue
        yield ln, bool(stack) or continued
        continued = False
        i = 0
        while i < len(ln):
            ch = ln[i]
            top = stack[-1] if stack else None
            if top == "'":
                if ch == "'":
                    stack.pop()
            elif top == "$'":
                if ch == "\\":
                    i += 1
                elif ch == "'":
                    stack.pop()
            elif top == '"':
                if ch == "\\":
                    continued = i == len(ln) - 1
                    i += 1
                elif ch == '"':
                    stack.pop()
                elif ln.startswith("$(", i):
                    stack.append("(")
                    i += 1
                elif ch == "`":
                    stack.append("`")
            else:  # command level, inside $(...) or inside backticks
                if ch == "\\":
                    continued = i == len(ln) - 1
                    i += 1
                elif ln.startswith("$'", i):
                    stack.append("$'")
                    i += 1
                elif ch == "'":
                    stack.append("'")
                elif ch == '"':
                    stack.append('"')
                elif ln.startswith("$(", i):
                    stack.append("(")
                    i += 1
                elif ch == ")" and top == "(":
                    stack.pop()
                elif ch == "`":
                    if top == "`":
                        stack.pop()
                    else:
                        stack.append("`")
                elif ch == "#" and (i == 0 or ln[i - 1] in " \t;&|()"):
                    break
                elif ln.startswith("<<", i) and not ln.startswith("<<<", i) and (i == 0 or ln[i - 1] != "<"):
                    opener = re.match(r"<<(-?)\s*(?:'([^']*)'|\"([^\"]*)\"|([^\s;&|<>()]+))", ln[i:])
                    if opener:
                        # A quoted word is literal; bash removes backslashes only from a bare word.
                        word = opener.group(4).replace("\\", "") if opener.group(4) is not None else (
                            opener.group(2) if opener.group(2) is not None else opener.group(3))
                        pending.append((word, opener.group(1) == "-"))
                        i += opener.end() - 1
            i += 1
        if pending:
            active = pending.pop(0)

begins = {BEGIN: END}
ends = set(begins.values())
cleaned, seen, active_end = [], set(), None
for ln, nested in shell_contexts(lines):
    marker = ln.strip()
    if nested and (marker in begins or marker in ends):
        raise SystemExit("Managed hook markers inside quoted or heredoc content; preserve and repair the existing hook first")
    if marker in begins:
        if active_end is not None or marker in seen:
            raise SystemExit("Malformed managed hook markers; preserve and repair the existing hook first")
        seen.add(marker)
        active_end = begins[marker]
        continue
    if marker in ends:
        if marker != active_end:
            raise SystemExit("Malformed managed hook markers; preserve and repair the existing hook first")
        active_end = None
        continue
    if active_end is None:
        cleaned.append(ln)
if active_end is not None:
    raise SystemExit("Malformed managed hook markers; preserve and repair the existing hook first")

# Migrate only the complete original block (Life commit 68dcbbed), at top level
# after a comment/blank preamble. Matching endpoints alone could swallow custom
# commands; matching the body inside a quote/heredoc would corrupt unrelated text.
legacy_qmd = """# QMD auto-reindex after commit. Runs in background so commits stay fast.
# Updates BM25 index + incrementally refreshes vector embeddings for changed files.
# Logs to /tmp/qmd-update.log

{
  qmd update 2>&1
  qmd embed 2>&1
} >> /tmp/qmd-update.log 2>&1 &
disown || true""".splitlines()
legacy_starts = [i for i, line in enumerate(cleaned)
                 if "QMD auto-reindex after commit." in line]
if legacy_starts:
    start = legacy_starts[0]
    preamble_only = all(not line.strip() or line.lstrip().startswith("#")
                        for line in cleaned[:start])
    if (len(legacy_starts) != 1 or not preamble_only
            or cleaned[start:start + len(legacy_qmd)] != legacy_qmd):
        raise SystemExit("Ambiguous legacy QMD hook content; preserve and review the existing hook first")
    cleaned = cleaned[:start] + cleaned[start + len(legacy_qmd):]

while cleaned and not cleaned[-1].strip():
    cleaned.pop()

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
