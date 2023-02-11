package software.amazon.event.ruler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The core idea of Ruler is to match rules to events at a rate that's independent of the number of rules.  This is
 *  achieved by compiling the rules into an automaton, at some up-front cost for compilation and memory use. There
 *  are some users who are unable to persist the compiled automaton but still like the "Event Pattern" idiom and want
 *  to match events with those semantics.
 * The memory cost is proportional to the product of the number of possible values provided in the rule and can
 *  grow surprisingly large
 * This class matches a single rule to a single event without any precompilation, using brute-force techniques, but
 *  with no up-front compute or memory cost.
 */
@ThreadSafe
@Immutable
public class Ruler {

    private final static ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private Ruler() { }

    /**
     * Return true if an event matches the provided rule. This is a thin wrapper around
     * rule machine and `rulesForJSONEvent` method.
     *
     * @param event The event, in JSON form
     * @param rule The rule, in JSON form
     * @return true or false depending on whether the rule matches the event
     */
    public static boolean matchesRule(final String event, final String rule) throws Exception {
        Machine machine = new Machine();
        machine.addRule("rule", rule);
        return !machine.rulesForJSONEvent(event).isEmpty();
    }

    /**
     * Return true if an event matches the provided rule.
     * <p>
     * This method is deprecated. You should use `Ruler.match` instead in all but one cases:
     * this method will return false for ` {"detail" : { "state": { "state": "running" } } }` with
     * `{ "detail" : { "state.state": "running" } }` while `Ruler.matchesRule(...)` will return true. When this gap
     * has been addressed, we will remove this method as it doesn't handle many of the new matchers
     * and is not able to perform array consistency checks like rest of Ruler. This method also is
     * slower.
     *
     * @param event The event, in JSON form
     * @param rule The rule, in JSON form
     * @return true or false depending on whether the rule matches the event
     */
    @Deprecated
    public static boolean matches(final String event, final String rule) throws IOException {
        final JsonNode eventRoot = OBJECT_MAPPER.readTree(event);
        final Map<List<String>, List<Patterns>> ruleMap = RuleCompiler.ListBasedRuleCompiler.flattenRule(rule);
        return matchesAllFields(eventRoot, ruleMap);
    }

    private static boolean matchesAllFields(final JsonNode event, final Map<List<String>, List<Patterns>> rule) {
        for (Map.Entry<List<String>, List<Patterns>> entry : rule.entrySet()) {
            final JsonNode fieldValue = tryToRetrievePath(event, entry.getKey());
            if (!matchesOneOf(fieldValue, entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    // TODO: Improve unit-test coverage
    private static boolean matchesOneOf(final JsonNode val, final List<Patterns> patterns) {
        for (Patterns pattern : patterns) {
            if (val == null) {
                // a non existent value matches the absent pattern,
                if (pattern.type() == MatchType.ABSENT) {
                    return true;
                }
            }
            else if (val.isArray()) {
                for (final JsonNode element : val) {
                    if (matches(element, pattern)) {
                        return true;
                    }
                }
            } else {
                if (matches(val, pattern)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean matches(final JsonNode val, final Patterns pattern) {
        switch (pattern.type()) {
            case EXACT:
                ValuePatterns valuePattern = (ValuePatterns) pattern;
                // if it's a string we match the "-quoted form, otherwise (true, false, null) as-is.
                final String compareTo = (val.isTextual()) ? '"' + val.asText() + '"' : val.asText();
                return compareTo.equals(valuePattern.pattern());

            case PREFIX:
                valuePattern = (ValuePatterns) pattern;
                return val.isTextual() && ('"' + val.asText()).startsWith(valuePattern.pattern());

            case ANYTHING_BUT:
                assert (pattern instanceof AnythingBut);
                AnythingBut anythingButPattern = (AnythingBut) pattern;
                if (val.isTextual()) {
                    return anythingButPattern.getValues().stream().noneMatch(v -> v.equals('"' + val.asText() + '"'));
                } else if (val.isNumber()) {
                    return anythingButPattern.getValues().stream()
                            .noneMatch(v -> v.equals(ComparableNumber.generate(val.asDouble())));
                }
                return false;
            case ANYTHING_BUT_IGNORE_CASE:
                assert (pattern instanceof AnythingButEqualsIgnoreCase);
                AnythingButEqualsIgnoreCase anythingButIgnoreCasePattern = (AnythingButEqualsIgnoreCase) pattern;
                if (val.isTextual()) {
                    return anythingButIgnoreCasePattern.getValues().stream().noneMatch(v -> v.equalsIgnoreCase('"' + val.asText() + '"'));
                }
                return false;

            case ANYTHING_BUT_SUFFIX:
                valuePattern = (ValuePatterns) pattern;
                return !(val.isTextual() && (val.asText() + '"').startsWith(valuePattern.pattern()));
            case ANYTHING_BUT_PREFIX:
                valuePattern = (ValuePatterns) pattern;
                return !(val.isTextual() && ('"' + val.asText()).startsWith(valuePattern.pattern()));

            case NUMERIC_EQ:
                valuePattern = (ValuePatterns) pattern;
                return val.isNumber() && ComparableNumber.generate(val.asDouble()).equals(valuePattern.pattern());
            case EXISTS:
                return true;
            case ABSENT:
                return false;
            case NUMERIC_RANGE:
                final Range nr = (Range) pattern;
                byte[] bytes;
                if (nr.isCIDR) {
                    if (!val.isTextual()) {
                        return false;
                    }
                    try {
                        bytes = CIDR.ipToString(val.asText()).getBytes(StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        return false;
                    }
                } else {
                    if (!val.isNumber()) {
                        return false;
                    }
                    bytes = ComparableNumber.generate(val.asDouble()).getBytes(StandardCharsets.UTF_8);
                }
                final int comparedToBottom = compare(bytes, nr.bottom);
                if ((comparedToBottom > 0) || (comparedToBottom == 0 && !nr.openBottom)) {
                    final int comparedToTop = compare(bytes, nr.top);
                    return comparedToTop < 0 || (comparedToTop == 0 && !nr.openTop);
                }
                return false;
            case EQUALS_IGNORE_CASE:
                valuePattern = (ValuePatterns) pattern;
                return val.isTextual() && ('"' + val.asText() + '"').equalsIgnoreCase(valuePattern.pattern());
            case WILDCARD:
                valuePattern = (ValuePatterns) pattern;
                return val.isTextual() &&
                        ('"' + val.asText() + '"').matches(valuePattern.pattern().replaceAll("\\*", ".*"));
            default:
                throw new RuntimeException("Unsupported Pattern type " + pattern.type());
        }
    }

    static JsonNode tryToRetrievePath(JsonNode node, final List<String> path) {
        for (final String step : path) {
            if ((node == null) || !node.isObject()) {
                return null;
            }
            node = node.get(step);
        }
        return node;
    }

    static int compare(final byte[] a, final byte[] b) {
        assert(a.length == b.length);
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                return a[i] - b[i];
            }
        }
        return 0;
    }
}
