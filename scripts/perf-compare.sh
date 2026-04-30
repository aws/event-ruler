#!/usr/bin/env bash
#
# Compare StableBenchmarks perf results between two git refs.
#
# Usage:
#   scripts/perf-compare.sh <before-ref> <after-ref> [-- mvn-args...]
#
# Examples:
#   scripts/perf-compare.sh main HEAD
#   scripts/perf-compare.sh v1.9.0 v2.0.0
#   scripts/perf-compare.sh main my-branch -- -Druler.perf.warmup=5 -Druler.perf.measure=10
#   scripts/perf-compare.sh main HEAD -- -Druler.perf.only=wildcard,suffix
#
# What it does:
#   1. Checks out <before-ref>, runs StableBenchmarks, saves CSV.
#   2. Checks out <after-ref>, runs StableBenchmarks, saves CSV.
#   3. Joins the two CSVs by rule type and prints a delta table annotated
#      with stddev-awareness: deltas smaller than the combined stddev are
#      flagged as "noise", larger ones as "regression"/"improvement".
#   4. Restores your original branch.
#
# Requires: bash, git, maven, awk. Tested on macOS and Linux.

set -euo pipefail

print_usage() {
    sed -n '/^# Usage:/,/^# Requires:/p' "$0" | sed 's/^# \{0,1\}//'
}

if [ $# -lt 2 ]; then
    print_usage
    exit 1
fi

BEFORE_REF="$1"
AFTER_REF="$2"
shift 2

# Optional mvn pass-through args after --
MVN_EXTRA_ARGS=()
if [ $# -gt 0 ]; then
    if [ "$1" = "--" ]; then
        shift
        MVN_EXTRA_ARGS=("$@")
    else
        echo "Unexpected argument: $1 (use -- to pass extra mvn args)" >&2
        exit 1
    fi
fi

# Ensure we're in a git repo
if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    echo "Error: not inside a git repo" >&2
    exit 1
fi

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

# Refuse to run with uncommitted changes (we're checking out other refs)
if ! git diff --quiet || ! git diff --cached --quiet; then
    echo "Error: working tree has uncommitted changes. Stash or commit first." >&2
    exit 1
fi

# Save current ref so we can restore
ORIG_REF="$(git rev-parse --abbrev-ref HEAD)"
if [ "$ORIG_REF" = "HEAD" ]; then
    # detached — use commit sha
    ORIG_REF="$(git rev-parse HEAD)"
fi

OUT_DIR="${OUT_DIR:-/tmp/ruler-perf-compare-$$}"
mkdir -p "$OUT_DIR"
BEFORE_CSV="$OUT_DIR/before.csv"
AFTER_CSV="$OUT_DIR/after.csv"
BEFORE_LOG="$OUT_DIR/before.log"
AFTER_LOG="$OUT_DIR/after.log"

cleanup() {
    echo ""
    echo "Restoring original ref: $ORIG_REF"
    git checkout --quiet "$ORIG_REF" 2>/dev/null || {
        echo "Warning: could not auto-restore to $ORIG_REF — you may need to check out manually" >&2
    }
}
trap cleanup EXIT

run_benchmark() {
    local ref="$1"
    local csv="$2"
    local log="$3"

    echo ""
    echo "=========================================="
    echo "  Checking out $ref"
    echo "=========================================="
    git checkout --quiet "$ref"
    echo "  HEAD: $(git rev-parse --short HEAD) ($(git log -1 --pretty=%s))"

    if [ ! -f src/test/software/amazon/event/ruler/StableBenchmarks.java ]; then
        echo ""
        echo "Error: ref '$ref' does not contain StableBenchmarks.java." >&2
        echo "Both refs must include the perf harness. If you need to compare" >&2
        echo "against an older ref, cherry-pick the StableBenchmarks commit" >&2
        echo "onto a temporary branch first, e.g.:" >&2
        echo "" >&2
        echo "    git checkout $ref" >&2
        echo "    git checkout -b ${ref}-with-perf" >&2
        echo "    git cherry-pick <stable-benchmarks-commit>" >&2
        echo "    scripts/perf-compare.sh ${ref}-with-perf my-branch" >&2
        exit 1
    fi

    echo ""
    echo "  Running StableBenchmarks (output -> $log)..."
    mvn clean test \
        -Dtest=StableBenchmarks \
        -Druler.perf.run=true \
        -Druler.perf.csv="$csv" \
        "${MVN_EXTRA_ARGS[@]}" \
        > "$log" 2>&1 || {
        echo ""
        echo "Benchmark run failed for $ref. Log tail:"
        tail -30 "$log"
        exit 1
    }

    if [ ! -f "$csv" ]; then
        echo ""
        echo "Error: CSV was not written. Check $log for details." >&2
        exit 1
    fi

    echo "  CSV written: $csv"
}

run_benchmark "$BEFORE_REF" "$BEFORE_CSV" "$BEFORE_LOG"
run_benchmark "$AFTER_REF" "$AFTER_CSV" "$AFTER_LOG"

# --- Join and report ---
echo ""
echo "=========================================="
echo "  Comparison: $BEFORE_REF -> $AFTER_REF"
echo "=========================================="

# Header
printf '%-26s  %12s  %12s  %10s  %s\n' \
    "rule_type" "before_eps" "after_eps" "delta_pct" "verdict"
printf '%-26s  %12s  %12s  %10s  %s\n' \
    "---------" "----------" "---------" "---------" "-------"

# awk does the heavy lifting: reads both CSVs, joins on rule_type, then
# compares means with a heuristic noise band. See the inline noise-band
# comment below for the math.
awk -F, '
    FNR == 1 { next }  # skip headers
    NR == FNR {
        # first file (before)
        before_mean[$1] = $2
        before_stddev_pct[$1] = $4
        next
    }
    {
        # second file (after)
        rt = $1
        after_mean_v = $2
        after_stddev_pct_v = $4

        if (!(rt in before_mean)) {
            printf "%-26s  %12s  %12.0f  %10s  %s\n", rt, "-", after_mean_v, "-", "(new)"
            next
        }

        before_mean_v = before_mean[rt]
        delta = (after_mean_v - before_mean_v) / before_mean_v * 100.0

        # Noise band. This is a heuristic, not a proper significance test.
        # We use 4x the pooled stddev (root-mean-square of the two relative
        # stddevs) because with few samples (default 5), the observed stddev
        # systematically understates true run-to-run variance on a shared
        # dev box. For tighter confidence bump iteration counts up.
        combined = sqrt( (before_stddev_pct[rt] * before_stddev_pct[rt] \
                          + after_stddev_pct_v * after_stddev_pct_v) / 2.0 )
        noise = 4 * combined

        if (delta > noise) {
            verdict = sprintf("improvement (noise ±%.1f%%)", noise)
        } else if (delta < -noise) {
            verdict = sprintf("REGRESSION (noise ±%.1f%%)", noise)
        } else {
            verdict = sprintf("noise (±%.1f%%)", noise)
        }

        sign = ""
        if (delta > 0) sign = "+"
        printf "%-26s  %12.0f  %12.0f  %9s%%  %s\n", \
            rt, before_mean_v, after_mean_v, sprintf("%s%.1f", sign, delta), verdict
    }
' "$BEFORE_CSV" "$AFTER_CSV"

echo ""
echo "  Raw CSVs: $BEFORE_CSV, $AFTER_CSV"
echo "  Full logs: $BEFORE_LOG, $AFTER_LOG"
echo ""
