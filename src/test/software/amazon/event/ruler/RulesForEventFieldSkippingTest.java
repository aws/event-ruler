package software.amazon.event.ruler;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Randomized check that {@link GenericMachine#rulesForEvent(List)} ({@link Finder}) and
 * {@link GenericMachine#rulesForJSONEvent(String)} return the same rules on events without arrays,
 * where the two matchers are specified to agree. Events carry many fields no rule uses, including fields whose
 * first step is used, so Finder's skipping of unused fields is exercised on every pattern type.
 */
public class RulesForEventFieldSkippingTest {

    // Leaf paths rules can reference. No path is a prefix of another, so every rule is valid JSON.
    private static final String[] RULE_PATHS = {"a.b", "a.c", "b", "c.d.e", "c.f", "d"};
    // Paths only events carry. Some share a used first step ("a", "c", "c.d"), so they pass the first step check.
    private static final String[] NOISE_PATHS = {"a.n1", "a.n2", "c.d.n3", "c.n4", "n5", "n6.a", "n7.b.c", "z"};
    private static final String[] STRING_VALUES = {"x", "xy", "Ab", "aB", "foo", "yy"};

    private static final String[] PATTERNS = {
        "\"x\"",
        "\"Ab\"",
        "7",
        "{\"prefix\": \"x\"}",
        "{\"suffix\": \"y\"}",
        "{\"equals-ignore-case\": \"ab\"}",
        "{\"wildcard\": \"*y\"}",
        "{\"anything-but\": [\"x\"]}",
        "{\"anything-but\": {\"prefix\": \"f\"}}",
        "{\"anything-but\": {\"suffix\": \"y\"}}",
        "{\"anything-but\": {\"equals-ignore-case\": [\"AB\"]}}",
        "{\"numeric\": [\">\", 5]}",
        "{\"numeric\": [\">=\", 3, \"<\", 9]}",
        "{\"exists\": true}",
        "{\"exists\": false}",
    };

    @Test
    public void rulesForEventAgreesWithRulesForJsonEventOnArrayFreeEvents() throws Exception {
        Random random = new Random(1126L);
        int matched = 0;
        for (int m = 0; m < 40; m++) {
            Machine machine = new Machine();
            int ruleCount = 1 + random.nextInt(30);
            for (int r = 0; r < ruleCount; r++) {
                machine.addRule("r" + r, randomRule(random));
            }
            for (int e = 0; e < 300; e++) {
                String event = randomEvent(random);
                List<String> viaFinder = sorted(machine.rulesForEvent(Event.flatten(event)));
                List<String> viaJsonEvent = sorted(machine.rulesForJSONEvent(event));
                assertEquals("event " + event, viaJsonEvent, viaFinder);
                matched += viaFinder.size();
            }
        }
        // Guard against a generator that never matches anything.
        assertTrue(matched > 1000);
    }

    private static String randomRule(final Random random) {
        Json rule = new Json();
        for (int f = 1 + random.nextInt(3); f > 0; f--) {
            String path = RULE_PATHS[random.nextInt(RULE_PATHS.length)];
            String patterns = random.nextInt(4) == 0
                    ? PATTERNS[random.nextInt(PATTERNS.length)] + ", " + PATTERNS[random.nextInt(PATTERNS.length)]
                    : PATTERNS[random.nextInt(PATTERNS.length)];
            rule.put(path, "[" + patterns + "]");
        }
        if (random.nextInt(5) == 0) {
            // Wrap the rule in an $or with a second single-field branch.
            String path = RULE_PATHS[random.nextInt(RULE_PATHS.length)];
            Json other = new Json();
            other.put(path, "[" + PATTERNS[random.nextInt(PATTERNS.length)] + "]");
            return "{\"$or\": [" + rule + ", " + other + "]}";
        }
        return rule.toString();
    }

    private static String randomEvent(final Random random) {
        Json event = new Json();
        for (String path : RULE_PATHS) {
            if (random.nextInt(4) != 0) {
                event.put(path, randomValue(random));
            }
        }
        for (String path : NOISE_PATHS) {
            if (random.nextBoolean()) {
                event.put(path, randomValue(random));
            }
        }
        return event.toString();
    }

    private static String randomValue(final Random random) {
        return random.nextBoolean()
                ? "\"" + STRING_VALUES[random.nextInt(STRING_VALUES.length)] + "\""
                : Integer.toString(random.nextInt(12));
    }

    private static List<String> sorted(final List<String> rules) {
        List<String> copy = new ArrayList<>(rules);
        Collections.sort(copy);
        return copy;
    }

    // Minimal nested-object JSON builder keyed by dotted paths. Values are raw JSON.
    private static final class Json {
        private final TreeMap<String, Object> fields = new TreeMap<>();

        void put(final String path, final String rawValue) {
            int dot = path.indexOf('.');
            if (dot < 0) {
                fields.put(path, rawValue);
                return;
            }
            Object child = fields.computeIfAbsent(path.substring(0, dot), k -> new Json());
            ((Json) child).put(path.substring(dot + 1), rawValue);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("{");
            String separator = "";
            for (Map.Entry<String, Object> field : fields.entrySet()) {
                sb.append(separator).append('"').append(field.getKey()).append("\": ").append(field.getValue());
                separator = ", ";
            }
            return sb.append('}').toString();
        }
    }
}
