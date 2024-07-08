package software.amazon.event.ruler;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ComparableNumberTest {

    @Test
    public void WHEN_BytesAreProvided_THEN_HexCharsAreReturned() {

        for (int input = 0; input < 256; input++) {

            char[] result = ComparableNumber.byteToHexChars((byte) input);

            char[] expectedResult = String.format("%02x", input).toUpperCase(Locale.ROOT).toCharArray();

            Assert.assertArrayEquals("byte to hex should match", expectedResult, result);

        }
    }

    @Test
    public void WHEN_WildlyVaryingNumberFormsAreProvided_THEN_TheGeneratedStringsAreSortable() {
        double[] data = {
                -Constants.FIVE_BILLION, -4_999_999_999.99999, -4_999_999_999.99998, -4_999_999_999.99997,
                -999999999.99999, -999999999.99, -10000, -122.413496, -0.000002,
                0, 0.000001, 3.8, 3.9, 11, 12, 122.415028, 2.5e4, 999999999.999998, 999999999.999999,
                4_999_999_999.99997, 4_999_999_999.99998, 4_999_999_999.99999, Constants.FIVE_BILLION
        };
        for (int i = 1; i < data.length; i++) { // -122.415028278886751
            String s0 = ComparableNumber.generate(Double.toString(data[i-1]));
            String s1 = ComparableNumber.generate(Double.toString(data[i]));
            assertGreater(s0, s1);
        }
    }

    @Test
    public void WHEN_EventHasVaryingPrecision_THEN_MatchRuleWithDecimalAsString() throws Exception {
        String badRule = "{\"x\": [ 37.807807921694092 ] }";
        String[] varying = {
                "37.807807921694092",
                "37.80780792169409",
                "37.8078079216940",
                "37.807807921694",
                "37.80780792169",
                "37.8078079216",
                "37.807807921",
                "37.80780792",
                "37.8078079",
                "37.807807",
                "37.80780",
        };
        int[] expected = {
                1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
        };
        for (int i=0; i < varying.length; i++) {
            String event = String.format("{\"x\": %s}", varying[i]);
            Machine mm = new Machine();
            mm.addRule("R", badRule);
            List<String> matches = mm.rulesForJSONEvent(event);
            assertEquals(expected[i], matches.size());
        }
    }

    @Test
    public void WHEN_EventHasVaryingPrecision_THEN_MatchWithSixDecimals() throws Exception {
        String goodRule = "{\"x\": [ 37.80780 ] }";
        String[] varying = {
                "37.807807921694092",
                "37.80780792169409",
                "37.8078079216940",
                "37.807807921694",
                "37.80780792169",
                "37.8078079216",
                "37.807807921",
                "37.80780792",
                "37.8078079",
                "37.807807",
                "37.80780",
        };
        int[] expected = {
               0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1
        };
        for (int i=0; i < varying.length; i++) {
            String event = String.format("{\"x\": %s}", varying[i]);
            Machine mm = new Machine();
            mm.addRule("R", goodRule);
            List<String> matches = mm.rulesForJSONEvent(event);
            assertEquals(expected[i], matches.size());
        }
    }

    @Test
    public void WHEN_NumbersWithDifferentFormat_THEN_allCanBeParsed() {
        Map<String, String> testCases = new HashMap<>();
        // integers
        testCases.put("-0", "11C37937E08000");
        testCases.put("01", "11C37937EFC240");
        testCases.put("-0777", "11C37909906BC0");
        testCases.put("-12345600", "11B83EC8C64000");
        testCases.put("010", "11C37938791680");
        testCases.put("-010", "11C3793747E980");
        testCases.put("12345600", "11CEB3A6FAC000");
        testCases.put("011", "11C379388858C0");
        testCases.put("-011", "11C3793738A740");
        testCases.put("0123", "11C3793F3554C0");
        testCases.put("-01", "11C37937D13DC0");
        testCases.put("0", "11C37937E08000");
        testCases.put("0000123456", "11C395F66D9000");
        testCases.put("-0000123456", "11C35C79537000");
        testCases.put("123456", "11C395F66D9000");
        testCases.put("-123456", "11C35C79537000");
        testCases.put("-0123", "11C379308BAB40");
        testCases.put("0777", "11C37966309440");
        // floats
        testCases.put("0.0", "11C37937E08000");
        testCases.put("-123.456", "11C3793084B600");
        testCases.put("123.456", "11C3793F3C4A00");
        testCases.put("1e2", "11C3793DD66100");
        testCases.put("-.123456", "11C37937DE9DC0");
        testCases.put("1e-2", "11C37937E0A710");
        testCases.put("-0.0", "11C37937E08000");

        for (Entry<String, String> entry: testCases.entrySet()) {
            String input = entry.getKey();
            String expected = entry.getValue();
            String actual = ComparableNumber.generate(input);
            assertEquals("For " + input + " got " + actual + " but expected " + expected,
                    expected, actual);
        }
    }

    @Test
    public void WHEN_LotsOfLargeNumbers_THEN_OrderingIsNotLost() {
        Random random = new Random();
        int numberOfDoubles = 1_000_000;
        double[] doubles = new double[numberOfDoubles];

        for (int i = 0; i < numberOfDoubles; i++) { // Generate large doubles with at most 6 decimals
            double randomDouble = random.nextDouble() * Constants.FIVE_BILLION;
            randomDouble = Math.round(randomDouble * 1e6) / 1e6;
            randomDouble = i % 2 == 0 ? randomDouble : randomDouble * -1;
            doubles[i] = randomDouble;
        }

        Arrays.sort(doubles);

        for (int i = 1; i < numberOfDoubles; i++) {
            double first = doubles[i - 1];
            double next = doubles[i];
            assertTrue("failed: " + next + " vs " + first, first <= next);

            String s0 = ComparableNumber.generate(Double.toString(first));
            String s1 = ComparableNumber.generate(Double.toString(next));
            assertGreater(s0, s1);
        }
    }

    @Test
    public void WHEN_LotsOfSixFractionalDigits_THEN_OrderingIsNotLost() {
        Random random = new Random();
        int numberOfDoubles = 1_000_000;
        double[] doubles = new double[numberOfDoubles];

        for (int i = 0; i < numberOfDoubles; i++) { // Generate doubles with at most 6 decimals
            double randomDouble = random.nextDouble() * 100000;
            randomDouble = Math.round(randomDouble * 1e6) / 1e6;
            randomDouble = i % 2 == 0 ? randomDouble : randomDouble * -1;
            doubles[i] = randomDouble;
        }

        Arrays.sort(doubles);

        for (int i = 1; i < numberOfDoubles; i++) {
            double first = doubles[i - 1];
            double next = doubles[i];
            assertTrue("failed: " + next + " vs " + first, first <= next);

            String s0 = ComparableNumber.generate(Double.toString(first));
            String s1 = ComparableNumber.generate(Double.toString(next));
            assertGreater(s0, s1);
        }
    }

    @Test
    public void WHEN_SixFractionalDigits_THEN_OrderingIsNotLost() {
        double[] lows = {-5_000_000_000.0, -4_999_999_999.999999, -4_999_999_999.999998};
        double[] highs = {4_999_999_999.999998, 4_999_999_999.999999, 5_000_000_000.0};

        for (int i = 1; i < lows.length; i++) {
            String s0 = ComparableNumber.generate(Double.toString(lows[i - 1]));
            String s1 = ComparableNumber.generate(Double.toString(lows[i]));
            System.out.println("i=" + i + " s0:" + s0 + " s1:" + s1);
            assertTrue(s0.compareTo(s1) < 0);
        }

        for (int i = 1; i < highs.length; i++) {
            String s0 = ComparableNumber.generate(Double.toString(highs[i - 1]));
            String s1 = ComparableNumber.generate(Double.toString(highs[i]));
            System.out.println("i=" + i + " s0:" + s0 + " s1:" + s1);
            assertTrue(s0.compareTo(s1) < 0);
        }
    }

    @Test
    public void tinyNumberTest() throws Exception {
        // ComparableNumber for 1.0e-6 is 1000000000000001
        // ComparableNumber for 4.0e-6 is 1000000000000004
        String closedRule = "{\n" +
                "  \"a\": [ { \"numeric\": [ \">=\", 2.0e-6, \"<=\", 4.0e-6 ] } ]\n" +
                "}";
        String openRule = "{\n" +
                "  \"a\": [ { \"numeric\": [ \">\", 2.0e-6, \"<\", 4.0e-6 ] } ]\n" +
                "}";
        String template = "{\n" +
                "  \"a\": VAL\n" +
                "}\n";
        Machine m = new Machine();
        m.addRule("r", closedRule);
        for (double d = 0; d < 9.0e-6; d += 1.0e-6) {
            String event = template.replace("VAL", Double.toString(d));
            List<String> r = m.rulesForJSONEvent(event);
            if (d >= 2.0e-6 && d <= 4.0e-6) {
                assertEquals("Should match: " + d, 1, m.rulesForJSONEvent(event).size());
            } else {
                assertEquals("Should not match: " + d, 0, m.rulesForJSONEvent(event).size());
            }
        }
        m = new Machine();
        m.addRule("r", openRule);
        for (double d = 0; d < 9.0e-6; d += 1.0e-6) {
            String event = template.replace("VAL", Double.toString(d));
            List<String> r = m.rulesForJSONEvent(event);
            if (d > 2.0e-6 && d < 4.0e-6) {
                assertEquals("Should match: " + d, 1, m.rulesForJSONEvent(event).size());
            } else {
                assertEquals("Should not match: " + d, 0, m.rulesForJSONEvent(event).size());
            }
        }
    }

    /**
     * refer to https://www.epochconverter.com/ for eligible timestamp
     * data1:
     * Epoch timestamp: 1628958408
     * Date and time (GMT): Saturday, August 14, 2021 4:26:48 PM
     * data2:
     * Epoch timestamp: 1629044808
     * Date and time (GMT): Sunday, August 15, 2021 4:26:48 PM
     */
    @Test
    public void epocTimestampRangeTest() throws Exception {
        final Map<String, String> rules  = new HashMap<String, String>() {{
            put("lessThan",
                    "{\n" +
                    "  \"timestamp\": [ { \"numeric\": [ \"<=\", 1628872008 ] } ]\n" +
                    "}");
            put("between",
                    "{\n" +
                    "  \"timestamp\": [ { \"numeric\": [ \">=\", 1628872008, \"<=\", 1629044808 ] } ]\n" +
                    "}");
            put("greatThan",
                    "{\n" +
                    "  \"timestamp\": [ { \"numeric\": [ \">=\", 1629044808 ] } ]\n" +
                    "}");
        }};

        String template = "{\n" +
                "  \"timestamp\": VAL\n" +
                "}\n";

        final Machine m = new Machine();
        for (Entry<String, String> r : rules.entrySet()) {
            m.addRule(r.getKey(), r.getValue());
        }

        for (int tt = 1628872000; tt < 1629045000; tt++) {
            String event = template.replace("VAL", Double.toString(tt));
            List<String> match = m.rulesForJSONEvent(event);
            if (tt <= 1628872008) {
                assertTrue(match.contains("lessThan"));
            } else if (tt < 1629044808) {
                assertTrue(match.contains("between"));
            } else {
                assertTrue(match.contains("greatThan"));
            }

            if (tt == 1628872008 || tt == 1629044808) {
                assertEquals(2, match.size());
            }
        }

        // delete rules, verify machine becomes empty
        for (Entry<String, String> r : rules.entrySet()) {
            m.deleteRule(r.getKey(), r.getValue());
        }
        assertTrue(m.isEmpty());
    }

    private static void assertGreater(String less, String big) {
        final char[] smallArr = less.toCharArray();
        final char[] bigArr = big.toCharArray();
        for (int j = 0; j < smallArr.length; j++) { // quick check
            if (smallArr[j] == bigArr[j]) {
                continue;
            }
            if (smallArr[j] < bigArr[j]) {
                break;
            }
            fail("failed: " + big + " vs " + less);
        }
    }
}