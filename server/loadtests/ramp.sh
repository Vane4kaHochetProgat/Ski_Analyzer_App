#!/usr/bin/env bash
set -euo pipefail

HOST="${HOST:-http://127.0.0.1:8001}"
DURATION="${DURATION:-60s}"
STEPS="${STEPS:-5 10 25 50 100}"
DB_NAME="${DB_NAME:-technique}"
CLEAN_USERS="${CLEAN_USERS:-1}"

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
RESULTS_DIR="$SCRIPT_DIR/results_$(date +%Y%m%d_%H%M%S)"
LOCUST_BIN="$SCRIPT_DIR/../.venv/bin/locust"

if [ ! -x "$LOCUST_BIN" ]; then
    echo "locust not found at $LOCUST_BIN" >&2
    echo "Run: $SCRIPT_DIR/../.venv/bin/pip install locust" >&2
    exit 1
fi

if ! curl -s --max-time 2 "$HOST/health" >/dev/null; then
    echo "Server at $HOST is not responding to /health" >&2
    exit 1
fi

mkdir -p "$RESULTS_DIR"
echo "Host          : $HOST"
echo "Per-step time : $DURATION"
echo "Steps (VU)    : $STEPS"
echo "Results       : $RESULTS_DIR"
echo

cleanup_test_users() {
    if [ "$CLEAN_USERS" = "1" ] && command -v psql >/dev/null 2>&1; then
        psql -d "$DB_NAME" -q -c "DELETE FROM users WHERE email LIKE '%@load.test';" >/dev/null 2>&1 || true
    fi
}

cd "$SCRIPT_DIR"
cleanup_test_users

declare -a SUMMARY_ROWS

for VU in $STEPS; do
    SPAWN=$(( VU < 10 ? VU : 10 ))
    PREFIX="$RESULTS_DIR/run_${VU}vu"
    echo "==== Running with $VU VU (spawn $SPAWN/s, $DURATION) ===="

    "$LOCUST_BIN" \
        --headless \
        --host "$HOST" \
        -u "$VU" -r "$SPAWN" -t "$DURATION" \
        --csv "$PREFIX" \
        --only-summary \
        >"$PREFIX.log" 2>&1 || true

    STATS_CSV="${PREFIX}_stats.csv"
    if [ ! -f "$STATS_CSV" ]; then
        echo "  (no stats file produced — see $PREFIX.log)"
        SUMMARY_ROWS+=("$VU|—|—|—|—|—|—")
        continue
    fi

    ROW=$(awk -F',' '$2=="Aggregated" {print}' "$STATS_CSV")
    if [ -z "$ROW" ]; then
        echo "  (no Aggregated row in $STATS_CSV)"
        SUMMARY_ROWS+=("$VU|—|—|—|—|—|—")
        continue
    fi

    REQ_COUNT=$(echo "$ROW" | awk -F',' '{print $3}')
    FAIL_COUNT=$(echo "$ROW" | awk -F',' '{print $4}')
    MEDIAN=$(echo "$ROW" | awk -F',' '{print $5}')
    AVG=$(echo "$ROW" | awk -F',' '{print $6}')
    RPS=$(echo "$ROW" | awk -F',' '{print $10}')
    P95=$(echo "$ROW" | awk -F',' '{print $17}')
    P99=$(echo "$ROW" | awk -F',' '{print $19}')

    FAIL_PCT="0"
    if [ "${REQ_COUNT:-0}" != "0" ]; then
        FAIL_PCT=$(awk -v f="$FAIL_COUNT" -v r="$REQ_COUNT" 'BEGIN{printf "%.2f", (f/r)*100}')
    fi

    printf "  reqs=%s fail=%s (%.2f%%) RPS=%s avg=%sms p50=%sms p95=%sms p99=%sms\n" \
        "$REQ_COUNT" "$FAIL_COUNT" "$FAIL_PCT" "$RPS" "$AVG" "$MEDIAN" "$P95" "$P99"

    SUMMARY_ROWS+=("$VU|$REQ_COUNT|$FAIL_PCT%|$RPS|$MEDIAN|$P95|$P99")
    cleanup_test_users
done

echo
{
    echo "| VU | Requests | Fail % | RPS | p50 (ms) | p95 (ms) | p99 (ms) |"
    echo "|----|----------|--------|-----|----------|----------|----------|"
    for row in "${SUMMARY_ROWS[@]}"; do
        IFS='|' read -r vu reqs fails rps p50 p95 p99 <<< "$row"
        printf "| %s | %s | %s | %s | %s | %s | %s |\n" \
            "$vu" "$reqs" "$fails" "$rps" "$p50" "$p95" "$p99"
    done
} | tee "$RESULTS_DIR/summary.md"

echo
echo "Full Locust output: $RESULTS_DIR/run_*.log"
echo "Per-run CSVs:       $RESULTS_DIR/run_*_stats.csv"
echo "Summary:            $RESULTS_DIR/summary.md"
