package software.amazon.event.ruler;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
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
import org.junit.Test;
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
        rule.setKeys("a", "b","c");
        rule.setExactMatchValues("11", "21","31");
        //test whether duplicated rule could be added
        machine.addPatternRule(rule.name, rule.fields);
        machine.addPatternRule(rule.name, rule.fields);
        machine.addPatternRule(rule.name, rule.fields);

        rule1 = new Rule("R1");
        rule1.setKeys("a", "b","zoo");
        rule1.setExactMatchValues("11", "21","keeper");
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
        //System.out.println("matched rule:" + actual);

        rule1 = new Rule("R1");
        rule1.setKeys("a", "b","gamma");
        rule1.setExactMatchValues("11", "21","41");
        machine.addPatternRule(rule1.name, rule1.fields);
        List<String> actual1 = machine.rulesForEvent(e.mTokens);
        assertEquals(1, actual1.size());
        //System.out.println("matched rule:" + actual1);


        // delete R1 subset with rule.fields
        machine.deletePatternRule(rule.name, rule.fields);

        List<String> actual2 = machine.rulesForEvent(e.mTokens);
        assertEquals(1, actual2.size());
        //System.out.println("matched rule:" + actual2);

        // delete R1 subset with rule1 fields, after this step,
        // the machine will become empty as if no rule was added before.
        machine.deletePatternRule(rule1.name, rule1.fields);

        List<String> actual3 = machine.rulesForEvent(e.mTokens);
        assertEquals(0, actual3.size());
        //System.out.println("matched rule:" + actual3);

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
        List <Rule> rules = new ArrayList<>();

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
}
