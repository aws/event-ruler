package software.amazon.event.ruler;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import software.amazon.event.ruler.MachineComplexityEvaluatorCorpus.Entry;
import software.amazon.event.ruler.MachineComplexityEvaluatorCorpus.Tier;
import software.amazon.event.ruler.MachineComplexityEvaluatorCorpus.WalkCountingEvaluator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static software.amazon.event.ruler.MachineComplexityEvaluatorCorpus.PRODUCTION_CAP;
import static software.amazon.event.ruler.MachineComplexityEvaluatorCorpus.UNCAPPED;

/**
 * Regression guard for {@link MachineComplexityEvaluator} over the {@link MachineComplexityEvaluatorCorpus}, run in
 * every build. One test per (entry, cap) pair, labelled with both: every entry under a production-style cap of 11, and
 * every fast entry uncapped as well — the entries whose uncapped walk takes seconds or minutes are evaluated capped
 * only, because the walk stops the moment a position set reaches the cap. Each test asserts:
 *
 * <ul>
 *   <li>Deterministic pins: the complexity the evaluation reports (the lesser of the cap and the entry's true value)
 *   and the number of ByteMachines it walks. A change in a reported value changes the meaning of the metric for every
 *   consumer enforcing a maximum; a walk count above one per ByteMachine means a machine behind a chain of exact-match
 *   value lists is walked once per combination of list values (the production shape's 4 walks becoming 2,203).</li>
 *   <li>A time bound: the evaluation completes within {@link #BOUND_MS}. Measured after warm-up on a 2024 workstation:
 *   0.2-14 ms capped, 2-32 ms uncapped; about 300 ms for the slowest fast entry in a cold JVM, and the uncapped bound
 *   is taken on a second evaluation, after the first has paid the cold-JIT cost. A change that defeats the cap fails
 *   the capped bound of the seconds-and-minutes entries, or their {@link #TIMEOUT_MS} JUnit timeout. The timeout fails
 *   the test but does not stop the evaluation thread, which keeps running because neither the evaluator nor NameState
 *   checks for interruption; a defeated cap therefore also leaves a multi-gigabyte walk running in the surefire fork
 *   behind the red test.</li>
 * </ul>
 *
 * {@link MachineComplexityEvaluatorBenchmarks} prints the timings behind the bounds; run it after any change here.
 */
@RunWith(Parameterized.class)
public class MachineComplexityEvaluatorRegressionTest {

    /** Upper bound for one evaluation. */
    private static final long BOUND_MS = 5_000;

    /** JUnit stops waiting for a test after this long and fails it; the evaluation thread runs on. */
    private static final long TIMEOUT_MS = 120_000;

    @Parameters(name = "{0}, {2}")
    public static Collection<Object[]> entriesAndCaps() {
        List<Object[]> parameters = new ArrayList<>();
        for (Entry entry : MachineComplexityEvaluatorCorpus.entries()) {
            parameters.add(new Object[] {entry, PRODUCTION_CAP, "cap " + PRODUCTION_CAP});
            if (entry.tier == Tier.FAST) {
                parameters.add(new Object[] {entry, UNCAPPED, "uncapped"});
            }
        }
        return parameters;
    }

    private final Entry entry;
    private final int maxComplexity;

    /** {@code capLabel} only labels the test ({@code "cap 11"} or {@code "uncapped"}). */
    public MachineComplexityEvaluatorRegressionTest(Entry entry, int maxComplexity, String capLabel) {
        this.entry = entry;
        this.maxComplexity = maxComplexity;
    }

    @Test(timeout = TIMEOUT_MS)
    public void evaluationReportsThePinnedValueAndWalksWithinBound() throws Exception {
        Machine machine = entry.build();
        WalkCountingEvaluator evaluator = new WalkCountingEvaluator(maxComplexity);
        long start = System.nanoTime();
        int complexity = machine.evaluateComplexity(evaluator);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertEquals(entry + " complexity", Math.min(maxComplexity, entry.expectedComplexity), complexity);
        assertEquals(entry + " ByteMachine walks", entry.expectedWalks, evaluator.getWalks());
        if (maxComplexity == UNCAPPED) {
            // The first evaluation paid the cold-JIT cost of the whole walk; the bound is taken on a second one.
            start = System.nanoTime();
            machine.evaluateComplexity(new MachineComplexityEvaluator(maxComplexity));
            elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        }
        assertTrue(entry + " evaluation took " + elapsedMs + " ms (complexity " + complexity + "), bound " + BOUND_MS
                + " ms", elapsedMs < BOUND_MS);
    }
}
