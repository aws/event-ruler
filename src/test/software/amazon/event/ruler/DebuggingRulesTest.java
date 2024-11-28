package software.amazon.event.ruler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class DebuggingRulesTest {

    @Test
    public void testMachinedMatches() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        String event = "{\n" +
                "  \"version\": \"0\",\n" +
                "  \"id\": \"ddddd4-aaaa-7777-4444-345dd43cc333\",\n" +
                "  \"source\": \"aws.cloudwatch\",\n" +
                "  \"region\": \"us-east-1\",\n" +
                "  \"resources\": [\n" +
                "    \"arn:aws:ec2:us-east-1:123456789012:instance/i-000000aaaaaa00000\"\n" +
                "  ],\n" +
                "  \"detail\": {\n" +
                "    \"metricType\": \"Blah\",\n" +
                "    \"namespace\": [  \"AWS/EC2\" ],\n" +
                "    \"value\": \"1.0\"\n" +
                "  }\n" +
                "}\n";

        JsonNode rule = mapper.readTree("{\n" +
                "    \"source\": [ \"aws.cloudwatch\" ],\n" +
                "    \"detail\" :  {\n" +
                "         \"metricType\": [ \"MetricType\" ]  , \n" +
                "         \"namespace\": [ \"AWS/EC2\", \"AWS/ES\" ] \n" +
                "    }\n" +
                "}\n");

        // System.out.println("Init : " + mapper.writeValueAsString(rule));

        Set<Variation> variations = createJsonVariationsRecursive(rule);

        // Print all variations

        Map<String, Variation> ruleToVariationJsonMap = new HashMap<>();
        Machine machine = Machine.builder().withAdditionalNameStateReuse(true).build();
        machine.addRule("fullRule", rule.toString());
        if(!machine.rulesForJSONEvent(event).isEmpty()) {
            // System.out.println("Full rule matches");
            return;
        }
        // else check which part of the rule matches
        // System.out.println("Print all variations : ");
        Variation[] variationArr = variations.toArray(new Variation[0]);
        for (int i=0;i<variationArr.length;i++) {
            // System.out.println("-------------------");
            String ruleName = "rulePart-" + i;
            String rulePattern = variationArr[i].includedJson.toString();
            if(JsonRuleCompiler.check(rulePattern) != null) {
                // System.out.println("Skipping : " + variationArr[i]);
                continue; // invalid rule, skip
            }
            if(variationArr[i].skippedJson == null) {
                // System.out.println("Skipping : " + variationArr[i]);
                continue; // matching the full rule, skip
            }
            machine.addRule(ruleName, rulePattern);

            // System.out.println("RuleName: " + ruleName);
            // System.out.println("Variation : " + variationArr[i]);

            ruleToVariationJsonMap.put(ruleName, variationArr[i]);
        }

        List<String> matches = machine.rulesForJSONEvent(event);
        // System.out.println(matches);

        Set<JsonNode> includedJsons = new HashSet<>();

        for(Map.Entry<String, Variation> entry : ruleToVariationJsonMap.entrySet()) {
            if(matches.contains(entry.getKey())) {
                includedJsons.add(entry.getValue().includedJson);
            }
        }

        JsonNode matchedParts = merge(includedJsons);
        System.out.println("Matched following parts of the rule " + matchedParts);
    }

    public static JsonNode merge(Set<JsonNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return null;
        }

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode result = mapper.createObjectNode();

        for (JsonNode node : nodes) {
            mergeNodes(result, node);
        }

        return result;
    }

    private static void mergeNodes(ObjectNode target, JsonNode source) {
        if (source == null) {
            return;
        }

        Iterator<String> fieldNames = source.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            JsonNode targetValue = target.get(fieldName);
            JsonNode sourceValue = source.get(fieldName);

            if (targetValue != null && targetValue.isObject() && sourceValue.isObject()) {
                // If both are objects, merge them recursively
                mergeNodes((ObjectNode) targetValue, sourceValue);
            } else if (targetValue != null && targetValue.isArray() && sourceValue.isArray()) {
                // If both are arrays, combine them
                ArrayNode targetArray = (ArrayNode) targetValue;
                ArrayNode sourceArray = (ArrayNode) sourceValue;
                Set<JsonNode> uniqueElements = new HashSet<>();
                targetArray.forEach(uniqueElements::add);
                sourceArray.forEach(uniqueElements::add);
                ArrayNode newArray = target.arrayNode();
                uniqueElements.forEach(newArray::add);
                target.set(fieldName, newArray);
            } else {
                // Otherwise, just set the value
                target.set(fieldName, sourceValue);
            }
        }
    }

    public static class Variation {
        public final JsonNode includedJson; // FIXME json is redundant
        public final JsonNode skippedJson;

        public Variation(JsonNode includedJson, JsonNode skippedJson) {
            this.includedJson = includedJson;
            this.skippedJson = skippedJson;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Variation variation = (Variation) o;
            return Objects.equals(includedJson, variation.includedJson) && Objects.equals(skippedJson, variation.skippedJson);
        }

        @Override
        public int hashCode() {
            return Objects.hash(includedJson, skippedJson);
        }

        @Override
        public String toString() {
            return "Variation{\n" +
                    "\tincludedJson=" + includedJson +
                    ",\n\tskippedJson=" + skippedJson +
                    "\n}";
        }
    }

    private static Set<Variation> createJsonVariationsRecursive(JsonNode rootNode) {
        Set<Variation> variations = new HashSet<>();
        variations.add(new Variation(rootNode.deepCopy(), null));

        if (rootNode.isObject()) {
            Iterator<String> fieldNames = rootNode.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldToSkip = fieldNames.next();
                ObjectNode variation = rootNode.deepCopy();
                ObjectNode skippedContent = JsonNodeFactory.instance.objectNode();
                skippedContent.set(fieldToSkip, variation.get(fieldToSkip));
                variation.remove(fieldToSkip);
                if (!variation.isEmpty()) {
                    variations.add(new Variation(variation, skippedContent));
                }

                // Handle nested objects and arrays
                JsonNode fieldValue = rootNode.get(fieldToSkip);
                if (fieldValue.isObject() || fieldValue.isArray()) {
                    Set<Variation> nestedVariations = createJsonVariationsRecursive(fieldValue);
                    for (Variation nestedVariation : nestedVariations) {
                        if (nestedVariation.skippedJson != null) {
                            ObjectNode combinedVariation = rootNode.deepCopy();
                            combinedVariation.set(fieldToSkip, nestedVariation.includedJson);
                            ObjectNode combinedSkipped = JsonNodeFactory.instance.objectNode();
                            combinedSkipped.set(fieldToSkip, nestedVariation.skippedJson);
                            if (!nestedVariation.includedJson.isEmpty()) {
                                variations.add(new Variation(combinedVariation, combinedSkipped));
                            }
                        }
                    }
                }
            }
        } else if (rootNode.isArray()) {
            for (int i = 0; i < rootNode.size(); i++) {
                JsonNode arrayElement = rootNode.get(i);

                // Create variation without this element
                ArrayNode arrayVariation = ((ArrayNode) rootNode).deepCopy();
                ArrayNode skippedContent = JsonNodeFactory.instance.arrayNode();
                skippedContent.add(arrayVariation.get(i));
                arrayVariation.remove(i);
                variations.add(new Variation(arrayVariation, skippedContent));

                // If array element is object or array, process it recursively
                if (arrayElement.isObject() || arrayElement.isArray()) {
                    Set<Variation> elementVariations = createJsonVariationsRecursive(arrayElement);
                    for (Variation elementVariation : elementVariations) {
                        if (elementVariation.skippedJson != null) {
                            ArrayNode combinedVariation = ((ArrayNode) rootNode).deepCopy();
                            combinedVariation.set(i, elementVariation.includedJson);
                            ArrayNode combinedSkipped = JsonNodeFactory.instance.arrayNode();
                            combinedSkipped.add(elementVariation.skippedJson);
                            if (!elementVariation.includedJson.isEmpty()) {
                                variations.add(new Variation(combinedVariation, combinedSkipped));
                            }
                        }
                    }
                }
            }
        }

        return variations;
    }

}
