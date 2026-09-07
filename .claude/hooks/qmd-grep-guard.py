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
  - Bash: recognize simple rg/ripgrep operands, including default recursion and
    cwd-relative scopes. Other grep-family commands keep the legacy pattern guard.
    Allow explicit non-knowledge scopes/files; complex shell syntax remains fail-open.

FAILS OPEN on any error — a bug here must never brick Grep/Bash. Every block is
appended to ~/.qmd-grep-audit.log (timestamp, repo, tool, detail) for audit.
"""
import sys, json, os, re, datetime, shlex
from pathlib import Path

_GREP = r"(?:grep|egrep|fgrep|rg|ripgrep|ag|ack)"
# grep-family invoked AS A COMMAND (line start, or after a pipe / ; / && / || / subshell) —
# not when "grep" merely appears as text inside a quoted argument (e.g. git commit -m "...grep...").
CMD_GREP = r"(?:^|[\n|;&(]|&&|\|\|)\s*(?:sudo\s+|command\s+|time\s+)?" + _GREP + r"\b"
GIT_GREP = r"(?:^|[\n|;&(]|&&|\|\|)\s*git\s+grep\b"


def rg_paths(command):
    """Return literal search operands for a supported simple rg call, else None.

    This is deliberately not a shell evaluator. Pipes, redirections, compound
    commands, expansions and unknown flags retain the existing fallback below.
    Flag values and patterns never become paths merely because they name a root.
    """
    # Retain quotes while recognizing operators so a literal regex such as '|'
    # is not mistaken for a shell pipeline. The second pass unquotes arguments.
    lexer = shlex.shlex(command, posix=False, punctuation_chars=";&|()<>")
    lexer.whitespace_split = True
    lexer.commenters = "#"
    words = list(lexer)
    if any(word and all(c in ";&|()<>" for c in word) for word in words):
        return None
    words = shlex.split(command, comments=True, posix=True)
    if words and words[0] in ("command", "time", "sudo"):
        words = words[1:]
    if not words or words[0] not in ("rg", "ripgrep"):
        return None
    values = {"-e", "--regexp", "-f", "--file", "-g", "--glob", "--iglob",
              "-t", "--type", "-T", "--type-not", "-A", "--after-context",
              "-B", "--before-context", "-C", "--context", "-m", "--max-count",
              "-M", "--max-columns", "-j", "--threads", "--max-depth", "--sort",
              "--sortr", "--encoding", "--engine", "--color"}
    switches = {"--hidden", "--no-ignore", "--no-ignore-vcs", "--no-ignore-parent",
                "--line-number", "--no-line-number", "--with-filename",
                "--no-filename", "--fixed-strings", "--ignore-case", "--smart-case",
                "--case-sensitive", "--word-regexp", "--line-regexp", "--invert-match",
                "--files-with-matches", "--files-without-match", "--count",
                "--count-matches", "--only-matching", "--quiet", "--text",
                "--multiline", "--multiline-dotall", "--pcre2", "--json",
                "--heading", "--no-heading", "--follow", "--no-messages",
                "--trim", "--stats", "--no-config"}
    patterns = {"-e", "--regexp", "-f", "--file"}
    explicit_pattern = False
    operands = []
    options = True
    i = 1
    while i < len(words):
        word = words[i]
        i += 1
        if options and word == "--":
            options = False
            continue
        if options and word.startswith("-") and word != "-":
            flag = word.split("=", 1)[0]
            if flag in values:
                explicit_pattern |= flag in patterns
                if "=" not in word:
                    if i >= len(words):
                        return None
                    i += 1
            elif word[:2] in values and len(word) > 2 and not word.startswith("--"):
                explicit_pattern |= word[:2] in patterns
            elif word in switches or re.fullmatch(r"-[nHhFiIsSwxvlLcoqatUPz0u]+", word):
                pass
            else:
                return None
        else:
            operands.append(word)
    if not explicit_pattern and not operands:
        return None
    paths = operands if explicit_pattern else operands[1:]
    if any(any(c in path for c in "$`*?[]{}") for path in paths):
        return None
    return paths or ["."]


def knowledge_scope(value, cwd, project, roots):
    """Resolve scope against native cwd, preserving the protected-file rule."""
    target = (cwd / Path(value).expanduser()).resolve()
    for name in roots:
        protected = (project / name).resolve()
        if (target == protected or target in protected.parents or
                protected in target.parents):
            return True
    return False


def main():
    roots = [r for r in sys.argv[1:] if r] or ["content", "data"]
    data = json.loads(sys.stdin.read())
    tool = data.get("tool_name", "")
    if tool not in ("Grep", "Bash"):
        return
    ti = data.get("tool_input", {}) or {}
    alt = "|".join(re.escape(r) for r in roots)
    project = Path(os.environ.get("CLAUDE_PROJECT_DIR") or Path(__file__).resolve().parents[2]).resolve()
    cwd = Path(data.get("cwd") or os.getcwd()).expanduser().resolve()

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
        if knowledge_scope(path, cwd, project, roots):
            deny(f"Grep path={path}")
        return

    # Bash
    cmd = ti.get("command", "") or ""
    paths = rg_paths(cmd)
    if paths is not None:
        # Only rg treats the literal '-' operand as stdin. Grep's path is a path.
        if any(path != "-" and knowledge_scope(path, cwd, project, roots) for path in paths):
            deny(f"Bash rg knowledge scope: {cmd[:160]}")
        return
    if re.search(GIT_GREP, cmd):
        deny(f"Bash git grep (repo-wide): {cmd[:160]}")
    if not re.search(CMD_GREP, cmd):
        return
    # Preserve the adapter's older conservative nested-cwd check for grammar
    # whose exact operands are not parsed above. This is still a workflow guard.
    if any(cwd == (project / name).resolve() or
           (project / name).resolve() in cwd.parents for name in roots):
        deny(f"Bash grep in knowledge cwd: {cmd[:160]}")
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
