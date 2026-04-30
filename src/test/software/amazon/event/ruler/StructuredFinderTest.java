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
 * Tests for {@link StructuredFinder} via {@code withStructuredMatching(true)}.
 *
 * Validates that structured matching produces identical results to the default
 * {@link GenericMachine#rulesForJSONEvent(String)} on all correctness cases,
 * then benchmarks both on large events.
 */
public class StructuredFinderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Structured matching must produce identical results to the default on every
     * correctness case. This is the primary correctness gate.
     */
    @Test
    public void structuredMatchesACOnAllCases() throws Exception {
        InputStream is = getClass().getResourceAsStream("/correctness-cases.json");
        JsonNode cases = MAPPER.readTree(is);
        int pass = 0;
        int fail = 0;

        System.out.printf("%n%-55s %-8s %-8s %-6s%n", "Case", "AC", "New", "OK?");

        for (JsonNode tc : cases) {
            String name = tc.get("name").asText();
            String rule = MAPPER.writeValueAsString(tc.get("rule"));
            String event = MAPPER.writeValueAsString(tc.get("event"));

            Machine m = new Machine();
            m.addRule("r1", rule);

            Machine sm = Machine.builder().withStructuredMatching(true).build();
            sm.addRule("r1", rule);

            List<String> acResult = m.rulesForJSONEvent(event);
            List<String> newResult = sm.rulesForJSONEvent(event);

            Collections.sort(acResult);
            Collections.sort(newResult);

            boolean ok = acResult.equals(newResult);
            if (ok) {
                pass++;
            } else {
                fail++;
            }

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
    @Test(timeout = 900000) // 15 minute timeout for the whole perf test
    public void perfComparison() throws Exception {
        System.out.printf("%n%-12s %-10s %-10s %-12s %-12s%n",
                "Scenario", "N", "KB", "Old(ms)", "New(ms)");

        perfRun("prim+scl",
                "{\"d\":{\"tags\":[{\"exists\":true}],\"type\":[{\"exists\":true}]}}",
                new int[]{1000, 5000, 10000, 50000, 100000},
                StructuredFinderTest::buildPrimArray, 1);

        perfRun("obj-both",
                "{\"d\":{\"a\":[{\"exists\":true}],\"b\":[{\"exists\":true}]}}",
                new int[]{1000, 5000, 10000, 20000},
                StructuredFinderTest::buildObjArray, 1);

        perfRun("nested2",
                "{\"a\":{\"b\":{\"x\":[{\"exists\":true}],\"y\":[{\"exists\":true}]}}}",
                new int[]{100, 500, 1000, 2000, 4000},
                StructuredFinderTest::buildNested2, 1);

        perfRun("customer",
                "{\"detail\":{\"data\":{\"name\":[{\"exists\":true}],\"domains\":{\"email\":[{\"exists\":true},{\"exists\":false}]},\"type\":[{\"exists\":true}]},\"context\":{\"companyId\":[{\"exists\":true}],\"userId\":[{\"exists\":true}],\"email\":[{\"exists\":true}]}}}",
                new int[]{1000, 5000, 10000, 50000},
                StructuredFinderTest::buildCustomerLike, 1);

        // Element contains large primitive array
        perfRun("elem+prim",
                "{\"d\":{\"tags\":[{\"exists\":true}],\"type\":[{\"exists\":true}]}}",
                new int[]{1000, 5000, 10000, 50000},
                StructuredFinderTest::buildElemWithPrimArray, 1);

        // Element contains nested object array
        perfRun("elem+obj",
                "{\"outer\":{\"inner\":{\"a\":[{\"exists\":true}],\"b\":[{\"exists\":true}]}}}",
                new int[]{1000, 5000, 10000},
                StructuredFinderTest::buildElemWithNestedObjArray, 1);

        // Sibling object arrays (cross-product via ACFinder fallback)
        perfRun("siblings",
                "{\"x\":{\"a\":[{\"exists\":true}]},\"y\":{\"b\":[{\"exists\":true}]}}",
                new int[]{100, 500, 1000},
                StructuredFinderTest::buildSiblingObjArrays, 1);

        // Sibling object arrays with SAME field names in rule
        perfRun("sib-same",
                "{\"x\":{\"a\":[{\"exists\":true}]},\"y\":{\"a\":[{\"exists\":true}]}}",
                new int[]{100, 500, 1000},
                StructuredFinderTest::buildSiblingsSameFieldName, 1);

        // Large parent scalar + object array (tests JSON string building overhead)
        perfRun("bigparent",
                "{\"d\":{\"a\":[{\"exists\":true}]},\"meta\":[{\"exists\":true}]}",
                new int[]{500, 5000, 10000, 50000, 100000},
                StructuredFinderTest::buildBigParentWithObjArray, 1);

        // 3-level deep nesting, single array at each level
        perfRun("deep3",
                "{\"a\":{\"b\":{\"c\":{\"x\":[{\"exists\":true}],\"y\":[{\"exists\":true}]}}}}",
                new int[]{50, 100, 200, 300, 400},
                StructuredFinderTest::buildDeep3Level, 1);

        // Single large object array, each element has many scalar fields
        perfRun("wide-elem",
                "{\"d\":{\"f0\":[{\"exists\":true}],\"f9\":[{\"exists\":true}]}}",
                new int[]{500, 1000, 5000, 10000},
                StructuredFinderTest::buildWideElements, 1);
    }

    // ==================== Event builders ====================

    static String buildPrimArray(int n) {
        StringBuilder s = new StringBuilder("{\"d\":{\"tags\":[");
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                s.append(",");
            }
            s.append("\"t").append(i).append("\"");
        }
        s.append("],\"type\":\"G\"}}");
        return s.toString();
    }

    static String buildObjArray(int n) {
        StringBuilder s = new StringBuilder("{\"d\":[");
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                s.append(",");
            }
            s.append("{\"a\":\"").append(i).append("\",\"b\":\"").append(i).append("\"}");
        }
        s.append("]}");
        return s.toString();
    }

    static String buildNested2(int n) {
        StringBuilder s = new StringBuilder("{\"a\":[");
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                s.append(",");
            }
            s.append("{\"b\":[");
            for (int j = 0; j < 10; j++) {
                if (j > 0) {
                    s.append(",");
                }
                s.append("{\"x\":\"").append(i).append("_").append(j)
                  .append("\",\"y\":\"").append(i).append("_").append(j).append("\"}");
            }
            s.append("]}");
        }
        s.append("]}");
        return s.toString();
    }

    static String buildCustomerLike(int n) {
        StringBuilder s = new StringBuilder("{\"detail\":{\"data\":{\"name\":[");
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                s.append(",");
            }
            s.append("\"u").append(i).append("\"");
        }
        s.append("],\"type\":\"G\",\"domains\":{\"email\":\"t@x\"}},\"context\":{\"companyId\":\"c\",\"userId\":\"u\",\"email\":\"a@b\"}}}");
        return s.toString();
    }

    /** Outer object array with 2 elements, each containing a large primitive array. */
    static String buildElemWithPrimArray(int n) {
        StringBuilder s = new StringBuilder("{\"d\":[");
        for (int e = 0; e < 2; e++) {
            if (e > 0) {
                s.append(",");
            }
            s.append("{\"tags\":[");
            for (int i = 0; i < n; i++) {
                if (i > 0) {
                    s.append(",");
                }
                s.append("\"t").append(e).append("_").append(i).append("\"");
            }
            s.append("],\"type\":\"G\"}");
        }
        s.append("]}");
        return s.toString();
    }

    /** Outer object array with 2 elements, each containing a nested object array of size N. */
    static String buildElemWithNestedObjArray(int n) {
        StringBuilder s = new StringBuilder("{\"outer\":[");
        for (int e = 0; e < 2; e++) {
            if (e > 0) {
                s.append(",");
            }
            s.append("{\"inner\":[");
            for (int i = 0; i < n; i++) {
                if (i > 0) {
                    s.append(",");
                }
                s.append("{\"a\":\"").append(e).append("_").append(i);
                s.append("\",\"b\":\"").append(e).append("_").append(i).append("\"}");
            }
            s.append("]}");
        }
        s.append("]}");
        return s.toString();
    }

    /** Two sibling object arrays, each with N elements. */
    static String buildSiblingObjArrays(int n) {
        StringBuilder s = new StringBuilder("{\"x\":[");
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                s.append(",");
            }
            s.append("{\"a\":\"x").append(i).append("\"}");
        }
        s.append("],\"y\":[");
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                s.append(",");
            }
            s.append("{\"b\":\"y").append(i).append("\"}");
        }
        s.append("]}");
        return s.toString();
    }

    /** Two sibling object arrays with the SAME field name "a" in both. */
    static String buildSiblingsSameFieldName(int n) {
        StringBuilder s = new StringBuilder("{\"x\":[");
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                s.append(",");
            }
            s.append("{\"a\":\"x").append(i).append("\"}");
        }
        s.append("],\"y\":[");
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                s.append(",");
            }
            s.append("{\"a\":\"y").append(i).append("\"}");
        }
        s.append("]}");
        return s.toString();
    }

    /** Large scalar field "meta" (10KB) + object array with N elements. */
    static String buildBigParentWithObjArray(int n) {
        StringBuilder meta = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            meta.append("abcdefghij");
        }
        StringBuilder s = new StringBuilder("{\"meta\":\"").append(meta).append("\",\"d\":[");
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                s.append(",");
            }
            s.append("{\"a\":\"v").append(i).append("\"}");
        }
        s.append("]}");
        return s.toString();
    }

    /** 3-level deep nesting: a[N] > b[10] > c[10], each leaf has x and y. */
    static String buildDeep3Level(int n) {
        StringBuilder s = new StringBuilder("{\"a\":[");
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                s.append(",");
            }
            s.append("{\"b\":[");
            for (int j = 0; j < 10; j++) {
                if (j > 0) {
                    s.append(",");
                }
                s.append("{\"c\":[");
                for (int k = 0; k < 10; k++) {
                    if (k > 0) {
                        s.append(",");
                    }
                    s.append("{\"x\":\"").append(i).append("_").append(j).append("_").append(k);
                    s.append("\",\"y\":\"").append(i).append("_").append(j).append("_").append(k).append("\"}");
                }
                s.append("]}");
            }
            s.append("]}");
        }
        s.append("]}");
        return s.toString();
    }

    /** Object array with N elements, each having 10 scalar fields. */
    static String buildWideElements(int n) {
        StringBuilder s = new StringBuilder("{\"d\":[");
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                s.append(",");
            }
            s.append("{");
            for (int f = 0; f < 10; f++) {
                if (f > 0) {
                    s.append(",");
                }
                s.append("\"f").append(f).append("\":\"v").append(i).append("_").append(f).append("\"");
            }
            s.append("}");
        }
        s.append("]}");
        return s.toString();
    }

    // ==================== Perf runner ====================

    interface EventGen {
        String build(int n);
    }

    private void perfRun(String label, String rule, int[] sizes, EventGen gen, int expected) throws Exception {
        Machine m = new Machine();
        m.addRule("r1", rule);

        Machine sm = Machine.builder().withStructuredMatching(true).build();
        sm.addRule("r1", rule);

        boolean oldTimedOut = false;

        for (int n : sizes) {
            String event = gen.build(n);
            double kb = event.length() / 1024.0;
            if (kb > 1100) {
                break;
            }

            // Structured: must always complete, assert correctness + perf up to 1MB
            sm.rulesForJSONEvent(event); // warmup
            long t1 = System.nanoTime();
            assertEquals(expected, sm.rulesForJSONEvent(event).size());
            long newMs = (System.nanoTime() - t1) / 1000000;
            assertTrue(label + " N=" + n + " structured took " + newMs + "ms (limit 15s)",
                    newMs < 15000);

            // Old method: best-effort comparison only on small payloads.
            // On large arrays the original ACFinder can take minutes or OOM,
            // which would cause CI timeouts. Skip if payload > 50KB or already failed.
            String oldStr;
            if (oldTimedOut || kb > 50) {
                oldStr = oldTimedOut ? "SKIP" : "—";
            } else {
                try {
                    m.rulesForJSONEvent(event); // warmup
                    long t0 = System.nanoTime();
                    m.rulesForJSONEvent(event);
                    long oldMs = (System.nanoTime() - t0) / 1000000;
                    if (oldMs > 5000) {
                        oldStr = "SLOW(" + oldMs + "ms)";
                        oldTimedOut = true;
                    } else {
                        oldStr = oldMs + "ms";
                    }
                } catch (Throwable e) {
                    oldStr = e.getClass().getSimpleName();
                    oldTimedOut = true;
                }
            }

            System.out.printf("%-12s %-10d %-10.1f %-12s %-12s%n", label, n, kb, oldStr, newMs + "ms");
        }
    }
}
