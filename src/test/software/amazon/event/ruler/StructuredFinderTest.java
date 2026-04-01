package software.amazon.event.ruler;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Tests for {@link StructuredFinder} via {@link GenericMachine#rulesForJSONEventNonBlocking(String)}.
 *
 * Validates that the new method produces identical results to the deprecated
 * {@link GenericMachine#rulesForJSONEvent(String)} on all correctness cases,
 * then benchmarks both on large events.
 */
public class StructuredFinderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * The new method must produce identical results to the old method on every
     * correctness case. This is the primary correctness gate.
     */
    @Test
    public void nonBlockingMatchesACOnAllCases() throws Exception {
        InputStream is = getClass().getResourceAsStream("/correctness-cases.json");
        JsonNode cases = MAPPER.readTree(is);
        int pass = 0, fail = 0;

        System.out.printf("%n%-55s %-8s %-8s %-6s%n", "Case", "AC", "New", "OK?");

        for (JsonNode tc : cases) {
            String name = tc.get("name").asText();
            String rule = MAPPER.writeValueAsString(tc.get("rule"));
            String event = MAPPER.writeValueAsString(tc.get("event"));

            Machine m = new Machine();
            m.addRule("r1", rule);

            List<String> acResult = m.rulesForJSONEvent(event);
            List<String> newResult = m.rulesForJSONEventNonBlocking(event);

            Collections.sort(acResult);
            Collections.sort(newResult);

            boolean ok = acResult.equals(newResult);
            if (ok) pass++; else fail++;

            System.out.printf("%-55s %-8s %-8s %-6s%n", name,
                    acResult.isEmpty() ? "no" : "MATCH",
                    newResult.isEmpty() ? "no" : "MATCH",
                    ok ? "OK" : "FAIL");

            assertEquals(name + ": new method must match old", acResult, newResult);
        }

        System.out.printf("Passed: %d, Failed: %d%n", pass, fail);
        assertEquals(0, fail);
    }

    /** Performance comparison on events up to ~500KB. */
    @Test(timeout = 300000) // 5 minute timeout for the whole perf test
    public void perfComparison() throws Exception {
        System.out.printf("%n%-12s %-10s %-10s %-12s %-12s%n",
                "Scenario", "N", "KB", "Old(ms)", "New(ms)");

        perfRun("prim+scl",
                "{\"d\":{\"tags\":[{\"exists\":true}],\"type\":[{\"exists\":true}]}}",
                new int[]{1000, 5000, 10000, 50000, 100000},
                StructuredFinderTest::buildPrimArray, 1);

        perfRun("obj-both",
                "{\"d\":{\"a\":[{\"exists\":true}],\"b\":[{\"exists\":true}]}}",
                new int[]{1000, 2000, 5000, 10000, 20000},
                StructuredFinderTest::buildObjArray, 1);

        perfRun("nested2",
                "{\"a\":{\"b\":{\"x\":[{\"exists\":true}],\"y\":[{\"exists\":true}]}}}",
                new int[]{100, 200, 500, 1000, 2000},
                StructuredFinderTest::buildNested2, 1);

        perfRun("customer",
                "{\"detail\":{\"data\":{\"name\":[{\"exists\":true}],\"domains\":{\"email\":[{\"exists\":true},{\"exists\":false}]},\"type\":[{\"exists\":true}]},\"context\":{\"companyId\":[{\"exists\":true}],\"userId\":[{\"exists\":true}],\"email\":[{\"exists\":true}]}}}",
                new int[]{1000, 5000, 10000, 50000},
                StructuredFinderTest::buildCustomerLike, 1);
    }

    // ==================== Event builders ====================

    static String buildPrimArray(int n) {
        StringBuilder s = new StringBuilder("{\"d\":{\"tags\":[");
        for (int i = 0; i < n; i++) { if (i > 0) s.append(","); s.append("\"t").append(i).append("\""); }
        s.append("],\"type\":\"G\"}}"); return s.toString();
    }

    static String buildObjArray(int n) {
        StringBuilder s = new StringBuilder("{\"d\":[");
        for (int i = 0; i < n; i++) { if (i > 0) s.append(","); s.append("{\"a\":\"").append(i).append("\",\"b\":\"").append(i).append("\"}"); }
        s.append("]}"); return s.toString();
    }

    static String buildNested2(int n) {
        StringBuilder s = new StringBuilder("{\"a\":[");
        for (int i = 0; i < n; i++) {
            if (i > 0) s.append(",");
            s.append("{\"b\":[");
            for (int j = 0; j < 10; j++) {
                if (j > 0) s.append(",");
                s.append("{\"x\":\"").append(i).append("_").append(j)
                  .append("\",\"y\":\"").append(i).append("_").append(j).append("\"}");
            }
            s.append("]}");
        }
        s.append("]}"); return s.toString();
    }

    static String buildCustomerLike(int n) {
        StringBuilder s = new StringBuilder("{\"detail\":{\"data\":{\"name\":[");
        for (int i = 0; i < n; i++) { if (i > 0) s.append(","); s.append("\"u").append(i).append("\""); }
        s.append("],\"type\":\"G\",\"domains\":{\"email\":\"t@x\"}},\"context\":{\"companyId\":\"c\",\"userId\":\"u\",\"email\":\"a@b\"}}}");
        return s.toString();
    }

    // ==================== Perf runner ====================

    interface EventGen { String build(int n); }

    private void perfRun(String label, String rule, int[] sizes, EventGen gen, int expected) throws Exception {
        Machine m = new Machine();
        m.addRule("r1", rule);
        boolean oldTimedOut = false;

        for (int n : sizes) {
            String event = gen.build(n);
            double kb = event.length() / 1024.0;
            if (kb > 1100) break;

            // New method: must always complete, assert correctness + perf up to 1MB
            m.rulesForJSONEventNonBlocking(event); // warmup
            long t1 = System.nanoTime();
            assertEquals(expected, m.rulesForJSONEventNonBlocking(event).size());
            long newMs = (System.nanoTime() - t1) / 1000000;
            assertTrue(label + " N=" + n + " new method took " + newMs + "ms (limit 10s)",
                    newMs < 10000);

            // Old method: run with 10s timeout, mark as TIMEOUT if exceeded
            String oldStr;
            if (oldTimedOut) {
                oldStr = "TIMEOUT";
            } else {
                m.rulesForJSONEvent(event); // warmup
                long t0 = System.nanoTime();
                assertEquals(expected, m.rulesForJSONEvent(event).size());
                long oldMs = (System.nanoTime() - t0) / 1000000;
                if (oldMs > 10000) {
                    oldStr = "TIMEOUT";
                    oldTimedOut = true;
                } else {
                    oldStr = oldMs + "ms";
                }
            }

            System.out.printf("%-12s %-10d %-10.1f %-12s %-12s%n", label, n, kb, oldStr, newMs + "ms");
        }
    }
}
