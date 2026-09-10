package software.amazon.event.ruler;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static software.amazon.event.ruler.PermutationsGenerator.generateAllPermutations;

/**
 * For each test, for illustrative purposes, I will provide one input string that results in the maximum number of
 * matching wildcard rule prefixes. Note there may be other input strings that achieve the same number of matching
 * wildcard rule prefixes. However, there are no input strings that result in a higher number of wildcard rule prefixes
 * (try to find one if you'd like).
 */
public class MachineComplexityEvaluatorTest {

    private static final int MAX_COMPLEXITY = 100;

    /**
     * "aaa" is matched by 7 prefixes of this pattern: "*", "*a", "*a*", "*a*a", "*a*a*", "*a*a*a" and "*a*a*a*".
     */
    private static final String WILDCARD_OF_COMPLEXITY_7 = "{\"wildcard\": \"*a*a*a*\"}";

    private MachineComplexityEvaluator evaluator;

    @Before
    public void setup() {
        evaluator = new MachineComplexityEvaluator(MAX_COMPLEXITY);
    }

    @Test
    public void testEvaluateOnlyWildcard() {
        ByteMachine machine = new ByteMachine();
        machine.addPattern(Patterns.wildcardMatch("*"));
        // "a" is matched by 1 wildcard prefix: "*"
        assertEquals(1, machine.evaluateComplexity(evaluator));
    }

    @Test
    public void testEvaluateOnlyWildcardWithExactMatch() {
        // "abc" is matched by 1 wildcard prefix: "*"
        testPatternPermutations(1, Patterns.wildcardMatch("*"),
                                   Patterns.exactMatch("abc"));
    }

    @Test
    public void testEvaluateOnlyWildcardWithWildcardMatch() {
        // "abc" is matched by 3 wildcard prefixes: "*", "ab*", "ab*c"
        testPatternPermutations(3, Patterns.wildcardMatch("*"),
                                   Patterns.wildcardMatch("ab*c"));
    }

    @Test
    public void testEvaluateWildcardPatternWithoutWildcards() {
        ByteMachine machine = new ByteMachine();
        machine.addPattern(Patterns.wildcardMatch("abc"));
        // "abc" is matched by 1 wildcard prefix: "abc"
        assertEquals(1, machine.evaluateComplexity(evaluator));
    }

    @Test
    public void testEvaluateOneWildcardLeadingCharTwoTrailingCharactersDifferent() {
        ByteMachine machine = new ByteMachine();
        machine.addPattern(Patterns.wildcardMatch("*ab"));
        // "ab" is matched by 2 wildcard prefixes: "*", "*ab"
        assertEquals(2, machine.evaluateComplexity(evaluator));
    }

    @Test
    public void testEvaluateOneWildcardLeadingCharTwoTrailingCharactersEqual() {
        ByteMachine machine = new ByteMachine();
        machine.addPattern(Patterns.wildcardMatch("*aa"));
        // "ab" is matched by 3 wildcard prefixes: "*", "*a", "*aa"
        assertEquals(3, machine.evaluateComplexity(evaluator));
    }

    @Test
    public void testEvaluateOneWildcardSecondLastChar() {
        ByteMachine machine = new ByteMachine();
        machine.addPattern(Patterns.wildcardMatch("a*b"));
        // "ab" is matched by 2 wildcard prefixes: "a*", "a*b"
        assertEquals(2, machine.evaluateComplexity(evaluator));
    }

    @Test
    public void testEvaluateOneWildcardTrailingChar() {
        ByteMachine machine = new ByteMachine();
        machine.addPattern(Patterns.wildcardMatch("aa*"));
        // "aa" is matched by 2 wildcard prefixes: "aa", "aa*"
        assertEquals(2, machine.evaluateComplexity(evaluator));
    }

    @Test
    public void testEvaluateOneWildcardNormalPositionTwoTrailingCharactersDifferent() {
        ByteMachine machine = new ByteMachine();
        machine.addPattern(Patterns.wildcardMatch("a*bc"));
        // "ab" is matched by 2 wildcard prefixes: "a*", "a*b"
        assertEquals(2, machine.evaluateComplexity(evaluator));
    }

    @Test
    public void testEvaluateOneWildcardNormalPositionTwoTrailingCharactersEqual() {
        ByteMachine machine = new ByteMachine();
        machine.addPattern(Patterns.wildcardMatch("a*bb"));
        // "abb" is matched by 3 wildcard prefixes: "a*", "a*b", "a*bb"
        assertEquals(3, machine.evaluateComplexity(evaluator));
    }

    @Test
    public void testEvaluateOneWildcardNormalPositionThreeTrailingCharactersDifferent() {
        ByteMachine machine = new ByteMachine();
        machine.addPattern(Patterns.wildcardMatch("a*bcb"));
        // "abcb" is matched by 3 wildcard prefixes: "a*", "a*b", "a*bcb"
        assertEquals(3, machine.evaluateComplexity(evaluator));
    }

    @Test
    public void testEvaluateOneWildcardNormalPositionThreeTrailingCharactersEqual() {
        ByteMachine machine = new ByteMachine();
        machine.addPattern(Patterns.wildcardMatch("a*bbb"));
        // "abbb" is matched by 4 wildcard prefixes: "a*", "a*b", "a*bb", "a*bbb"
        assertEquals(4, machine.evaluateComplexity(evaluator));
    }

    @Test
    public void testEvaluateTwoWildcardsLeadingCharAndNormalPosition() {
        ByteMachine machine = new ByteMachine();
        machine.addPattern(Patterns.wildcardMatch("*ab*ad"));
        // "aba" is matched by 4 wildcard prefixes: "*", "*a", "*ab*", "*ab*a"
        assertEquals(4, machine.evaluateComplexity(evaluator));
    }

    @Test
    public void testEvaluateTwoWildcardsLeadingCharAndSecondLastChar() {
        ByteMachine machine = new ByteMachine();
        machine.addPattern(Patterns.wildcardMatch("*ab*d"));
        // "abd" is matched by 3 wildcard prefixes: "*", "*ab*", "*ab*d"
        assertEquals(3, machine.evaluateComplexity(evaluator));
    }

    @Test
    public void testEvaluateTwoWildcardsLeadingCharAndTrailingChar() {
        ByteMachine machine = new ByteMachine();
        machine.addPattern(Patterns.wildcardMatch("*aba*"));
        // "aba" is matched by 4 wildcard prefixes: "*", "*a", "*aba", "*aba*"
        assertEquals(4, machine.evaluateComplexity(evaluator));
    }

    @Test
    public void testEvaluateTwoWildcardsBothNormalPositionTwoTrailingCharactersEqual() {
        ByteMachine machine = new ByteMachine();
        machine.addPattern(Patterns.wildcardMatch("a*b*bb"));
        // "abbb" is matched by 5 wildcard prefixes: "a*", "a*b", "a*b*", "a*b*b", "a*b*bb"
        assertEquals(5, machine.evaluateComplexity(evaluator));
    }

    @Test
    public void testEvaluateTwoWildcardsBothNormalPositionTwoTrailingCharactersDifferent() {
        ByteMachine machine = new ByteMachine();
        machine.addPattern(Patterns.wildcardMatch("a*b*cb"));
        // "abcb" is matched by 4 wildcard prefixes: "a*", "a*b", "a*b*", "a*b*cb"
        assertEquals(4, machine.evaluateComplexity(evaluator));
    }

    @Test
    public void testEvaluateTwoWildcardsBothNormalPositionTwoTrailingCharactersDifferentLastCharUnique() {
        ByteMachine machine = new ByteMachine();
        machine.addPattern(Patterns.wildcardMatch("a*b*cd"));
        // "abcd" is matched by 3 wildcard prefixes: "a*", "a*b*", "a*b*cd"
        assertEquals(3, machine.evaluateComplexity(evaluator));
    }

    @Test
    public void testEvaluateTwoWildcardsNormalPositionAndSecondLastChar() {
        ByteMachine machine = new ByteMachine();
        machine.addPattern(Patterns.wildcardMatch("a*b*b"));
        // "abb" is matched by 4 wildcard prefixes: "a*", "a*b", "a*b*", "a*b*b"
        assertEquals(4, machine.evaluateComplexity(evaluator));
    }

    @Test
    public void testEvaluateTwoWildcardsNormalPositionAndTrailingChar() {
        ByteMachine machine = new ByteMachine();
        machine.addPattern(Patterns.wildcardMatch("a*bb*"));
        // "abb" is matched by 4 wildcard prefixes: "a*", "a*b", "a*bb", "a*bb*"
        assertEquals(4, machine.evaluateComplexity(evaluator));
    }

    @Test
    public void testEvaluateTwoWildcardsThirdLastCharAndTrailingChar() {
        ByteMachine machine = new ByteMachine();
        machine.addPattern(Patterns.wildcardMatch("ab*b*"));
        // "abb" is matched by 3 wildcard prefixes: "ab*", "ab*b", "ab*b*"
        assertEquals(3, machine.evaluateComplexity(evaluator));
    }

    @Test
    public void testEvaluateTwoWildcardsNoCommonPrefix() {
        // "xxx" is matched by 3 wildcard prefixes: "x*", "x*x", "x*xx"
        testPatternPermutations(3, Patterns.wildcardMatch("ab*c"),
                                   Patterns.wildcardMatch("x*xx"));
    }

    @Test
    public void testEvaluateTwoWildcardsBothLeadingOneIsPrefixOfOther() {
        // "abc" is matched by 4 wildcard prefixes: "*", "*abc", "*", "*abc"
        testPatternPermutations(4, Patterns.wildcardMatch("*abc"),
                                   Patterns.wildcardMatch("*abcde"));
    }

    @Test
    public void testEvaluateTwoWildcardsBothNormalPosition() {
        // "abcd" is matched by 4 wildcard prefixes: "a*", "a*bcd", "ab*", "ab*cd"
        testPatternPermutations(4, Patterns.wildcardMatch("a*bcd"),
                                   Patterns.wildcardMatch("ab*cd"));
    }

    @Test
    public void testEvaluateTwoWildcardsBothNormalPositionOneIsPrefixOfOther() {
        // "ab" is matched by 4 wildcard prefixes: "a*", "a*b", "a*", "a*b"
        testPatternPermutations(4, Patterns.wildcardMatch("a*bc"),
                                   Patterns.wildcardMatch("a*bcde"));
    }

    @Test
    public void testEvaluateTwoWildcardsBothNormalPositionAllSameCharacter() {
        // "aaaa" is matched by 7 wildcard prefixes: "a*", "a*a", "a*aa", "a*aaa", "aa*", "aa*a", "aa*aa"
        testPatternPermutations(7, Patterns.wildcardMatch("a*aaa"),
                                   Patterns.wildcardMatch("aa*aa"));
    }

    @Test
    public void testEvaluateTwoWildcardsOneNormalPositionAndOneSecondLastCharacter() {
        // "abc" is matched by 4 wildcard prefixes: "a*", "a*bc", "abc", "abc*"
        testPatternPermutations(4, Patterns.wildcardMatch("a*bc"),
                                   Patterns.wildcardMatch("abc*d"));
    }

    @Test
    public void testEvaluateTwoWildcardsOneNormalPositionAndOneSecondLastCharacterAllSameCharacter() {
        // "aaaa" is matched by 6 wildcard prefixes: "a*", "a*a", "a*aa", "a*aaa", "aaa*", "aaa*a"
        testPatternPermutations(6, Patterns.wildcardMatch("a*aaa"),
                                   Patterns.wildcardMatch("aaa*a"));
    }

    @Test
    public void testEvaluateTwoWildcardsOneNormalPositionAndOneSecondLastCharacterAllSameCharacterButLast() {
        // "aaa" is matched by 5 wildcard prefixes: "a*", "a*a", "a*aa", "aaa", "aaa*"
        testPatternPermutations(5, Patterns.wildcardMatch("a*aax"),
                                   Patterns.wildcardMatch("aaa*x"));
    }

    @Test
    public void testEvaluateTwoWildcardsOneNormalPositionAndOneTrailing() {
        // "abc" is matched by 4 wildcard prefixes: "a*", "a*bc", "abc", "abc*"
        testPatternPermutations(4, Patterns.wildcardMatch("a*bc"),
                                   Patterns.wildcardMatch("abc*"));
    }

    @Test
    public void testEvaluateTwoWildcardsOneNormalPositionAndOneTrailingAllSameCharacter() {
        // "aaa" is matched by 5 wildcard prefixes: "a*", "a*a", "a*aa", "aaa", "aaa*"
        testPatternPermutations(5, Patterns.wildcardMatch("a*aa"),
                                   Patterns.wildcardMatch("aaa*"));
    }

    @Test
    public void testEvaluateTwoWildcardsBothTrailingCharOneIsPrefixOfOther() {
        // "abc" is matched by 3 wildcard prefixes: "abc", "abc*", "abc"
        testPatternPermutations(3, Patterns.wildcardMatch("abc*"),
                                   Patterns.wildcardMatch("abcde*"));
    }

    @Test
    public void testEvaluateThreeWildcardsTransitionFromSameState() {
        // "ab" is matched by 6 wildcard prefixes: "ab", "ab*", "ab", "ab*", "ab", "ab*"
        testPatternPermutations(6, Patterns.wildcardMatch("ab*cd"),
                                   Patterns.wildcardMatch("ab*wx"),
                                   Patterns.wildcardMatch("ab*yz"));
    }

    @Test
    public void testEvaluateFourWildcardsLeadingCharNormalPositionThirdLastCharAndTrailingChar() {
        ByteMachine machine = new ByteMachine();
        machine.addPattern(Patterns.wildcardMatch("*ab*b*b*"));
        // "abbbab" is matched by 7 wildcard prefixes: "*", "*ab", "*ab*", "*ab*b", "*ab*b*", "*ab*b*b", "*ab*b*b*"
        assertEquals(7, machine.evaluateComplexity(evaluator));
    }

    @Test
    public void testEvaluateLongSequenceofWildcards() {
        ByteMachine machine = new ByteMachine();
        machine.addPattern(Patterns.wildcardMatch("*a*a*a*a*a*a*a*a*"));
        // "aaaaaaaa" is matched by all 17 wildcard prefixes
        assertEquals(17, machine.evaluateComplexity(evaluator));
    }

    @Test
    public void testEvaluateOneWildcardLeadingCharWithExactMatch() {
        // "abc" is matched by 2 wildcard prefixes: "*", "*abc"
        testPatternPermutations(2, Patterns.wildcardMatch("*abc"),
                                   Patterns.exactMatch("abc"));
    }

    @Test
    public void testEvaluateOneWildcardNormalPositionWithExactMatch() {
        // "abc" is matched by 2 wildcard prefixes: "a*", "a*bc"
        testPatternPermutations(2, Patterns.wildcardMatch("a*bc"),
                                   Patterns.exactMatch("abc"));
    }

    @Test
    public void testEvaluateOneWildcardSecondLastCharWithExactMatch() {
        // "abc" is matched by 2 wildcard prefixes: "ab*", "ab*c"
        testPatternPermutations(2, Patterns.wildcardMatch("ab*c"),
                                   Patterns.exactMatch("abc"));
    }

    @Test
    public void testEvaluateOneWildcardTrailingCharWithExactMatch() {
        // "abc" is matched by 2 wildcard prefixes: "abc", "abc*"
        testPatternPermutations(2, Patterns.wildcardMatch("abc*"),
                                   Patterns.exactMatch("abc"));
    }

    @Test
    public void testEvaluateOneWildcardTrailingCharWithLongerExactMatch() {
        // "abc" is matched by 2 wildcard prefixes: "abc", "abc*"
        testPatternPermutations(2, Patterns.wildcardMatch("abc*"),
                                   Patterns.exactMatch("abcde"));
    }

    @Test
    public void testEvaluateOneWildcardTrailingCharWithLongerExactMatchPrefixMatchAndEqualsIgnoreCaseMatch() {
        // "abc" is matched by 2 wildcard prefixes: "abc", "abc*"
        testPatternPermutations(2, Patterns.wildcardMatch("abc*"),
                                   Patterns.exactMatch("abcde"),
                                   Patterns.prefixMatch("abcde"),
                                   Patterns.equalsIgnoreCaseMatch("ABCDE"));
    }

    @Test
    public void testEvaluateOneWildcardTrailingCharWithVaryingLengthExactMatchPrefixMatchAndEqualsIgnoreCaseMatch() {
        // "abc" is matched by 2 wildcard prefixes: "abc", "abc*"
        testPatternPermutations(2, Patterns.wildcardMatch("abc*"),
                                   Patterns.exactMatch("abcde"),
                                   Patterns.prefixMatch("abcdef"),
                                   Patterns.equalsIgnoreCaseMatch("ABCDEFG"));
    }

    @Test
    public void testEvaluateExistencePatternHasNoEffect() {
        // "ab" is matched by 2 wildcard prefixes: "ab", "ab*"
        testPatternPermutations(2, Patterns.wildcardMatch("ab*c"),
                                   Patterns.exactMatch("abc"),
                                   Patterns.existencePatterns());
    }

    @Test
    public void testEvaluateJustExactMatches() {
        testPatternPermutations(0, Patterns.exactMatch("abc"),
                                   Patterns.exactMatch("abcde"));
    }

    @Test
    public void testEvaluateDuplicateWildcardPatterns() {
        // "abc" is matched by 2 wildcard prefixes: "ab*", "ab*c" (duplicate patterns do not duplicate prefix count)
        testPatternPermutations(2, Patterns.wildcardMatch("ab*c"),
                                   Patterns.wildcardMatch("ab*c"));
    }

    @Test
    public void testEvaluateWordEndingInSameLetterThatFollowsWildcard() {
        // "FeatureFeature" is matched by 10 wildcard prefixes: "F*", "F*e", "F*eature", "F*eatureFeature", "Fe*",
        //                                                      "Fe*ature", "Fe*atureFeature", "Fea*", "Fea*ture",
        //                                                      "Fea*tureFeature"
        testPatternPermutations(10, Patterns.wildcardMatch("F*eatureFeature"),
                                    Patterns.wildcardMatch("Fe*atureFeature"),
                                    Patterns.wildcardMatch("Fea*tureFeature"));
    }

    @Test
    public void testEvaluateNestedMachinesViaNextNameStates() throws Exception {
        Machine machine = new Machine();
        machine.addRule("name", "{" +
                "\"abc\": [ { \"prefix\": \"a\" }, \"abcdef\", { \"suffix\": \"z\" } ]," +
                "\"def\": [ { \"prefix\": \"b\" }, { \"wildcard\": \"a*a*a*a*a*a*\" }, { \"suffix\": \"c\" } ]," +
                "\"ghi\": [ { \"prefix\": \"a\" }, \"abcdef\", { \"suffix\": \"z\" } ]" +
                "}");
        assertEquals(11, machine.evaluateComplexity(evaluator));
    }

    /**
     * I'm not going to try to determine the maximum complexity input string. This is here just to demonstrate that this
     * set of rules, which has proven problematic for Quamina in the past, is handled ok by Ruler.
     */
    @Test
    public void testEvaluateQuaminaExploder() {
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
        assertEquals(45, machine.evaluateComplexity(evaluator));
    }

    /**
     * This test verifies that complexity evaluation caps out at 100. This test also indirectly verifies, by having
     * reasonable runtime, that a full traversal for the worst-case input is not performed. Otherwise, we'd be looking
     * at runtime of O(n^2) where n=140,000.
     */
    @Test
    public void testEvaluateBeyondMaxComplexity() throws InterruptedException {
        Timer timer = new Timer();
        ByteMachine machine = new ByteMachine();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            builder.append("F*e*a*t*u*r*e*");
        }
        machine.addPattern(Patterns.wildcardMatch(builder.toString()));

        // Start a complexity evaluation task.
        final int[] complexity = { -1 };
        TimerTask evaluationTask = new TimerTask() {
            @Override
            public void run() {
                complexity[0] = machine.evaluateComplexity(evaluator);
            }
        };
        timer.schedule(evaluationTask, 0);

        // Start a timeout task that will fail the test if complexity evaluation takes over 60 seconds.
        final boolean[] timedOut = { false };
        TimerTask timeoutTask = new TimerTask() {
            @Override
            public void run() {
                timedOut[0] = true;
            }
        };
        timer.schedule(timeoutTask, 60000);

        // Wait either for complexity evaluation to finish or for timeout to occur.
        while (complexity[0] == -1 && !timedOut[0]) {
            Thread.sleep(10);
        }

        if (timedOut[0]) {
            fail("Complexity evaluation took over 60 seconds");
        }
        assertEquals(MAX_COMPLEXITY, complexity[0]);

        // Cancel the timeoutTask in case it hasn't run yet.
        timeoutTask.cancel();
    }

    @Test
    public void testEvaluateAnythingButWildcard() {
        ByteMachine machine = new ByteMachine();
        machine.addPattern(Patterns.anythingButWildcard("a*b*b"));
        // "abb" is matched by 4 wildcard prefixes: "a*", "a*b", "a*b*", "a*b*b"
        assertEquals(4, machine.evaluateComplexity(evaluator));
    }

    @Test
    public void testEvaluateAnythingButWildcardMultiplePatterns() {
        // "aaaa" is matched by 7 wildcard prefixes: "a*", "a*a", "a*aa", "a*aaa", "aa*", "aa*a", "aa*aa"
        testPatternPermutations(7, Patterns.anythingButWildcard("a*aaa"),
                                   Patterns.anythingButWildcard("aa*aa"));
    }

    @Test
    public void testEvaluateAnythingButWildcardMultiplePatternsViaSet() {
        ByteMachine machine = new ByteMachine();
        machine.addPattern(Patterns.anythingButWildcard(new HashSet<>(Arrays.asList("a*aaa", "aa*aa"))));
        // "aaaa" is matched by 7 wildcard prefixes: "a*", "a*a", "a*aa", "a*aaa", "aa*", "aa*a", "aa*aa"
        assertEquals(6, machine.evaluateComplexity(evaluator));
    }

    @Test
    public void testEvaluateAnythingButWildcardWithWildcard() {
        // "aaaa" is matched by 7 wildcard prefixes: "a*", "a*a", "a*aa", "a*aaa", "aa*", "aa*a", "aa*aa"
        testPatternPermutations(7, Patterns.anythingButWildcard("a*aaa"),
                                   Patterns.wildcardMatch("aa*aa"));
    }

    @Test
    public void testEvaluateWildcardWithAnythingButWildcard() {
        // "aaaa" is matched by 7 wildcard prefixes: "a*", "a*a", "a*aa", "a*aaa", "aa*", "aa*a", "aa*aa"
        testPatternPermutations(7, Patterns.wildcardMatch("a*aaa"),
                                   Patterns.anythingButWildcard("aa*aa"));
    }

    /**
     * Make sure we do not trigger a state explosion when evaluating complexity for rules
     * with numeric matchers
     */
    @Test(timeout = 250)
    public void testEvaluateForMultipleNumericMatchers() throws Exception {
        String rule = "{\n" +
                "    \"field1\": [{\n" +
                "        \"numeric\": [\"<=\", 120.0]\n" +
                "    }],\n" +
                "    \"field2\": [{\n" +
                "        \"numeric\": [\">\", 300.0]\n" +
                "    }],\n" +
                "    \"field3\": [{\n" +
                "        \"numeric\": [\"=\", 60.0]\n" +
                "    }],\n" +
                "    \"field4\": [{\n" +
                "        \"numeric\": [\"<\", 60.0]\n" +
                "    }],\n" +
                "    \"field5\": [{\n" +
                "        \"numeric\": [\"<=\", 60.0]\n" +
                "    }]\n" +
                "}";
        Machine machine = new Machine.Builder().withAdditionalNameStateReuse(true).build();
        machine.addRule("rule", rule);
        assertEquals(0, machine.evaluateComplexity(evaluator));

        machine = new Machine.Builder().withAdditionalNameStateReuse(false).build();
        machine.addRule("rule", rule);
        assertEquals(0, machine.evaluateComplexity(evaluator));
    }

    @Test
    public void testEvaluateWildcardBehindAbsentKey() throws Exception {
        // Keys sort as aaa, zzz: the wildcard machine on zzz sits behind aaa's absent-key pattern, which is a
        // NameMatcher edge rather than a ByteMachine. "zz" is matched by 3 wildcard prefixes: "*", "*z", "*z*".
        String ruleBehindAbsentKey = "{\"aaa\": [{\"exists\": false}], \"zzz\": [{\"wildcard\": \"*z*\"}]}";
        String ruleBehindPresentKey = "{\"aaa\": [{\"exists\": true}], \"zzz\": [{\"wildcard\": \"*z*\"}]}";
        String wildcardRule = "{\"zzz\": [{\"wildcard\": \"*z*\"}]}";
        for (boolean additionalNameStateReuse : new boolean[] { false, true }) {
            assertEquals(3, complexityOfRule(ruleBehindAbsentKey, additionalNameStateReuse));
            assertEquals(3, complexityOfRule(ruleBehindPresentKey, additionalNameStateReuse));
            assertEquals(3, complexityOfRule(wildcardRule, additionalNameStateReuse));
        }
    }

    @Test
    public void testEvaluateWildcardBehindNestedAbsentKey() throws Exception {
        String rule = "{\"aaa\": {\"inner\": [{\"exists\": false}]}, \"zzz\": [{\"wildcard\": \"*z*\"}]}";
        // "zz" is matched by 3 wildcard prefixes: "*", "*z", "*z*"
        assertEquals(3, complexityOfRule(rule, false));
        assertEquals(3, complexityOfRule(rule, true));
    }

    @Test
    public void testEvaluateWildcardBehindAbsentKeyRespectsMaxComplexity() throws Exception {
        String rule = "{\"aaa\": [{\"exists\": false}], \"zzz\": [{\"wildcard\": \"*z*\"}]}";
        Machine machine = new Machine();
        machine.addRule("rule", rule);
        assertEquals(1, machine.evaluateComplexity(new MachineComplexityEvaluator(1)));
        assertEquals(2, machine.evaluateComplexity(new MachineComplexityEvaluator(2)));
    }

    @Test
    public void testComplexityBehindAbsentKeysIsCountedByDefault() {
        assertFalse(new MachineComplexityEvaluator(MAX_COMPLEXITY).isComplexityBehindAbsentKeysIgnored());
    }

    @Test
    public void testWithComplexityBehindAbsentKeysIgnoredReturnsNewEvaluatorWithSameCap() {
        MachineComplexityEvaluator strict = new MachineComplexityEvaluator(5);
        MachineComplexityEvaluator ignoring = strict.withComplexityBehindAbsentKeysIgnored(true);
        assertNotSame(strict, ignoring);
        assertFalse(strict.isComplexityBehindAbsentKeysIgnored());
        assertTrue(ignoring.isComplexityBehindAbsentKeysIgnored());
        assertEquals(5, ignoring.getMaxComplexity());
        assertFalse(ignoring.withComplexityBehindAbsentKeysIgnored(false).isComplexityBehindAbsentKeysIgnored());
    }

    @Test
    public void testSubclassTakesComplexityBehindAbsentKeysIgnoredThroughProtectedConstructor() throws Exception {
        MachineComplexityEvaluator subclassIgnoring = new MachineComplexityEvaluator(MAX_COMPLEXITY, true) { };
        assertTrue(subclassIgnoring.isComplexityBehindAbsentKeysIgnored());
        String rule = "{\"aaa\": [{\"exists\": false}], \"zzz\": [" + WILDCARD_OF_COMPLEXITY_7 + "]}";
        for (boolean additionalNameStateReuse : new boolean[] { false, true }) {
            assertEquals("subclass ignoring, additionalNameStateReuse=" + additionalNameStateReuse, 0,
                    complexityOfRule(rule, additionalNameStateReuse, subclassIgnoring));
        }
    }

    @Test
    public void testComplexityBehindAbsentKeysIgnoredRestoresPreviousEvaluation() throws Exception {
        // Keys sort as aaa, zzz, so the wildcard machine on zzz sits behind aaa's absent-key pattern: the default
        // evaluation counts it, the pre-2.1.0 evaluation never reached it.
        assertComplexityUnderBothReadings(evaluator,
                "{\"aaa\": [{\"exists\": false}], \"zzz\": [" + WILDCARD_OF_COMPLEXITY_7 + "]}", 7, 0);
        // "abcdef" is matched by 2 wildcard prefixes: "abc*" and "abc*def".
        assertComplexityUnderBothReadings(evaluator,
                "{\"aaa\": [{\"exists\": false}], \"zzz\": [{\"wildcard\": \"abc*def\"}]}", 2, 0);
        assertComplexityUnderBothReadings(evaluator,
                "{\"aaa\": {\"inner\": [{\"exists\": false}]}, \"zzz\": [" + WILDCARD_OF_COMPLEXITY_7 + "]}", 7, 0);
        // Without an absent-key pattern both evaluations walk the same machines.
        assertComplexityUnderBothReadings(evaluator, "{\"zzz\": [" + WILDCARD_OF_COMPLEXITY_7 + "]}", 7, 7);
        // The absent key sorts after the wildcard key, so the machine sits ahead of the edge the evaluations differ on.
        assertComplexityUnderBothReadings(evaluator,
                "{\"zzz\": [{\"exists\": false}], \"aaa\": [" + WILDCARD_OF_COMPLEXITY_7 + "]}", 7, 7);
        // A wildcard machine on each side of the absent key: the pre-2.1.0 evaluation stops at the absent-key edge
        // and reports the machine ahead of it ("aa" is matched by "*", "*a", "*a*"); the default walks past the edge.
        assertComplexityUnderBothReadings(evaluator, "{\"aaa\": [{\"wildcard\": \"*a*\"}], "
                + "\"mmm\": [{\"exists\": false}], \"zzz\": [" + WILDCARD_OF_COMPLEXITY_7 + "]}", 7, 3);
        // Only machines reachable through nothing but an absent-key edge are ignored: aaa is absent OR "x" here, so
        // the machine on zzz is also reachable through aaa's value and both evaluations count it.
        assertComplexityUnderBothReadings(evaluator,
                "{\"aaa\": [\"x\", {\"exists\": false}], \"zzz\": [" + WILDCARD_OF_COMPLEXITY_7 + "]}", 7, 7);
    }

    @Test
    public void testComplexityBehindAbsentKeysIgnoredRespectsMaxComplexity() throws Exception {
        // "aaaaaa" is matched by 13 prefixes of "*a*a*a*a*a*a*"; a cap of 11 stops the walk at 11.
        String wildcardOfComplexity13 = "{\"wildcard\": \"*a*a*a*a*a*a*\"}";
        MachineComplexityEvaluator cappedAt11 = new MachineComplexityEvaluator(11);
        assertComplexityUnderBothReadings(cappedAt11,
                "{\"aaa\": [{\"exists\": false}], \"zzz\": [" + wildcardOfComplexity13 + "]}", 11, 0);
        assertComplexityUnderBothReadings(cappedAt11, "{\"zzz\": [" + wildcardOfComplexity13 + "]}", 11, 11);
    }

    @Test
    public void testComplexityBehindAbsentKeysIgnoredDoesNotAffectMatching() throws Exception {
        // The setting lives on the evaluator and is read only while evaluating complexity, so matching cannot depend
        // on it; this pins that evaluating a machine under either evaluation leaves its matches unchanged.
        String[] rules = {
                "{\"aaa\": [{\"exists\": false}], \"zzz\": [" + WILDCARD_OF_COMPLEXITY_7 + "]}",
                "{\"zzz\": [" + WILDCARD_OF_COMPLEXITY_7 + "]}",
                "{\"aaa\": [{\"exists\": false}], \"zzz\": [{\"wildcard\": \"abc*def\"}]}",
                "{\"zzz\": [{\"exists\": false}], \"aaa\": [" + WILDCARD_OF_COMPLEXITY_7 + "]}",
        };
        String[] events = {
                "{\"zzz\": \"aaa\"}",
                "{\"aaa\": 1, \"zzz\": \"aaa\"}",
                "{\"zzz\": \"abcdef\"}",
                "{\"aaa\": \"aaa\"}",
                "{\"aaa\": \"aaa\", \"zzz\": 1}",
        };
        // Which events each rule matches, in the order of the events above.
        boolean[][] expectedMatches = {
                { true, false, false, false, false },
                { true, true, false, false, false },
                { false, false, true, false, false },
                { false, false, false, true, false },
        };
        for (int i = 0; i < rules.length; i++) {
            Machine machine = Machine.builder().build();
            machine.addRule("rule", rules[i]);
            List<Boolean> before = matches(machine, events);
            for (int j = 0; j < events.length; j++) {
                assertEquals(rules[i] + " on " + events[j], expectedMatches[i][j], before.get(j));
            }
            machine.evaluateComplexity(evaluator);
            machine.evaluateComplexity(evaluator.withComplexityBehindAbsentKeysIgnored(true));
            assertEquals("matches after evaluating " + rules[i], before, matches(machine, events));
        }
    }

    private static List<Boolean> matches(Machine machine, String[] events) throws Exception {
        List<Boolean> matches = new ArrayList<>();
        for (String event : events) {
            matches.add(!machine.rulesForJSONEvent(event).isEmpty());
        }
        return matches;
    }

    /**
     * Asserts the complexity of a single-rule machine under the given evaluator and under the same evaluator with the
     * complexity behind absent keys ignored, in both additionalNameStateReuse configurations.
     */
    private void assertComplexityUnderBothReadings(MachineComplexityEvaluator base, String rule,
                                                   int expectedByDefault, int expectedWhenIgnored) throws Exception {
        MachineComplexityEvaluator ignoring = base.withComplexityBehindAbsentKeysIgnored(true);
        for (boolean additionalNameStateReuse : new boolean[] { false, true }) {
            String configuration = " (cap " + base.getMaxComplexity() + ", additionalNameStateReuse="
                    + additionalNameStateReuse + ")";
            assertEquals("default evaluation of " + rule + configuration, expectedByDefault,
                    complexityOfRule(rule, additionalNameStateReuse, base));
            assertEquals("complexity behind absent keys ignored for " + rule + configuration, expectedWhenIgnored,
                    complexityOfRule(rule, additionalNameStateReuse, ignoring));
        }
    }

    private int complexityOfRule(String rule, boolean additionalNameStateReuse) throws Exception {
        return complexityOfRule(rule, additionalNameStateReuse, evaluator);
    }

    private int complexityOfRule(String rule, boolean additionalNameStateReuse,
                                 MachineComplexityEvaluator complexityEvaluator) throws Exception {
        Machine machine = new Machine.Builder().withAdditionalNameStateReuse(additionalNameStateReuse).build();
        machine.addRule("rule", rule);
        return machine.evaluateComplexity(complexityEvaluator);
    }

    private void testPatternPermutations(int expectedComplexity, Patterns ... patterns) {
        ByteMachine machine = new ByteMachine();
        List<Patterns[]> patternPermutations = generateAllPermutations(patterns);
        for (Patterns[] patternPermutation : patternPermutations) {
            for (Patterns pattern : patternPermutation) {
                machine.addPattern(pattern);
            }
            assertEquals(expectedComplexity, machine.evaluateComplexity(evaluator));
            for (Patterns pattern : patternPermutation) {
                machine.deletePattern(pattern);
            }
            assertTrue(machine.isEmpty());
        }
    }
}
