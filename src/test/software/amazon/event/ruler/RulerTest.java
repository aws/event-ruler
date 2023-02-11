package software.amazon.event.ruler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RulerTest {

    private static final String JSON_FROM_RFC = " {\n" +
            "        \"Image\": {\n" +
            "            \"Width\":  800,\n" +
            "            \"Height\": 600,\n" +
            "            \"Title\":  \"View from 15th Floor\",\n" +
            "            \"Thumbnail\": {\n" +
            "                \"Url\":    \"http://www.example.com/image/481989943\",\n" +
            "                \"Height\": 125,\n" +
            "                \"Width\":  100\n" +
            "            },\n" +
            "            \"Animated\" : false,\n" +
            "            \"IDs\": [116, 943, 234, 38793]\n" +
            "          }\n" +
            "      }";
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
            "    \"source-ip\": \"10.0.0.33\",\n" +
            "    \"instance-id\": \"i-000000aaaaaa00000\",\n" +
            "    \"state\": \"running\"\n" +
            "  }\n" +
            "}\n";

    private static final String JSON_FROM_README_WITH_FLAT_FORMAT = "{\n" +
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
            "  \"detail.c.count\": 5,\n" +
            "  \"detail.d.count\": 3,\n" +
            "  \"detail.x.limit\": 301.8,\n" +
            "  \"detail.instance-id\": \"i-000000aaaaaa00000\",\n" +
            "  \"detail.state\": \"running\"\n" +
            "}\n";

    private static final String JSON_WITH_COMPLEX_ARRAYS = "{\n" +
            "  \"employees\":[\n" +
            "    [\n" +
            "      { \"firstName\":\"John\", \"lastName\":\"Doe\" , \"ids\" : [ 1234, 1000, 9999 ] },\n" +
            "      { \"firstName\":\"Anna\", \"lastName\":\"Smith\" }\n" +
            "    ],\n" +
            "    [\n" +
            "      { \"firstName\":\"Peter\", \"lastName\":\"Jones\", \"ids\" : [ ]  }\n" +
            "    ]\n" +
            "  ]\n" +
            "}";

    @Test
    public void WHEN_RulesFromReadmeAreTried_THEN_TheyWork() throws Exception {
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
                        "  \"detail\": {\n" +
                        "    \"source-ip\": [ { \"cidr\": \"10.0.0.0/24\" } ],\n" +
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
                        "    \"source-ip\": [ { \"cidr\": \"10.0.0.0/24\" } ]\n" +
                        "  }\n" +
                        "}",
                "{\n" +
                        "  \"detail\": {\n" +
                        "    \"source-ip\": [ { \"cidr\": \"10.0.0.0/8\" } ]\n" +
                        "  }\n" +
                        "}"
        };

        for (String rule : rules) {
            assertTrue(Ruler.matchesRule(JSON_FROM_README, rule));

            // None flattened rule should not be matched with the flattened event
            // Keep these around until we can make the tests pass for `Ruler.match`
            assertTrue(Ruler.matches(JSON_FROM_README, rule));
            assertFalse(Ruler.matches(JSON_FROM_README_WITH_FLAT_FORMAT, rule));
        }
    }


    @Test
    public void WHEN_WeWriteRulesToMatchVariousFieldCombos_THEN_TheyWork() throws Exception {
        String[] matchingRules = {
                "  {\n" +
                        "    \"Image\": {\n" +
                        "      \"Width\": [ 800 ],\n" +
                        "      \"Thumbnail\": {\n" +
                        "      \"Height\": [ 125, 300 ]\n" +
                        "      }\n" +
                        "    }\n" +
                        "  }",
                "  {\n" +
                        "    \"Image\": {\n" +
                        "      \"Thumbnail\": {\n" +
                        "        \"Url\": [ { \"prefix\": \"http\" } ]\n" +
                        "      }\n" +
                        "    }\n" +
                        "  }",
                "  {\n" +
                        "    \"Image\": {\n" +
                        "      \"Width\":  [ {      \"numeric\": [ \"<\", 1000 ] } ],\n" +
                        "      \"Height\": [ {      \"numeric\": [ \"<=\", 600 ] } ],\n" +
                        "      \"Title\":  [ { \"anything-but\": \"View from 15th Flood\" } ],\n" +
                        "      \"Thumbnail\": {\n" +
                        "        \"Url\":    [ { \"prefix\": \"http\" } ],\n" +
                        "        \"Height\": [ { \"numeric\": [ \">\", 124, \"<\", 126 ] } ],\n" +
                        "        \"Width\":  [ { \"numeric\": [ \">=\", 99.999, \"<=\", 100 ] } ]\n" +
                        "      },\n" +
                        "      \"Animated\": [ false ]\n" +
                        "    }\n" +
                        "}\n"
        };
        String[] nonMatchingRules = {
                "  {\n" +
                        "    \"Image\": {\n" +
                        "      \"Width\":  [ {      \"numeric\": [ \"<\", 800 ] } ]\n" +
                        "    }\n" +
                        "}\n",
                "  {\n" +
                        "    \"Image\": {\n" +
                        "      \"Height\":  [ {      \"numeric\": [ \"<\", 599 ] } ]\n" +
                        "    }\n" +
                        "}\n",
                "  {\n" +
                        "    \"Image\": {\n" +
                        "      \"Title\":  [ { \"anything-but\": \"View from 15th Floor\" } ]\n" +
                        "    }\n" +
                        "}\n",
                "  {\n" +
                        "    \"Image\": {\n" +
                        "      \"Thumbnail\": {\n" +
                        "        \"Url\": [ { \"prefix\": \"https\" } ]\n" +
                        "      }\n" +
                        "    }\n" +
                        "}\n",
                "  {\n" +
                        "    \"Image\": {\n" +
                        "      \"Thumbnail\": {\n" +
                        "        \"Height\": [ { \"numeric\": [ \">\", 124, \"<\", 125 ] } ]\n" +
                        "      }\n" +
                        "    }\n" +
                        "}\n",
                "  {\n" +
                        "    \"Image\": {\n" +
                        "      \"Thumbnail\": {\n" +
                        "        \"Width\": [ { \"numeric\": [ \">=\", 100.00001, \"<=\", 1001] } ]\n" +
                        "      }\n" +
                        "    }\n" +
                        "}\n",
                "  {\n" +
                        "    \"Image\": {\n" +
                        "      \"Animated\": [ true ]\n" +
                        "    }\n" +
                        "}\n"
        };
        for (String rule : matchingRules){
            assertTrue(Ruler.matchesRule(JSON_FROM_RFC, rule));
        }
        for (String rule : nonMatchingRules) {
            assertFalse(Ruler.matchesRule(JSON_FROM_RFC, rule));
        }
    }

    @Test
    public void WHEN_CompareIsPassedComparableNumbers_THEN_ItOrdersThemCorrectly() {
        double[] data = {
                -Constants.FIVE_BILLION, -999999999.99999, -999999999.99, -10000, -0.000002,
                0, 0.000001, 3.8, 3.9, 11, 12, 2.5e4, 999999999.999998, 999999999.999999, Constants.FIVE_BILLION
        };
        for (double d1 : data) {
            for (double d2 : data) {

                byte[] s0 = ComparableNumber.generate(d1).getBytes(StandardCharsets.UTF_8);
                byte[] s1 = ComparableNumber.generate(d2).getBytes(StandardCharsets.UTF_8);
                if (d1 < d2) {
                    assertTrue(Ruler.compare(s0, s1) < 0);
                } else if (d1 == d2) {
                    assertEquals(0, Ruler.compare(s0, s1));
                } else {
                    assertTrue(Ruler.compare(s0, s1) > 0);
                }
            }
        }
    }

    @Test
    public void WHEN_JSONContainsManyElementTypes_THEN_TheyCanAllBeRetrievedByPath() throws Exception {
        JsonNode json = new ObjectMapper().readTree(JSON_FROM_RFC);
        JsonNode n;
        n = Ruler.tryToRetrievePath(json, Collections.singletonList("Image"));
        assertNotNull(n);
        assertTrue(n.isObject());
        n = Ruler.tryToRetrievePath(json, Arrays.asList("Image", "Width"));
        assertNotNull(n);
        assertTrue(n.isNumber());
        assertEquals(800.0, n.asDouble(), 0.001);
        n = Ruler.tryToRetrievePath(json, Arrays.asList("Image","Height"));
        assertNotNull(n);
        assertTrue(n.isNumber());
        assertEquals(600, n.asDouble(), 0.001);
        n = Ruler.tryToRetrievePath(json, Arrays.asList("Image","Title"));
        assertNotNull(n);
        assertTrue(n.isTextual());
        n = Ruler.tryToRetrievePath(json, Arrays.asList("Image","Thumbnail"));
        assertNotNull(n);
        assertTrue(n.isObject());
        n = Ruler.tryToRetrievePath(json, Arrays.asList("Image","Thumbnail","Url"));
        assertNotNull(n);
        assertTrue(n.isTextual());
        n = Ruler.tryToRetrievePath(json, Arrays.asList("Image","Thumbnail","Height"));
        assertNotNull(n);
        assertTrue(n.isNumber());
        assertEquals(125.0, n.asDouble(), 0.001);
        n = Ruler.tryToRetrievePath(json, Arrays.asList("Image","Thumbnail","Width"));
        assertNotNull(n);
        assertTrue(n.isNumber());
        assertEquals(100.0, n.asDouble(), 0.001);
        n = Ruler.tryToRetrievePath(json, Arrays.asList("Image","Animated"));
        assertNotNull(n);
        assertTrue(n.isBoolean());

        n = Ruler.tryToRetrievePath(json, Collections.singletonList("x"));
        assertNull(n);
        n = Ruler.tryToRetrievePath(json, Arrays.asList("Thumbnail","foo"));
        assertNull(n);
    }

    @Test
    public void WHEN_JSONContainsArrays_THEN_RulerNoCompileMatchesWork() throws Exception {
        String[] matchingRules = new String[] {
                "{\n" +
                        "    \"employees\": {\n" +
                        "        \"firstName\": [\"Anna\"]\n" +
                        "    }\n" +
                        "}",
                "{\n" +
                        "    \"employees\": {\n" +
                        "        \"firstName\": [\"John\"],\n" +
                        "        \"ids\": [ 1000 ]\n" +
                        "    }\n" +
                        "}",
                "{\n" +
                        "    \"employees\": {\n" +
                        "        \"firstName\": [\"Anna\"],\n" +
                        "        \"ids\": [ { \"exists\": false  } ]\n" +
                        "    }\n" +
                        "}"
        };
        String[] nonMatchingRules = new String[] {
                "{\n" +
                        "    \"employees\": {\n" +
                        "        \"firstName\": [\"Alice\"]\n" +
                        "    }\n" +
                        "}",
                "{\n" + // See JSON Array Matching in README
                        "    \"employees\": {\n" +
                        "        \"firstName\": [\"Anna\"],\n" +
                        "        \"lastName\": [\"Jones\"]\n" +
                        "    }\n" +
                        "}",
                "{\n" +
                        "    \"employees\": {\n" +
                        "        \"firstName\": [\"Alice\"],\n" +
                        "        \"lastName\": [\"Bob\"]\n" +
                        "    }\n" +
                        "}",
                "{\n" +
                        "    \"a\": [ \"b\" ]\n" +
                        "}",
                "{\n" +
                        "    \"employees\": [ \"b\" ]\n" +
                        "}",
                "{\n" +
                        "    \"employees\": {\n" +
                        "        \"firstName\": [\"Anna\"],\n" +
                        "        \"ids\": [ 1000 ]\n" +
                        "    }\n" +
                        "}",
                "{\n" +
                        "    \"employees\": {\n" +
                        "        \"firstName\": [\"Anna\"],\n" +
                        "        \"ids\": [ { \"exists\": true  } ]\n" +
                        "    }\n" +
                        "}"
        };
        for(String rule : matchingRules) {
            assertTrue(rule, Ruler.matchesRule(JSON_WITH_COMPLEX_ARRAYS, rule));
        }
        for(String rule : nonMatchingRules) {
            assertFalse(rule, Ruler.matchesRule(JSON_WITH_COMPLEX_ARRAYS, rule));
        }
    }

    @Test
    public void WHEN_WeTryToMatchExistsRules_THEN_TheyWork() throws Exception {
        String rule1 = "{ \"a\" : [ { \"exists\": true } ] }";
        String rule2 = "{ \"b\" : [ { \"exists\": false } ] }";
        String rule3 = "{ \"x\" : [ {\"exists\": true} ] }";
        String rule4 = "{ \"x\" : [ {\"exists\": false} ] }";

        String event1 = "{ \"a\" : 1 }";
        String event2 = "{ \"b\" : 2 }";
        String event3 = "{ \"x\" : \"X\" }";

        assertTrue("1/1", Ruler.matchesRule(event1, rule1));
        assertTrue("2/1", Ruler.matchesRule(event1, rule2));
        assertFalse("3/1", Ruler.matchesRule(event1, rule3));
        assertTrue("4/1", Ruler.matchesRule(event1, rule4));
        assertFalse("1/2", Ruler.matchesRule(event2, rule1));
        assertFalse("2/2", Ruler.matchesRule(event2, rule2));
        assertFalse("3/2", Ruler.matchesRule(event2, rule3));
        assertTrue("4/2", Ruler.matchesRule(event2, rule4));
        assertFalse("1/3", Ruler.matchesRule(event3, rule1));
        assertTrue("2/3", Ruler.matchesRule(event3, rule2));
        assertTrue("3/3", Ruler.matchesRule(event3, rule3));
        assertFalse("4/3", Ruler.matchesRule(event3, rule4));
    }

    @Test
    public void WHEN_WeTryReallySimpleRules_THEN_TheyWork() throws Exception {
        String rule1 = "{ \"a\" : [ 1 ] }";
        String rule2 = "{ \"b\" : [ 2 ] }";
        String rule3 = "{ \"x\" : [ \"X\" ] }";

        String event1 = "{ \"a\" : 1 }";
        String event2 = "{ \"b\" : 2 }";
        String event3 = "{ \"x\" : \"X\" }";
        String event4 = "{ \"x\" : true }";


        assertTrue("1/1", Ruler.matchesRule(event1, rule1));
        assertTrue("2/2", Ruler.matchesRule(event2, rule2));
        assertTrue("3/3", Ruler.matchesRule(event3, rule3));
        assertFalse("4/1", Ruler.matchesRule(event4, rule1));
        assertFalse("4/2", Ruler.matchesRule(event4, rule2));
        assertFalse("4/3", Ruler.matchesRule(event4, rule3));
    }

    @Test
    public void WHEN_WeTryAnythingButRules_THEN_Theywork() throws Exception {
        String rule = "{\n" +
                "\"a\": [ { \"anything-but\": [ \"dad0\",\"dad1\",\"dad2\" ] } ],\n" +
                "\"b\": [ { \"anything-but\": [ 111, 222, 333 ] } ],\n" +
                "\"c\": [ { \"anything-but\": \"zdd\" } ],\n" +
                "\"d\": [ { \"anything-but\": 444 } ],\n" +
                "\"z\": [ { \"numeric\": [ \">\", 0, \"<\", 1 ] } ],\n" +
                "\"w\": [ { \"anything-but\": { \"prefix\": \"zax\" } } ],\n" +
                "\"n\": [ { \"anything-but\": { \"suffix\": \"ing\" } } ],\n" +
                "\"o\": [ { \"anything-but\": {\"equals-ignore-case\": \"CamelCase\" } } ],\n" +
                "\"p\": [ { \"anything-but\": {\"equals-ignore-case\": [\"CamelCase\", \"AbC\"] } } ]\n" +
                "}";

        String[] events = {
                "{" +
                        "    \"a\": \"child1\",\n" +
                        "    \"b\": \"444\",\n" +
                        "    \"c\": \"child1\",\n" +
                        "    \"d\": 123,\n" +
                        "    \"w\": \"xaz\",\n" +
                        "    \"z\": 0.001, \n" +
                        "    \"n\": \"nomatch\", \n" +
                        "    \"o\": \"nomatch\", \n" +
                        "    \"p\": \"nomatch\" \n" +
                        "}\n",
                "{" +
                        "    \"a\": \"dad789\",\n" +
                        "    \"b\": 789,\n" +
                        "    \"c\": \"zdd\",\n" +
                        "    \"d\": 123,\n" +
                        "    \"w\": \"xaz\",\n" +
                        "    \"z\": 0.001, \n" +
                        "    \"n\": \"nomatch\", \n" +
                        "    \"o\": \"nomatch\", \n" +
                        "    \"p\": \"nomatch\" \n" +
                        "}\n",
                "{" +
                        "    \"a\": \"dad789\",\n" +
                        "    \"b\": 789,\n" +
                        "    \"c\": \"child1\",\n" +
                        "    \"d\": 123,\n" +
                        "    \"w\": \"xaz\",\n" +
                        "    \"z\": 1.01, \n" +
                        "    \"n\": \"nomatch\", \n" +
                        "    \"o\": \"nomatch\", \n" +
                        "    \"p\": \"nomatch\" \n" +
                        "}\n",
                "{" +
                        "    \"a\": \"dad1\",\n" +
                        "    \"b\": 345,\n" +
                        "    \"c\": \"child1\",\n" +
                        "    \"d\": 123,\n" +
                        "    \"w\": \"xaz\",\n" +
                        "    \"z\": 0.001, \n" +
                        "    \"n\": \"nomatch\", \n" +
                        "    \"o\": \"nomatch\", \n" +
                        "    \"p\": \"nomatch\" \n" +
                        "}\n",
                "{" +
                        "    \"a\": \"abc\",\n" +
                        "    \"b\": 111,\n" +
                        "    \"c\": \"child1\",\n" +
                        "    \"d\": 123,\n" +
                        "    \"w\": \"xaz\",\n" +
                        "    \"z\": 0.001, \n" +
                        "    \"n\": \"nomatch\", \n" +
                        "    \"o\": \"nomatch\", \n" +
                        "    \"p\": \"nomatch\" \n" +
                        "}\n",
                "{" +
                        "    \"a\": \"dad1\",\n" +
                        "    \"b\": 333,\n" +
                        "    \"c\": \"child1\",\n" +
                        "    \"d\": 123,\n" +
                        "    \"w\": \"xaz\",\n" +
                        "    \"z\": 0.001, \n" +
                        "    \"n\": \"nomatch\", \n" +
                        "    \"o\": \"nomatch\", \n" +
                        "    \"p\": \"nomatch\" \n" +
                        "}\n",
                "{" +
                        "    \"a\": \"abc\",\n" +
                        "    \"b\": 0,\n" +
                        "    \"c\": \"child1\",\n" +
                        "    \"d\": 444,\n" +
                        "    \"w\": \"xaz\",\n" +
                        "    \"z\": 0.999999, \n" +
                        "    \"n\": \"nomatch\", \n" +
                        "    \"o\": \"nomatch\", \n" +
                        "    \"p\": \"nomatch\" \n" +
                        "}\n",
                "{" +
                        "    \"a\": \"child1\",\n" +
                        "    \"b\": \"444\",\n" +
                        "    \"c\": \"child1\",\n" +
                        "    \"d\": 123,\n" +
                        "    \"w\": \"zaxonie\",\n" +
                        "    \"z\": 0.001, \n" +
                        "    \"n\": \"nomatch\", \n" +
                        "    \"o\": \"nomatch\", \n" +
                        "    \"p\": \"nomatch\" \n" +
                        "}\n",
                "{" +
                        "    \"a\": \"child1\",\n" +
                        "    \"b\": \"444\",\n" +
                        "    \"c\": \"child1\",\n" +
                        "    \"d\": 123,\n" +
                        "    \"w\": \"xaz\",\n" +
                        "    \"z\": 0.001, \n" +
                        "    \"n\": \"matching\", \n" +
                        "    \"o\": \"nomatch\", \n" +
                        "    \"p\": \"nomatch\" \n" +
                        "}\n",
                "{" +
                        "    \"a\": \"child1\",\n" +
                        "    \"b\": \"444\",\n" +
                        "    \"c\": \"child1\",\n" +
                        "    \"d\": 123,\n" +
                        "    \"w\": \"xaz\",\n" +
                        "    \"z\": 0.001, \n" +
                        "    \"n\": \"nomatch\", \n" +
                        "    \"o\": \"camelcase\", \n" +
                        "    \"p\": \"nomatch\" \n" +
                        "}\n",
                "{" +
                        "    \"a\": \"child1\",\n" +
                        "    \"b\": \"444\",\n" +
                        "    \"c\": \"child1\",\n" +
                        "    \"d\": 123,\n" +
                        "    \"w\": \"xaz\",\n" +
                        "    \"z\": 0.001, \n" +
                        "    \"n\": \"nomatch\", \n" +
                        "    \"o\": \"nomatch\", \n" +
                        "    \"p\": \"abc\" \n" +
                        "}\n",
        };

        boolean[] result = {true, false, false, false, false, false, false, false, false, false, false };

        for (int i = 0; i< events.length; i++) {
            assertEquals(events[i], result[i], Ruler.matchesRule(events[i], rule));
        }
    }

    @Test
    public void WHEN_WeTryEqualsIgnoreCaseRules_THEN_TheyWork() throws Exception {
        String rule = "{\n" +
                "\"a\": [ { \"equals-ignore-case\": \"aBc\" } ],\n" +
                "\"b\": [ { \"equals-ignore-case\": \"Def\" } ],\n" +
                "\"c\": [ { \"equals-ignore-case\": \"xyZ\" } ]\n" +
                "}";

        String[] events = {
                "{" +
                        "    \"a\": \"ABC\",\n" +
                        "    \"b\": \"defx\",\n" +
                        "    \"c\": \"xYz\",\n" +
                        "    \"d\": \"rst\"\n" +
                        "}\n",
                "{" +
                        "    \"a\": \"ABC\",\n" +
                        "    \"c\": \"xYz\",\n" +
                        "    \"d\": \"rst\"\n" +
                        "}\n",
                "{" +
                        "    \"a\": \"ABC\",\n" +
                        "    \"b\": \"xYz\",\n" +
                        "    \"c\": \"def\",\n" +
                        "    \"d\": \"rst\"\n" +
                        "}\n",
                "{" +
                        "    \"a\": \"ABC\",\n" +
                        "    \"b\": \"def\",\n" +
                        "    \"c\": \"xYz\",\n" +
                        "    \"d\": \"rst\"\n" +
                        "}\n"
        };

        boolean[] result = {false, false, false, true };

        for (int i = 0; i< events.length; i++) {
            assertEquals(events[i], result[i], Ruler.matchesRule(events[i], rule));
        }
    }

    @Test
    public void WHEN_WeTryWildcardRules_THEN_TheyWork() throws Exception {
        String rule = "{\n" +
                "\"a\": [ { \"wildcard\": \"*bc\" } ],\n" +
                "\"b\": [ { \"wildcard\": \"d*f\" } ],\n" +
                "\"c\": [ { \"wildcard\": \"xy*\" } ],\n" +
                "\"d\": [ { \"wildcard\": \"xy*\" } ]\n" +
                "}";

        String[] events = {
                "{" +
                        "    \"a\": \"abcbc\",\n" +
                        "    \"b\": \"deeeefx\",\n" +
                        "    \"c\": \"xy\",\n" +
                        "    \"d\": \"xyzzz\"\n" +
                        "}\n",
                "{" +
                        "    \"a\": \"abcbc\",\n" +
                        "    \"b\": \"deeeef\",\n" +
                        "    \"d\": \"xyzzz\"\n" +
                        "}\n",
                "{" +
                        "    \"a\": \"abcbc\",\n" +
                        "    \"b\": \"xy\",\n" +
                        "    \"c\": \"deeeef\",\n" +
                        "    \"d\": \"xyzzz\"\n" +
                        "}\n",
                "{" +
                        "    \"a\": \"abcbc\",\n" +
                        "    \"b\": \"deeeef\",\n" +
                        "    \"c\": \"xy\",\n" +
                        "    \"d\": \"xyzzz\"\n" +
                        "}\n"
        };

        boolean[] result = {false, false, false, true };

        for (int i = 0; i< events.length; i++) {
            assertEquals(events[i], result[i], Ruler.matchesRule(events[i], rule));
        }
    }
}
