#!/bin/bash
# One-shot convenience wrapper: runs every tools/setup-*.sh in order,
# then prints a short "what's next" summary.
#
# Safe to run repeatedly — each underlying script is idempotent.
#
# Usage: ./tools/setup-all.sh

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TOOLS_DIR="$REPO_ROOT/tools"

echo "==> cp-deep-dive setup"
echo "    Repo: $REPO_ROOT"
echo

# Collect setup-*.sh scripts in lexicographic order (setup-memory-link,
# setup-qmd-hook, ...). New scripts following the naming convention are
# picked up automatically.
scripts=()
while IFS= read -r -d '' f; do
  scripts+=("$f")
done < <(find "$TOOLS_DIR" -maxdepth 1 -type f -name "setup-*.sh" ! -name "setup-all.sh" -print0 | sort -z)

if [ "${#scripts[@]}" -eq 0 ]; then
  echo "No setup-*.sh scripts found under $TOOLS_DIR."
  exit 0
fi

for script in "${scripts[@]}"; do
  name="$(basename "$script")"
  echo "==> Running $name"
  if [ ! -x "$script" ]; then
    echo "    (making executable)"
    chmod +x "$script"
  fi
  "$script"
  echo
done

cat <<EOF
==> All setup scripts ran.

Next steps:
  1. Open README.md to find your current chapter.
  2. Check docs/plan.md for the full learning roadmap.
  3. Start working chapter-by-chapter from apps/py-cp-sat/ and apps/kt-cp-sat/.

QMD:
  - Local daemon should be running on :8181.
  - If queries return "connection refused":
      launchctl kickstart -k gui/\$(id -u)/io.qmd.daemon
  - To manually re-index: qmd update && qmd embed
EOF
