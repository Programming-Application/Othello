#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

mkdir -p bin logs

BOARDS="${BOARDS:-3}"
REPEATS="${REPEATS:-1}"
BUDGET_MS="${BUDGET_MS:-30}"
SEED="${SEED:-20260616}"
STAMP="$(date +%Y%m%d-%H%M%S)"
LOG="logs/measure42-${STAMP}.log"

javac -encoding UTF-8 -d bin $(find . -name "*.java")

{
  echo "# command"
  echo "BOARDS=$BOARDS REPEATS=$REPEATS BUDGET_MS=$BUDGET_MS SEED=$SEED scripts/measure42.sh"
  echo
  java -cp "bin:." Measure42 \
    --boards "$BOARDS" \
    --repeats "$REPEATS" \
    --budget-ms "$BUDGET_MS" \
    --seed "$SEED"
} | tee "$LOG"

echo
echo "log: $LOG"
