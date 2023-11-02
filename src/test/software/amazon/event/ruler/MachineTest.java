package software.amazon.event.ruler;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Unit testing a state machine is hard.  Tried hand-computing a few machines
 *  but kept getting them wrong, the software was right.  So this is really
 *  more of a smoke/integration test.  But the coverage is quite good.
 */
public class MachineTest {

    private String toIP(int ip) {
        StringBuilder sb = new StringBuilder();
        sb.append((ip >> 24) & 0xFF).append('.');
        sb.append((ip >> 16) & 0xFF).append('.');
        sb.append((ip >> 8) & 0xFF).append('.');
        sb.append(ip & 0xFF);
        return sb.toString();
    }

    @Test
    public void CIDRTest() throws Exception {

        String template = "{" +
                "  \"a\": \"IP\"" +
                "}";

        long base = 0x0A000000;

        // tested by hand with much smaller starts but the runtime gets looooooooong
        for (int i = 22; i < 32; i++) {

            String rule = "{ " +
                    "  \"a\": [ {\"cidr\": \"10.0.0.0/" + i + "\"} ]" +
                    "}";
            Machine m = new Machine();
            m.addRule("r", rule);
            long numberThatShouldMatch = 1L << (32 - i);

            // don't want to run through all of them for low values of maskbits
            long windowEnd = base + numberThatShouldMatch + 16;
            int matches = 0;
            for (long j = base - 16; j < windowEnd; j++) {

                String event = template.replace("IP", toIP((int) j));
                if (m.rulesForEvent(event).size() == 1) {
                    matches++;
                }
            }
            assertEquals(numberThatShouldMatch, matches);
        }
    }

    @Test
    public void testIPAddressOfCIDRIsEqualToMaximumOfRange() throws Exception {
        String rule1 = "{\"sourceIPAddress\": [{\"cidr\": \"220.160.153.171/31\"}]}";
        String rule2 = "{\"sourceIPAddress\": [{\"cidr\": \"220.160.154.255/24\"}]}";
        String rule3 = "{\"sourceIPAddress\": [{\"cidr\": \"220.160.59.225/31\"}]}";

        Machine machine = new Machine();
        machine.addRule("rule1", rule1);
        machine.addRule("rule2", rule2);
        machine.addRule("rule3", rule3);

        List<String> matches = machine.rulesForEvent("{\"sourceIPAddress\": \"220.160.153.170\"}");
        assertEquals(1, matches.size());
        assertTrue(matches.contains("rule1"));
        matches = machine.rulesForEvent("{\"sourceIPAddress\": \"220.160.153.171\"}");
        assertEquals(1, matches.size());
        assertTrue(matches.contains("rule1"));
        matches = machine.rulesForEvent("{\"sourceIPAddress\": \"220.160.153.169\"}");
        assertTrue(matches.isEmpty());
        matches = machine.rulesForEvent("{\"sourceIPAddress\": \"220.160.153.172\"}");
        assertTrue(matches.isEmpty());

        matches = machine.rulesForEvent("{\"sourceIPAddress\": \"220.160.154.0\"}");
        assertEquals(1, matches.size());
        assertTrue(matches.contains("rule2"));
        matches = machine.rulesForEvent("{\"sourceIPAddress\": \"220.160.154.255\"}");
        assertEquals(1, matches.size());
        assertTrue(matches.contains("rule2"));
        matches = machine.rulesForEvent("{\"sourceIPAddress\": \"220.160.153.255\"}");
        assertTrue(matches.isEmpty());
        matches = machine.rulesForEvent("{\"sourceIPAddress\": \"220.160.155.0\"}");
        assertTrue(matches.isEmpty());

        matches = machine.rulesForEvent("{\"sourceIPAddress\": \"220.160.59.224\"}");
        assertEquals(1, matches.size());
        assertTrue(matches.contains("rule3"));
        matches = machine.rulesForEvent("{\"sourceIPAddress\": \"220.160.59.225\"}");
        assertEquals(1, matches.size());
        assertTrue(matches.contains("rule3"));
        matches = machine.rulesForEvent("{\"sourceIPAddress\": \"220.160.59.223\"}");
        assertTrue(matches.isEmpty());
        matches = machine.rulesForEvent("{\"sourceIPAddress\": \"220.160.59.226\"}");
        assertTrue(matches.isEmpty());
    }

    @Test
    public void testSimplestPossibleMachine() throws Exception {
        String rule1 = "{ \"a\" : [ 1 ] }";
        String rule2 = "{ \"b\" : [ 2 ] }";
        Machine machine = new Machine();
        machine.addRule("r1", rule1);
        machine.addRule("r2", rule2);
        String[] event1 = { "a", "1" };
        String[] event2 = { "b", "2" };
        String[] event3 = { "x", "true" };
        List<String> val;
        val = machine.rulesForEvent(event1);
        assertEquals(1, val.size());
        assertEquals("r1", val.get(0));

        val = machine.rulesForEvent(event2);
        assertEquals(1, val.size());
        assertEquals("r2", val.get(0));

        val = machine.rulesForEvent(event3);
        assertEquals(0, val.size());
    }

    @Test
    public void testPrefixMatching() throws Exception {
        String rule1 = "{ \"a\" : [ { \"prefix\": \"zoo\" } ] }";
        String rule2 = "{ \"b\" : [ { \"prefix\": \"child\" } ] }";
        Machine machine = new Machine();
        machine.addRule("r1", rule1);
        machine.addRule("r2", rule2);
        String[][] events = {
                {"a", "\"zookeeper\""},
                {"a", "\"zoo\""},
                {"b", "\"childlike\""},
                {"b", "\"childish\""},
                {"b", "\"childhood\""}
        };
        for (String[] event : events) {
            List<String> rules = machine.rulesForEvent(event);
            assertEquals(1, rules.size());
            if ("a".equals(event[0])) {
                assertEquals("r1", rules.get(0));
            } else {
                assertEquals("r2", rules.get(0));
            }
        }

        machine = new Machine();
        String rule3 = "{ \"a\" : [ { \"prefix\": \"al\" } ] }";
        String rule4 = "{ \"a\" : [ \"albert\" ] }";
        machine.addRule("r3", rule3);
        machine.addRule("r4", rule4);
        String[] e2 = { "a", "\"albert\""};
        List<String> rules = machine.rulesForEvent(e2);
        assertEquals(2, rules.size());
    }

    @Test
    public void testSuffixMatching() throws Exception {
        String rule1 = "{ \"a\" : [ { \"suffix\": \"er\" } ] }";
        String rule2 = "{ \"b\" : [ { \"suffix\": \"txt\" } ] }";
        Machine machine = new Machine();
        machine.addRule("r1", rule1);
        machine.addRule("r2", rule2);
        String[] events = {
                "{\"a\" : \"zookeeper\"}",
                "{\"b\" : \"amazon.txt\"}",
                "{\"a\" : \"checker\"}",
                "{\"b\" : \"Texttxt\"}",
                "{\"a\" : \"er\"}",
                "{\"b\" : \"txt\"}"
        };
        int i = 0;
        for (String event : events) {
            List<String> rules = machine.rulesForJSONEvent(event);
            assertEquals(1, rules.size());
            if (i++ % 2 == 0) {
                assertEquals("r1", rules.get(0));
            } else {
                assertEquals("r2", rules.get(0));
            }
        }

        machine.deleteRule("r2", rule2);
        i = 0;
        for (String event : events) {
            List<String> rules = machine.rulesForJSONEvent(event);
            if (i++ % 2 == 0) {
                assertEquals(1, rules.size());
            } else {
                assertEquals(0, rules.size());
            }
        }

        machine.deleteRule("r1", rule1);
        assertTrue(machine.isEmpty());
    }

    @Test
    public void testEqualsIgnoreCaseMatching() throws Exception {
        Machine machine = new Machine();
        String rule1 = "{ \"a\" : [ { \"equals-ignore-case\": \"aBc\" } ] }";
        String rule2 = "{ \"b\" : [ { \"equals-ignore-case\": \"XyZ\" } ] }";
        String rule3 = "{ \"b\" : [ { \"equals-ignore-case\": \"xyZ\" } ] }";

        machine.addRule("r1", rule1);
        machine.addRule("r2", rule2);
        machine.addRule("r3", rule3);
        assertEquals(Arrays.asList("r1"), machine.rulesForJSONEvent("{\"a\" : \"abc\"}"));
        assertEquals(new HashSet<>(Arrays.asList("r2", "r3")),
                new HashSet<>(machine.rulesForJSONEvent("{\"b\" : \"XYZ\"}")));
        assertEquals(Arrays.asList("r1"), machine.rulesForJSONEvent("{\"a\" : \"AbC\"}"));
        assertTrue(machine.rulesForJSONEvent("{\"b\" : \"xyzz\"}").isEmpty());
        assertTrue(machine.rulesForJSONEvent("{\"a\" : \"aabc\"}").isEmpty());
        assertTrue(machine.rulesForJSONEvent("{\"b\" : \"ABCXYZ\"}").isEmpty());

        machine.deleteRule("r3", rule3);
        assertEquals(Arrays.asList("r2"), machine.rulesForJSONEvent("{\"b\" : \"XYZ\"}"));

        machine.deleteRule("r1", rule1);
        machine.deleteRule("r2", rule2);
        assertTrue(machine.isEmpty());
    }

    @Test
    public void testWildcardMatching() throws Exception {
        Machine machine = new Machine();
        String rule1 = "{ \"a\" : [ { \"wildcard\": \"*bc\" } ] }";
        String rule2 = "{ \"b\" : [ { \"wildcard\": \"d*f\" } ] }";
        String rule3 = "{ \"b\" : [ { \"wildcard\": \"d*ff\" } ] }";
        String rule4 = "{ \"c\" : [ { \"wildcard\": \"xy*\" } ] }";
        String rule5 = "{ \"c\" : [ { \"wildcard\": \"xy*\" } ] }";
        String rule6 = "{ \"d\" : [ { \"wildcard\": \"12*4*\" } ] }";

        machine.addRule("r1", rule1);
        machine.addRule("r2", rule2);
        machine.addRule("r3", rule3);
        machine.addRule("r4", rule4);
        machine.addRule("r5", rule5);
        machine.addRule("r6", rule6);
        assertEquals(Arrays.asList("r1"), machine.rulesForJSONEvent("{\"a\" : \"bc\"}"));
        assertEquals(Arrays.asList("r1"), machine.rulesForJSONEvent("{\"a\" : \"abc\"}"));
        assertEquals(Arrays.asList("r2"), machine.rulesForJSONEvent("{\"b\" : \"dexef\"}"));
        assertEquals(new HashSet<>(Arrays.asList("r2", "r3")),
                new HashSet<>(machine.rulesForJSONEvent("{\"b\" : \"dexeff\"}")));
        assertEquals(new HashSet<>(Arrays.asList("r4", "r5")),
                new HashSet<>(machine.rulesForJSONEvent("{\"c\" : \"xyzzz\"}")));
        assertEquals(Arrays.asList("r6"), machine.rulesForJSONEvent("{\"d\" : \"12345\"}"));
        assertTrue(machine.rulesForJSONEvent("{\"c\" : \"abc\"}").isEmpty());
        assertTrue(machine.rulesForJSONEvent("{\"a\" : \"xyz\"}").isEmpty());
        assertTrue(machine.rulesForJSONEvent("{\"c\" : \"abcxyz\"}").isEmpty());
        assertTrue(machine.rulesForJSONEvent("{\"b\" : \"ef\"}").isEmpty());
        assertTrue(machine.rulesForJSONEvent("{\"b\" : \"de\"}").isEmpty());
        assertTrue(machine.rulesForJSONEvent("{\"d\" : \"1235\"}").isEmpty());

        machine.deleteRule("r5", rule5);
        assertEquals(Arrays.asList("r4"), machine.rulesForJSONEvent("{\"c\" : \"xy\"}"));

        machine.deleteRule("r1", rule1);
        machine.deleteRule("r2", rule2);
        machine.deleteRule("r3", rule3);
        machine.deleteRule("r4", rule4);
        machine.deleteRule("r6", rule6);
        assertTrue(machine.isEmpty());
    }

    @Test
    public void testCityLotsProblemLines() throws Exception {

        String[] e = {
                "geometry.coordinates",  "-122.42860896096424",
                "geometry.coordinates", "37.795818585593523",
                "geometry.cordinates", "0.0",
                "geometry.type", "\"Polygon\"",
                "properties.BLKLOT", "\"0567002\"",
                "properties.BLOCK_NUM", "\"0567\"",
                "properties.FROM_ST", "\"2521\"",
                "properties.LOT_NUM", "\"002\"",
                "properties.MAPBLKLOT", "\"0567002\"",
                "properties.ODD_EVEN", "\"O\"",
                "properties.STREET", "\"OCTAVIA\"",
                "properties.ST_TYPE", "\"ST\"",
                "properties.TO_ST", "\"2521\"",
                "type", ""
        };
        String eJSON = "{" +
                "  \"geometry\": {\n" +
                "    \"coordinates\": [ -122.4286089609642, 37.795818585593523 ],\n" +
                "    \"type\": \"Polygon\"\n" +
                "  },\n" +
                "  \"properties\": {\n" +
                "    \"BLKLOT\": \"0567002\",\n" +
                "    \"BLOCK_NUM\": \"0567\",\n" +
                "    \"FROM_ST\": \"2521\",\n" +
                "    \"LOT_NUM\": \"002\",\n" +
                "    \"MAPBLKLOT\": \"0567002\",\n" +
                "    \"ODD_EVEN\": \"O\",\n" +
                "    \"STREET\": \"OCTAVIA\",\n" +
                "    \"ST_TYPE\": \"ST\",\n" +
                "    \"TO_ST\": \"2521\"\n" +
                "  }\n" +
                "}\n";
        String rule = "{" +
                "  \"properties\": {" +
                "    \"FROM_ST\": [ \"2521\" ]" +
                "  },\n" +
                "  \"geometry\": {\n" +
                "    \"type\": [ \"Polygon\" ]" +
                "  }" +
                "}";
        Machine machine = new Machine();
        machine.addRule("R1", rule);
        List<String> r = machine.rulesForEvent(e);
        assertEquals(1, r.size());
        assertEquals("R1", r.get(0));
        List<String> eFromJ = Event.flatten(eJSON);
        r = machine.rulesForEvent(eFromJ);
        assertEquals(1, r.size());
        assertEquals("R1", r.get(0));
    }

    @Test
    public void testExistencePatternsLifecycle() throws Exception {
        String rule1 = "rule1";
        Map<String, List<Patterns>> r1 = new HashMap<>();
        List<Patterns> existsPattern = new ArrayList<>();
        existsPattern.add(Patterns.existencePatterns());
        r1.put("a", existsPattern);
        List<Patterns> exactPattern = new ArrayList<>();
        exactPattern.add(Patterns.exactMatch("\"b_val\""));
        r1.put("b", exactPattern);

        String rule2 = "rule2";
        Map<String, List<Patterns>> r2 = new HashMap<>();
        List<Patterns> absencePatterns = new ArrayList<>();
        absencePatterns.add(Patterns.absencePatterns());
        r2.put("c", absencePatterns);
        List<Patterns> numericalPattern = new ArrayList<>();
        numericalPattern.add(Patterns.numericEquals(3));
        r2.put("d", numericalPattern);

        String rule3 = "rule3";
        Map<String, List<Patterns>> r3 = new HashMap<>();
        List<Patterns> exactPattern_r3 = new ArrayList<>();
        exactPattern_r3.add(Patterns.exactMatch("\"1\""));
        r3.put("a", exactPattern_r3);

        TestEvent event1 = new TestEvent("a", "\"1\"", "b", "\"b_val\"");
        TestEvent event2 = new TestEvent("a", "\"1\"", "b", "\"b_val\"", "d", "3");
        TestEvent event3 = new TestEvent("a", "\"1\"", "b", "\"b_val\"", "c", "\"c_val\"", "d", "3");

        String jsonEvent1 = "{ \"a\": \"1\", \"b\": \"b_val\" }";
        String jsonEvent2 = "{ \"a\": \"1\", \"b\": \"b_val\", \"d\" : 3 }";
        String jsonEvent3 = "{ \"a\": \"1\", \"b\": \"b_val\",  \"c\" : \"c_val\", \"d\" : 3}";

        Machine machine = new Machine();
        machine.addPatternRule(rule1, r1);

        event1.setExpected(rule1);
        event2.setExpected(rule1);
        event3.setExpected(rule1);

        singleEventTest(machine, event1);
        singleEventTestForRulesForJSONEvent(machine, jsonEvent1, event1);
        singleEventTest(machine, event2);
        singleEventTestForRulesForJSONEvent(machine, jsonEvent2, event2);
        singleEventTest(machine, event3);
        singleEventTestForRulesForJSONEvent(machine, jsonEvent3, event3);

        machine.addPatternRule(rule2, r2);

        event2.setExpected(rule2);

        singleEventTest(machine, event1);
        singleEventTestForRulesForJSONEvent(machine, jsonEvent1, event1);
        singleEventTest(machine, event2);
        singleEventTestForRulesForJSONEvent(machine, jsonEvent2, event2);
        singleEventTest(machine, event3);
        singleEventTestForRulesForJSONEvent(machine, jsonEvent3, event3);

        machine.addPatternRule(rule3, r3);

        event1.setExpected(rule3);
        event2.setExpected(rule3);
        event3.setExpected(rule3);

        singleEventTest(machine, event1);
        singleEventTestForRulesForJSONEvent(machine, jsonEvent1, event1);
        singleEventTest(machine, event2);
        singleEventTestForRulesForJSONEvent(machine, jsonEvent2, event2);
        singleEventTest(machine, event3);
        singleEventTestForRulesForJSONEvent(machine, jsonEvent3, event3);

        machine.deletePatternRule(rule1, r1);

        event1.clearExpected(rule1);
        event2.clearExpected(rule1);
        event3.clearExpected(rule1);

        singleEventTest(machine, event1);
        singleEventTestForRulesForJSONEvent(machine, jsonEvent1, event1);
        singleEventTest(machine, event2);
        singleEventTestForRulesForJSONEvent(machine, jsonEvent2, event2);
        singleEventTest(machine, event3);
        singleEventTestForRulesForJSONEvent(machine, jsonEvent3, event3);

        machine.deletePatternRule(rule2, r2);

        event1.clearExpected(rule2);
        event2.clearExpected(rule2);
        event3.clearExpected(rule2);

        singleEventTest(machine, event1);
        singleEventTestForRulesForJSONEvent(machine, jsonEvent1, event1);
        singleEventTest(machine, event2);
        singleEventTestForRulesForJSONEvent(machine, jsonEvent2, event2);
        singleEventTest(machine, event3);
        singleEventTestForRulesForJSONEvent(machine, jsonEvent3, event3);

        machine.deletePatternRule(rule3, r3);

        event1.clearExpected(rule3);
        event2.clearExpected(rule3);
        event3.clearExpected(rule3);

        singleEventTest(machine, event1);
        singleEventTestForRulesForJSONEvent(machine, jsonEvent1, event1);
        singleEventTest(machine, event2);
        singleEventTestForRulesForJSONEvent(machine, jsonEvent2, event2);
        singleEventTest(machine, event3);
        singleEventTestForRulesForJSONEvent(machine, jsonEvent3, event3);

        assertTrue(machine.isEmpty());
    }

    @Test
    public void matchRulesWithExistencePatternAndMatchOnExistenceByte() throws Exception {
        String rule1 = "rule1";
        Map<String, List<Patterns>> r1 = new HashMap<>();
        List<Patterns> existsPattern = new ArrayList<>();
        existsPattern.add(Patterns.existencePatterns());
        r1.put("a", existsPattern);

        String rule2 = "rule2";
        Map<String, List<Patterns>> r2 = new HashMap<>();
        List<Patterns> exactPattern = new ArrayList<>();
        exactPattern.add(Patterns.exactMatch("\"Y\""));
        r2.put("b", exactPattern);

        String rule3 = "rule3";
        Map<String, List<Patterns>> r3 = new HashMap<>();
        List<Patterns> exactPattern_r3 = new ArrayList<>();
        exactPattern_r3.add(Patterns.exactMatch("\"YES\""));
        r3.put("c", exactPattern_r3);

        String jsonEvent = "{\"a\": \"1\", \"b\": \"Y\", \"c\": \"YES\"}";
        TestEvent event = new TestEvent("a", "\"1\"", "b", "\"Y\"", "c", "\"YES\"");

        Machine machine = new Machine();
        machine.addPatternRule(rule1, r1);

        event.setExpected(rule1);
        singleEventTest(machine, event);
        singleEventTestForRulesForJSONEvent(machine, jsonEvent, event);

        machine.addPatternRule(rule2, r2);

        event.setExpected(rule2);
        singleEventTest(machine, event);
        singleEventTestForRulesForJSONEvent(machine, jsonEvent, event);

        machine.addPatternRule(rule3, r3);

        event.setExpected(rule3);
        singleEventTest(machine, event);
        singleEventTestForRulesForJSONEvent(machine, jsonEvent, event);

        machine.deletePatternRule(rule3, r3);
        event.clearExpected(rule3);
        singleEventTest(machine, event);
        singleEventTestForRulesForJSONEvent(machine, jsonEvent, event);

        machine.deletePatternRule(rule2, r2);
        event.clearExpected(rule2);
        singleEventTest(machine, event);
        singleEventTestForRulesForJSONEvent(machine, jsonEvent, event);

        machine.deletePatternRule(rule1, r1);
        event.clearExpected(rule1);
        singleEventTest(machine, event);
        singleEventTestForRulesForJSONEvent(machine, jsonEvent, event);

        assertTrue(machine.isEmpty());
    }

    @Test
    public void matchRuleWithExistencePatternAtEnd_andMatchesAtEventAfterAllFieldsHaveExhausted() throws Exception {
        String rule1 = "rule1";
        Map<String, List<Patterns>> r1 = new HashMap<>();

        List<Patterns> exactPattern = new ArrayList<>();
        exactPattern.add(Patterns.exactMatch("\"Y\""));
        r1.put("a", exactPattern);

        List<Patterns> greaterPattern = new ArrayList<>();
        greaterPattern.add(Range.greaterThan(10));
        r1.put("b", greaterPattern);

        List<Patterns> existsPattern = new ArrayList<>();
        existsPattern.add(Patterns.existencePatterns());
        r1.put("c", existsPattern);

        List<Patterns> absencePattern = new ArrayList<>();
        absencePattern.add(Patterns.absencePatterns());
        r1.put("d", absencePattern);

        String jsonEvent = "{\"a\": \"Y\", \"b\": 20, \"c\": \"YES\"}";
        TestEvent event = new TestEvent("a", "\"Y\"", "b", "20", "c", "\"YES\"");

        Machine machine = new Machine();
        machine.addPatternRule(rule1, r1);

        event.setExpected(rule1);
        singleEventTest(machine, event);
        singleEventTestForRulesForJSONEvent(machine, jsonEvent, event);
    }

    private void setRules(Machine machine) {

        List<Rule> rules = new ArrayList<>();

        Rule rule;

        rule = new Rule("R1");
        rule.setKeys("beta", "alpha", "gamma");
        rule.setExactMatchValues("2", "1", "3");
        rules.add(rule);

        rule = new Rule("R2");
        rule.setKeys("alpha", "foo", "bar");
        rule.setExactMatchValues("1", "\"v23\"", "\"v22\"");
        rules.add(rule);

        rule = new Rule("R3");
        rule.setKeys("inner", "outer", "upper");
        rule.setExactMatchValues("\"i0\"", "\"i1\"", "\"i2\"");
        rules.add(rule);

        rule = new Rule("R4");
        rule.setKeys("d1", "d2");
        rule.setExactMatchValues("\"v1\"", "\"v2\"");
        rules.add(rule);

        rule = new Rule("R5-super");
        rule.setKeys("r5-k1", "r5-k2", "r5-k3");
        rule.setExactMatchValues("\"r5-v1\"", "\"r5-v2\"", "\"r5-v3\"");
        rules.add(rule);

        rule = new Rule("R5-prefix");
        rule.setKeys("r5-k1", "r5-k2");
        rule.setExactMatchValues("\"r5-v1\"", "\"r5-v2\"");
        rules.add(rule);

        rule = new Rule("R5-suffix");
        rule.setKeys("r5-k2", "r5-k3");
        rule.setExactMatchValues("\"r5-v2\"", "\"r5-v3\"");
        rules.add(rule);

        rule = new Rule("R6");
        List<Patterns> x15_25 = new ArrayList<>();
        x15_25.add(Patterns.exactMatch("15"));
        x15_25.add(Patterns.exactMatch("25"));
        rule.addMulti(x15_25);
        rule.setKeys("x1", "x7");
        rule.setExactMatchValues("11", "50");
        rules.add(rule);

        rule = new Rule("R7");
        rule.setKeys("r5-k1");
        rule.setPatterns(Patterns.prefixMatch("\"r5"));
        rules.add(rule);

        for (Rule r : rules) {
            machine.addPatternRule(r.name, r.fields);
        }
    }

    private List<TestEvent> createEvents() {
        List<TestEvent> events = new ArrayList<>();

        TestEvent e;

        // match an event exactly
        e = new TestEvent("alpha", "1", "beta", "2", "gamma", "3");
        e.setExpected("R1");
        events.add(e);

        // extras between all the fields, should still match
        e = new TestEvent("0", "", "alpha", "1", "arc", "xx", "beta", "2", "gamma", "3", "zoo", "keeper");
        e.setExpected("R1");
        events.add(e);

        // fail on value
        e = new TestEvent("alpha", "1", "beta", "3", "gamma", "3");
        events.add(e);

        // fire two rules
        e = new TestEvent("alpha", "1", "beta", "2", "gamma", "3", "inner", "\"i0\"", "outer", "\"i1\"", "upper", "\"i2\"");
        e.setExpected("R1", "R3");
        events.add(e);

        // one rule inside another
        e = new TestEvent("alpha", "1", "beta", "2", "d1", "\"v1\"", "d2", "\"v2\"", "gamma", "3");
        e.setExpected("R1", "R4");
        events.add(e);

        // two rules in a funny way
        e = new TestEvent("0", "", "alpha", "1", "arc", "xx", "bar", "\"v22\"", "beta", "2", "foo", "\"v23\"", "gamma", "3",
                "zoo", "keeper");
        e.setExpected("R1", "R2");
        events.add(e);

        // match prefix rule
        e = new TestEvent("r5-k1", "\"r5-v1\"", "r5-k2", "\"r5-v2\"");
        e.setExpected("R5-prefix", "R7");
        events.add(e);

        // match 3 rules
        e = new TestEvent("r5-k1", "\"r5-v1\"", "r5-k2", "\"r5-v2\"", "r5-k3", "\"r5-v3\"");
        e.setExpected("R5-prefix", "R5-super", "R5-suffix", "R7");
        events.add(e);

        // single state with two branches
        e = new TestEvent("zork", "max", "x1", "11", "x6", "15", "x7", "50");
        e.setExpected("R6");
        events.add(e);
        e = new TestEvent("x1", "11", "x6", "25", "foo", "bar", "x7", "50");
        e.setExpected("R6");
        events.add(e);

        // extras between all the fields, should still match
        e = new TestEvent("0", "", "alpha", "1", "arc", "xx", "beta", "2", "gamma", "3", "zoo", "keeper");
        e.setExpected("R1");
        events.add(e);

        // fail on value
        e = new TestEvent("alpha", "1", "beta", "3", "gamma", "3");
        events.add(e);

        // fire two rules
        e = new TestEvent("alpha", "1", "beta", "2", "gamma", "3", "inner", "\"i0\"", "outer", "\"i1\"", "upper", "\"i2\"");
        e.setExpected("R1", "R3");
        events.add(e);

        // one rule inside another
        e = new TestEvent("alpha", "1", "beta", "2", "d1", "\"v1\"", "d2", "\"v2\"", "gamma", "3");
        e.setExpected("R1", "R4");
        events.add(e);

        // two rules in a funny way
        e = new TestEvent("0", "", "alpha", "1", "arc", "xx", "bar", "\"v22\"", "beta", "2", "foo", "\"v23\"", "gamma", "3",
                "zoo", "keeper");
        e.setExpected("R1", "R2");
        events.add(e);

        // match prefix rule
        e = new TestEvent("r5-k1", "\"r5-v1\"", "r5-k2", "\"r5-v2\"");
        e.setExpected("R5-prefix", "R7");
        events.add(e);

        // match 3 rules
        e = new TestEvent("r5-k1", "\"r5-v1\"", "r5-k2", "\"r5-v2\"", "r5-k3", "\"r5-v3\"");
        e.setExpected("R5-prefix", "R5-super", "R5-suffix", "R7");
        events.add(e);

        // single state with two branches
        e = new TestEvent("zork", "max", "x1", "11", "x6", "15", "x7", "50");
        e.setExpected("R6");
        events.add(e);
        e = new TestEvent("x1", "11", "x6", "25", "foo", "bar", "x7", "50");
        e.setExpected("R6");
        events.add(e);

        return events;
    }


    @Test
    public void testBuild() {
        Machine machine = new Machine();

        setRules(machine);
        assertNotNull(machine);

        List<TestEvent> events = createEvents();

        for (TestEvent event : events) {
            singleEventTest(machine, event);
        }
    }

    private void singleEventTest(Machine machine, TestEvent event) {
        List<String> actual = machine.rulesForEvent(event.mTokens);
        if (event.mExpectedRules.isEmpty()) {
            assertTrue(actual.isEmpty());
        } else {
            for (String expected : event.mExpectedRules) {
                assertTrue("Event " + event + "\n did not match rule '" + expected + "'", actual.contains(expected));
                actual.remove(expected);
            }
            if (!actual.isEmpty()) {
                fail("Event " + event + "\n rule " + actual.size() + " extra rules: " + actual.get(0));
            }
        }
    }

    private void singleEventTestForRulesForJSONEvent(Machine machine, String eventJson, TestEvent event) throws Exception {
        List<String> actual = machine.rulesForJSONEvent(eventJson);
        if (event.mExpectedRules.isEmpty()) {
            assertTrue(actual.isEmpty());
        } else {
            for (String expected : event.mExpectedRules) {
                assertTrue("Event " + event + "\n did not match rule '" + expected + "'", actual.contains(expected));
                actual.remove(expected);
            }
            if (!actual.isEmpty()) {
                fail("Event " + event + "\n rule " + actual.size() + " extra rules: " + actual.get(0));
            }
        }
    }


    private static class TestEvent {

        private String[] mTokens;
        private final List<String> mExpectedRules = new ArrayList<>();

        TestEvent(String... tokens) {
            mTokens = tokens;
        }

        void setExpected(String... rules) {
            Collections.addAll(mExpectedRules, rules);
        }

        void clearExpected(String... rules) {
            Arrays.stream(rules).forEach(mExpectedRules::remove);
        }

        @Override
        public String toString() {

            StringBuilder sb = new StringBuilder();
            sb.append("Tokens: ");
            for (String token : mTokens) {
                sb.append(token).append(" / ");
            }
            sb.append(" Expected: ");
            for (String expected : mExpectedRules) {
                sb.append(expected).append(" / ");
            }
            sb.append("\n");
            return sb.toString();
        }
    }

    private static class Rule {
        String name;
        final Map<String, List<Patterns>> fields = new HashMap<>();
        private String[] keys;

        Rule(String name) {
            this.name = name;
        }

        void setKeys(String... keys) {
            this.keys = keys;
        }

        void setExactMatchValues(String... values) {
            for (int i = 0; i < values.length; i++) {
                final List<Patterns> valList = new ArrayList<>();
                valList.add(Patterns.exactMatch(values[i]));
                fields.put(keys[i], valList);
            }
        }

        void setPatterns(Patterns... values) {
            for (int i = 0; i < values.length; i++) {
                final List<Patterns> valList = new ArrayList<>();
                valList.add((values[i]));
                fields.put(keys[i], valList);
            }
        }

        void addMulti(List<Patterns> vals) {
            fields.put("x6", vals);
        }
    }

    @Test
    public void addRuleOriginalAPI() {
        Machine machine = new Machine();
        Map<String, List<String>> map1 = new HashMap<>();
        Map<String, List<String>> map2 = new HashMap<>();
        List<String> s1 = asList("x", "y");
        List<String> s2 = asList("1", "2");
        List<String> s3 = asList("foo", "bar");
        map1.put("f1", s1);
        map1.put("f2", s2);
        map2.put("f1", s3);
        machine.addRule("r1", map1);
        machine.addRule("r2", map2);
        String[] event1 = { "f1", "x", "f2", "1" };
        String[] event2 = { "f1", "foo" };
        List<String> l = machine.rulesForEvent(event1);
        assertEquals(1, l.size());
        assertEquals("r1", l.get(0));
        l = machine.rulesForEvent(event2);
        assertEquals(1, l.size());
        assertEquals("r2", l.get(0));
    }

    @Test
    public void twoRulesSamePattern() throws IOException {
        Machine machine = new Machine();
        String json = "{\"detail\":{\"testId\":[\"foo\"]}}";
        machine.addRule("rule1", json);
        machine.addRule("rule2", new StringReader(json));
        machine.addRule("rule3", json.getBytes(StandardCharsets.UTF_8));
        machine.addRule("rule4", new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        List<String> event = new ArrayList<>();
        event.add("detail.testId");
        event.add("\"foo\"");
        List<String> strings = machine.rulesForEvent(event);

        assertEquals(4, strings.size());
    }

    @Test
    public void twoRulesSamePattern2() {
        Machine machine = new Machine();
        List<Rule> rules = new ArrayList<>();
        Rule rule;
        rule = new Rule("R1");
        rule.setKeys("a", "c", "b");
        rule.setExactMatchValues("1", "3", "2");
        rules.add(rule);

        rule = new Rule("R2");
        rule.setKeys("a", "b", "c");
        rule.setExactMatchValues("1", "2", "3");
        rules.add(rule);

        for (Rule r : rules) {
            machine.addPatternRule(r.name, r.fields);
        }

        TestEvent e = new TestEvent("0", "", "a", "1", "b", "2", "c", "3", "gamma", "3", "zoo", "keeper");
        e.setExpected("R1", "R2");

        List<String> actual = machine.rulesForEvent(e.mTokens);
        assertEquals(2, actual.size());
    }

    @Test
    public void dynamicAddRules() {
        Machine machine = new Machine();

        TestEvent e = new TestEvent("0", "", "a", "11", "b", "21", "c", "31", "gamma", "41", "zoo", "keeper");

        Rule rule;
        Rule rule1;
        Rule rule2;
        Rule rule3;

        rule = new Rule("R1");
        rule.setKeys("a", "b", "c");
        rule.setExactMatchValues("11", "21", "31");
        //test whether duplicated rule could be added
        machine.addPatternRule(rule.name, rule.fields);
        machine.addPatternRule(rule.name, rule.fields);
        machine.addPatternRule(rule.name, rule.fields);

        rule1 = new Rule("R1");
        rule1.setKeys("a", "b", "zoo");
        rule1.setExactMatchValues("11", "21", "keeper");
        machine.addPatternRule(rule1.name, rule1.fields);

        List<String> actual1 = machine.rulesForEvent(e.mTokens);
        assertEquals(1, actual1.size());

        rule2 = new Rule("R2-1");
        rule2.setKeys("a", "c", "b");
        rule2.setExactMatchValues("11", "31", "21");
        machine.addPatternRule(rule2.name, rule2.fields);

        List<String> actual2 = machine.rulesForEvent(e.mTokens);
        assertEquals(2, actual2.size());

        rule3 = new Rule("R2-2");
        rule3.setKeys("gamma", "zoo");
        rule3.setExactMatchValues("41", "keeper");
        machine.addPatternRule(rule3.name, rule3.fields);

        List<String> actual3 = machine.rulesForEvent(e.mTokens);
        assertEquals(3, actual3.size());
    }


    /**
     *  Incrementally build Rule R1 by different namevalues, observing new state and rules created
     *  Decrementally delete rule R1 by pointed namevalues, observing state and rules
     *  which is not used have been removed.
     */
    @Test
    public void dynamicDeleteRules() {
        Machine machine = new Machine();

        TestEvent e = new TestEvent("0", "", "a", "11", "b", "21", "c", "31", "gamma", "41", "zoo", "keeper");

        Rule rule;
        Rule rule1;
        rule = new Rule("R1");
        rule.setKeys("a", "b", "c");
        rule.setExactMatchValues("11", "21", "31");
        machine.addPatternRule(rule.name, rule.fields);
        List<String> actual = machine.rulesForEvent(e.mTokens);
        assertEquals(1, actual.size());

        rule1 = new Rule("R1");
        rule1.setKeys("a", "b", "gamma");
        rule1.setExactMatchValues("11", "21", "41");
        machine.addPatternRule(rule1.name, rule1.fields);
        List<String> actual1 = machine.rulesForEvent(e.mTokens);
        assertEquals(1, actual1.size());


        // delete R1 subset with rule.fields
        machine.deletePatternRule(rule.name, rule.fields);

        List<String> actual2 = machine.rulesForEvent(e.mTokens);
        assertEquals(1, actual2.size());

        // delete R1 subset with rule1 fields, after this step,
        // the machine will become empty as if no rule was added before.
        machine.deletePatternRule(rule1.name, rule1.fields);

        List<String> actual3 = machine.rulesForEvent(e.mTokens);
        assertEquals(0, actual3.size());
    }

    /**
     * Setup thread pools with 310 threads inside, among them, 300 threads are calling rulesForEvent(),
     * 10 threads are adding rule. the test is designed to add rules and match rules operation handled in parallel,
     * observe rulesForEvent whether could work well while there is new rule keeping added dynamically in parallel.
     * Keep same event call rulesForEvent() in parallel, expect to see more and more rules will be matched
     * aligned with more and more new rules added.
     * In this test:
     * We created 100 rules with 100 key/val pair (each rule use one key/val), we created one "global" event by using
     * those 100 key/val pairs. this event should match out all those 100 rules since they are added.
     * So if we keep using this event query machine while adding 100 rules in parallel, we should see the output of
     * number of matched rules by rulesForEvent keep increasing from 0 to 100, then stabilize returning 100 for
     * all of following rulesForEvent().
     */
    @Test
    public void testMultipleThreadReadAddRule() {
        Machine machine = new Machine();
        List<String> event = new ArrayList<>();
        List<Rule> rules = new ArrayList<>();

        for (int i = 0; i< 100; i++) {
            event.add(String.format("key-%03d",i));
            event.add(String.format("val-%03d",i));
        }

        for (int j = 0, i = 0; j<200 && i<100; j+=2,i++) {
            Rule rule = new Rule("R-" + i);
            rule.setKeys(event.get(j));
            rule.setExactMatchValues(event.get(j+1));
            rules.add(rule);
        }

        CountDownLatch latch = new CountDownLatch(1);

        class AddRuleRunnable implements Runnable {
            private final int i;

            private AddRuleRunnable(int i) {
                this.i = i;
            }

            @Override
            public void run() {
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Rule rule = rules.get(i);
                machine.addPatternRule(rule.name,rule.fields);
            }
        }

        class MatchRuleRunnable implements Runnable {
            @Override
            public void run() {
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                int i = 0;
                int n;
                // this thread only return when match out 100 rules 100 times.
                while (i < 100) {
                    List<String> actual = machine.rulesForEvent(event);
                    // the number of matched rules will keep growing from 0 till to 100
                    n = actual.size();
                    if (n == 100) {
                        i++;
                    }
                }
            }
        }

        ExecutorService execW = Executors.newFixedThreadPool(10);
        ExecutorService execR = Executors.newFixedThreadPool(300);
        for(int i = 0; i < 100; i++) {
            execW.execute(new AddRuleRunnable(i));
        }
        for(int i = 0; i < 300; i++) {
            execR.execute(new MatchRuleRunnable());
        }

        // release threads to let them work
        latch.countDown();

        execW.shutdown();
        execR.shutdown();
        try {
            execW.awaitTermination(5, TimeUnit.MINUTES);
            execR.awaitTermination(5, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        List<String> actual = machine.rulesForEvent(event);
        assertEquals(100, actual.size());
    }

    @Test
    public void testMultipleThreadReadDeleteRule() {
        Machine machine = new Machine();
        List<String> event = new ArrayList<>();
        List <Rule> rules = new ArrayList<>();

        for (int i = 0; i< 100; i++) {
            event.add(String.format("key-%03d",i));
            event.add(String.format("val-%03d",i));
        }

        // first add 100 rules into machine.
        for (int j = 0, i = 0; j<200 && i<100; j+=2,i++) {
            Rule rule = new Rule("R-" + i);
            rule.setKeys(event.get(j));
            rule.setExactMatchValues(event.get(j+1));
            rules.add(rule);
            machine.addPatternRule(rule.name,rule.fields);
        }

        CountDownLatch latch = new CountDownLatch(1);

        class DeleteRuleRunnable implements Runnable {
            private final int i;

            private DeleteRuleRunnable(int i) {
                this.i = i;
            }

            @Override
            public void run() {
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Rule rule = rules.get(i);
                machine.deletePatternRule(rule.name,rule.fields);
            }
        }

        class MatchRuleRunnable implements Runnable {
            @Override
            public void run() {
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                int i = 0;
                int n;
                // this thread only return when match out 100 rules 100 times.
                while (i < 100) {
                    List<String> actual = machine.rulesForEvent(event);
                    // the number of matched rules will keep growing from 0 till to 100
                    n = actual.size();
                    if (n == 0) {
                        i++;
                    }
                }
            }
        }

        ExecutorService execW = Executors.newFixedThreadPool(10);
        ExecutorService execR = Executors.newFixedThreadPool(300);
        for(int i = 0; i < 100; i++) {
            execW.execute(new DeleteRuleRunnable(i));
        }
        for(int i = 0; i < 300; i++) {
            execR.execute(new MatchRuleRunnable());
        }

        // release threads to let them work
        latch.countDown();

        execW.shutdown();
        execR.shutdown();
        try {
            execW.awaitTermination(5, TimeUnit.MINUTES);
            execR.awaitTermination(5, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        List<String> actual = machine.rulesForEvent(event);
        assertEquals(0, actual.size());
    }

    @Test
    public void testFunkyDelete() throws Exception {
        String rule = "{ \"foo\": { \"bar\": [ 23 ] }}";
        Machine cut = new Machine();

        // add the rule, ensure it matches
        cut.addRule("r1", rule);
        String[] event = { "foo.bar", "23" };
        assertEquals(1, cut.rulesForEvent(event).size());

        // delete the rule, no match
        cut.deleteRule("r1", rule);
        assertEquals(0, cut.rulesForEvent(event).size());
        assertTrue(cut.isEmpty());

        // add it back, it matches
        cut.addRule("r1", rule);
        assertEquals(1, cut.rulesForEvent(event).size());

        // delete it but with the wrong name.  Should be a no-op
        cut.deleteRule("r2", rule);
        assertEquals(1, cut.rulesForEvent(event).size());
        assertFalse(cut.isEmpty());

        // delete it but with the correct name.  Should be no match
        cut.deleteRule("r1", rule);
        assertEquals(0, cut.rulesForEvent(event).size());
        assertTrue(cut.isEmpty());

    }


    @Test
    public void testFunkyDelete1() throws Exception {

        String rule = "{ \"foo\": { \"bar\": [ 23, 45 ] }}";
        String rule1 = "{ \"foo\": { \"bar\": [ 23 ] }}";
        String rule2 = "{ \"foo\": { \"bar\": [ 45 ] }}";
        String rule3 = "{ \"foo\": { \"bar\": [ 44, 45, 46 ] }}";
        String rule4 = "{ \"foo\": { \"bar\": [ 44, 46, 47 ] }}";
        String rule5 = "{ \"foo\": { \"bar\": [ 23, 44, 45, 46, 47 ] }}";
        Machine cut = new Machine();

        // add the rule, ensure it matches
        cut.addRule("r1", rule);
        String[] event = { "foo.bar", "23" };
        String[] event1 = { "foo.bar", "45" };
        assertEquals(1, cut.rulesForEvent(event).size());
        assertEquals(1, cut.rulesForEvent(event1).size());

        // delete partial rule 23, partial match
        cut.deleteRule("r1", rule1);
        assertEquals(0, cut.rulesForEvent(event).size());
        assertEquals(1, cut.rulesForEvent(event1).size());

        // delete partial rule 45, no match
        cut.deleteRule("r1", rule2);
        assertEquals(0, cut.rulesForEvent(event).size());
        assertEquals(0, cut.rulesForEvent(event1).size());
        assertTrue(cut.isEmpty());

        // add it back, it matches
        cut.addRule("r1", rule);
        assertEquals(1, cut.rulesForEvent(event).size());
        assertEquals(1, cut.rulesForEvent(event1).size());

        // delete rule3, partially delete 45, 44 and 46 are not existing will be ignored,
        // so should only match 23
        cut.deleteRule("r1", rule3);
        assertEquals(1, cut.rulesForEvent(event).size());
        assertEquals(0, cut.rulesForEvent(event1).size());

        // delete rule then should match nothing ...
        cut.deleteRule("r1", rule);
        assertEquals(0, cut.rulesForEvent(event).size());
        assertEquals(0, cut.rulesForEvent(event1).size());
        assertTrue(cut.isEmpty());

        // add it back, it matches
        cut.addRule("r1", rule);
        assertEquals(1, cut.rulesForEvent(event).size());
        assertEquals(1, cut.rulesForEvent(event1).size());

        // delete rule4, as rule4 has nothing related with other rule, machine should do nothing.
        cut.deleteRule("r1", rule4);
        assertEquals(1, cut.rulesForEvent(event).size());
        assertEquals(1, cut.rulesForEvent(event1).size());

        // delete all
        cut.deleteRule("r1", rule5);
        assertEquals(0, cut.rulesForEvent(event).size());
        assertEquals(0, cut.rulesForEvent(event1).size());
        assertTrue(cut.isEmpty());
    }


    @Test
    public void testRangeDeletion() throws Exception {

        String rule = "{\"x\": [{\"numeric\": [\">=\", 0, \"<\", 1000000000]}]}";

        Machine cut = new Machine();

        // add the rule, ensure it matches
        cut.addRule("r1", rule);
        String[] event = {"x", "111111111.111111111"};
        String[] event1 = {"x", "1000000000"};
        assertEquals(1, cut.rulesForEvent(event).size());
        assertEquals(0, cut.rulesForEvent(event1).size());

        // delete partial rule 23, partial match
        cut.deleteRule("r1", rule);
        assertEquals(0, cut.rulesForEvent(event).size());
        assertEquals(0, cut.rulesForEvent(event1).size());
        assertTrue(cut.isEmpty());

        // test multiple key/value rule
        rule = "{\n" +
                "\"m\": [\"abc\"],\n" +
                "\"x\": [\n" +
                "{\n" +
                "\"numeric\": [\">\", 0, \"<\", 30]\n" +
                "},\n" +
                "{\n" +
                "\"numeric\": [\">\", 100, \"<\", 120]\n" +
                "}\n" +
                "],\n" +
                "\"n\": [\"efg\"]\n" +
                "}";
        cut.addRule("r2", rule);
        event = new String[]{"m", "\"abc\"", "n", "\"efg\"", "x", "23"};
        event1 = new String[]{"m", "\"abc\"", "n", "\"efg\"", "x", "110"};
        assertEquals(1, cut.rulesForEvent(event).size());
        assertEquals(1, cut.rulesForEvent(event1).size());
        cut.deleteRule("r2", rule);
        assertEquals(0, cut.rulesForEvent(event).size());
        assertEquals(0, cut.rulesForEvent(event1).size());
        assertTrue(cut.isEmpty());
    }

    @Test
    public void deleteRule() throws Exception {
        String rule = "{ \"foo\": { \"bar\": [ \"ab\", \"cd\" ] }}";
        Machine cut = new Machine();

        // add the rule, ensure it matches
        cut.addRule("r1", rule);
        String[] event = { "foo.bar", "\"ab\"" };
        String[] event1 = { "foo.bar", "\"cd\"" };
        String[] event2 = { "foo.bar", "\"ab\"", "foo.bar", "\"cd\"" };
        assertEquals(1, cut.rulesForEvent(event).size());
        assertEquals(1, cut.rulesForEvent(event1).size());
        assertEquals(1, cut.rulesForEvent(event2).size());

        Map<String, List<String>> namevals = new HashMap<>();
        // delete partial rule 23, partial match
        namevals.put("foo.bar", Collections.singletonList("\"ab\""));
        cut.deleteRule("r1", namevals);
        assertEquals(0, cut.rulesForEvent(event).size());
        assertEquals(1, cut.rulesForEvent(event1).size());
        assertEquals(1, cut.rulesForEvent(event2).size());

        namevals.put("foo.bar", Collections.singletonList("\"cd\""));
        cut.deleteRule("r1", namevals);
        assertEquals(0, cut.rulesForEvent(event).size());
        assertEquals(0, cut.rulesForEvent(event1).size());
        assertEquals(0, cut.rulesForEvent(event2).size());
    }

    @Test
    public void WHEN_RuleForJsonEventIsPresented_THEN_ItIsMatched() throws Exception {
        final Machine rulerMachine = new Machine();
        rulerMachine.addRule( "test-rule", "{ \"type\": [\"Notification\"] }" );

        String[] sortedEventFields = {
                "signature", "\"JYFVGfee...\"",
                "signatureVersion", "1",
                "signingCertUrl", "\"https://sns.us-east-1.amazonaws.com/SimpleNotificationService-1234.pem\"",
                "subscribeUrl", "null",
                "topicArn", "\"arn:aws:sns:us-east-1:108960525716:cw-to-sns-to-slack\"",
                "type", "\"Notification\""
                //, etc
        };

        List<String> foundRules = rulerMachine.rulesForEvent(sortedEventFields);
        assertTrue(foundRules.contains("test-rule"));
    }

    @Test
    public void OneEventWithDuplicatedKeyButDifferentValueMatchRules() throws Exception {

        Machine cut = new Machine();
        String rule1 = "{ \"foo\": { \"bar\": [ \"ab\" ] }}";
        String rule2 = "{ \"foo\": { \"bar\": [ \"cd\" ] }}";

        // add the rule, ensure it matches
        cut.addRule("r1", rule1);
        cut.addRule("r2", rule2);

        String[] event = { "foo.bar", "\"ab\"" };
        String[] event1 = { "foo.bar", "\"cd\"" };
        String[] event2 = { "foo.bar", "\"ab\"", "foo.bar", "\"cd\"" };


        assertEquals(1, cut.rulesForEvent(event).size());
        assertEquals(1, cut.rulesForEvent(event1).size());
        assertEquals(2, cut.rulesForEvent(event2).size());

        Map<String, List<String>> namevals = new HashMap<>();
        // delete partial rule 23, partial match
        namevals.put("foo.bar", Collections.singletonList("\"ab\""));
        cut.deleteRule("r1", namevals);
        assertEquals(0, cut.rulesForEvent(event).size());
        assertEquals(1, cut.rulesForEvent(event1).size());
        assertEquals(1, cut.rulesForEvent(event2).size());

        namevals.put("foo.bar", Collections.singletonList("\"cd\""));
        cut.deleteRule("r2", namevals);
        assertEquals(0, cut.rulesForEvent(event).size());
        assertEquals(0, cut.rulesForEvent(event1).size());
        assertEquals(0, cut.rulesForEvent(event2).size());
    }

    @Test
    public void OneRuleMadeByTwoConditions() throws Exception {

        Machine cut = new Machine();
        String condition1 = "{\n" +
                "\"A\" : [ \"on\" ],\n" +
                "\"C\" : [ \"on\" ],\n" +
                "\"D\" : [ \"off\" ]\n" +
                "}";
        String condition2 = "{\n" +
                "\"B\" : [\"on\" ],\n" +
                "\"C\" : [\"on\" ],\n" +
                "\"D\" : [\"off\" ]\n" +
                "}";

        // add the rule, ensure it matches
        cut.addRule("AlarmRule1", condition1);
        cut.addRule("AlarmRule1", condition2);

        String[] event = { "A", "\"on\"", "C", "\"on\"",  "D", "\"off\"" };
        String[] event1 = { "B", "\"on\"",  "C", "\"on\"",  "D", "\"off\"" };
        String[] event2 = { "A", "\"on\"",  "B", "\"on\"",  "C", "\"on\"",  "D", "\"on\"" };
        String[] event3 = { "A", "\"on\"",  "B", "\"on\"",  "C", "\"on\"",  "D", "\"off\"" };

        assertEquals(1, cut.rulesForEvent(event).size());
        assertEquals(1, cut.rulesForEvent(event1).size());
        assertEquals(0, cut.rulesForEvent(event2).size());
        assertEquals(1, cut.rulesForEvent(event3).size());

        cut.deleteRule("AlarmRule1", condition1);
        assertEquals(0, cut.rulesForEvent(event).size());
        assertEquals(1, cut.rulesForEvent(event1).size());
        assertEquals(0, cut.rulesForEvent(event2).size());
        assertEquals(1, cut.rulesForEvent(event3).size());

        cut.deleteRule("AlarmRule1", condition2);
        assertEquals(0, cut.rulesForEvent(event).size());
        assertEquals(0, cut.rulesForEvent(event1).size());
        assertEquals(0, cut.rulesForEvent(event2).size());
        assertEquals(0, cut.rulesForEvent(event3).size());
    }

    @Test
    public void testNumericMatch() throws Exception {
        String rule = "{\n" +
                "\"a\": [ { \"numeric\": [ \"=\", 0 ] } ],\n" +
                "\"b\": [ { \"numeric\": [ \"<\", 0 ] } ],\n" +
                "\"c\": [ { \"numeric\": [ \"<=\", 0 ] } ],\n" +
                "\"x\": [ { \"numeric\": [ \">\", 0 ] } ],\n" +
                "\"y\": [ { \"numeric\": [ \">=\", 0 ] } ],\n" +
                "\"z\": [ { \"numeric\": [ \">\", 0, \"<\", 1 ] } ]\n" +
                "}";
        Machine cut = new Machine();

        // add the rule, ensure it matches
        cut.addRule("r1", rule);
        String[] event = {"a", "0", "b", "-0.1", "c", "0", "x", "1", "y", "0", "z", "0.1"};
        String eventJSON = "{\n" +
                "\t\"a\": [0],\n" +
                "\t\"b\": [-0.1],\n" +
                "\t\"c\": [0],\n" +
                "\t\"x\": [1],\n" +
                "\t\"y\": [0],\n" +
                "\t\"z\": [0.1]\n" +
                "}";
        assertEquals(1, cut.rulesForEvent(event).size());
        assertTrue(Ruler.matchesRule(eventJSON, rule));

        // delete partial rule
        cut.deleteRule("r1", rule);
        assertEquals(0, cut.rulesForEvent(event).size());
        assertTrue(Ruler.matchesRule(eventJSON, rule));
        assertTrue(cut.isEmpty());
    }

    @Test
    public void testAnythingButDeletion() throws Exception {
        String rule = "{\n" +
                "\"a\": [ { \"anything-but\": [ \"dad0\",\"dad1\",\"dad2\" ] } ],\n" +
                "\"b\": [ { \"anything-but\": [ 111, 222, 333 ] } ],\n" +
                "\"c\": [ { \"anything-but\": \"dad0\" } ],\n" +
                "\"d\": [ { \"anything-but\": 111 } ],\n" +
                "\"z\": [ { \"numeric\": [ \">\", 0, \"<\", 1 ] } ]\n" +
                "}";
        Machine cut = new Machine();

        // add the rule, ensure it matches
        cut.addRule("r1", rule);

        String event = "{" +
                "    \"a\": \"child1\",\n" +
                "    \"b\": \"444\",\n" +
                "    \"c\": \"child1\",\n" +
                "    \"d\": \"444\",\n" +
                "    \"z\": 0.001 \n" +
                "}\n";
        String event1 = "{" +
                "    \"a\": \"dad4\",\n" +
                "    \"b\": 444,\n" +
                "    \"c\": \"child1\",\n" +
                "    \"d\": \"444\",\n" +
                "    \"z\": 0.001 \n" +
                "}\n";

        assertEquals(1, cut.rulesForEvent(event).size());
        assertEquals(1, cut.rulesForEvent(event1).size());

        // delete partial rule 23, partial match
        cut.deleteRule("r1", rule);
        assertEquals(0, cut.rulesForEvent(event).size());
        assertEquals(0, cut.rulesForEvent(event1).size());
        assertTrue(cut.isEmpty());
    }

    @Test
    public void testAnythingButEqualsIgnoreCaseDeletion() throws Exception {
        String rule = "{\n" +
                "\"a\": [ { \"anything-but\": {\"equals-ignore-case\": [ \"dad0\",\"dad1\",\"dad2\" ] } } ],\n" +
                "\"c\": [ { \"anything-but\": {\"equals-ignore-case\": \"dad0\" } } ],\n" +
                "\"z\": [ { \"numeric\": [ \">\", 0, \"<\", 1 ] } ]\n" +
                "}";
        Machine cut = new Machine();
    
        // add the rule, ensure it matches
        cut.addRule("r1", rule);
        
        String event = "{" +
                "    \"a\": \"Child1\",\n" +
                "    \"c\": \"chiLd1\",\n" +
                "    \"z\": 0.001 \n" + 
                "}\n";
        String event1 = "{" +
                "    \"a\": \"dAd4\",\n" +
                "    \"c\": \"Child1\",\n" +
                "    \"z\": 0.001 \n" +
                "}\n";

        assertEquals(1, cut.rulesForEvent(event).size());
        assertEquals(1, cut.rulesForEvent(event1).size());

        // delete partial rule 23, partial match
        cut.deleteRule("r1", rule);
        assertEquals(0, cut.rulesForEvent(event).size());
        assertEquals(0, cut.rulesForEvent(event1).size());
        assertTrue(cut.isEmpty());
    }

    @Test
    public void testIpExactMatch() throws Exception {

        String[] ipAddressesToBeMatched = {
                "0011:2233:4455:6677:8899:aabb:ccdd:eeff",
                "2001:db8::ff00:42:8329",
                "::0",
                "::3",
                "::9",
                "::a",
                "::f",
                "2400:6500:FF00::36FB:1F80",
                "2400:6500:FF00::36FB:1F85",
                "2400:6500:FF00::36FB:1F89",
                "2400:6500:FF00::36FB:1F8C",
                "2400:6500:FF00::36FB:1F8F",
                "54.240.196.255",
                "255.255.255.255",
                "255.255.255.0",
                "255.255.255.5",
                "255.255.255.10",
                "255.255.255.16",
                "255.255.255.26",
                "255.255.255.27"
        };

        String[] ipAddressesNotBeMatched = {
                "0011:2233:4455:6677:8899:aabb:ccdd:eefe",
                "2001:db8::ff00:42:832a",
                "::1",
                "::4",
                "::8",
                "::b",
                "::e",
                "2400:6500:FF00::36FB:1F81",
                "2400:6500:FF00::36FB:1F86",
                "2400:6500:FF00::36FB:1F8a",
                "2400:6500:FF00::36FB:1F8D",
                "2400:6500:FF00::36FB:1F8E",
                "54.240.196.254",
                "255.255.255.254",
                "255.255.255.1",
                "255.255.255.6",
                "255.255.255.11",
                "255.255.255.17",
                "255.255.255.28",
                "255.255.255.29"
        };

        String sourceTemplate = "{" +
                                "    \"detail\": {" +
                                "        \"eventName\": [" +
                                "            \"UpdateService\"" +
                                "        ],\n" +
                                "        \"eventSource\": [\n" +
                                "            \"ecs.amazonaws.com\"" +
                                "        ]," +
                                "        \"sourceIPAddress\": [" +
                                "            \"%s\"" +
                                "        ]\n" +
                                "    },\n" +
                                "    \"detail-type\": [" +
                                "        \"AWS API Call via CloudTrail\"" +
                                "    ],\n" +
                                "    \"source\": [" +
                                "        \"aws.ecs\"" +
                                "    ]" +
                                "}";

        Machine machine = new Machine();

        for (String ip : ipAddressesToBeMatched) {
            machine.addRule(ip, String.format(sourceTemplate, ip));
        }

        for (String ip : ipAddressesToBeMatched) {
            List<String> rules =machine.rulesForJSONEvent(String.format(sourceTemplate, ip));
            assertEquals(ip, rules.get(0));
        }

        for (String ip : ipAddressesNotBeMatched) {
            List<String> rules =machine.rulesForJSONEvent(String.format(sourceTemplate, ip));
            assertEquals(0, rules.size());
        }

        for (String ip : ipAddressesToBeMatched) {
            machine.deleteRule(ip, String.format(sourceTemplate, ip));
        }

        assertTrue(machine.isEmpty());
    }

    @Test
    public void testExists() throws Exception {
        String rule1 = "{\"abc\": [{\"exists\": false}]}";
        String rule2 = "{\"abc\": [{\"exists\": true}]}";

        Machine machine = new Machine();
        machine.addRule("rule1", rule1);
        machine.addRule("rule2", rule2);

        String event1 = "{\"abc\": \"xyz\"}";
        String event2 = "{\"xyz\": \"abc\"}";

        List<String> matches = machine.rulesForEvent(event1);
        assertEquals(1, matches.size());
        assertTrue(matches.contains("rule2"));
        matches = machine.rulesForEvent(event2);
        assertEquals(1, matches.size());
        assertTrue(matches.contains("rule1"));
    }

    @Test
    public void testAddAndDeleteTwoRulesSamePattern() throws Exception {
        final Machine machine = new Machine();
        String event = "{\n" +
                "  \"x\": \"y\"\n" +
                "}";

        String rule1 = "{\n" +
                "  \"x\": [ \"y\" ]\n" +
                "}";

        String rule2 = "{\n" +
                "  \"x\": [ \"y\" ]\n" +
                "}";

        machine.addRule("rule1", rule1);
        machine.addRule("rule2", rule2);

        List<String> found = machine.rulesForEvent(event);
        assertEquals(2, found.size());
        assertTrue(found.contains("rule1"));
        assertTrue(found.contains("rule2"));

        machine.deleteRule("rule1", rule1);
        found = machine.rulesForEvent(event);
        assertEquals(1, found.size());
        machine.deleteRule("rule2", rule2);
        found = machine.rulesForEvent(event);
        assertEquals(0, found.size());
        assertTrue(machine.isEmpty());
    }

    @Test
    public void testAddAndDeleteTwoRulesSameCaseInsensitivePatternEqualsIgnoreCase() throws Exception {
        final Machine machine = new Machine();
        String event = "{\n" +
                "  \"x\": \"y\"\n" +
                "}";

        String rule1 = "{\n" +
                "  \"x\": [ { \"equals-ignore-case\": \"y\" } ]\n" +
                "}";

        String rule2 = "{\n" +
                "  \"x\": [ { \"equals-ignore-case\": \"Y\" } ]\n" +
                "}";

        machine.addRule("rule1", rule1);
        machine.addRule("rule2", rule2);

        List<String> found = machine.rulesForEvent(event);
        assertEquals(2, found.size());
        assertTrue(found.contains("rule1"));
        assertTrue(found.contains("rule2"));

        machine.deleteRule("rule1", rule1);
        found = machine.rulesForEvent(event);
        assertEquals(1, found.size());
        machine.deleteRule("rule2", rule2);
        found = machine.rulesForEvent(event);
        assertEquals(0, found.size());
        assertTrue(machine.isEmpty());
    }

    @Test
    public void testDuplicateKeyLastOneWins() throws Exception {
        final Machine machine = new Machine();
        String event1 = "{\n" +
                "  \"x\": \"y\"\n" +
                "}";
        String event2 = "{\n" +
                "  \"x\": \"z\"\n" +
                "}";

        String rule1 = "{\n" +
                "  \"x\": [ \"y\" ],\n" +
                "  \"x\": [ \"z\" ]\n" +
                "}";

        machine.addRule("rule1", rule1);

        List<String> found = machine.rulesForEvent(event1);
        assertEquals(0, found.size());
        found = machine.rulesForEvent(event2);
        assertEquals(1, found.size());
        assertTrue(found.contains("rule1"));
    }

    @Test
    public void testSharedNameState() throws Exception {
        // "bar" will be the first key (alphabetical)
        String rule1 = "{\"foo\":[\"a\"], \"bar\":[\"x\", \"y\"]}";
        String rule2 = "{\"foo\":[\"a\", \"b\"], \"bar\":[\"x\"]}";
        String rule3 = "{\"foo\":[\"a\", \"b\"], \"bar\":[\"y\"]}";

        Machine machine = new Machine();
        machine.addRule("rule1", rule1);
        machine.addRule("rule2", rule2);
        machine.addRule("rule3", rule3);

        String event = "{" +
                "\"foo\": \"a\"," +
                "\"bar\": \"x\"" +
                "}";

        // Ensure rule3 does not piggyback on rule1's shared NameState accessed by both "x" and "y" for "bar"
        List<String> matches = machine.rulesForEvent(event);
        assertEquals(2, matches.size());
        assertTrue(matches.contains("rule1"));
        assertTrue(matches.contains("rule2"));
    }

    @Test
    public void testRuleDeletionFromSharedNameState() throws Exception {
        // "bar" will be the first key (alphabetical) and both rules have a match on "x", leading to a shared NameState
        String rule1 = "{\"foo\":[\"a\"], \"bar\":[\"x\", \"y\"]}";
        String rule2 = "{\"foo\":[\"b\"], \"bar\":[\"x\"]}";

        Machine machine = new Machine();
        machine.addRule("rule1", rule1);
        machine.addRule("rule2", rule2);

        String event1 = "{" +
                "\"foo\": \"a\"," +
                "\"bar\": \"x\"" +
                "}";
        String event2 = "{" +
                "\"foo\": \"a\"," +
                "\"bar\": \"y\"" +
                "}";

        List<String> matches = machine.rulesForEvent(event1);
        assertEquals(1, matches.size());
        assertTrue(matches.contains("rule1"));
        matches = machine.rulesForEvent(event2);
        assertEquals(1, matches.size());
        assertTrue(matches.contains("rule1"));

        // Shared NameState will remain as it is used by rule1
        machine.deleteRule("rule2", rule2);

        matches = machine.rulesForEvent(event1);
        assertEquals(1, matches.size());
        assertTrue(matches.contains("rule1"));
        matches = machine.rulesForEvent(event2);
        assertEquals(1, matches.size());
        assertTrue(matches.contains("rule1"));

        // "y" also leads to the shared NameState. The shared NameState will get extended by rule2's "foo" field. By
        // checking that only events with a "bar" of "y" and not a "bar" of "x", we verify that no remnants of the
        // original rule2 were left in the shared NameState.
        String rule2b = "{\"foo\":[\"a\"], \"bar\":[\"y\"]}";

        machine.addRule("rule2", rule2b);

        matches = machine.rulesForEvent(event1);
        assertEquals(1, matches.size());
        assertTrue(matches.contains("rule1"));
        matches = machine.rulesForEvent(event2);
        assertEquals(2, matches.size());
        assertTrue(matches.contains("rule1"));
        assertTrue(matches.contains("rule2"));
    }

    @Test
    public void testPrefixRuleDeletionFromSharedNameState() throws Exception {
        // "bar", "foo", "zoo" will be the key order (alphabetical)
        String rule1 = "{\"zoo\":[\"1\"], \"foo\":[\"a\"], \"bar\":[\"x\"]}";
        String rule2 = "{\"foo\":[\"a\"], \"bar\":[\"x\"]}";

        Machine machine = new Machine();
        machine.addRule("rule1", rule1);
        machine.addRule("rule2", rule2);

        String event = "{" +
                "\"zoo\": \"1\"," +
                "\"foo\": \"a\"," +
                "\"bar\": \"x\"" +
                "}";

        // Only rule1 should match
        List<String> matches = machine.rulesForEvent(event);
        assertEquals(2, matches.size());
        assertTrue(matches.contains("rule1"));
        assertTrue(matches.contains("rule2"));

        // Delete rule2, which is a subpath/prefix of rule1, and ensure full path still exists for rule1 to match
        machine.deleteRule("rule2", rule2);

        matches = machine.rulesForEvent(event);
        assertEquals(1, matches.size());
        assertTrue(matches.contains("rule1"));
    }

    @Test
    public void testDifferentValuesFromOrRuleBothGoThroughSharedNameState() throws Exception {
        // "bar", "foo", "zoo" will be the key order (alphabetical)
        String rule1 = "{\"foo\":[\"a\"], \"bar\":[\"x\", \"y\"]}";
        String rule2 = "{\"zoo\":[\"1\"], \"bar\":[\"x\"]}";
        String rule2b = "{\"foo\":[\"a\"], \"bar\":[\"y\"]}";

        Machine machine = new Machine();
        machine.addRule("rule1", rule1);
        machine.addRule("rule2", rule2);
        machine.addRule("rule2", rule2b);

        String event = "{" +
                "\"foo\": \"a\"," +
                "\"bar\": \"x\"" +
                "}";

        // Only rule1 should match
        List<String> matches = machine.rulesForEvent(event);
        assertEquals(1, matches.size());
        assertTrue(matches.contains("rule1"));
    }

    @Test
    public void testDifferentValuesFromExplicitOrRuleBothGoThroughSharedNameState() throws Exception {
        // "bar", "foo", "zoo" will be the key order (alphabetical)
        String rule1 = "{\"foo\":[\"a\"], \"bar\":[\"x\", \"y\"]}";
        String rule2 = "{\"$or\":[{\"zoo\":[\"1\"], \"bar\":[\"x\"]}, {\"foo\":[\"a\"], \"bar\":[\"y\"]}]}";

        Machine machine = new Machine();
        machine.addRule("rule1", rule1);
        machine.addRule("rule2", rule2);

        String event = "{" +
                "\"foo\": \"a\"," +
                "\"bar\": \"x\"" +
                "}";

        // Only rule1 should match
        List<String> matches = machine.rulesForEvent(event);
        assertEquals(1, matches.size());
        assertTrue(matches.contains("rule1"));
    }

    @Test
    public void testRuleIsSingleItemSubsetOfOtherRule() throws Exception {
        // Second rule added pertains to same field as first rule but has a subset of the allowed values.
        String rule1 = "{\"foo\": [\"a\", \"b\", \"c\"]}";
        String rule2 = "{\"foo\": [\"b\"]}";

        Machine machine = new Machine();
        machine.addRule("rule1", rule1);
        machine.addRule("rule2", rule2);

        String event = "{" +
                "\"foo\": \"a\"" +
                "}";

        // Only rule1 should match
        List<String> matches = machine.rulesForEvent(event);
        assertEquals(1, matches.size());
        assertTrue(matches.contains("rule1"));
    }

    @Test
    public void testRuleIsMultipleItemSubsetOfOtherRule() throws Exception {
        // Second rule added pertains to same field as first rule but has a subset of the allowed values.
        String rule1 = "{\"foo\": [\"a\", \"b\", \"c\", \"d\", \"e\", \"f\", \"g\"]}";
        String rule2 = "{\"foo\": [\"b\", \"d\", \"f\"]}";

        Machine machine = new Machine();
        machine.addRule("rule1", rule1);
        machine.addRule("rule2", rule2);

        String event = "{" +
                "\"foo\": \"e\"" +
                "}";

        // Only rule1 should match
        List<String> matches = machine.rulesForEvent(event);
        assertEquals(1, matches.size());
        assertTrue(matches.contains("rule1"));
    }

    @Test
    public void testRuleIsSingleItemSubsetOfOtherRuleWithPrecedingKey() throws Exception {
        // Second rule added pertains to same field as first rule but has a subset of the allowed values.
        String rule1 = "{\"bar\": [\"1\"], \"foo\": [\"a\", \"b\", \"c\"]}";
        String rule2 = "{\"bar\": [\"1\"], \"foo\": [\"b\"]}";

        Machine machine = new Machine();
        machine.addRule("rule1", rule1);
        machine.addRule("rule2", rule2);

        String event = "{" +
                "\"bar\": \"1\"," +
                "\"foo\": \"a\"" +
                "}";

        // Only rule1 should match
        List<String> matches = machine.rulesForEvent(event);
        assertEquals(1, matches.size());
        assertTrue(matches.contains("rule1"));
    }

    @Test
    public void testRuleIsMultipleItemSubsetOfOtherRuleWithPrecedingKey() throws Exception {
        // Second rule added pertains to same field as first rule but has a subset of the allowed values.
        String rule1 = "{\"bar\": [\"1\"], \"foo\": [\"a\", \"b\", \"c\", \"d\", \"e\", \"f\", \"g\"]}";
        String rule2 = "{\"bar\": [\"1\"], \"foo\": [\"b\", \"d\", \"f\"]}";

        Machine machine = new Machine();
        machine.addRule("rule1", rule1);
        machine.addRule("rule2", rule2);

        String event = "{" +
                "\"bar\": \"1\"," +
                "\"foo\": \"e\"" +
                "}";

        // Only rule1 should match
        List<String> matches = machine.rulesForEvent(event);
        assertEquals(1, matches.size());
        assertTrue(matches.contains("rule1"));
    }

    @Test
    public void testRuleIsSingleItemSubsetOfOtherRuleWithFollowingKey() throws Exception {
        // Second rule added pertains to same field as first rule but has a subset of the allowed values.
        String rule1 = "{\"zoo\": [\"1\"], \"foo\": [\"a\", \"b\", \"c\"]}";
        String rule2 = "{\"zoo\": [\"1\"], \"foo\": [\"b\"]}";

        Machine machine = new Machine();
        machine.addRule("rule1", rule1);
        machine.addRule("rule2", rule2);

        String event = "{" +
                "\"zoo\": \"1\"," +
                "\"foo\": \"a\"" +
                "}";

        // Only rule1 should match
        List<String> matches = machine.rulesForEvent(event);
        assertEquals(1, matches.size());
        assertTrue(matches.contains("rule1"));
    }

    @Test
    public void testRuleIsMultipleItemSubsetOfOtherRuleWithFollowingKey() throws Exception {
        // Second rule added pertains to same field as first rule but has a subset of the allowed values.
        String rule1 = "{\"zoo\": [\"1\"], \"foo\": [\"a\", \"b\", \"c\", \"d\", \"e\", \"f\", \"g\"]}";
        String rule2 = "{\"zoo\": [\"1\"], \"foo\": [\"b\", \"d\", \"f\"]}";

        Machine machine = new Machine();
        machine.addRule("rule1", rule1);
        machine.addRule("rule2", rule2);

        String event = "{" +
                "\"zoo\": \"1\"," +
                "\"foo\": \"e\"" +
                "}";

        // Only rule1 should match
        List<String> matches = machine.rulesForEvent(event);
        assertEquals(1, matches.size());
        assertTrue(matches.contains("rule1"));
    }

    @Test
    public void testExistsFalseAsFinalKeyAfterSharedNameState() throws Exception {
        // Second rule added uses same NameState for bar, but then has a final key, foo, that must not exist.
        String rule1 = "{\"bar\": [\"a\", \"b\"]}";
        String rule2 = "{\"bar\": [\"b\"], \"foo\": [{\"exists\": false}]}";

        Machine machine = new Machine();
        machine.addRule("rule1", rule1);
        machine.addRule("rule2", rule2);

        String event = "{" +
                "\"bar\": \"a\"" +
                "}";

        // Only rule1 should match
        List<String> matches = machine.rulesForEvent(event);
        assertEquals(1, matches.size());
        assertTrue(matches.contains("rule1"));
    }

    @Test
    public void testExistsTrueAsFinalKeyAfterSharedNameState() throws Exception {
        // Second rule added uses same NameState for bar, but then has a final key, foo, that must exist.
        String rule1 = "{\"bar\": [\"a\", \"b\"]}";
        String rule2 = "{\"bar\": [\"b\"], \"foo\": [{\"exists\": true}]}";

        Machine machine = new Machine();
        machine.addRule("rule1", rule1);
        machine.addRule("rule2", rule2);

        String event = "{" +
                "\"bar\": \"a\"," +
                "\"foo\": \"1\"" +
                "}";

        // Only rule1 should match
        List<String> matches = machine.rulesForEvent(event);
        assertEquals(1, matches.size());
        assertTrue(matches.contains("rule1"));
    }

    @Test
    public void testExistsFalseNameStateSharedWithSpecificValueMatch() throws Exception {
        // First rule adds a NameState for exists=false. Second rule will use this same NameState and add a value of "1"
        // to it. Third rule will now use this same shared NameState as well due to its value of "1".
        String rule1 = "{\"foo\": [\"a\"], \"bar\": [{\"exists\": false}]}";
        String rule2 = "{\"foo\": [\"b\"], \"bar\": [{\"exists\": false}, \"1\"]}";
        String rule3 = "{\"foo\": [\"a\"], \"bar\": [\"1\"]}";

        Machine machine = new Machine();
        machine.addRule("rule1", rule1);
        machine.addRule("rule2", rule2);
        machine.addRule("rule3", rule3);

        String event = "{" +
                "\"foo\": \"a\"" +
                "}";

        // Only rule1 should match
        List<String> matches = machine.rulesForEvent(event);
        assertEquals(1, matches.size());
        assertTrue(matches.contains("rule1"));
    }

    @Test
    public void testExistsTrueNameStateSharedWithSpecificValueMatch() throws Exception {
        // First rule adds a NameState for exists=true. Second rule will use this same NameState and add a value of "1"
        // to it. Third rule will now use this same shared NameState as well due to its value of "1".
        String rule1 = "{\"foo\": [\"a\"], \"bar\": [{\"exists\": true}]}";
        String rule2 = "{\"foo\": [\"b\"], \"bar\": [{\"exists\": true}, \"1\"]}";
        String rule3 = "{\"foo\": [\"a\"], \"bar\": [\"1\"]}";

        Machine machine = new Machine();
        machine.addRule("rule1", rule1);
        machine.addRule("rule2", rule2);
        machine.addRule("rule3", rule3);

        String event = "{" +
                "\"foo\": \"a\"," +
                "\"bar\": \"1\"" +
                "}";

        // Only rule1 and rule3 should match
        List<String> matches = machine.rulesForEvent(event);
        assertEquals(2, matches.size());
        assertTrue(matches.contains("rule1"));
        assertTrue(matches.contains("rule3"));
    }

    @Test
    public void testInitialSharedNameStateWithTwoMustNotExistsIsTerminalForOnlyOne() throws Exception {
        // Initial NameState has two different (bar and foo) exists=false patterns. One is terminal, whereas the other
        // leads to another NameState with another key (zoo).
        String rule1 = "{\"bar\": [{\"exists\": false}]}";
        String rule2 = "{\"foo\": [{\"exists\": false}], \"zoo\": [\"a\"]}";

        Machine machine = new Machine();
        machine.addRule("rule1", rule1);
        machine.addRule("rule2", rule2);

        String event = "{" +
                "\"zoo\": \"a\"" +
                "}";

        List<String> matches = machine.rulesForEvent(event);
        assertEquals(2, matches.size());
        assertTrue(matches.contains("rule1"));
        assertTrue(matches.contains("rule2"));
    }

    @Test
    public void testSharedNameStateForMultipleAnythingButPatterns() throws Exception {
        // Every event will match this rule because any bar that is "a" cannot also be "b".
        String rule1 = "{\"bar\": [{\"anything-but\": \"a\"}, {\"anything-but\": \"b\"}]}";

        Machine machine = new Machine();
        machine.addRule("rule1", rule1);

        String event = "{\"bar\": \"b\"}";

        List<String> matches = machine.rulesForEvent(event);
        assertEquals(1, matches.size());
        assertTrue(matches.contains("rule1"));
    }

    @Test
    public void testSharedNameStateWithTwoSubRulesDifferingAtFirstNameState() throws Exception {
        // Two different sub-rules here with a NameState after bar and after foo: (bar=1, foo=a) and (bar=2, foo=a).
        String rule1 = "{\"$or\": [{\"bar\": [\"1\"]}, {\"bar\": [\"2\"]}]," +
                        "\"foo\": [\"a\"] }";

        Machine machine = new Machine();
        machine.addRule("rule1", rule1);

        String event = "{\"bar\": \"2\"," +
                        "\"foo\": \"a\"}";

        List<String> matches = machine.rulesForEvent(event);
        assertEquals(1, matches.size());
        assertTrue(matches.contains("rule1"));
    }

    @Test
    public void testInitialSharedNameStateAlreadyExistsWithNonLeadingValue() throws Exception {
        // When rule2 is added, a NameState will already exist for bar=b. Adding bar=a will lead to a new initial
        // NameState, and then the existing NameState for bar=b will be encountered.
        String rule1 = "{\"bar\" :[\"b\"], \"foo\": [\"c\"]}";
        String rule2 = "{\"bar\": [\"a\", \"b\"], \"foo\": [\"c\"]}";

        Machine machine = new Machine();

        machine.addRule("rule1", rule1);
        machine.addRule("rule2", rule2);

        String event = "{\"bar\": \"a\"," +
                "\"foo\": \"c\"}";

        List<String> matches = machine.rulesForEvent(event);
        assertEquals(1, matches.size());
        assertTrue(matches.contains("rule2"));
    }

    @Test
    public void testApproxSizeForSimplestPossibleMachine() throws Exception {
        String rule1 = "{ \"a\" : [ 1 ] }";
        String rule2 = "{ \"b\" : [ 2 ] }";
        String rule3 = "{ \"c\" : [ 3 ] }";

        Machine machine = new Machine();
        assertEquals(1, machine.approximateObjectCount(10000));

        machine.addRule("r1", rule1);
        assertEquals(23, machine.approximateObjectCount(10000));

        machine.addRule("r2", rule2);
        assertEquals(44, machine.approximateObjectCount(10000));

        machine.addRule("r3", rule3);
        assertEquals(65, machine.approximateObjectCount(10000));
    }

    @Test
    public void testApproxSizeForDuplicatedRules() throws Exception {
        String rule1 = "{ \"a\" : [ 1, 2, 3 ], \"b\" : [4, 5, 6] }";

        Machine machine = new Machine();
        machine.addRule("r1", rule1);
        assertEquals(80, machine.approximateObjectCount(10000));

        // Adding the same rule multiple times should not increase object count
        for (int i = 0; i < 100; i++) {
            machine.addRule("r1", rule1);
        }
        assertEquals(80, machine.approximateObjectCount(10000));
    }

    @Test
    public void testApproxSizeForAddingDuplicateRuleExceptTerminalKeyIsSubset() throws Exception {
        String rule1a = "{ \"a\" : [ 1, 2, 3 ], \"b\" : [4, 5, 6] }";
        String rule1b = "{ \"a\" : [ 1, 2, 3 ], \"b\" : [4, 5] }";

        Machine machine = new Machine();
        machine.addRule("r1", rule1a);
        assertEquals(80, machine.approximateObjectCount(10000));

        // Adding rule with terminal key having subset of values will be treated as same rule and thus increase size
        machine.addRule("r1", rule1b);
        assertEquals(80, machine.approximateObjectCount(10000));
    }

    @Test
    public void testApproxSizeForAddingDuplicateRuleExceptNonTerminalKeyIsSubset() throws Exception {
        String rule1a = "{ \"a\" : [ 1, 2, 3 ], \"b\" : [4, 5, 6] }";
        String rule1b = "{ \"a\" : [ 1, 2 ], \"b\" : [4, 5, 6] }";

        Machine machine = new Machine();
        machine.addRule("r1", rule1a);
        assertEquals(80, machine.approximateObjectCount(10000));

        // Adding rule with non-terminal key having subset of values will be treated as same rule and not affect count
        machine.addRule("r1", rule1b);
        assertEquals(80, machine.approximateObjectCount(10000));
    }

    @Test
    public void testApproxSizeForAddingDuplicateRuleExceptTerminalKeyIsSuperset() throws Exception {
        String rule1a = "{ \"a\" : [ 1, 2, 3 ], \"b\" : [4, 5, 6] }";
        String rule1b = "{ \"a\" : [ 1, 2, 3 ], \"b\" : [4, 5, 6, 7] }";

        Machine machine = new Machine();
        machine.addRule("r1", rule1a);
        assertEquals(80, machine.approximateObjectCount(10000));

        // Adding rule with terminal key having superset of values will be treated as new rule and increase count
        machine.addRule("r1", rule1b);
        assertEquals(90, machine.approximateObjectCount(10000));
    }

    @Test
    public void testApproxSizeForAddingDuplicateRuleExceptNonTerminalKeyIsSuperset() throws Exception {
        String rule1a = "{ \"a\" : [ 1, 2, 3 ], \"b\" : [4, 5, 6] }";
        String rule1b = "{ \"a\" : [ 1, 2, 3, 7 ], \"b\" : [4, 5, 6] }";

        Machine machine = new Machine();
        machine.addRule("r1", rule1a);
        assertEquals(80, machine.approximateObjectCount(10000));

        // Adding rule with non-terminal key having superset of values will be treated as new rule and increase count
        machine.addRule("r1", rule1b);
        assertEquals(90, machine.approximateObjectCount(10000));
    }

    @Test
    public void testSuffixChineseMatch() throws Exception {
        Machine m = new Machine();
        String rule = "{\n" +
                "   \"status\": {\n" +
                "       \"weatherText\": [{\"suffix\": \"统治者\"}]\n" +
                "    }\n" +
                "}";
        String eventStr ="{\n" +
                "  \"status\": {\n" +
                "    \"weatherText\": \"事件统治者\",\n" +
                "    \"pm25\": 23\n" +
                "  }\n" +
                "}";
        m.addRule("r1", rule);
        List<String> matchRules = m.rulesForEvent(eventStr);
        assertEquals(1, matchRules.size());
    }

    @Test(timeout = 500)
    public void testApproximateSizeDoNotTakeForeverForRulesWithNumericMatchers() throws Exception {
        Machine machine = new Machine();
        machine.addRule("rule",
                "{\n" +
                        "    \"a\": [{  \"numeric\": [\"<\", 0] }],\n" +
                        "    \"b\": { \"b1\": [{ \"numeric\": [\"=\", 3] }] },\n" +
                        "    \"c\": [{ \"numeric\": [\">\", 50] }]\n" +
                        "}");

        assertEquals(517, machine.approximateObjectCount(10000));
    }

    @Test
    public void testApproximateSizeForDifferentBasicRules() throws Exception {
        Machine machine = new Machine();
        assertEquals(1, machine.approximateObjectCount(10000));


        machine.addRule("single-rule", "{ \"key\" :  [ \"value\" ] }");
        assertEquals(8, machine.approximateObjectCount(10000));

        // every new rule also is considered as part of the end-state
        machine = new Machine();
        for(int i = 0 ; i < 1000; i ++) {
            machine.addRule("lots-rule-" + i, "{ \"key\" :  [ \"value\" ] }");
        }
        assertEquals(1007, machine.approximateObjectCount(10000));

        // new unique rules create new states
        machine = new Machine();
        for(int i = 0 ; i < 1000; i ++) {
            machine.addRule("lots-key-values-" + i, "{ \"many-kv-" + i + "\" :  [ \"value" + i + "\" ] }");
        }
        assertEquals(7001, machine.approximateObjectCount(10000));

        // new unique rule keys create same states as unique rules
        machine = new Machine();
        for(int i = 0 ; i < 1000; i ++) {
            machine.addRule("lots-keys-" + i, "{ \"many-key-" + i + "\" :  [ \"value\" ] }");
        }
        assertEquals(6002, machine.approximateObjectCount(10000));

        // new unique rule with many values are smaller
        machine = new Machine();
        for(int i = 0 ; i < 1000; i ++) {
            machine.addRule("lots-values-" + i, "{ \"many-values-key\" :  [ \"value" + i + " \" ] }");
        }
        assertEquals(5108, machine.approximateObjectCount(10000));
    }

    @Test
    public void testApproximateSizeWhenCapped() throws Exception {
        Machine machine = new Machine();
        assertEquals(0, machine.approximateObjectCount(0));
        assertEquals(1, machine.approximateObjectCount(1));
        assertEquals(1, machine.approximateObjectCount(10));

        machine.addRule("single-rule", "{ \"key\" :  [ \"value\" ] }");
        assertEquals(1, machine.approximateObjectCount(1));
        assertEquals(8, machine.approximateObjectCount(10));

        for(int i = 0 ; i < 100000; i ++) {
            machine.addRule("lots-rule-" + i, "{ \"key\" :  [ \"value\" ] }");
        }
        for(int i = 0 ; i < 100000; i ++) {
            machine.addRule("lots-key-values-" + i, "{ \"many-kv-" + i + "\" :  [ \"value" + i + "\" ] }");
        }
        for(int i = 0 ; i < 100000; i ++) {
            machine.addRule("lots-keys-" + i, "{ \"many-key-" + i + "\" :  [ \"value\" ] }");
        }
        for(int i = 0 ; i < 100000; i ++) {
            machine.addRule("lots-values-" + i, "{ \"many-values-key\" :  [ \"value" + i + " \" ] }");
        }
        assertApproximateCountWithTime(machine, 1000, 1000, 150);
        assertApproximateCountWithTime(machine, Integer.MAX_VALUE, 1910015, 5000);
    }

    private void assertApproximateCountWithTime(Machine machine, int maxThreshold, int expectedValue, int maxExpectedDurationMillis) {
        final Instant start = Instant.now();
        assertEquals(expectedValue, machine.approximateObjectCount(maxThreshold));
        assertTrue(maxExpectedDurationMillis > Duration.between(start, Instant.now()).toMillis());
    }

    @Test
    public void testApproximateSizeForRulesManyEventNameArrayElements() throws Exception {
        Machine machine = new Machine();
        machine.addRule("rule-with-three-element",
                "{\n" +
                        "    \"source\": [\"Source\"],\n" +
                        "    \"detail\": {\n" +
                        "        \"eventName\": [\"Name1\",\"Name2\",\"Name3\"]\n" +
                        "    }\n" +
                        "}");
        assertEquals(25, machine.approximateObjectCount(10000));

        machine.addRule("rule-with-six-elements",
                "{\n" +
                        "    \"source\": [\"Source\"],\n" +
                        "    \"detail\": {\n" +
                        "        \"eventName\": [\"Name1\",\"Name2\",\"Name3\",\"Name4\",\"Name5\",\"Name6\"]\n" +
                        "    }\n" +
                        "}");
        assertEquals(35, machine.approximateObjectCount(10000));


        machine.addRule("rule-with-six-more-elements",
                "{\n" +
                        "    \"source\": [\"Source\"],\n" +
                        "    \"detail\": {\n" +
                        "        \"eventName\": [\"Name7\",\"Name8\",\"Name9\",\"Name10\",\"Name11\",\"Name12\"]\n" +
                        "    }\n" +
                        "}");
        assertEquals(60, machine.approximateObjectCount(10000));
    }

    @Test
    public void testApproximateSizeForRulesManySouceAndEventNameArrayElements() throws Exception {
        Machine machine = new Machine();
        machine.addRule("first-rule",
                "{\n" +
                        "    \"source\": [\"Source1\",\"Source2\"],\n" +
                        "    \"detail\": {\n" +
                        "        \"eventName\": [\"Name1\",\"Name2\",\"Name3\"]\n" +
                        "    }\n" +
                        "}");
        assertEquals(35, machine.approximateObjectCount(10000));

        machine.addRule("rule-with-two-more-source-and-eventNames",
                "{\n" +
                        "    \"source\": [\"Source1\",\"Source2\", \"Source3\",\"Source4\"],\n" +
                        "    \"detail\": {\n" +
                        "        \"eventName\": [\"Name1\",\"Name2\",\"Name3\",\"Name4\",\"Name5\"]\n" +
                        "    }\n" +
                        "}");
        assertEquals(48, machine.approximateObjectCount(10000));

        machine.addRule("rule-with-more-unique-source-and-eventNames",
                "{\n" +
                        "    \"source\": [\"Source5\",\"Source6\", \"Source7\",\"Source8\"],\n" +
                        "    \"detail\": {\n" +
                        "        \"eventName\": [\"Name6\",\"Name7\",\"Name8\",\"Name9\",\"Name10\"]\n" +
                        "    }\n" +
                        "}");
        assertEquals(87, machine.approximateObjectCount(10000));
    }

    @Test
    public void testLargeArrayRulesVsOR() throws Exception {
        Machine machine = new Machine();
        machine.addRule("rule1",
                "{\n" +
                        "  \"detail-type\" : [ \"jV4 Tij ny6H K9Z 6pALqePKFR\", \"jV4 RbfEU04 dSyRZH K9Z 6pALqePKFR\" ],\n" +
                        "  \"source\" : [ \"e9C1c0.qRk\", \"e9C1c0.3FD\", \"e9C1c0.auf\", \"e9C1c0.L6kj0T\", \"e9C1c0.sTEi\", \"e9C1c0.ATnVwRJH4\", \"e9C1c0.gOTbM9V\", \"e9C1c0.6Foy06YCE03DGH\", \"e9C1c0.UD7QBnjzEQNRODz\", \"e9C1c0.DVTtb8c\", \"e9C1c0.hmXIsf6p\", \"e9C1c0.ANK\" ],\n" +
                        "  \"detail\" : {\n" +
                        "    \"eventSource\" : [ \"qRk.BeMfKctgml0y.s1x\", \"3FD.BeMfKctgml0y.s1x\", \"auf.BeMfKctgml0y.s1x\", \"L6kj0T.BeMfKctgml0y.s1x\", \"sTEi.BeMfKctgml0y.s1x\", \"ATnVwRJH4.BeMfKctgml0y.s1x\", \"gOTbM9V.BeMfKctgml0y.s1x\", \"6Foy06YCE03DGH.BeMfKctgml0y.s1x\", \"UD7QBnjzEQNRODz.BeMfKctgml0y.s1x\", \"DVTtb8c.BeMfKctgml0y.s1x\", \"hmXIsf6p.BeMfKctgml0y.s1x\", \"ANK.BeMfKctgml0y.s1x\" ],\n" +
                        "    \"eventName\" : [ \"QK66sO0I4REUYb\", \"62HIWfGqrGTXpFotMy9xA\", \"7ucBUowmZxyA\", \"uo3piGS6CMHlcHDIzyNSr\", \"KCflDTVvp6krjt\", \"a9QrxONB6ZuU6m\", \"n8ASzCtTR8gjtkUtb\", \"bGZ94i5383n7hOOFF3XEkG3aUUY\", \"Dcw7pR9ikAMdOsAO6\", \"ccIkzb5umk6ffsWxT\", \"CrigfFIQshKoTi27S\", \"Tzi0k780pMtBV5FJV\", \"YS5tzAqzICdIajJcv\", \"ziLYvUKGSf1aqRZxU3ySvIYJ1HAQeF\", \"OgDBotcyXlPBJiGkzgEvx62KgIZ5Fc\", \"4tng21yDnIL8LJhaOptRG4d0yFm6WN\", \"aKnV3yMDVj2lq2Vfb\", \"HUhCGNVADyoDmWD9aCyzZe\", \"QHoYhQ1SDFMUNST7eHp4at\", \"QqCH8sS0zyQyPCVRitbCLHD0FEStOFXEQK\", \"YAzYOUP5qAqRiXLvKi2FGHXwOzLRTqF\", \"cyE74DukyW8Jx89B0mYfuuSwAhMV2XA\", \"5TGldWFzELapQ1gAaWbmzdozlLDy2PI\", \"9iXtpCTGB97r9QA\", \"oSZp5vZ52aD9wBcwIi\", \"OuP0M08FAxvonc5Pj2WTUEi\", \"LoQpJl4NmLXpzYou8FKT32s\", \"NUIhlIsVoqwXmXJKYYIo\", \"ZO0QKPO7T3Ic0WFaZzx5LkX\", \"ryuyOUuRIxS6fhIOtepxTgj\", \"l4x1SJQRJTAl0p3aQOc\", \"5wIJAxf3zR89u5WiKwQ\" ]\n" +
                        "  }\n" +
                        "}");
        machine.addRule("rule2",
                "{\n" +
                        "  \"detail-type\" : [ \"jV4 Tij ny6H K9Z 6pALqePKFR\", \"jV4 RbfEU04 dSyRZH K9Z 6pALqePKFR\" ],\n" +
                        "  \"source\" : [ \"0K9kV5.qRk\", \"0K9kV5.3FD\", \"0K9kV5.auf\", \"0K9kV5.L6kj0T\", \"0K9kV5.sTEi\", \"0K9kV5.ATnVwRJH4\", \"0K9kV5.gOTbM9V\", \"0K9kV5.6Foy06YCE03DGH\", \"0K9kV5.UD7QBnjzEQNRODz\", \"0K9kV5.DVTtb8c\", \"0K9kV5.hmXIsf6p\", \"0K9kV5.ANK\" ],\n" +
                        "  \"detail\" : {\n" +
                        "    \"eventSource\" : [ \"qRk.A2Ptm07Ncrg2.s1x\", \"3FD.A2Ptm07Ncrg2.s1x\", \"auf.A2Ptm07Ncrg2.s1x\", \"L6kj0T.A2Ptm07Ncrg2.s1x\", \"sTEi.A2Ptm07Ncrg2.s1x\", \"ATnVwRJH4.A2Ptm07Ncrg2.s1x\", \"gOTbM9V.A2Ptm07Ncrg2.s1x\", \"6Foy06YCE03DGH.A2Ptm07Ncrg2.s1x\", \"UD7QBnjzEQNRODz.A2Ptm07Ncrg2.s1x\", \"DVTtb8c.A2Ptm07Ncrg2.s1x\", \"hmXIsf6p.A2Ptm07Ncrg2.s1x\", \"ANK.A2Ptm07Ncrg2.s1x\" ],\n" +
                        "    \"eventName\" : [ \"eqzMRqwMielUtv\", \"bIdx6KYCn3lpviFOEFWda\", \"oM0D40U9s6En\", \"pRqp3WZkboetrmWci51p6\", \"Sc0UwrhEureEzQ\", \"b0V8ou0Lp6PrEu\", \"VIC8D82ll1FIstePk\", \"qOBBxX2kntyHDwCSGBcOd8yloVo\", \"YXPayoGQlGoFk6nkR\", \"zGMY1DfzOQvwMNmK4\", \"xLUKKGRNglfr7RzbW\", \"wbPkaR8SjIKOWOFKU\", \"U2LAfXHBUgQ9BK6OE\", \"UsXW3IKWtjUun81O5A2RvYipYYiWPf\", \"1WMPVZQFB44o4hS4qsdtv1DrHOg6le\", \"NAZAKdRXGpyYF8aVNTvsQYB4mcevPP\", \"ZKbsTPS4xbrnbP3xG\", \"w52EAqErWZ49EcaFQBN3h7\", \"OI6eIIiVmrxJOVhiq7IENU\", \"QqCH8sS0zyQyPCVRitbCLHD0FEStOFXEQK\", \"EX8qET0anoJJMvoEcGLYMZJvkzSLch4\", \"cyE74DukyW8Jx89B0mYfuuSwAhMV2XA\", \"G2hyHJGzf41Q0hDdKVZ3oeLy4ZJl32S\", \"C6kqFl3fleB3zIF\", \"4fx5kxFt2KucxvrG0s\", \"1MewNMgaPjslx4l5ISCRWhn\", \"VI7aNjEq4a1J6QYF0wQ2pV6\", \"ns4SneqAxCuWNVoepM2Q\", \"1OdzCqyk4cQtQrVOd2Zf60v\", \"0MjQEBo5tW89oNlWktVbRfH\", \"soKlU8SKloI9YCAcssn\", \"3IqglcGMMVfJAin4tBg\" ]\n" +
                        "  }\n" +
                        "}");
        machine.addRule("rule3",
                "{\n" +
                        "  \"detail-type\" : [ \"jV4 Tij ny6H K9Z 6pALqePKFR\", \"jV4 RbfEU04 dSyRZH K9Z 6pALqePKFR\" ],\n" +
                        "  \"source\" : [ \"oeoNrI.qRk\", \"oeoNrI.3FD\", \"oeoNrI.auf\", \"oeoNrI.L6kj0T\", \"oeoNrI.sTEi\", \"oeoNrI.ATnVwRJH4\", \"oeoNrI.gOTbM9V\", \"oeoNrI.6Foy06YCE03DGH\", \"oeoNrI.UD7QBnjzEQNRODz\", \"oeoNrI.DVTtb8c\", \"oeoNrI.hmXIsf6p\", \"oeoNrI.ANK\" ],\n" +
                        "  \"detail\" : {\n" +
                        "    \"eventSource\" : [ \"qRk.6SOVnnlY9Y2B.s1x\", \"3FD.6SOVnnlY9Y2B.s1x\", \"auf.6SOVnnlY9Y2B.s1x\", \"L6kj0T.6SOVnnlY9Y2B.s1x\", \"sTEi.6SOVnnlY9Y2B.s1x\", \"ATnVwRJH4.6SOVnnlY9Y2B.s1x\", \"gOTbM9V.6SOVnnlY9Y2B.s1x\", \"6Foy06YCE03DGH.6SOVnnlY9Y2B.s1x\", \"UD7QBnjzEQNRODz.6SOVnnlY9Y2B.s1x\", \"DVTtb8c.6SOVnnlY9Y2B.s1x\", \"hmXIsf6p.6SOVnnlY9Y2B.s1x\", \"ANK.6SOVnnlY9Y2B.s1x\" ],\n" +
                        "    \"eventName\" : [ \"wSjB92xeOBe2jf\", \"8owQcNCzpfsEjvv0zslQc\", \"XHSVWCs93l4m\", \"80jswkMW46QOp9ZasRC9i\", \"XoZakwvaiEbgvF\", \"A4oqVIUG1rS9G7\", \"9mU5hzwkFxHKDpo4A\", \"hI7uk7VTJB6gjcsRUoUIxuBPJaF\", \"UUFHA8cBHOvHk3lfO\", \"3cKTrqLEH5IMlsMDv\", \"TFaY7vCJG9EsxsjVd\", \"ZawowkBcOxdUsfgEs\", \"yOFNW7sxv0TNoMO6m\", \"Hp0AcGKGUlvM8lCgZqpiwOemCb2HSs\", \"SLDqS9ycYaKhJlzAdFC2bS92zrTpOO\", \"nAs966ixa5JQ9u2UlQOWh73PNMWehY\", \"tznZRlX80kDVIC8gH\", \"icLnBAt7pdp9aNDvOnqmMN\", \"NQHtpcQPybOVV0ZU4HInha\", \"QqCH8sS0zyQyPCVRitbCLHD0FEStOFXEQK\", \"6PXEaDOnRk7nmP6EhA9t2OE9g75eMmI\", \"cyE74DukyW8Jx89B0mYfuuSwAhMV2XA\", \"6n4FMCgGV1D09pLFanLGObbRBc1MXSH\", \"gk3lANJe2ZNiCdu\", \"5bL8gLCE5CE8pS0kRR\", \"hHZciQGDRCFKqf5S206HnMM\", \"HT14rl37Pa0ADgY5diV4cUa\", \"VcNAACSECOywtvlq42KR\", \"UhmN71rqtx6x0PagQr9Y4oU\", \"KX6z6AN1ApQq0HsSXbsyXgE\", \"RIo0rQN1PwKHiGnHcHP\", \"lhavqRt32TNqxjnfT2P\" ]\n" +
                        "  }\n" +
                        "}");
        assertEquals(811, machine.approximateObjectCount(150000));

        machine = new Machine();
        machine.addRule("rule1",
                "{\n" +
                        "  \"$or\" : [ {\n" +
                        "    \"source\" : [ \"e9C1c0.qRk\" ],\n" +
                        "    \"detail\" : {\n" +
                        "      \"eventName\" : [ \"QK66sO0I4REUYb\", \"62HIWfGqrGTXpFotMy9xA\" ]\n" +
                        "    }\n" +
                        "  }, {\n" +
                        "    \"source\" : [ \"e9C1c0.3FD\" ],\n" +
                        "    \"detail\" : {\n" +
                        "      \"eventName\" : [ \"7ucBUowmZxyA\", \"uo3piGS6CMHlcHDIzyNSr\" ]\n" +
                        "    }\n" +
                        "  }, {\n" +
                        "    \"source\" : [ \"e9C1c0.auf\" ],\n" +
                        "    \"detail\" : {\n" +
                        "      \"eventName\" : [ \"KCflDTVvp6krjt\", \"ixHBhtn3T99\" ]\n" +
                        "    }\n" +
                        "  }, {\n" +
                        "    \"source\" : [ \"e9C1c0.L6kj0T\" ],\n" +
                        "    \"detail\" : {\n" +
                        "      \"eventName\" : [ \"Yuq5PWrpi8h2Hi\" ]\n" +
                        "    }\n" +
                        "  }, {\n" +
                        "    \"source\" : [ \"e9C1c0.sTEi\" ],\n" +
                        "    \"detail\" : {\n" +
                        "      \"eventName\" : [ \"bGZ94i5383n7hOOFF3XEkG3aUUY\", \"Dcw7pR9ikAMdOsAO6\", \"ccIkzb5umk6ffsWxT\" ]\n" +
                        "    }\n" +
                        "  }, {\n" +
                        "    \"source\" : [ \"e9C1c0.ATnVwRJH4\" ],\n" +
                        "    \"detail\" : {\n" +
                        "      \"eventName\" : [ \"CrigfFIQshKoTi27S\", \"Tzi0k780pMtBV5FJV\", \"YS5tzAqzICdIajJcv\", \"ziLYvUKGSf1aqRZxU3ySvIYJ1HAQeF\", \"OgDBotcyXlPBJiGkzgEvx62KgIZ5Fc\", \"4tng21yDnIL8LJhaOptRG4d0yFm6WN\" ]\n" +
                        "    }\n" +
                        "  }, {\n" +
                        "    \"source\" : [ \"e9C1c0.gOTbM9V\", \"e9C1c0.6Foy06YCE03DGH\", \"e9C1c0.UD7QBnjzEQNRODz\" ],\n" +
                        "    \"detail\" : {\n" +
                        "      \"eventName\" : [ \"aKnV3yMDVj2lq2Vfb\", \"HUhCGNVADyoDmWD9aCyzZe\", \"QHoYhQ1SDFMUNST7eHp4at\", \"QqCH8sS0zyQyPCVRitbCLHD0FEStOFXEQK\", \"YAzYOUP5qAqRiXLvKi2FGHXwOzLRTqF\", \"cyE74DukyW8Jx89B0mYfuuSwAhMV2XA\", \"5TGldWFzELapQ1gAaWbmzdozlLDy2PI\" ]\n" +
                        "    }\n" +
                        "  }, {\n" +
                        "    \"source\" : [ \"DVTtb8c.BeMfKctgml0y.s1x\" ],\n" +
                        "    \"detail\" : {\n" +
                        "      \"eventName\" : [ \"9iXtpCTGB97r9QA\", \"oSZp5vZ52aD9wBcwIi\", \"OuP0M08FAxvonc5Pj2WTUEi\", \"LoQpJl4NmLXpzYou8FKT32s\" ]\n" +
                        "    }\n" +
                        "  }, {\n" +
                        "    \"source\" : [ \"e9C1c0.hmXIsf6p\" ],\n" +
                        "    \"detail\" : {\n" +
                        "      \"eventName\" : [ \"NUIhlIsVoqwXmXJKYYIo\", \"ZO0QKPO7T3Ic0WFaZzx5LkX\", \"ryuyOUuRIxS6fhIOtepxTgj\" ]\n" +
                        "    }\n" +
                        "  }, {\n" +
                        "    \"source\" : [ \"e9C1c0.ANK\" ],\n" +
                        "    \"detail\" : {\n" +
                        "      \"eventName\" : [ \"l4x1SJQRJTAl0p3aQOc\", \"5wIJAxf3zR89u5WiKwQ\" ]\n" +
                        "    }\n" +
                        "  } ]\n" +
                        "}");
        machine.addRule("rule2",
                "{\n" +
                        "  \"$or\" : [ {\n" +
                        "    \"source\" : [ \"0K9kV5.qRk\" ],\n" +
                        "    \"detail\" : {\n" +
                        "      \"eventName\" : [ \"eqzMRqwMielUtv\", \"bIdx6KYCn3lpviFOEFWda\" ]\n" +
                        "    }\n" +
                        "  }, {\n" +
                        "    \"source\" : [ \"0K9kV5.3FD\" ],\n" +
                        "    \"detail\" : {\n" +
                        "      \"eventName\" : [ \"oM0D40U9s6En\", \"pRqp3WZkboetrmWci51p6\" ]\n" +
                        "    }\n" +
                        "  }, {\n" +
                        "    \"source\" : [ \"0K9kV5.auf\" ],\n" +
                        "    \"detail\" : {\n" +
                        "      \"eventName\" : [ \"Sc0UwrhEureEzQ\", \"ixHBhtn3T99\" ]\n" +
                        "    }\n" +
                        "  }, {\n" +
                        "    \"source\" : [ \"0K9kV5.L6kj0T\" ],\n" +
                        "    \"detail\" : {\n" +
                        "      \"eventName\" : [ \"Yuq5PWrpi8h2Hi\" ]\n" +
                        "    }\n" +
                        "  }, {\n" +
                        "    \"source\" : [ \"0K9kV5.sTEi\" ],\n" +
                        "    \"detail\" : {\n" +
                        "      \"eventName\" : [ \"qOBBxX2kntyHDwCSGBcOd8yloVo\", \"YXPayoGQlGoFk6nkR\", \"zGMY1DfzOQvwMNmK4\" ]\n" +
                        "    }\n" +
                        "  }, {\n" +
                        "    \"source\" : [ \"0K9kV5.ATnVwRJH4\" ],\n" +
                        "    \"detail\" : {\n" +
                        "      \"eventName\" : [ \"xLUKKGRNglfr7RzbW\", \"wbPkaR8SjIKOWOFKU\", \"U2LAfXHBUgQ9BK6OE\", \"UsXW3IKWtjUun81O5A2RvYipYYiWPf\", \"1WMPVZQFB44o4hS4qsdtv1DrHOg6le\", \"NAZAKdRXGpyYF8aVNTvsQYB4mcevPP\" ]\n" +
                        "    }\n" +
                        "  }, {\n" +
                        "    \"source\" : [ \"0K9kV5.gOTbM9V\", \"0K9kV5.6Foy06YCE03DGH\", \"0K9kV5.UD7QBnjzEQNRODz\" ],\n" +
                        "    \"detail\" : {\n" +
                        "      \"eventName\" : [ \"ZKbsTPS4xbrnbP3xG\", \"w52EAqErWZ49EcaFQBN3h7\", \"OI6eIIiVmrxJOVhiq7IENU\", \"QqCH8sS0zyQyPCVRitbCLHD0FEStOFXEQK\", \"EX8qET0anoJJMvoEcGLYMZJvkzSLch4\", \"cyE74DukyW8Jx89B0mYfuuSwAhMV2XA\", \"G2hyHJGzf41Q0hDdKVZ3oeLy4ZJl32S\" ]\n" +
                        "    }\n" +
                        "  }, {\n" +
                        "    \"source\" : [ \"DVTtb8c.A2Ptm07Ncrg2.s1x\" ],\n" +
                        "    \"detail\" : {\n" +
                        "      \"eventName\" : [ \"C6kqFl3fleB3zIF\", \"4fx5kxFt2KucxvrG0s\", \"1MewNMgaPjslx4l5ISCRWhn\", \"VI7aNjEq4a1J6QYF0wQ2pV6\" ]\n" +
                        "    }\n" +
                        "  }, {\n" +
                        "    \"source\" : [ \"0K9kV5.hmXIsf6p\" ],\n" +
                        "    \"detail\" : {\n" +
                        "      \"eventName\" : [ \"ns4SneqAxCuWNVoepM2Q\", \"1OdzCqyk4cQtQrVOd2Zf60v\", \"0MjQEBo5tW89oNlWktVbRfH\" ]\n" +
                        "    }\n" +
                        "  }, {\n" +
                        "    \"source\" : [ \"0K9kV5.ANK\" ],\n" +
                        "    \"detail\" : {\n" +
                        "      \"eventName\" : [ \"soKlU8SKloI9YCAcssn\", \"3IqglcGMMVfJAin4tBg\" ]\n" +
                        "    }\n" +
                        "  } ]\n" +
                        "}");
        machine.addRule("rule3",
                "{\n" +
                        "  \"$or\" : [ {\n" +
                        "    \"source\" : [ \"oeoNrI.qRk\" ],\n" +
                        "    \"detail\" : {\n" +
                        "      \"eventName\" : [ \"wSjB92xeOBe2jf\", \"8owQcNCzpfsEjvv0zslQc\" ]\n" +
                        "    }\n" +
                        "  }, {\n" +
                        "    \"source\" : [ \"oeoNrI.3FD\" ],\n" +
                        "    \"detail\" : {\n" +
                        "      \"eventName\" : [ \"XHSVWCs93l4m\", \"80jswkMW46QOp9ZasRC9i\" ]\n" +
                        "    }\n" +
                        "  }, {\n" +
                        "    \"source\" : [ \"oeoNrI.auf\" ],\n" +
                        "    \"detail\" : {\n" +
                        "      \"eventName\" : [ \"XoZakwvaiEbgvF\", \"ixHBhtn3T99\" ]\n" +
                        "    }\n" +
                        "  }, {\n" +
                        "    \"source\" : [ \"oeoNrI.L6kj0T\" ],\n" +
                        "    \"detail\" : {\n" +
                        "      \"eventName\" : [ \"Yuq5PWrpi8h2Hi\" ]\n" +
                        "    }\n" +
                        "  }, {\n" +
                        "    \"source\" : [ \"oeoNrI.sTEi\" ],\n" +
                        "    \"detail\" : {\n" +
                        "      \"eventName\" : [ \"hI7uk7VTJB6gjcsRUoUIxuBPJaF\", \"UUFHA8cBHOvHk3lfO\", \"3cKTrqLEH5IMlsMDv\" ]\n" +
                        "    }\n" +
                        "  }, {\n" +
                        "    \"source\" : [ \"oeoNrI.ATnVwRJH4\" ],\n" +
                        "    \"detail\" : {\n" +
                        "      \"eventName\" : [ \"TFaY7vCJG9EsxsjVd\", \"ZawowkBcOxdUsfgEs\", \"yOFNW7sxv0TNoMO6m\", \"Hp0AcGKGUlvM8lCgZqpiwOemCb2HSs\", \"SLDqS9ycYaKhJlzAdFC2bS92zrTpOO\", \"nAs966ixa5JQ9u2UlQOWh73PNMWehY\" ]\n" +
                        "    }\n" +
                        "  }, {\n" +
                        "    \"source\" : [ \"oeoNrI.gOTbM9V\", \"oeoNrI.6Foy06YCE03DGH\", \"oeoNrI.UD7QBnjzEQNRODz\" ],\n" +
                        "    \"detail\" : {\n" +
                        "      \"eventName\" : [ \"tznZRlX80kDVIC8gH\", \"icLnBAt7pdp9aNDvOnqmMN\", \"NQHtpcQPybOVV0ZU4HInha\", \"QqCH8sS0zyQyPCVRitbCLHD0FEStOFXEQK\", \"6PXEaDOnRk7nmP6EhA9t2OE9g75eMmI\", \"cyE74DukyW8Jx89B0mYfuuSwAhMV2XA\", \"6n4FMCgGV1D09pLFanLGObbRBc1MXSH\" ]\n" +
                        "    }\n" +
                        "  }, {\n" +
                        "    \"source\" : [ \"DVTtb8c.6SOVnnlY9Y2B.s1x\" ],\n" +
                        "    \"detail\" : {\n" +
                        "      \"eventName\" : [ \"gk3lANJe2ZNiCdu\", \"5bL8gLCE5CE8pS0kRR\", \"hHZciQGDRCFKqf5S206HnMM\", \"HT14rl37Pa0ADgY5diV4cUa\" ]\n" +
                        "    }\n" +
                        "  }, {\n" +
                        "    \"source\" : [ \"oeoNrI.hmXIsf6p\" ],\n" +
                        "    \"detail\" : {\n" +
                        "      \"eventName\" : [ \"VcNAACSECOywtvlq42KR\", \"UhmN71rqtx6x0PagQr9Y4oU\", \"KX6z6AN1ApQq0HsSXbsyXgE\" ]\n" +
                        "    }\n" +
                        "  }, {\n" +
                        "    \"source\" : [ \"oeoNrI.ANK\" ],\n" +
                        "    \"detail\" : {\n" +
                        "      \"eventName\" : [ \"RIo0rQN1PwKHiGnHcHP\", \"lhavqRt32TNqxjnfT2P\" ]\n" +
                        "    }\n" +
                        "  } ]\n" +
                        "}");
        assertEquals(608, machine.approximateObjectCount(10000));
    }

    @Test
    public void testApproximateObjectCountEachKeyHasThreePatternsAddedOneAtATime() throws Exception {
        Machine machine = new Machine();
        testApproximateObjectCountEachKeyHasThreePatternsAddedOneAtATime(machine);
        assertEquals(72216, machine.approximateObjectCount(500000));
    }

    @Test
    public void testApproximateObjectCountEachKeyHasThreePatternsAddedOneAtATimeWithAdditionalNameStateReuse() throws Exception {
        Machine machine = new Machine(new Configuration.Builder().withAdditionalNameStateReuse(true).build());
        testApproximateObjectCountEachKeyHasThreePatternsAddedOneAtATime(machine);
        assertEquals(136, machine.approximateObjectCount(500000));
    }

    private void testApproximateObjectCountEachKeyHasThreePatternsAddedOneAtATime(Machine machine) throws Exception {
        machine.addRule("0", "{\"key1\": [\"a\"]}");
        machine.addRule("1", "{\"key1\": [\"b\"]}");
        machine.addRule("2", "{\"key1\": [\"c\"]}");
        machine.addRule("3", "{\"key1\": [\"a\", \"b\", \"c\"], \"key2\": [\"d\"]}");
        machine.addRule("4", "{\"key1\": [\"a\", \"b\", \"c\"], \"key2\": [\"e\"]}");
        machine.addRule("5", "{\"key1\": [\"a\", \"b\", \"c\"], \"key2\": [\"f\"]}");
        machine.addRule("6", "{\"key1\": [\"a\", \"b\", \"c\"], \"key2\": [\"d\", \"e\", \"f\"], \"key3\": [\"g\"]}");
        machine.addRule("7", "{\"key1\": [\"a\", \"b\", \"c\"], \"key2\": [\"d\", \"e\", \"f\"], \"key3\": [\"h\"]}");
        machine.addRule("8", "{\"key1\": [\"a\", \"b\", \"c\"], \"key2\": [\"d\", \"e\", \"f\"], \"key3\": [\"i\"]}");
        machine.addRule("9", "{\"key1\": [\"a\", \"b\", \"c\"], \"key2\": [\"d\", \"e\", \"f\"], \"key3\": [\"g\", \"h\", \"i\"], \"key4\": [\"j\"]}");
        machine.addRule("10", "{\"key1\": [\"a\", \"b\", \"c\"], \"key2\": [\"d\", \"e\", \"f\"], \"key3\": [\"g\", \"h\", \"i\"], \"key4\": [\"k\"]}");
        machine.addRule("11", "{\"key1\": [\"a\", \"b\", \"c\"], \"key2\": [\"d\", \"e\", \"f\"], \"key3\": [\"g\", \"h\", \"i\"], \"key4\": [\"l\"]}");
        machine.addRule("12", "{\"key1\": [\"a\", \"b\", \"c\"], \"key2\": [\"d\", \"e\", \"f\"], \"key3\": [\"g\", \"h\", \"i\"], \"key4\": [\"j\", \"k\", \"l\"], \"key5\": [\"m\"]}");
        machine.addRule("13", "{\"key1\": [\"a\", \"b\", \"c\"], \"key2\": [\"d\", \"e\", \"f\"], \"key3\": [\"g\", \"h\", \"i\"], \"key4\": [\"j\", \"k\", \"l\"], \"key5\": [\"n\"]}");
        machine.addRule("14", "{\"key1\": [\"a\", \"b\", \"c\"], \"key2\": [\"d\", \"e\", \"f\"], \"key3\": [\"g\", \"h\", \"i\"], \"key4\": [\"j\", \"k\", \"l\"], \"key5\": [\"o\"]}");
        machine.addRule("15", "{\"key1\": [\"a\", \"b\", \"c\"], \"key2\": [\"d\", \"e\", \"f\"], \"key3\": [\"g\", \"h\", \"i\"], \"key4\": [\"j\", \"k\", \"l\"], \"key5\": [\"m\", \"n\", \"o\"], \"key6\": [\"p\"]}");
        machine.addRule("16", "{\"key1\": [\"a\", \"b\", \"c\"], \"key2\": [\"d\", \"e\", \"f\"], \"key3\": [\"g\", \"h\", \"i\"], \"key4\": [\"j\", \"k\", \"l\"], \"key5\": [\"m\", \"n\", \"o\"], \"key6\": [\"q\"]}");
        machine.addRule("17", "{\"key1\": [\"a\", \"b\", \"c\"], \"key2\": [\"d\", \"e\", \"f\"], \"key3\": [\"g\", \"h\", \"i\"], \"key4\": [\"j\", \"k\", \"l\"], \"key5\": [\"m\", \"n\", \"o\"], \"key6\": [\"r\"]}");
        machine.addRule("18", "{\"key1\": [\"a\", \"b\", \"c\"], \"key2\": [\"d\", \"e\", \"f\"], \"key3\": [\"g\", \"h\", \"i\"], \"key4\": [\"j\", \"k\", \"l\"], \"key5\": [\"m\", \"n\", \"o\"], \"key6\": [\"p\", \"q\", \"r\"], \"key7\": [\"s\"]}");
        machine.addRule("19", "{\"key1\": [\"a\", \"b\", \"c\"], \"key2\": [\"d\", \"e\", \"f\"], \"key3\": [\"g\", \"h\", \"i\"], \"key4\": [\"j\", \"k\", \"l\"], \"key5\": [\"m\", \"n\", \"o\"], \"key6\": [\"p\", \"q\", \"r\"], \"key7\": [\"t\"]}");
        machine.addRule("20", "{\"key1\": [\"a\", \"b\", \"c\"], \"key2\": [\"d\", \"e\", \"f\"], \"key3\": [\"g\", \"h\", \"i\"], \"key4\": [\"j\", \"k\", \"l\"], \"key5\": [\"m\", \"n\", \"o\"], \"key6\": [\"p\", \"q\", \"r\"], \"key7\": [\"u\"]}");
        machine.addRule("21", "{\"key1\": [\"a\", \"b\", \"c\"], \"key2\": [\"d\", \"e\", \"f\"], \"key3\": [\"g\", \"h\", \"i\"], \"key4\": [\"j\", \"k\", \"l\"], \"key5\": [\"m\", \"n\", \"o\"], \"key6\": [\"p\", \"q\", \"r\"], \"key7\": [\"s\", \"t\", \"u\"], \"key8\": [\"v\"]}");
        machine.addRule("22", "{\"key1\": [\"a\", \"b\", \"c\"], \"key2\": [\"d\", \"e\", \"f\"], \"key3\": [\"g\", \"h\", \"i\"], \"key4\": [\"j\", \"k\", \"l\"], \"key5\": [\"m\", \"n\", \"o\"], \"key6\": [\"p\", \"q\", \"r\"], \"key7\": [\"s\", \"t\", \"u\"], \"key8\": [\"w\"]}");
        machine.addRule("23", "{\"key1\": [\"a\", \"b\", \"c\"], \"key2\": [\"d\", \"e\", \"f\"], \"key3\": [\"g\", \"h\", \"i\"], \"key4\": [\"j\", \"k\", \"l\"], \"key5\": [\"m\", \"n\", \"o\"], \"key6\": [\"p\", \"q\", \"r\"], \"key7\": [\"s\", \"t\", \"u\"], \"key8\": [\"x\"]}");
        machine.addRule("24", "{\"key1\": [\"a\", \"b\", \"c\"], \"key2\": [\"d\", \"e\", \"f\"], \"key3\": [\"g\", \"h\", \"i\"], \"key4\": [\"j\", \"k\", \"l\"], \"key5\": [\"m\", \"n\", \"o\"], \"key6\": [\"p\", \"q\", \"r\"], \"key7\": [\"s\", \"t\", \"u\"], \"key8\": [\"v\", \"w\", \"x\"], \"key9\": [\"y\"]}");
    }
}
