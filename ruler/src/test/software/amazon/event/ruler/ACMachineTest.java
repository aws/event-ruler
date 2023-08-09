package software.amazon.event.ruler;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Unit testing a state machine is hard.  Tried hand-computing a few machines
 *  but kept getting them wrong, the software was right.  So this is really
 *  more of a smoke/integration test.  But the coverage is quite good.
 */
public class ACMachineTest {

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
                if (m.rulesForJSONEvent(event).size() == 1) {
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

        List<String> matches = machine.rulesForJSONEvent("{\"sourceIPAddress\": \"220.160.153.170\"}");
        assertEquals(1, matches.size());
        assertTrue(matches.contains("rule1"));
        matches = machine.rulesForJSONEvent("{\"sourceIPAddress\": \"220.160.153.171\"}");
        assertEquals(1, matches.size());
        assertTrue(matches.contains("rule1"));
        matches = machine.rulesForJSONEvent("{\"sourceIPAddress\": \"220.160.153.169\"}");
        assertTrue(matches.isEmpty());
        matches = machine.rulesForJSONEvent("{\"sourceIPAddress\": \"220.160.153.172\"}");
        assertTrue(matches.isEmpty());

        matches = machine.rulesForJSONEvent("{\"sourceIPAddress\": \"220.160.154.0\"}");
        assertEquals(1, matches.size());
        assertTrue(matches.contains("rule2"));
        matches = machine.rulesForJSONEvent("{\"sourceIPAddress\": \"220.160.154.255\"}");
        assertEquals(1, matches.size());
        assertTrue(matches.contains("rule2"));
        matches = machine.rulesForJSONEvent("{\"sourceIPAddress\": \"220.160.153.255\"}");
        assertTrue(matches.isEmpty());
        matches = machine.rulesForJSONEvent("{\"sourceIPAddress\": \"220.160.155.0\"}");
        assertTrue(matches.isEmpty());

        matches = machine.rulesForJSONEvent("{\"sourceIPAddress\": \"220.160.59.224\"}");
        assertEquals(1, matches.size());
        assertTrue(matches.contains("rule3"));
        matches = machine.rulesForJSONEvent("{\"sourceIPAddress\": \"220.160.59.225\"}");
        assertEquals(1, matches.size());
        assertTrue(matches.contains("rule3"));
        matches = machine.rulesForJSONEvent("{\"sourceIPAddress\": \"220.160.59.223\"}");
        assertTrue(matches.isEmpty());
        matches = machine.rulesForJSONEvent("{\"sourceIPAddress\": \"220.160.59.226\"}");
        assertTrue(matches.isEmpty());
    }

    private static final String JSON_FROM_README = "{\n" +
            "  \"version\": \"0\",\n" +
            "  \"id\": \"ddddd4-aaaa-7777-4444-345dd43cc333\",\n" +
            "  \"detail-type\": \"EC2 Instance State-change Notification\",\n" +
            "  \"source\": \"aws.ec2\",\n" +
            "  \"account\": \"012345679012\",\n" +
            "  \"time\": \"2017-10-02T16:24:49Z\",\n" +
            "  \"region\": \"us-east-1\",\n" +
            "  \"resources\": [\n" +
            "    \"arn:aws:ec2:us-east-1:012345679012:instance/i-000000aaaaaa00000\"\n" +
            "  ],\n" +
            "  \"detail\": {\n" +
            "    \"c.count\": 5,\n" +
            "    \"d.count\": 3,\n" +
            "    \"x.limit\": 301.8,\n" +
            "    \"instance-id\": \"i-000000aaaaaa00000\",\n" +
            "    \"state\": \"running\"\n" +
            "  }\n" +
            "}\n";

    @Test
    public void rulesFromREADMETest() throws Exception {
        String[] rules = {
                "{\n" +
                        "  \"detail-type\": [ \"EC2 Instance State-change Notification\" ],\n" +
                        "  \"resources\": [ \"arn:aws:ec2:us-east-1:012345679012:instance/i-000000aaaaaa00000\" ],\n" +
                        "  \"detail\": {\n" +
                        "    \"state\": [ \"initializing\", \"running\" ]\n" +
                        "  }\n" +
                        "}",
                "{\n" +
                        "  \"time\": [ { \"prefix\": \"2017-10-02\" } ],\n" +
                        "  \"detail\": {\n" +
                        "    \"state\": [ { \"anything-but\": \"initializing\" } ]\n" +
                        "  }\n" +
                        "}",
                "{\n" +
                        "  \"time\": [ { \"prefix\": \"2017-10-02\" } ],\n" +
                        "  \"detail\": {\n" +
                        "    \"state\": [ { \"suffix\": \"ing\" } ]\n" +
                        "  }\n" +
                        "}",
                "{\n" +
                        "  \"detail\": {\n" +
                        "    \"c.count\": [ { \"numeric\": [ \">\", 0, \"<=\", 5 ] } ],\n" +
                        "    \"d.count\": [ { \"numeric\": [ \"<\", 10 ] } ],\n" +
                        "    \"x.limit\": [ { \"numeric\": [ \"=\", 3.018e2 ] } ]\n" +
                        "  }\n" +
                        "}  \n",
                "{\n" +
                        "  \"detail\": {\n" +
                        "    \"state\": [ { \"anything-but\": { \"prefix\": \"init\" } } ]\n" +
                        "  }\n" +
                        "}",
                "{\n" +
                        "  \"detail\": {\n" +
                        "    \"instance-id\": [ { \"anything-but\": { \"suffix\": \"1234\" } } ]\n" +
                        "  }\n" +
                        "}",
                "{\n" +
                        "  \"detail\": {\n" +
                        "    \"state\": [ { \"anything-but\": {\"equals-ignore-case\": [\"Stopped\", \"OverLoaded\"] } } ]\n" +
                        "  }\n" +
                        "}"
        };

        for (String rule : rules) {
            Machine m = new Machine();
            m.addRule("r0", rule);
            assertEquals(1, m.rulesForJSONEvent(JSON_FROM_README).size());
        }
    }

    @Test
    public void songsACTest() throws Exception {
        String songs = "{\n" +
                "  \"Year\": 1966,\n" +
                "  \"Genres\": [" +
                "    { \"Pop\": true }," +
                "    { \"Classical\": false }" +
                "  ]," +
                "  \"Songs\": [\n" +
                "    {\n" +
                "      \"Name\": \"Paperback Writer\",\n" +
                "      \"Writers\": [\n" +
                "        { \"First\": \"John\", \"Last\": \"Lennon\" },\n" +
                "        { \"First\": \"Paul\", \"Last\": \"McCartney\" }\n" +
                "      ]\n" +
                "    },\n" +
                "    {\n" +
                "      \"Name\": \"Paint It Black\",\n" +
                "      \"Writers\": [\n" +
                "        { \"First\": \"Mick\", \"Last\": \"Jagger\" },\n" +
                "        { \"First\": \"Keith\", \"Last\": \"Richards\" }\n" +
                "      ]\n" +
                "    }\n" +
                "  ]\n" +
                "}\n";

        String[] rulesThatShouldMatch = {
                "{\"Year\": [ 1966 ], \"Songs\": { \"Name\":[ \"Paperback Writer\" ], \"Writers\": { \"First\": [ \"Paul\" ], \"Last\":[\"McCartney\"]}}}",
                "{\"Genres\": { \"Pop\": [ true ] }, \"Songs\": { \"Name\":[ \"Paint It Black\" ] }}",
                "{\"Year\": [ 1966 ], \"Songs\": { \"Name\":[ \"Paint It Black\" ] }}",
                "{\"Songs\": { \"Name\":[ \"Paperback Writer\" ], \"Writers\": { \"First\": [ \"Paul\" ], \"Last\":[\"McCartney\"]}}}"
        };
        String[] rulesThatShouldFail = {
                "{\"Year\": [ 1966 ], \"Songs\": { \"Name\":[ \"Paperback Writer\" ], \"Writers\": { \"First\": [ \"Mick\" ] }}}",
                "{\"Songs\": { \"Writers\": { \"First\": [ \"Mick\" ], \"Last\": [ \"McCartney\" ] }}}",
                "{\"Genres\": { \"Pop\": [ true ] }, \"Songs\": { \"Writers\": { \"First\": [ \"Mick\" ], \"Last\": [ \"McCartney\" ] }}}",
        };
        Machine m = new Machine();
        for (int i = 0; i < rulesThatShouldMatch.length; i++) {
            String rule = rulesThatShouldMatch[i];
            String name = String.format("r%d", i);
            m.addRule(name, rule);
        }
        for (int i = 0; i < rulesThatShouldFail.length; i++) {
            String rule = rulesThatShouldFail[i];
            String name = String.format("f%d", i);
            m.addRule(name, rule);
        }
        List<String> result = m.rulesForJSONEvent(songs);
        assertEquals(rulesThatShouldMatch.length, result.size());
        for (int i = 0; i < rulesThatShouldMatch.length; i++) {
            String name = String.format("r%d", i);
            assertTrue(result.contains(name));
        }
        for (int i = 0; i < rulesThatShouldMatch.length; i++) {
            String name = String.format("f%d", i);
            assertFalse(result.contains(name));
        }
    }

    @Test
    public void intensitiesACTest() throws Exception {
        String intensities = "{\n" +
                "  \"lines\": [{ \n" +
                "    \"intensities\": [\n" +
                "      [\n" +
                "        0.5\n" +
                "      ]\n" +
                "    ],\n" +
                "    \"points\": [\n" +
                "      [\n" +
                "        [\n" +
                "          {\"pp\" : \"index0\"},\n" +
                "          483.3474497412421,\n" +
                "          287.48116291799363\n" +
                "        ],\n" +
                "        [\n" +
                "          {\"pp\" : \"index1\"},\n" +
                "          489.9999497412421,\n" +
                "          299.99996291799363\n" +
                "        ]\n" +
                "        \n" +
                "      ]\n" +
                "    ]\n" +
                "  }]\n" +
                "}";

        String rule =   "{\n" +
                "  \"lines\": \n" +
                "    { \n" +
                "      \"points\": [287.48116291799363],\n" +
                "      \"points\": { \n" +
                "         \"pp\" : [\"index0\"]\n" +
                "      }\n" +
                "    }\n" +
                "}";


        Machine m = new Machine();
        m.addRule("r", rule);
        List<String> result = m.rulesForJSONEvent(intensities);
        assertEquals(1, result.size());

    }

    @Test
    public void testSimplestPossibleMachine() throws Exception {
        String rule1 = "{ \"a\" : [ 1 ] }";
        String rule2 = "{ \"b\" : [ 2 ] }";
        Machine machine = new Machine();
        machine.addRule("r1", rule1);
        machine.addRule("r2", rule2);
        String event1 = "{ \"a\": 1 }";
        String event2 = "{ \"b\": 2 }";
        String event3 = "{ \"x\": true }";
        List<String> val;
        val = machine.rulesForJSONEvent(event1);
        assertEquals(1, val.size());
        assertEquals("r1", val.get(0));

        val = machine.rulesForJSONEvent(event2);
        assertEquals(1, val.size());
        assertEquals("r2", val.get(0));

        val = machine.rulesForJSONEvent(event3);
        assertEquals(0, val.size());
    }

    @Test
    public void testPrefixMatching() throws Exception {
        String rule1 = "{ \"a\" : [ { \"prefix\": \"zoo\" } ] }";
        String rule2 = "{ \"b\" : [ { \"prefix\": \"child\" } ] }";
        Machine machine = new Machine();
        machine.addRule("r1", rule1);
        machine.addRule("r2", rule2);
        String[] events = {
                "{\"a\": \"zookeeper\"}",
                "{\"a\": \"zoo\"}",
                "{\"b\": \"childlike\"}",
                "{\"b\": \"childish\"}",
                "{\"b\": \"childhood\"}"
        };
        for (String event : events) {
            List<String> rules = machine.rulesForJSONEvent(event);
            assertEquals(1, rules.size());
            if (event.contains("\"a\"")) {
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
        String e2 = "{ \"a\": \"albert\"}";
        List<String> rules = machine.rulesForJSONEvent(e2);
        assertEquals(2, rules.size());
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
        List<String> matchRules = m.rulesForJSONEvent(eventStr);
        assertEquals(1, matchRules.size());
    }

    @Test
    public void testCityLotsProblemLines() throws Exception {

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
        List<String> r = machine.rulesForJSONEvent(eJSON);
        assertEquals(1, r.size());
        assertEquals("R1", r.get(0));
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
        e = new TestEvent("0", "\"\"", "alpha", "1", "arc", "\"xx\"", "beta", "2", "gamma", "3", "zoo", "\"keeper\"");
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
        e = new TestEvent("0", "\"\"", "alpha", "1", "arc", "\"xx\"", "bar", "\"v22\"", "beta", "2", "foo", "\"v23\"", "gamma", "3",
                "zoo", "\"keeper\"");
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
        e = new TestEvent("zork", "\"max\"", "x1", "11", "x6", "15", "x7", "50");
        e.setExpected("R6");
        events.add(e);
        e = new TestEvent("x1", "11", "x6", "25", "foo", "\"bar\"", "x7", "50");
        e.setExpected("R6");
        events.add(e);

        // extras between all the fields, should still match
        e = new TestEvent("0", "\"\"", "alpha", "1", "arc", "\"xx\"", "beta", "2", "gamma", "3", "zoo", "\"keeper\"");
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
        e = new TestEvent("0", "\"\"", "alpha", "1", "arc", "\"xx\"", "bar", "\"v22\"", "beta", "2", "foo", "\"v23\"", "gamma", "3",
                "zoo", "\"keeper\"");
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
        e = new TestEvent("zork", "\"max\"", "x1", "11", "x6", "15", "x7", "50");
        e.setExpected("R6");
        events.add(e);
        e = new TestEvent("x1", "11", "x6", "25", "foo", "\"bar\"", "x7", "50");
        e.setExpected("R6");
        events.add(e);

        return events;
    }


    @Test
    public void testBuild() throws Exception {
        Machine machine = new Machine();

        setRules(machine);
        assertNotNull(machine);

        List<TestEvent> events = createEvents();

        for (TestEvent event : events) {
            singleEventTest(machine, event);
        }
    }

    private void singleEventTest(Machine machine, TestEvent event) throws Exception {
        List<String> actual = machine.rulesForJSONEvent(event.toString());
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

        private final List<String> mExpectedRules = new ArrayList<>();
        private final String jsonString;

        TestEvent(String... tokens) {
            StringBuilder sb = new StringBuilder("{");
            for (int i = 0; i < tokens.length; i += 2) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append('"').append(tokens[i]).append("\":").append(tokens[i + 1]);
            }
            sb.append('}');
            jsonString = sb.toString();
        }

        void setExpected(String... rules) {
            Collections.addAll(mExpectedRules, rules);
        }

        @Override
        public String toString() {
            return jsonString;
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
    public void addRuleOriginalAPI() throws Exception {
        Machine machine = new Machine();
        String rule1 = "{ \"f1\": [ \"x\", \"y\"], \"f2\": [1,2]}";
        String rule2 = "{ \"f1\": [\"foo\", \"bar\"] }";
        machine.addRule("r1", rule1);
        machine.addRule("r2", rule2);
        String event1 = "{ \"f1\": \"x\", \"f2\": 1 }";
        String event2 = "{ \"f1\": \"foo\" }";
        List<String> l = machine.rulesForJSONEvent(event1);
        assertEquals(1, l.size());
        assertEquals("r1", l.get(0));
        l = machine.rulesForJSONEvent(event2);
        assertEquals(1, l.size());
        assertEquals("r2", l.get(0));
    }

    @Test
    public void twoRulesSamePattern() throws Exception {
        Machine machine = new Machine();
        String json = "{\"detail\":{\"testId\":[\"foo\"]}}";
        machine.addRule("rule1", json);
        machine.addRule("rule2", new StringReader(json));
        machine.addRule("rule3", json.getBytes(StandardCharsets.UTF_8));
        machine.addRule("rule4", new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        String e = "{ \"detail\": { \"testId\": \"foo\"}}";
        List<String> strings = machine.rulesForJSONEvent(e);

        assertEquals(4, strings.size());
    }

    @Test
    public void twoRulesSamePattern2() throws Exception {
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

        TestEvent e = new TestEvent("0", "\"\"", "a", "1", "b", "2", "c", "3", "gamma", "3", "zoo", "\"keeper\"");
        e.setExpected("R1", "R2");

        List<String> actual = machine.rulesForJSONEvent(e.toString());
        assertEquals(2, actual.size());
    }

    @Test
    public void dynamicAddRules() throws Exception {
        Machine machine = new Machine();

        TestEvent e = new TestEvent("0", "\"\"", "a", "11", "b", "21", "c", "31", "gamma", "41", "zoo", "\"keeper\"");

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
        rule1.setExactMatchValues("11", "21","\"keeper\"");
        machine.addPatternRule(rule1.name, rule1.fields);

        List<String> actual1 = machine.rulesForJSONEvent(e.toString());
        assertEquals(1, actual1.size());

        rule2 = new Rule("R2-1");
        rule2.setKeys("a", "c", "b");
        rule2.setExactMatchValues("11", "31", "21");
        machine.addPatternRule(rule2.name, rule2.fields);

        List<String> actual2 = machine.rulesForJSONEvent(e.toString());
        assertEquals(2, actual2.size());

        rule3 = new Rule("R2-2");
        rule3.setKeys("gamma", "zoo");
        rule3.setExactMatchValues("41", "\"keeper\"");
        machine.addPatternRule(rule3.name, rule3.fields);

        List<String> actual3 = machine.rulesForJSONEvent(e.toString());
        assertEquals(3, actual3.size());
    }

    /**
     *  Incrementally build Rule R1 by different namevalues, observing new state and rules created
     *  Decrementally delete rule R1 by pointed namevalues, observing state and rules
     *  which is not used have been removed.
     */
    @Test
    public void dynamicDeleteRules() throws Exception {
        Machine machine = new Machine();

        TestEvent e = new TestEvent("0", "\"\"", "a", "11", "b", "21", "c", "31", "gamma", "41", "zoo", "\"keeper\"");

        Rule rule;
        Rule rule1;
        rule = new Rule("R1");
        rule.setKeys("a", "b", "c");
        rule.setExactMatchValues("11", "21", "31");
        machine.addPatternRule(rule.name, rule.fields);
        List<String> actual = machine.rulesForJSONEvent(e.toString());
        assertEquals(1, actual.size());

        rule1 = new Rule("R1");
        rule1.setKeys("a", "b","gamma");
        rule1.setExactMatchValues("11", "21","41");
        machine.addPatternRule(rule1.name, rule1.fields);
        List<String> actual1 = machine.rulesForJSONEvent(e.toString());
        assertEquals(1, actual1.size());


        // delete R1 subset with rule.fields
        machine.deletePatternRule(rule.name, rule.fields);

        List<String> actual2 = machine.rulesForJSONEvent(e.toString());
        assertEquals(1, actual2.size());

        // delete R1 subset with rule1 fields, after this step,
        // the machine will become empty as if no rule was added before.
        machine.deletePatternRule(rule1.name, rule1.fields);

        List<String> actual3 = machine.rulesForJSONEvent(e.toString());
        assertEquals(0, actual3.size());
    }

    /**
     * Setup thread pools with 310 threads inside, among them, 300 threads are calling rulesForJSONEvent(),
     * 10 threads are adding rule. the test is designed to add rules and match rules operation handled in parallel,
     * observe rulesForJSONEvent whether could work well while there is new rule keeping added dynamically in parallel.
     * Keep same event call rulesForJSONEvent() in parallel, expect to see more and more rules will be matched
     * aligned with more and more new rules added.
     * In this test:
     * We created 100 rules with 100 key/val pair (each rule use one key/val), we created one "global" event by using
     * those 100 key/val pairs. this event should match out all those 100 rules since they are added.
     * So if we keep using this event query machine while adding 100 rules in parallel, we should see the output of
     * number of matched rules by rulesForJSONEvent keep increasing from 0 to 100, then stabilize returning 100 for
     * all of following rulesForJSONEvent().
     */
    @Test
    public void testMultipleThreadReadAddRule() throws Exception {
        Machine machine = new Machine();
        List<String> event = new ArrayList<>();
        List <Rule> rules = new ArrayList<>();

        for (int i = 0; i< 100; i++) {
            event.add(String.format("key-%03d",i));
            event.add(String.format("\"val-%03d\"",i));
        }
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < event.size(); i += 2) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(event.get(i)).append("\":").append(event.get(i+1));
        }
        sb.append('}');
        String eString = sb.toString();

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
                try {
                    while (i < 100) {
                        List<String> actual = machine.rulesForJSONEvent(eString);
                        // the number of matched rules will keep growing from 0 till to 100
                        n = actual.size();
                        if (n == 100) {
                            i++;
                        }
                    }
                } catch (Exception e) {
                    System.err.println("OUCH: " + e.getMessage());
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

        List<String> actual = machine.rulesForJSONEvent(eString);
        assertEquals(100, actual.size());
    }

    @Test
    public void testMultipleThreadReadDeleteRule() throws Exception {
        Machine machine = new Machine();
        List<String> event = new ArrayList<>();
        List <Rule> rules = new ArrayList<>();

        for (int i = 0; i< 100; i++) {
            event.add(String.format("key-%03d",i));
            event.add(String.format("\"val-%03d\"",i));
        }
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < event.size(); i += 2) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(event.get(i)).append("\":").append(event.get(i+1));
        }
        sb.append('}');
        String eString = sb.toString();

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
                    try {
                        List<String> actual = machine.rulesForJSONEvent(eString);
                        // the number of matched rules will keep growing from 0 till to 100
                        n = actual.size();
                        if (n == 0) {
                            i++;
                        }
                    } catch (Exception e) {
                        fail("OUCH bad event: " + eString);
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

        List<String> actual = machine.rulesForJSONEvent(eString);
        assertEquals(0, actual.size());
    }

    @Test
    public void testFunkyDelete() throws Exception {
        String rule = "{ \"foo\": { \"bar\": [ 23 ] }}";
        Machine cut = new Machine();

        // add the rule, ensure it matches
        cut.addRule("r1", rule);
        String event = "{ \"foo\": {\"bar\": 23 }}";
        assertEquals(1, cut.rulesForJSONEvent(event).size());

        // delete the rule, no match
        cut.deleteRule("r1", rule);
        assertEquals(0, cut.rulesForJSONEvent(event).size());
        assertTrue(cut.isEmpty());

        // add it back, it matches
        cut.addRule("r1", rule);
        assertEquals(1, cut.rulesForJSONEvent(event).size());

        // delete it but with the wrong name.  Should be a no-op
        cut.deleteRule("r2", rule);
        assertEquals(1, cut.rulesForJSONEvent(event).size());
        assertFalse(cut.isEmpty());

        // delete it but with the correct name.  Should be no match
        cut.deleteRule("r1", rule);
        assertEquals(0, cut.rulesForJSONEvent(event).size());
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
        String event = "{ \"foo\": { \"bar\":23 }}";
        String event1 = "{ \"foo\": { \"bar\": 45 }}";
        assertEquals(1, cut.rulesForJSONEvent(event).size());
        assertEquals(1, cut.rulesForJSONEvent(event1).size());

        // delete partial rule 23, partial match
        cut.deleteRule("r1", rule1);
        assertEquals(0, cut.rulesForJSONEvent(event).size());
        assertEquals(1, cut.rulesForJSONEvent(event1).size());

        // delete partial rule 45, no match
        cut.deleteRule("r1", rule2);
        assertEquals(0, cut.rulesForJSONEvent(event).size());
        assertEquals(0, cut.rulesForJSONEvent(event1).size());
        assertTrue(cut.isEmpty());

        // add it back, it matches
        cut.addRule("r1", rule);
        assertEquals(1, cut.rulesForJSONEvent(event).size());
        assertEquals(1, cut.rulesForJSONEvent(event1).size());

        // delete rule3, partially delete 45, 44 and 46 are not existing will be ignored,
        // so should only match 23
        cut.deleteRule("r1", rule3);
        assertEquals(1, cut.rulesForJSONEvent(event).size());
        assertEquals(0, cut.rulesForJSONEvent(event1).size());

        // delete rule then should match nothing ...
        cut.deleteRule("r1", rule);
        assertEquals(0, cut.rulesForJSONEvent(event).size());
        assertEquals(0, cut.rulesForJSONEvent(event1).size());
        assertTrue(cut.isEmpty());

        // add it back, it matches
        cut.addRule("r1", rule);
        assertEquals(1, cut.rulesForJSONEvent(event).size());
        assertEquals(1, cut.rulesForJSONEvent(event1).size());

        // delete rule4, as rule4 has nothing related with other rule, machine should do nothing.
        cut.deleteRule("r1", rule4);
        assertEquals(1, cut.rulesForJSONEvent(event).size());
        assertEquals(1, cut.rulesForJSONEvent(event1).size());

        // delete all
        cut.deleteRule("r1", rule5);
        assertEquals(0, cut.rulesForJSONEvent(event).size());
        assertEquals(0, cut.rulesForJSONEvent(event1).size());
        assertTrue(cut.isEmpty());
    }


    @Test
    public void testRangeDeletion() throws Exception {

        String rule = "{\"x\": [{\"numeric\": [\">=\", 0, \"<\", 1000000000]}]}";

        Machine cut = new Machine();

        // add the rule, ensure it matches
        cut.addRule("r1", rule);
        String event = "{\"x\": 111111111.111111111}";
        String event1 = "{\"x\": 1000000000}";
        assertEquals(1, cut.rulesForJSONEvent(event).size());
        assertEquals(0, cut.rulesForJSONEvent(event1).size());

        // delete partial rule 23, partial match
        cut.deleteRule("r1", rule);
        assertEquals(0, cut.rulesForJSONEvent(event).size());
        assertEquals(0, cut.rulesForJSONEvent(event1).size());
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
        event = "{\"m\": \"abc\", \"n\": \"efg\", \"x\": 23  }";
        event1 =  "{\"m\": \"abc\", \"n\": \"efg\", \"x\": 110  }";
        assertEquals(1, cut.rulesForJSONEvent(event).size());
        assertEquals(1, cut.rulesForJSONEvent(event1).size());
        cut.deleteRule("r2", rule);
        assertEquals(0, cut.rulesForJSONEvent(event).size());
        assertEquals(0, cut.rulesForJSONEvent(event1).size());
        assertTrue(cut.isEmpty());
    }

    @Test
    public void deleteRule() throws Exception {
        String rule = "{ \"foo\": { \"bar\": [ \"ab\", \"cd\" ] }}";
        Machine cut = new Machine();

        // add the rule, ensure it matches
        cut.addRule("r1", rule);
        String event = "{ \"foo\": {\"bar\": \"ab\" }}";
        String event1 = "{ \"foo\": {\"bar\": \"cd\" }}";
        String event2 = "{ \"foo\": {\"bar\": [\"ab\", \"cd\" ]}}";
        assertEquals(1, cut.rulesForJSONEvent(event).size());
        assertEquals(1, cut.rulesForJSONEvent(event1).size());
        assertEquals(1, cut.rulesForJSONEvent(event2).size());

        Map<String, List<String>> namevals = new HashMap<>();
        // delete partial rule 23, partial match
        namevals.put("foo.bar", Collections.singletonList("\"ab\""));
        cut.deleteRule("r1", namevals);
        assertEquals(0, cut.rulesForJSONEvent(event).size());
        assertEquals(1, cut.rulesForJSONEvent(event1).size());
        assertEquals(1, cut.rulesForJSONEvent(event2).size());

        namevals.put("foo.bar", Collections.singletonList("\"cd\""));
        cut.deleteRule("r1", namevals);
        assertEquals(0, cut.rulesForJSONEvent(event).size());
        assertEquals(0, cut.rulesForJSONEvent(event1).size());
        assertEquals(0, cut.rulesForJSONEvent(event2).size());
    }

    @Test
    public void WHEN_RuleForJsonEventIsPresented_THEN_ItIsMatched() throws Exception {
        final Machine rulerMachine = new Machine();
        rulerMachine.addRule( "test-rule", "{ \"type\": [\"Notification\"] }" );

        String event = "        { \n" +
                "          \"signature\": \"JYFVGfee...\", \n" +
                "          \"signatureVersion\": 1, \n" +
                "          \"signingCertUrl\": \"https://sns.us-east-1.amazonaws.com/SimpleNotificationService-1234.pem\",\n" +
                "          \"subscribeUrl\": \"arn:aws:sns:us-east-1:108960525716:cw-to-sns-to-slack\",\n" +
                "          \"topicArn\": \"arn:aws:sns:us-east-1:108960525716:cw-to-sns-to-slack\",\n" +
                "          \"type\":\"Notification\"\n" +
                "        }\n";

        List<String> foundRules = rulerMachine.rulesForJSONEvent(event);
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

        String event = "{ \"foo\": { \"bar\": \"ab\" }}";//{ "foo.bar", "\"ab\"" };
        String event1 = "{ \"foo\": { \"bar\": \"cd\" }}";//{ "foo.bar", "\"cd\"" };
        String event2 = "{ \"foo\": { \"bar\": [ \"ab\", \"cd\" ]}}";//{ "foo.bar", "\"ab\"", "foo.bar", "\"cd\"" };


        assertEquals(1, cut.rulesForJSONEvent(event).size());
        assertEquals(1, cut.rulesForJSONEvent(event1).size());
        assertEquals(2, cut.rulesForJSONEvent(event2).size());

        Map<String, List<String>> namevals = new HashMap<>();
        // delete partial rule 23, partial match
        namevals.put("foo.bar", Collections.singletonList("\"ab\""));
        cut.deleteRule("r1", namevals);
        assertEquals(0, cut.rulesForJSONEvent(event).size());
        assertEquals(1, cut.rulesForJSONEvent(event1).size());
        assertEquals(1, cut.rulesForJSONEvent(event2).size());

        namevals.put("foo.bar", Collections.singletonList("\"cd\""));
        cut.deleteRule("r2", namevals);
        assertEquals(0, cut.rulesForJSONEvent(event).size());
        assertEquals(0, cut.rulesForJSONEvent(event1).size());
        assertEquals(0, cut.rulesForJSONEvent(event2).size());
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

        String event = "{\"A\": \"on\", \"C\": \"on\", \"D\": \"off\"}"; //{ "A", "\"on\"", "C", "\"on\"",  "D", "\"off\"" };
        String event1 = "{\"B\":\"on\", \"C\":\"on\", \"D\":\"off\"}";// { "B", "\"on\"",  "C", "\"on\"",  "D", "\"off\"" };
        String event2 = "{\"A\":\"on\",\"B\":\"on\",\"C\":\"on\", \"D\": \"on\"}";// { "A", "\"on\"",  "B", "\"on\"",  "C", "\"on\"",  "D", "\"on\"" };
        String event3 = "{\"A\":\"on\",\"B\":\"on\",\"C\":\"on\", \"D\":\"off\"}"; //{ "A", "\"on\"",  "B", "\"on\"",  "C", "\"on\"",  "D", "\"off\"" };

        assertEquals(1, cut.rulesForJSONEvent(event).size());
        assertEquals(1, cut.rulesForJSONEvent(event1).size());
        assertEquals(0, cut.rulesForJSONEvent(event2).size());
        assertEquals(1, cut.rulesForJSONEvent(event3).size());

        cut.deleteRule("AlarmRule1", condition1);
        assertEquals(0, cut.rulesForJSONEvent(event).size());
        assertEquals(1, cut.rulesForJSONEvent(event1).size());
        assertEquals(0, cut.rulesForJSONEvent(event2).size());
        assertEquals(1, cut.rulesForJSONEvent(event3).size());

        cut.deleteRule("AlarmRule1", condition2);
        assertEquals(0, cut.rulesForJSONEvent(event).size());
        assertEquals(0, cut.rulesForJSONEvent(event1).size());
        assertEquals(0, cut.rulesForJSONEvent(event2).size());
        assertEquals(0, cut.rulesForJSONEvent(event3).size());
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
        String eventJSON = "{\n" +
                "\"a\": [0],\n" +
                "\"b\": [-0.1],\n" +
                "\"c\": [0],\n" +
                "\"x\": [1],\n" +
                "\"y\": [0],\n" +
                "\"z\": [0.1]\n" +
                "}";
        assertEquals(1, cut.rulesForJSONEvent(eventJSON).size());

        // delete partial rule
        cut.deleteRule("r1", rule);
        assertEquals(0, cut.rulesForJSONEvent(eventJSON).size());
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

        assertEquals(1, cut.rulesForJSONEvent(event).size());
        assertEquals(1, cut.rulesForJSONEvent(event1).size());

        // delete partial rule 23, partial match
        cut.deleteRule("r1", rule);
        assertEquals(0, cut.rulesForJSONEvent(event).size());
        assertEquals(0, cut.rulesForJSONEvent(event1).size());
        assertTrue(cut.isEmpty());
    }

    @Test
    public void testAnythingButSuffix() throws Exception {

        String rule = "{\n" +
                "\"a\": [ { \"anything-but\": {\"suffix\": \"$\"} } ]\n" +
                "}";

        Machine machine = new Machine();
        machine.addRule("r1", rule);

        String event1 = "{" +
                "    \"a\": \"value$\"\n" +
                "}\n";

        String event2 = "{" +
                "    \"a\": \"notvalue\"\n" +
                "}\n";

        String event3 = "{" +
                "    \"a\": \"$notvalue\"\n" +
                "}\n";

        assertEquals(0, machine.rulesForJSONEvent(event1).size());
        assertEquals(1, machine.rulesForJSONEvent(event2).size());
        assertEquals(1, machine.rulesForJSONEvent(event3).size());

    }

    @Test
    public void testAnythingButEqualsIgnoreCase() throws Exception {

        String rule = "{\n" +
                "\"a\": [ { \"anything-but\": {\"equals-ignore-case\": [\"yes\", \"please\"]  } } ],\n" +
                "\"b\": [ { \"anything-but\": {\"equals-ignore-case\": \"no\"  } } ]\n" +
                "}";

        Machine machine = new Machine();
        machine.addRule("r1", rule);

        String event1 = "{" +
                "    \"a\": \"value\",\n" +
                "    \"b\": \"nothing\"\n" +
                "}\n";

        String event2 = "{" +
                "    \"a\": \"YES\",\n" +
                "    \"b\": \"nothing\"\n" +
                "}\n";

        String event3 = "{" +
                "    \"a\": \"yEs\",\n" +
                "    \"b\": \"nothing\"\n" +
                "}\n";

        String event4 = "{" +
                "    \"a\": \"pLease\",\n" +
                "    \"b\": \"nothing\"\n" +
                "}\n";

        String event5 = "{" +
                "    \"a\": \"PLEASE\",\n" +
                "    \"b\": \"nothing\"\n" +
                "}\n";

        String event6 = "{" +
                "    \"a\": \"please\",\n" +
                "    \"b\": \"nothing\"\n" +
                "}\n";

        String event7 = "{" +
                "    \"a\": \"please\",\n" +
                "    \"b\": \"no\"\n" +
                "}\n";

        String event8 = "{" +
                "    \"a\": \"please\",\n" +
                "    \"b\": \"No\"\n" +
                "}\n";

        String event9 = "{" +
                "    \"a\": \"please\",\n" +
                "    \"b\": \"No!\"\n" +
                "}\n";

        assertEquals(1, machine.rulesForJSONEvent(event1).size());
        assertEquals(0, machine.rulesForJSONEvent(event2).size());
        assertEquals(0, machine.rulesForJSONEvent(event3).size());
        assertEquals(0, machine.rulesForJSONEvent(event4).size());
        assertEquals(0, machine.rulesForJSONEvent(event5).size());
        assertEquals(0, machine.rulesForJSONEvent(event6).size());
        assertEquals(0, machine.rulesForJSONEvent(event7).size());
        assertEquals(0, machine.rulesForJSONEvent(event8).size());
        assertEquals(0, machine.rulesForJSONEvent(event9).size());

    }

    @Test
    public void testACWithExistFalseRule() throws Exception {
        // exists:false on leaf node of "interfaceName"
        String rule1 = "{\n" +
                              "  \"requestContext\": {\n" +
                              "    \"taskInstances\": {\n" +
                              "      \"taskInstanceState\": [\n" +
                              "        \"ACTIVE\"\n" +
                              "      ],\n" +
                              "      \"provider\": {\n" +
                              "        \"id\": [\n" +
                              "          \"XXXXXXXXXXXX\"\n" +
                              "        ]\n" +
                              "      },\n" +
                              "      \"resources\": {\n" +
                              "        \"additionalContext\": {\n" +
                              "          \"capabilityInterfaces\": {\n" +
                              "            \"interfaceName\": [\n" +
                              "              {\n" +
                              "                \"exists\": false\n" +
                              "              }\n" +
                              "            ]\n" +
                              "          }\n" +
                              "        }\n" +
                              "      }\n" +
                              "    }\n" +
                              "  }\n" +
                              "}";

        // exists:false on leaf node of "abc"
        String rule2 = "{\n" +
                              "  \"requestContext\": {\n" +
                              "    \"taskInstances\": {\n" +
                              "      \"taskInstanceState\": [\n" +
                              "        \"ACTIVE\"\n" +
                              "      ],\n" +
                              "      \"provider\": {\n" +
                              "        \"id\": [\n" +
                              "          \"XXXXXXXXXXXX\"\n" +
                              "        ]\n" +
                              "      },\n" +
                              "      \"resources\": {\n" +
                              "        \"additionalContext\": {\n" +
                              "          \"capabilityInterfaces\": {\n" +
                              "            \"abc\": [\n" +
                              "              {\n" +
                              "                \"exists\": false\n" +
                              "              }\n" +
                              "            ]\n" +
                              "          }\n" +
                              "        }\n" +
                              "      }\n" +
                              "    }\n" +
                              "  }\n" +
                              "}";

        // event1 should be able to match above 2 rules because of first element in array taskInstances
        String event1 = "{\n" +
                               "  \"requestContext\": {\n" +
                               "    \"taskInstances\": [\n" +
                               "      {\n" +
                               "        \"taskInstanceId\": \"id1\",\n" +
                               "        \"taskInstanceState\": \"ACTIVE\",\n" +
                               "        \"provider\": {\n" +
                               "          \"id\": \"XXXXXXXXXXXX\",\n" +
                               "          \"type\": \"XXXXXXXX\"\n" +
                               "        },\n" +
                               "        \"resources\": [\n" +
                               "          {\n" +
                               "            \"legacyFocusCategory\": \"XXXXXFocus\",\n" +
                               "            \"endpointIdentifier\": {\n" +
                               "              \"type\": \"XXXIdentifier\",\n" +
                               "              \"MaskedNumber\": \"XXXXXXXXXX\"\n" +
                               "            }\n" +
                               "          }\n" +
                               "        ]\n" +
                               "      },\n" +
                               "      {\n" +
                               "        \"taskInstanceId\": \"id1\",\n" +
                               "        \"taskInstanceState\": \"InACTIVE\",\n" +
                               "        \"provider\": {\n" +
                               "          \"id\": \"SomeRandom\",\n" +
                               "          \"type\": \"XXXXXXXX\"\n" +
                               "        },\n" +
                               "        \"resources\": [\n" +
                               "          {\n" +
                               "            \"legacyFocusCategory\": \"XXXXXFocus\",\n" +
                               "            \"endpointIdentifier\": {\n" +
                               "              \"type\": \"XXXIdentifier\",\n" +
                               "              \"MaskedNumber\": \"XXXXXXXXXXXX\"\n" +
                               "            },\n" +
                               "            \"additionalContext\": {\n" +
                               "              \"capabilityInterfaces\": [\n" +
                               "                {\n" +
                               "                  \"interfaceName\": \"Dummy.DummyDumDum\"\n" +
                               "                }\n" +
                               "              ]\n" +
                               "            }\n" +
                               "          }\n" +
                               "        ]\n" +
                               "      }\n" +
                               "    ]\n" +
                               "  }\n" +
                               "}";

        // event2 should only match the second rule because additionalContext.capabilityInterfaces.interfaceName exists.
        String event2 = "{\n" +
                                "  \"requestContext\": {\n" +
                                "    \"taskInstances\": [\n" +
                                "      {\n" +
                                "        \"taskInstanceId\": \"id1\",\n" +
                                "        \"taskInstanceState\": \"ACTIVE\",\n" +
                                "        \"provider\": {\n" +
                                "          \"id\": \"XXXXXXXXXXXX\",\n" +
                                "          \"type\": \"XXXXXXXX\"\n" +
                                "        },\n" +
                                "        \"resources\": [\n" +
                                "          {\n" +
                                "            \"legacyFocusCategory\": \"XXXXXFocus\",\n" +
                                "            \"endpointIdentifier\": {\n" +
                                "              \"type\": \"XXXIdentifier\",\n" +
                                "              \"MaskedNumber\": \"XXXXXXXXXX\"\n" +
                                "            },\n" +
                                "            \"additionalContext\": {\n" +
                                "              \"capabilityInterfaces\": [\n" +
                                "                {\n" +
                                "                  \"interfaceName\": \"XXXX.XXXXXXXXXXXXXX\"\n" +
                                "                }\n" +
                                "              ]\n" +
                                "            }\n" +
                                "          }\n" +
                                "        ]\n" +
                                "      },\n" +
                                "      {\n" +
                                "        \"taskInstanceId\": \"id1\",\n" +
                                "        \"taskInstanceState\": \"InACTIVE\",\n" +
                                "        \"provider\": {\n" +
                                "          \"id\": \"SomeRandom\",\n" +
                                "          \"type\": \"XXXXXXXX\"\n" +
                                "        },\n" +
                                "        \"resources\": [\n" +
                                "          {\n" +
                                "            \"legacyFocusCategory\": \"XXXXXFocus\",\n" +
                                "            \"endpointIdentifier\": {\n" +
                                "              \"type\": \"XXXIdentifier\",\n" +
                                "              \"MaskedNumber\": \"XXXXXXXXXXXX\"\n" +
                                "            },\n" +
                                "            \"additionalContext\": {\n" +
                                "              \"capabilityInterfaces\": [\n" +
                                "                {\n" +
                                "                  \"interfaceName\": \"XXXX.XXXXXXXXXXXXXX\"\n" +
                                "                }\n" +
                                "              ]\n" +
                                "            }\n" +
                                "          }\n" +
                                "        ]\n" +
                                "      }\n" +
                                "    ]\n" +
                                "  }\n" +
                                "}";

        Machine cut = new Machine();
        // add the rule, ensure it matches
        cut.addRule("r1", rule1);
        cut.addRule("r2", rule2);

        assertEquals(2, cut.rulesForJSONEvent(event1).size());
        List<String> matchedRules = cut.rulesForJSONEvent(event2);
        assertEquals(1, matchedRules.size());
        assertEquals("r2", matchedRules.get(0));
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

        List<String> matches = machine.rulesForJSONEvent(event1);
        assertEquals(1, matches.size());
        assertTrue(matches.contains("rule2"));
        matches = machine.rulesForJSONEvent(event2);
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

        List<String> found = machine.rulesForJSONEvent(event);
        assertEquals(2, found.size());
        assertTrue(found.contains("rule1"));
        assertTrue(found.contains("rule2"));

        machine.deleteRule("rule1", rule1);
        found = machine.rulesForJSONEvent(event);
        assertEquals(1, found.size());
        machine.deleteRule("rule2", rule2);
        found = machine.rulesForJSONEvent(event);
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

        List<String> found = machine.rulesForJSONEvent(event);
        assertEquals(2, found.size());
        assertTrue(found.contains("rule1"));
        assertTrue(found.contains("rule2"));

        machine.deleteRule("rule1", rule1);
        found = machine.rulesForJSONEvent(event);
        assertEquals(1, found.size());
        machine.deleteRule("rule2", rule2);
        found = machine.rulesForJSONEvent(event);
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

        List<String> found = machine.rulesForJSONEvent(event1);
        assertEquals(0, found.size());
        found = machine.rulesForJSONEvent(event2);
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
        List<String> matches = machine.rulesForJSONEvent(event);
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

        List<String> matches = machine.rulesForJSONEvent(event1);
        assertEquals(1, matches.size());
        assertTrue(matches.contains("rule1"));
        matches = machine.rulesForJSONEvent(event2);
        assertEquals(1, matches.size());
        assertTrue(matches.contains("rule1"));

        // Shared NameState will remain as it is used by rule1
        machine.deleteRule("rule2", rule2);

        matches = machine.rulesForJSONEvent(event1);
        assertEquals(1, matches.size());
        assertTrue(matches.contains("rule1"));
        matches = machine.rulesForJSONEvent(event2);
        assertEquals(1, matches.size());
        assertTrue(matches.contains("rule1"));

        // "y" also leads to the shared NameState. The shared NameState will get extended by rule2's "foo" field. By
        // checking that only events with a "bar" of "y" and not a "bar" of "x", we verify that no remnants of the
        // original rule2 were left in the shared NameState.
        String rule2b = "{\"foo\":[\"a\"], \"bar\":[\"y\"]}";

        machine.addRule("rule2", rule2b);

        matches = machine.rulesForJSONEvent(event1);
        assertEquals(1, matches.size());
        assertTrue(matches.contains("rule1"));
        matches = machine.rulesForJSONEvent(event2);
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
        List<String> matches = machine.rulesForJSONEvent(event);
        assertEquals(2, matches.size());
        assertTrue(matches.contains("rule1"));
        assertTrue(matches.contains("rule2"));

        // Delete rule2, which is a subpath/prefix of rule1, and ensure full path still exists for rule1 to match
        machine.deleteRule("rule2", rule2);

        matches = machine.rulesForJSONEvent(event);
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
        List<String> matches = machine.rulesForJSONEvent(event);
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
        List<String> matches = machine.rulesForJSONEvent(event);
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
        List<String> matches = machine.rulesForJSONEvent(event);
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
        List<String> matches = machine.rulesForJSONEvent(event);
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
        List<String> matches = machine.rulesForJSONEvent(event);
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
        List<String> matches = machine.rulesForJSONEvent(event);
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
        List<String> matches = machine.rulesForJSONEvent(event);
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
        List<String> matches = machine.rulesForJSONEvent(event);
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
        List<String> matches = machine.rulesForJSONEvent(event);
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
        List<String> matches = machine.rulesForJSONEvent(event);
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
        List<String> matches = machine.rulesForJSONEvent(event);
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
        List<String> matches = machine.rulesForJSONEvent(event);
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

        List<String> matches = machine.rulesForJSONEvent(event);
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

        List<String> matches = machine.rulesForJSONEvent(event);
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

        List<String> matches = machine.rulesForJSONEvent(event);
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

        List<String> matches = machine.rulesForJSONEvent(event);
        assertEquals(1, matches.size());
        assertTrue(matches.contains("rule2"));
    }
}
