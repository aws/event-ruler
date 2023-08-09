package software.amazon.event.ruler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Runs benchmarks to measure various aspects of Ruler performance with the help of JUnit. Normally, most of
 *  the tests here are marked @Ignore because they are not required as part of normal unit testing.  However, after
 *  any nontrivial change to Ruler code, the tests should be re-run and the results checked against the performance
 *  numbers given in README.md to ensure there hasn't been a performance regression.
 *
 * Several of the benchmarks operate on a large file containing 213068 JSON records averaging about 900 bytes in size.
 *  There are two slightly varying versions, citylots.json.gz and citylots2.json.gz.  Between the two of them they
 *  total ~400Mbytes, which makes Ruler a little slow to check out from git.
 */

public class Benchmarks {

    // original citylots
    private static final String CITYLOTS_JSON = "src/test/data/citylots.json.gz";

    // revised citylots with structured arrays
    private static final String CITYLOTS_2 = "src/test/data/citylots2.json.gz";

    private final String[] EXACT_RULES = {
            "{\n" +
                    "  \"properties\": {\n" +
                    "    \"MAPBLKLOT\": [ \"1430022\" ]\n" +
                    "  }" +
                    "}",
            "{\n" +
                    "  \"properties\": {\n" +
                    "    \"MAPBLKLOT\": [ \"2607117\" ]\n" +
                    "  }\n" +
                    "}",
            "{\n" +
                    "  \"properties\": {\n" +
                    "    \"MAPBLKLOT\": [ \"2607218\" ]\n" +
                    "  }\n" +
                    "}",
            "{\n" +
                    "  \"properties\": {\n" +
                    "    \"MAPBLKLOT\": [ \"3745012\" ]\n" +
                    "  }\n" +
                    "}",
            "{\n" +
                    "  \"properties\": {\n" +
                    "    \"MAPBLKLOT\": [ \"VACSTWIL\" ]\n" +
                    "  }\n" +
                    "}"
    };
    private final int[] EXACT_MATCHES = { 1, 101, 35, 655, 1 };

    private final String[] WILDCARD_RULES = {
            "{\n" +
                    "  \"properties\": {\n" +
                    "    \"MAPBLKLOT\": [ { \"wildcard\": \"143*\" } ]\n" +
                    "  }" +
                    "}",
            "{\n" +
                    "  \"properties\": {\n" +
                    "    \"MAPBLKLOT\": [ { \"wildcard\": \"2*0*1*7\" } ]\n" +
                    "  }\n" +
                    "}",
            "{\n" +
                    "  \"properties\": {\n" +
                    "    \"MAPBLKLOT\": [ { \"wildcard\": \"*218\" } ]\n" +
                    "  }\n" +
                    "}",
            "{\n" +
                    "  \"properties\": {\n" +
                    "    \"MAPBLKLOT\": [ { \"wildcard\": \"3*5*2\" } ]\n" +
                    "  }\n" +
                    "}",
            "{\n" +
                    "  \"properties\": {\n" +
                    "    \"MAPBLKLOT\": [ { \"wildcard\": \"VA*IL\" } ]\n" +
                    "  }\n" +
                    "}"
    };
    private final int[] WILDCARD_MATCHES = { 490, 713, 43, 2540, 1 };

    private final String[] PREFIX_RULES = {
      "{\n" +
              "  \"properties\": {\n" +
              "    \"STREET\": [ { \"prefix\": \"AC\" } ]\n" +
              "  }\n" +
              "}",
      "{\n" +
              "  \"properties\": {\n" +
              "    \"STREET\": [ { \"prefix\": \"BL\" } ]\n" +
              "  }\n" +
              "}",
      "{\n" +
              "  \"properties\": {\n" +
              "    \"STREET\": [ { \"prefix\": \"DR\" } ]\n" +
              "  }\n" +
              "}",
      "{\n" +
              "  \"properties\": {\n" +
              "    \"STREET\": [ { \"prefix\": \"FU\" } ]\n" +
              "  }\n" +
              "}",
      "{\n" +
              "  \"properties\": {\n" +
              "    \"STREET\": [ { \"prefix\": \"RH\" } ]\n" +
              "  }\n" +
              "}"
    };
    private final int[] PREFIX_MATCHES = { 24, 442, 38, 2387, 328 };

    private final String[] SUFFIX_RULES = {
            "{\n" +
                    "  \"properties\": {\n" +
                    "    \"STREET\": [ { \"suffix\": \"ON\" } ]\n" +
                    "  }\n" +
                    "}",
            "{\n" +
                    "  \"properties\": {\n" +
                    "    \"STREET\": [ { \"suffix\": \"KE\" } ]\n" +
                    "  }\n" +
                    "}",
            "{\n" +
                    "  \"properties\": {\n" +
                    "    \"STREET\": [ { \"suffix\": \"MM\" } ]\n" +
                    "  }\n" +
                    "}",
            "{\n" +
                    "  \"properties\": {\n" +
                    "    \"STREET\": [ { \"suffix\": \"ING\" } ]\n" +
                    "  }\n" +
                    "}",
            "{\n" +
                    "  \"properties\": {\n" +
                    "    \"STREET\": [ { \"suffix\": \"GO\" } ]\n" +
                    "  }\n" +
                    "}"
    };
    private final int[] SUFFIX_MATCHES = { 17921, 871, 13, 1963, 682 };

    private final String[] EQUALS_IGNORE_CASE_RULES = {
            "{\n" +
                    "  \"properties\": {\n" +
                    "    \"STREET\": [ { \"equals-ignore-case\": \"jefferson\" } ]\n" +
                    "  }\n" +
                    "}",
            "{\n" +
                    "  \"properties\": {\n" +
                    "    \"STREET\": [ { \"equals-ignore-case\": \"bEaCh\" } ]\n" +
                    "  }\n" +
                    "}",
            "{\n" +
                    "  \"properties\": {\n" +
                    "    \"STREET\": [ { \"equals-ignore-case\": \"HyDe\" } ]\n" +
                    "  }\n" +
                    "}",
            "{\n" +
                    "  \"properties\": {\n" +
                    "    \"STREET\": [ { \"equals-ignore-case\": \"CHESTNUT\" } ]\n" +
                    "  }\n" +
                    "}",
            "{\n" +
                    "  \"properties\": {\n" +
                    "    \"ST_TYPE\": [ { \"equals-ignore-case\": \"st\" } ]\n" +
                    "  }\n" +
                    "}"
    };
    private final int[] EQUALS_IGNORE_CASE_MATCHES = { 131, 211, 1758, 825, 116386 };

    private final String[] COMPLEX_ARRAYS_RULES = {
      "{\n" +
              "  \"geometry\": {\n" +
              "    \"type\": [ \"Polygon\" ],\n" +
              "    \"coordinates\": {\n" +
              "      \"x\": [ { \"numeric\": [ \"=\", -122.42916360922355 ] } ]\n" +
              "    }\n" +
              "  }\n" +
              "}",
      "{\n" +
              "  \"geometry\": {\n" +
              "    \"type\": [ \"MultiPolygon\" ],\n" +
              "    \"coordinates\": {\n" +
              "      \"y\": [ { \"numeric\": [ \"=\", 37.729900216217324 ] } ]\n" +
              "    }\n" +
              "  }\n" +
              "}",
      "{\n" +
              "  \"geometry\": {\n" +
              "    \"coordinates\": {\n" +
              "      \"x\": [ { \"numeric\": [ \"<\", -122.41600944012424 ] } ]\n" +
              "    }\n" +
              "  }\n" +
              "}",
      "{\n" +
              "  \"geometry\": {\n" +
              "    \"coordinates\": {\n" +
              "      \"x\": [ { \"numeric\": [ \">\", -122.41600944012424 ] } ]\n" +
              "    }\n" +
              "  }\n" +
              "}",
      "{\n" +
              "  \"geometry\": {\n" +
              "    \"coordinates\": {\n" +
              "      \"x\": [ { \"numeric\": [ \">\",  -122.46471267081272, \"<\", -122.4063085128395 ] } ]\n" +
              "    }\n" +
              "  }\n" +
              "}"
    };
    private final int[] COMPLEX_ARRAYS_MATCHES = { 227, 2, 149444, 64368, 127485 };

    private final String[] NUMERIC_RULES = {
            "{\n" +
                    "  \"geometry\": {\n" +
                    "    \"type\": [ \"Polygon\" ],\n" +
                    "    \"firstCoordinates\": {\n" +
                    "      \"x\": [ { \"numeric\": [ \"=\", -122.42916360922355 ] } ]\n" +
                    "    }\n" +
                    "  }\n" +
                    "}",
            "{\n" +
                    "  \"geometry\": {\n" +
                    "    \"type\": [ \"MultiPolygon\" ],\n" +
                    "    \"firstCoordinates\": {\n" +
                    "      \"z\": [ { \"numeric\": [ \"=\", 0 ] } ]\n" +
                    "    }\n" +
                    "  }\n" +
                    "}",
            "{\n" +
                    "  \"geometry\": {\n" +
                    "    \"firstCoordinates\": {\n" +
                    "      \"x\": [ { \"numeric\": [ \"<\", -122.41600944012424 ] } ]\n" +
                    "    }\n" +
                    "  }\n" +
                    "}",
            "{\n" +
                    "  \"geometry\": {\n" +
                    "    \"firstCoordinates\": {\n" +
                    "      \"x\": [ { \"numeric\": [ \">\", -122.41600944012424 ] } ]\n" +
                    "    }\n" +
                    "  }\n" +
                    "}",
            "{\n" +
                    "  \"geometry\": {\n" +
                    "    \"firstCoordinates\": {\n" +
                    "      \"x\": [ { \"numeric\": [ \">\",  -122.46471267081272, \"<\", -122.4063085128395 ] } ]\n" +
                    "    }\n" +
                    "  }\n" +
                    "}"
    };
    private final int[] NUMERIC_MATCHES = { 8, 120, 148943, 64120, 127053 };

    private final String[] ANYTHING_BUT_RULES = {
      "{\n" +
              "  \"properties\": {\n" +
              "    \"STREET\": [ { \"anything-but\": [ \"FULTON\" ] } ]\n" +
              "  }\n" +
              "}",
      "{\n" +
              "  \"properties\": {\n" +
              "    \"STREET\": [ { \"anything-but\": [ \"MASON\" ] } ]\n" +
              "  }\n" +
              "}",
      "{\n" +
              "  \"properties\": {\n" +
              "    \"ST_TYPE\": [ { \"anything-but\": [ \"ST\" ] } ]\n" +
              "  }\n" +
              "}",
      "{\n" +
              "  \"geometry\": {\n" +
              "    \"type\": [ {\"anything-but\": [ \"Polygon\" ] } ]\n" +
              "  }\n" +
              "}",
      "{\n" +
              "  \"properties\": {\n" +
              "    \"FROM_ST\": [ { \"anything-but\": [ \"441\" ] } ]\n" +
              "  }\n" +
              "}"
    };
    private final int[] ANYTHING_BUT_MATCHES = { 211158, 210411, 96682, 120, 210615 };

    private final String[] ANYTHING_BUT_IGNORE_CASE_RULES = {
      "{\n" +
              "  \"properties\": {\n" +
              "    \"STREET\": [ { \"anything-but\": {\"equals-ignore-case\": [ \"Fulton\" ] } } ]\n" +
              "  }\n" +
              "}", 
      "{\n" +
              "  \"properties\": {\n" +
              "    \"STREET\": [ { \"anything-but\": {\"equals-ignore-case\": [ \"Mason\" ] } } ]\n" + 
              "  }\n" +
              "}",
      "{\n" +
              "  \"properties\": {\n" +
              "    \"ST_TYPE\": [ { \"anything-but\": {\"equals-ignore-case\": [ \"st\" ] } } ]\n" +
              "  }\n" +
              "}",
      "{\n" +
              "  \"geometry\": {\n" +
              "    \"type\": [ {\"anything-but\": {\"equals-ignore-case\": [ \"polygon\" ] } } ]\n" +
              "  }\n" +
              "}",
      "{\n" +
              "  \"properties\": {\n" +
              "    \"FROM_ST\": [ { \"anything-but\": {\"equals-ignore-case\": [ \"441\" ] } } ]\n" +
              "  }\n" +  
              "}"   
    };              
    private final int[] ANYTHING_BUT_IGNORE_CASE_MATCHES = { 211158, 210411, 96682, 120, 210615 };


    private final String[] ANYTHING_BUT_PREFIX_RULES = {
      "{\n" +
              "  \"properties\": {\n" +
              "    \"STREET\": [ { \"anything-but\": {\"prefix\": \"FULTO\" } } ]\n" +
              "  }\n" +
              "}",
      "{\n" +
              "  \"properties\": {\n" +
              "    \"STREET\": [ { \"anything-but\": { \"prefix\": \"MASO\"} } ]\n" +
              "  }\n" +
              "}",
      "{\n" +
              "  \"properties\": {\n" +
              "    \"ST_TYPE\": [ { \"anything-but\": {\"prefix\": \"S\"}  } ]\n" +
              "  }\n" +
              "}",
      "{\n" +
              "  \"geometry\": {\n" +
              "    \"type\": [ {\"anything-but\": {\"prefix\": \"Poly\"} } ]\n" +
              "  }\n" +
              "}",
      "{\n" +
              "  \"properties\": {\n" +
              "    \"FROM_ST\": [ { \"anything-but\": {\"prefix\": \"44\"} } ]\n" +
              "  }\n" +
              "}"
    };
    private final int[] ANYTHING_BUT_PREFIX_MATCHES = { 211158, 210118, 96667, 120, 209091 };

    private final String[] ANYTHING_BUT_SUFFIX_RULES = {
      "{\n" +
              "  \"properties\": {\n" +
              "    \"STREET\": [ { \"anything-but\": {\"suffix\": \"ULTON\" } } ]\n" +
              "  }\n" +
              "}",
      "{\n" +
              "  \"properties\": {\n" +
              "    \"STREET\": [ { \"anything-but\": { \"suffix\": \"ASON\"} } ]\n" +
              "  }\n" +
              "}",
      "{\n" +
              "  \"properties\": {\n" +
              "    \"ST_TYPE\": [ { \"anything-but\": {\"suffix\": \"T\"}  } ]\n" +
              "  }\n" +
              "}",
      "{\n" +
              "  \"geometry\": {\n" +
              "    \"type\": [ {\"anything-but\": {\"suffix\": \"olygon\"} } ]\n" +
              "  }\n" +
              "}",
      "{\n" +
              "  \"properties\": {\n" +
              "    \"FROM_ST\": [ { \"anything-but\": {\"suffix\": \"41\"} } ]\n" +
              "  }\n" +
              "}"
    };
    private final int[] ANYTHING_BUT_SUFFIX_MATCHES = { 211136, 210411, 94908, 0, 209055 };

    // This needs to be run with -Xms2g -Xmx2g (Dunno about the 2g but the same Xms and Xmx, and big enough
    //  to hold several copies of the 200M in citylots2
    private final static int NUMBER_OF_RULES = 400000;
    private final static int NUMBER_OF_FIELD_NAMES = 10;

    @Test
    public void ruleMemoryBenchmark() throws Exception {

        // 10 random field names
        String[] fieldNames = new String[NUMBER_OF_FIELD_NAMES];
        for (int i = 0; i < NUMBER_OF_FIELD_NAMES; i++) {
            fieldNames[i] = randomAscii(12);
        }

        // now we're going to add 10K random rules and see how much memory it uses
        String ruleTemplate = "{\n" +
                "  \"Abernathy\": {\n" +
                "    \"-FIELD1\": [ -NUMBER1 ]\n" +
                "  },\n" +
                "  \"Barnard\": {\n" +
                "    \"Oliver\": [ { \"prefix\": \"-PREFIX\" } ]\n" +
                "  }\n" +
                "}";
        List<String> rules = new ArrayList<>();
        Machine mm = new Machine();
        for (int i = 0; i < NUMBER_OF_RULES; i++) {
            String rule = ruleTemplate;
            rule = rule.replace("-FIELD1", fieldNames[i % NUMBER_OF_FIELD_NAMES]);
            //rule = rule.replace("-NUMBER1", Double.toString(1000000.0 * rand.nextDouble()));
            rule = rule.replace("-NUMBER1", Integer.toString(rand.nextInt(100)));
            rule = rule.replace("-PREFIX", randomAscii(6));
            rules.add(rule);

            // handy when fooling with rule templates
            // if (i < 12) {
            //    System.out.println("R: " + rule);
            // }
        }

        System.gc();
        long memBefore = Runtime.getRuntime().freeMemory();
        int sizeBefore = mm.approximateObjectCount();
        System.out.printf("Before: %.1f (%d)\n", 1.0 * memBefore / 1000000, sizeBefore);
        for (int i = 0; i < rules.size(); i++) {
            mm.addRule("mm" + i, rules.get(i));
        }
        System.gc();
        long memAfter = Runtime.getRuntime().freeMemory();
        int sizeAfter = mm.approximateObjectCount();
        System.out.printf("After: %.1f (%d)\n", 1.0 * memAfter / 1000000, sizeAfter);
        int perRuleMem = (int) ((1.0 * (memAfter - memBefore)) / rules.size());
        int perRuleSize = (int) ((1.0 * (sizeAfter - sizeBefore)) / rules.size());
        System.out.println("Per rule: " + perRuleMem + " (" + perRuleSize + ")");
        rules.clear();
    }

    // This needs to be run with -Xms2g -Xmx2g
    // Only use exact match to verify exact match memory optimization change.
    @Test
    public void exactRuleMemoryBemchmark() throws Exception {

        // 10 random field names
        String[] fieldNames = new String[NUMBER_OF_FIELD_NAMES];
        for (int i = 0; i < NUMBER_OF_FIELD_NAMES; i++) {
            fieldNames[i] = randomAscii(12);
        }

        // now we're going to add 10K random rules and see how much memory it uses
        String ruleTemplate = "{\n" +
                "  \"Abernathy\": {\n" +
                "    \"-FIELD1\": [ -STR1 ]\n" +
                "  },\n" +
                "  \"Barnard\": {\n" +
                "    \"Oliver\": [ -STR2 ]\n" +
                "  }\n" +
                "}";
        List<String> rules = new ArrayList<>();
        Machine mm = new Machine();
        for (int i = 0; i < NUMBER_OF_RULES; i++) {
            String rule = ruleTemplate;
            rule = rule.replace("-FIELD1", fieldNames[i % NUMBER_OF_FIELD_NAMES]);
            rule = rule.replace("-STR1", "\"" + randomAscii(20) + "\"");
            rule = rule.replace("-STR2", "\"" + randomAscii(20) + "\"");
            rules.add(rule);
        }

        System.gc();
        long memBefore = Runtime.getRuntime().freeMemory();
        int sizeBefore = mm.approximateObjectCount();
        System.out.printf("Before: %.1f (%d)\n", 1.0 * memBefore / 1000000, sizeBefore);
        for (int i = 0; i < rules.size(); i++) {
            mm.addRule("mm" + i, rules.get(i));
        }
        System.gc();
        long memAfter = Runtime.getRuntime().freeMemory();
        int sizeAfter = mm.approximateObjectCount();
        System.out.printf("After: %.1f (%d)\n", 1.0 * memAfter / 1000000, sizeAfter);
        int perRuleMem = (int) ((1.0 * (memAfter - memBefore)) / rules.size());
        int perRuleSize = (int) ((1.0 * (sizeAfter - sizeBefore)) / rules.size());
        System.out.println("Per rule: " + perRuleMem + " (" + perRuleSize + ")");
        rules.clear();
    }

    @Test
    public void AnythingButPerformanceBenchmark() throws Exception {
        readCityLots2();
        final Machine m = new Machine();
        String rule = "{\n" +
                "  \"properties\": {\n" +
                "    \"ODD_EVEN\": [ {\"anything-but\":\"E\"} ],\n" +
                "    \"ST_TYPE\": [ {\"anything-but\":\"ST\"} ]\n" +
                "  },\n" +
                "  \"geometry\": {\n" +
                "    \"coordinates\": {\n" +
                "      \"z\": [ {\"anything-but\": 1} ] \n" +
                "    }\n" +
                "  }\n" +
                "}";
        m.addRule("r", rule);
        int count = 0;
        int matched = 0;
        long before = System.currentTimeMillis();
        for (String lot : citylots2) {
            count++;
            if ((count % 10000) == 0) {
                System.out.println("Lots: " + count);
            }
            if (m.rulesForJSONEvent(lot).size() != 0) {
                matched++;
            }
        }
        assertEquals(52527, matched);  // from grep
        System.out.println("Matched: " + matched);
        long after = System.currentTimeMillis();
        System.out.println("Lines: " + citylots2.size() +
                ", Msec: " + (after - before));
        double eps = (1000.0 / (1.0 * (after - before) / citylots2.size()));
        System.out.println("Events/sec: " + String.format("%.1f", eps));
    }

    @Test
    public void CL2NoCompileBenchmark() throws Exception {
        readCityLots2();
        final Machine staticMachine = new Machine();
        Map<String, Integer> expected = setRules(staticMachine);

        int count = 0;
        Map<String, AtomicInteger> received = new HashMap<>();
        Set<String> rules = expected.keySet();
        System.out.println("Finding Rules...");
        long before = System.currentTimeMillis();
        for (String lot : citylots2) {
            for (String rule : rules) {
                if (Ruler.matchesRule(lot, ruleJSON.get(rule))) {
                    incrRuleCount(rule, received);
                }
            }
            count++;
            if ((count % 10000) == 0) {
                System.out.println("Lots: " + count);
            }
        }
        long after = System.currentTimeMillis();
        System.out.println("Lines: " + citylots2.size() +
                ", Msec: " + (after - before));
        double eps = (1000.0 / (1.0 * (after - before) / citylots2.size()));
        System.out.println("Events/sec: " + String.format("%.1f", eps));
        System.out.println(" Rules/sec: " + String.format("%.1f", eps * rules.size()));
        for (String rule : rules) {
            assertEquals(rule, (long) expected.get(rule), received.get(rule).get());
        }
    }

    @Test
    public void CL2Benchmark() throws Exception {
        readCityLots2();
        Benchmarker bm;

        // initial run to stabilize memory
        bm = new Benchmarker();
        bm.addRules(EXACT_RULES, EXACT_MATCHES);
        bm.run(citylots2);

        bm = new Benchmarker();

        bm.addRules(EXACT_RULES, EXACT_MATCHES);
        bm.run(citylots2);
        System.out.println("EXACT events/sec: " + String.format("%.1f", bm.getEPS()));

        bm = new Benchmarker();

        bm.addRules(WILDCARD_RULES, WILDCARD_MATCHES);
        bm.run(citylots2);
        System.out.println("WILDCARD events/sec: " + String.format("%.1f", bm.getEPS()));

        bm = new Benchmarker();

        bm.addRules(PREFIX_RULES, PREFIX_MATCHES);
        bm.run(citylots2);
        System.out.println("PREFIX events/sec: " + String.format("%.1f", bm.getEPS()));

        bm = new Benchmarker();

        bm.addRules(SUFFIX_RULES, SUFFIX_MATCHES);
        bm.run(citylots2);
        System.out.println("SUFFIX events/sec: " + String.format("%.1f", bm.getEPS()));

        bm = new Benchmarker();

        bm.addRules(EQUALS_IGNORE_CASE_RULES, EQUALS_IGNORE_CASE_MATCHES);
        bm.run(citylots2);
        System.out.println("EQUALS_IGNORE_CASE events/sec: " + String.format("%.1f", bm.getEPS()));

        bm = new Benchmarker();

        bm.addRules(NUMERIC_RULES, NUMERIC_MATCHES);
        bm.run(citylots2);
        System.out.println("NUMERIC events/sec: " + String.format("%.1f", bm.getEPS()));

        bm = new Benchmarker();

        bm.addRules(ANYTHING_BUT_RULES, ANYTHING_BUT_MATCHES);
        bm.run(citylots2);
        System.out.println("ANYTHING-BUT events/sec: " + String.format("%.1f", bm.getEPS()));

        bm = new Benchmarker();

        bm.addRules(ANYTHING_BUT_IGNORE_CASE_RULES, ANYTHING_BUT_IGNORE_CASE_MATCHES);
        bm.run(citylots2);
        System.out.println("ANYTHING-BUT-IGNORE-CASE events/sec: " + String.format("%.1f", bm.getEPS()));

        bm = new Benchmarker();

        bm.addRules(ANYTHING_BUT_PREFIX_RULES, ANYTHING_BUT_PREFIX_MATCHES);
        bm.run(citylots2);
        System.out.println("ANYTHING-BUT-PREFIX events/sec: " + String.format("%.1f", bm.getEPS()));

        bm = new Benchmarker();

        bm.addRules(ANYTHING_BUT_SUFFIX_RULES, ANYTHING_BUT_SUFFIX_MATCHES);
        bm.run(citylots2);
        System.out.println("ANYTHING-BUT-SUFFIX events/sec: " + String.format("%.1f", bm.getEPS()));

        bm = new Benchmarker();

        bm.addRules(COMPLEX_ARRAYS_RULES, COMPLEX_ARRAYS_MATCHES);
        bm.run(citylots2);
        System.out.println("COMPLEX_ARRAYS events/sec: " + String.format("%.1f", bm.getEPS()));

        // skips complex arrays matchers because their slowness can hide improvements
        // and regressions for other matchers. Remove this once we find ways to make
        // arrays fast enough to others matchers
        bm = new Benchmarker();

        bm.addRules(NUMERIC_RULES, NUMERIC_MATCHES);
        bm.addRules(EXACT_RULES, EXACT_MATCHES);
        bm.addRules(PREFIX_RULES, PREFIX_MATCHES);
        bm.addRules(ANYTHING_BUT_RULES, ANYTHING_BUT_MATCHES);
        bm.addRules(ANYTHING_BUT_IGNORE_CASE_RULES, ANYTHING_BUT_IGNORE_CASE_MATCHES);
        bm.addRules(ANYTHING_BUT_PREFIX_RULES, ANYTHING_BUT_PREFIX_MATCHES);
        bm.addRules(ANYTHING_BUT_SUFFIX_RULES, ANYTHING_BUT_SUFFIX_MATCHES);
        bm.run(citylots2);
        System.out.println("PARTIAL_COMBO events/sec: " + String.format("%.1f", bm.getEPS()));

        bm = new Benchmarker();
        bm.addRules(NUMERIC_RULES, NUMERIC_MATCHES);
        bm.addRules(EXACT_RULES, EXACT_MATCHES);
        bm.addRules(PREFIX_RULES, PREFIX_MATCHES);
        bm.addRules(ANYTHING_BUT_RULES, ANYTHING_BUT_MATCHES);
        bm.addRules(ANYTHING_BUT_IGNORE_CASE_RULES, ANYTHING_BUT_IGNORE_CASE_MATCHES);
        bm.addRules(ANYTHING_BUT_PREFIX_RULES, ANYTHING_BUT_PREFIX_MATCHES);
        bm.addRules(ANYTHING_BUT_SUFFIX_RULES, ANYTHING_BUT_SUFFIX_MATCHES);
        bm.addRules(COMPLEX_ARRAYS_RULES, COMPLEX_ARRAYS_MATCHES);
        bm.run(citylots2);
        System.out.println("COMBO events/sec: " + String.format("%.1f", bm.getEPS()));
    }

    // make sure we can handle nasty deep events
    @Test
    public void DeepEventBenchmark() throws Exception {

        // how many levels deep we want to go
        int maxLevel = 100;
        // we create a rule every time the number of events is a multiple of this number
        int ruleEveryNEvents = 10;

        ObjectMapper m = new ObjectMapper();

        Map<String, Object> root = new HashMap<>();

        Map<String, Object> ruleRoot = new HashMap<>();

        List<String> deepEvents = new ArrayList<>();
        List<String> deepRules = new ArrayList<>();
        List<Integer> deepExpected = new ArrayList<>();

        Map<String, Object> currentLevel = root;
        Map<String, Object> currentRule = ruleRoot;

        for (int i = 0; i < maxLevel; i++) {
            currentLevel.put("numeric" + i, i * i);
            currentLevel.put("string" + i, "value" + i);


            if (i % ruleEveryNEvents == 0) {
                currentRule.put("string" + i, Collections.singletonList("value" + i));
                deepRules.add(m.writeValueAsString(ruleRoot));
                currentRule.remove("string" + i);
                // all the events generated below this point will match this rule.
                deepExpected.add(maxLevel - i);
            }

            deepEvents.add(m.writeValueAsString(root));

            HashMap<String, Object> newLevel = new HashMap<>();
            currentLevel.put("level" + i, newLevel);
            currentLevel = newLevel;

            HashMap<String, Object> newRuleLevel = new HashMap<>();
            currentRule.put("level" + i, newRuleLevel);
            currentRule = newRuleLevel;
        }

        // warm up
        Benchmarker bm = new Benchmarker();
        bm.addRules(deepRules.toArray(new String[0]), deepExpected.stream().mapToInt(Integer::intValue).toArray());
        bm.run(deepEvents);

        // exercise
        bm = new Benchmarker();
        bm.addRules(deepRules.toArray(new String[0]), deepExpected.stream().mapToInt(Integer::intValue).toArray());
        bm.run(deepEvents);

        System.out.println("DEEP EXACT events/sec: " + String.format("%.1f", bm.getEPS()));

    }

    private final List<String> citylots2 = new ArrayList<>();

    private static class Benchmarker {
        int ruleCount = 0;
        Map<String, Integer> wanted = new HashMap<>();
        Machine machine = new Machine();
        double eps = 0.0;

        void addRules(String[] rules, int[] wanted) throws Exception {
            for (int i = 0; i < rules.length; i++) {
                String rname = String.format("r%d", ruleCount++);
                machine.addRule(rname, rules[i]);
                this.wanted.put(rname, wanted[i]);
            }
        }

        void run(List<String> events) throws Exception {
            final Map<String, Integer> gotMatches = new HashMap<>();
            long before = System.currentTimeMillis();
            for (String event : events) {
                List<String> matches = machine.rulesForJSONEvent(event);
                for (String match : matches) {
                    Integer got = gotMatches.get(match);
                    if (got == null) {
                        got = 1;
                    } else {
                        got = got + 1;
                    }
                    gotMatches.put(match, got);
                }
            }

            long after = System.currentTimeMillis();
            eps = (1000.0 / (1.0 * (after - before) / events.size()));

            for (String got : gotMatches.keySet()) {
                assertEquals(wanted.get(got), gotMatches.get(got));
            }
            for (String want : wanted.keySet()) {
                assertEquals(wanted.get(want), gotMatches.get(want) == null ? (Integer) 0 : gotMatches.get(want));
            }
        }

        double getEPS() {
            return eps;
        }
    }

    private final Random rand = new Random(234345);

    private String randomAscii(final int len) {
        final String abc = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_";
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append(abc.charAt(rand.nextInt(abc.length())));
        }
        return sb.toString();
    }

    private void readCityLots2() {
        try {
            System.out.println("Reading citylots2");
            final FileInputStream fileInputStream = new FileInputStream(CITYLOTS_2);
            final GZIPInputStream gzipInputStream = new GZIPInputStream(fileInputStream);
            BufferedReader cl2Reader = new BufferedReader(new InputStreamReader(gzipInputStream));
            String line = cl2Reader.readLine();
            while (line != null) {
                citylots2.add(line);
                line = cl2Reader.readLine();
            }
            cl2Reader.close();
            System.out.println("Read " + citylots2.size() + " events");
        } catch (Exception e) {
            System.out.println("Can't find, current directory " + System.getProperty("user.dir"));
            throw new RuntimeException(e);
        }
    }

    /////////////////////////// Old benchmarks start here ////////////////////////

    private BufferedReader cityLotsReader;
    private final Machine machine = new Machine();
    private final Map<String, String> ruleJSON = new HashMap<>();

    @Test
    public void RulebaseBenchmark() throws Exception {
        File dir = new File("data");
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                if (name.startsWith("rules")) {
                    BufferedReader reader = new BufferedReader(new FileReader(file));
                    List<String[]> lines = linesFrom(reader);
                    reader.close();
                    doRules(lines);
                }
            }
        }
    }

    private void doRules(List<String[]> lines) {
        Machine machine = new Machine();
        String[] rnames = new String[lines.size()];
        for (int i = 0; i < rnames.length; i++) {
            rnames[i] = "R" + String.format("%03d", i);
        }
        List<Map<String, List<String>>> rules = new ArrayList<>();
        int size = 0;
        for (String[] line : lines) {
            Map<String, List<String>> rule = new HashMap<>();
            for (int i = 0; i < line.length; i += 2) {
                List<String> l = new ArrayList<>();
                l.add(line[i + 1]);
                rule.put(line[i], l);
                size += l.size();
            }
            rules.add(rule);
        }
        long before = System.currentTimeMillis();
        for (int i = 0; i < rules.size(); i++) {
            machine.addRule(rnames[i], rules.get(i));
        }
        long after = System.currentTimeMillis();
        long delta = after - before;
        int avg = (int) ((1.0 * size / rules.size()) + 0.5);
        System.out.printf("%d rules, average size %d, %dms\n", rules.size(), avg, delta);
    }

    private List<String[]> linesFrom(BufferedReader reader) throws Exception {
        List<String[]> lines = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            String[] parts = line.split("\\*");
            lines.add(parts);
        }
        return lines;
    }

    @Test
    public void CitylotsBenchmark() throws Exception {
        openInput();
        System.out.println("Turning JSON into field-lists...");
        List<String[]> parsedLines = readAndParseLines();
        Map<String, Integer> expected = setRules(machine);
        List<String> allRules = new ArrayList<>();
        System.out.println("Finding Rules...");
        long before = System.currentTimeMillis();
        for (String[] line : parsedLines) {
            allRules.addAll(machine.rulesForEvent(line));
        }
        long after = System.currentTimeMillis();

        System.out.println("Lines: " + parsedLines.size() +
                ", Msec: " + (after - before));
        double eps = (1000.0 / (1.0 * (after - before) / parsedLines.size()));
        System.out.println("Events/sec: " + String.format("%.1f", eps));
        Map<String, Integer> rc = new HashMap<>();
        for (String rule : allRules) {
            incr(rule, rc);
        }
        for (String r : expected.keySet()) {
            assertEquals(expected.get(r), rc.get(r));
        }
    }

    @Test
    public void RealisticCityLots() throws Exception {
        openInput();
        System.out.println("Reading lines...");
        List<String> lines = readLines();
        Map<String, Integer> expected = setRules(machine);
        List<String> allRules = new ArrayList<>();
        System.out.println("Finding Rules...");
        long before = System.currentTimeMillis();
        int count = 0;
        for (String line : lines) {
            allRules.addAll(machine.rulesForJSONEvent(line));
            count++;
            if ((count % 10000) == 0) {
                System.out.println("Lots: " + count);
            }
        }
        long after = System.currentTimeMillis();
        System.out.println("Lines: " + lines.size() +
                ", Msec: " + (after - before));
        double eps = (1000.0 / (1.0 * (after - before) / lines.size()));
        System.out.println("Events/sec: " + String.format("%.1f", eps));
        System.out.println(" Rules/sec: " + String.format("%.1f", eps * allRules.size()));
        Map<String, Integer> rc = new HashMap<>();
        for (String rule : allRules) {
            incr(rule, rc);
        }
        for (String r : expected.keySet()) {
            assertEquals(expected.get(r), rc.get(r));
        }
    }

    @Test
    public void NoCompileBenchmark() throws Exception {
        openInput();
        System.out.println("Reading lines...");
        List<String> lines = readLines();
        int count = 0;
        final Machine staticMachine = new Machine();
        Map<String, Integer> expected = setRules(staticMachine);
        Map<String, AtomicInteger> received = new HashMap<>();
        Set<String> rules = expected.keySet();
        System.out.println("Finding Rules...");
        long before = System.currentTimeMillis();
        for (String lot : lines) {
            for (String rule : rules) {
                if (Ruler.matchesRule(lot, ruleJSON.get(rule))) {
                    incrRuleCount(rule, received);
                }
            }
            count++;
            if ((count % 10000) == 0) {
                System.out.println("Lots: " + count);
            }
        }
        long after = System.currentTimeMillis();
        System.out.println("Lines: " + lines.size() +
                ", Msec: " + (after - before));
        double eps = (1000.0 / (1.0 * (after - before) / lines.size()));
        System.out.println("Events/sec: " + String.format("%.1f", eps));
        System.out.println(" Rules/sec: " + String.format("%.1f", eps * rules.size()));
        for (String rule : rules) {
            assertEquals(rule, (long) expected.get(rule), received.get(rule).get());
        }
    }

    private void incrRuleCount(String rule, Map<String, AtomicInteger> counts) {
        AtomicInteger count = counts.get(rule);
        if (count == null) {
            count = new AtomicInteger(0);
            counts.put(rule, count);
        }
        count.incrementAndGet();
    }

    private void incr(String r, Map<String, Integer> rc) {
        rc.merge(r, 1, Integer::sum);
    }

    private void openInput() {
        try {
            final FileInputStream fileInputStream = new FileInputStream(CITYLOTS_JSON);
            final GZIPInputStream gzipInputStream = new GZIPInputStream(fileInputStream);
            cityLotsReader = new BufferedReader(new InputStreamReader(gzipInputStream));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<String> readLines() throws Exception {
        String line;
        List<String> lines = new ArrayList<>();
        while ((line = cityLotsReader.readLine()) != null) {
            lines.add(line);
        }
        return lines;
    }

    private List<String[]> readAndParseLines() throws Exception {
        String line;
        List<String[]> lines = new ArrayList<>();
        while ((line = cityLotsReader.readLine()) != null) {
            List<String> fieldList = Event.flatten(line);
            String[] fields = new String[fieldList.size()];
            fieldList.toArray(fields);
            lines.add(fields);
        }
        return lines;
    }

    private Map<String, Integer> setRules(Machine machine) {

        List<Rule> rules = new ArrayList<>();
        HashMap<String, Integer> expected = new HashMap<>();

        Rule rule;

        rule = new Rule("R1");
        rule.setKeys("properties.STREET", "properties.FROM_ST");
        rule.setJson("{ \"properties\": { \"STREET\": [ \"FOERSTER\" ], \"FROM_ST\": [ \"755\" ] } }");
        rule.setValues("\"FOERSTER\"", "\"755\"");
        rule.setShouldMatch(1);
        rules.add(rule);
        ruleJSON.put("R1", rule.json);

        rule = new Rule("R2");
        rule.setJson("{ \"properties\": { \"BLOCK_NUM\": [ \"3027A\" ] } }");
        rule.setKeys("properties.BLOCK_NUM");
        rule.setValues("\"3027A\"");
        rule.setShouldMatch(59);
        rules.add(rule);
        ruleJSON.put("R2", rule.json);

        rule = new Rule("R3");
        rule.setKeys("properties.BLOCK_NUM", "properties.ODD_EVEN");
        rule.setValues("\"2183\"", "\"E\"");
        rule.setJson("{ \"properties\": { \"BLOCK_NUM\": [ \"2183\" ], \"ODD_EVEN\": [ \"E\" ] } }");
        rule.setShouldMatch(27);
        rules.add(rule);
        ruleJSON.put("R3", rule.json);

        rule = new Rule("R4");
        rule.setKeys("properties.STREET");
        rule.setValues("\"17TH\"");
        rule.setJson("{ \"properties\": { \"STREET\": [ \"17TH\" ] } }");
        rule.setShouldMatch(1619);
        rules.add(rule);
        ruleJSON.put("R4", rule.json);

        rule = new Rule("R5");
        rule.setKeys("properties.FROM_ST", "geometry.type");
        rule.setValues("\"2521\"", "\"Polygon\"");
        rule.setJson("{ \"properties\": { \"FROM_ST\": [ \"2521\" ] }, \"geometry\": { \"type\": [ \"Polygon\" ] } }");
        rule.setShouldMatch(19);
        rules.add(rule);
        ruleJSON.put("R5", rule.json);

        rule = new Rule("R6");
        rule.setKeys("properties.STREET");
        rule.setValues("\"FOLSOM\"");
        rule.setJson("{ \"properties\": { \"STREET\": [ \"FOLSOM\" ] } }");
        rule.setShouldMatch(1390);
        rules.add(rule);
        ruleJSON.put("R6", rule.json);

        rule = new Rule("R7");
        rule.setKeys("properties.BLOCK_NUM", "properties.ST_TYPE");
        rule.setValues("\"3789\"", "\"ST\"");
        rule.setJson("{ \"properties\": { \"BLOCK_NUM\": [ \"3789\" ], \"ST_TYPE\": [ \"ST\" ] } }");
        rule.setShouldMatch(527);
        rules.add(rule);
        ruleJSON.put("R7", rule.json);

        for (Rule r : rules) {
            expected.put(r.name, r.shouldMatch);
            machine.addRule(r.name, r.fields);
        }
        return expected;
    }

    private static class Rule {

        String name;
        String json;
        int shouldMatch = 0;
        final Map<String, List<String>> fields = new HashMap<>();
        private String[] keys;

        Rule(String name) {
            this.name = name;
        }

        void setJson(String json) {
            assertNull(RuleCompiler.check(json));
            this.json = json;
        }
        void setKeys(String... keys) {
            this.keys = keys;
        }

        void setValues(String... values) {
            for (int i = 0; i < values.length; i++) {
                final List<String> valList = new ArrayList<>();
                valList.add(values[i]);
                fields.put(keys[i], valList);
            }
        }

        void setShouldMatch(int shouldMatch) {
            this.shouldMatch = shouldMatch;
        }
    }
}
