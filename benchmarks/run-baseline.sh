#!/usr/bin/env bash
# Baseline NSP benchmark: all 3 solvers on both toy instances, 30s each.
#
# Writes results to `benchmarks/results/2026-04-baseline/` by default; override
# the run-id with the first argument.

set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." >/dev/null 2>&1 && pwd)"
RUNNER="$ROOT/benchmarks/runner"
RUN_ID="${1:-2026-04-baseline}"
TIME_LIMIT="${TIME_LIMIT:-30}"
SOLVERS="${SOLVERS:-cpsat,timefold,choco}"
INSTANCES="${INSTANCES:-data/nsp/toy-*.json}"
OUT="$ROOT/benchmarks/results"

echo "--- cp-deep-dive baseline benchmark ---"
echo "run-id:      $RUN_ID"
echo "solvers:     $SOLVERS"
echo "instances:   $INSTANCES"
echo "time-limit:  ${TIME_LIMIT}s"
echo "out:         $OUT/$RUN_ID/"
echo

cd "$RUNNER"
./gradlew --no-daemon -q run --args="\
  --solvers $SOLVERS \
  --instances $INSTANCES \
  --time-limit $TIME_LIMIT \
  --out $OUT \
  --run-id $RUN_ID \
  --project-root $ROOT"

echo
echo "Done. Inspect:"
echo "  cat $OUT/$RUN_ID/results.csv"
echo "  ls  $OUT/$RUN_ID/"
