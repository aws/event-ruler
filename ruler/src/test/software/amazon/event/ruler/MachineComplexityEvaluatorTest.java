package software.amazon.event.ruler;

import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import static software.amazon.event.ruler.PermutationsGenerator.generateAllPermutations;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * For each test, for illustrative purposes, I will provide one input string that results in the maximum number of
 * matching wildcard rule prefixes. Note there may be other input strings that achieve the same number of matching
 * wildcard rule prefixes. However, there are no input strings that result in a higher number of wildcard rule prefixes
 * (try to find one if you'd like).
 */
public class MachineComplexityEvaluatorTest {

    private static final int MAX_COMPLEXITY = 100;
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
