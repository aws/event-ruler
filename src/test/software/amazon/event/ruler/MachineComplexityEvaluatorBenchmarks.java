package software.amazon.event.ruler;

import org.junit.Assume;
import org.junit.Test;

import software.amazon.event.ruler.MachineComplexityEvaluatorCorpus.Entry;
import software.amazon.event.ruler.MachineComplexityEvaluatorCorpus.Tier;
import software.amazon.event.ruler.MachineComplexityEvaluatorCorpus.WalkCountingEvaluator;

import com.sun.management.ThreadMXBean;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static software.amazon.event.ruler.MachineComplexityEvaluatorCorpus.PRODUCTION_CAP;
import static software.amazon.event.ruler.MachineComplexityEvaluatorCorpus.UNCAPPED;

/**
 * Timings of {@link MachineComplexityEvaluator} over the {@link MachineComplexityEvaluatorCorpus}: for every machine,
 * build time, then evaluation time, allocation and reported complexity under a production-style cap and uncapped.
 *
 * <p>Gated off by default, like {@link StableBenchmarks}. Flip it on with {@code -Druler.perf.run=true}:
 *
 * <pre>
 *   # Default: 3 warmup + 10 measured evaluations per row, except the seconds-long (HEAVY) machines' uncapped row,
 *   # which runs once (about 20 s of the run); the minutes-long (EXPENSIVE) machine runs capped only. About half a
 *   # minute in total, and a 2 GB heap suffices.
 *   mvn test -Dtest=MachineComplexityEvaluatorBenchmarks -Druler.perf.run=true
 *
 *   # Custom pass counts (the HEAVY and EXPENSIVE uncapped rows stay single-pass)
 *   mvn test -Dtest=MachineComplexityEvaluatorBenchmarks -Druler.perf.run=true \
 *       -Druler.perf.warmup=5 -Druler.perf.measure=20
 *
 *   # Only machines whose name contains one of the given substrings
 *   mvn test -Dtest=MachineComplexityEvaluatorBenchmarks -Druler.perf.run=true \
 *       -Druler.perf.only=quamina,multi-star
 *
 *   # Uncapped evaluation of the heavy machines: false = none, true = HEAVY tier (default), all = EXPENSIVE tier too
 *   # (the 16-pattern leading-star set: about three minutes and 7 GB of live heap, e.g. -DargLine=-Xmx12g)
 *   mvn test -Dtest=MachineComplexityEvaluatorBenchmarks -Druler.perf.run=true -Druler.perf.heavy=all
 *
 *   # Write a CSV for diffing two revisions
 *   mvn test -Dtest=MachineComplexityEvaluatorBenchmarks -Druler.perf.run=true \
 *       -Druler.perf.csv=/tmp/evaluator-perf.csv
 * </pre>
 *
 * <p>Reading the table: {@code complexity} and {@code walks} must not change between revisions — this class asserts
 * both against the corpus on every row, and {@link MachineComplexityEvaluatorRegressionTest} does the same in every
 * build. {@code median_us} and {@code alloc_kb} are the numbers to compare across revisions on one host. The capped
 * rows are what a consumer enforcing a maximum of 10 pays at rule-creation time; the uncapped rows are the cost of
 * reporting the true number, which for the leading-star sets grows exponentially with the pattern count.
 */
public class MachineComplexityEvaluatorBenchmarks {

    private static final int DEFAULT_WARMUP_PASSES = 3;
    private static final int DEFAULT_MEASURE_PASSES = 10;
    private static final String ROW_FORMAT = "%-36s %-9s %10d %6d %7d %10.1f %11.1f %11.1f %11.1f %10.1f%n";

    /** One row of the report. */
    private static final class Row {
        final String machine;
        final String cap;
        final int complexity;
        final int walks;
        final int passes;
        final double buildMedianUs;
        final double medianUs;
        final double minUs;
        final double maxUs;
        final double allocKb;

        Row(String machine, String cap, int complexity, int walks, int passes, double buildMedianUs, double medianUs,
            double minUs, double maxUs, double allocKb) {
            this.machine = machine;
            this.cap = cap;
            this.complexity = complexity;
            this.walks = walks;
            this.passes = passes;
            this.buildMedianUs = buildMedianUs;
            this.medianUs = medianUs;
            this.minUs = minUs;
            this.maxUs = maxUs;
            this.allocKb = allocKb;
        }

        void print() {
            System.out.printf(Locale.ROOT, ROW_FORMAT, machine, cap, complexity, walks, passes, buildMedianUs, medianUs,
                    minUs, maxUs, allocKb);
        }
    }

    @Test
    public void evaluatorBenchmarks() throws Exception {
        Assume.assumeTrue(
                "Skipped: set -Druler.perf.run=true to run the complexity evaluator benchmarks. "
                        + "See the class javadoc for options.",
                Boolean.getBoolean("ruler.perf.run"));

        int warmupPasses = getIntProp("ruler.perf.warmup", DEFAULT_WARMUP_PASSES, 0);
        int measurePasses = getIntProp("ruler.perf.measure", DEFAULT_MEASURE_PASSES, 1);
        String heavy = System.getProperty("ruler.perf.heavy", "true").trim().toLowerCase(Locale.ROOT);
        Tier uncapUpTo;
        switch (heavy) {
            case "false":
                uncapUpTo = Tier.FAST;
                break;
            case "true":
                uncapUpTo = Tier.HEAVY;
                break;
            case "all":
                uncapUpTo = Tier.EXPENSIVE;
                break;
            default:
                throw new IllegalArgumentException("ruler.perf.heavy must be false, true or all, got: " + heavy);
        }
        List<String> only = onlyFilter();
        Path csv = csvPath();

        System.out.printf(Locale.ROOT, "%nMachineComplexityEvaluator benchmarks: %s %s, %s %s %s, %d cpus, %d MB heap%n",
                System.getProperty("java.vm.name"), System.getProperty("java.version"),
                System.getProperty("os.name"), System.getProperty("os.version"), System.getProperty("os.arch"),
                Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().maxMemory() / (1024 * 1024));
        System.out.printf(Locale.ROOT, "warmup=%d measure=%d heavy=%s only=%s%n%n", warmupPasses, measurePasses, heavy,
                only.isEmpty() ? "(all)" : only);

        List<Entry> selected = new ArrayList<>();
        for (Entry entry : MachineComplexityEvaluatorCorpus.entries()) {
            if (only.isEmpty() || matchesFilter(entry.name, only)) {
                selected.add(entry);
            }
        }
        if (selected.isEmpty()) {
            System.out.println("no corpus entry matches -Druler.perf.only=" + only);
            return;
        }

        // Compile the evaluator's hot paths before the first measured row: the first machine's few capped passes are
        // otherwise far too little work for the JIT, and its numbers straddle interpreted and compiled code. Over every
        // fast entry, whatever the filter selected (about a second).
        for (Entry entry : MachineComplexityEvaluatorCorpus.entries()) {
            if (entry.tier == Tier.FAST) {
                Machine machine = entry.build();
                machine.evaluateComplexity(new WalkCountingEvaluator(PRODUCTION_CAP));
                machine.evaluateComplexity(new WalkCountingEvaluator(UNCAPPED));
            }
        }

        System.out.printf(Locale.ROOT, "%-36s %-9s %10s %6s %7s %10s %11s %11s %11s %10s%n",
                "machine", "cap", "complexity", "walks", "passes", "build_us", "median_us", "min_us", "max_us",
                "alloc_kb");
        List<Row> rows = new ArrayList<>();
        for (Entry entry : selected) {
            double buildMedianUs = measureBuild(entry, warmupPasses, measurePasses);
            rows.add(measureAndCheck(entry, PRODUCTION_CAP, warmupPasses, measurePasses, buildMedianUs));
            if (entry.tier.compareTo(uncapUpTo) <= 0) {
                boolean repeat = entry.tier == Tier.FAST;
                rows.add(measureAndCheck(entry, UNCAPPED, repeat ? warmupPasses : 0, repeat ? measurePasses : 1,
                        buildMedianUs));
            }
        }
        System.out.println();

        if (csv != null) {
            writeCsv(csv, rows);
            System.out.println("CSV written to " + csv);
        }
    }

    /** The {@code ruler.perf.csv} path with its parent directory created, or null when the property is unset. */
    private static Path csvPath() throws IOException {
        String csvPath = System.getProperty("ruler.perf.csv");
        if (csvPath == null || csvPath.trim().isEmpty()) {
            return null;
        }
        Path csv = Paths.get(csvPath.trim());
        if (csv.getParent() != null) {
            Files.createDirectories(csv.getParent());
        }
        return csv;
    }

    /** Comma-separated, case-insensitive substrings from {@code ruler.perf.only}; empty parts are dropped. */
    private static List<String> onlyFilter() {
        List<String> only = new ArrayList<>();
        String onlyProp = System.getProperty("ruler.perf.only");
        if (onlyProp != null) {
            for (String part : onlyProp.split(",")) {
                String needle = part.trim().toLowerCase(Locale.ROOT);
                if (!needle.isEmpty()) {
                    only.add(needle);
                }
            }
        }
        return only;
    }

    private static boolean matchesFilter(String name, List<String> only) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String needle : only) {
            if (lower.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    /** Median wall time of {@code new Machine() + addRule}, over the measured passes. */
    private static double measureBuild(Entry entry, int warmupPasses, int measurePasses) throws Exception {
        for (int i = 0; i < warmupPasses; i++) {
            entry.build();
        }
        double[] us = new double[measurePasses];
        for (int i = 0; i < us.length; i++) {
            long start = System.nanoTime();
            entry.build();
            us[i] = (System.nanoTime() - start) / 1000.0;
        }
        return median(us);
    }

    /**
     * Times {@code measurePasses} evaluations of the entry's machine under the given cap after {@code warmupPasses}
     * unmeasured ones, asserts the reported complexity and walk count against the corpus, and prints the row.
     */
    private static Row measureAndCheck(Entry entry, int maxComplexity, int warmupPasses, int measurePasses,
                                       double buildMedianUs) throws Exception {
        Machine machine = entry.build();
        for (int i = 0; i < warmupPasses; i++) {
            machine.evaluateComplexity(new WalkCountingEvaluator(maxComplexity));
        }
        ThreadMXBean allocation = allocationCounter();
        long threadId = Thread.currentThread().getId();
        double[] us = new double[measurePasses];
        double[] kb = new double[measurePasses];
        int complexity = -1;
        int walks = -1;
        for (int i = 0; i < measurePasses; i++) {
            WalkCountingEvaluator evaluator = new WalkCountingEvaluator(maxComplexity);
            long allocatedBefore = allocation == null ? 0 : allocation.getThreadAllocatedBytes(threadId);
            long start = System.nanoTime();
            complexity = machine.evaluateComplexity(evaluator);
            us[i] = (System.nanoTime() - start) / 1000.0;
            kb[i] = allocation == null ? Double.NaN
                    : (allocation.getThreadAllocatedBytes(threadId) - allocatedBefore) / 1024.0;
            walks = evaluator.getWalks();
        }
        String capLabel = maxComplexity == UNCAPPED ? "uncapped" : String.valueOf(maxComplexity);
        Row row = new Row(entry.name, capLabel, complexity, walks, measurePasses, buildMedianUs, median(us),
                Arrays.stream(us).min().getAsDouble(), Arrays.stream(us).max().getAsDouble(), median(kb));
        row.print();
        // The corpus pins what an uncapped evaluation reports; a capped one reports the lesser of the cap and that.
        assertEquals(entry.name + " complexity under cap " + capLabel,
                Math.min(maxComplexity, entry.expectedComplexity), complexity);
        assertEquals(entry.name + " ByteMachine walks under cap " + capLabel, entry.expectedWalks, walks);
        return row;
    }

    /** The JVM's per-thread allocation counter, or null when this JVM does not support or has disabled it. */
    private static ThreadMXBean allocationCounter() {
        java.lang.management.ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        if (!(threads instanceof ThreadMXBean)) {
            return null;
        }
        ThreadMXBean allocation = (ThreadMXBean) threads;
        return allocation.isThreadAllocatedMemorySupported() && allocation.isThreadAllocatedMemoryEnabled()
                ? allocation : null;
    }

    private static void writeCsv(Path path, List<Row> rows) throws IOException {
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(path, StandardCharsets.UTF_8))) {
            out.println("machine,cap,complexity,walks,passes,build_us,median_us,min_us,max_us,alloc_kb");
            for (Row row : rows) {
                out.printf(Locale.ROOT, "%s,%s,%d,%d,%d,%.1f,%.1f,%.1f,%.1f,%.1f%n", row.machine, row.cap,
                        row.complexity, row.walks, row.passes, row.buildMedianUs, row.medianUs, row.minUs, row.maxUs,
                        row.allocKb);
            }
        }
    }

    private static int getIntProp(String name, int defaultValue, int minimum) {
        String value = System.getProperty(name);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        int parsed = Integer.parseInt(value.trim());
        if (parsed < minimum) {
            throw new IllegalArgumentException(name + " must be at least " + minimum + ", got: " + parsed);
        }
        return parsed;
    }

    private static double median(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int n = sorted.length;
        return n % 2 == 1 ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0;
    }

}
