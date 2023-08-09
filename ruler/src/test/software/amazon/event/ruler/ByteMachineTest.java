package software.amazon.event.ruler;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import software.amazon.event.ruler.input.ParseException;
import org.junit.Test;

import static software.amazon.event.ruler.PermutationsGenerator.generateAllPermutations;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ByteMachineTest {

    @Test
    public void WHEN_ManyOverLappingStringsAreAdded_THEN_TheyAreParsedProperly() {

        String[] patterns = {
                "a", "ab", "ab", "ab", "abc", "abx", "abz", "abzz", "x",
                "cafe furioso", "café",
                "Ξ\uD800\uDF46ज",
                "Ξ ज"
        };

        ByteMachine cut = new ByteMachine();
        for (String pattern : patterns) {
            cut.addPattern(Patterns.exactMatch(pattern));
        }

        for (String pattern : patterns) {
            assertFalse(cut.transitionOn(pattern).isEmpty());
        }

        String[] noMatches = {
          "foo", "bar", "baz", "cafe pacifica", "cafë"
        };
        for (String noMatch : noMatches) {
            assertTrue(cut.transitionOn(noMatch).isEmpty());
        }
    }

    @Test
    public void WHEN_AnythingButPrefixPatternIsAdded_THEN_ItMatchesAppropriately() {
        Patterns abpp = Patterns.anythingButPrefix("foo");
        ByteMachine bm = new ByteMachine();
        bm.addPattern(abpp);
        String[] shouldMatch = {
          "f",
          "fo",
          "a",
          "33"
        };
        String[] shouldNotMatch = {
          "foo",
          "foo1",
          "foossssss2sssssssssssss4ssssssssssssss5sssssssssssssss7ssssssssssssssssssssssssssssssssssssssss"
        };
        Set<NameStateWithPattern> matches;
        for (String s : shouldMatch) {
            matches = bm.transitionOn(s);
            assertEquals(1, matches.size());
        }
        for (String s : shouldNotMatch) {
            matches = bm.transitionOn(s);
            assertEquals(0, matches.size());
        }
        abpp = Patterns.anythingButPrefix("foo");
        bm.deletePattern(abpp);
        for (String s : shouldMatch) {
            matches = bm.transitionOn(s);
            assertEquals(0, matches.size());
        }

        // exercise deletePattern
        Set<String> abs = new HashSet<>();
        abs.add("foo");
        AnythingBut ab = AnythingBut.anythingButMatch(abs);

        bm.addPattern(abpp);
        Patterns[] trickyPatterns = {
                Patterns.prefixMatch("foo"),
                Patterns.anythingButPrefix("f"),
                Patterns.anythingButPrefix("fo"),
                Patterns.anythingButPrefix("fooo"),
                Patterns.exactMatch("foo"),
                ab
        };
        for (Patterns tricky : trickyPatterns) {
            bm.deletePattern(tricky);
        }

        for (String s : shouldMatch) {
            matches = bm.transitionOn(s);
            assertEquals(1, matches.size());
        }
    }

    @Test
    public void WHEN_NumericEQIsAdded_THEN_ItMatchesMultipleNumericForms() {

        ByteMachine cut = new ByteMachine();
        cut.addPattern(Patterns.numericEquals(300.0));
        String[] threeHundreds = { "300", "3.0e+2", "300.0000" };
        for (String threeHundred : threeHundreds) {
            assertEquals(1, cut.transitionOn(threeHundred).size());
        }
    }

    @Test
    public void WHEN_NumericRangesAreAdded_THEN_TheyWorkCorrectly() {
        double[] data = {
                -Constants.FIVE_BILLION, -4_999_999_999.99999, -4_999_999_999.99998, -4_999_999_999.99997,
                -999999999.99999, -999999999.99, -10000, -0.000002,
                0, 0.000001, 3.8, 2.5e4, 999999999.999998, 999999999.999999, 1628955663d, 3206792463d, 4784629263d,
                4_999_999_999.99997, 4_999_999_999.99998, 4_999_999_999.99999, Constants.FIVE_BILLION
        };

        // Orderly add rule and random delete rules
        {
            Range[] ranges = new Range[data.length-1];
            int rangeIdx = 0;
            ByteMachine cut = new ByteMachine();
            for (int i = 1; i < data.length; i++) {
                cut.addPattern(Range.lessThan(data[i]));
                ranges[rangeIdx++] = Range.lessThan(data[i]);
            }
            // shuffle the array
            List<Range> list = Arrays.asList(ranges);
            Collections.shuffle(list);
            list.toArray(ranges);

            for (Range range : ranges) {
                cut.deletePattern(range);
            }
            assertTrue("byteMachine must be empty after delete pattern", cut.isEmpty());
        }

        // first: less than
        for (int i = 1; i < data.length; i++) {
            ByteMachine cut = new ByteMachine();
            cut.addPattern(Range.lessThan(data[i]));
            for (double aData : data) {
                String num = String.format("%f", aData);
                Set<NameStateWithPattern> matched = cut.transitionOn(num);
                if (aData < data[i]) {
                    assertEquals(num + " should match < " + data[i], 1, matched.size());
                } else {
                    assertEquals(num + " should not match <" + data[i], 0, matched.size());
                }
            }
            cut.deletePattern(Range.lessThan(data[i]));
            assertTrue("byteMachine must be empty after delete pattern", cut.isEmpty());
        }

        // <=
        for (int i = 1; i < data.length; i++) {
            ByteMachine cut = new ByteMachine();
            cut.addPattern(Range.lessThanOrEqualTo(data[i]));
            for (double aData : data) {
                String num = String.format("%f", aData);
                Set<NameStateWithPattern> matched = cut.transitionOn(num);
                if (aData <= data[i]) {
                    assertEquals(num + " should match <= " + data[i], 1, matched.size());
                } else {
                    assertEquals(num + " should not match <=" + data[i], 0, matched.size());
                }
            }
            cut.deletePattern(Range.lessThanOrEqualTo(data[i]));
            assertTrue("byteMachine must be empty after delete pattern", cut.isEmpty());
        }

        // >
        for (int i = 0; i < (data.length - 1); i++) {
            ByteMachine cut = new ByteMachine();
            cut.addPattern(Range.greaterThan(data[i]));
            for (double aData : data) {
                String num = String.format("%f", aData);
                Set<NameStateWithPattern> matched = cut.transitionOn(num);
                if (aData > data[i]) {
                    assertEquals(num + " should match > " + data[i], 1, matched.size());
                } else {
                    assertEquals(num + " should not match >" + data[i], 0, matched.size());
                }
            }
            cut.deletePattern(Range.greaterThan(data[i]));
            assertTrue("byteMachine must be empty after delete pattern", cut.isEmpty());
        }

        // >=
        for (int i = 0; i < (data.length - 1); i++) {
            ByteMachine cut = new ByteMachine();
            Range nr = Range.greaterThanOrEqualTo(data[i]);
            cut.addPattern(nr);
            for (double aData : data) {
                String num = String.format("%f", aData);
                Set<NameStateWithPattern> matched = cut.transitionOn(num);
                if (aData >= data[i]) {
                    assertEquals(num + " should match > " + data[i], 1, matched.size());
                } else {
                    assertEquals(num + " should not match >" + data[i], 0, matched.size());
                }
            }
            cut.deletePattern(Range.greaterThanOrEqualTo(data[i]));
            assertTrue("byteMachine must be empty after delete pattern", cut.isEmpty());
        }

        // open/open range
        for (int i = 0; i < (data.length - 2); i++) {
            for (int j = i + 2; j < data.length; j++) {
                ByteMachine cut = new ByteMachine();
                Range r = Range.between(data[i], true, data[j], true);
                cut.addPattern(r);
                for (double aData : data) {
                    String num = String.format("%f", aData);
                    Set<NameStateWithPattern> matched = cut.transitionOn(num);
                    if (aData > data[i] && aData < data[j]) {
                        assertEquals(1, matched.size());
                    } else {
                        assertEquals(0, matched.size());
                    }
                }
                cut.deletePattern(r);
                assertTrue("byteMachine must be empty after delete pattern", cut.isEmpty());
            }
        }

        // open/closed range
        for (int i = 0; i < (data.length - 2); i++) {
            for (int j = i + 2; j < data.length; j++) {
                ByteMachine cut = new ByteMachine();
                Range r = Range.between(data[i], true, data[j], false);
                cut.addPattern(r);
                for (double aData : data) {
                    String num = String.format("%f", aData);
                    Set<NameStateWithPattern> matched = cut.transitionOn(num);
                    if (aData > data[i] && aData <= data[j]) {
                        assertEquals(1, matched.size());
                    } else {
                        assertEquals(0, matched.size());
                    }
                }
                cut.deletePattern(r);
                assertTrue("byteMachine must be empty after delete pattern", cut.isEmpty());
            }
        }

        // closed/open range
        for (int i = 0; i < (data.length - 2); i++) {
            for (int j = i + 2; j < data.length; j++) {
                ByteMachine cut = new ByteMachine();
                Range r = Range.between(data[i], false, data[j], true);
                cut.addPattern(r);
                for (double aData : data) {
                    String num = String.format("%f", aData);
                    Set<NameStateWithPattern> matched = cut.transitionOn(num);
                    if (aData >= data[i] && aData < data[j]) {
                        assertEquals(1, matched.size());
                    } else {
                        assertEquals(0, matched.size());
                    }
                }
                cut.deletePattern(r);
                assertTrue("byteMachine must be empty after delete pattern", cut.isEmpty());
            }
        }
        // closed/closed range
        for (int i = 0; i < (data.length - 2); i++) {
            for (int j = i + 2; j < data.length; j++) {
                ByteMachine cut = new ByteMachine();
                Range r = Range.between(data[i], false, data[j], false);
                cut.addPattern(r);
                for (double aData : data) {
                    String num = String.format("%f", aData);
                    Set<NameStateWithPattern> matched = cut.transitionOn(num);
                    if (aData >= data[i] && aData <= data[j]) {
                        assertEquals(1, matched.size());
                    } else {
                        assertEquals(0, matched.size());
                    }
                }
                cut.deletePattern(r);
                assertTrue("byteMachine must be empty after delete pattern", cut.isEmpty());
            }
        }

        // overlapping ranges
        int[] containedCount = new int[data.length];
        ByteMachine cut = new ByteMachine();
        for (int i = 0; i < (data.length - 2); i++) {
            for (int j = i + 2; j < data.length; j++) {
                Range r = Range.between(data[i], false, data[j], false);
                cut.addPattern(r);
                for (int k = 0; k < data.length; k++) {
                    if (data[k] >= data[i] && data[k] <= data[j]) {
                        containedCount[k]++;
                    }
                }
            }
        }
        for (int k = 0; k < data.length; k++) {
            String num = String.format("%f", data[k]);
            Set<NameStateWithPattern> matched = cut.transitionOn(num);
            assertEquals(containedCount[k], matched.size());
        }
        // delete the range
        for (int i = 0; i < (data.length - 2); i++) {
            for (int j = i + 2; j < data.length; j++) {
                Range r = Range.between(data[i], false, data[j], false);
                cut.deletePattern(r);
                for (int k = 0; k < data.length; k++) {
                    if (data[k] >= data[i] && data[k] <= data[j]) {
                        containedCount[k]--;
                    }
                }
            }
        }

        assertTrue("byteMachine must be empty after delete pattern", cut.isEmpty());
        for (int k = 0; k < data.length; k++) {
            String num = String.format("%f", data[k]);
            Set<NameStateWithPattern> matched = cut.transitionOn(num);
            assertEquals(containedCount[k], matched.size());
            assertEquals(0, matched.size());
        }

        // overlapping open ranges
        containedCount = new int[data.length];
        cut = new ByteMachine();
        for (int i = 0; i < (data.length - 2); i++) {
            for (int j = i + 2; j < data.length; j++) {
                Range r = Range.between(data[i], true, data[j], true);
                cut.addPattern(r);
                for (int k = 0; k < data.length; k++) {
                    if (data[k] > data[i] && data[k] < data[j]) {
                        containedCount[k]++;
                    }
                }
            }
        }
        for (int k = 0; k < data.length; k++) {
            String num = String.format("%f", data[k]);
            Set<NameStateWithPattern> matched = cut.transitionOn(num);
            assertEquals(containedCount[k], matched.size());
        }

        for (int i = 0; i < (data.length - 2); i++) {
            for (int j = i + 2; j < data.length; j++) {
                Range r = Range.between(data[i], true, data[j], true);
                cut.deletePattern(r);
                for (int k = 0; k < data.length; k++) {
                    if (data[k] > data[i] && data[k] < data[j]) {
                        containedCount[k]--;
                    }
                }
            }
        }
        assertTrue("byteMachine must be empty after delete pattern", cut.isEmpty());
        for (int k = 0; k < data.length; k++) {
            String num = String.format("%f", data[k]);
            Set<NameStateWithPattern> matched = cut.transitionOn(num);
            assertEquals(containedCount[k], matched.size());
            assertEquals(0, matched.size());
        }
    }

    @Test
    public void WHEN_AnExactMatchAndAPrefixMatchCoincide_THEN_TwoNameStateJumpsAreGenerated() {
        ByteMachine cut = new ByteMachine();
        cut.addPattern(Patterns.exactMatch("horse"));
        cut.addPattern(Patterns.prefixMatch("horse"));
        assertEquals(2, cut.transitionOn("horse").size());
        assertEquals(1, cut.transitionOn("horseback").size());
    }

    @Test
    public void WHEN_TheSamePatternIsAddedTwice_THEN_ItOnlyCausesOneNamestateJump() {

        ByteMachine cut = new ByteMachine();
        cut.addPattern(Patterns.exactMatch("foo"));
        cut.addPattern(Patterns.exactMatch("foo"));

        Set<NameStateWithPattern> l = cut.transitionOn("foo");
        assertEquals(1, l.size());
    }

    @Test
    public void WHEN_AnyThingButPatternsAreAdded_THEN_TheyWork() {

        ByteMachine cut = new ByteMachine();
        cut.addPattern(Patterns.anythingButMatch("foo"));
        String[] notFoos = { "bar", "baz", "for", "too", "fro", "fo", "foobar" };
        for (String notFoo : notFoos) {
            assertEquals(1, cut.transitionOn(notFoo).size());
        }
        assertEquals(0, cut.transitionOn("foo").size());
    }

    @Test
    public void WHEN_AnyThingButStringListPatternsAreAdded_THEN_TheyWork() {

        ByteMachine cut = new ByteMachine();
        cut.addPattern(Patterns.anythingButMatch(new HashSet<>(Arrays.asList("bab","ford"))));
        String[] notFoos = { "bar", "baz", "for", "too", "fro", "fo", "foobar" };
        for (String notFoo : notFoos) {
            assertEquals(1, cut.transitionOn(notFoo).size());
        }
        assertEquals(0, cut.transitionOn("bab").size());
        assertEquals(0, cut.transitionOn("ford").size());
    }

    @Test
    public void WHEN_AnyThingButNumberListPatternsAreAdded_THEN_TheyWork() {

        ByteMachine cut = new ByteMachine();
        cut.addPattern(Patterns.anythingButNumberMatch(
                new HashSet<>(Arrays.asList(1.11, 2d))));
        cut.addPattern(Patterns.anythingButNumberMatch(
                new HashSet<>(Arrays.asList(3.33, 2d))));
        String[] notFoos = { "0", "1.1", "5", "9", "112", "fo", "foobar" };
        for (String notFoo : notFoos) {
            assertEquals(2, cut.transitionOn(notFoo).size());
        }

        assertEquals(1, cut.transitionOn("1.11").size());
        assertEquals(1, cut.transitionOn("3.33").size());
        assertEquals(2, cut.transitionOn("1").size());
        assertEquals(0, cut.transitionOn("2").size());

        cut.deletePattern(Patterns.anythingButNumberMatch(
                new HashSet<>(Arrays.asList(1.11, 2d))));
        for (String notFoo : notFoos) {
            assertEquals(1, cut.transitionOn(notFoo).size());
        }
        assertEquals(1, cut.transitionOn("1.11").size());
        assertEquals(0, cut.transitionOn("3.33").size());
        assertEquals(1, cut.transitionOn("1").size());
        assertEquals(0, cut.transitionOn("2").size());
        assertFalse(cut.isEmpty());

        cut.deletePattern(Patterns.anythingButNumberMatch(
                new HashSet<>(Arrays.asList(3.33, 2d))));
        for (String notFoo : notFoos) {
            assertEquals(0, cut.transitionOn(notFoo).size());
        }
        assertEquals(0, cut.transitionOn("1.11").size());
        assertEquals(0, cut.transitionOn("3.33").size());
        assertEquals(0, cut.transitionOn("1").size());
        assertEquals(0, cut.transitionOn("2").size());
        assertTrue(cut.isEmpty());

        cut.addPattern(Patterns.anythingButNumberMatch(
                new HashSet<>(Arrays.asList(3.33, 2d))));
        assertEquals(0, cut.transitionOn("2").size());
        assertEquals(0, cut.transitionOn("3.33").size());
        assertEquals(1, cut.transitionOn("2022").size());
        assertEquals(1, cut.transitionOn("400").size());
    }

    @Test
    public void WHEN_MixedPatternsAreAdded_THEN_TheyWork() {


        ByteMachine cut = new ByteMachine();
        Patterns p = Patterns.exactMatch("foo");

        cut.addPattern(p);
        p = Patterns.prefixMatch("foo");
        cut.addPattern(p);
        p = Patterns.anythingButMatch("foo");
        cut.addPattern(p);

        p = Patterns.numericEquals(3);
        cut.addPattern(p);
        p = Range.lessThan(3);
        cut.addPattern(p);
        p = Range.greaterThan(3);
        cut.addPattern(p);
        p = Range.lessThanOrEqualTo(3);
        cut.addPattern(p);
        p = Range.greaterThanOrEqualTo(3);
        cut.addPattern(p);
        p = Range.between(0, false, 8, false);
        cut.addPattern(p);
        p = Range.between(0, true, 8, true);
        cut.addPattern(p);

        String[] notFoos =    { "bar", "baz", "for", "too", "fro", "fo", "foobar" };
        int[] notFooMatches = { 1,     1,      1,    1,      1,     1,    2 };
        for (int i = 0; i < notFooMatches.length; i++) {
            assertEquals(notFooMatches[i], cut.transitionOn(notFoos[i]).size());
        }
        assertEquals(2, cut.transitionOn("foo").size());

        String[] numbers = { "3", "2.5", "3.5", "0", "8", "-1", "9" };
        int[] numMatches = {  6,     5,     5,   4,   4,    3,   3 } ;
        for (int i = 0; i < numbers.length; i++) {
            assertEquals("numbers[" + i + "]=" + numbers[i], numMatches[i], cut.transitionOn(numbers[i]).size());
        }
    }

    @Test
    public void WHEN_AnythingButIsAPrefixOfAnotherPattern_THEN_TheyWork() {

        ByteMachine cut = new ByteMachine();
        cut.addPattern(Patterns.exactMatch("football"));
        cut.addPattern(Patterns.anythingButMatch("foo"));

        assertEquals(2, cut.transitionOn("football").size());
        assertEquals(0, cut.transitionOn("foo").size());
    }

    @Test
    public void WHEN_NumericEQIsRequested_THEN_DifferentNumberSyntaxesMatch() {
        ByteMachine cut = new ByteMachine();
        cut.addPattern(Patterns.numericEquals(300.0));
        assertEquals(1, cut.transitionOn("300").size());
        assertEquals(1, cut.transitionOn("300.0000").size());
        assertEquals(1, cut.transitionOn("3.0e+2").size());
    }

    @Test
    public void RangePatternEqual() {
        double[] data = {
                1234, 5678.1234, 7890
        };

        NameState ns = new NameState();
        ByteMatch cut = new ByteMatch(Range.lessThan(data[0]), ns);
        ByteMatch cc = new ByteMatch(Range.lessThan(data[1]), ns);
        ByteMatch s2 = new ByteMatch(Range.lessThan(data[2]), ns);

        Range range1 = (Range) cut.getPattern().clone();
        Range range2 = (Range) cc.getPattern().clone();
        Range range3 = (Range) s2.getPattern().clone();

        assertEquals("pattern match range.", range1, cut.getPattern());
        assertEquals("pattern match range.", range2, cc.getPattern());
        assertEquals("pattern match range.", range3, s2.getPattern());

        assertNotSame("pattern object doesn't match range.", range1, (cut.getPattern()));
        assertNotSame("pattern object doesn't match range", range2, (cc.getPattern()));
        assertNotSame("pattern object doesn't match range", range3, (s2.getPattern()));
    }

    @Test
    public void WHEN_NumericRangesAdded_THEN_TheyWorkCorrectly_THEN_MatchNothing_AFTER_Removed() {

        ByteMachine cut = new ByteMachine();
        Range r = Range.between(0, true, 4, false);
        Range r1 = Range.between(1, true, 3, false);
        cut.addPattern(r);
        cut.addPattern(r1);

        String num = String.format("%f", 2.0);
        assertEquals(2, cut.transitionOn(num).size());
        cut.deletePattern(r);
        assertEquals(1, cut.transitionOn(num).size());
        cut.deletePattern(r1);
        assertEquals(0, cut.transitionOn(num).size());
        assertTrue("byteMachine must be empty after delete pattern", cut.isEmpty());

    }

    @Test
    public void WHEN_NumericRangesAddedMultipleTime_BecomeEmptyWithOneDelete() {
        ByteMachine cut = new ByteMachine();
        String num = String.format("%f", 2.0);
        Range r = Range.between(0, true, 4, false);
        Range r1 = (Range) r.clone();
        Range r2 = (Range) r1.clone();
        Range r3 = (Range) r2.clone();

        assertEquals(0, cut.transitionOn(num).size());
        cut.addPattern(r);
        assertEquals(1, cut.transitionOn(num).size());
        cut.addPattern(r1);
        cut.addPattern(r2);

        assertNotNull("must find the range pattern", cut.findPattern(r1));
        assertEquals(cut.findPattern(r1), cut.findPattern(r3));

        assertEquals(1, cut.transitionOn(num).size());
        cut.deletePattern(r3);
        assertEquals(0, cut.transitionOn(num).size());
        assertTrue("byteMachine must be empty after delete pattern", cut.isEmpty());
        cut.deletePattern(r);
        assertTrue("byteMachine must be empty after delete pattern", cut.isEmpty());
    }

    @Test
    public void WHEN_PatternAdded_THEN_ItCouldBeFound_AndReturnNullForOtherPatternSearch() {

        // Range pattern
        double[] data = {
                -Constants.FIVE_BILLION, -4_999_999_999.99999, -4_999_999_999.99998, -4_999_999_999.99997,
                -999999999.99999, -999999999.99, -10000, -0.000002,
                0, 0.000001, 3.8, 2.5e4, 999999999.999998, 999999999.999999,
                4_999_999_999.99997, 4_999_999_999.99998, 4_999_999_999.99999, Constants.FIVE_BILLION
        };

        ByteMachine cut = new ByteMachine();
        Range r = Range.between(0, true, 4, false);
        assertNull("must NOT find the range pattern", cut.findPattern(r));
        cut.addPattern(r);
        assertNotNull("must find the range pattern", cut.findPattern(r));

        for (int i = 0; i < 1000; i++) {
            for (int j = i + 1; j < 1001; j++) {
                if (i == 0 && j == 4) {
                    assertNotNull("must find the range pattern", cut.findPattern(Range.between(i, true, j, false)));
                } else {
                    assertNull("must NOT find the range pattern", cut.findPattern(Range.between(i, true, j, false)));
                }
            }
        }

        for (int i = 1; i < data.length; i++) {
            // first: <
            assertNull("must NOT find the range pattern", cut.findPattern(Range.lessThan(data[i])));
            // <=
            assertNull("must NOT find the range pattern", cut.findPattern(Range.lessThanOrEqualTo(data[i])));
        }
        for (int i = 0; i < data.length-1; i++) {
            // >
            assertNull("must NOT find the range pattern", cut.findPattern(Range.greaterThan(data[i])));
            // >=
            assertNull("must NOT find the range pattern", cut.findPattern(Range.greaterThanOrEqualTo(data[i])));
        }

        for (int i = 0; i < (data.length - 2); i++) {
            for (int j = i + 2; j < data.length; j++) {
                // open/open range
                assertNull("must NOT find the range pattern", cut.findPattern(Range.between(data[i], true, data[j], true)));
                // open/closed range
                assertNull("must NOT find the range pattern", cut.findPattern(Range.between(data[i], true, data[j], false)));
            }
        }

        for (int i = 0; i < (data.length - 2); i++) {
            for (int j = i + 2; j < data.length; j++) {
                // overlap range
                assertNull("must NOT find the range pattern", cut.findPattern(Range.between(data[i], true, data[j], true)));
            }
        }

        // other pattern
        cut.addPattern(Patterns.exactMatch("test"));
        cut.addPattern(Patterns.prefixMatch("test"));
        cut.addPattern(Patterns.anythingButMatch("test"));
        cut.addPattern(Patterns.numericEquals(1.11));
        assertNotNull("must find the pattern", cut.findPattern(Patterns.exactMatch("test")));
        assertNotNull("must find the pattern", cut.findPattern(Patterns.prefixMatch("test")));
        assertNotNull("must find the pattern", cut.findPattern(Patterns.anythingButMatch("test")));
        assertNotNull("must find the pattern", cut.findPattern(Patterns.numericEquals(1.11)));

        assertNull("must NOT find the pattern", cut.findPattern(Patterns.exactMatch("test1")));
        assertNull("must NOT find the pattern", cut.findPattern(Patterns.prefixMatch("test1")));
        assertNull("must NOT find the pattern", cut.findPattern(Patterns.anythingButMatch("test1")));
        assertNull("must NOT find the pattern", cut.findPattern(Patterns.numericEquals(1.111)));

        cut.deletePattern(Patterns.exactMatch("test"));
        cut.deletePattern(Patterns.prefixMatch("test"));
        cut.deletePattern(Patterns.anythingButMatch("test"));
        cut.deletePattern(Patterns.numericEquals(1.11));
        cut.deletePattern(Range.between(0, true, 4, false));
        assertTrue("cut is empty", cut.isEmpty());
    }

    @Test
    public void whenKeyExistencePatternAdded_itCouldBeFound_AndBecomesEmptyWithOneDelete() {
        ByteMachine cut = new ByteMachine();

        cut.addPattern(Patterns.existencePatterns());

        NameState stateFound;

        cut.deletePattern(Patterns.existencePatterns());

        stateFound = cut.findPattern(Patterns.existencePatterns());
        assertNull(stateFound);

        assertTrue(cut.isEmpty());
    }

    @Test
    public void testExistencePatternFindsMatch() {
        ByteMachine cut = new ByteMachine();

        cut.addPattern(Patterns.existencePatterns());

        Set<NameStateWithPattern> matches = cut.transitionOn("someValue");
        assertEquals(1, matches.size());

        cut.deletePattern(Patterns.existencePatterns());
        assertTrue(cut.isEmpty());
    }

    @Test
    public void testIfExistencePatternIsNotAdded_itDoesNotFindMatch() {
        ByteMachine cut = new ByteMachine();

        Set<NameStateWithPattern> matches = cut.transitionOn("someValue");
        assertEquals(0, matches.size());

        assertTrue(cut.isEmpty());
    }

    @Test
    public void testExistencePattern_WithOtherPatterns_workCorrectly() {
        ByteMachine cut = new ByteMachine();
        String val = "value";

        cut.addPattern(Patterns.existencePatterns());
        cut.addPattern(Patterns.exactMatch(val));

        Set<NameStateWithPattern> matches = cut.transitionOn("anotherValue");
        assertEquals(1, matches.size());

        matches = cut.transitionOn(val);
        assertEquals(2, matches.size());

        cut.deletePattern(Patterns.existencePatterns());
        matches = cut.transitionOn("anotherValue");
        assertEquals(0, matches.size());

        matches = cut.transitionOn(val);
        assertEquals(1, matches.size());

        cut.deletePattern(Patterns.exactMatch(val));
        matches = cut.transitionOn(val);
        assertEquals(0, matches.size());
    }

    @Test
    public void testNonNumericValue_DoesNotMatchNumericPattern() {
        ByteMachine cut = new ByteMachine();
        String val = "0A,";
        cut.addPattern(Range.greaterThanOrEqualTo(-1e9));

        Set<NameStateWithPattern> matches = cut.transitionOn(val);
        assertTrue(matches.isEmpty());
    }

    @Test
    public void testExistencePattern_startingWithDesignatedByteString_WithOtherPatterns_workCorrectly() {
        ByteMachine cut = new ByteMachine();
        String val = "value";

        cut.addPattern(Patterns.existencePatterns());
        cut.addPattern(Patterns.exactMatch(val));

        Set<NameStateWithPattern> matches = cut.transitionOn("NewValue");
        assertEquals(1, matches.size());

        matches = cut.transitionOn(val);
        assertEquals(2, matches.size());

        cut.deletePattern(Patterns.existencePatterns());
        matches = cut.transitionOn("NewValue");
        assertEquals(0, matches.size());

        matches = cut.transitionOn(val);
        assertEquals(1, matches.size());

        cut.deletePattern(Patterns.exactMatch(val));
        matches = cut.transitionOn(val);
        assertEquals(0, matches.size());
    }

    @Test
    public void WHEN_ShortcutTransAddedAndDeleted_THEN_TheyWorkCorrectly() {
        ByteMachine cut = new ByteMachine();
        String [] values = {"a", "ab", "abc", "abcd", "a", "ab", "abc", "abcd", "ac", "acb", "aabc", "abcdeffg"};
        // add them in ordering, no proxy transition is reburied.
        for (String value : values) {
            cut.addPattern(Patterns.exactMatch(value));
        }
        for (String value :values) {
            assertEquals(value + " did not have a match", 1, cut.transitionOn(value).size());
        }
        for (String value : values) {
            cut.deletePattern(Patterns.exactMatch(value));
        }
        assertTrue(cut.isEmpty());

        // add them in reverse ordering, there is only one proxy transition existing.
        for (int i = values.length-1; i >=0; i--) {
            cut.addPattern(Patterns.exactMatch(values[i]));
        }
        for (String value :values) {
            assertEquals(1, cut.transitionOn(value).size());
        }
        for (String value : values) {
            cut.deletePattern(Patterns.exactMatch(value));
        }
        assertTrue(cut.isEmpty());

        String[] copiedValues = values.clone();
        for (int t = 50; t > 0; t--) {
            //add them in random order, it should work
            randomizeArray(copiedValues);
            for (int i = values.length - 1; i >= 0; i--) {
                cut.addPattern(Patterns.exactMatch(copiedValues[i]));
            }
            for (String value : values) {
                assertEquals(1, cut.transitionOn(value).size());
            }
            randomizeArray(copiedValues);
            for (String value : copiedValues) {
                cut.deletePattern(Patterns.exactMatch(value));
            }
            assertTrue(cut.isEmpty());
        }

        // test exactly match and prefix together
        for (int t = 50; t > 0; t--) {
            //add them in random order, it should work
            randomizeArray(copiedValues);
            // add them in ordering, both exactly match and prefix match
            for (String value : copiedValues) {
                cut.addPattern(Patterns.exactMatch(value));
            }
            randomizeArray(copiedValues);
            // add them in ordering, both exactly match and prefix match
            for (String value : copiedValues) {
                cut.addPattern(Patterns.prefixMatch(value));
            }
            int[] expected = {2, 3, 4, 5, 2, 3, 4, 5, 3, 4, 3, 6};
            for (int i = 0; i < values.length; i++) {
                assertEquals(expected[i], cut.transitionOn(values[i]).size());
            }
            randomizeArray(copiedValues);
            for (String value : copiedValues) {
                cut.deletePattern(Patterns.exactMatch(value));
                cut.deletePattern(Patterns.prefixMatch(value));
            }
            assertTrue(cut.isEmpty());
        }

    }

    private static void randomizeArray(String[] array){
        Random rgen = new Random();  // Random number generator
        for (int i=0; i<array.length; i++) {
            int randomPosition = rgen.nextInt(array.length);
            String temp = array[i];
            array[i] = array[randomPosition];
            array[randomPosition] = temp;
        }
    }

    @Test
    public void testSuffixPattern() {
        ByteMachine cut = new ByteMachine();
        cut.addPattern(Patterns.suffixMatch("java"));
        String[] shouldBeMatched = { "java", ".java", "abc.java", "jjjava", "123java" };
        String[] shouldNOTBeMatched = { "vav", "javaa", "xjavax", "foo", "foojaoova" };
        for (String foo : shouldBeMatched) {
            assertEquals(1, cut.transitionOn(foo).size());
        }
        for (String notFoo : shouldNOTBeMatched) {
            assertEquals(0, cut.transitionOn(notFoo).size());
        }
    }

    @Test
    public void testEqualsIgnoreCasePattern() {
        String[] noMatches = new String[] { "JAV", "jav", "ava", "AVA", "JAVAx", "javax", "xJAVA", "xjava" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.equalsIgnoreCaseMatch("jAVa"),
                        "java", "JAVA", "Java", "jAvA", "jAVa", "JaVa")
        );
    }

    @Test
    public void testEqualsIgnoreCasePatternWithExactMatchAsPrefix() {
        String[] noMatches = new String[] { "", "ja", "JA", "JAV", "javax" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.equalsIgnoreCaseMatch("jAVa"),
                        "java", "jAVa", "JavA", "JAVA"),
                new PatternMatch(Patterns.exactMatch("ja"),
                        "ja")
        );
    }

    @Test
    public void testEqualsIgnoreCasePatternWithExactMatchAsPrefixLengthOneLess() {
        String[] noMatches = new String[] { "", "JAV", "javax" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.equalsIgnoreCaseMatch("jAVa"),
                        "java", "jAVa", "JavA", "JAVA"),
                new PatternMatch(Patterns.exactMatch("jav"),
                        "jav")
        );
    }

    @Test
    public void testEqualsIgnoreCasePatternNonLetterCharacters() {
        String[] noMatches = new String[] { "", "2#$^sS我ŐaBc", "1#%^sS我ŐaBc", "1#$^sS大ŐaBc", "1#$^sS我ŏaBc" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.equalsIgnoreCaseMatch("1#$^sS我ŐaBc"),
                        "1#$^sS我ŐaBc", "1#$^Ss我ŐAbC")
        );
    }

    @Test
    public void testEqualsIgnoreCaseLowerCaseCharacterWithDifferentByteLengthForUpperCase() {
        String[] noMatches = new String[] { "", "12a34", "12A34" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.equalsIgnoreCaseMatch("12ⱥ34"),
                        "12ⱥ34", "12Ⱥ34")
        );
    }

    @Test
    public void testEqualsIgnoreCaseUpperCaseCharacterWithDifferentByteLengthForLowerCase() {
        String[] noMatches = new String[] { "", "12a34", "12A34" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.equalsIgnoreCaseMatch("12Ⱥ34"),
                        "12ⱥ34", "12Ⱥ34")
        );
    }

    @Test
    public void testEqualsIgnoreCaseLowerCaseCharacterWithDifferentByteLengthForUpperCaseAtStartOfString() {
        String[] noMatches = new String[] { "", "a12", "A12" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.equalsIgnoreCaseMatch("ⱥ12"),
                        "ⱥ12", "Ⱥ12")
        );
    }

    @Test
    public void testEqualsIgnoreCaseUpperCaseCharacterWithDifferentByteLengthForLowerCaseAtStartOfString() {
        String[] noMatches = new String[] { "", "a12", "A12" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.equalsIgnoreCaseMatch("Ⱥ12"),
                        "ⱥ12", "Ⱥ12")
        );
    }

    @Test
    public void testEqualsIgnoreCaseLowerCaseCharacterWithDifferentByteLengthForUpperCaseAtEndOfString() {
        String[] noMatches = new String[] { "", "12a", "12A" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.equalsIgnoreCaseMatch("12ⱥ"),
                        "12ⱥ", "12Ⱥ")
        );
    }

    @Test
    public void testEqualsIgnoreCaseUpperCaseCharacterWithDifferentByteLengthForLowerCaseAtEndOfString() {
        String[] noMatches = new String[] { "", "12a", "12A" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.equalsIgnoreCaseMatch("12Ⱥ"),
                        "12ⱥ", "12Ⱥ")
        );
    }

    @Test
    public void testEqualsIgnoreCaseManyCharactersWithDifferentByteLengthForLowerCaseAndUpperCase() {
        String[] noMatches = new String[] { "", "Ϋ́ȿⱯΐΫ́Η͂k", "ΰⱾɐΪ́ΰῆK" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.equalsIgnoreCaseMatch("ΰɀɐΐΰῆK"),
                        "Ϋ́ɀⱯΐΫ́Η͂k", "ΰⱿɐΪ́ΰῆK", "Ϋ́ⱿⱯΪ́Ϋ́ῆk", "ΰɀɐΐΰΗ͂K")
        );
    }

    @Test
    public void testEqualsIgnoreCaseMiddleCharacterWithDifferentByteLengthForLowerCaseAndUpperCaseWithPrefixMatches() {
        String[] noMatches = new String[] { "", "a", "aa" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.equalsIgnoreCaseMatch("abȺcd"),
                        "abⱥcd", "abȺcd"),
                new PatternMatch(Patterns.prefixMatch("ab"),
                        "ab", "abⱥ", "abȺ", "abⱥcd", "abȺcd"),
                new PatternMatch(Patterns.prefixMatch("abⱥ"),
                        "abⱥ", "abⱥcd"),
                new PatternMatch(Patterns.prefixMatch("abȺ"),
                        "abȺ", "abȺcd")
        );
    }

    @Test
    public void testEqualsIgnoreCaseLastCharacterWithDifferentByteLengthForLowerCaseAndUpperCaseWithPrefixMatches() {
        String[] noMatches = new String[] { "", "ab" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.equalsIgnoreCaseMatch("abcȺ"),
                        "abcⱥ", "abcȺ"),
                new PatternMatch(Patterns.prefixMatch("abc"),
                        "abc", "abca", "abcA", "abcⱥ", "abcȺ"),
                new PatternMatch(Patterns.prefixMatch("abca"),
                        "abca"),
                new PatternMatch(Patterns.prefixMatch("abcA"),
                        "abcA")
        );
    }

    @Test
    public void testEqualsIgnoreCaseFirstCharacterWithDifferentByteLengthForCasesWithLowerCasePrefixMatch() {
        String[] noMatches = new String[] { "", "ⱥ", "Ⱥ", "c" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.equalsIgnoreCaseMatch("ⱥc"),
                        "ⱥc", "Ⱥc"),
                new PatternMatch(Patterns.prefixMatch("ⱥc"),
                        "ⱥc", "ⱥcd")
        );
    }

    @Test
    public void testEqualsIgnoreCaseFirstCharacterWithDifferentByteLengthForCasesWithUpperCasePrefixMatch() {
        String[] noMatches = new String[] { "", "ⱥ", "Ⱥ", "c" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.equalsIgnoreCaseMatch("Ⱥc"),
                        "ⱥc", "Ⱥc"),
                new PatternMatch(Patterns.prefixMatch("Ⱥc"),
                        "Ⱥc", "Ⱥcd")
        );
    }

    @Test
    public void testEqualsIgnoreCaseWhereLowerAndUpperCaseAlreadyExist() {
        String[] noMatches = new String[] { "", "a", "b" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.prefixMatch("ab"),
                        "ab", "abc", "abC"),
                new PatternMatch(Patterns.prefixMatch("AB"),
                        "AB", "ABC", "ABc"),
                new PatternMatch(Patterns.equalsIgnoreCaseMatch("ab"),
                        "ab", "AB", "Ab", "aB")
        );
    }

    @Test
    public void testEqualsIgnoreCasePatternMultipleWithMultipleExactMatch() {
        String[] noMatches = new String[] { "", "he", "HEL", "hell", "HELL", "helloxx" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.equalsIgnoreCaseMatch("hElLo"),
                        "hello", "HELLO", "HeLlO", "hElLo"),
                new PatternMatch(Patterns.equalsIgnoreCaseMatch("HeLlOX"),
                        "hellox", "HELLOX", "HeLlOx", "hElLoX"),
                new PatternMatch(Patterns.exactMatch("hello"),
                        "hello"),
                new PatternMatch(Patterns.exactMatch("HELLO"),
                        "HELLO"),
                new PatternMatch(Patterns.exactMatch("hel"),
                        "hel")
        );
    }

    @Test
    public void testEqualsIgnoreCaseWithExactMatchLeadingCharacterSameLowerAndUpperCase() {
        String[] noMatches = new String[] { "", "!", "!A", "a", "A", "b", "B" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.equalsIgnoreCaseMatch("!b"),
                        "!b", "!B"),
                new PatternMatch(Patterns.exactMatch("!a"),
                        "!a")
        );
    }

    @Test
    public void testWildcardSingleWildcardCharacter() {
        testPatternPermutations(
                new PatternMatch(Patterns.wildcardMatch("*"),
                        "", "*", "h", "hello")
        );
    }

    @Test
    public void testWildcardLeadingWildcardCharacter() {
        String[] noMatches = new String[] { "", "ello", "hellx", "xhellx", "hell5rHGGHo" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("*hello"),
                        "hello", "hhello", "xxxhello", "*hello", "23Őzhello")
        );
    }

    @Test
    public void testWildcardNormalPositionWildcardCharacter() {
        String[] noMatches = new String[] { "", "hlo", "hll", "hellol", "hel5rHGGHlo" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("h*llo"),
                        "hllo", "hello", "hxxxllo", "hel23Őzlllo")
        );
    }

    @Test
    public void testWildcardSecondLastCharWildcardCharacter() {
        String[] noMatches = new String[] { "", "hell", "helox", "hellox", "hel5rHGGHe" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("hel*o"),
                        "helo", "hello", "helxxxo", "hel23Őzlllo")
        );
    }

    @Test
    public void testWildcardTrailingWildcardCharacter() {
        String[] noMatches = new String[] { "", "hell", "hellx", "hellxo", "hol5rHGGHo" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("hello*"),
                        "hello", "hellox", "hellooo", "hello*", "hello23Őzlllo")
        );
    }

    @Test
    public void testWildcardMultipleWildcardCharacters() {
        String[] noMatches = new String[] { "", "ho", "heeo", "helx", "llo", "hex5rHGGHo" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("h*l*o"),
                        "hlo", "helo", "hllo", "hloo", "hello", "hxxxlxxxo", "h*l*o", "hel*o", "h*llo", "hel23Őzlllo")
        );
    }

    @Test
    public void testWildcardLastCharAndThirdLastCharWildcardCharacters() {
        String[] noMatches = new String[] { "", "he", "hex", "hexxx" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("he*l*"),
                        "hel", "hexl", "helx", "helxx", "helxl", "helxlx", "helxxl", "helxxlxx", "helxxlxxl")
        );
    }

    @Test
    public void testWildcardLastCharAndThirdLastCharWildcardCharactersForStringOfLengthThree() {
        String[] noMatches = new String[] { "", "x", "xx", "xtx" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("*l*"),
                        "l", "xl", "lx", "xlx", "xxl", "lxx", "xxlxx", "xlxlxlxlxl", "lxlxlxlxlx")
        );
    }

    @Test
    public void testWildcardEscapedAsterisk() {
        String[] noMatches = new String[] { "helo", "hello" };
        testPatternPermutations(noMatches,
                // Only one backslash in the actual string. Need to escape the backslash for Java compiler.
                new PatternMatch(Patterns.wildcardMatch("hel\\*o"),
                        "hel*o")
        );
    }

    @Test
    public void testWildcardEscapedAsteriskFollowedByWildcard() {
        String[] noMatches = new String[] { "heo", "helo", "hello", "he*l" };
        testPatternPermutations(noMatches,
                // Only one backslash in the actual string. Need to escape the backslash for Java compiler.
                new PatternMatch(Patterns.wildcardMatch("he\\**o"),
                        "he*o", "he*llo", "he*hello")
        );
    }

    @Test
    public void testWildcardEscapedBackslash() {
        String[] noMatches = new String[] { "hello", "he\\\\llo" };
        testPatternPermutations(noMatches,
                // Only two backslashes in the actual string. Need to escape the backslashes for Java compiler.
                new PatternMatch(Patterns.wildcardMatch("he\\\\llo"),
                        "he\\llo")
        );
    }

    @Test
    public void testWildcardEscapedBackslashFollowedByEscapedAsterisk() {
        String[] noMatches = new String[] { "hello", "he\\\\llo", "he\\llo", "he\\xxllo" };
        testPatternPermutations(noMatches,
                // Only three backslashes in the actual string. Need to escape the backslashes for Java compiler.
                new PatternMatch(Patterns.wildcardMatch("he\\\\\\*llo"),
                        "he\\*llo")
        );
    }

    @Test
    public void testWildcardEscapedBackslashFollowedByWildcard() {
        String[] noMatches = new String[] { "hello", "he\\ll" };
        testPatternPermutations(noMatches,
                // Only two backslashes in the actual string. Need to escape the backslashes for Java compiler.
                new PatternMatch(Patterns.wildcardMatch("he\\\\*llo"),
                        "he\\llo", "he\\*llo", "he\\\\llo", "he\\\\\\llo", "he\\xxllo")
        );
    }

    @Test
    public void testWildcardInvalidEscapeCharacter() {
        try {
            new ByteMachine().addPattern(Patterns.wildcardMatch("he\\llo"));
            fail("Expected ParseException");
        } catch (ParseException e) {
            assertEquals("Invalid escape character at pos 2", e.getMessage());
        }
    }

    @Test
    public void testWildcardSingleBackslashAllowedByExactMatch() {
        String[] noMatches = new String[] { "hello", "he\\\\llo" };
        testPatternPermutations(noMatches,
                // Only one backslash in the actual string. Need to escape the backslash for Java compiler.
                new PatternMatch(Patterns.exactMatch("he\\llo"),
                        "he\\llo")
        );
    }

    @Test
    public void testWildcardSingleWildcardCharacterWithOtherPatterns() {
        testPatternPermutations(
                new PatternMatch(Patterns.wildcardMatch("*"),
                        "", "*", "h", "ho", "hello"),
                new PatternMatch(Patterns.wildcardMatch("h*o"),
                        "ho", "hello"),
                new PatternMatch(Patterns.exactMatch("hello"),
                        "hello")
        );
    }

    @Test
    public void testWildcardLeadingWildcardCharacterNotUsedByExactMatch() {
        String[] noMatches = new String[] { "", "hello", "hellox", "blahabc" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("*hello"),
                        "hello", "xhello", "hehello"),
                new PatternMatch(Patterns.exactMatch("abc"),
                        "abc")
        );
    }

    @Test
    public void testWildcardTwoPatternsFirstLeadingSecondNormalPositionAdjacent() {
        String[] noMatches = new String[] { "", "h", "ello", "hel", "hlo", "hell" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("*hello"),
                        "hello", "xhello", "hehello"),
                new PatternMatch(Patterns.wildcardMatch("h*llo"),
                        "hllo", "hello", "hehello")
        );
    }

    @Test
    public void testWildcardTwoPatternsFirstLeadingSecondNormalPositionNonAdjacent() {
        String[] noMatches = new String[] { "", "h", "ello", "hel", "heo", "hell" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("*hello"),
                        "hello", "xhello", "hehello"),
                new PatternMatch(Patterns.wildcardMatch("he*lo"),
                        "helo", "hello", "hehello")
        );
    }

    @Test
    public void testWildcardTwoPatternsFirstLeadingSecondLastCharAndThirdLastCharAdjacent() {
        String[] noMatches = new String[] { "", "e", "l", "lo", "hel" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("*elo"),
                        "elo", "helo", "xhelo"),
                new PatternMatch(Patterns.wildcardMatch("e*l*"),
                        "el", "elo", "exl", "elx", "exlx", "exxl", "elxx", "exxlxx")
        );
    }

    @Test
    public void testWildcardTwoPatternsFirstLeadingSecondLastCharAndThirdLastCharNonAdjacent() {
        String[] noMatches = new String[] { "", "he", "hexxo", "ello" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("*hello"),
                        "hello", "xhello", "xxhello"),
                new PatternMatch(Patterns.wildcardMatch("he*l*"),
                        "hel", "hello", "helo", "hexl", "hexlx", "hexxl", "helxx", "hexxlxx")
        );
    }

    @Test
    public void testWildcardTwoPatternsFirstNormalPositionSecondNormalPositionAdjacent() {
        String[] noMatches = new String[] { "", "hlo", "heo", "hllol", "helol" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("h*llo"),
                        "hllo", "hello", "hxxxllo", "hexxxllo"),
                new PatternMatch(Patterns.wildcardMatch("he*lo"),
                        "helo", "hello", "hexxxlo", "hexxxllo")
        );
    }

    @Test
    public void testWildcardTwoPatternsFirstNormalPositionSecondNormalPositionNonAdjacent() {
        String[] noMatches = new String[] { "", "hlox", "hllo", "helo", "heox", "helx", "hellx", "helloxx", "heloxx" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("h*llox"),
                        "hllox", "hellox", "hxxxllox", "helhllox", "hheloxllox"),
                new PatternMatch(Patterns.wildcardMatch("hel*ox"),
                        "helox", "hellox", "helxxxox", "helhllox", "helhlloxox")
        );
    }

    @Test
    public void testWildcardTwoPatternsFirstNormalPositionSecondLastCharAndThirdLastCharAdjacent() {
        String[] noMatches = new String[] { "", "h", "he", "hl", "el", "hlo", "llo", "hllol", "hxll", "hexxx" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("h*llo"),
                        "hllo", "hello", "hxxxllo", "hexxxllo", "hexxxlllo"),
                new PatternMatch(Patterns.wildcardMatch("he*l*"),
                        "hel", "helo", "hexl", "hello", "helol", "hexxxlo", "hexxxllo", "hexxxlllo")
        );
    }

    @Test
    public void testWildcardTwoPatternsFirstNormalPositionSecondLastCharAndThirdLastCharNonAdjacent() {
        String[] noMatches = new String[] { "", "h", "hex", "hl", "exl", "hxlo", "xllo", "hxllol", "hxxll", "hexxx" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("h*xllo"),
                        "hxllo", "hexllo", "hxxxllo", "hexxxllo"),
                new PatternMatch(Patterns.wildcardMatch("hex*l*"),
                        "hexl", "hexlo", "hexxl", "hexllo", "hexlol", "hexxxlo", "hexxxllo", "hexxxlllo")
        );
    }

    @Test
    public void testWildcardTwoPatternsFirstNormalPositionSecondSecondLastCharAdjacent() {
        String[] noMatches = new String[] { "", "hel", "heo", "hlo", "hellxox" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("he*lo"),
                        "helo", "hello", "hexxxlo", "helxxxlo"),
                new PatternMatch(Patterns.wildcardMatch("hel*o"),
                        "helo", "hello", "hellxo", "helxxxo", "helxxxlo")
        );
    }

    @Test
    public void testWildcardTwoPatternsFirstNormalPositionSecondSecondLastCharNonAdjacent() {
        String[] noMatches = new String[] { "", "hlo", "hll", "hel", "helox" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("h*llo"),
                        "hllo", "hello", "hxxxllo", "helllo"),
                new PatternMatch(Patterns.wildcardMatch("hel*o"),
                        "helo", "hello", "helxo", "helllo")
        );
    }

    @Test
    public void testWildcardTwoPatternsFirstNormalPositionSecondTrailing() {
        String[] noMatches = new String[] { "", "he", "hel", "helox", "helx", "hxlo" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("he*lo"),
                        "helo", "hello", "helllo", "helxlo"),
                new PatternMatch(Patterns.wildcardMatch("hell*"),
                        "hell", "hello", "helllo", "hellx", "hellxxx")
        );
    }

    @Test
    public void testWildcardTwoPatternsFirstSecondLastCharSecondTrailing() {
        String[] noMatches = new String[] { "", "hel", "helox", "helxox", "hexo" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("hel*o"),
                        "helo", "hello", "helllo", "hellloo", "helloo", "heloo"),
                new PatternMatch(Patterns.wildcardMatch("hell*"),
                        "hell", "hello", "helllo", "hellloo", "helloo", "hellox")
        );
    }

    @Test
    public void testWildcardTwoPatternsBothTrailing() {
        String[] noMatches = new String[] { "", "he", "hex", "hexlo" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("hel*"),
                        "hel", "helx", "hello", "hellox"),
                new PatternMatch(Patterns.wildcardMatch("hello*"),
                        "hello", "hellox")
        );
    }

    @Test
    public void testWildcardLeadingWildcardOccursBeforeLeadingCharacterOfExactMatch() {
        String[] noMatches = new String[] { "", "ello", "hell" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("*hello"),
                        "hello", "hhello", "hhhello"),
                new PatternMatch(Patterns.exactMatch("hello"),
                        "hello")
        );
    }

    @Test
    public void testWildcardNormalPositionWildcardOccursBeforeNormalPositionCharacterOfExactMatch() {
        String[] noMatches = new String[] { "", "he", "hel", "heo", "heloz", "hellox", "heloxo" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("he*lo"),
                        "helo", "hello", "helllo"),
                new PatternMatch(Patterns.exactMatch("helox"),
                        "helox")
        );
    }

    @Test
    public void testWildcardSecondLastCharWildcardOccursBeforeNormalPositionCharacterOfExactMatch() {
        String[] noMatches = new String[] { "", "he", "helx", "helo", "hexlx", "hellox", "heloxx" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("he*l"),
                        "hel", "hexl", "hexxxl"),
                new PatternMatch(Patterns.exactMatch("helox"),
                        "helox")
        );
    }

    @Test
    public void testWildcardTrailingWildcardOccursBeforeNormalPositionCharacterOfExactMatch() {
        String[] noMatches = new String[] { "", "h", "hxlox", "hxelox" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("he*"),
                        "he", "helo", "helox", "heloxx"),
                new PatternMatch(Patterns.exactMatch("helox"),
                        "helox")
        );
    }

    @Test
    public void testWildcardMultipleWildcardOccursBeforeNormalPositionCharacterOfExactMatch() {
        String[] noMatches = new String[] { "", "h", "he", "hel", "hexxo", "hexxohexxo" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("h*l*o"),
                        "hlo", "helo", "hllo", "hello", "hexloo", "hellohello", "hellohellxo"),
                new PatternMatch(Patterns.exactMatch("hellohello"),
                        "hellohello")
        );
    }

    @Test
    public void testWildcardLastCharAndThirdLastCharWildcardOccursBeforeNormalPositionCharacterOfExactMatch() {
        String[] noMatches = new String[] { "", "h", "he", "hlo", "hexxo", "hexxohexxo" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("he*l*"),
                        "hel", "helo", "hexl", "hello", "hexloo", "hellohellx", "hellohello"),
                new PatternMatch(Patterns.exactMatch("hellohello"),
                        "hellohello")
        );
    }

    @Test
    public void testWildcardLeadingWildcardOccursBeforeSameFinalCharacterOfExactMatch() {
        String[] noMatches = new String[] { "", "x", "hx" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("*h"),
                        "h", "hh", "xhh"),
                new PatternMatch(Patterns.exactMatch("h"),
                        "h")
        );
    }

    @Test
    public void testWildcardNormalPositionWildcardOccursBeforeSameFinalCharacterOfExactMatch() {
        String[] noMatches = new String[] { "", "hel", "helx", "heoo", "helxoxo", "heloxo", "helxoox" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("hel*oo"),
                        "heloo", "helloo", "helxxoo"),
                new PatternMatch(Patterns.exactMatch("helo"),
                        "helo")
        );
    }

    @Test
    public void testWildcardNormalPositionWildcardOccursBeforeDivergentFinalCharacterOfExactMatch() {
        String[] noMatches = new String[] { "", "hel", "helo", "heoo", "helxoxo", "heloxo", "helxoox" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("hel*oo"),
                        "heloo", "helloo", "helxxoo"),
                new PatternMatch(Patterns.exactMatch("helx"),
                        "helx")
        );
    }

    @Test
    public void testWildcardSecondLastCharWildcardOccursBeforeSameFinalCharacterOfExactMatch() {
        String[] noMatches = new String[] { "", "hel", "hexlo", "helox" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("hel*o"),
                        "helo", "hello", "helxxxo"),
                new PatternMatch(Patterns.exactMatch("helo"),
                        "helo")
        );
    }

    @Test
    public void testWildcardSecondLastCharWildcardOccursBeforeDivergentFinalCharacterOfExactMatch() {
        String[] noMatches = new String[] { "", "hel", "hexl", "helo", "helxx" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("hel*o"),
                        "helo", "hello", "helxo", "helxxxo"),
                new PatternMatch(Patterns.exactMatch("helx"),
                        "helx")
        );
    }

    @Test
    public void testWildcardTrailingWildcardOccursBeforeFinalCharacterOfExactMatch() {
        String[] noMatches = new String[] { "", "hex", "hexl" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("hel*"),
                        "hel", "helo", "hello", "heloo" ),
                new PatternMatch(Patterns.exactMatch("helo"),
                        "helo")
        );
    }

    @Test
    public void testWildcardMultipleWildcardOccursBeforeSameFinalCharacterOfExactMatch() {
        String[] noMatches = new String[] { "", "ho", "hell", "ello", "hxo" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("h*l*o"),
                        "hlo", "helo", "hllo", "hello", "hxxlo", "hlxxo", "hxxlxxo"),
                new PatternMatch(Patterns.exactMatch("hello"),
                        "hello")
        );
    }

    @Test
    public void testWildcardMultipleWildcardOccursBeforeDivergentFinalCharacterOfExactMatch() {
        String[] noMatches = new String[] { "", "ho", "hell", "ello", "hxo" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("h*l*o"),
                        "hlo", "helo", "hllo", "hello", "hxxlo", "hlxxo", "hxxlxxo"),
                new PatternMatch(Patterns.exactMatch("hellx"),
                        "hellx")
        );
    }

    @Test
    public void testWildcardLastCharAndThirdLastCharWildcardOccursBeforeFinalCharacterOfExactMatch() {
        String[] noMatches = new String[] { "", "h", "l", "elo", "hex", "hexo" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("h*l*"),
                        "hl", "hel", "hlo", "helo", "heel", "hloo", "heelo", "heloo", "heeloo" ),
                new PatternMatch(Patterns.exactMatch("helo"),
                        "helo")
        );
    }

    @Test
    public void testWildcardLeadingWildcardOccursAtDivergentCharacterOfExactMatch() {
        String[] noMatches = new String[] { "", "ello", "hellobye", "bbye" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("*hello"),
                        "hello", "xhello", "byehello"),
                new PatternMatch(Patterns.exactMatch("bye"),
                        "bye")
        );
    }

    @Test
    public void testWildcardNormalPositionWildcardOccursAtDivergentCharacterOfExactMatch() {
        String[] noMatches = new String[] { "", "hel", "heo", "hexo", "hexzz" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("he*lo"),
                        "helo", "hello", "hexxxlo"),
                new PatternMatch(Patterns.exactMatch("hexz"),
                        "hexz")
        );
    }

    @Test
    public void testWildcardSecondLastCharWildcardOccursAtDivergentCharacterOfExactMatch() {
        String[] noMatches = new String[] { "", "hel", "heloxz", "helxzz" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("hel*o"),
                        "helo", "heloo", "hello", "helloo", "helxxxo", "helxzo"),
                new PatternMatch(Patterns.exactMatch("helxz"),
                        "helxz")
        );
    }

    @Test
    public void testWildcardTrailingWildcardOccursAtDivergentCharacterOfExactMatch() {
        String[] noMatches = new String[] { "", "he", "hex" , "hexzz" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("hel*"),
                        "hel", "helx", "helxz", "hello"),
                new PatternMatch(Patterns.exactMatch("helxz"),
                        "helxz")
        );
    }

    @Test
    public void testWildcardMultipleWildcardOccursAtDivergentCharacterOfExactMatch() {
        String[] noMatches = new String[] { "", "ho", "hxl", "bye", "lbye", "hbye" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("h*l*o"),
                        "hlo", "helo", "hllo", "hello", "hxxlo", "hlxxo", "hxxlxxo", "hlbyeo"),
                new PatternMatch(Patterns.exactMatch("hlbye"),
                        "hlbye")
        );
    }

    @Test
    public void testWildcardLastCharAndThirdLastCharWildcardOccursAtDivergentCharacterOfExactMatch() {
        String[] noMatches = new String[] { "", "ho", "hxl", "bye", "lbye", "hbye" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("he*l*"),
                        "hel", "hexl", "helo", "hello", "hexxl", "helxx", "helbye", "hellobye", "helbyeo"),
                new PatternMatch(Patterns.exactMatch("helbye"),
                        "helbye")
        );
    }

    @Test
    public void testWildcardNormalPositionWildcardOccursInMiddleOfExactMatch() {
        String[] noMatches = new String[] { "", "he", "hel", "hellx", "hexllo" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("he*ll"),
                        "hell", "hexll", "hexxll"),
                new PatternMatch(Patterns.exactMatch("hello"),
                        "hello")
        );
    }

    @Test
    public void testWildcardSecondLastCharWildcardOccursInMiddleOfExactMatch() {
        String[] noMatches = new String[] { "", "hxl", "hxel", "hex", "hexlx" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("he*l"),
                        "hel", "hexl", "hexyzl"),
                new PatternMatch(Patterns.exactMatch("hello"),
                        "hello")
        );
    }

    @Test
    public void testWildcardTrailingWildcardOccursInMiddleOfExactMatch() {
        String[] noMatches = new String[] { "", "hex", "hexl" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("hel*"),
                        "hel", "helx", "helhel", "hello", "hellox"),
                new PatternMatch(Patterns.exactMatch("hello"),
                        "hello")
        );
    }

    @Test
    public void testWildcardNormalPositionWildcardOccursAfterFinalCharacterOfExactMatch() {
        String[] noMatches = new String[] { "", "hell", "hexlo", "helxo" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("hel*lo"),
                        "hello", "helllllo", "helxxxlo"),
                new PatternMatch(Patterns.exactMatch("hel"),
                        "hel")
        );
    }

    @Test
    public void testWildcardSecondLastCharWildcardOccursAfterFinalCharacterOfExactMatch() {
        String[] noMatches = new String[] { "", "he", "heo", "heloox" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("hel*o"),
                        "helo", "heloo", "hello", "helxo", "helllllxlllo"),
                new PatternMatch(Patterns.exactMatch("hel"),
                        "hel")
        );
    }

    @Test
    public void testWildcardTrailingWildcardOccursAfterFinalCharacterOfExactMatch() {
        String[] noMatches = new String[] { "", "he", "hex", "hexl" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("hel*"),
                        "hel", "hello"),
                new PatternMatch(Patterns.exactMatch("hel"),
                        "hel")
        );
    }

    @Test
    public void testWildcardMultipleWildcardOccursAfterFinalCharacterOfExactMatch() {
        String[] noMatches = new String[] { "", "x", "ho", "hxo" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("h*l*o"),
                        "hlo", "helo", "hllo", "hello", "hxxlxxo"),
                new PatternMatch(Patterns.exactMatch("h"),
                        "h")
        );
    }

    @Test
    public void testWildcardLastCharAndThirdLastCharWildcardOccursAfterFinalCharacterOfExactMatch() {
        String[] noMatches = new String[] { "", "h", "hl", "hex" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("he*l*"),
                        "hel", "hexl", "helo", "hello", "hexxlxx"),
                new PatternMatch(Patterns.exactMatch("he"),
                        "he")
        );
    }

    @Test
    public void testWildcardMultipleWildcardOccursSurroundingFinalCharacterOfExactMatch() {
        String[] noMatches = new String[] { "", "he", "hl", "ho", "hxo" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("h*l*o"),
                        "hlo", "helo", "hllo", "hello", "hxxlxxo"),
                new PatternMatch(Patterns.exactMatch("hel"),
                        "hel")
        );
    }

    @Test
    public void testWildcardLastCharAndThirdLastCharWildcardOccursSurroundingFinalCharacterOfExactMatch() {
        String[] noMatches = new String[] { "", "he", "hl", "hexo", "el" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("he*l*"),
                        "hel", "hell", "helo", "hello", "hexxlxx"),
                new PatternMatch(Patterns.exactMatch("hell"),
                        "hell")
        );
    }

    @Test
    public void testWildcardExactMatchHasWildcardCharacter() {
        String[] noMatches = new String[] { "hel", "heo", "hlo", "helox", "hxlo" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("he*lo"),
                        "helo", "he*lo", "hello", "hellllllo"),
                new PatternMatch(Patterns.exactMatch("he*lo"),
                        "he*lo")
        );
    }

    @Test
    public void testWildcardFourMultipleWildcardPatternsInterweaving() {
        String[] noMatches = new String[] { "", "ab", "bc", "ac", "xbyazc", "xbycza", "xcybza" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("*a*b*c"),
                        "abc", "aabbcc", "xaybzc", "xxayybzzc"),
                new PatternMatch(Patterns.wildcardMatch("a*b*c*"),
                        "abc", "aabbcc", "axbycz", "azzbyyczz"),
                new PatternMatch(Patterns.wildcardMatch("*a*c*b"),
                        "acb", "aaccbb", "xayczb", "xxayyczzb"),
                new PatternMatch(Patterns.wildcardMatch("a*c*b*"),
                        "acb", "aaccbb", "axcybz", "axxcyybzz")
        );
    }

    @Test
    public void testWildcardWithAnythingButPattern() {
        testPatternPermutations(
                new PatternMatch(Patterns.wildcardMatch("he*lo"),
                        "helo", "hello", "hexxxlo"),
                new PatternMatch(Patterns.anythingButMatch("hello"),
                        "", "helo", "helol", "hexxxlo")
        );
    }

    @Test
    public void testWildcardWithAnythingButSetPattern() {
        testPatternPermutations(
                new PatternMatch(Patterns.wildcardMatch("he*lo"),
                        "helo", "hello", "hexxxlo"),
                new PatternMatch(Patterns.anythingButMatch(new HashSet<>(Arrays.asList("helo", "hello"))),
                        "", "helol", "hexxxlo")
        );
    }

    @Test
    public void testWildcardWithAnythingButPrefixPatternWildcardStartsWithPrefix() {
        String[] noMatches = new String[] { "he", "hel", "hell", "hexxx" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("he*lo"),
                        "helo", "hello", "hellllo"),
                new PatternMatch(Patterns.anythingButPrefix("he"),
                        "", "h", "hllo", "x", "xx")
        );
    }

    @Test
    public void testWildcardWithAnythingButSuffixPatternEndsWithSuffix() {
        String[] noMatches = new String[] { "going", "leaving", "ng", "gong" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.anythingButSuffix("ng"),
                        "", "g", "gog", "x", "xx")
        );
    }

    @Test
    public void testWildcardWithAnythingButPrefixPatternWildcardStartsWithHalfOfPrefix() {
        String[] noMatches = new String[] { "he", "hel", "hell", "hello", "hexxx" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("hx*lo"),
                        "hxlo", "hxllo", "hxllllo"),
                new PatternMatch(Patterns.anythingButPrefix("he"),
                        "", "h", "hxlo", "hxllo", "hxllllo", "x", "xx")
        );
    }

    @Test
    public void testWildcardWithAnythingButPrefixPatternWildcardStartsWithNoneOfPrefix() {
        String[] noMatches = new String[] { "xe", "xex", "xexx" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("hx*lo"),
                        "hxlo", "hxllo", "hxllllo"),
                new PatternMatch(Patterns.anythingButPrefix("xe"),
                        "", "x", "xyyy", "h", "he", "hxlo", "hxllo", "hxllllo")
        );
    }

    @Test
    public void testWildcardWithPrefixPatternWildcardStartsWithPrefix() {
        String[] noMatches = new String[] { "", "hlo", "elo" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("he*lo"),
                        "helo", "hello", "hellllo"),
                new PatternMatch(Patterns.prefixMatch("he"),
                        "he", "helo", "hello", "hellllo")
        );
    }

    @Test
    public void testWildcardWithPrefixPatternWildcardStartsWithHalfOfPrefix() {
        String[] noMatches = new String[] { "", "hx", "hxl", "hlo", "elo", "xlo" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("hx*lo"),
                        "hxlo", "hxllo", "hxllllo"),
                new PatternMatch(Patterns.prefixMatch("he"),
                        "he", "helo", "hello", "hellllo")
        );
    }

    @Test
    public void testWildcardWithPrefixPatternWildcardStartsWithNoneOfPrefix() {
        String[] noMatches = new String[] { "", "h", "x", "xx", "xxl", "xlo", "hxllo" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("xx*lo"),
                        "xxlo", "xxllo", "xxllllo"),
                new PatternMatch(Patterns.prefixMatch("he"),
                        "he", "helo", "hello", "hellllo")
        );
    }

    @Test
    public void testWildcardWithSuffixPatternWildcardEndsWithSuffix() {
        String[] noMatches = new String[] { "", "he", "hel" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("he*lo"),
                        "helo", "hello", "hellllo"),
                new PatternMatch(Patterns.suffixMatch("lo"),
                        "lo", "helo", "hello", "hellllo")
        );
    }

    @Test
    public void testWildcardWithSuffixPatternWildcardEndsWithHalfOfSuffix() {
        String[] noMatches = new String[] { "", "he", "hex", "exo" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("he*xo"),
                        "hexo", "hexxo", "hexxxxo"),
                new PatternMatch(Patterns.suffixMatch("lo"),
                        "lo", "helo", "hello", "hellllo")
        );
    }

    @Test
    public void testWildcardWithSuffixPatternWildcardEndsWithNoneOfSuffix() {
        String[] noMatches = new String[] { "", "he", "hex", "exy", "exo" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("he*xy"),
                        "hexy", "hexxy", "hexxxxy"),
                new PatternMatch(Patterns.suffixMatch("lo"),
                        "lo", "helo", "hello", "hellllo")
        );
    }

    @Test
    public void testWildcardWithEqualsIgnoreCasePattern() {
        String[] noMatches = new String[] { "", "hel", "heo", "HEXLO", "HExLO", "hexlO", "helllLo", "hElo", "HEXlo" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("he*lo"),
                        "helo", "hello", "heLlo", "hellllo"),
                new PatternMatch(Patterns.equalsIgnoreCaseMatch("heLLo"),
                        "heLLo", "hello", "HELLO", "HEllO", "HeLlO", "hElLo", "heLlo")
        );
    }

    @Test
    public void testWildcardWithExistencePattern() {
        testPatternPermutations(
                new PatternMatch(Patterns.wildcardMatch("he*lo"),
                        "helo", "hello", "hellllo"),
                new PatternMatch(Patterns.existencePatterns(),
                        "", "a", "b", "c", "abc", "helo", "hello", "hellllo")
        );
    }

    @Test
    public void testWildcardSecondWildcardCharacterIsNotReusedByOtherWildcardRule() {
        String[] noMatches = new String[] { "", "abcd", "bcde", "ae", "xabcde", "abcdex" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("a*bc*de"),
                        "abcde", "axbcde", "abcxde", "axbcxde"),
                new PatternMatch(Patterns.wildcardMatch("a*bcde"),
                        "abcde", "axbcde")
        );
    }

    @Test
    public void testWildcardAddTwiceDeleteOnceLeadingWildcard() {
        ByteMachine cut = new ByteMachine();
        cut.addPattern(Patterns.wildcardMatch("*hello"));
        cut.addPattern(Patterns.wildcardMatch("*hello"));
        cut.deletePattern(Patterns.wildcardMatch("*hello"));
        assertTrue(cut.isEmpty());
    }

    @Test
    public void testWildcardAddTwiceDeleteOnceNormalPositionWildcard() {
        ByteMachine cut = new ByteMachine();
        cut.addPattern(Patterns.wildcardMatch("h*llo"));
        cut.addPattern(Patterns.wildcardMatch("h*llo"));
        cut.deletePattern(Patterns.wildcardMatch("h*llo"));
        assertTrue(cut.isEmpty());
    }

    @Test
    public void testWildcardAddTwiceDeleteOnceSecondLastCharWildcard() {
        ByteMachine cut = new ByteMachine();
        cut.addPattern(Patterns.wildcardMatch("hell*o"));
        cut.addPattern(Patterns.wildcardMatch("hell*o"));
        cut.deletePattern(Patterns.wildcardMatch("hell*o"));
        assertTrue(cut.isEmpty());
    }

    @Test
    public void testWildcardAddTwiceDeleteOnceTrailingWildcard() {
        ByteMachine cut = new ByteMachine();
        cut.addPattern(Patterns.wildcardMatch("hello*"));
        cut.addPattern(Patterns.wildcardMatch("hello*"));
        cut.deletePattern(Patterns.wildcardMatch("hello*"));
        assertTrue(cut.isEmpty());
    }

    @Test
    public void testWildcardAddTwiceDeleteOnceMultipleWildcard() {
        ByteMachine cut = new ByteMachine();
        cut.addPattern(Patterns.wildcardMatch("h*l*o"));
        cut.addPattern(Patterns.wildcardMatch("h*l*o"));
        cut.deletePattern(Patterns.wildcardMatch("h*l*o"));
        assertTrue(cut.isEmpty());
    }

    @Test
    public void testWildcardAddTwiceDeleteOnceLastCharAndThirdLastCharWildcard() {
        ByteMachine cut = new ByteMachine();
        cut.addPattern(Patterns.wildcardMatch("he*l*"));
        cut.addPattern(Patterns.wildcardMatch("he*l*"));
        cut.deletePattern(Patterns.wildcardMatch("he*l*"));
        assertTrue(cut.isEmpty());
    }

    @Test
    public void testWildcardAddTwiceDeleteOnceSingleCharWildcard() {
        ByteMachine cut = new ByteMachine();
        cut.addPattern(Patterns.wildcardMatch("*"));
        cut.addPattern(Patterns.wildcardMatch("*"));
        cut.deletePattern(Patterns.wildcardMatch("*"));
        assertTrue(cut.isEmpty());
    }

    @Test
    public void testWildcardAddTwiceDeleteOnceMixed() {
        ByteMachine cut = new ByteMachine();
        for (int i = 0; i < 2; i ++){
            cut.addPattern(Patterns.wildcardMatch("h*llo"));
            cut.addPattern(Patterns.wildcardMatch("*"));
            cut.addPattern(Patterns.wildcardMatch("*hello"));
            cut.addPattern(Patterns.wildcardMatch("hello*"));
            cut.addPattern(Patterns.wildcardMatch("hell*o"));
            cut.addPattern(Patterns.wildcardMatch("h*l*o"));
            cut.addPattern(Patterns.wildcardMatch("he*l*"));
        }
        cut.deletePattern(Patterns.wildcardMatch("h*llo"));
        cut.deletePattern(Patterns.wildcardMatch("*"));
        cut.deletePattern(Patterns.wildcardMatch("*hello"));
        cut.deletePattern(Patterns.wildcardMatch("hello*"));
        cut.deletePattern(Patterns.wildcardMatch("hell*o"));
        cut.deletePattern(Patterns.wildcardMatch("h*l*o"));
        cut.deletePattern(Patterns.wildcardMatch("he*l*"));
        assertTrue(cut.isEmpty());
    }

    @Test
    public void testWildcardMachineNotEmptyWithSingleWildcardCharacterPattern() {
        ByteMachine cut = new ByteMachine();
        cut.addPattern(Patterns.wildcardMatch("*"));
        assertFalse(cut.isEmpty());
        cut.deletePattern(Patterns.wildcardMatch("*"));
        assertTrue(cut.isEmpty());
    }

    @Test
    public void testWildcardRuleIsNotDuplicated() {
        ByteMachine cut = new ByteMachine();
        for (int i = 0; i < 10; i++) {
            cut.addPattern(Patterns.wildcardMatch("1*2345"));
        }
        assertEquals(2, cut.evaluateComplexity(new MachineComplexityEvaluator(Integer.MAX_VALUE)));
    }

    @Test
    public void testWildcardRuleIsNotDuplicatedWildcardIsFirstChar() {
        ByteMachine cut = new ByteMachine();
        for (int i = 0; i < 10; i++) {
            cut.addPattern(Patterns.wildcardMatch("*123"));
        }
        assertEquals(2, cut.evaluateComplexity(new MachineComplexityEvaluator(Integer.MAX_VALUE)));
    }

    @Test
    public void testWildcardRuleIsNotDuplicatedWildcardIsSecondLastChar() {
        ByteMachine cut = new ByteMachine();
        for (int i = 0; i < 10; i++) {
            cut.addPattern(Patterns.wildcardMatch("1*2"));
        }
        assertEquals(2, cut.evaluateComplexity(new MachineComplexityEvaluator(Integer.MAX_VALUE)));
    }

    @Test
    public void testWildcardRuleIsNotDuplicatedWildcardIsLastChar() {
        ByteMachine cut = new ByteMachine();
        for (int i = 0; i < 10; i++) {
            cut.addPattern(Patterns.wildcardMatch("1*"));
        }
        assertEquals(2, cut.evaluateComplexity(new MachineComplexityEvaluator(Integer.MAX_VALUE)));
    }

    @Test
    public void testWildcardRuleIsNotDuplicatedWildcardIsThirdLastAndLastChar() {
        ByteMachine cut = new ByteMachine();
        for (int i = 0; i < 10; i++) {
            cut.addPattern(Patterns.wildcardMatch("12*3*"));
        }
        assertEquals(3, cut.evaluateComplexity(new MachineComplexityEvaluator(Integer.MAX_VALUE)));
    }

    @Test
    public void testWildcardMultipleWildcardPatterns1() {
        // Considering the following as potential matches: "hello", "hxxllo", "xhello", "xxhello", "hellox", "helloxx", "hellxo", "hellxxo", "", "helxlo", "kaboom"
        String[] noMatches = new String[] { "", "helxlo", "kaboom" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("h*llo"),
                        "hello", "hxxllo"),
                new PatternMatch(Patterns.wildcardMatch("*hello"),
                        "hello", "xhello", "xxhello"),
                new PatternMatch(Patterns.wildcardMatch("hello*"),
                        "hello", "hellox", "helloxx"),
                new PatternMatch(Patterns.wildcardMatch("hell*o"),
                        "hello", "hellxo", "hellxxo")
        );
    }

    @Test
    public void testWildcardMultipleWildcardPatterns2() {
        // Considering the following as potential matches: "hello", "hellox", "hxllo", "hxlxo", "xlo", "", "hell", "hellx", "xell", "xellox"
        String[] noMatches = new String[] { "", "hell", "hellx", "xell", "xellox" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("hello*"),
                        "hello", "hellox"),
                new PatternMatch(Patterns.wildcardMatch("h*llo"),
                        "hello", "hxllo"),
                new PatternMatch(Patterns.wildcardMatch("h*l*o"),
                        "hello", "hxllo", "hxlxo"),
                new PatternMatch(Patterns.wildcardMatch("*lo"),
                        "hello", "hxllo", "xlo")
        );
    }

    @Test
    public void testWildcardMultipleMixedPatterns() {
        // Considering the following as potential matches: "", "x", "hello", "xhello", "helo", "helxo", "hellox", "", "x", "xello"
        String[] noMatches = new String[] { "", "x", "xello" };
        testPatternPermutations(noMatches,
                new PatternMatch(Patterns.wildcardMatch("*hello"),
                        "hello", "xhello"),
                new PatternMatch(Patterns.wildcardMatch("hel*o"),
                        "hello", "helo", "helxo"),
                new PatternMatch(Patterns.wildcardMatch("hello*"),
                        "hello", "hellox"),
                new PatternMatch(Patterns.wildcardMatch("h*o"),
                        "hello", "helo", "helxo"),
                new PatternMatch(Patterns.exactMatch("hello"),
                        "hello"),
                new PatternMatch(Patterns.exactMatch("helo"),
                        "helo"),
                new PatternMatch(Patterns.prefixMatch("h"),
                        "hello", "helo", "helxo", "hellox")
        );
    }

    @Test
    public void testWildcardNoConsecutiveWildcardCharacters() {
        try {
            new ByteMachine().addPattern(Patterns.wildcardMatch("h**o"));
            fail("Expected ParseException");
        } catch (ParseException e) {
            assertEquals("Consecutive wildcard characters at pos 1", e.getMessage());
        }
    }

    @Test
    public void testAddMatchPatternGivenNameStateReturned() {
        NameState nameState = new NameState();
        ByteMachine cut = new ByteMachine();
        assertSame(nameState, cut.addPattern(Patterns.exactMatch("a"), nameState));
    }

    @Test
    public void testAddMatchPatternNoNameStateGiven() {
        ByteMachine cut = new ByteMachine();
        assertNotNull(cut.addPattern(Patterns.exactMatch("a")));
    }

    @Test
    public void testAddExistencePatternGivenNameStateReturned() {
        NameState nameState = new NameState();
        ByteMachine cut = new ByteMachine();
        assertSame(nameState, cut.addPattern(Patterns.existencePatterns(), nameState));
    }

    @Test
    public void testAddExistencePatternNoNameStateGiven() {
        ByteMachine cut = new ByteMachine();
        assertNotNull(cut.addPattern(Patterns.existencePatterns()));
    }

    @Test
    public void testAddAnythingButPatternGivenNameStateReturned() {
        NameState nameState = new NameState();
        ByteMachine cut = new ByteMachine();
        assertSame(nameState, cut.addPattern(Patterns.anythingButMatch("z"), nameState));
    }

    @Test
    public void testAddAnythingButPatternNoNameStateGiven() {
        ByteMachine cut = new ByteMachine();
        assertNotNull(cut.addPattern(Patterns.anythingButMatch("z")));
    }

    @Test
    public void testAddRangePatternGivenNameStateReturned() {
        NameState nameState = new NameState();
        ByteMachine cut = new ByteMachine();
        assertSame(nameState, cut.addPattern(Range.lessThan(5), nameState));
    }

    @Test
    public void testAddRangePatternNoNameStateGiven() {
        ByteMachine cut = new ByteMachine();
        assertNotNull(cut.addPattern(Range.lessThan(5)));
    }

    private void testPatternPermutations(PatternMatch ... patternMatches) {
        testPatternPermutations(new String[0], patternMatches);
    }

    /**
     * Adds all given patterns to the machine according to all possible permutations. For a given permutation, as each
     * pattern is added, the machine is exercised against the given match values, and we verify that each match value
     * generates the expected number of matches. After adding the last pattern of a permutation and verifying the
     * matches, we delete all patterns, verifying the expected number of matches after each pattern deletion, and
     * verifying the machine is empty after the last pattern is deleted. Deletion can be done in one of two ways. For
     * sufficiently large numbers of permutations, we simply choose a random permutation for the deletion. However, for
     * smaller numbers of permutations, we perform deletion according to all permutations, i.e. for each addition
     * permutation, we exercise all deletion permutations, which is (n!)^2 where n is the number of patterns.
     *
     * Any match specified by one PatternMatch will be evaluated against all patterns. So if you specify that one
     * pattern has matched a certain value, you need to specify that same value for all other patterns that match it as
     * well. Provide values that will not match any patterns in the noMatches array and this function will verify that
     * they are never matched. If you provide a value in the noMatches array that is present in one of the PatternMatch
     * objects, it will have no effect; i.e. the value will still be expected to match the pattern.
     *
     * @param noMatches Array of values that do not match any of the given patterns.
     * @param patternMatches Array where each element contains a pattern and values that will match the pattern.
     */
    private void testPatternPermutations(String[] noMatches, PatternMatch ... patternMatches) {
        long seed = new Random().nextLong();
        System.out.println("USE ME TO REPRODUCE - ByteMachineTest.testPatternPermutations seeding with " + seed);
        Random r = new Random(seed);
        ByteMachine cut = new ByteMachine();
        Set<String> matchValues = Stream.of(patternMatches)
                                        .map(patternMatch -> patternMatch.matches)
                                        .flatMap(set -> set.stream())
                                        .collect(Collectors.toSet());
        matchValues.addAll(Arrays.asList(noMatches));
        Matches matches = new Matches(matchValues.stream()
                                                 .map(string -> new Match(string))
                                                 .collect(Collectors.toList())
                                                 .toArray(new Match[0]));

        List<PatternMatch[]> permutations = generateAllPermutations(patternMatches);

        for (PatternMatch[] additionPermutation : permutations) {
            // Magic number alert: For 5 or less patterns, it is reasonable to test all deletion permutations for each
            // addition permutation. But for 6 or more patterns, the runtime becomes ridiculous, so we will settle for
            // choosing a random deletion permutation for each addition permutation.
            if (patternMatches.length <= 5) {
                for (PatternMatch[] deletionPermutation : permutations) {
                    testPatternPermutation(cut, additionPermutation, deletionPermutation, matches);
                }
            } else {
                testPatternPermutation(cut, additionPermutation, permutations.get(r.nextInt(permutations.size())),
                        matches);
            }
        }
    }

    private void testPatternPermutation(ByteMachine cut, PatternMatch[] additionPermutation,
                                        PatternMatch[] deletionPermutation, Matches matches) {
        for (PatternMatch patternMatch : additionPermutation) {
            cut.addPattern(matches.registerPattern(patternMatch));
            for (Match match : matches.get()) {
                assertEquals("Failed on " + match.value,
                        match.getNumPatternsRegistered(), cut.transitionOn(match.value).size());
            }
        }
        for (PatternMatch patternMatch : deletionPermutation) {
            cut.deletePattern(matches.deregisterPattern(patternMatch));
            for (Match match : matches.get()) {
                assertEquals("Failed on " + match.value, match.getNumPatternsRegistered(),
                        cut.transitionOn(match.value).size());
            }
        }
        assertTrue(cut.isEmpty());
        matches.assertNoPatternsRegistered();
    }

    private static class Matches {
        private final Match[] matches;

        public Matches(Match ... matches) {
            this.matches = matches;
        }

        public Match[] get() {
            return matches;
        }

        public Patterns registerPattern(PatternMatch patternMatch) {
            for (Match match : matches) {
                match.registerPattern(patternMatch);
            }
            return patternMatch.pattern;
        }

        public Patterns deregisterPattern(PatternMatch patternMatch) {
            for (Match match : matches) {
                match.deregisterPattern(patternMatch);
            }
            return patternMatch.pattern;
        }

        public void assertNoPatternsRegistered() {
            for (Match match : matches) {
                assertEquals(0, match.getNumPatternsRegistered());
            }
        }
    }

    private static class Match {
        private final String value;
        private int num = 0;

        public Match(String value) {
            this.value = value;
        }

        public void registerPattern(PatternMatch patternMatch) {
            if (patternMatch.matches.contains(value)) {
                num++;
            }
        }

        public void deregisterPattern(PatternMatch patternMatch) {
            if (patternMatch.matches.contains(value)) {
                num--;
            }
        }

        public int getNumPatternsRegistered() {
            return num;
        }
    }

    private static class PatternMatch {
        private final Patterns pattern;
        private final Set<String> matches;

        public PatternMatch(Patterns pattern, String ... matches) {
            this.pattern = pattern;
            this.matches = new HashSet<>(Arrays.asList(matches));
        }
    }

}
