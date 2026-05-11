#!/bin/bash
# QMD health doctor — diagnose and auto-fix common QMD failures.
#
# Detects:
#   1. better-sqlite3 native binary ABI mismatch (after Node upgrade)
#   2. Daemon stopped / not responding on MCP HTTP endpoint
#   3. Daemon running on stale in-memory binary (would die on next restart)
#
# Auto-fixes:
#   - npm rebuild better-sqlite3 in the qmd install dir (fixes ABI mismatch)
#   - launchctl kickstart the daemon (forces it onto the freshly-built binary)
#   - Removes ~/.qmd-broken flag once green
#
# Exit codes:
#   0 — healthy (was healthy or fixed successfully)
#   1 — diagnosed but couldn't auto-fix (needs human)
#   2 — qmd not installed
#
# Usage:
#   ./tools/qmd-doctor.sh           # diagnose + auto-fix
#   ./tools/qmd-doctor.sh --check   # diagnose only (used by post-commit hook)

set -uo pipefail

FLAG_FILE="$HOME/.qmd-broken"
CHECK_ONLY=0
if [[ "${1:-}" == "--check" ]]; then
  CHECK_ONLY=1
fi

log() { echo "[qmd-doctor] $*"; }
err() { echo "[qmd-doctor] ERROR: $*" >&2; }

# --- step 0: qmd installed? ---
if ! command -v qmd >/dev/null 2>&1; then
  err "qmd binary not found in PATH. Install: npm install -g @tobilu/qmd"
  exit 2
fi

QMD_LINK=$(command -v qmd)
# Resolve all symlinks via python to find the actual bin/qmd file, then walk up 2 levels
# to reach the @tobilu/qmd package root. macOS readlink doesn't have -f.
QMD_REAL=$(python3 -c "import os,sys;print(os.path.realpath(sys.argv[1]))" "$QMD_LINK" 2>/dev/null)
if [[ -n "$QMD_REAL" && -f "$QMD_REAL" ]]; then
  QMD_PKG_ROOT="$(cd "$(dirname "$QMD_REAL")/.." && pwd)"
fi
if [[ -z "${QMD_PKG_ROOT:-}" || ! -d "$QMD_PKG_ROOT/node_modules" ]]; then
  # fallback to standard homebrew global location
  QMD_PKG_ROOT="/opt/homebrew/lib/node_modules/@tobilu/qmd"
fi

# --- step 1: probe CLI ---
CLI_STATUS_OUT=$(qmd status 2>&1 | head -50)
CLI_OK=0
if echo "$CLI_STATUS_OUT" | grep -q "NODE_MODULE_VERSION"; then
  CLI_OK=0
  ISSUE_ABI=1
elif echo "$CLI_STATUS_OUT" | grep -q "Total:.*files indexed"; then
  CLI_OK=1
  ISSUE_ABI=0
else
  CLI_OK=0
  ISSUE_ABI=0
fi

if [[ $CLI_OK -eq 1 ]]; then
  log "CLI healthy."
else
  log "CLI broken."
  if [[ ${ISSUE_ABI:-0} -eq 1 ]]; then
    log "Cause: better-sqlite3 native binary ABI mismatch (Node was upgraded)."
    if [[ $CHECK_ONLY -eq 1 ]]; then
      log "FLAG: $FLAG_FILE will be written."
    else
      log "Auto-fixing: npm rebuild better-sqlite3 in $QMD_PKG_ROOT ..."
      if (cd "$QMD_PKG_ROOT" && npm rebuild better-sqlite3 2>&1 | tail -3); then
        log "Rebuild done. Re-probing CLI..."
        if qmd status >/dev/null 2>&1; then
          CLI_OK=1
          log "CLI healthy after rebuild."
        else
          err "Rebuild ran but CLI still broken. Manual: cd $QMD_PKG_ROOT && npm install"
        fi
      else
        err "npm rebuild failed. Check Xcode CLI tools + node-gyp availability."
      fi
    fi
  fi
fi

# --- step 2: probe daemon ---
DAEMON_PID=$(launchctl list io.qmd.daemon 2>/dev/null | awk '/"PID"/{gsub(/[;]/,""); print $3; exit}')
DAEMON_OK=0
DAEMON_STALE=0

if [[ -z "$DAEMON_PID" || "$DAEMON_PID" == "0" ]]; then
  log "Daemon not running (launchd reports no PID)."
else
  # Probe via MCP initialize handshake (the only way to get a 200 from an MCP server).
  # Check both common ports (8181 + 9876) plus whatever is in the recent log.
  PORT=""
  INIT_PAYLOAD='{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"qmd-doctor","version":"1"}}}'
  for p in 8181 9876 $(grep -oE 'localhost:[0-9]+' /tmp/qmd.err 2>/dev/null | tail -1 | grep -oE '[0-9]+$'); do
    RESP=$(curl -fsS --max-time 3 -X POST "http://localhost:${p}/mcp" \
           -H "Content-Type: application/json" \
           -H "Accept: application/json, text/event-stream" \
           -d "$INIT_PAYLOAD" 2>/dev/null || true)
    if [[ -n "$RESP" ]] && echo "$RESP" | grep -q '"serverInfo"'; then
      PORT=$p
      break
    fi
  done
  if [[ -n "$PORT" ]]; then
    DAEMON_OK=1
    log "Daemon healthy (PID $DAEMON_PID, port $PORT)."
  else
    log "Daemon process exists (PID $DAEMON_PID) but MCP endpoint not responding."
  fi

  # Detect stale-binary risk: daemon started BEFORE the on-disk binary mtime.
  # If we just rebuilt, daemon is running on the old in-memory binary.
  if [[ $CLI_OK -eq 1 ]]; then
    BIN_PATH="$QMD_PKG_ROOT/node_modules/better-sqlite3/build/Release/better_sqlite3.node"
    if [[ -f "$BIN_PATH" ]]; then
      BIN_MTIME=$(stat -f %m "$BIN_PATH" 2>/dev/null || stat -c %Y "$BIN_PATH" 2>/dev/null)
      DAEMON_START_S=$(ps -p "$DAEMON_PID" -o lstart= 2>/dev/null | xargs -I{} date -j -f "%a %b %d %T %Y" "{}" "+%s" 2>/dev/null || echo "0")
      if [[ "$BIN_MTIME" -gt "$DAEMON_START_S" && "$DAEMON_START_S" -gt 0 ]]; then
        DAEMON_STALE=1
        log "Daemon is running on OLDER binary than on-disk (next restart would die)."
      fi
    fi
  fi
fi

if [[ $DAEMON_OK -eq 0 || $DAEMON_STALE -eq 1 ]]; then
  if [[ $CHECK_ONLY -eq 1 ]]; then
    log "FLAG: daemon needs restart (kickstart)."
  else
    log "Restarting daemon: launchctl kickstart -k gui/$(id -u)/io.qmd.daemon ..."
    if launchctl kickstart -k "gui/$(id -u)/io.qmd.daemon" 2>&1; then
      sleep 2
      NEW_PID=$(launchctl list io.qmd.daemon 2>/dev/null | awk '/"PID"/{gsub(/[;]/,""); print $3; exit}')
      log "Daemon restarted (PID $NEW_PID)."
      DAEMON_OK=1
      DAEMON_STALE=0
    else
      err "Failed to kickstart daemon."
    fi
  fi
fi

# --- step 3: write/clear flag file ---
if [[ $CLI_OK -eq 1 && $DAEMON_OK -eq 1 && $DAEMON_STALE -eq 0 ]]; then
  if [[ -f "$FLAG_FILE" ]]; then
    rm -f "$FLAG_FILE"
    log "Removed flag $FLAG_FILE."
  fi
  log "ALL GREEN."
  exit 0
else
  {
    echo "QMD BROKEN — detected $(date -Iseconds)"
    echo "  CLI healthy: $CLI_OK"
    echo "  Daemon healthy: $DAEMON_OK"
    echo "  Daemon stale (would die on restart): $DAEMON_STALE"
    echo "  ABI mismatch detected: ${ISSUE_ABI:-0}"
    echo ""
    echo "Recovery:"
    echo "  $(dirname "$0")/qmd-doctor.sh"
  } > "$FLAG_FILE"
  err "QMD UNHEALTHY. Flag written to $FLAG_FILE."

  # Surface via macOS notification (best-effort, ignored if osascript missing)
  if command -v osascript >/dev/null 2>&1; then
    osascript -e 'display notification "QMD is broken — run tools/qmd-doctor.sh" with title "QMD Health Alert"' 2>/dev/null || true
  fi
  exit 1
fi
