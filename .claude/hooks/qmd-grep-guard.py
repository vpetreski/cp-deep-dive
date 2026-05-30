#!/usr/bin/env python3
"""QMD-first guard (PreToolUse hook).

Blocks grep/rg/etc. over this repo's QMD-indexed knowledge dirs so searches go
through `mcp__qmd__query` instead of grep. The protected dir names are passed as
CLI args (derived per-repo from ~/.config/qmd/index.yml), e.g.:

    qmd-grep-guard.py content data        # life / altius / cinc
    qmd-grep-guard.py docs                # cp-deep-dive
    qmd-grep-guard.py content ideas common  # indie-hacking

Enforcement:
  - Grep tool: deny if path is unset/repo-root/"." (repo-wide includes knowledge
    dirs) or resolves under a protected dir. Allow when an explicit non-knowledge
    path (tools/, src/, ...) is given.
  - Bash: deny grep-family commands that target a protected dir ("... content/"),
    recurse over "." / "*", or use `git grep` (repo-wide). Allow single-file/other.

FAILS OPEN on any error — a bug here must never brick Grep/Bash. Every block is
appended to ~/.qmd-grep-audit.log (timestamp, repo, tool, detail) for audit.
"""
import sys, json, os, re, datetime

_GREP = r"(?:grep|egrep|fgrep|rg|ripgrep|ag|ack)"
# grep-family invoked AS A COMMAND (line start, or after a pipe / ; / && / || / subshell) —
# not when "grep" merely appears as text inside a quoted argument (e.g. git commit -m "...grep...").
CMD_GREP = r"(?:^|[\n|;&(]|&&|\|\|)\s*(?:sudo\s+|command\s+|time\s+)?" + _GREP + r"\b"
GIT_GREP = r"(?:^|[\n|;&(]|&&|\|\|)\s*git\s+grep\b"


def main():
    roots = [r for r in sys.argv[1:] if r] or ["content", "data"]
    data = json.loads(sys.stdin.read())
    tool = data.get("tool_name", "")
    if tool not in ("Grep", "Bash"):
        return
    ti = data.get("tool_input", {}) or {}
    alt = "|".join(re.escape(r) for r in roots)

    def deny(detail):
        try:
            with open(os.path.expanduser("~/.qmd-grep-audit.log"), "a") as f:
                ts = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
                f.write(f"{ts}\t{os.path.basename(os.getcwd())}\t{tool}\t{detail}\n")
        except Exception:
            pass
        reason = (
            "Knowledge-base search blocked — use mcp__qmd__query (lex/vec/hyde) instead. "
            f"grep over [{', '.join(roots)}] is disabled to enforce QMD-first. "
            "Known single file? use Read. Structural code search? target tools/ (or another "
            "non-knowledge dir) explicitly so it isn't repo-wide."
        )
        print(json.dumps({"hookSpecificOutput": {
            "hookEventName": "PreToolUse",
            "permissionDecision": "deny",
            "permissionDecisionReason": reason,
        }}))
        sys.exit(0)

    if tool == "Grep":
        path = (ti.get("path") or "").strip()
        if path in ("", ".", "./"):
            deny("Grep repo-wide (no/'.' path includes knowledge dirs)")
        norm = path.lstrip("./").rstrip("/")
        if re.search(rf"(^|/)(?:{alt})(/|$)", norm):
            deny(f"Grep path={path}")
        return

    # Bash
    cmd = ti.get("command", "") or ""
    if re.search(GIT_GREP, cmd):
        deny(f"Bash git grep (repo-wide): {cmd[:160]}")
    if not re.search(CMD_GREP, cmd):
        return
    # grep-family touching "<root>/..."
    if re.search(rf"(^|[\s=/'\"(])(?:{alt})/", cmd):
        deny(f"Bash grep over knowledge dir: {cmd[:160]}")
    # recursive grep over cwd / "." / "*" / bare root dir
    if re.search(r"(?:\s-[A-Za-z]*[rR]|--recursive)", cmd) and re.search(rf"(\s)(?:{alt}|\.|\*)(\s|$|[|;&])", cmd + " "):
        deny(f"Bash recursive grep over cwd/knowledge: {cmd[:160]}")
    return


try:
    main()
except Exception:
    sys.exit(0)  # fail open — never brick Grep/Bash
