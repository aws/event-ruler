package software.amazon.event.ruler;

import org.junit.Ignore;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

/**
 * Benchmarks for {@link MachineComplexityEvaluator}.
 *
 * <p>Most methods here are marked {@code @Ignore} because their wall-clock numbers depend on the
 * hardware they run on, so they are not part of the normal unit-test suite. After any non-trivial
 * change to the complexity-evaluation code, re-run them manually (e.g. with
 * {@code mvn test -Dtest=MachineComplexityEvaluatorBenchmarks}) and compare the printed timings
 * against the previous run to detect performance regressions, in the same spirit as
 * {@link Benchmarks}.</p>
 *
 * <p>The one non-ignored test asserts a generous upper bound on a realistic worst-case machine. It
 * is the CI-runnable half of "detect performance regressions in machine complexity evaluation": a
 * refactor that made evaluation grossly inefficient (for example, by defeating the
 * {@code maxComplexity} cap) would blow past the bound and fail the build instead of going
 * unnoticed. It targets the latency path that latency-sensitive consumers care about, as discussed
 * in <a href="https://github.com/aws/event-ruler/issues/26">issue #26</a>.</p>
 */
public class MachineComplexityEvaluatorBenchmarks {

    // Generous bound: a correct, complexity-capped evaluator finishes a realistic worst-case machine
    // in well under this even on slow CI hardware. A regression that makes evaluation blow up will
    // exceed it.
    private static final long REGRESSION_TIME_BOUND_MS = 5_000;

    private static final int WARMUP_ITERATIONS = 5;
    private static final int MEASURED_ITERATIONS = 50;

    /**
     * Non-ignored regression guard. Runs in CI; fails if complexity evaluation becomes grossly
     * inefficient on a representative nasty input (the "Quamina exploder" pattern set).
     */
    @Test
    public void complexityEvaluationStaysBounded() {
        MachineComplexityEvaluator evaluator = new MachineComplexityEvaluator(100);
        ByteMachine machine = buildQuaminaExploder();
        long start = System.nanoTime();
        int complexity = machine.evaluateComplexity(evaluator);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue("complexity evaluation took too long: " + elapsedMs + " ms (complexity=" + complexity + ")",
                elapsedMs < REGRESSION_TIME_BOUND_MS);
    }

    @Ignore("Manual benchmark: run and compare timings across changes to detect regressions.")
    @Test
    public void benchmarkQuaminaExploder() {
        MachineComplexityEvaluator evaluator = new MachineComplexityEvaluator(100);
        ByteMachine machine = buildQuaminaExploder();
        long avgNs = measure(evaluator, machine);
        System.out.println("MachineComplexityEvaluator benchmark (Quamina exploder, 33 patterns, "
                + "maxComplexity=100): " + TimeUnit.NANOSECONDS.toMicros(avgNs) + " us/eval");
    }

    @Ignore("Manual benchmark: high max-complexity path that latency-sensitive consumers rely on.")
    @Test
    public void benchmarkHighMaxComplexity() {
        // A high cap exercises the path consumers with a really high maxComplexity hit, while the
        // machine's true complexity stays well below the cap so evaluation terminates quickly.
        MachineComplexityEvaluator evaluator = new MachineComplexityEvaluator(10_000_000);
        ByteMachine machine = buildLongWildcardChain(100);
        long avgNs = measure(evaluator, machine);
        System.out.println("MachineComplexityEvaluator benchmark (long single wildcard pattern, "
                + "maxComplexity=10_000_000): " + TimeUnit.NANOSECONDS.toMicros(avgNs) + " us/eval");
    }

    private static long measure(MachineComplexityEvaluator evaluator, ByteMachine machine) {
        // Warm up so the JIT has a chance to compile the evaluation path before we time it.
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            machine.evaluateComplexity(evaluator);
        }
        long total = 0;
        for (int i = 0; i < MEASURED_ITERATIONS; i++) {
            long start = System.nanoTime();
            machine.evaluateComplexity(evaluator);
            total += System.nanoTime() - start;
        }
        return total / MEASURED_ITERATIONS;
    }

    /**
     * Builds the "Quamina exploder" pattern set, a collection of wildcard rules that has historically
     * been problematic for similar automaton libraries and is used as a stress input in
     * {@link MachineComplexityEvaluatorTest#testEvaluateQuaminaExploder()}.
     */
    private static ByteMachine buildQuaminaExploder() {
        ByteMachine machine = new ByteMachine();
        machine.addPattern(Patterns.wildcardMatch("aahed*"));
        machine.addPattern(Patterns.wildcardMatch("aal*ii"));
        machine.addPattern(Patterns.wildcardMatch("aargh*"));
        machine.addPattern(Patterns.wildcardMatch("aarti*"));
        machine.addPattern(Patterns.wildcardMatch("a*baca"));
        machine.addPattern(Patterns.wildcardMatch("*abaci"));
        machine.addPattern(Patterns.wildcardMatch("a*back"));
        machine.addPattern(Patterns.wildcardMatch("ab*acs"));
        machine.addPattern(Patterns.wildcardMatch("abaf*t"));
        machine.addPattern(Patterns.wildcardMatch("*abaka"));
        machine.addPattern(Patterns.wildcardMatch("ab*amp"));
        machine.addPattern(Patterns.wildcardMatch("a*band"));
        machine.addPattern(Patterns.wildcardMatch("*abase"));
        machine.addPattern(Patterns.wildcardMatch("abash*"));
        machine.addPattern(Patterns.wildcardMatch("abas*k"));
        machine.addPattern(Patterns.wildcardMatch("ab*ate"));
        machine.addPattern(Patterns.wildcardMatch("aba*ya"));
        machine.addPattern(Patterns.wildcardMatch("abbas*"));
        machine.addPattern(Patterns.wildcardMatch("abbed*"));
        machine.addPattern(Patterns.wildcardMatch("ab*bes"));
        machine.addPattern(Patterns.wildcardMatch("abbey*"));
        machine.addPattern(Patterns.wildcardMatch("*abbot"));
        machine.addPattern(Patterns.wildcardMatch("ab*cee"));
        machine.addPattern(Patterns.wildcardMatch("abea*m"));
        machine.addPattern(Patterns.wildcardMatch("abe*ar"));
        machine.addPattern(Patterns.wildcardMatch("a*bele"));
        machine.addPattern(Patterns.wildcardMatch("a*bers"));
        machine.addPattern(Patterns.wildcardMatch("abet*s"));
        machine.addPattern(Patterns.wildcardMatch("*abhor"));
        machine.addPattern(Patterns.wildcardMatch("abi*de"));
        machine.addPattern(Patterns.wildcardMatch("a*bies"));
        machine.addPattern(Patterns.wildcardMatch("*abled"));
        return machine;
    }

    /**
     * Builds a single long wildcard pattern ("*a*a*...*a*") with {@code stars} wildcard positions.
     * Exercises the prefix-counting path under a high {@code maxComplexity} cap.
     */
    private static ByteMachine buildLongWildcardChain(int stars) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < stars; i++) {
            builder.append("*a");
        }
        ByteMachine machine = new ByteMachine();
        machine.addPattern(Patterns.wildcardMatch(builder.toString()));
        return machine;
    }
}
