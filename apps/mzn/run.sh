#!/usr/bin/env bash
# Run a MiniZinc model by name.
#
# Usage:
#   ./run.sh <model>          -- runs <model>.mzn with <model>.dzn (if present),
#                                using MiniZinc's default solver
#   ./run.sh <model> <solver> -- explicitly specify a solver (e.g. gecode, cp-sat)

set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "usage: $0 <model> [<solver>]" >&2
  exit 2
fi

model="$1"
solver="${2:-}"

here="$(cd "$(dirname "$0")" && pwd)"
mzn="$here/${model}.mzn"
dzn="$here/${model}.dzn"

if [[ ! -f "$mzn" ]]; then
  echo "model not found: $mzn" >&2
  exit 1
fi

if ! command -v minizinc >/dev/null 2>&1; then
  cat >&2 <<EOF
minizinc not found on PATH. Install it from https://www.minizinc.org/software.html
and ensure the 'minizinc' binary is on PATH.
EOF
  exit 1
fi

args=()
if [[ -n "$solver" ]]; then
  args+=(--solver "$solver")
fi
args+=("$mzn")
if [[ -f "$dzn" ]]; then
  args+=("$dzn")
fi

exec minizinc "${args[@]}"
