package software.amazon.event.ruler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Machines whose complexity evaluation has a known cost, shared by {@link MachineComplexityEvaluatorRegressionTest}
 * (deterministic pins plus generous time bounds, runs in every build) and {@link MachineComplexityEvaluatorBenchmarks}
 * (timings, opt-in).
 *
 * <p>Every machine is built through the public {@code Machine.addRule} API rather than a bare {@code ByteMachine}, so an
 * evaluation walks the NameState graph too — the recursion into next NameStates across exact-match value lists, and the
 * absent-key ({@code {"exists": false}}) edges — which a benchmark over a single ByteMachine cannot see.
 */
final class MachineComplexityEvaluatorCorpus {

    /**
     * {@code maxComplexity} of a consumer enforcing a per-rule maximum of 10 (README, complexity strategy #2): 10 + 1,
     * because the README's snippet rejects on {@code complexity > max}, so the evaluator must be able to report max + 1.
     */
    static final int PRODUCTION_CAP = 11;

    /** A cap no machine here reaches: the evaluator reports the machine's true complexity. */
    static final int UNCAPPED = 1_000_000;

    /** The four anything-but wildcard strings of {@link MachineComplexityEvaluatorWalkCountTest}, all leading with a star. */
    private static final String[] LEADING_STAR_STRINGS = {
        "*kiwi*", "*BASE_QuxB*", "*Delta*", "*a/example-lake-abc98765432*",
    };

    /** The wildcard set that "explodes" a naive automaton; also in {@code MachineComplexityEvaluatorTest}. */
    private static final String[] QUAMINA_EXPLODER = {
        "aahed*", "aal*ii", "aargh*", "aarti*", "a*baca", "*abaci", "a*back", "ab*acs", "abaf*t", "*abaka", "ab*amp",
        "a*band", "*abase", "abash*", "abas*k", "ab*ate", "aba*ya", "abbas*", "abbed*", "ab*bes", "abbey*", "*abbot",
        "ab*cee", "abea*m", "abe*ar", "a*bele", "a*bers", "abet*s", "*abhor", "abi*de", "a*bies", "*abled",
    };

    private MachineComplexityEvaluatorCorpus() {
    }

    /**
     * How expensive an uncapped evaluation of an entry is, which decides where it runs. Declared in ascending cost
     * order; the benchmark compares on it.
     */
    enum Tier {
        /** Milliseconds: the regression test evaluates it uncapped and pins the result; the benchmark repeats it. */
        FAST,
        /**
         * Seconds and gigabytes of transient allocation: the regression test evaluates it only under
         * {@link #PRODUCTION_CAP}; the benchmark evaluates it uncapped once ({@code -Druler.perf.heavy=true}, the
         * default).
         */
        HEAVY,
        /**
         * Minutes, with several gigabytes of live heap: the regression test evaluates it only under
         * {@link #PRODUCTION_CAP}, as the guard that the cap bounds the walk; the benchmark evaluates it uncapped once
         * only with {@code -Druler.perf.heavy=all}.
         */
        EXPENSIVE
    }

    /**
     * One machine of the corpus.
     */
    static final class Entry {
        /** Short name, used in test names, benchmark output and {@code -Druler.perf.only}. */
        final String name;
        /** The rule JSON the machine is built from. */
        private final String rule;
        /** The complexity an uncapped evaluation reports. */
        final int expectedComplexity;
        /**
         * How many ByteMachines an evaluation walks: one per key holding value patterns on every NameState the walk
         * reaches, exact-match keys included; an absent-key pattern adds no ByteMachine. The count is the same capped
         * and uncapped for every entry here: a capped walk stops recursing into the NameStates behind a ByteMachine
         * that reaches the cap, and no entry puts a cap-reaching key ahead of another key. An entry that does needs a
         * capped and an uncapped count.
         */
        final int expectedWalks;
        /** Cost class of the uncapped evaluation. */
        final Tier tier;

        private Entry(String name, String rule, int expectedComplexity, int expectedWalks, Tier tier) {
            this.name = name;
            this.rule = rule;
            this.expectedComplexity = expectedComplexity;
            this.expectedWalks = expectedWalks;
            this.tier = tier;
        }

        Machine build() throws Exception {
            Machine machine = new Machine();
            machine.addRule(name, rule);
            return machine;
        }

        /** The name: JUnit's parameterized runner uses it to label each entry's tests. */
        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Counts how many ByteMachine walks an evaluation starts: {@code ByteMachine.evaluateComplexity} calls
     * {@code evaluate(ByteState)} exactly once, so the count is the number of ByteMachine evaluations.
     */
    static final class WalkCountingEvaluator extends MachineComplexityEvaluator {
        private int walks = 0;

        WalkCountingEvaluator(int maxComplexity) {
            super(maxComplexity);
        }

        @Override
        int evaluate(ByteState state) {
            walks++;
            return super.evaluate(state);
        }

        int getWalks() {
            return walks;
        }
    }

    /**
     * The corpus, in evaluation-cost order. Expected values are the numbers the evaluator reports at the commit that
     * introduced this class; a change in any of them is a semantic change to the metric and needs a deliberate decision.
     */
    static List<Entry> entries() {
        List<Entry> entries = new ArrayList<>();
        // 32 wildcards on one key with heavy prefix sharing: the historical stress input.
        entries.add(new Entry("quamina-exploder", oneKey("key", wildcards(QUAMINA_EXPLODER)), 45, 1, Tier.FAST));
        // Four leading-star exclusions, as one anything-but set and as four plain wildcards: the same strings score
        // differently because an anything-but set is one pattern object and plain wildcards are four.
        entries.add(new Entry("leading-star-anything-but-set", oneKey("key", anythingButWildcards(LEADING_STAR_STRINGS)),
                7, 1, Tier.FAST));
        entries.add(new Entry("leading-star-plain-wildcards", oneKey("key", wildcards(LEADING_STAR_STRINGS)), 12, 1,
                Tier.FAST));
        // The production shape: exact-match lists of 2 x 50 x 21 values on keys that sort ahead of the anything-but set.
        // The evaluator walks each key's machine once (4 walks in all), not once per combination of list values
        // (2,100 walks of the set's machine).
        entries.add(new Entry("value-lists-then-anything-but", valueListsThenAnythingBut(), 7, 4, Tier.FAST));
        // One wildcard "*a*a*...*a*" - n repeats of "*a" then "*", so n + 1 stars - whose repeated character keeps
        // every position live on a run of that character: complexity 2n + 1, the README's warning about repeating
        // character sequences following a wildcard character.
        entries.add(new Entry("multi-star-50", oneKey("zzz", wildcards(multiStar(50))), 101, 1, Tier.FAST));
        entries.add(new Entry("multi-star-100", oneKey("zzz", wildcards(multiStar(100))), 201, 1, Tier.FAST));
        // The same 100-repeat machine behind an absent-key pattern whose key sorts before it: the evaluator follows the
        // NameMatcher edge and reports the open machine's 201.
        entries.add(new Entry("multi-star-100-behind-absent-key", behindAbsentKey("zzz", wildcards(multiStar(100))),
                201, 1, Tier.FAST));
        // m patterns "*<token>*" sharing the leading star: complexity 3m. Uncapped evaluation is exponential in m
        // because the walk visits every reachable subset of the m trailing-star states (about 30 ms at 8 after
        // warm-up, 6 s at 12, about three minutes and 7 GB of live heap at 16). Capped at 11, the 12- and 16-pattern
        // sets return at the walk's first step, since the shared leading star alone reaches m >= 11 patterns.
        entries.add(new Entry("leading-star-set-4", oneKey("zzz", wildcards(leadingStarSet(4))), 12, 1, Tier.FAST));
        entries.add(new Entry("leading-star-set-8", oneKey("zzz", wildcards(leadingStarSet(8))), 24, 1, Tier.FAST));
        entries.add(new Entry("leading-star-set-12", oneKey("zzz", wildcards(leadingStarSet(12))), 36, 1, Tier.HEAVY));
        entries.add(new Entry("leading-star-set-16", oneKey("zzz", wildcards(leadingStarSet(16))), 48, 1,
                Tier.EXPENSIVE));
        // A 4,045-character multi-star wildcard (2,022 repeats) behind an absent key - a rule at the scale of
        // EventBridge's 4,096-character event-pattern limit, with its whole cost behind a NameMatcher edge. Complexity
        // 4,045; uncapped evaluation about 15 s with about 6 GB of transient allocation.
        entries.add(new Entry("multi-star-2022-behind-absent-key", behindAbsentKey("zzz", wildcards(multiStar(2022))),
                4045, 1, Tier.HEAVY));
        return Collections.unmodifiableList(entries);
    }

    // ---- rule builders -------------------------------------------------------------------------------------------

    /** {@code {"<key>": <patterns>}} */
    private static String oneKey(String key, String patterns) {
        return "{\"" + key + "\": " + patterns + "}";
    }

    /** {@code {"aaa": [{"exists": false}], "<key>": <patterns>}} — "aaa" sorts before every key used here. */
    private static String behindAbsentKey(String key, String patterns) {
        return "{\"aaa\": [{\"exists\": false}], \"" + key + "\": " + patterns + "}";
    }

    /** A JSON array of {@code {"wildcard": "<s>"}} patterns. */
    private static String wildcards(String... values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            sb.append(i > 0 ? ", " : "").append("{\"wildcard\": \"").append(values[i]).append("\"}");
        }
        return sb.append(']').toString();
    }

    /** A JSON array holding one {@code {"anything-but": {"wildcard": [...]}}} pattern over all the values. */
    private static String anythingButWildcards(String... values) {
        StringBuilder sb = new StringBuilder("[{\"anything-but\": {\"wildcard\": [");
        for (int i = 0; i < values.length; i++) {
            sb.append(i > 0 ? ", " : "").append('"').append(values[i]).append('"');
        }
        return sb.append("]}}]").toString();
    }

    /** A JSON array of quoted exact-match values. */
    private static String exactValues(List<String> values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            sb.append(i > 0 ? ", " : "").append('"').append(values.get(i)).append('"');
        }
        return sb.append(']').toString();
    }

    /** {@code "*a"} repeated the given number of times, then {@code "*"}: n + 1 stars, 2n + 1 characters. */
    private static String[] multiStar(int repeats) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < repeats; i++) {
            sb.append("*a");
        }
        return new String[] {sb.append('*').toString()};
    }

    /** {@code "*<token>*"} for the given number of distinct two-letter tokens. */
    private static String[] leadingStarSet(int patterns) {
        String[] values = new String[patterns];
        for (int i = 0; i < patterns; i++) {
            values[i] = "*" + (char) ('a' + i % 26) + (char) ('a' + i / 26) + "*";
        }
        return values;
    }

    /**
     * Two error codes x 50 event names (two shared-prefix families) x 21 event sources (one shared-suffix family) as
     * exact-match lists on keys sorting ahead of the anything-but set.
     */
    private static String valueListsThenAnythingBut() {
        List<String> eventNames = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            eventNames.add("MakeThing" + i);
        }
        for (int i = 0; i < 25; i++) {
            eventNames.add("DropThing" + i);
        }
        List<String> eventSources = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            eventSources.add(String.format("svc%02d.example.com", i));
        }
        return "{\"aaa\": " + exactValues(Arrays.asList("ErrCodeOne", "ErrCodeTwo"))
                + ", \"bbb\": " + exactValues(eventNames)
                + ", \"ccc\": " + exactValues(eventSources)
                + ", \"zzz\": " + anythingButWildcards(LEADING_STAR_STRINGS) + "}";
    }
}
