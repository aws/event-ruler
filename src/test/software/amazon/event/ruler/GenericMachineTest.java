package software.amazon.event.ruler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit testing a state GenericMachine is hard.  Tried hand-computing a few GenericMachines
 *  but kept getting them wrong, the software was right.  So this is really
 *  more of a smoke/integration test.  But the coverage is quite good.
 */
public class GenericMachineTest {

    @Test
    public void anythingButPrefixTest() throws Exception {
        String event = "  {\n" +
                "    \"a\": \"lorem\", " +
                "    \"b\": \"ipsum\"" +
                "  }";
        String ruleTemplate = "{\n" +
                "  \"a\": [ { \"anything-but\": { \"prefix\": \"FOO\" } } ]" +
                "}";

        String[] prefixes = { "l", "lo", "lor", "lorem" };
        List<String> ruleNames = new ArrayList<>();
        Machine m = new Machine();
        for (int i = 0; i < prefixes.length; i++) {
            String ruleName = "r" + i;
            ruleNames.add(ruleName);
            String rule = ruleTemplate.replace("FOO", prefixes[i]);
            m.addRule(ruleName, rule);
        }
        List<String> matches = m.rulesForJSONEvent(event);
        assertEquals(0, matches.size());

        String[] shouldMatch = { "now", "is", "the", "time", "for", "all", "good", "aliens" };
        for (String s : shouldMatch) {
            String e = event.replace("lorem", s);
            matches = m.rulesForJSONEvent(e);
            assertEquals(ruleNames.size(), matches.size());
            for (String rn : ruleNames) {
                assertTrue(matches.contains(rn));
            }
        }

        for (int i = 0; i < prefixes.length; i++) {
            String ruleName = "r" + i;
            String rule = ruleTemplate.replace("FOO", prefixes[i]);
            m.deleteRule(ruleName, rule);
        }
        for (String s : shouldMatch) {
            String e = event.replace("lorem", s);
            matches = m.rulesForJSONEvent(e);
            assertEquals(0, matches.size());
        }
    }

    @Test
    public void anythingButSuffixTest() throws Exception {
        String event = "  {\n" +
                "    \"a\": \"lorem\", " +
                "    \"b\": \"ipsum\"" +
                "  }";
        String ruleTemplate = "{\n" +
                "  \"a\": [ { \"anything-but\": { \"suffix\": \"FOO\" } } ]" +
                "}";

        String[] suffixes = { "m", "em", "rem", "orem", "lorem" };
        List<String> ruleNames = new ArrayList<>();
        Machine m = new Machine();
        for (int i = 0; i < suffixes.length; i++) {
            String ruleName = "r" + i;
            ruleNames.add(ruleName);
            String rule = ruleTemplate.replace("FOO", suffixes[i]);
            m.addRule(ruleName, rule);
        }
        List<String> matches = m.rulesForJSONEvent(event);

        assertEquals(0, matches.size());

        String[] shouldMatch = { "run", "walk", "skip", "hop" };
        for (String s : shouldMatch) {
            String e = event.replace("lorem", s);
            matches = m.rulesForJSONEvent(e);
            assertEquals(ruleNames.size(), matches.size());
            for (String rn : ruleNames) {
                assertTrue(matches.contains(rn));
            }
        }

        for (int i = 0; i < suffixes.length; i++) {
            String ruleName = "r" + i;
            String rule = ruleTemplate.replace("FOO", suffixes[i]);
            m.deleteRule(ruleName, rule);
        }
        for (String s : shouldMatch) {
            String e = event.replace("lorem", s);
            matches = m.rulesForJSONEvent(e);
            assertEquals(0, matches.size());
        }
    }

    public static String readData(String jsonName) throws Exception {
        String wd = System.getProperty("user.dir");
        Path path = FileSystems.getDefault().getPath(wd, "src", "test", "data", jsonName);
        return new String(Files.readAllBytes(path));
    }

    public static JsonNode readAsTree(String jsonName) throws Exception, IOException {
        return  new ObjectMapper().readTree(readData(jsonName));
    }


    @Test
    public void arraysBugTest() throws Exception {
        String event = "{\n" +
                "  \"requestContext\": { \"obfuscatedCustomerId\": \"AIDACKCEVSQ6C2EXAMPLE\" },\n" +
                "  \"hypotheses\": [\n" +
                "    { \"isBluePrint\": true, \"creator\": \"A123\" },\n" +
                "    { \"isBluePrint\": false, \"creator\": \"A234\" }\n" +
                "  ]\n" +
                "}";

        String r1 = "{\n" +
                "  \"hypotheses\": {\n" +
                "    \"isBluePrint\": [ false ],\n" +
                "    \"creator\": [ \"A123\" ]\n" +
                "  }\n" +
                "}";
        String r2 = "{\n" +
                "  \"hypotheses\": {\n" +
                "    \"isBluePrint\": [ true ],\n" +
                "    \"creator\": [ \"A234\" ]\n" +
                "  }\n" +
                "}";

        Machine m = new Machine();
        m.addRule("r1", r1);
        m.addRule("r2", r2);
        JsonNode json = new ObjectMapper().readTree(event);

        List<String> matches = m.rulesForJSONEvent(json);
        assertEquals(0, matches.size());
    }

    @Test
    public void nestedArraysTest() throws Exception {

        String event1 = readData("arrayEvent1.json");
        String event2 = readData("arrayEvent2.json");
        String event3 = readData("arrayEvent3.json");
        String event4 = readData("arrayEvent4.json");

        String rule1 = readData("arrayRule1.json");
        String rule2 = readData("arrayRule2.json");
        String rule3 = readData("arrayRule3.json");

        Machine m = new Machine();
        m.addRule("rule1", rule1);
        m.addRule("rule2", rule2);
        m.addRule("rule3", rule3);

        List<String> r1 = m.rulesForEvent(event1);
        List<String> r1AC = m.rulesForJSONEvent(event1);
        assertEquals(2, r1.size());
        assertTrue(r1.contains("rule1"));
        assertTrue(r1.contains("rule2"));
        assertEquals(r1, r1AC);

        // event2 shouldn't match any rules
        List<String> r2 = m.rulesForEvent(event2);
        List<String> r2AC = m.rulesForJSONEvent(event2);
        assertEquals(0, r2.size());
        assertEquals(r2, r2AC);

        // event3 shouldn't match any rules with AC on
        List<String> r3 = m.rulesForEvent(event3);
        List<String> r3AC = m.rulesForJSONEvent(event3);
        assertEquals(1, r3.size());
        assertTrue(r3.contains("rule3"));
        assertEquals(0, r3AC.size());

        // event4 should match rule3
        List<String> r4 = m.rulesForEvent(event4);
        List<String> r4AC = m.rulesForJSONEvent(event4);
        assertEquals(1, r4.size());
        assertTrue(r4.contains("rule3"));
        assertEquals(r4, r4AC);
    }

    // create a customized class as T
    // TODO: Figure out what these unused fields are for and either finish what was started here, or discard it
    public static final class SimpleFilter {
        private String filterId;
        private String filterExpression;
        private List<String> downChannels;
        private long lastUpdatedMs;

        SimpleFilter(String clientId, String filterId, String filterExpression,
                            List<String> downChannels, long lastUpdatedMs) {
            this.filterId = filterId;
            this.filterExpression = filterExpression;
            this.downChannels = downChannels;
            this.lastUpdatedMs = lastUpdatedMs;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof SimpleFilter)) {
                return false;
            }
            SimpleFilter other = (SimpleFilter) obj;
            return filterId.equals(other.filterId);
        }

        @Override
        public int hashCode() {
            return filterId.hashCode();
        }
    }

    private final static SimpleFilter r1 =
            new SimpleFilter("clientId1111","filterId1111","{ \"a\" : [ 1 ] }",
            Arrays.asList("dc11111", "dc21111", "dc31111"),1525821702L);
    private final static SimpleFilter r2 =
            new SimpleFilter("clientId2222","filterId2222","{ \"a\" : [ 2 ] }",
            Arrays.asList("dc12222", "dc22222", "dc32222"),1525821702L);
    private final static SimpleFilter r3 =
            new SimpleFilter("clientId1111","filterId3333","{ \"a\" : [ 3 ] }",
            Arrays.asList("dc13333", "dc23333", "dc33333"),1525821702L);

    @Test
    public void testSimplestPossibleGenericMachine() throws Exception {

        String rule1 = "{ \"a\" : [ 1 ] }";
        String rule2 = "{ \"b\" : [ 2 ] }";
        String rule3 = "{ \"c\" : [ 3 ] }";

        GenericMachine<SimpleFilter> genericMachine = new GenericMachine<>();
        genericMachine.addRule(r1, rule1);
        genericMachine.addRule(r2, rule2);
        genericMachine.addRule(r3, rule3);

        String[] event1 = { "a", "1" };
        String jsonEvent1 = "{ \"a\" :  1 }" ;
        String[] event2 = { "b", "2" };
        String jsonEvent2 = "{ \"b\" :  2 }" ;
        String[] event4 = { "x", "true" };
        String jsonEvent4 = "{ \"x\" :  true }" ;
        String[] event5 = { "a", "1", "b", "2","c", "3"};
        String jsonEvent5 = "{ \"a\" :  1, \"b\": 2, \"c\" : 3 }";

        List<SimpleFilter> val;
        val = genericMachine.rulesForEvent(event1);
        assertEquals(1, val.size());
        assertEquals(r1, val.get(0));
        assertNotNull(val.get(0));

        val = genericMachine.rulesForJSONEvent(jsonEvent1);
        assertEquals(1, val.size());
        assertEquals(r1, val.get(0));
        assertNotNull(val.get(0));

        val = genericMachine.rulesForEvent(event2);
        assertEquals(1, val.size());
        assertEquals(r2, val.get(0));
        assertNotNull(val.get(0));

        val = genericMachine.rulesForJSONEvent(jsonEvent2);
        assertEquals(1, val.size());
        assertEquals(r2, val.get(0));
        assertNotNull(val.get(0));

        val = genericMachine.rulesForEvent(event4);
        assertEquals(0, val.size());

        val = genericMachine.rulesForJSONEvent(jsonEvent4);
        assertEquals(0, val.size());

        val = genericMachine.rulesForEvent(event5);
        assertEquals(3, val.size());
        val.forEach(r -> assertTrue(Stream.of(r1, r2, r3).anyMatch(i -> (i == r))));

        val = genericMachine.rulesForJSONEvent(jsonEvent5);
        assertEquals(3, val.size());
        val.forEach(r -> assertTrue(Stream.of(r1, r2, r3).anyMatch(i -> (i == r))));
    }

    @Test
    public void testEvaluateComplexity() throws Exception {
        String rule1 = "{ \"a\" : [ { \"wildcard\": \"a*bc\" } ] }";
        String rule2 = "{ \"b\" : [ { \"wildcard\": \"a*aa\" } ] }";
        String rule3 = "{ \"c\" : [ { \"wildcard\": \"xyz*\" } ] }";

        GenericMachine<SimpleFilter> genericMachine = new GenericMachine<>();
        genericMachine.addRule(r1, rule1);
        genericMachine.addRule(r2, rule2);
        genericMachine.addRule(r3, rule3);

        MachineComplexityEvaluator evaluator = new MachineComplexityEvaluator(100);
        assertEquals(3, genericMachine.evaluateComplexity(evaluator));
    }

    @Test
    public void testEvaluateComplexityHitMax() throws Exception {
        String rule1 = "{ \"a\" : [ { \"wildcard\": \"a*bc\" } ] }";
        String rule2 = "{ \"b\" : [ { \"wildcard\": \"a*aa\" } ] }";
        String rule3 = "{ \"c\" : [ { \"wildcard\": \"xyz*\" } ] }";

        GenericMachine<SimpleFilter> genericMachine = new GenericMachine<>();
        genericMachine.addRule(r1, rule1);
        genericMachine.addRule(r2, rule2);
        genericMachine.addRule(r3, rule3);

        MachineComplexityEvaluator evaluator = new MachineComplexityEvaluator(2);
        assertEquals(2, genericMachine.evaluateComplexity(evaluator));
    }

    @Test
    public void testEmptyInput() throws Exception {
        String rule1 = "{\n" + "\"detail\": {\n" + " \"c-count\": [ { \"exists\": false  } ]\n" + "},\n"
                + "\"d-count\": [ { \"exists\": false  } ],\n" + "\"e-count\": [ { \"exists\": false  } ]\n" + "}";

        String event = "{}";
        GenericMachine<String> genericMachine = new GenericMachine<>();
        genericMachine.addRule("rule1", rule1);

        List<String> rules = genericMachine.rulesForJSONEvent(event);
        assertTrue(rules.contains("rule1"));

        rules = genericMachine.rulesForEvent(new ArrayList<>());
        assertTrue(rules.contains("rule1"));
    }

}
