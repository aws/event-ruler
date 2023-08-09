package software.amazon.event.ruler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class EventTest {

    private
    String[] catchAllRules = {
            "{\n" +
                    "  \"Image\": {\n" +
                    "    \"Width\": [ 200 ],\n" +
                    "    \"Height\": [ 200 ],\n" +
                    "    \"Title\": [ \"Whatever\" ],\n" +
                    "    \"Thumbnail\": {\n" +
                    "      \"Url\": [ \"https://foo.com\" ],\n" +
                    "      \"Height\": [ 125 ],\n" +
                    "      \"Width\": [ 225 ]\n" +
                    "    },\n" +
                    "    \"Animated\": [ true ],\n" +
                    "    \"IDs\": [2, 4, 6]\n" +
                    "  }\n" +
                    "}"
    };

    private String[] jsonFromRFC = {
            " {\n" +
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
                    "      }",
            "{\n" +
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
                    "    \"c-count\": 5,\n" +
                    "    \"d-count\": 3,\n" +
                    "    \"x-limit\": 301.8,\n" +
                    "    \"instance-id\": \"i-000000aaaaaa00000\",\n" +
                    "    \"state\": \"running\"\n" +
                    "  }\n" +
                    "}"
    };


    @Test
    public void WHEN_EventIsConstructed_THEN_SimpleArraysAreHandledCorrectly() throws Exception {
        Machine m = new Machine();
        m.addRule("r1", catchAllRules[0]);
        Event e = new Event(jsonFromRFC[0], m);
        String[] wantKeys = {
          "Image.Animated", "Image.Height", "Image.IDs", "Image.IDs", "Image.IDs", "Image.IDs",
                "Image.Thumbnail.Height", "Image.Thumbnail.Url", "Image.Thumbnail.Width", "Image.Title", "Image.Width"
        };
        String[] wantVals = {
                "false", "600", null, null, null, null, "125", "\"http://www.example.com/image/481989943\"",
                "100", "\"View from 15th Floor\"", "800"
        };
        checkFlattening(e, wantKeys, wantVals);
    }

    @Test
    public void WHEN_EventIsConstructed_THEN_HeterogeneousArraysAreHandled() throws Exception {
        String hetero = "{\n" +
                "  \"lines\": [\n" +
                "    { \n" +
                "      \"intensities\": [\n" +
                "        [ 0.5 ]\n" +
                "      ],\n" +
                "      \"points\": [\n" +
                "        [\n" +
                "          [\n" +
                "           483.3474497412421, 287.48116291799363,  {\"pp\" : \"index0\"}\n" +
                "          ],\n" +
                "          [\n" +
                "            {\"pp\" : \"index1\"}, 489.9999497412421, 299.99996291799363\n" +
                "          ]\n" +
                "        ]\n" +
                "      ]\n" +
                "    }\n" +
                "  ]\n" +
                "}";
        String rule = "{\n" +
                "  \"lines\": \n" +
                "    { \n" +
                "      \"points\": [287.48116291799363],\n" +
                "      \"points\": { \n" +
                "         \"pp\" : [\"index0\"]\n" +
                "      }\n" +
                "    }\n" +
                "}";

        String[] wantedFieldNames = {
                "lines.points", "lines.points", "lines.points", "lines.points", "lines.points.pp", "lines.points.pp"
        };
        String[] wantedArrayMemberships = {
                "0[0] 2[0] 1[0] ", "0[0] 2[0] 1[0] ", "0[0] 2[1] 1[0] ", "0[0] 2[1] 1[0] ", "0[0] 2[0] 3[2] 1[0] ",
                "0[0] 4[0] 2[1] 1[0] "
        };

        Machine m = new Machine();
        m.addRule("r", rule);
        Event e = new Event(hetero, m);
        for (int i = 0; i < e.fields.size(); i++) {
            assertEquals("testcase #" + i, wantedFieldNames[i], e.fields.get(i).name);
            assertEquals("testcase #" + i, wantedArrayMemberships[i], e.fields.get(i).arrayMembership.toString());
        }
        List<String> res = m.rulesForJSONEvent(hetero);
        assertEquals(1, res.size());
        assertEquals("r", res.get(0));
    }

    @Test
    public void WHEN_EventsIsConstructed_THEN_NestedArraysAreHandled() throws Exception {
        String songs = "{\n" +
                "  \"Songs\": [\n" +
                "    {\n" +
                "      \"Name\": \"Norwegian Wood\",\n" +
                "      \"Writers\": [\n" +
                "\t{\n" +
                "\t  \"First\": \"John\",\n" +
                "\t  \"Last\": \"Lennon\"\n" +
                "\t},\n" +
                "\t{\n" +
                "\t  \"First\": \"Paul\",\n" +
                "\t  \"Last\": \"McCartney\"\n" +
                "\t}\n" +
                "      ]\n" +
                "    },\n" +
                "    {\n" +
                "      \"Name\": \"Paint It Black\",\n" +
                "      \"Writers\": [\n" +
                "\t{\n" +
                "\t  \"First\": \"Keith\",\n" +
                "\t  \"Last\": \"Richards\"\n" +
                "\t},\n" +
                "\t{\n" +
                "\t  \"First\": \"Mick\",\n" +
                "\t  \"Last\": \"Jagger\"\n" +
                "\t}\n" +
                "      ]\n" +
                "    }\n" +
                "  ],\n" +
                "  \"Z\": 1" +
                "}\n";
        String songsRule = "{\n" +
                "  \"Songs\": {\n" +
                "    \"Name\": [ \"Norwegian Wood\" ],\n" +
                "    \"Writers\": {\n" +
                "      \"First\": [ \"John\" ],\n" +
                "      \"Last\": [ \"Lennon\" ]\n" +
                "    }\n" +
                "  }\n" +
                "}\n";
        String[] wantedKeys = {
                "Songs.Name", "Songs.Name",
                "Songs.Writers.First", "Songs.Writers.First", "Songs.Writers.First", "Songs.Writers.First",
                "Songs.Writers.Last", "Songs.Writers.Last", "Songs.Writers.Last", "Songs.Writers.Last",
                "Z"
        };
        String[] vals = {
                "Norwegian Wood", "Paint It Black",
                "John", "Keith", "Mick", "Paul",
                "Jagger", "Lennon", "McCartney", "Richards",
                "Z"
        };
        String[] membershipStrings = {
                "0[0]", "0[1]",
                "0[0] 1[0]", "0[1] 2[0]", "0[1] 2[1]", "0[0] 1[1]",
                "0[1] 2[1]", "0[0] 1[0]", "0[0] 1[1]", "0[1] 2[0]",
                null
        };
        Map<String, String> wantedMemberships = new HashMap<>();
        for (int i = 0; i < vals.length; i++) {
            wantedMemberships.put('"' + vals[i] + '"', membershipStrings[i]);
        }

        Machine m = new Machine();
        m.addRule("r1", songsRule);
        Event e = new Event(songs, m);
        checkFlattening(e, wantedKeys, null);
        for (Field f : e.fields) {
            assertEquals(f.val, wantedMemberships.get(f.val), f.arrayMembership.toString().trim());
        }
    }

    private void checkFlattening(Event e, String[] wantedKeys, String[] wantedVals) {
        for (int i = 0; i < e.fields.size(); i++) {
            assertEquals(wantedKeys[i], e.fields.get(i).name);
            if (wantedVals != null && wantedVals[i] != null) {
                assertEquals(wantedVals[i], e.fields.get(i).val);
            }
        }
    }

    @Test
    public void WHEN_InvalidJSONIsPresented_THEN_ErrorsAreHandledAppropriately() {
        String[] bads = { null, "{ 3 4 5", "[1,2,3]" };

        for (String bad : bads) {
            try {
                Event.flatten(bad);
                fail("Should throw exception");
            } catch (IllegalArgumentException e) {
                // yay
            }
        }
    }

    @Test
    public void WHEN_VariousShapesOfJSONArePresented_THEN_TheyAreFlattenedCorectly() throws Exception {


        String[][] desiredNameVals = {
                {
                        "Image.Animated", "false",
                        "Image.Height", "600",
                        "Image.IDs", "116",
                        "Image.IDs", "943",
                        "Image.IDs", "234",
                        "Image.IDs", "38793",
                        "Image.Thumbnail.Height", "125",
                        "Image.Thumbnail.Url", "\"http://www.example.com/image/481989943\"",
                        "Image.Thumbnail.Width", "100",
                        "Image.Title",  "\"View from 15th Floor\"",
                        "Image.Width", "800"
                },
                {
                        "account", "\"012345679012\"",
                        "detail-type",  "\"EC2 Instance State-change Notification\"",
                        "detail.c-count", "5",
                        "detail.d-count", "3",
                        "detail.instance-id", "\"i-000000aaaaaa00000\"",
                        "detail.state", "\"running\"",
                        "detail.x-limit", "301.8",
                        "id", "\"ddddd4-aaaa-7777-4444-345dd43cc333\"",
                        "region", "\"us-east-1\"",
                        "resources", "\"arn:aws:ec2:us-east-1:012345679012:instance/i-000000aaaaaa00000\"",
                        "source", "\"aws.ec2\"",
                        "time", "\"2017-10-02T16:24:49Z\"",
                        "version", "\"0\"",
                }
        };

        ObjectMapper objectMapper = new ObjectMapper();
        for (int i = 0; i < jsonFromRFC.length; i++) {
            String json = jsonFromRFC[i];
            // test method which accepts raw json
            List<String> nameVals = Event.flatten(json);
            // test method which accepts parsed json
            JsonNode rootNode = objectMapper.readTree(json);
            List<String> nameValsWithParsedJson = Event.flatten(rootNode);

            // verify both paths tpo flatten an event produce equivalent results
            assertEquals(nameValsWithParsedJson, nameVals);

            assertEquals(desiredNameVals[i].length, nameVals.size());
            for (int j = 0; j < nameVals.size(); j++) {
                assertEquals(desiredNameVals[i][j], nameVals.get(j));
            }
        }
    }

    @Test
    public void WHEN_PathsOfVariousLengthsAreStringified_THEN_TheyAreCorrect() {
        Stack<String> stack = new Stack<>();
        assertEquals("", Event.pathName(stack));
        stack.push("foo");
        assertEquals("foo", Event.pathName(stack));
        stack.push("bar");
        assertEquals("foo.bar", Event.pathName(stack));
        stack.push("baz");
        assertEquals("foo.bar.baz", Event.pathName(stack));
        stack.pop();
        assertEquals("foo.bar", Event.pathName(stack));
    }

    @Test
    public void WHEN_NameValPairsAreStored_ListsAreReturnedAppropriately() {
        Map<String, List<String>> map = new HashMap<>();
        Stack<String> stack = new Stack<>();
        stack.push("foo");
        Event.recordNameVal(map, stack, "bar");
        stack.pop();
        stack.push("x");
        stack.push("y");
        Event.recordNameVal(map, stack, "1");
        Event.recordNameVal(map, stack, "2");

        assertNull(map.get("z"));
        List<String> a = map.get("foo");
        assertEquals(1, a.size());
        assertTrue(a.contains("bar"));
        a = map.get("x.y");
        assertEquals(2, a.size());
        assertTrue(a.contains("1"));
        assertTrue(a.contains("2"));
    }
}