package software.amazon.event.ruler;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * Property-style verification that recursing into next NameStates once per distinct NameState returns exactly the
 * complexity that recursing once per distinct match returns: for a corpus of machines spanning the pattern types, key
 * shapes, and rule combinations Ruler supports, the two evaluators must agree on every machine.
 *
 * <p>The reference arm re-implements per-match recursion on top of the same ByteMachine walk, so the two arms differ
 * only in their recursion targets. They agree because a recursion's result depends only on the NameState it starts
 * from and a maximum is insensitive to duplicates; this suite checks that argument against the compiled machines
 * themselves.
 */
public class MachineComplexityEvaluatorEquivalenceTest {

    /**
     * Recurses once per distinct match (once per exact-match list value), which walks the machines behind a chain of
     * value lists once per combination of values. Slow, so the corpus keeps its value lists short.
     */
    private static class PerMatchRecursionEvaluator extends MachineComplexityEvaluator {
        PerMatchRecursionEvaluator(int maxComplexity) {
            super(maxComplexity);
        }

        @Override
        int evaluateNextNameStates(Collection<Set<ByteMatch>> matchesAccessibleFromEachTransition) {
            Set<ByteMatch> uniqueMatches = new HashSet<>();
            for (Set<ByteMatch> matches : matchesAccessibleFromEachTransition) {
                uniqueMatches.addAll(matches);
            }
            int maxSizeFromNextNameStates = 0;
            for (ByteMatch match : uniqueMatches) {
                NameState nextNameState = match.getNextNameState();
                if (nextNameState != null) {
                    maxSizeFromNextNameStates = Math.max(maxSizeFromNextNameStates,
                            nextNameState.evaluateComplexity(this));
                }
            }
            return maxSizeFromNextNameStates;
        }
    }

    private static final int[] MAX_COMPLEXITIES = { 1, 3, 100, Integer.MAX_VALUE };

    /**
     * Value-pattern shapes, each expressed as the pattern array for one key: every MatchType Ruler's JSON syntax can
     * produce appears at least once, plus the syntax variants that compile onto the same MatchType differently
     * (numeric, list-valued and single-valued anything-but) and a few mixed arrays.
     */
    private static final Map<String, String> PATTERN_SETS = buildPatternSets();

    private static Map<String, String> buildPatternSets() {
        Map<String, String> sets = new LinkedHashMap<>();
        sets.put("exactSingle", "[\"pv1\"]");
        sets.put("exactList", "[\"pv1\", \"pv2\", \"pv3\"]");
        sets.put("exactDuplicates", "[\"dup\", \"dup\", \"dup2\"]");
        sets.put("prefix", "[{\"prefix\": \"pre\"}]");
        sets.put("prefixEqualsIgnoreCase", "[{\"prefix\": {\"equals-ignore-case\": \"PrE\"}}]");
        sets.put("suffix", "[{\"suffix\": \"fix\"}]");
        sets.put("suffixEqualsIgnoreCase", "[{\"suffix\": {\"equals-ignore-case\": \"FiX\"}}]");
        sets.put("equalsIgnoreCase", "[{\"equals-ignore-case\": \"MiXeD\"}]");
        sets.put("wildcardLeading", "[{\"wildcard\": \"*ab\"}]");
        sets.put("wildcardMiddle", "[{\"wildcard\": \"a*b\"}]");
        sets.put("wildcardTrailing", "[{\"wildcard\": \"ab*\"}]");
        sets.put("wildcardMulti", "[{\"wildcard\": \"*a*a*\"}]");
        sets.put("wildcardRepeatedTail", "[{\"wildcard\": \"F*eatureFeature\"}]");
        sets.put("anythingButExactList", "[{\"anything-but\": [\"x1\", \"x2\"]}]");
        sets.put("anythingButSingleString", "[{\"anything-but\": \"x1\"}]");
        sets.put("anythingButNumericList", "[{\"anything-but\": [100, 200]}]");
        sets.put("anythingButSingleNumber", "[{\"anything-but\": 123}]");
        sets.put("anythingButWildcardSingle", "[{\"anything-but\": {\"wildcard\": \"*ab*\"}}]");
        sets.put("anythingButWildcardSet", "[{\"anything-but\": {\"wildcard\": [\"*ab*\", \"*cd\", \"e*f\"]}}]");
        sets.put("anythingButPrefix", "[{\"anything-but\": {\"prefix\": \"pre\"}}]");
        sets.put("anythingButSuffix", "[{\"anything-but\": {\"suffix\": \"fix\"}}]");
        sets.put("anythingButEqualsIgnoreCase", "[{\"anything-but\": {\"equals-ignore-case\": [\"dad0\", \"dad1\"]}}]");
        sets.put("numericRange", "[{\"numeric\": [\">\", 0, \"<=\", 100]}]");
        sets.put("numericEquals", "[{\"numeric\": [\"=\", 3.5]}]");
        sets.put("existsTrue", "[{\"exists\": true}]");
        sets.put("existsFalse", "[{\"exists\": false}]");
        sets.put("cidr", "[{\"cidr\": \"10.0.0.0/24\"}]");
        sets.put("mixedExactPrefixWildcard", "[\"ev1\", {\"prefix\": \"px\"}, {\"wildcard\": \"*w\"}]");
        sets.put("mixedAnythingButWildcardExact", "[{\"anything-but\": {\"wildcard\": \"*q*\"}}, \"plain\"]");
        return Collections.unmodifiableMap(sets);
    }

    /**
     * The subset of pattern sets that interact most with complexity evaluation, used for pairwise combinations.
     */
    private static final List<String> PAIRWISE_SET_NAMES = Collections.unmodifiableList(Arrays.asList(
            "exactList", "wildcardLeading", "wildcardMulti", "anythingButWildcardSet", "prefix",
            "mixedExactPrefixWildcard", "existsFalse", "numericRange"));

    @Test
    public void testEverySinglePatternSetEvaluatesEquivalently() throws Exception {
        for (Map.Entry<String, String> patternSet : PATTERN_SETS.entrySet()) {
            String rule = "{\"kk\": " + patternSet.getValue() + "}";
            assertEquivalentInAllConfigurations("single pattern set " + patternSet.getKey(),
                    Collections.singletonList(rule));
        }
    }

    @Test
    public void testEveryOrderedPatternSetPairEvaluatesEquivalently() throws Exception {
        for (String firstSetName : PAIRWISE_SET_NAMES) {
            for (String secondSetName : PAIRWISE_SET_NAMES) {
                String rule = "{" +
                        "\"aa\": " + PATTERN_SETS.get(firstSetName) + "," +
                        "\"bb\": " + PATTERN_SETS.get(secondSetName) +
                        "}";
                assertEquivalentInAllConfigurations(
                        "pattern set pair (" + firstSetName + ", " + secondSetName + ")",
                        Collections.singletonList(rule));
            }
        }
    }

    @Test
    public void testKeyChainShapesEvaluateEquivalently() throws Exception {
        List<String> chainRules = Arrays.asList(
                // Exact-match lists ahead of a wildcard-bearing key: the reported pathological shape.
                "{\"aaa\": [\"a1\", \"a2\", \"a3\"], \"bbb\": [\"b1\", \"b2\", \"b3\"]," +
                        " \"zzz\": [{\"wildcard\": \"*z*\"}]}",
                // Wildcard-bearing key first, exact-match lists after it.
                "{\"aaa\": [{\"wildcard\": \"*a*\"}], \"mmm\": [\"m1\", \"m2\", \"m3\"]," +
                        " \"zzz\": [\"z1\", \"z2\", \"z3\"]}",
                // Wildcard-bearing key between exact-match lists.
                "{\"aaa\": [\"a1\", \"a2\"], \"mmm\": [{\"wildcard\": \"*m*\"}], \"zzz\": [\"z1\", \"z2\"]}",
                // Every key carries a wildcard.
                "{\"aaa\": [{\"wildcard\": \"*a\"}], \"bbb\": [{\"wildcard\": \"b*b\"}]," +
                        " \"ccc\": [{\"wildcard\": \"*c*\"}]}",
                // Two wildcard-bearing keys separated by exact-match lists.
                "{\"aaa\": [\"a1\", \"a2\"], \"bbb\": [{\"wildcard\": \"*b\"}], \"ccc\": [\"c1\", \"c2\"]," +
                        " \"ddd\": [{\"anything-but\": {\"wildcard\": \"*d*\"}}]}",
                // Small-scale production shape: lists then an anything-but-wildcard set of leading-star patterns.
                "{\"aaa\": [\"e1\", \"e2\"], \"bbb\": [\"n1\", \"n2\", \"n3\"]," +
                        " \"ccc\": [\"s1\", \"s2\", \"s3\", \"s4\"]," +
                        " \"zzz\": [{\"anything-but\": {\"wildcard\":" +
                        " [\"*kiwi*\", \"*BASE_QuxB*\", \"*Delta*\", \"*a/example-lake-abc98765432*\"]}}]}",
                // Deep chain of exact-match lists.
                "{\"aaa\": [\"a1\", \"a2\"], \"bbb\": [\"b1\", \"b2\"], \"ccc\": [\"c1\", \"c2\"]," +
                        " \"ddd\": [\"d1\", \"d2\"], \"eee\": [\"e1\", \"e2\"]," +
                        " \"zzz\": [{\"wildcard\": \"*z\"}]}");
        for (String rule : chainRules) {
            assertEquivalentInAllConfigurations("key chain " + rule, Collections.singletonList(rule));
        }
    }

    @Test
    public void testNestedKeysEvaluateEquivalently() throws Exception {
        List<String> nestedRules = Arrays.asList(
                "{\"aaa\": {\"inner\": [\"a1\", \"a2\"]}, \"zzz\": [{\"wildcard\": \"*z\"}]}",
                "{\"detail\": {\"state\": [{\"anything-but\": {\"wildcard\": \"*/bin/*.jar\"}}]}}",
                "{\"outer\": {\"middle\": {\"inner\": [{\"wildcard\": \"*i*\"}]}}," +
                        " \"other\": [\"o1\", \"o2\"]}");
        for (String rule : nestedRules) {
            assertEquivalentInAllConfigurations("nested keys " + rule, Collections.singletonList(rule));
        }
    }

    @Test
    public void testOrSubRulesEvaluateEquivalently() throws Exception {
        List<String> orRules = Arrays.asList(
                "{\"$or\": [{\"bar\": [\"1\"]}, {\"bar\": [\"2\"]}], \"foo\": [{\"wildcard\": \"*f\"}]}",
                "{\"$or\": [{\"bar\": [{\"wildcard\": \"*x\"}]}, {\"baz\": [{\"wildcard\": \"y*\"}]}]," +
                        " \"foo\": [\"fv\"]}",
                "{\"$or\": [{\"bar\": [\"b1\", \"b2\"]}, {\"baz\": [\"z1\", \"z2\"]}]," +
                        " \"foo\": [{\"anything-but\": {\"wildcard\": [\"*f*\", \"g*g\"]}}]}");
        for (String rule : orRules) {
            assertEquivalentInAllConfigurations("$or rule " + rule, Collections.singletonList(rule));
        }
    }

    @Test
    public void testMultipleRulesPerMachineEvaluateEquivalently() throws Exception {
        List<List<String>> ruleSets = new ArrayList<>();
        // Shared first-key chain diverging afterwards, wildcards on the diverging keys.
        ruleSets.add(Arrays.asList(
                "{\"aaa\": [\"a1\", \"a2\"], \"zzz\": [{\"wildcard\": \"*x*\"}]}",
                "{\"aaa\": [\"a1\", \"a3\"], \"zzz\": [{\"wildcard\": \"*y\"}]}"));
        // Rules of different key sets sharing one key.
        ruleSets.add(Arrays.asList(
                "{\"aaa\": [\"a1\"], \"bbb\": [\"b1\"]}",
                "{\"aaa\": [\"a1\"], \"ccc\": [{\"wildcard\": \"*c\"}]}"));
        // Wildcard-heavy rules on disjoint keys.
        ruleSets.add(Arrays.asList(
                "{\"aaa\": [{\"wildcard\": \"a*aaa\"}]}",
                "{\"bbb\": [{\"wildcard\": \"aa*aa\"}]}",
                "{\"ccc\": [{\"anything-but\": {\"wildcard\": \"*c*c*\"}}]}"));
        for (List<String> ruleSet : ruleSets) {
            assertEquivalentInAllConfigurations("multi-rule machine " + ruleSet, ruleSet);
        }
    }

    @Test
    public void testSameRuleNameAcrossShapesEvaluatesEquivalently() throws Exception {
        // Adding several shapes under one rule name exercises sub-rule bookkeeping on shared NameStates.
        Machine machine = new Machine();
        machine.addRule("rule", "{\"aaa\": [\"a1\", \"a2\"], \"zzz\": [{\"wildcard\": \"*z\"}]}");
        machine.addRule("rule", "{\"aaa\": [\"a1\"], \"yyy\": [{\"wildcard\": \"*y*\"}]}");
        for (int maxComplexity : MAX_COMPLEXITIES) {
            assertMachineEvaluatesEquivalently("same rule name across shapes", machine, maxComplexity);
        }
    }

    /**
     * Seeded (so deterministic in CI) randomized corpus across pattern sets, key counts, nesting, $or use,
     * machine configurations, rule counts, and maxComplexity values. On failure the message carries the full
     * generated rules, so any divergence is directly reproducible.
     */
    @Test
    public void testRandomizedRuleCorpusEvaluatesEquivalently() throws Exception {
        Random random = new Random(20260831L);
        String[] keyPool = { "aaa", "bbb", "ccc", "ddd", "eee" };
        List<String> patternSetValues = new ArrayList<>(PATTERN_SETS.values());
        int[] maxComplexityPool = { 1, 2, 3, 7, 50, Integer.MAX_VALUE };

        for (int i = 0; i < 250; i++) {
            int ruleCount = 1 + random.nextInt(2);
            List<String> rules = new ArrayList<>();
            for (int j = 0; j < ruleCount; j++) {
                rules.add(generateRandomRule(random, keyPool, patternSetValues));
            }
            boolean additionalNameStateReuse = random.nextBoolean();
            int maxComplexity = maxComplexityPool[random.nextInt(maxComplexityPool.length)];
            assertEquivalent("random corpus machine " + i + " (additionalNameStateReuse="
                            + additionalNameStateReuse + ", maxComplexity=" + maxComplexity + ") rules=" + rules,
                    rules, additionalNameStateReuse, maxComplexity);
        }
    }

    private static String generateRandomRule(Random random, String[] keyPool, List<String> patternSetValues) {
        int keyCount = 1 + random.nextInt(4);
        List<String> keys = new ArrayList<>(Arrays.asList(keyPool));
        Collections.shuffle(keys, random);
        keys = keys.subList(0, keyCount);

        List<String> members = new ArrayList<>();
        int keyIndex = 0;

        // A quarter of multi-key rules put their first two keys into single-key $or branches.
        if (keyCount >= 2 && random.nextInt(4) == 0) {
            String firstBranch = "{\"" + keys.get(0) + "\": "
                    + patternSetValues.get(random.nextInt(patternSetValues.size())) + "}";
            String secondBranch = "{\"" + keys.get(1) + "\": "
                    + patternSetValues.get(random.nextInt(patternSetValues.size())) + "}";
            members.add("\"$or\": [" + firstBranch + ", " + secondBranch + "]");
            keyIndex = 2;
        }

        for (; keyIndex < keyCount; keyIndex++) {
            String patternSet = patternSetValues.get(random.nextInt(patternSetValues.size()));
            // A fifth of keys nest their pattern one level deep.
            if (random.nextInt(5) == 0) {
                members.add("\"" + keys.get(keyIndex) + "\": {\"inner\": " + patternSet + "}");
            } else {
                members.add("\"" + keys.get(keyIndex) + "\": " + patternSet);
            }
        }
        return "{" + String.join(", ", members) + "}";
    }

    private void assertEquivalentInAllConfigurations(String description, List<String> rules) throws Exception {
        for (boolean additionalNameStateReuse : new boolean[] { false, true }) {
            for (int maxComplexity : MAX_COMPLEXITIES) {
                assertEquivalent(description + " (additionalNameStateReuse=" + additionalNameStateReuse
                                + ", maxComplexity=" + maxComplexity + ")",
                        rules, additionalNameStateReuse, maxComplexity);
            }
        }
    }

    private void assertEquivalent(String description, List<String> rules, boolean additionalNameStateReuse,
                                  int maxComplexity) throws Exception {
        Machine machine = additionalNameStateReuse
                ? new Machine.Builder().withAdditionalNameStateReuse(true).build()
                : new Machine();
        int ruleIndex = 0;
        for (String rule : rules) {
            machine.addRule("rule" + ruleIndex++, rule);
        }
        assertMachineEvaluatesEquivalently(description, machine, maxComplexity);
    }

    private void assertMachineEvaluatesEquivalently(String description, Machine machine, int maxComplexity) {
        int perMatchRecursion = machine.evaluateComplexity(new PerMatchRecursionEvaluator(maxComplexity));
        int perNameStateRecursion = machine.evaluateComplexity(new MachineComplexityEvaluator(maxComplexity));
        assertEquals("complexity divergence: " + description, perMatchRecursion, perNameStateRecursion);
    }
}
