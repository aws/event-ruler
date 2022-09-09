package software.amazon.event.ruler;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
                -999999999.99999, -999999999.99, -10000, -122.413496524705309, -0.000002,
                0, 0.000001, 3.8, 3.9, 11, 12, 122.415028278886751, 2.5e4, 999999999.999998, 999999999.999999,
                4_999_999_999.99997, 4_999_999_999.99998, 4_999_999_999.99999, Constants.FIVE_BILLION
        };
        for (int i = 1; i < data.length; i++) { // -122.415028278886751
            String s0 = ComparableNumber.generate(data[i-1]);
            String s1 = ComparableNumber.generate(data[i]);
            System.out.println("i=" + i + " s0:"+s0+" s1:"+s1 );
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