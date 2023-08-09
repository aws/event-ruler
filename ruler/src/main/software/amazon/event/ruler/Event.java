package software.amazon.event.ruler;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.StringJoiner;
import java.util.TreeMap;

/**
 * Prepares events for Ruler rule matching.
 *
 * There are three different implementations of code that have a similar goal: Prepare an Event, provided as JSON,
 *  for processing by Ruler.
 *
 * There is the flatten() entry point, which generates a list of strings that alternately represent the fields
 *  and values in an event, i.e. s[0] = name of first field, s[1] is value of that field, s[2] is name of 2nd field,
 *  and so on.  They are sorted in order of field name.  Its chief tools are the flattenObject and FlattenArray methods.
 *  This method cannot support array-consistent matching and is called only from the now-deprecated
 *  rulesForEvent(String json) method.
 *
 * There are two Event constructors, both called from the rulesForJSONEvent method in GenericMachine.
 *  Both generates a list of Field objects sorted by field name and equipped for matching with
 *  array consistency
 *
 * One takes a parsed version of the JSON event, presumably constructed by ObjectMapper. Its chief tools are the
 *  loadObject and loadArray methods.
 *
 * The constructor which takes a JSON string as argument uses the JsonParser's nextToken() method to traverse the
 *  structure without parsing it into a tree, and is thus several times faster.  Its chief tools are the
 *  traverseObject and traverseArray methods.
 */
// TODO: Improve unit-test coverage, there are surprising gaps
@Immutable
@ThreadSafe
class Event {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final JsonFactory JSON_FACTORY = new JsonFactory();

    // the fields of the event
    final List<Field> fields = new ArrayList<>();

    /**
     * represents the current state of an Event-constructor project
     */
    static class Progress {
        final ArrayMembership membership = new ArrayMembership();
        int arrayCount = 0;
        final Path path = new Path();
        // final Stack<String> path = new Stack<>();
        final GenericMachine<?> machine;
        Progress(final GenericMachine<?> m) {
            machine = m;
        }
    }

    /**
     * represents a field value during Event construction
     */
    static class Value {
        final String val;
        final ArrayMembership membership;
        Value(String val, ArrayMembership membership) {
            this.val = val;
            this.membership = new ArrayMembership(membership); // clones the argument
        }
    }

    /**
     * Generates an Event with a structure that supports checking for consistent array membership.
     *
     * @param json JSON representation of the event
     * @throws IOException if the JSON can't be parsed
     * @throws IllegalArgumentException if the top level of the Event is not a JSON object
     */
    Event(@Nonnull final String json, @Nonnull final GenericMachine<?> machine) throws IOException, IllegalArgumentException {
        final JsonParser parser = JSON_FACTORY.createParser(json);
        final Progress progress = new Progress(machine);
        final TreeMap<String, List<Value>> fieldMap = new TreeMap<>();

        if (parser.nextToken() != JsonToken.START_OBJECT) {
            throw new IllegalArgumentException("Event must be a JSON object");
        }
        traverseObject(parser, fieldMap, progress);
        parser.close();
        for (Map.Entry<String, List<Value>> entry : fieldMap.entrySet()) {
            for (Value val : entry.getValue()) {
                fields.add(new Field(entry.getKey(), val.val, val.membership));
            }
        }
    }

    // as above, only with the JSON already parsed into a ObjectMapper tree
    Event(@Nonnull final JsonNode eventRoot, @Nonnull final GenericMachine<?> machine) throws IllegalArgumentException {
        if (!eventRoot.isObject()) {
            throw new IllegalArgumentException("Event must be a JSON object");
        }
        final TreeMap<String, List<Value>> fieldMap = new TreeMap<>();
        final Progress progress = new Progress(machine);
        loadObject(eventRoot, fieldMap, progress);

        for (Map.Entry<String, List<Value>> entry : fieldMap.entrySet()) {
            for (Value val : entry.getValue()) {
                fields.add(new Field(entry.getKey(), val.val, val.membership));
            }
        }
    }

    private void traverseObject(final JsonParser parser, final TreeMap<String, List<Value>> fieldMap, final Progress progress) throws IOException {
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            // step name
            final String stepName = parser.getCurrentName();
            JsonToken nextToken = parser.nextToken();

            // If we know current step name hasn't been used by any rules, we don't parse into this step.
            if (!progress.machine.isFieldStepUsed(stepName)) {
                ignoreCurrentStep(parser);
                continue;
            }

            progress.path.push(stepName);
            switch (nextToken) {
                case START_OBJECT:
                    traverseObject(parser, fieldMap, progress);
                    break;
                case START_ARRAY:
                    traverseArray(parser, fieldMap, progress);
                    break;
                case VALUE_STRING:
                    addField(fieldMap, progress, '"' + parser.getText() + '"');
                    break;
                default:
                    addField(fieldMap, progress, parser.getText());
                    break;
            }
            progress.path.pop();
        }
    }

    private void traverseArray(final JsonParser parser, final TreeMap<String, List<Value>> fieldMap, final Progress progress) throws IOException {
        final int arrayID = progress.arrayCount++;

        JsonToken token;
        int arrayIndex = 0;
        while ((token = parser.nextToken()) != JsonToken.END_ARRAY) {
            switch (token) {
                case START_OBJECT:
                    progress.membership.putMembership(arrayID, arrayIndex);
                    traverseObject(parser, fieldMap, progress);
                    progress.membership.deleteMembership(arrayID);
                    break;
                case START_ARRAY:
                    progress.membership.putMembership(arrayID, arrayIndex);
                    traverseArray(parser, fieldMap, progress);
                    progress.membership.deleteMembership(arrayID);
                    break;
                case VALUE_STRING:
                    addField(fieldMap, progress, '"' + parser.getText() + '"');
                    break;
                default:
                    addField(fieldMap, progress, parser.getText());
                    break;
            }
            arrayIndex++;
        }
    }

    /**
     * Flattens a json String for matching rules in the state machine.
     * @param json the json string
     * @return flattened json event
     */
    static List<String> flatten(@Nonnull final String json) throws IllegalArgumentException {
        try {
            final JsonNode eventRoot = OBJECT_MAPPER.readTree(json);
            return doFlatten(eventRoot);
        } catch (Throwable e) { // should be IOException, but the @Nonnull annotation doesn't work in some scenarios
                                // catch all throwable exceptions
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Flattens a parsed json for matching rules in the state machine.
     * @param eventRoot root node for the parsed json
     * @return flattened json event
     */
    static List<String> flatten(@Nonnull final JsonNode eventRoot) throws IllegalArgumentException {
        try {
            return doFlatten(eventRoot);
        } catch (Throwable e) { // should be IOException, but the @Nonnull annotation doesn't work in some scenarios
                                // catch all throwable exceptions
            throw new IllegalArgumentException(e);
        }
    }

    private static List<String> doFlatten(final JsonNode eventRoot) throws IllegalArgumentException {

        final TreeMap<String, List<String>> fields = new TreeMap<>();

        if (!eventRoot.isObject()) {
            throw new IllegalArgumentException("Event must be a JSON object");
        }

        final Stack<String> path = new Stack<>();
        flattenObject(eventRoot, fields, path);

        // Ruler algorithm will explore all possible matches based on field (key value pair) in event, duplicated fields
        // in event do NOT impact final matches but it will downgrade the performance by producing duplicated
        // checking Steps, in worse case, those the duplicated steps can stuck the process and use up all memory, so when
        // building the event, dedupe the fields from event can low such risk.
        final Set<String> uniqueValues = new HashSet<>();
        final List<String> nameVals = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : fields.entrySet()) {
            String k = entry.getKey();
            List<String> vs = entry.getValue();
            if(vs.size() != 1) {
                uniqueValues.clear();
                for (String v : vs) {
                    if (uniqueValues.add(v)) {
                        nameVals.add(k);
                        nameVals.add(v);
                    }
                }
            } else {
                nameVals.add(k);
                nameVals.add(vs.get(0));
            }
        }
        return nameVals;
    }

    private void loadObject(final JsonNode object, final Map<String, List<Value>> fieldMap, final Progress progress) {
        final Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
        while (fields.hasNext()) {
            final Map.Entry<String, JsonNode> field = fields.next();
            final JsonNode val = field.getValue();

            // If we know current step name hasn't been used by any rules, we don't parse into this step.
            if (!progress.machine.isFieldStepUsed(field.getKey())) {
                continue;
            }

            progress.path.push(field.getKey());
            switch (val.getNodeType()) {
                case OBJECT:
                    loadObject(val, fieldMap, progress);
                    break;
                case ARRAY:
                    loadArray(val, fieldMap, progress);
                    break;

                case STRING:
                    addField(fieldMap, progress, '"' + val.asText() + '"');
                    break;
                case NULL:
                case BOOLEAN:
                case NUMBER:
                    addField(fieldMap, progress, val.asText());
                    break;
                default:
                    throw new RuntimeException("Unknown JsonNode type for: " + val.asText());
            }
            progress.path.pop();
        }
    }

    private static void flattenObject(final JsonNode object, final Map<String, List<String>> map, final Stack<String> path) {
        final Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
        while (fields.hasNext()) {
            final Map.Entry<String, JsonNode> field = fields.next();
            final JsonNode val = field.getValue();

            path.push(field.getKey());
            switch (val.getNodeType()) {
                case OBJECT:
                    flattenObject(val, map, path);
                    break;
                case ARRAY:
                    flattenArray(val, map, path);
                    break;
                case STRING:
                    recordNameVal(map, path, '"' + val.asText() + '"');
                    break;
                case NULL:
                case BOOLEAN:
                case NUMBER:
                    recordNameVal(map, path, val.asText());
                    break;
                default:
                    throw new RuntimeException("Unknown JsonNode type for: " + val.asText());
            }
            path.pop();
        }
    }

    private void loadArray(final JsonNode array, final Map<String, List<Value>> fieldMap, final Progress progress) {
        final int arrayID = progress.arrayCount++;
        final Iterator<JsonNode> elements = array.elements();

        int arrayIndex = 0;
        while (elements.hasNext()) {
            final JsonNode element = elements.next();
            switch (element.getNodeType()) {
            case OBJECT:
                progress.membership.putMembership(arrayID, arrayIndex);
                loadObject(element, fieldMap, progress);
                progress.membership.deleteMembership(arrayID);
                break;
            case ARRAY:
                progress.membership.putMembership(arrayID, arrayIndex);
                loadArray(element, fieldMap, progress);
                progress.membership.deleteMembership(arrayID);
                break;
            case STRING:
                addField(fieldMap, progress, '"' + element.asText() + '"');
                break;
            case NULL:
            case BOOLEAN:
            case NUMBER:
                addField(fieldMap, progress, element.asText());
                break;
            default:
                throw new RuntimeException("Unknown JsonNode type for: " + element.asText());
            }
            arrayIndex++;
        }
    }

    private static void flattenArray(final JsonNode array, final Map<String, List<String>> map, final Stack<String> path) {
        final Iterator<JsonNode> elements = array.elements();
        while (elements.hasNext()) {
            final JsonNode element = elements.next();
            switch (element.getNodeType()) {
            case OBJECT:
                flattenObject(element, map, path);
                break;
            case ARRAY:
                flattenArray(element, map, path);
                break;
            case STRING:
                recordNameVal(map, path, '"' + element.asText() + '"');
                break;
            case NULL:
            case BOOLEAN:
            case NUMBER:
                recordNameVal(map, path, element.asText());
                break;
            default:
                throw new RuntimeException("Unknown JsonNode type for: " + element.asText());
            }
        }
    }

    private void addField(final Map<String, List<Value>> fieldMap, final Progress progress, final String val) {
        final String key = progress.path.name();
        final List<Value> vals = fieldMap.computeIfAbsent(key, k -> new ArrayList<>());
        vals.add(new Value(val, progress.membership));
    }

    static void recordNameVal(final Map<String, List<String>> map, final Stack<String> path, final String val) {
        final String key = pathName(path);
        List<String> vals = map.computeIfAbsent(key, k -> new ArrayList<>());

        vals.add(val);
    }

    static String pathName(final Stack<String> path) {
        final StringJoiner joiner = new StringJoiner(".");
        for (String step : path) {
            joiner.add(step);
        }
        return joiner.toString();
    }

    private void ignoreCurrentStep(JsonParser jsonParser) throws IOException {
        JsonToken token = jsonParser.getCurrentToken();
        if (token == JsonToken.START_OBJECT) {
            advanceToClosingToken(jsonParser, JsonToken.START_OBJECT, JsonToken.END_OBJECT);
        } else if (token == JsonToken.START_ARRAY) {
            advanceToClosingToken(jsonParser, JsonToken.START_ARRAY, JsonToken.END_ARRAY);
        }
    }

    private void advanceToClosingToken(JsonParser jsonParser, JsonToken openingToken, JsonToken closingToken) throws IOException {
        int count = 1;
        do {
            JsonToken currentToken = jsonParser.nextToken();
            if (currentToken == openingToken) {
                count++;
            } else if (currentToken == closingToken) {
                count--;
            }
        } while (count > 0);
    }
}
