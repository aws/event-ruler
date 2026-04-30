package software.amazon.event.ruler;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.Assume;
import org.junit.Test;

import static software.amazon.event.ruler.Benchmarks.ANYTHING_BUT_IGNORE_CASE_RULES;
import static software.amazon.event.ruler.Benchmarks.ANYTHING_BUT_PREFIX_RULES;
import static software.amazon.event.ruler.Benchmarks.ANYTHING_BUT_RULES;
import static software.amazon.event.ruler.Benchmarks.ANYTHING_BUT_SUFFIX_RULES;
import static software.amazon.event.ruler.Benchmarks.ANYTHING_BUT_WILDCARD_RULES;
import static software.amazon.event.ruler.Benchmarks.COMPLEX_ARRAYS_RULES;
import static software.amazon.event.ruler.Benchmarks.EQUALS_IGNORE_CASE_RULES;
import static software.amazon.event.ruler.Benchmarks.EXACT_RULES;
import static software.amazon.event.ruler.Benchmarks.NUMERIC_RULES;
import static software.amazon.event.ruler.Benchmarks.PREFIX_EQUALS_IGNORE_CASE_RULES;
import static software.amazon.event.ruler.Benchmarks.PREFIX_RULES;
import static software.amazon.event.ruler.Benchmarks.SUFFIX_EQUALS_IGNORE_CASE_RULES;
import static software.amazon.event.ruler.Benchmarks.SUFFIX_RULES;
import static software.amazon.event.ruler.Benchmarks.WILDCARD_RULES;
import static software.amazon.event.ruler.Benchmarks.readCityLots2;

/**
 * Warmup + averaged perf benchmarks for {@code rulesForJSONEvent} against the
 * {@code citylots2} dataset. Complements {@link Benchmarks#CL2Benchmark},
 * which is a single-shot quick-eyeball benchmark; this one is meant for
 * before/after comparison during CR review.
 *
 * <h2>Why this exists</h2>
 *
 * <p>{@link Benchmarks#CL2Benchmark} runs each rule type once and reports
 * events/sec. On a JVM the first pass pays for JIT compilation and class
 * loading, so single-pass variance between runs of the same code routinely
 * hits 10-20%. That's fine for eyeballing a big win, not fine for deciding
 * whether a subtle change regressed anything by 2%.
 *
 * <p>{@code StableBenchmarks} runs N warmup passes (discarded), then M
 * measured passes per rule type, and reports mean, standard deviation, min,
 * max. With the default 3/5 passes, measured variance is usually under 2% —
 * good enough for regression review.
 *
 * <h2>Running</h2>
 *
 * <p>Gated off by default. Flip it on with {@code -Druler.perf.run=true}:
 *
 * <pre>
 *   # Full run, all 14 rule types, default 3 warmup + 5 measure
 *   mvn test -Dtest=StableBenchmarks -Druler.perf.run=true
 *
 *   # Tighter error bars (slower, ~6 min)
 *   mvn test -Dtest=StableBenchmarks -Druler.perf.run=true \
 *       -Druler.perf.warmup=5 -Druler.perf.measure=10
 *
 *   # Focus on specific rule types (substring match, case-insensitive)
 *   mvn test -Dtest=StableBenchmarks -Druler.perf.run=true \
 *       -Druler.perf.only=wildcard,suffix
 *
 *   # Verbose: include per-pass timings
 *   mvn test -Dtest=StableBenchmarks -Druler.perf.run=true \
 *       -Druler.perf.verbose=true
 *
 *   # Write machine-readable results for later diffing
 *   mvn test -Dtest=StableBenchmarks -Druler.perf.run=true \
 *       -Druler.perf.csv=/tmp/ruler-perf.csv
 * </pre>
 *
 * <h2>Comparing two revisions</h2>
 *
 * <p>The easy path is {@code scripts/perf-compare.sh &lt;before&gt; &lt;after&gt;},
 * which checks out each ref, runs this harness on both, and prints a
 * noise-aware delta table. See the Performance section of the README.
 *
 * <p>Manual path, if you need control over the flow:
 *
 * <pre>
 *   git checkout &lt;before&gt;
 *   mvn clean test -Dtest=StableBenchmarks -Druler.perf.run=true \
 *       -Druler.perf.csv=/tmp/before.csv | tee /tmp/before.log
 *
 *   git checkout &lt;after&gt;
 *   mvn clean test -Dtest=StableBenchmarks -Druler.perf.run=true \
 *       -Druler.perf.csv=/tmp/after.csv | tee /tmp/after.log
 *
 *   # Quick side-by-side of summary lines
 *   grep MEAN /tmp/before.log /tmp/after.log | sort
 *
 *   # Or diff the CSVs
 *   diff /tmp/before.csv /tmp/after.csv
 * </pre>
 *
 * <h2>Output format</h2>
 *
 * <p>Each rule type produces a single-line summary prefixed with {@code MEAN=}:
 *
 * <pre>
 *   [WILDCARD              ] MEAN=122693  STDDEV=492  (0.4%)  MIN=121887  MAX=123221  events/sec
 * </pre>
 *
 * <p>Columns are space-aligned so {@code grep | sort} gives a readable diff
 * across revisions without further formatting.
 *
 * <h2>Scope</h2>
 *
 * <p>Covers the same fourteen rule types as
 * {@link Benchmarks#CL2Benchmark}. Not a replacement for the JMH benchmarks in
 * {@code jmh/} — those are the right tool for publication numbers. This is
 * the right tool for <em>"did my change regress anything?"</em> during CR.
 */
public class StableBenchmarks {

    private static final int DEFAULT_WARMUP_PASSES = 3;
    private static final int DEFAULT_MEASURE_PASSES = 5;

    // Label column width chosen to fit the longest rule type name
    // ("ANYTHING_BUT_IGNORE_CASE") plus a small margin, so the MEAN columns
    // in the output align cleanly.
    private static final int LABEL_COL_WIDTH = 24;

    // Expected match counts per rule bank. Duplicated from Benchmarks.java
    // because the originals are package-private instance fields, not static.
    // If these drift, CL2Benchmark will also fail — they're tied to the
    // citylots2 dataset.
    private static final int[] EXACT_MATCHES = { 1, 101, 35, 655, 1 };
    private static final int[] WILDCARD_MATCHES = { 490, 713, 43, 2540, 1 };
    private static final int[] PREFIX_MATCHES = { 24, 442, 38, 2387, 328 };
    private static final int[] PREFIX_EQUALS_IGNORE_CASE_MATCHES = { 24, 442, 38, 2387, 328 };
    private static final int[] SUFFIX_MATCHES = { 17921, 871, 13, 1963, 682 };
    private static final int[] SUFFIX_EQUALS_IGNORE_CASE_MATCHES = { 17921, 871, 13, 1963, 682 };
    private static final int[] EQUALS_IGNORE_CASE_MATCHES = { 131, 211, 1758, 825, 116386 };
    private static final int[] NUMERIC_MATCHES = { 2, 120, 148948, 64120, 127053 };
    private static final int[] ANYTHING_BUT_MATCHES = { 211158, 210411, 96682, 120, 210615 };
    private static final int[] ANYTHING_BUT_IGNORE_CASE_MATCHES = { 211158, 210411, 96682, 120, 210615 };
    private static final int[] ANYTHING_BUT_PREFIX_MATCHES = { 211158, 210118, 96667, 120, 209091 };
    private static final int[] ANYTHING_BUT_SUFFIX_MATCHES = { 211136, 210411, 94908, 0, 209055 };
    private static final int[] ANYTHING_BUT_WILDCARD_MATCHES = { 212578, 212355, 213025, 210528, 213067 };
    private static final int[] COMPLEX_ARRAYS_MATCHES = { 218, 1, 149446, 64368, 127485 };

    private final List<String> citylots2 = new ArrayList<>();

    /** Results collected across all rule types in this run, for CSV output. */
    private final Map<String, RuleTypeResult> results = new LinkedHashMap<>();

    /** Run every rule type end-to-end, unless filtered via {@code ruler.perf.only}. */
    @Test
    public void runAll() throws Exception {
        Assume.assumeTrue(
                "Skipped: set -Druler.perf.run=true to run the stable benchmarks. "
                        + "See this class's javadoc for usage.",
                Boolean.getBoolean("ruler.perf.run"));

        readCityLots2(citylots2);
        int warmup = getIntProp("ruler.perf.warmup", DEFAULT_WARMUP_PASSES);
        int measure = getIntProp("ruler.perf.measure", DEFAULT_MEASURE_PASSES);
        List<String> only = getOnlyFilter();
        boolean verbose = Boolean.getBoolean("ruler.perf.verbose");

        printHeader(warmup, measure, only, verbose);

        // Rule types grouped by complexity. Order is stable so output diffs
        // between revisions are easy to read.
        maybeRun("EXACT", EXACT_RULES, EXACT_MATCHES, only, warmup, measure, verbose);
        maybeRun("WILDCARD", WILDCARD_RULES, WILDCARD_MATCHES, only, warmup, measure, verbose);
        maybeRun("PREFIX", PREFIX_RULES, PREFIX_MATCHES, only, warmup, measure, verbose);
        maybeRun("PREFIX_EIC",
                PREFIX_EQUALS_IGNORE_CASE_RULES, PREFIX_EQUALS_IGNORE_CASE_MATCHES,
                only, warmup, measure, verbose);
        maybeRun("SUFFIX", SUFFIX_RULES, SUFFIX_MATCHES, only, warmup, measure, verbose);
        maybeRun("SUFFIX_EIC",
                SUFFIX_EQUALS_IGNORE_CASE_RULES, SUFFIX_EQUALS_IGNORE_CASE_MATCHES,
                only, warmup, measure, verbose);
        maybeRun("EQUALS_IGNORE_CASE",
                EQUALS_IGNORE_CASE_RULES, EQUALS_IGNORE_CASE_MATCHES,
                only, warmup, measure, verbose);
        maybeRun("NUMERIC", NUMERIC_RULES, NUMERIC_MATCHES, only, warmup, measure, verbose);
        maybeRun("ANYTHING_BUT",
                ANYTHING_BUT_RULES, ANYTHING_BUT_MATCHES, only, warmup, measure, verbose);
        maybeRun("ANYTHING_BUT_IGNORE_CASE",
                ANYTHING_BUT_IGNORE_CASE_RULES, ANYTHING_BUT_IGNORE_CASE_MATCHES,
                only, warmup, measure, verbose);
        maybeRun("ANYTHING_BUT_PREFIX",
                ANYTHING_BUT_PREFIX_RULES, ANYTHING_BUT_PREFIX_MATCHES,
                only, warmup, measure, verbose);
        maybeRun("ANYTHING_BUT_SUFFIX",
                ANYTHING_BUT_SUFFIX_RULES, ANYTHING_BUT_SUFFIX_MATCHES,
                only, warmup, measure, verbose);
        maybeRun("ANYTHING_BUT_WILDCARD",
                ANYTHING_BUT_WILDCARD_RULES, ANYTHING_BUT_WILDCARD_MATCHES,
                only, warmup, measure, verbose);
        maybeRun("COMPLEX_ARRAYS",
                COMPLEX_ARRAYS_RULES, COMPLEX_ARRAYS_MATCHES, only, warmup, measure, verbose);

        maybeWriteCsv();
    }

    // --- Header / output -----------------------------------------------

    private void printHeader(int warmup, int measure, List<String> only, boolean verbose) {
        System.out.println("========== StableBenchmarks ==========");
        System.out.printf(Locale.ROOT, "  warmup  passes : %d%n", warmup);
        System.out.printf(Locale.ROOT, "  measure passes : %d%n", measure);
        System.out.printf(Locale.ROOT, "  events         : %d%n", citylots2.size());
        System.out.printf(Locale.ROOT, "  verbose        : %s%n", verbose);
        System.out.printf(Locale.ROOT, "  only filter    : %s%n", only == null ? "(all)" : only);
        System.out.println("  --- environment ---");
        System.out.printf(Locale.ROOT, "  jvm            : %s %s%n",
                System.getProperty("java.vm.name"), System.getProperty("java.version"));
        System.out.printf(Locale.ROOT, "  os             : %s %s / %s%n",
                System.getProperty("os.name"), System.getProperty("os.version"),
                System.getProperty("os.arch"));
        System.out.printf(Locale.ROOT, "  cores          : %d%n",
                Runtime.getRuntime().availableProcessors());
        System.out.printf(Locale.ROOT, "  heap max       : %d MB%n",
                Runtime.getRuntime().maxMemory() / (1024 * 1024));
        String csvPath = System.getProperty("ruler.perf.csv");
        if (csvPath != null) {
            System.out.printf(Locale.ROOT, "  csv output     : %s%n", csvPath);
        }
        System.out.println();
    }

    // --- Runner --------------------------------------------------------

    private void maybeRun(String label, String[] rules, int[] expectedMatches,
                          List<String> only, int warmupPasses, int measurePasses,
                          boolean verbose)
            throws Exception {
        if (only != null && !matchesOnlyFilter(label, only)) {
            return;
        }
        runRuleType(label, rules, expectedMatches, warmupPasses, measurePasses, verbose);
    }

    private void runRuleType(String label, String[] rules, int[] expectedMatches,
                             int warmupPasses, int measurePasses, boolean verbose)
            throws Exception {
        List<Double> samples = new ArrayList<>(measurePasses);

        for (int i = 0; i < warmupPasses; i++) {
            double eps = timeOnePass(rules, expectedMatches);
            if (verbose) {
                System.out.printf(Locale.ROOT,
                        "  [%s] warmup %d/%d: %.1f events/sec%n",
                        padLabel(label), i + 1, warmupPasses, eps);
            }
        }

        for (int i = 0; i < measurePasses; i++) {
            double eps = timeOnePass(rules, expectedMatches);
            samples.add(eps);
            if (verbose) {
                System.out.printf(Locale.ROOT,
                        "  [%s] measure %d/%d: %.1f events/sec%n",
                        padLabel(label), i + 1, measurePasses, eps);
            }
        }

        double mean = mean(samples);
        double stddev = stddev(samples, mean);
        double relStddev = 100.0 * stddev / mean;
        double min = Collections.min(samples);
        double max = Collections.max(samples);

        // One-liner summary. Column widths are fixed so that piping through
        // `grep MEAN | sort` lines up nicely, and so before/after logs are
        // visually diffable.
        System.out.printf(Locale.ROOT,
                "  [%s] MEAN=%7.0f  STDDEV=%6.0f  (%4.1f%%)  MIN=%7.0f  MAX=%7.0f  events/sec%n",
                padLabel(label), mean, stddev, relStddev, min, max);

        results.put(label, new RuleTypeResult(label, mean, stddev, min, max, samples));
    }

    /**
     * Build a fresh machine for this rule set and time a single scan of
     * citylots2. Each pass compiles the rules from scratch, so compile cost
     * is included. Returns events/sec for the scan.
     */
    private double timeOnePass(String[] rules, int[] expectedMatches) throws Exception {
        Machine machine = new Machine();
        int[] gotCounts = new int[rules.length];
        for (int i = 0; i < rules.length; i++) {
            machine.addRule("r" + i, rules[i]);
        }

        long before = System.nanoTime();
        for (String event : citylots2) {
            List<String> matches = machine.rulesForJSONEvent(event);
            for (String match : matches) {
                int idx = Integer.parseInt(match.substring(1));
                gotCounts[idx]++;
            }
        }
        long afterNs = System.nanoTime() - before;

        // Sanity: reject the result if counts don't match. A perf number for
        // a match function that doesn't return correct results is useless.
        for (int i = 0; i < rules.length; i++) {
            if (gotCounts[i] != expectedMatches[i]) {
                throw new AssertionError("match count mismatch for rule " + i
                        + ": expected=" + expectedMatches[i] + " got=" + gotCounts[i]);
            }
        }

        return (1_000_000_000.0 * citylots2.size()) / afterNs;
    }

    // --- CSV output ----------------------------------------------------

    private void maybeWriteCsv() throws IOException {
        String csvPath = System.getProperty("ruler.perf.csv");
        if (csvPath == null || csvPath.isEmpty()) {
            return;
        }
        Path out = Paths.get(csvPath);
        if (out.getParent() != null) {
            Files.createDirectories(out.getParent());
        }
        try (PrintWriter w = new PrintWriter(
                Files.newBufferedWriter(out, StandardCharsets.UTF_8))) {
            w.println("rule_type,mean_eps,stddev_eps,rel_stddev_pct,min_eps,max_eps,samples");
            for (RuleTypeResult r : results.values()) {
                w.printf(Locale.ROOT, "%s,%.2f,%.2f,%.4f,%.2f,%.2f,%s%n",
                        r.label, r.mean, r.stddev,
                        100.0 * r.stddev / r.mean,
                        r.min, r.max,
                        formatSamples(r.samples));
            }
        }
        System.out.println();
        System.out.println("Wrote CSV: " + out.toAbsolutePath());
    }

    private static String formatSamples(List<Double> samples) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < samples.size(); i++) {
            if (i > 0) {
                sb.append('|');
            }
            sb.append(String.format(Locale.ROOT, "%.2f", samples.get(i)));
        }
        return sb.toString();
    }

    // --- Helpers -------------------------------------------------------

    private static String padLabel(String label) {
        if (label.length() >= LABEL_COL_WIDTH) {
            return label;
        }
        StringBuilder sb = new StringBuilder(LABEL_COL_WIDTH);
        sb.append(label);
        while (sb.length() < LABEL_COL_WIDTH) {
            sb.append(' ');
        }
        return sb.toString();
    }

    private static double mean(List<Double> xs) {
        double sum = 0;
        for (double x : xs) {
            sum += x;
        }
        return sum / xs.size();
    }

    private static double stddev(List<Double> xs, double mean) {
        if (xs.size() < 2) {
            return 0;
        }
        double sum = 0;
        for (double x : xs) {
            double d = x - mean;
            sum += d * d;
        }
        return Math.sqrt(sum / (xs.size() - 1));
    }

    private static int getIntProp(String name, int defaultValue) {
        String v = System.getProperty(name);
        if (v == null || v.isEmpty()) {
            return defaultValue;
        }
        return Integer.parseInt(v);
    }

    /**
     * Parse {@code -Druler.perf.only=wildcard,suffix} into a list of lowercase
     * substrings. Match is case-insensitive substring — so
     * {@code only=wildcard} matches {@code WILDCARD} and {@code ANYTHING_BUT_WILDCARD}.
     * Returns {@code null} if the property is unset, meaning "run all".
     */
    private static List<String> getOnlyFilter() {
        String v = System.getProperty("ruler.perf.only");
        if (v == null || v.isEmpty()) {
            return null;
        }
        List<String> out = new ArrayList<>();
        for (String part : v.split(",")) {
            String trimmed = part.trim().toLowerCase(Locale.ROOT);
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    private static boolean matchesOnlyFilter(String label, List<String> only) {
        String low = label.toLowerCase(Locale.ROOT);
        for (String needle : only) {
            if (low.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    // --- Result record -------------------------------------------------

    private static final class RuleTypeResult {
        final String label;
        final double mean;
        final double stddev;
        final double min;
        final double max;
        final List<Double> samples;

        RuleTypeResult(String label, double mean, double stddev,
                       double min, double max, List<Double> samples) {
            this.label = label;
            this.mean = mean;
            this.stddev = stddev;
            this.min = min;
            this.max = max;
            this.samples = new ArrayList<>(samples);
        }
    }
}
