#!/bin/bash
# Symlink Claude Code project memory to the versioned copy in this repo.
# Run once per machine after cloning, so memory persists across machines via git.
#
# Usage: ./tools/setup-memory-link.sh

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SLUG="$(echo "$REPO_ROOT" | sed 's|/|-|g')"
TARGET="$HOME/.claude/projects/$SLUG/memory"
SOURCE="$REPO_ROOT/.claude/memory"

if [ ! -d "$SOURCE" ]; then
  echo "ERROR: $SOURCE does not exist. Is this the cp-deep-dive repo root?"
  exit 1
fi

mkdir -p "$(dirname "$TARGET")"

if [ -L "$TARGET" ]; then
  existing="$(readlink "$TARGET")"
  if [ "$existing" = "$SOURCE" ]; then
    echo "OK: Memory symlink already correct: $TARGET -> $SOURCE"
    exit 0
  fi
  echo "ERROR: $TARGET is already a symlink to $existing (not $SOURCE). Resolve manually."
  exit 1
fi

if [ -d "$TARGET" ] && [ -n "$(ls -A "$TARGET" 2>/dev/null)" ]; then
  backup="$TARGET.backup.$(date +%Y%m%d-%H%M%S)"
  echo "WARNING: $TARGET has existing content. Backing up to $backup"
  mv "$TARGET" "$backup"
fi

rm -rf "$TARGET"
ln -s "$SOURCE" "$TARGET"
echo "OK: Linked $TARGET -> $SOURCE"
echo "     Memory is now versioned in this repo and will sync via git."
