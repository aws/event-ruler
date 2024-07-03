package software.amazon.event.ruler;

import org.junit.Assert;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
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
            String s0 = ComparableNumber.generate(data[i-1]);
            String s1 = ComparableNumber.generate(data[i]);
            System.out.println("i=" + i + " s0:"+s0+" s1:"+s1 );
        }
    }

    //@Test // Takes too long
    public void testAllTheNumbers() {
        BigDecimal start = new BigDecimal(Constants.FIVE_BILLION);
        final BigDecimal bigDecimal = new BigDecimal(Constants.FIVE_BILLION * -1);
        final BigDecimal TEN_E_NEG_SIX = new BigDecimal(1E-6);

        while (start.compareTo(bigDecimal) > 0) {
            BigDecimal next = start.subtract(TEN_E_NEG_SIX);
            assertTrue(next.compareTo(start) < 0);
            final double sd = start.doubleValue();
            final double nd = next.doubleValue();

            String s0 = ComparableNumber.generate(sd);
            String s1 = ComparableNumber.generate(nd);
            assertTrue(s1 + " vs " + s0 + " " + next + " vs " + start, s1.compareTo(s0) < 0);

            start = next;
        }
    }
    /**
     * FIXME add assertions
     * we should probably? throw error when precision is sent over 6 decimals
     * https://github.com/aws/event-ruler/issues/163
     */
    @Test
    public void WHEN_PrecisionIsIgnored_THEN_BugsEnsue() {
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
        for (String number : varying) {
            String event = String.format("{\"x\": %s}", number);
            Machine mm = new Machine();
            try {
                mm.addRule("R", badRule);
                List<String> matches = mm.rulesForJSONEvent(event);
                System.out.printf("For %s, %d matches\n", number, matches.size());
            } catch (Exception e) {
                System.out.println("Ouch! " + e.toString() + " = " + event);
            }
        }

        String goodRule = "{\"x\": [ 37.80780 ] }";
        for (String number : varying) {
            String event = String.format("{\"x\": %s}", number);
            Machine mm = new Machine();
            try {
                mm.addRule("R", goodRule);
                List<String> matches = mm.rulesForJSONEvent(event);
                System.out.printf("For %s, %d matches\n", number, matches.size());
            } catch (Exception e) {
                System.out.println("Ouch! " + e.toString() + " = " + event);
            }
        }
    }

    @Test
    public void testGetPrecision() {
        Map<String, Long> testCases = new HashMap<>();
        testCases.put("1.23456789", 8L);
        testCases.put("1234567.89", 2L);
        testCases.put("0.000123456789", 12L);
        testCases.put("123456789", 0L);
        // numbers but not decimals
        testCases.put("1234", 0L);
        testCases.put("1E8", 0L);
        testCases.put("-5E9", 0L);
        testCases.put("1.2e3", 0L);
        testCases.put("120000e-3", 0L);
        testCases.put("0E2", 0L);
        testCases.put("Infinity", 0L);
        testCases.put("-Infinity", 0L);
        testCases.put("NaN", 0L);
        // training zeros
        testCases.put("0.0", 0L);
        testCases.put("-0.0", 0L);
        testCases.put("123.456000", 3L);
        testCases.put("123.0000456", 7L);
        testCases.put("123.040404040", 8L);
        // numbers with decimals and exponents
        testCases.put("1.234567898E8", 1L);
        testCases.put("1234567.89E-2", 4L);
        testCases.put("0.000123456789E1", 11L);

        for (Entry<String, Long> entry: testCases.entrySet()) {
            String input = entry.getKey();
            long expected = entry.getValue();
            long actual = ComparableNumber.getPrecision(input);
            assertEquals("For " + input + " got " + actual + " but expected " + expected,
                    expected, actual);
        }
    }

    @Test // FIXME
    public void testGetPrecisionErrors() {
        List<String> testCases = new ArrayList<>();
        // not numbers
        testCases.add("\"foo\"");
        testCases.add("\"1.2345\"");
        testCases.add("null");
        testCases.add("true");
        testCases.add("false");
        testCases.add("[]");
        testCases.add("{}");
        // malformed numbers
        testCases.add("1.2.3");
        testCases.add("1e2.3");
        testCases.add("1E2E3");
        testCases.add("E3");
        testCases.add("1E");
        testCases.add("1E-");
        testCases.add("1E+");
        testCases.add("1E+E3");
        testCases.add("1E3E2");
        testCases.add("1E--3");
        testCases.add("1E++3");
        testCases.add("1E.-3");
        testCases.add("1E.+");
        testCases.add("1E.+E3");

        for (String entry: testCases) {
            try {
                long actual = ComparableNumber.getPrecision(entry);
                fail("Expected error for " + entry + " but got " + actual);
            } catch (Exception e) {
                // expected
            }

        }
    }

    @Test
    public void WHEN_LotsOfLargeNumbers_THEN_OrderingIsNotLost() {
        Random random = new Random();
        int numberOfDoubles = 10_000_000;
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

            String s0 = ComparableNumber.generate(first);
            String s1 = ComparableNumber.generate(next);
            final char[] s0ca = s0.toCharArray();
            final char[] s1ca = s1.toCharArray();
            for(int j = 0; j < s0ca.length; j++) { // quick check
                if(s0ca[j] == s1ca[j]) continue;
                if(s0ca[j] < s1ca[j]) break;
                fail("failed: " + s1 + " vs " + s0 + " : " + next + " vs " + first);
            }
        }
    }

    @Test
    public void WHEN_LotsOfSixFractionalDigits_THEN_OrderingIsNotLost() {
        Random random = new Random();
        int numberOfDoubles = 10_000_000;
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

            String s0 = ComparableNumber.generate(first);
            String s1 = ComparableNumber.generate(next);
            final char[] s0ca = s0.toCharArray();
            final char[] s1ca = s1.toCharArray();
            for(int j = 0; j < s0ca.length; j++) {
                if(s0ca[j] == s1ca[j]) continue;
                if(s0ca[j] < s1ca[j]) break;
                fail("failed: " + s1 + " vs " + s0 + " : " + next + " vs " + first);
            }
        }
    }

    @Test
    public void WHEN_SixFractionalDigits_THEN_OrderingIsNotLost() {
        double[] lows = {-5_000_000_000.0, -4_999_999_999.999999, -4_999_999_999.999998};
        double[] highs = {4_999_999_999.999998, 4_999_999_999.999999, 5_000_000_000.0};

        // This looses precision
        for (double low : lows) { // FIXME REMOVE BEFORE MAINLINE MERGE
            String c = ComparableNumber.generate(low);
            System.out.printf("%f => %s\n", low, c);
        }
        for (double high : highs) { // FIXME REMOVE BEFORE MAINLINE MERGE
            String c = ComparableNumber.generate(high);
            System.out.printf("%f => %s\n", high, c);
        }


        for (int i = 1; i < lows.length; i++) {
            String s0 = ComparableNumber.generate(lows[i - 1]);
            String s1 = ComparableNumber.generate(lows[i]);
            System.out.println("i=" + i + " s0:" + s0 + " s1:" + s1);
            assertTrue(s0.compareTo(s1) < 0);
        }

        for (int i = 1; i < highs.length; i++) {
            String s0 = ComparableNumber.generate(highs[i - 1]);
            String s1 = ComparableNumber.generate(highs[i]);
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
}