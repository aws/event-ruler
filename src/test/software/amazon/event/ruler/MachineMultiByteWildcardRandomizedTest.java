package software.amazon.event.ruler;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MachineMultiByteWildcardRandomizedTest {

    private static final String[] TEMPLATES = {
            "{\"wildcard\": \"*{L}\"}",
            "{\"wildcard\": \"*z{L}\"}",
            "{\"wildcard\": \"*a*{L}\"}",
            "{\"wildcard\": \"*{L}*\"}",
            "{\"wildcard\": \"{L}*\"}",
            "{\"wildcard\": \"x*{L}\"}",
            "{\"wildcard\": \"*{U}\"}",
            "{\"wildcard\": \"*xz{L}\"}",
            "{\"wildcard\": \"*z*z{L}\"}",
            "{\"wildcard\": \"*{L}*{L}\"}",
            "{\"equals-ignore-case\": \"{L}x\"}",
            "{\"equals-ignore-case\": \"{L}\"}",
            "{\"equals-ignore-case\": \"x{L}\"}",
            "{\"prefix\": {\"equals-ignore-case\": \"{L}x\"}}",
            "{\"prefix\": {\"equals-ignore-case\": \"{L}\"}}",
            "{\"suffix\": {\"equals-ignore-case\": \"{L}x\"}}",
            "{\"anything-but\": {\"equals-ignore-case\": \"{L}x\"}}",
            "{\"prefix\": \"{L}\"}",
            "{\"suffix\": \"x\"}",
    };

    private static final String[] TEMPLATES_FOR_CASE_VARYING_CHARACTERS_ONLY = {
            "{\"suffix\": {\"equals-ignore-case\": \"x{L}\"}}",
            "\"{L}x\"",
    };

    private static final String KELVIN_SIGN = "\u212A";
    private static final int MAX_EVENT_LENGTH = 3;
    private static final int MAX_RULES_PER_MACHINE = 5;

    @Test
    public void combinedMachineMatchesUnionOfSingleRuleMachinesForTwoByteLatinLetter() throws Exception {
        assertCombinedMachinesMatchUnion(caseVaryingPool("é", "É"), 600, "é", "É", "x", "X", "z", "a");
    }

    @Test
    public void combinedMachineMatchesUnionOfSingleRuleMachinesForCyrillicLetter() throws Exception {
        assertCombinedMachinesMatchUnion(caseVaryingPool("д", "Д"), 100, "д", "Д", "x", "X", "z", "a");
    }

    @Test
    public void combinedMachineMatchesUnionOfSingleRuleMachinesForFullWidthLetter() throws Exception {
        assertCombinedMachinesMatchUnion(caseVaryingPool("ａ", "Ａ"), 100, "ａ", "Ａ", "x", "X", "z", "a");
    }

    @Test
    public void combinedMachineMatchesUnionOfSingleRuleMachinesForKelvinSign() throws Exception {
        assertCombinedMachinesMatchUnion(caseVaryingPool(KELVIN_SIGN, "k"), 100,
                KELVIN_SIGN, "k", "K", "x", "X", "z");
    }

    @Test
    public void combinedMachineMatchesUnionOfSingleRuleMachinesForDotlessI() throws Exception {
        assertCombinedMachinesMatchUnion(caseVaryingPool("ı", "I"), 100, "ı", "I", "i", "x", "X", "z");
    }

    @Test
    public void combinedMachineMatchesUnionOfSingleRuleMachinesForCaselessCharacter() throws Exception {
        assertCombinedMachinesMatchUnion(pool("次", "次", TEMPLATES), 100, "次", "x", "X", "z", "a");
    }

    private static List<String> caseVaryingPool(final String lower, final String upper) {
        final List<String> patterns = pool(lower, upper, TEMPLATES);
        patterns.addAll(pool(lower, upper, TEMPLATES_FOR_CASE_VARYING_CHARACTERS_ONLY));
        return patterns;
    }

    private static List<String> pool(final String lower, final String upper, final String[] templates) {
        final Set<String> patterns = new LinkedHashSet<>();
        for (final String template : templates) {
            patterns.add(template.replace("{L}", lower).replace("{U}", upper));
        }
        return new ArrayList<>(patterns);
    }

    private static void assertCombinedMachinesMatchUnion(final List<String> pool, final int machines,
                                                         final String... alphabet) throws Exception {
        final Random random = new Random(20261030L);
        final List<String> values = allValues(alphabet);
        final Map<String, Machine> singleRuleMachines = new HashMap<>();

        for (int m = 0; m < machines; m++) {
            final int ruleCount = 2 + random.nextInt(MAX_RULES_PER_MACHINE - 1);
            final List<String> patterns = new ArrayList<>(pool);
            Collections.shuffle(patterns, random);
            final List<String[]> rules = new ArrayList<>();
            for (int r = 0; r < ruleCount; r++) {
                rules.add(new String[] {"r" + r, rule(patterns.get(r))});
            }

            final Machine machine = new Machine();
            for (final String[] rule : rules) {
                machine.addRule(rule[0], rule[1]);
            }
            assertUnion("rules=" + describe(rules), machine, rules, values, singleRuleMachines);

            Collections.shuffle(rules, random);
            final int deleted = rules.size() / 2;
            for (final String[] rule : rules.subList(0, deleted)) {
                machine.deleteRule(rule[0], rule[1]);
            }
            final List<String[]> remaining = rules.subList(deleted, rules.size());
            assertUnion("after delete, remaining=" + describe(remaining), machine, remaining, values,
                    singleRuleMachines);

            for (final String[] rule : remaining) {
                machine.deleteRule(rule[0], rule[1]);
            }
            assertTrue("not empty after deleting all, rules=" + describe(rules), machine.isEmpty());
        }
    }

    private static void assertUnion(final String context, final Machine machine, final List<String[]> rules,
                                    final List<String> values, final Map<String, Machine> singleRuleMachines)
            throws Exception {
        for (final String value : values) {
            final String event = "{\"a\": \"" + value + "\"}";
            final Set<String> expected = new TreeSet<>();
            for (final String[] rule : rules) {
                if (!singleRuleMachine(rule[1], singleRuleMachines).rulesForJSONEvent(event).isEmpty()) {
                    expected.add(rule[0]);
                }
            }
            assertEquals(context + " value=" + value, expected, new TreeSet<>(machine.rulesForJSONEvent(event)));
        }
    }

    private static Machine singleRuleMachine(final String rule, final Map<String, Machine> singleRuleMachines)
            throws Exception {
        Machine machine = singleRuleMachines.get(rule);
        if (machine == null) {
            machine = new Machine();
            machine.addRule("single", rule);
            singleRuleMachines.put(rule, machine);
        }
        return machine;
    }

    private static String rule(final String pattern) {
        return "{\"a\": [" + pattern + "]}";
    }

    private static String describe(final List<String[]> rules) {
        final StringBuilder description = new StringBuilder();
        for (final String[] rule : rules) {
            description.append(rule[0]).append('=').append(rule[1]).append(' ');
        }
        return description.toString();
    }

    private static List<String> allValues(final String[] alphabet) {
        final List<String> values = new ArrayList<>();
        values.add("");
        List<String> previousLength = Collections.singletonList("");
        for (int length = 1; length <= MAX_EVENT_LENGTH; length++) {
            final List<String> currentLength = new ArrayList<>();
            for (final String prefix : previousLength) {
                for (final String character : alphabet) {
                    currentLength.add(prefix + character);
                }
            }
            values.addAll(currentLength);
            previousLength = currentLength;
        }
        return values;
    }
}
