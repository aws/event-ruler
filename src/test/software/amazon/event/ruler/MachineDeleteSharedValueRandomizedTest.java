package software.amazon.event.ruler;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MachineDeleteSharedValueRandomizedTest {

    private static final String[] TEMPLATES = {
            "\"{V}\"",
            "{\"equals-ignore-case\": \"{V}\"}",
            "{\"prefix\": \"{V}\"}",
            "{\"prefix\": {\"equals-ignore-case\": \"{V}\"}}",
            "{\"suffix\": \"{V}\"}",
            "{\"suffix\": {\"equals-ignore-case\": \"{V}\"}}",
            "{\"anything-but\": {\"equals-ignore-case\": [\"{V}\"]}}",
            "{\"suffix\": {\"equals-ignore-case\": \"{V}\"}}, {\"prefix\": \"{V}\"}",
    };

    private static final String[] TEMPLATES_WITH_WILDCARDS = {
            "\"{V}\"",
            "{\"prefix\": \"{V}\"}",
            "{\"suffix\": \"{V}\"}",
            "{\"wildcard\": \"{V}*\"}",
            "{\"wildcard\": \"*{V}\"}",
            "{\"wildcard\": \"z*{V}\"}",
            "{\"prefix\": \"{V}\"}, {\"wildcard\": \"*{V}\"}",
    };

    private static final String[] RULE_VALUE_ALPHABET = { "é", "x", "1", "次" };
    private static final String[] EVENT_VALUE_ALPHABET = { "é", "É", "x", "X", "1", "次" };
    private static final String[] WILDCARD_RULE_VALUE_ALPHABET = { "z", "x", "1" };
    private static final String[] WILDCARD_EVENT_VALUE_ALPHABET = { "z", "x", "1", "y" };
    private static final String[] CASE_SET_RULE_VALUE_ALPHABET = { "\u212A", "k", "\u017F", "S", "\u0131", "I", "x" };
    private static final String[] CASE_SET_EVENT_VALUE_ALPHABET =
            { "k", "K", "\u212A", "s", "S", "\u017F", "i", "I", "\u0131", "x" };
    private static final String[] SHARED_FIRST_BYTE_RULE_VALUE_ALPHABET = { "×", "é" };
    private static final String[] SHARED_FIRST_BYTE_EVENT_VALUE_ALPHABET = { "×", "é", "É" };
    private static final String FIELD = "a";
    private static final int MAX_RULE_VALUE_LENGTH = 2;
    private static final int MAX_EVENT_VALUE_LENGTH = 3;
    private static final int MAX_CASE_SET_EVENT_VALUE_LENGTH = 2;
    private static final int MIN_RULES_PER_MACHINE = 2;
    private static final int MAX_RULES_PER_MACHINE = 5;
    private static final int RULE_VALUES_TO_SHARE = 2;
    private static final int MACHINES_PER_SEED = 300;
    private static final long FIRST_SEED = 0L;
    private static final long LAST_SEED = 9L;

    @Test
    public void WHEN_RulesThatShareValuesAreDeleted_THEN_MachineMatchesUnionOfRemainingSingleRuleMachines()
            throws Exception {
        assertDeletesForAllSeeds(TEMPLATES, allValues(RULE_VALUE_ALPHABET, 1, MAX_RULE_VALUE_LENGTH),
                allValues(EVENT_VALUE_ALPHABET, 0, MAX_EVENT_VALUE_LENGTH));
    }

    @Test
    public void WHEN_RulesWithCaseSetMembersOfDifferentByteLengthsAreDeleted_THEN_MachineMatchesUnionOfRemaining()
            throws Exception {
        assertDeletesForAllSeeds(TEMPLATES, allValues(CASE_SET_RULE_VALUE_ALPHABET, 1, MAX_RULE_VALUE_LENGTH),
                allValues(CASE_SET_EVENT_VALUE_ALPHABET, 0, MAX_CASE_SET_EVENT_VALUE_LENGTH));
    }

    @Test
    public void WHEN_RulesWithCaselessAndCasedCharactersOnSameFirstByteAreDeleted_THEN_MachineMatchesUnionOfRemaining()
            throws Exception {
        assertDeletesForAllSeeds(TEMPLATES, allValues(SHARED_FIRST_BYTE_RULE_VALUE_ALPHABET, 1, MAX_RULE_VALUE_LENGTH),
                allValues(SHARED_FIRST_BYTE_EVENT_VALUE_ALPHABET, 0, MAX_EVENT_VALUE_LENGTH));
    }

    @Test
    public void WHEN_RulesThatShareValuesWithWildcardRulesAreDeleted_THEN_MachineMatchesUnionOfRemaining()
            throws Exception {
        assertDeletesForAllSeeds(TEMPLATES_WITH_WILDCARDS,
                allValues(WILDCARD_RULE_VALUE_ALPHABET, 1, MAX_RULE_VALUE_LENGTH),
                allValues(WILDCARD_EVENT_VALUE_ALPHABET, 0, MAX_EVENT_VALUE_LENGTH));
    }

    private static void assertDeletesForAllSeeds(final String[] templates, final List<String> ruleValues,
                                                 final List<String> eventValues) throws Exception {
        final Map<String, Machine> singleRuleMachines = new HashMap<>();

        for (long seed = FIRST_SEED; seed <= LAST_SEED; seed++) {
            final Random random = new Random(seed);
            for (int m = 0; m < MACHINES_PER_SEED; m++) {
                assertDeletes("seed=" + seed + " machine=" + m, random, templates, ruleValues, eventValues,
                        singleRuleMachines);
            }
        }
    }

    private static void assertDeletes(final String context, final Random random, final String[] templates,
                                      final List<String> ruleValues, final List<String> eventValues,
                                      final Map<String, Machine> singleRuleMachines) throws Exception {
        final List<String> values = pickRuleValuesToShare(random, ruleValues);
        final int ruleCount = MIN_RULES_PER_MACHINE
                + random.nextInt(MAX_RULES_PER_MACHINE - MIN_RULES_PER_MACHINE + 1);
        final List<Rule> rules = new ArrayList<>();
        for (int r = 0; r < ruleCount; r++) {
            final String template = templates[random.nextInt(templates.length)];
            final String value = values.get(random.nextInt(values.size()));
            rules.add(new Rule("r" + r, template.replace("{V}", value)));
        }

        final Machine machine = new Machine();
        for (final Rule rule : rules) {
            machine.addRule(rule.name, rule.json);
        }
        assertUnion(context + " rules=" + rules, machine, rules, eventValues, singleRuleMachines);
        assertSubRulesAreOnlyOnNameStatesThatTheirPatternsLeadTo(context + " rules=" + rules, machine, rules);

        Collections.shuffle(rules, random);
        final int deleted = 1 + random.nextInt(rules.size() - 1);
        for (final Rule rule : rules.subList(0, deleted)) {
            machine.deleteRule(rule.name, rule.json);
        }
        final List<Rule> remaining = rules.subList(deleted, rules.size());
        assertUnion(context + " after delete, remaining=" + remaining, machine, remaining, eventValues,
                singleRuleMachines);
        assertSubRulesAreOnlyOnNameStatesThatTheirPatternsLeadTo(context + " after delete, remaining=" + remaining,
                machine, remaining);

        for (final Rule rule : remaining) {
            machine.deleteRule(rule.name, rule.json);
        }
        assertTrue(context + " not empty after deleting all, rules=" + rules, machine.isEmpty());
    }

    private static List<String> pickRuleValuesToShare(final Random random, final List<String> ruleValues) {
        final List<String> values = new ArrayList<>();
        for (int i = 0; i < RULE_VALUES_TO_SHARE; i++) {
            values.add(ruleValues.get(random.nextInt(ruleValues.size())));
        }
        if (random.nextBoolean()) {
            final String value = values.get(0);
            values.add(value.substring(0, value.offsetByCodePoints(0, 1)));
        }
        return values;
    }

    private static void assertUnion(final String context, final Machine machine, final List<Rule> rules,
                                    final List<String> values, final Map<String, Machine> singleRuleMachines)
            throws Exception {
        for (final String value : values) {
            final String event = "{\"" + FIELD + "\": \"" + value + "\"}";
            final Set<String> expected = new TreeSet<>();
            for (final Rule rule : rules) {
                if (!singleRuleMachine(rule, singleRuleMachines).rulesForJSONEvent(event).isEmpty()) {
                    expected.add(rule.name);
                }
            }
            assertEquals(context + " value=" + value, expected, new TreeSet<>(machine.rulesForJSONEvent(event)));
        }
    }

    private static void assertSubRulesAreOnlyOnNameStatesThatTheirPatternsLeadTo(final String context,
            final Machine machine, final List<Rule> rules) throws Exception {
        final ByteMachine byteMachine = machine.getStartState().getTransitionOn(FIELD);
        final Set<NameState> nameStates = reachableNameStates(byteMachine);
        for (final Rule rule : rules) {
            for (final Patterns pattern : JsonRuleCompiler.compile(rule.json).get(0).get(FIELD)) {
                final Set<NameState> nameStatesWithRule = new HashSet<>();
                for (final NameState nameState : nameStates) {
                    final Set<SubRuleContext> subRules = nameState.getTerminalSubRuleIdsForPattern(pattern);
                    if (subRules != null && subRules.stream().anyMatch(s -> rule.name.equals(s.getRuleName()))) {
                        nameStatesWithRule.add(nameState);
                    }
                }
                final Set<NameState> nameStatesOfPattern = byteMachine.findAllPatterns(pattern);
                final String message = "a sub-rule is only on NameStates that its pattern leads to, and on at least "
                        + "one: " + context + " rule=" + rule + " pattern=" + pattern + " NameStates with the rule="
                        + nameStatesWithRule + " NameStates of the pattern=" + nameStatesOfPattern;
                assertTrue(message, !nameStatesWithRule.isEmpty()
                        && nameStatesOfPattern.containsAll(nameStatesWithRule));
            }
        }
    }

    private static Set<NameState> reachableNameStates(final ByteMachine byteMachine) {
        final Set<NameState> nameStates = new HashSet<>();
        for (final ByteState state : ByteMachineTest.reachableByteStates(byteMachine)) {
            for (final ByteTransition transition : state.getTransitions()) {
                for (final SingleByteTransition single : transition.expand()) {
                    for (final ByteMatch match : single.getMatches()) {
                        nameStates.add(match.getNextNameState());
                    }
                }
            }
        }
        return nameStates;
    }

    private static Machine singleRuleMachine(final Rule rule, final Map<String, Machine> singleRuleMachines)
            throws Exception {
        Machine machine = singleRuleMachines.get(rule.json);
        if (machine == null) {
            machine = new Machine();
            machine.addRule("single", rule.json);
            singleRuleMachines.put(rule.json, machine);
        }
        return machine;
    }

    private static List<String> allValues(final String[] alphabet, final int minLength, final int maxLength) {
        final List<String> values = new ArrayList<>();
        List<String> previousLength = Collections.singletonList("");
        if (minLength == 0) {
            values.add("");
        }
        for (int length = 1; length <= maxLength; length++) {
            final List<String> currentLength = new ArrayList<>();
            for (final String prefix : previousLength) {
                for (final String character : alphabet) {
                    currentLength.add(prefix + character);
                }
            }
            if (length >= minLength) {
                values.addAll(currentLength);
            }
            previousLength = currentLength;
        }
        return values;
    }

    private static final class Rule {
        private final String name;
        private final String json;

        private Rule(final String name, final String pattern) {
            this.name = name;
            this.json = "{\"" + FIELD + "\": [" + pattern + "]}";
        }

        @Override
        public String toString() {
            return name + "=" + json;
        }
    }
}
