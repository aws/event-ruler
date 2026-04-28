package software.amazon.event.ruler;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import org.junit.Test;

/**
 * Regression tests for a compile-time crash in {@link ByteMachine} where the
 * backwards-array walker in
 * {@code extractNextJavaCharacterFromInputCharactersForBackwardsArrays} cast
 * an {@link software.amazon.event.ruler.input.InputWildcard} sentinel to an
 * {@link software.amazon.event.ruler.input.InputByte}.
 *
 * <p>Symptom before the fix:
 * {@code java.lang.ClassCastException: InputWildcard cannot be cast to InputByte}
 * thrown from {@link ByteMachine#isContinuationByte} (called by the backwards
 * walker at {@code addMatchValue}/{@code canReuseNextByteState}).
 *
 * <p>Trigger conditions:
 * <ol>
 *   <li>At least one suffix rule has been added, so {@code hasSuffix} {@literal >} 0.
 *       This enables {@code isNextCharacterFirstContinuationByteForSuffixMatch},
 *       which triggers the backwards walker for wildcard compile paths.</li>
 *   <li>A wildcard rule's parsed {@code InputCharacter[]} contains a multi-byte
 *       UTF-8 sequence immediately followed by a trailing {@code InputWildcard}
 *       sentinel — e.g. the value {@code *<multi-byte>*} or {@code <prefix><multi-byte>*}.</li>
 *   <li>A second wildcard rule with the same pattern value (or any subsequent
 *       wildcard compile that walks existing state and reaches
 *       {@code canReuseNextByteState} where {@code nextByteState.hasIndeterminatePrefix()}
 *       is true) is added. This drives the compiler into
 *       {@code doMultipleTransitionsConvergeForInputByte} →
 *       {@code extractNextJavaCharacterFromInputCharacters} →
 *       the backwards walker.</li>
 * </ol>
 *
 * <p>The {@code additionalNameStateReuse} builder flag (used by Jetstream ER
 * via {@code RulerMachineProvider}) amplifies the crash by forcing NameState
 * reuse for shared key subsequences, but the minimal 3-rule repro crashes
 * with both default {@code Machine} and the reuse-enabled config.</p>
 *
 * <p>The crash is not sensitive to the exact multi-byte character; any UTF-8
 * sequence whose terminal byte is itself a continuation byte (i.e. any 2+ byte
 * UTF-8 character whose last byte has bits {@code 10xxxxxx}) is enough to drive
 * the walker past the last data byte into the wildcard sentinel.
 *
 * <p>This class uses generic multi-byte kanji/katakana values ("次期", "データ",
 * "オブジェクト") chosen for byte-pattern coverage, not for any resemblance to
 * production data.
 */
public class ByteMachineMultiByteWildcardTest {

    // Three multi-byte UTF-8 words with mixed code-point widths:
    //   "次期"       : two 3-byte kanji (CJK Unified Ideographs)
    //   "データ"      : three 3-byte full-width katakana
    //   "オブジェクト"  : six 3-byte full-width katakana
    // The trailing code point in each case ends in a continuation byte
    // (0x9F/0xBF/0x88 etc. all share the 10xxxxxx high bits), which is what
    // historically drove the walker past the array tail.
    private static final String MB_WORD_1 = "\u6B21\u671F";                                   // 次期
    private static final String MB_WORD_2 = "\u30C7\u30FC\u30BF";                             // データ
    private static final String MB_WORD_3 = "\u30AA\u30D6\u30B8\u30A7\u30AF\u30C8";            // オブジェクト

    // Half-width katakana (3-byte UTF-8 each) used verbatim in the original
    // prod repro. The combination 0xEF 0xBE 0x80 is what first surfaced the
    // bug — keeping it in one test case guarantees byte-level coverage of
    // that exact sequence.
    private static final String MB_HALFWIDTH = "\uFF83\uFF9E\uFF70\uFF80";                    // ﾃﾞｰﾀ

    // --- Scenario 1: smallest repro of the pre-fix crash ------------------

    /**
     * Minimal 3-rule repro: suffix rule on the same path + two wildcard rules
     * with identical multi-byte + trailing-{@code *} value. With
     * {@code additionalNameStateReuse=true} (as used by Jetstream ER's
     * RulerMachineProvider), this deterministically crashes pre-fix.
     */
    @Test
    public void multiByteWildcardTwice_withSuffixRule_additionalReuse_compilesCleanly() throws Exception {
        Machine m = Machine.builder().withAdditionalNameStateReuse(true).build();

        String wildcardRule = makeWildcardKeyRule("*" + MB_WORD_2 + "*");
        String suffixRule = makeSuffixKeyRule(".csv");

        m.addRule("r-wildcard-first", wildcardRule);
        m.addRule("r-suffix", suffixRule);
        // Pre-fix: throws ClassCastException inside addRule.
        m.addRule("r-wildcard-second", wildcardRule);

        // Correctness: both wildcard rules match a value containing the substring.
        assertRulesMatch(m, jsonEventWithKey("prefix_" + MB_WORD_2 + "_suffix"),
                "r-wildcard-first", "r-wildcard-second");
        // Suffix rule matches independently.
        assertRulesMatch(m, jsonEventWithKey("report.csv"), "r-suffix");
        // Both match when value has the substring AND the suffix.
        assertRulesMatch(m, jsonEventWithKey(MB_WORD_2 + "_report.csv"),
                "r-wildcard-first", "r-wildcard-second", "r-suffix");
        // Neither matches an unrelated value.
        assertRulesMatch(m, jsonEventWithKey("unrelated.txt"));
    }

    /**
     * Same 3-rule repro with default configuration (no
     * {@code additionalNameStateReuse}). The bug is not gated on the reuse
     * flag in the minimal case; both configs crash pre-fix. Keeping this
     * test distinct from the reuse-flag variant guards against a regression
     * that only shows up in one configuration.
     */
    @Test
    public void multiByteWildcardTwice_withSuffixRule_defaultConfig_compilesCleanly() throws Exception {
        Machine m = new Machine();

        String wildcardRule = makeWildcardKeyRule("*" + MB_WORD_2 + "*");
        String suffixRule = makeSuffixKeyRule(".csv");

        m.addRule("r-wildcard-first", wildcardRule);
        m.addRule("r-suffix", suffixRule);
        m.addRule("r-wildcard-second", wildcardRule);

        // Correctness: same assertions as the reuse variant.
        assertRulesMatch(m, jsonEventWithKey("prefix_" + MB_WORD_2 + "_suffix"),
                "r-wildcard-first", "r-wildcard-second");
        assertRulesMatch(m, jsonEventWithKey("report.csv"), "r-suffix");
        assertRulesMatch(m, jsonEventWithKey(MB_WORD_2 + "_report.csv"),
                "r-wildcard-first", "r-wildcard-second", "r-suffix");
        assertRulesMatch(m, jsonEventWithKey("unrelated.txt"));
    }

    // --- Scenario 2: coverage by multi-byte code-point width --------------

    /**
     * Drive the bug with several distinct multi-byte values, each added twice
     * with a suffix rule wedged between, exercising 2-char, 3-char, and
     * 6-char kanji/katakana sequences under the crashing state flag.
     * Verifies matching correctness for each value after compile.
     */
    @Test
    public void variousMultiByteWildcards_additionalReuse_allCompileAndMatch() throws Exception {
        String[] wildcardValues = new String[] {
                "*" + MB_WORD_1 + "*",
                "*" + MB_WORD_2 + "*",
                "*" + MB_WORD_3 + "*",
                "*" + MB_HALFWIDTH + "*",
                "*" + MB_WORD_1 + MB_WORD_2 + "*",
                MB_WORD_1 + "*",
                "*" + MB_WORD_3,
        };
        // Corresponding event values that should match each wildcard.
        String[] matchingEventValues = new String[] {
                "prefix_" + MB_WORD_1 + "_suffix",
                "prefix_" + MB_WORD_2 + "_suffix",
                "prefix_" + MB_WORD_3 + "_suffix",
                "prefix_" + MB_HALFWIDTH + "_suffix",
                "prefix_" + MB_WORD_1 + MB_WORD_2 + "_suffix",
                MB_WORD_1 + "_anything",
                "anything_" + MB_WORD_3,
        };
        // Event values that should NOT match each wildcard.
        String[] nonMatchingEventValues = new String[] {
                "no_match_here",
                "no_match_here",
                "no_match_here",
                "no_match_here",
                "prefix_" + MB_WORD_1 + "_suffix",  // missing MB_WORD_2
                "x" + MB_WORD_1,                     // doesn't start with MB_WORD_1
                MB_WORD_3 + "_trailing",             // doesn't end with MB_WORD_3
        };

        for (int i = 0; i < wildcardValues.length; i++) {
            Machine m = Machine.builder().withAdditionalNameStateReuse(true).build();
            String wildcardRule = makeWildcardKeyRule(wildcardValues[i]);
            String suffixRule = makeSuffixKeyRule(".csv");

            m.addRule("r-wildcard-first-" + i, wildcardRule);
            m.addRule("r-suffix-" + i, suffixRule);
            m.addRule("r-wildcard-second-" + i, wildcardRule);

            // Correctness: wildcard rules match the expected value.
            assertRulesMatch(m, jsonEventWithKey(matchingEventValues[i]),
                    "r-wildcard-first-" + i, "r-wildcard-second-" + i);
            // Suffix rule matches independently.
            assertRulesMatch(m, jsonEventWithKey("data.csv"), "r-suffix-" + i);
            // Non-matching value produces no wildcard hits.
            assertRulesMatch(m, jsonEventWithKey(nonMatchingEventValues[i]));
        }
    }

    // --- Scenario 3: higher-fidelity to the prod shape --------------------

    /**
     * Adds many suffix and wildcard rules in alphabetical order, interleaved
     * the way a DDB-scan load does in production. Ensures the walker is
     * exercised with a realistic number of sibling paths and
     * {@code hasSuffix} values. Verifies matching correctness for each rule.
     */
    @Test
    public void manyRulesWithMultiByteWildcards_additionalReuse_allCompileAndMatch() throws Exception {
        Machine m = Machine.builder().withAdditionalNameStateReuse(true).build();

        // Suffix rules first -- they bump hasSuffix so any later wildcard
        // compile on the same path walks the buggy branch.
        m.addRule("a-suffix-1", makeMultiSuffixKeyRule(".csv", ".CSV"));
        m.addRule("a-suffix-2", makeMultiSuffixKeyRule("data.csv", "data.CSV"));
        m.addRule("a-suffix-3", makeSuffixKeyRule("JSYS0010_SYONIN_UTF8.csv"));
        m.addRule("a-suffix-4", makeSuffixKeyRule("_SYAIN1.csv"));

        // Interleaved ASCII wildcards and multi-byte wildcards.
        m.addRule("b-wildcard-ascii-1", makeWildcardKeyRule("*GBL_FLOG*"));
        m.addRule("b-wildcard-mb-1", makeWildcardKeyRule("*" + MB_WORD_1 + "*"));
        m.addRule("b-wildcard-mb-2", makeWildcardKeyRule("*" + MB_WORD_2 + "*"));
        m.addRule("b-wildcard-ascii-2", makeWildcardKeyRule("*BSC_*.csv"));
        m.addRule("b-wildcard-mb-3", makeWildcardKeyRule("*" + MB_HALFWIDTH + "*"));

        // Second copies of the same multi-byte values -- pre-fix crash site.
        m.addRule("c-wildcard-mb-1-copy", makeWildcardKeyRule("*" + MB_WORD_1 + "*"));
        m.addRule("c-wildcard-mb-2-copy", makeWildcardKeyRule("*" + MB_WORD_2 + "*"));
        m.addRule("c-wildcard-mb-3-copy", makeWildcardKeyRule("*" + MB_HALFWIDTH + "*"));

        // Correctness: suffix rules match by suffix.
        assertRulesMatch(m, jsonEventWithKey("report.csv"), "a-suffix-1");
        assertRulesMatch(m, jsonEventWithKey("report.CSV"), "a-suffix-1");
        assertRulesMatch(m, jsonEventWithKey("data.csv"), "a-suffix-1", "a-suffix-2");
        assertRulesMatch(m, jsonEventWithKey("JSYS0010_SYONIN_UTF8.csv"), "a-suffix-1", "a-suffix-3");
        assertRulesMatch(m, jsonEventWithKey("SF_SYAIN1.csv"), "a-suffix-1", "a-suffix-4");

        // Correctness: ASCII wildcards match.
        assertRulesMatch(m, jsonEventWithKey("prefix_GBL_FLOG_suffix"), "b-wildcard-ascii-1");
        assertRulesMatch(m, jsonEventWithKey("BSC_report.csv"),
                "a-suffix-1", "b-wildcard-ascii-2");

        // Correctness: multi-byte wildcards match (both original and copy).
        assertRulesMatch(m, jsonEventWithKey("x_" + MB_WORD_1 + "_y"),
                "b-wildcard-mb-1", "c-wildcard-mb-1-copy");
        assertRulesMatch(m, jsonEventWithKey("x_" + MB_WORD_2 + "_y"),
                "b-wildcard-mb-2", "c-wildcard-mb-2-copy");
        assertRulesMatch(m, jsonEventWithKey("x_" + MB_HALFWIDTH + "_y"),
                "b-wildcard-mb-3", "c-wildcard-mb-3-copy");

        // Correctness: no false positives.
        assertRulesMatch(m, jsonEventWithKey("completely_unrelated.txt"));
    }

    // --- Scenario 4: add order and state-interaction permutations ---------

    /**
     * Compile the same three-rule repro in every permutation of add order.
     * The pre-fix crash is order-sensitive; the fix must tolerate all orders
     * and produce correct matching in each.
     */
    @Test
    public void allAddOrders_threeRuleRepro_additionalReuse_compileAndMatch() throws Exception {
        String wildcardRule = makeWildcardKeyRule("*" + MB_WORD_2 + "*");
        String suffixRule = makeSuffixKeyRule(".csv");

        String[] names = new String[] { "W1", "W2", "S" };
        String[] defs = new String[] { wildcardRule, wildcardRule, suffixRule };

        int[][] perms = new int[][] {
                { 0, 1, 2 }, { 0, 2, 1 }, { 1, 0, 2 },
                { 1, 2, 0 }, { 2, 0, 1 }, { 2, 1, 0 },
        };

        for (int[] p : perms) {
            Machine m = Machine.builder().withAdditionalNameStateReuse(true).build();
            for (int idx : p) {
                m.addRule(names[idx], defs[idx]);
            }

            // Correctness: same expected results regardless of add order.
            String desc = "order=" + Arrays.toString(p);
            assertRulesMatchMsg(desc, m, jsonEventWithKey("x_" + MB_WORD_2 + "_y"),
                    "W1", "W2");
            assertRulesMatchMsg(desc, m, jsonEventWithKey("report.csv"),
                    "S");
            assertRulesMatchMsg(desc, m, jsonEventWithKey(MB_WORD_2 + ".csv"),
                    "W1", "W2", "S");
            assertRulesMatchMsg(desc, m, jsonEventWithKey("unrelated.txt"));
        }
    }

    // --- Scenario 5: runtime matching preserved after fix -----------------

    /**
     * The fix alters the compile path, not the match path. A multi-byte
     * wildcard should still match a JSON value that contains the pattern
     * substring, and a coexisting suffix rule should still match by suffix.
     */
    @Test
    public void matching_afterFix_multiByteWildcardAndSuffixStillMatch() throws Exception {
        Machine m = Machine.builder().withAdditionalNameStateReuse(true).build();

        m.addRule("wildcard", makeWildcardKeyRule("*" + MB_WORD_2 + "*"));
        m.addRule("suffix", makeSuffixKeyRule(".csv"));

        // Wildcard matches when the multi-byte substring is present.
        List<String> r1 = m.rulesForJSONEvent(jsonEventWithKey(
                "prefix_" + MB_WORD_2 + "_suffix.txt"));
        assertEquals(Collections.singletonList("wildcard"), r1);

        // Suffix-only matches cleanly.
        List<String> r2 = m.rulesForJSONEvent(jsonEventWithKey("hello.csv"));
        assertEquals(Collections.singletonList("suffix"), r2);

        // Both match when both substrings are present.
        Set<String> r3 = new HashSet<>(m.rulesForJSONEvent(jsonEventWithKey(
                MB_WORD_2 + "_payload.csv")));
        assertEquals(new HashSet<>(Arrays.asList("wildcard", "suffix")), r3);

        // Neither matches for an unrelated value.
        assertEquals(Collections.emptyList(),
                m.rulesForJSONEvent(jsonEventWithKey("unrelated.txt")));
    }

    /**
     * Add-then-delete-then-add should also compile cleanly and match
     * correctly. Exercises the walker after {@link ByteMachine#deletePattern}
     * mutations have touched the shared state.
     */
    @Test
    public void addDeleteAdd_multiByteWildcardWithSuffix_compilesAndMatchesCorrectly() throws Exception {
        Machine m = Machine.builder().withAdditionalNameStateReuse(true).build();

        String wildcardRule = makeWildcardKeyRule("*" + MB_WORD_2 + "*");
        String suffixRule = makeSuffixKeyRule(".csv");

        m.addRule("w1", wildcardRule);
        m.addRule("s", suffixRule);
        m.addRule("w2", wildcardRule);
        m.deleteRule("w2", wildcardRule);
        m.addRule("w2-again", wildcardRule);

        // Correctness: w1 and w2-again match; w2 was deleted.
        assertRulesMatch(m, jsonEventWithKey("x_" + MB_WORD_2 + "_y"),
                "w1", "w2-again");
        assertRulesMatch(m, jsonEventWithKey("report.csv"), "s");
        assertRulesMatch(m, jsonEventWithKey("unrelated.txt"));
    }

    // --- Direct stress of isContinuationByte / backwards walker -----------

    /**
     * Direct probe of the previously-crashing call path via {@code Machine}
     * compile. We add 50 distinct multi-byte wildcard rules, each followed
     * by a duplicate, with suffix rules primed -- if any path through
     * {@code canReuseNextByteState} regresses, this catches it. Verifies
     * matching correctness for a sample of the compiled rules.
     */
    @Test
    public void stress_manyDuplicateMultiByteWildcards_additionalReuse_allCompileAndMatch() throws Exception {
        Machine m = Machine.builder().withAdditionalNameStateReuse(true).build();

        m.addRule("suffix-prime-1", makeSuffixKeyRule(".csv"));
        m.addRule("suffix-prime-2", makeSuffixKeyRule(".CSV"));
        m.addRule("suffix-prime-3", makeSuffixKeyRule(".txt"));

        String[] words = new String[] { MB_WORD_1, MB_WORD_2, MB_WORD_3 };

        for (int i = 0; i < 50; i++) {
            String word = words[i % 3];
            String value = "*" + word + "*";

            try {
                m.addRule("w-" + i + "-first", makeWildcardKeyRule(value));
                m.addRule("w-" + i + "-second", makeWildcardKeyRule(value));
            } catch (Throwable t) {
                fail("Unexpected failure compiling iteration " + i
                        + " with value <" + value + ">: " + t);
            }
        }

        // Correctness: spot-check matching for each of the three word families.
        // All rules with the same word should match an event containing that word.
        for (int wordIdx = 0; wordIdx < words.length; wordIdx++) {
            Set<String> expected = new HashSet<>();
            for (int i = wordIdx; i < 50; i += 3) {
                expected.add("w-" + i + "-first");
                expected.add("w-" + i + "-second");
            }
            Set<String> actual = new HashSet<>(m.rulesForJSONEvent(
                    jsonEventWithKey("prefix_" + words[wordIdx] + "_suffix")));
            assertEquals("word family " + wordIdx, expected, actual);
        }

        // Suffix rules still match independently.
        assertRulesMatch(m, jsonEventWithKey("report.csv"), "suffix-prime-1");
        assertRulesMatch(m, jsonEventWithKey("report.CSV"), "suffix-prime-2");
        assertRulesMatch(m, jsonEventWithKey("report.txt"), "suffix-prime-3");
    }

    // --- Helpers ----------------------------------------------------------

    /** Assert that exactly {@code expectedRules} match the given event. */
    private static void assertRulesMatch(Machine m, String jsonEvent, String... expectedRules) throws Exception {
        Set<String> expected = new HashSet<>(Arrays.asList(expectedRules));
        Set<String> actual = new HashSet<>(m.rulesForJSONEvent(jsonEvent));
        assertEquals(expected, actual);
    }

    /** Assert with a description prefix for better failure messages. */
    private static void assertRulesMatchMsg(String desc, Machine m, String jsonEvent, String... expectedRules) throws Exception {
        Set<String> expected = new HashSet<>(Arrays.asList(expectedRules));
        Set<String> actual = new HashSet<>(m.rulesForJSONEvent(jsonEvent));
        assertEquals(desc, expected, actual);
    }

    private static String jsonEventWithKey(String key) {
        return "{\"detail\":{\"object\":{\"key\":" + jsonString(key) + "}}}";
    }

    private static String makeWildcardKeyRule(String wildcardValue) {
        return "{\"detail\":{\"object\":{\"key\":[{\"wildcard\":" + jsonString(wildcardValue) + "}]}}}";
    }

    private static String makeSuffixKeyRule(String suffixValue) {
        return "{\"detail\":{\"object\":{\"key\":[{\"suffix\":" + jsonString(suffixValue) + "}]}}}";
    }

    private static String makeMultiSuffixKeyRule(String... suffixValues) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"detail\":{\"object\":{\"key\":[");
        for (int i = 0; i < suffixValues.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("{\"suffix\":").append(jsonString(suffixValues[i])).append("}");
        }
        sb.append("]}}}");
        return sb.toString();
    }

    /** Minimal JSON string escaper — sufficient for the multi-byte values used here. */
    private static String jsonString(String raw) {
        StringBuilder sb = new StringBuilder(raw.length() + 2);
        sb.append('"');
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '"' || c == '\\') {
                sb.append('\\').append(c);
            } else {
                sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
