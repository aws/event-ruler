package software.amazon.event.ruler;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

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
        Set<NameState> matches;
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
            assertTrue("bytMachine must be empty after delete pattern", cut.isEmpty());
        }

        // first: less than
        for (int i = 1; i < data.length; i++) {
            ByteMachine cut = new ByteMachine();
            cut.addPattern(Range.lessThan(data[i]));
            for (double aData : data) {
                String num = String.format("%f", aData);
                Set<NameState> matched = cut.transitionOn(num);
                if (aData < data[i]) {
                    assertEquals(num + " should match < " + data[i], 1, matched.size());
                } else {
                    assertEquals(num + " should not match <" + data[i], 0, matched.size());
                }
            }
            cut.deletePattern(Range.lessThan(data[i]));
            assertTrue("bytMachine must be empty after delete pattern", cut.isEmpty());
        }

        // <=
        for (int i = 1; i < data.length; i++) {
            ByteMachine cut = new ByteMachine();
            cut.addPattern(Range.lessThanOrEqualTo(data[i]));
            for (double aData : data) {
                String num = String.format("%f", aData);
                Set<NameState> matched = cut.transitionOn(num);
                if (aData <= data[i]) {
                    assertEquals(num + " should match <= " + data[i], 1, matched.size());
                } else {
                    assertEquals(num + " should not match <=" + data[i], 0, matched.size());
                }
            }
            cut.deletePattern(Range.lessThanOrEqualTo(data[i]));
            assertTrue("bytMachine must be empty after delete pattern", cut.isEmpty());
        }

        // >
        for (int i = 0; i < (data.length - 1); i++) {
            ByteMachine cut = new ByteMachine();
            cut.addPattern(Range.greaterThan(data[i]));
            for (double aData : data) {
                String num = String.format("%f", aData);
                Set<NameState> matched = cut.transitionOn(num);
                if (aData > data[i]) {
                    assertEquals(num + " should match > " + data[i], 1, matched.size());
                } else {
                    assertEquals(num + " should not match >" + data[i], 0, matched.size());
                }
            }
            cut.deletePattern(Range.greaterThan(data[i]));
            assertTrue("bytMachine must be empty after delete pattern", cut.isEmpty());
        }

        // >=
        for (int i = 0; i < (data.length - 1); i++) {
            ByteMachine cut = new ByteMachine();
            Range nr = Range.greaterThanOrEqualTo(data[i]);
            cut.addPattern(nr);
            for (double aData : data) {
                String num = String.format("%f", aData);
                Set<NameState> matched = cut.transitionOn(num);
                if (aData >= data[i]) {
                    assertEquals(num + " should match > " + data[i], 1, matched.size());
                } else {
                    assertEquals(num + " should not match >" + data[i], 0, matched.size());
                }
            }
            cut.deletePattern(Range.greaterThanOrEqualTo(data[i]));
            assertTrue("bytMachine must be empty after delete pattern", cut.isEmpty());
        }

        // open/open range
        for (int i = 0; i < (data.length - 2); i++) {
            for (int j = i + 2; j < data.length; j++) {
                ByteMachine cut = new ByteMachine();
                Range r = Range.between(data[i], true, data[j], true);
                cut.addPattern(r);
                for (double aData : data) {
                    String num = String.format("%f", aData);
                    Set<NameState> matched = cut.transitionOn(num);
                    if (aData > data[i] && aData < data[j]) {
                        assertEquals(1, matched.size());
                    } else {
                        assertEquals(0, matched.size());
                    }
                }
                cut.deletePattern(r);
                assertTrue("bytMachine must be empty after delete pattern", cut.isEmpty());
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
                    Set<NameState> matched = cut.transitionOn(num);
                    if (aData > data[i] && aData <= data[j]) {
                        assertEquals(1, matched.size());
                    } else {
                        assertEquals(0, matched.size());
                    }
                }
                cut.deletePattern(r);
                assertTrue("bytMachine must be empty after delete pattern", cut.isEmpty());
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
                    Set<NameState> matched = cut.transitionOn(num);
                    if (aData >= data[i] && aData < data[j]) {
                        assertEquals(1, matched.size());
                    } else {
                        assertEquals(0, matched.size());
                    }
                }
                cut.deletePattern(r);
                assertTrue("bytMachine must be empty after delete pattern", cut.isEmpty());
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
                    Set<NameState> matched = cut.transitionOn(num);
                    if (aData >= data[i] && aData <= data[j]) {
                        assertEquals(1, matched.size());
                    } else {
                        assertEquals(0, matched.size());
                    }
                }
                cut.deletePattern(r);
                assertTrue("bytMachine must be empty after delete pattern", cut.isEmpty());
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
            Set<NameState> matched = cut.transitionOn(num);
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

        assertTrue("bytMachine must be empty after delete pattern", cut.isEmpty());
        for (int k = 0; k < data.length; k++) {
            String num = String.format("%f", data[k]);
            Set<NameState> matched = cut.transitionOn(num);
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
            Set<NameState> matched = cut.transitionOn(num);
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
        assertTrue("bytMachine must be empty after delete pattern", cut.isEmpty());
        for (int k = 0; k < data.length; k++) {
            String num = String.format("%f", data[k]);
            Set<NameState> matched = cut.transitionOn(num);
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

        Set<NameState> l = cut.transitionOn("foo");
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
        assertTrue("bytMachine must be empty after delete pattern", cut.isEmpty());

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
        assertTrue("bytMachine must be empty after delete pattern", cut.isEmpty());
        cut.deletePattern(r);
        assertTrue("bytMachine must be empty after delete pattern", cut.isEmpty());
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
        assertThat(stateFound, is(nullValue()));

        assertTrue(cut.isEmpty());
    }

    @Test
    public void testExistencePatternFindsMatch() {
        ByteMachine cut = new ByteMachine();

        cut.addPattern(Patterns.existencePatterns());

        Set<NameState> matches = cut.transitionOn("someValue");
        assertThat(matches.size(), is(equalTo(1)));

        cut.deletePattern(Patterns.existencePatterns());
        assertTrue(cut.isEmpty());
    }

    @Test
    public void testIfExistencePatternIsNotAdded_itDoesNotFindMatch() {
        ByteMachine cut = new ByteMachine();

        Set<NameState> matches = cut.transitionOn("someValue");
        assertThat(matches.size(), is(equalTo(0)));

        assertTrue(cut.isEmpty());
    }

    @Test
    public void testExistencePattern_WithOtherPatterns_workCorrectly() {
        ByteMachine cut = new ByteMachine();
        String val = "value";

        cut.addPattern(Patterns.existencePatterns());
        cut.addPattern(Patterns.exactMatch(val));

        Set<NameState> matches = cut.transitionOn("anotherValue");
        assertThat(matches.size(), is(equalTo(1)));

        matches = cut.transitionOn(val);
        assertThat(matches.size(), is(equalTo(2)));

        cut.deletePattern(Patterns.existencePatterns());
        matches = cut.transitionOn("anotherValue");
        assertThat(matches.size(), is(equalTo(0)));

        matches = cut.transitionOn(val);
        assertThat(matches.size(), is(equalTo(1)));

        cut.deletePattern(Patterns.exactMatch(val));
        matches = cut.transitionOn(val);
        assertThat(matches.size(), is(equalTo(0)));
    }

    @Test
    public void testNonNumericValue_DoesNotMatchNumericPattern() {
        ByteMachine cut = new ByteMachine();
        String val = "0A,";
        cut.addPattern(Range.greaterThanOrEqualTo(-1e9));

        Set<NameState> matches = cut.transitionOn(val);
        assertTrue(matches.isEmpty());
    }

    @Test
    public void testExistencePattern_startingWithDesignatedByteString_WithOtherPatterns_workCorrectly() {
        ByteMachine cut = new ByteMachine();
        String val = "value";

        cut.addPattern(Patterns.existencePatterns());
        cut.addPattern(Patterns.exactMatch(val));

        Set<NameState> matches = cut.transitionOn("NewValue");
        assertThat(matches.size(), is(equalTo(1)));

        matches = cut.transitionOn(val);
        assertThat(matches.size(), is(equalTo(2)));

        cut.deletePattern(Patterns.existencePatterns());
        matches = cut.transitionOn("NewValue");
        assertThat(matches.size(), is(equalTo(0)));

        matches = cut.transitionOn(val);
        assertThat(matches.size(), is(equalTo(1)));

        cut.deletePattern(Patterns.exactMatch(val));
        matches = cut.transitionOn(val);
        assertThat(matches.size(), is(equalTo(0)));
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
            assertEquals(1, cut.transitionOn(value).size());
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
        ByteMachine cut = new ByteMachine();
        String[] shouldBeMatched = { "java", "JAVA", "Java", "jAvA", "JaVa" };
        String[] shouldNOTBeMatched = { "JAV", "jav", "ava", "AVA", "JAVAx", "javax", "xJAVA", "xjava" };

        cut.addPattern(Patterns.equalsIgnoreCaseMatch("java"));
        for (String foo : shouldBeMatched) {
            assertEquals(1, cut.transitionOn(foo).size());
        }
        for (String notFoo : shouldNOTBeMatched) {
            assertEquals(0, cut.transitionOn(notFoo).size());
        }

        cut.deletePattern(Patterns.equalsIgnoreCaseMatch("java"));
        for (String notFoo : shouldBeMatched) {  // should no longer be matched
            assertEquals(0, cut.transitionOn(notFoo).size());
        }

        cut.addPattern(Patterns.equalsIgnoreCaseMatch("JaVA"));
        for (String foo : shouldBeMatched) {
            assertEquals(1, cut.transitionOn(foo).size());
        }
        for (String notFoo : shouldNOTBeMatched) {
            assertEquals(0, cut.transitionOn(notFoo).size());
        }
    }

    @Test
    public void testEqualsIgnoreCasePatternWithAdjustedShortcut() {
        ByteMachine cut = new ByteMachine();
        cut.addPattern(Patterns.equalsIgnoreCaseMatch("jAVa"));
        cut.addPattern(Patterns.exactMatch("ja"));
        assertEquals(1, cut.transitionOn("java").size());
    }

    @Test
    public void testEqualsIgnoreCasePatternWithoutShortcut() {
        ByteMachine cut = new ByteMachine();
        cut.addPattern(Patterns.equalsIgnoreCaseMatch("jAVa"));
        cut.addPattern(Patterns.exactMatch("jav"));
        assertEquals(1, cut.transitionOn("java").size());
    }

    @Test
    public void testEqualsIgnoreCasePatternNonLetterCharacters() {
        ByteMachine cut = new ByteMachine();
        cut.addPattern(Patterns.equalsIgnoreCaseMatch("1#$^sS我ŐaBc"));
        assertEquals(1, cut.transitionOn("1#$^sS我ŐaBc").size());
        assertEquals(1, cut.transitionOn("1#$^Ss我ŐAbC").size());
        assertEquals(0, cut.transitionOn("2#$^sS我ŐaBc").size());
        assertEquals(0, cut.transitionOn("1#%^sS我ŐaBc").size());
        assertEquals(0, cut.transitionOn("1#$^sS大ŐaBc").size());
        assertEquals(0, cut.transitionOn("1#$^sS我ŏaBc").size());
    }
}
