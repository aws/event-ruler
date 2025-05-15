package software.amazon.event.ruler;

import com.fasterxml.jackson.core.JsonParseException;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RuleCompilerTest {

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
    public void testPrefixEqualsIgnoreCaseCompile() {
        String json = "{\"a\": [ { \"prefix\": { \"equals-ignore-case\": \"child\" } } ] }";
        assertNull("Good prefix equals-ignore-case should parse", RuleCompiler.check(json));
    }

    @Test
    public void testSuffixEqualsIgnoreCaseCompile() {
        String json = "{\"a\": [ { \"suffix\": { \"equals-ignore-case\": \"child\" } } ] }";
        assertNull("Good suffix equals-ignore-case should parse", RuleCompiler.check(json));
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
        assertNotNull("Top level must be an object", RuleCompiler.check(j));

        InputStream is = new ByteArrayInputStream(j.getBytes(StandardCharsets.UTF_8));
        assertNotNull("Top level must be an object (bytes)", RuleCompiler.check(is));

        j = "{\"a\":1}";
        assertNotNull("Values must be in an array", RuleCompiler.check(j.getBytes(StandardCharsets.UTF_8)));

        j = "{\"a\":[ { \"x\":2 } ]}";
        assertNotNull("Array values must be primitives", RuleCompiler.check(new StringReader(j)));

        j = "{ \"foo\": {}}";
        assertNotNull("Objects must not be empty", RuleCompiler.check(new StringReader(j)));

        j = "{ \"foo\": []}";
        assertNotNull("Arrays must not be empty", RuleCompiler.check(new StringReader(j)));

        j = "{\"a\":[1]}";
        assertNull(RuleCompiler.check(j));

        Map<String, List<Patterns>> m = RuleCompiler.compile(new ByteArrayInputStream(j.getBytes(StandardCharsets.UTF_8)));
        List<Patterns> l = m.get("a");
        assertEquals(2, l.size());
        for (Patterns p : l) {
            ValuePatterns vp = (ValuePatterns) p;
            if (p.type() == MatchType.NUMERIC_EQ) {
                assertEquals(ComparableNumber.generate("1.0"), vp.pattern());
            } else {
                assertEquals("1", vp.pattern());
            }
        }

        j = "{\"a\": [ { \"prefix\": \"child\" } ] }";
        assertNull("Good prefix should parse", RuleCompiler.check(j));

        j = "{\"a\": [ { \"suffix\": \"child\" } ] }";
        assertNull("Good suffix should parse", RuleCompiler.check(j));

        j = "{\"a\": [ { \"anything-but\": \"child\" } ] }";
        assertNull("Good anything-but should parse", RuleCompiler.check(j));

        j = "{\"a\": [ { \"anything-but\": [\"child0\",\"child1\",\"child2\"] } ] }";
        assertNull("Good anything-but should parse", RuleCompiler.check(j));

        j = "{\"a\": [ { \"anything-but\": [111,222,333] } ] }";
        assertNull("Good anything-but should parse", RuleCompiler.check(j));

        j = "{\"a\": [ { \"anything-but\": \"child0\" } ] }";
        assertNull("Good anything-but should parse", RuleCompiler.check(j));

        j = "{\"a\": [ { \"anything-but\": 111 } ] }";
        assertNull("Good anything-but should parse", RuleCompiler.check(j));

        j = "{\"a\": [ { \"anything-but\": { \"prefix\": \"foo\" } } ] }";
        assertNull("Good anything-but should parse", RuleCompiler.check(j));

        j = "{\"a\": [ { \"anything-but\": { \"suffix\": \"foo\" } } ] }";
        assertNull("Good anything-but should parse", RuleCompiler.check(j));

        j = "{\"a\": [ { \"anything-but\": {\"equals-ignore-case\": \"rule\" } } ] }";
        assertNull("Good anything-but/ignore-case should parse", JsonRuleCompiler.check(j));

        j = "{\"a\": [ { \"anything-but\": {\"equals-ignore-case\": [\"abc\", \"123\"] } } ] }";
        assertNull("Good anything-but/ignore-case should parse", JsonRuleCompiler.check(j));

        j = "{\"a\": [ { \"exactly\": \"child\" } ] }";
        assertNull("Good exact-match should parse", RuleCompiler.check(j));

        j = "{\"a\": [ { \"exists\": true } ] }";
        assertNull("Good exists true should parse", RuleCompiler.check(j));

        j = "{\"a\": [ { \"exists\": false } ] }";
        assertNull("Good exists false should parse", RuleCompiler.check(j));

        j = "{\"a\": [ { \"cidr\": \"10.0.0.0/8\" } ] }";
        assertNull("Good CIDR should parse", RuleCompiler.check(j));

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
                "{\"a\": [ { \"anything-but\": {\"equals-ignore-case\": [1, 2 3] } } ] }",
                "{\"a\": [ { \"anything-but\": {\"equals-ignore-case\": [1, 2, 3] } } ] }", // no numbers allowed
                "{\"a\": [ { \"equals-ignore-case\": 5 } ] }",
                "{\"a\": [ { \"equals-ignore-case\": [ \"abc\" ] } ] }",
                "{\"a\": [ { \"prefix\": { \"invalid-expression\": [ \"abc\" ] } } ] }",
                "{\"a\": [ { \"prefix\": { \"equals-ignore-case\": 5 } } ] }",
                "{\"a\": [ { \"prefix\": { \"equals-ignore-case\": [ \"abc\" ] } } ] }",
                "{\"a\": [ { \"prefix\": { \"equals-ignore-case\": \"abc\", \"test\": \"def\" } } ] }",
                "{\"a\": [ { \"prefix\": { \"equals-ignore-case\": \"abc\" }, \"test\": \"def\" } ] }",
                "{\"a\": [ { \"prefix\": { \"equals-ignore-case\": [ 1, 2 3 ] } } ] }",
                "{\"a\": [ { \"suffix\": { \"invalid-expression\": [ \"abc\" ] } } ] }",
                "{\"a\": [ { \"suffix\": { \"equals-ignore-case\": 5 } } ] }",
                "{\"a\": [ { \"suffix\": { \"equals-ignore-case\": [ \"abc\" ] } } ] }",
                "{\"a\": [ { \"suffix\": { \"equals-ignore-case\": \"abc\", \"test\": \"def\" } } ] }",
                "{\"a\": [ { \"suffix\": { \"equals-ignore-case\": \"abc\" }, \"test\": \"def\" } ] }",
                "{\"a\": [ { \"suffix\": { \"equals-ignore-case\": [ 1, 2 3 ] } } ] }",
                "{\"a\": [ { \"wildcard\": 5 } ] }",
                "{\"a\": [ { \"wildcard\": [ \"abc\" ] } ] }"
        };
        for (String badPattern : badPatternTypes) {
            assertNotNull("bad pattern shouldn't parse", RuleCompiler.check(badPattern));
        }

        j = "{\n"
                + "  \"resources\": [\n"
                + "    \"r1\",\n"
                + "    \"r2\"\n"
                + "  ]\n"
                + "}";
        m = RuleCompiler.compile(j);
        l = m.get("resources");
        assertEquals(2, l.size());
        ValuePatterns vp1 = (ValuePatterns) l.get(0);
        ValuePatterns vp2 = (ValuePatterns) l.get(1);
        assertEquals("\"r1\"", vp1.pattern());
        assertEquals("\"r2\"", vp2.pattern());

        /*
        {
          "detail-getType": [ "ec2/spot-bid-matched" ],
          "detail" : {
            "state": [ "in-service" ]
           }
         }
         */
        j = "{\n"
                + "  \"detail-getType\": [ \"ec2/spot-bid-matched\" ],\n"
                + "  \"detail\" : { \n"
                + "   \"state\": [ \"in-service\", \"dead\" ]\n"
                + "  }\n"
                + "}\n";
        m = RuleCompiler.compile(j);
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
    public void testFlattenRule() throws Exception {
        final String rule = "{" +
                "\"a1\": [123, \"child\", {\"numeric\": [\">\", 0, \"<=\", 5]}]," +
                "\"a2\": { \"b\": {" +
                "\"c1\": [" +
                "{ \"suffix\": \"child\" }," +
                "{ \"anything-but\": [111,222,333]}," +
                "{ \"anything-but\": { \"prefix\": \"foo\"}}," +
                "{ \"anything-but\": { \"suffix\": \"ing\"}}," +
                "{ \"anything-but\": {\"equals-ignore-case\": \"def\" } }" +
                "]," +
                "\"c2\": { \"d\": { \"e\": [" +
                "{ \"exactly\": \"child\" }," +
                "{ \"exists\": true }," +
                "{ \"cidr\": \"10.0.0.0/8\" }" +
                "]}}}" +
                "}}";

        Map<List<String>, List<Patterns>> expected = new HashMap<>();
        expected.put(Collections.singletonList("a1"), Arrays.asList(
                Patterns.numericEquals("123"),
                Patterns.exactMatch("123"),
                Patterns.exactMatch("\"child\""),
                Range.between("0", true, "5", false)
        ));
        expected.put(Arrays.asList("a2", "b", "c1"), Arrays.asList(
                Patterns.suffixMatch("child\""),
                Patterns.anythingButNumbersMatch(Stream.of(111, 222, 333).map(d -> Double.toString(d)).collect(Collectors.toSet())),
                Patterns.anythingButPrefix("\"foo"),
                Patterns.anythingButSuffix("ing\""),
                Patterns.anythingButIgnoreCaseMatch("\"def\"")
        ));
        expected.put(Arrays.asList("a2", "b", "c2", "d", "e"), Arrays.asList(
                Patterns.exactMatch("\"child\""),
                Patterns.existencePatterns(),
                CIDR.cidr("10.0.0.0/8")
        ));

        assertEquals(expected, RuleCompiler.ListBasedRuleCompiler.flattenRule(rule));
    }

    @Test
    public void testNumericExpressions() {
        String[] goods = {
                "[\"=\", 3.8]", "[\"=\", 0.000033]", "[\"=\", -4e-6]", "[\"=\", 55555]", "[\"=\", 1E-320]",
                "[\"<\", 3.8]", "[\"<\", 0.000033]", "[\"<\", -4e-6]", "[\"<\", 55555]", "[\"<\", 1E-320]",
                "[\">\", 3.8]", "[\">\", 0.000033]", "[\">\", -4e-6]", "[\">\", 55555]", "[\">\", 1E-320]",
                "[\"<=\", 3.8]", "[\"<=\", 0.000033]", "[\"<=\", -4e-6]", "[\"<=\", 55555]", "[\"<=\", 1E-320]",
                "[\">=\", 3.8]", "[\">=\", 0.000033]", "[\">=\", -4e-6]", "[\">=\", 55555]", "[\">=\", 1E-320]",
                "[\">\", 0, \"<\", 1]", "[\">=\", 0, \"<\", 1]",
                "[\">\", 0, \"<=\", 1]", "[\">=\", 0, \"<=\", 1]"
        };

        String[] bads = new String[]{
                "[\"=\", true]", "[\"=\", 2.0e422]", "[\"=\", \"-4e-6\"]", "[\"=\"]", "[\"=\", 1E309]", "[\"=\", 1E-325]",
                "[\"<\", true]", "[\"<\", 2.0e422]", "[\"<\", \"-4e-6\"]", "[\"<\"]", "[\"=\", 1E309]", "[\"=\", 1E-325]",
                "[\">=\", true]", "[\">=\", 2.0e422]", "[\">=\", \"-4e-6\"]", "[\">=\"]", "[\"=\", 1E309]", "[\"=\", 1E-325]",
                "[\"<=\", true]", "[\"<=\", 2.0e422]", "[\"<=\", \"-4e-6\"]", "[\"<=\"]", "[\"=\", 1E309]", "[\"=\", 1E-325]",
                "[\"<>\", 1, \">\", 0]", "[\"==\", 1, \">\", 0]",
                "[\"<\", 1, \">\", 0]", "[\">\", 1, \"<\", 1]",
                "[\">\", 30, \"<\", 1]", "[\">\", 1, \"<\", 30, false]"
        };

        for (String good : goods) {
            String json = "{\"x\": [{\"numeric\": " + good + "}]}";
            String m = RuleCompiler.check(json);
            assertNull(json + " => " + m, m);
        }

        for (String bad : bads) {
            String json = "{\"x\": [{\"numeric\": " + bad + "}]}";
            String m = RuleCompiler.check(json);
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
            String m = RuleCompiler.check(json);
            assertNull(json + " => " + m, m);
        }

        for (String bad : bads) {
            String json = "{\"x\": [{\"exists\": " + bad + "}]}";
            String m = RuleCompiler.check(json);
            assertNotNull("Bad: " + json, m);
        }
    }

    @Test
    public void testMachineWithNoRules() {
        Machine machine = new Machine();
        List<String> found = machine.rulesForEvent(Arrays.asList("foo", "bar"));
        assertNotNull(found);
        assertEquals(0, found.size());
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
    public void testEndtoEndinParallel() throws Exception {

        int numRules = 1000; // Number of matching rules

        String rule1 = "{\n"
                + "  \"source\":[\"aws.events\"],\n"
                + "  \"resources\": [\n"
                + "    \"arn:aws:events:ap-northeast-1:123456789012:event\"\n"
                + "  ],\n"
                + "   \"detail-getType\":[\"Scheduled Event\"]\n"
                + "}\n";

        String rule2 = "{\n"
                + "  \"resources\": [\n"
                + "    \"arn:aws:ec2:us-east-1::image/ami-1a2b3c4d\",\n"
                + "    \"arn:aws:ec2:us-east-1::image/ami-98765432\"\n"
                + "  ],\n"
                + "  \"detail.state\": [ \"halted\", \"pending\"]\n"
                + "}\n";

        String[] event = {
                "account", "\"123456789012\"",
                "detail-getType", "\"Scheduled Event\"",
                "detail.instanceId", "arn:aws:events:ap-northeast-1:123456789012:event\"",
                "detail.spotInstanceRequestId", "\"eaa472d8-8422-a9bb-8888-4919fd99310\"",
                "detail.state", "\"in-service\"",
                "detail.requestParameters.zone", "\"us-east-1a\"",
                "id", "\"cdc73f9d-aea9-11e3-9d5a-835b769c0d9c\"",
                "region", "\"us-west-2\"",
                "resources", "\"arn:aws:events:ap-northeast-1:123456789012:event\"",
                "source", "\"aws.events\"",
                "tags", "\"2015Q1",
                "tags", "\"Euro-fleet\"",
                "time", "\"2014-03-18T14:30:07Z\"",
                "version", "\"0\"",
        };

        List<String> rules = new ArrayList<>();

        // Add all rules to the machine
        for (int i = 0; i < numRules; i++) {

            rules.add(rule1);
        }

        rules.add(rule2);

        List<String[]> events = new ArrayList<>();

        for (int i = 0; i < numRules; i++) {

            events.add(event);
        }

        multiThreadedTestHelper(rules, events, numRules);

    }

    @Test
    public void testEndToEndInParallelWithDifferentEvents() throws Exception {

        int numRules = 1000; // Number of matching rules

        String rule1 = "{\n"
                + "  \"source\":[\"aws.events\"],\n"
                + "  \"resources\": [\n"
                + "    \"arn:aws:events:ap-northeast-1:123456789012:event-%d\"\n"
                + "  ],\n"
                + "   \"detail-getType\":[\"Scheduled Event\"]\n"
                + "}\n";

        String rule2 = "{\n"
                + "  \"resources\": [\n"
                + "    \"arn:aws:ec2:us-east-1::image/ami-1a2b3c4d\",\n"
                + "    \"arn:aws:ec2:us-east-1::image/ami-98765432\"\n"
                + "  ],\n"
                + "  \"detail.state\": [ \"halted\", \"pending\"]\n"
                + "}\n";

        String[] event = {
                "account", "\"123456789012\"",
                "detail-getType", "\"Scheduled Event\"",
                "detail.instanceId", "arn:aws:events:ap-northeast-1:123456789012:event\"",
                "detail.spotInstanceRequestId", "\"eaa472d8-8422-a9bb-8888-4919fd99310\"",
                "detail.state", "\"in-service\"",
                "detail.requestParameters.zone", "\"us-east-1a\"",
                "id", "\"cdc73f9d-aea9-11e3-9d5a-835b769c0d9c\"",
                "region", "\"us-west-2\"",
                "resources", "\"arn:aws:events:ap-northeast-1:123456789012:event-%d\"",
                "source", "\"aws.events\"",
                "tags", "\"2015Q1",
                "tags", "\"Euro-fleet\"",
                "time", "\"2014-03-18T14:30:07Z\"",
                "version", "\"0\"",
        };

        List<String> rules = new ArrayList<>();

        // Add all rules to the machine
        for (int i = 0; i < numRules; i++) {

            rules.add(String.format(rule1, i));
        }

        rules.add(rule2);

        List<String[]> events = new ArrayList<>();

        for (int i = 0; i < numRules; i++) {

            event[17] = String.format(event[17], i);
            events.add(event);
        }

        multiThreadedTestHelper(rules, events, 1);

    }

    @Test
    public void testWildcardConsecutiveWildcards() throws IOException {
        try {
            RuleCompiler.compile("{\"key\": [{\"wildcard\": \"abc**def\"}]}");
            fail("Expected JSONParseException");
        } catch (JsonParseException e) {
            assertEquals("Consecutive wildcard characters at pos 4\n" +
                    " at [Source: (String)\"{\"key\": [{\"wildcard\": \"abc**def\"}]}\"; line: 1, column: 33]",
                    e.getMessage());
        }
    }

    @Test
    public void testWildcardInvalidEscapeCharacter() throws IOException {
        try {
            RuleCompiler.compile("{\"key\": [{\"wildcard\": \"a*c\\def\"}]}");
            fail("Expected JSONParseException");
        } catch (JsonParseException e) {
            assertEquals("Unrecognized character escape 'd' (code 100)\n" +
                    " at [Source: (String)\"{\"key\": [{\"wildcard\": \"a*c\\def\"}]}\"; line: 1, column: 28]",
                    e.getMessage());
        }
    }

    @Test
    public void testWithRuleOverride() throws Exception {
        // Example rule taken from: https://github.com/aws/event-ruler/issues/22
        String jsonSimpleRule2 = "{\n" +
            "  \"source\": [\"aws.sns\"],\n" +
            "  \"detail-type\": [\"AWS API Call via CloudTrail\"],\n" +
            "  \"detail\": {\n" +
            "    \"eventSource\": [\"s3.amazonaws.com\"],\n" +
            "    \"eventSource\": [\"sns.amazonaws.com\"]\n" +
            "  }\n" +
            "}";
        assertNull("", RuleCompiler.check(jsonSimpleRule2));
        assertEquals("Path `detail.eventSource` cannot be allowed multiple times\n at [Source: (String)\"{\n" +
                "  \"source\": [\"aws.sns\"],\n" +
                "  \"detail-type\": [\"AWS API Call via CloudTrail\"],\n" +
                "  \"detail\": {\n" +
                "    \"eventSource\": [\"s3.amazonaws.com\"],\n" +
                "    \"eventSource\": [\"sns.amazonaws.com\"]\n" +
                "  }\n" +
                "}\"; line: 6, column: 41]", RuleCompiler.check(jsonSimpleRule2, false));
    }

    private void multiThreadedTestHelper(List<String> rules,
                                         List<String[]> events, int numMatchesPerEvent) throws Exception {

        int numTries = 30; // Run the test several times as the race condition may be intermittent
        int numThreads = 1000;

        for (int j = 0; j < numTries; j++) {

            Machine machine = new Machine();
            CountDownLatch countDownLatch = new CountDownLatch(1);
            EventMatcherThreadPool eventMatcherThreadPool = new EventMatcherThreadPool(numThreads, countDownLatch);

            int i = 0;

            // Add all rules to the machine
            for (String rule : rules) {

                String ruleName = "rule" + i++;

                machine.addRule(ruleName, rule);
            }

            List<Future<List<String>>> futures =
                    events.stream().map(event -> eventMatcherThreadPool.addEventsToMatch(machine, event)).collect(Collectors.toList());

            countDownLatch.countDown();

            for (Future<List<String>> f : futures) {

                if (f.get().size() != numMatchesPerEvent) {
                    fail();
                }
            }

            eventMatcherThreadPool.close();
        }
    }

    static class EventMatcherThreadPool {

        private final ExecutorService executorService;
        private final CountDownLatch countDownLatch;

        EventMatcherThreadPool(int numThreads, CountDownLatch latch) {

            executorService = Executors.newFixedThreadPool(numThreads);
            countDownLatch = latch;
        }

        Future<List<String>> addEventsToMatch(Machine m, String[] event) {


            return executorService.submit(new MachineRunner(m, event, countDownLatch));

        }

        void close() {
            this.executorService.shutdown();
        }

        private static class MachineRunner implements Callable<List<String>> {

            private final Machine machine;
            private final String[] events;
            private final CountDownLatch latch;

            MachineRunner(Machine machine, String[] events, CountDownLatch latch) {
                this.machine = machine;
                this.events = events;
                this.latch = latch;
            }

            @Override
            public List<String> call() {

                try {
                    latch.await();
                } catch (InterruptedException ie) {
                    //
                } //Latch released

                return machine.rulesForEvent(events);

            }
        }

    }
}
