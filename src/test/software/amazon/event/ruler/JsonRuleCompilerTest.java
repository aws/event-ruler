package software.amazon.event.ruler;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class JsonRuleCompilerTest {

    @Test
    public void testBigNumbers() throws Exception {
        Machine m = new Machine();
        String rule = "{\n" +
                "  \"account\": [ 123456789012 ]\n" +
                "}";
        String event = "{\"account\": 123456789012 }";
        m.addRule("r1", rule);
        assertEquals(1, m.rulesForJSONEvent(event).size());
    }

    @Test
    public void testVariantForms() throws Exception {
        Machine m = new Machine();
        String r1 = "{\n" +
                "  \"a\": [ 133.3 ]\n" +
                "}";
        String r2 = "{\n" +
                "  \"a\": [ { \"numeric\": [ \">\", 120, \"<=\", 140 ] } ]\n" +
                "}";
        String r3 = "{\n" +
                "  \"b\": [ \"192.0.2.0\" ]\n" +
                "}\n";
        String r4 = "{\n" +
                "  \"b\": [ { \"cidr\": \"192.0.2.0/24\" } ]\n" +
                "}";
        String event = "{\n" +
                "  \"a\": 133.3,\n" +
                "  \"b\": \"192.0.2.0\"\n" +
                "}";
        m.addRule("r1", r1);
        m.addRule("r2", r2);
        m.addRule("r3", r3);
        m.addRule("r4", r4);
        List<String> nr = m.rulesForJSONEvent(event);
        assertEquals(4, nr.size());
    }

    @Test
    public void testCompile() throws Exception {
        String j = "[1,2,3]";
        assertNotNull("Top level must be an object", JsonRuleCompiler.check(j));

        InputStream is = new ByteArrayInputStream(j.getBytes(StandardCharsets.UTF_8));
        assertNotNull("Top level must be an object (bytes)", JsonRuleCompiler.check(is));

        j = "{\"a\":1}";
        assertNotNull("Values must be in an array", JsonRuleCompiler.check(j.getBytes(StandardCharsets.UTF_8)));

        j = "{\"a\":[ { \"x\":2 } ]}";
        assertNotNull("Array values must be primitives", JsonRuleCompiler.check(new StringReader(j)));

        j = "{ \"foo\": {}}";
        assertNotNull("Objects must not be empty", JsonRuleCompiler.check(new StringReader(j)));

        j = "{ \"foo\": []}";
        assertNotNull("Arrays must not be empty", JsonRuleCompiler.check(new StringReader(j)));

        j = "{\"a\":[1]}";
        assertNull(JsonRuleCompiler.check(j));

        Map<String, List<Patterns>> m = JsonRuleCompiler.compile(new ByteArrayInputStream(j.getBytes(StandardCharsets.UTF_8))).get(0);
        List<Patterns> l = m.get("a");
        assertEquals(2, l.size());
        for (Patterns p : l) {
            ValuePatterns vp = (ValuePatterns) p;
            if (p.type() == MatchType.NUMERIC_EQ) {
                assertEquals(ComparableNumber.generate(1.0), vp.pattern());
            } else {
                assertEquals("1", vp.pattern());
            }
        }

        j = "{\"a\": [ { \"prefix\": \"child\" } ] }";
        assertNull("Good prefix should parse", JsonRuleCompiler.check(j));

        j = "{\"a\": [ { \"suffix\": \"child\" } ] }";
        assertNull("Good suffix should parse", RuleCompiler.check(j));

        j = "{\"a\": [ { \"anything-but\": \"child\" } ] }";
        assertNull("Good anything-but should parse", JsonRuleCompiler.check(j));

        j = "{\"a\": [ { \"anything-but\": [\"child0\",\"child1\",\"child2\"] } ] }";
        assertNull("Good anything-but should parse", JsonRuleCompiler.check(j));

        j = "{\"a\": [ { \"anything-but\": [111,222,333] } ] }";
        assertNull("Good anything-but should parse", JsonRuleCompiler.check(j));

        j = "{\"a\": [ { \"anything-but\": \"child0\" } ] }";
        assertNull("Good anything-but should parse", JsonRuleCompiler.check(j));

        j = "{\"a\": [ { \"anything-but\": 111 } ] }";
        assertNull("Good anything-but should parse", JsonRuleCompiler.check(j));

        j = "{\"a\": [ { \"anything-but\": { \"prefix\": \"foo\" } } ] }";
        assertNull("Good anything-but should parse", JsonRuleCompiler.check(j));

        j = "{\"a\": [ { \"anything-but\": { \"suffix\": \"foo\" } } ] }";
        assertNull("Good anything-but should parse", JsonRuleCompiler.check(j));

        j = "{\"a\": [ { \"anything-but\": {\"equals-ignore-case\": \"rule\" } } ] }";
        assertNull("Good anything-but/ignore-case should parse", JsonRuleCompiler.check(j));

        j = "{\"a\": [ { \"anything-but\": {\"equals-ignore-case\": [\"abc\", \"123\"] } } ] }";
        assertNull("Good anything-but/ignore-case should parse", JsonRuleCompiler.check(j));


        j = "{\"a\": [ { \"exactly\": \"child\" } ] }";
        assertNull("Good exact-match should parse", JsonRuleCompiler.check(j));

        j = "{\"a\": [ { \"exists\": true } ] }";
        assertNull("Good exists true should parse", JsonRuleCompiler.check(j));

        j = "{\"a\": [ { \"exists\": false } ] }";
        assertNull("Good exists false should parse", JsonRuleCompiler.check(j));

        j = "{\"a\": [ { \"cidr\": \"10.0.0.0/8\" } ] }";
        assertNull("Good CIDR should parse", JsonRuleCompiler.check(j));

        j = "{\"a\": [ { \"equals-ignore-case\": \"abc\" } ] }";
        assertNull("Good equals-ignore-case should parse", JsonRuleCompiler.check(j));

        j = "{\"a\": [ { \"wildcard\": \"a*b*c\" } ] }";
        assertNull("Good wildcard should parse", JsonRuleCompiler.check(j));

        String[] badPatternTypes = {
                "{\"a\": [ { \"exactly\": 33 } ] }",
                "{\"a\": [ { \"prefix\": \"child\", \"foo\": [] } ] }",
                "{\"a\": [ { \"prefix\": 3 } ] }",
                "{\"a\": [ { \"prefix\": [1, 2 3] } ] }",
                "{\"a\": [ { \"suffix\": \"child\", \"foo\": [] } ] }",
                "{\"a\": [ { \"suffix\": 3 } ] }",
                "{\"a\": [ { \"suffix\": [1, 2 3] } ] }",
                "{\"a\": [ { \"foo\": \"child\" } ] }",
                "{\"a\": [ { \"cidr\": \"foo\" } ] }",
                "{\"a\": [ { \"anything-but\": \"child\", \"foo\": [] } ] }",
                "{\"a\": [ { \"anything-but\": [1, 2 3] } ] }",
                "{\"a\": [ { \"anything-but\": \"child\", \"foo\": [] } ] }",
                "{\"a\": [ { \"anything-but\": [\"child0\",111,\"child2\"] } ] }",
                "{\"a\": [ { \"anything-but\": [1, 2 3] } ] }",
                "{\"a\": [ { \"anything-but\": { \"foo\": 3 } ] }",
                "{\"a\": [ { \"anything-but\": { \"prefix\": 27 } } ] }",
                "{\"a\": [ { \"anything-but\": { \"prefix\": \"\" } } ] }",
                "{\"a\": [ { \"anything-but\": { \"prefix\": \"foo\", \"a\":1 } } ] }",
                "{\"a\": [ { \"anything-but\": { \"prefix\": \"foo\" }, \"x\": 1 } ] }",
                "{\"a\": [ { \"anything-but\": { \"suffix\": 27 } } ] }",
                "{\"a\": [ { \"anything-but\": { \"suffix\": \"\" } } ] }",
                "{\"a\": [ { \"anything-but\": { \"suffix\": \"foo\", \"a\":1 } } ] }",
                "{\"a\": [ { \"anything-but\": { \"suffix\": \"foo\" }, \"x\": 1 } ] }",
                "{\"a\": [ { \"anything-but\" : { \"equals-ignore-case\": [1, 2 3] } } ] }",
                "{\"a\": [ { \"anything-but\": {\"equals-ignore-case\": [1, 2, 3] } } ] }", // no numbers
                "{\"a\": [ { \"equals-ignore-case\": 5 } ] }",
                "{\"a\": [ { \"equals-ignore-case\": [ \"abc\" ] } ] }",
                "{\"a\": [ { \"wildcard\": 5 } ] }",
                "{\"a\": [ { \"wildcard\": [ \"abc\" ] } ] }"
        };
        for (String badPattern : badPatternTypes) {
            assertNotNull("bad pattern shouldn't parse", JsonRuleCompiler.check(badPattern));
        }

        j = "{\n"
                + "  \"resources\": [\n"
                + "    \"r1\",\n"
                + "    \"r2\"\n"
                + "  ]\n"
                + "}";
        m = JsonRuleCompiler.compile(j).get(0);
        l = m.get("resources");
        assertEquals(2, l.size());
        ValuePatterns vp1 = (ValuePatterns) l.get(0);
        ValuePatterns vp2 = (ValuePatterns) l.get(1);
        assertEquals("\"r1\"", vp1.pattern());
        assertEquals("\"r2\"", vp2.pattern());

        j = "{\n"
                + "  \"detail-getType\": [ \"ec2/spot-bid-matched\" ],\n"
                + "  \"detail\" : { \n"
                + "   \"state\": [ \"in-service\", \"dead\" ]\n"
                + "  }\n"
                + "}\n";
        m = JsonRuleCompiler.compile(j).get(0);
        assertEquals(2, m.size());
        l = m.get("detail-getType");
        vp1 = (ValuePatterns) l.get(0);
        assertEquals("\"ec2/spot-bid-matched\"", vp1.pattern());
        l = m.get("detail.state");
        assertEquals(2, l.size());
        vp1 = (ValuePatterns) l.get(0);
        vp2 = (ValuePatterns) l.get(1);
        assertEquals("\"in-service\"", vp1.pattern());
        assertEquals("\"dead\"", vp2.pattern());
    }

    @Test
    public void testNumericExpressions() {
        String[] goods = {
                "[\"=\", 3.8]", "[\"=\", 0.00000033]", "[\"=\", -4e-8]", "[\"=\", 55555]",
                "[\"<\", 3.8]", "[\"<\", 0.00000033]", "[\"<\", -4e-8]", "[\"<\", 55555]",
                "[\">\", 3.8]", "[\">\", 0.00000033]", "[\">\", -4e-8]", "[\">\", 55555]",
                "[\"<=\", 3.8]", "[\"<=\", 0.00000033]", "[\"<=\", -4e-8]", "[\"<=\", 55555]",
                "[\">=\", 3.8]", "[\">=\", 0.00000033]", "[\">=\", -4e-8]", "[\">=\", 55555]",
                "[\">\", 0, \"<\", 1]", "[\">=\", 0, \"<\", 1]",
                "[\">\", 0, \"<=\", 1]", "[\">=\", 0, \"<=\", 1]"
        };

        String[] bads = {
                "[\"=\", true]", "[\"=\", 2.0e22]", "[\"=\", \"-4e-8\"]", "[\"=\"]",
                "[\"<\", true]", "[\"<\", 2.0e22]", "[\"<\", \"-4e-8\"]", "[\"<\"]",
                "[\">=\", true]", "[\">=\", 2.0e22]", "[\">=\", \"-4e-8\"]", "[\">=\"]",
                "[\"<=\", true]", "[\"<=\", 2.0e22]", "[\"<=\", \"-4e-8\"]", "[\"<=\"]",
                "[\"<>\", 1, \">\", 0]", "[\"==\", 1, \">\", 0]",
                "[\"<\", 1, \">\", 0]", "[\">\", 1, \"<\", 1]",
                "[\">\", 30, \"<\", 1]", "[\">\", 1, \"<\", 30, false]"
        };

        for (String good : goods) {
            String json = "{\"x\": [{\"numeric\": " + good + "}]}";
            String m = JsonRuleCompiler.check(json);
            assertNull(json + " => " + m, m);
        }

        for (String bad : bads) {
            String json = "{\"x\": [{\"numeric\": " + bad + "}]}";
            String m = JsonRuleCompiler.check(json);
            assertNotNull("Bad: " + json, m);
        }
    }

    @Test
    public void testExistsExpression() {
        String[] goods = {
                "true ",
                " false "
        };

        String[] bads = {
                "\"badString\"",
                "\"= abc\"",
                "true, \"extraKey\": \"extraValue\" "
        };

        for (String good : goods) {
            String json = "{\"x\": [{\"exists\": " + good + "}]}";
            String m = JsonRuleCompiler.check(json);
            assertNull(json + " => " + m, m);
        }

        for (String bad : bads) {
            String json = "{\"x\": [{\"exists\": " + bad + "}]}";
            String m = JsonRuleCompiler.check(json);
            assertNotNull("Bad: " + json, m);
        }
    }

    @Test
    public void testEnd2End() throws Exception {
        Machine machine = new Machine();
        String[] event = {
                "account", "\"012345678901\"",
                "detail-getType", "\"ec2/spot-bid-matched\"",
                "detail.instanceId", "arn:aws:ec2:us-east-1::image/ami-1a2b3c4d\"",
                "detail.spotInstanceRequestId", "\"eaa472d8-8422-a9bb-8888-4919fd99310\"",
                "detail.state", "\"in-service\"",
                "detail.requestParameters.zone", "\"us-east-1a\"",
                "id", "\"cdc73f9d-aea9-11e3-9d5a-835b769c0d9c\"",
                "region", "\"us-west-2\"",
                "resources", "\"arn:aws:ec2:us-east-1::image/ami-1a2b3c4d\"",
                "source", "\"aws.ec2\"",
                "tags", "\"2015Q1",
                "tags", "\"Euro-fleet\"",
                "time", "\"2014-03-18T14:30:07Z\"",
                "version", "\"0\"",
        };
        String rule1 = "{\n"
                + "  \"resources\": [\n"
                + "    \"arn:aws:ec2:us-east-1::image/ami-1a2b3c4d\",\n"
                + "    \"arn:aws:ec2:us-east-1::image/ami-98765432\"\n"
                + "  ]\n"
                + "}\n";
        String rule2 = "{\n"
                + "  \"detail-getType\": [ \"ec2/spot-bid-matched\" ],\n"
                + "  \"detail\" : { \n"
                + "    \"state\": [ \"in-service\" ]\n"
                + "  }\n"
                + "}\n";

        String rule3 = "{\n"
                + "  \"tags\": [ \"Euro-fleet\", \"Asia-fleet\" ]\n"
                + "}\n";

        String rule4 = "{\n"
                + "  \"resources\": [\n"
                + "    \"arn:aws:ec2:us-east-1::image/ami-1a2b3c4d\",\n"
                + "    \"arn:aws:ec2:us-east-1::image/ami-98765432\"\n"
                + "  ],\n"
                + "  \"detail.state\": [ \"halted\", \"pending\"]\n"
                + "}\n";

        String rule5 = "{\n"
                + "  \"resources\": [\n"
                + "    \"arn:aws:ec2:us-east-1::image/ami-1a2b3c4d\",\n"
                + "    \"arn:aws:ec2:us-east-1::image/ami-98765432\"\n"
                + "  ],\n"
                + "  \"detail.request-level\": [ \"urgent\"]\n"
                + "}\n";

        String rule6 = "{\n"
                + "  \"detail-getType\": [ \"ec2/spot-bid-matched\" ],\n"
                + "  \"detail\" : { \n"
                + "    \"requestParameters\": {\n"
                + "      \"zone\": [\n"
                + "        \"us-east-1a\"\n"
                + "      ]\n"
                + "    }\n"
                + "  }\n"
                + "}\n";

        machine.addRule("rule1", rule1);
        machine.addRule("rule2", rule2);
        machine.addRule("rule3", rule3);
        machine.addRule("rule4", rule4);
        machine.addRule("rule5", rule5);
        machine.addRule("rule6", rule6);
        List<String> found = machine.rulesForEvent(event);
        assertEquals(4, found.size());
        assertTrue(found.contains("rule1"));
        assertTrue(found.contains("rule2"));
        assertTrue(found.contains("rule3"));
        assertTrue(found.contains("rule6"));
    }

    @Test
    public void testBasicFunctionOfOrRelationshipRules() throws Exception {
        final Machine machine = new Machine();
        String event = "{\n" +
                "  \"detail\": {\n" +
                "    \"detail-type\": \"EC2 Instance State-change Notification\",\n" +
                "    \"resources\": \"arn:aws:ec2:us-east-1:123456789012:instance/i-000000aaaaaa00000\",\n" +
                "    \"info\": {\n" +
                "      \"state-status\": \"running\",\n" +
                "      \"state-count\": {\n" +
                "        \"count\": 100\n" +
                "      }\n" +
                "    },\n" +
                "    \"c-count\": 1,\n" +
                "    \"d-count\": 8\n" +
                "  },\n" +
                "  \"news\": {\n" +
                "    \"newsProviders\": \"p1\",\n" +
                "    \"newCondition1\": \"news111\",\n" +
                "    \"newCondition2\": \"news222\"\n" +
                "  }\n" +
                "}";

        String rule1 = "{\n" +
                "  \"detail\": {\n" +
                "    \"$or\" : [\n" +
                "       {\"c-count\": [ { \"numeric\": [ \">\", 0, \"<=\", 5 ] } ]},\n" +
                "       {\"d-count\": [ { \"numeric\": [ \"<\", 10 ] } ]},\n" +
                "       {\"x-limit\": [ { \"numeric\": [ \"=\", 3.018e2 ] } ]}\n" +
                "    ]\n" +
                "  }\n" +
                "}";

        String rule2 = "{\n" +
                "  \"detail\": {\n" +
                "    \"detail-type\": [ \"EC2 Instance State-change Notification\" ],\n" +
                "    \"resources\": [ \"arn:aws:ec2:us-east-1:123456789012:instance/i-000000aaaaaa00000\" ],\n" +
                "    \"info\": {\n" +
                "        \"state-status\": [ \"initializing\", \"running\" ],\n" +
                "        \"state-count\" : { \n" +
                "          \"count\" : [ 100 ]\n" +
                "        }\n" +
                "    },\n" +
                "    \"$or\" : [\n" +
                "       {\"c-count\": [ { \"numeric\": [ \">\", 0, \"<=\", 5 ] } ]},\n" +
                "       {\"d-count\": [ { \"numeric\": [ \"<\", 10 ] } ]},\n" +
                "       {\"x-limit\": [ { \"numeric\": [ \"=\", 3.018e2 ] } ]}\n" +
                "    ]\n" +
                "  },\n" +
                "  \"news\": {\n" +
                "      \"newsProviders\": [ \"p1\", \"p2\" ],\n" +
                "      \"$or\" : [\n" +
                "         {\"newCondition1\": [ \"news111\" ] },\n" +
                "         {\"newCondition2\": [ \"news222\"] }\n" +
                "      ]\n" +
                "  }\n" +
                "}";


        List<Map<String, List<Patterns>>> compiledRules;
        compiledRules = JsonRuleCompiler.compile(rule1);
        assertEquals(3, compiledRules.size());
        compiledRules = JsonRuleCompiler.compile(rule2);
        assertEquals(6, compiledRules.size());

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
    public void testMoreCaseOfOrRelationshipRules() throws Exception {
        final Machine machine = new Machine();

        final JsonNode rules = GenericMachineTest.readAsTree("orRelationshipRules.json");
        assertTrue(rules.isArray());
        assertTrue(machine.isEmpty());

        final int expectedSubRuleSize [] = {2, 4, 3, 4, 2};
        final String expectedCompiledRules [] = {
                "[{metricName=[VP:\"CPUUtilization\" (T:EXACT), VP:\"ReadLatency\" (T:EXACT)]}, {namespace=[VP:\"AWS/EC2\" (T:EXACT), VP:\"AWS/ES\" (T:EXACT)]}]",
                "[{detail.source=[VP:\"aws.cloudwatch\" (T:EXACT)], metricName=[VP:\"CPUUtilization\" (T:EXACT), VP:\"ReadLatency\" (T:EXACT)]}, {namespace=[VP:\"AWS/EC2\" (T:EXACT), VP:\"AWS/ES\" (T:EXACT)], detail.source=[VP:\"aws.cloudwatch\" (T:EXACT)]}, {detail.detail-type=[VP:\"CloudWatch Alarm State Change\" (T:EXACT)], metricName=[VP:\"CPUUtilization\" (T:EXACT), VP:\"ReadLatency\" (T:EXACT)]}, {namespace=[VP:\"AWS/EC2\" (T:EXACT), VP:\"AWS/ES\" (T:EXACT)], detail.detail-type=[VP:\"CloudWatch Alarm State Change\" (T:EXACT)]}]",
                "[{source=[VP:\"aws.cloudwatch\" (T:EXACT)], metricName=[VP:\"CPUUtilization\" (T:EXACT), VP:\"ReadLatency\" (T:EXACT)]}, {namespace=[VP:\"AWS/EC2\" (T:EXACT), VP:\"AWS/ES\" (T:EXACT)], metricType=[VP:\"MetricType\" (T:EXACT)], source=[VP:\"aws.cloudwatch\" (T:EXACT)]}, {source=[VP:\"aws.cloudwatch\" (T:EXACT)], scope=[VP:\"Service\" (T:EXACT)]}]",
                "[{source=[VP:\"aws.cloudwatch\" (T:EXACT)], metricName=[VP:\"CPUUtilization\" (T:EXACT), VP:\"ReadLatency\" (T:EXACT)]}, {namespace=[VP:\"AWS/EC2\" (T:EXACT), VP:\"AWS/ES\" (T:EXACT)], metricType=[VP:\"MetricType\" (T:EXACT)], source=[VP:\"aws.cloudwatch\" (T:EXACT)], metricId=[VP:11C379816DD880 (T:NUMERIC_EQ), VP:1234 (T:EXACT)]}, {namespace=[VP:\"AWS/EC2\" (T:EXACT), VP:\"AWS/ES\" (T:EXACT)], metricType=[VP:\"MetricType\" (T:EXACT)], spaceId=[VP:11C379737B4A00 (T:NUMERIC_EQ), VP:1000 (T:EXACT)], source=[VP:\"aws.cloudwatch\" (T:EXACT)]}, {source=[VP:\"aws.cloudwatch\" (T:EXACT)], scope=[VP:\"Service\" (T:EXACT)]}]",
                "[{detail.state.value=[VP:\"ALARM\" (T:EXACT)], source=[VP:\"aws.cloudwatch\" (T:EXACT)], withConfiguration.metrics.metricStat.metric.namespace=[VP:\"AWS/EC2\" (T:EXACT)]}, {detail.state.value=[VP:\"ALARM\" (T:EXACT)], source=[VP:\"aws.cloudwatch\" (T:EXACT)], withoutConfiguration.metric.name=[VP:\"AWS/Default\" (T:EXACT)]}]"
        };
        int i = 0;
        List<Map<String, List<Patterns>>> compiledRules;

        for (final JsonNode rule : rules) {
            String ruleStr = rule.toString();
            compiledRules = JsonRuleCompiler.compile(ruleStr);
            assertEquals(expectedCompiledRules[i], compiledRules.toString());
            assertEquals(expectedSubRuleSize[i], compiledRules.size());
            machine.addRule("rule-" + i, ruleStr);
            i++;
        }
        assertTrue(!machine.isEmpty());

        // after delete the rule, verify the machine become empty again.
        i = 0;
        for (final JsonNode rule : rules) {
            machine.deleteRule("rule-" + i, rule.toString());
            i++;
        }
        assertTrue(machine.isEmpty());
    }

    @Test
    public void testWrongOrRelationshipRules() throws Exception {
        // enable the "$or" feature
        final Machine machine = new Machine();
        final JsonNode rules = GenericMachineTest.readAsTree("wrongOrRelationshipRules.json");
        assertTrue(rules.isArray());

        int i = 0;

        for (final JsonNode rule : rules) {
            try {
                machine.addRule("rule-"+i, rule.toString());
            } catch (JsonParseException e) {
                i++;
            }
        }
        // verify each rule had thrown JsonParseException.
        assertEquals(rules.size(), i);
    }

    @Test
    public void testOrFieldCanKeepWorkingInLegacyRuleCompiler() throws Exception {
        // Not enable any feature when construct the Machine.
        final Machine machine = new Machine();

        // there are 3 rules in the file, all of them are wrong to use $or tag
        final JsonNode rules = GenericMachineTest.readAsTree("normalRulesWithOrWording.json");
        assertTrue(rules.isArray());
        assertTrue(machine.isEmpty());

        final String expectedCompiledRules [] = {
                "{$or=[0A000000/0A0000FF:false/false (T:NUMERIC_RANGE)]}",
                "{$or.namespace=[VP:\"AWS/EC2\" (T:EXACT), VP:\"AWS/ES\" (T:EXACT)], source=[VP:\"aws.cloudwatch\" (T:EXACT)], $or.metricType=[VP:\"MetricType\" (T:EXACT)]}",
                "{detail.$or=[11C37937E08000/11C379382CCB40:true/false (T:NUMERIC_RANGE), 0A000000/0AFFFFFF:false/false (T:NUMERIC_RANGE)], time=[VP:\"2017-10-02 (T:PREFIX)]}",
                "{detail.$or=[11C37937E08000/11C379382CCB40:true/false (T:NUMERIC_RANGE), 11C37938791680/2386F26FC10000:true/false (T:NUMERIC_RANGE)]}"
        };

        int i = 0;

        // verify the legacy rules with using "$or" as normal field can work correctly with legacy RuleCompiler
        for (final JsonNode rule : rules) {
            machine.addRule("Rule-" + i, rule.toString());
            Map<String, List<Patterns>> r = RuleCompiler.compile(rule.toString());
            assertEquals(expectedCompiledRules[i], r.toString());
            i++;
        }

        // verify each rule had thrown JsonParseException.
        assertEquals(rules.size(), i);
        assertTrue(!machine.isEmpty());

        // verify the legacy rules with using "$or" as normal field can not work with the new JsonRuleCompiler
        i = 0;
        for (final JsonNode rule : rules) {
            try {
                machine.deleteRule("Rule-" + i, rule.toString());
                JsonRuleCompiler.compile(rule.toString());
            } catch (JsonParseException e) {
                i++;
            }
        }
        // verify each rule had thrown JsonParseException.
        assertEquals(rules.size(), i);
        // after delete the rule, verify the machine become empty again.
        assertTrue(machine.isEmpty());
    }

}
