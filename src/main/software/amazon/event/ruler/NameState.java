package software.amazon.event.ruler;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.concurrent.ThreadSafe;

import static java.util.Objects.requireNonNull;

/**
 * Represents a state in the machine.
 *
 * The "valueTransitions" map is keyed by field name and yields a ByteMachine
 * that is used to match values.
 *
 * The "keyTransitions" map is keyed by field name and yields a NameMatcher
 * that is used to match keys for [ { exists: false } ].
 */
@ThreadSafe
class NameState {

    // the key is field name. the value is a value matcher which returns.  These have to be
    //  concurrent so they can be accessed while add/delete Rule is active in another thread,
    //  without any locks.
    private final Map<String, ByteMachine> valueTransitions = new ConcurrentHashMap<>();

    // the key is field name. the value is a name matcher which contains the next state after matching
    // an [ { exists: false } ].  These have to be concurrent so they can be accessed
    // while add/delete Rule is active in another thread, without any locks.
    private final Map<String, NameMatcher<NameState>> mustNotExistMatchers = new ConcurrentHashMap<>(1);

    // All terminal sub-rules, keyed by pattern, that led to this NameState.
    private final Map<Patterns, Set<SubRule>> patternToTerminalSubRules = new ConcurrentHashMap<>();

    // All non-terminal sub-rules, keyed by pattern, that led to this NameState.
    private final Map<Patterns, Set<SubRule>> patternToNonTerminalSubRules = new ConcurrentHashMap<>();

    ByteMachine getTransitionOn(final String token) {
        return valueTransitions.get(token);
    }

    /**
     * Get all the terminal patterns that have led to this NameState. "Terminal" means the pattern was used by the last
     * field of a rule to lead to this NameState, and thus, the rule's matching criteria have been fully satisfied.
     *
     * NOTE: This function returns the raw key set from one of NameState's internal maps. Mutating it will corrupt the
     * state of NameState. Although standard coding practice would warrant returning a copy of the set, or wrapping it
     * to be immutable, we instead return the raw key set as a performance optimization. This avoids creating new data
     * structures and/or copying elements between them.
     *
     * @return Set of all terminal patterns.
     */
    Set<Patterns> getTerminalPatterns() {
        return patternToTerminalSubRules.keySet();
    }

    /**
     * Get all the terminal sub-rules that used a given pattern to lead to this NameState. "Terminal" means the last
     * field of the sub-rule led to this NameState, and thus, the sub-rule's matching criteria have been satisfied.
     *
     * NOTE: This function returns the raw key set from one of NameState's internal maps. Mutating it will corrupt the
     * state of NameState. Although standard coding practice would warrant returning a copy of the set, or wrapping it
     * to be immutable, we instead return the raw key set as a performance optimization since this is called repeatedly
     * on the critical matching path. This avoids creating new data structures and/or copying elements between them.
     *
     * @param pattern The pattern that the rules must use to get to this NameState.
     * @return The sub-rules.
     */
    Set<SubRule> getTerminalSubRulesForPattern(Patterns pattern) {
        return patternToTerminalSubRules.get(pattern);
    }

    /**
     * Get all the non-terminal sub-rules that used a given pattern to lead to this NameState.
     *
     * NOTE: This function returns the raw key set from one of NameState's internal maps. Mutating it will corrupt the
     * state of NameState. Although standard coding practice would warrant returning a copy of the set, or wrapping it
     * to be immutable, we instead return the raw key set as a performance optimization since this is called repeatedly
     * on the critical matching path. This avoids creating new data structures and/or copying elements between them.
     *
     * @param pattern The pattern that the rules must use to get to this NameState.
     * @return The sub-rules.
     */
    Set<SubRule> getNonTerminalSubRulesForPattern(Patterns pattern) {
        return patternToNonTerminalSubRules.get(pattern);
    }

    /**
     * Get all the sub-rule IDs that match the provided rule, pattern, and isTerminal.
     *
     * @param rule The rule that led to this NameState.
     * @param pattern The pattern used by the given rule to lead to this NameState.
     * @param isTerminal Whether or not the last field of the rule was used to lead to this NameState.
     * @return All matching sub-rule IDs.
     */
    Set<Double> getSubRuleIds(final Object rule, final Patterns pattern, final boolean isTerminal) {
        Set<Double> subRuleIds = new HashSet<>();
        Set<SubRule> subRules = (isTerminal ? patternToTerminalSubRules : patternToNonTerminalSubRules).get(pattern);
        if (subRules != null) {
            for (SubRule subRule : subRules) {
                if (subRule.rule.equals(rule)) {
                    subRuleIds.add(subRule.id);
                }
            }
        }
        return subRuleIds;
    }

    /**
     * Delete a sub-rule to indicate that it no longer transitions to this NameState using the provided pattern.
     *
     * @param rule The rule, which may have multiple sub-rules.
     * @param subRuleId The ID of the sub-rule.
     * @param pattern The pattern used by the sub-rule to transition to this NameState.
     * @param isTerminal True indicates that the sub-rule is using pattern to match on the final event field.
     */
    void deleteSubRule(final Object rule, final double subRuleId, final Patterns pattern, final boolean isTerminal) {
        SubRule subRule = new SubRule(rule, subRuleId);
        Map<Patterns, Set<SubRule>> patternToSubRules =
                isTerminal ? patternToTerminalSubRules : patternToNonTerminalSubRules;
        Set<SubRule> subRules = patternToSubRules.get(pattern);
        if (subRules != null) {
            subRules.remove(subRule);
            if (subRules.isEmpty()) {
                patternToSubRules.remove(pattern);
            }
        }
    }

    void removeTransition(String name) {
        valueTransitions.remove(name);
    }

    void removeKeyTransition(String name) {
        mustNotExistMatchers.remove(name);
    }

    boolean hasTransitions() {
        return !valueTransitions.isEmpty() || !mustNotExistMatchers.isEmpty();
    }

    boolean isEmpty() {
        return  valueTransitions.isEmpty() &&
                patternToTerminalSubRules.isEmpty() &&
                patternToNonTerminalSubRules.isEmpty() &&
                mustNotExistMatchers.isEmpty();
    }

    /**
     * Add a sub-rule to indicate that it transitions to this NameState using the provided pattern.
     *
     * @param rule The rule, which may have multiple sub-rules.
     * @param subRuleId The ID of the sub-rule.
     * @param pattern The pattern used by the sub-rule to transition to this NameState.
     * @param isTerminal True indicates that the sub-rule is using pattern to match on the final event field.
     */
    void addSubRule(final Object rule, final double subRuleId, final Patterns pattern, final boolean isTerminal) {
        SubRule subRule = new SubRule(rule, subRuleId);
        Map<Patterns, Set<SubRule>> patternToSubRules =
                isTerminal ? patternToTerminalSubRules : patternToNonTerminalSubRules;
        if (!patternToSubRules.containsKey(pattern)) {
            patternToSubRules.put(pattern, new HashSet<>());
        }
        patternToSubRules.get(pattern).add(subRule);
    }

    void addTransition(final String key, final ByteMachine to) {
        valueTransitions.put(key, to);
    }

    void addKeyTransition(final String key, final NameMatcher<NameState> to) {
        mustNotExistMatchers.put(key, to);
    }

    NameMatcher<NameState> getKeyTransitionOn(final String token) {
        return mustNotExistMatchers.get(token);
    }

    boolean hasKeyTransitions() {
        return !mustNotExistMatchers.isEmpty();
    }

    Set<NameState> getNameTransitions(final String[] event) {

        Set<NameState> nextNameStates = new HashSet<>();

        if (mustNotExistMatchers.isEmpty()) {
            return nextNameStates;
        }

        Set<NameMatcher<NameState>> absentValues = new HashSet<>(mustNotExistMatchers.values());

        for (int i = 0; i < event.length; i += 2) {

            NameMatcher<NameState> matcher = mustNotExistMatchers.get(event[i]);
            if (matcher != null) {
                absentValues.remove(matcher);
                if (absentValues.isEmpty()) {
                    break;
                }
            }
        }

        for (NameMatcher<NameState> nameMatcher: absentValues) {
            nextNameStates.add(nameMatcher.getNextState());
        }

        return nextNameStates;
    }

    Set<NameState> getNameTransitions(final Event event, final ArrayMembership membership) {

        final Set<NameState> nextNameStates = new HashSet<>();

        if (mustNotExistMatchers.isEmpty()) {
            return nextNameStates;
        }

        Set<NameMatcher<NameState>> absentValues = new HashSet<>(mustNotExistMatchers.values());

        for (Field field :  event.fields) {
            NameMatcher<NameState> matcher = mustNotExistMatchers.get(field.name);
            if (matcher != null) {
                // we should only consider the field who doesn't violate array consistency.
                // normally, we should first check array consistency of field, then check mustNotExistMatchers, but
                // for performance optimization, we first check mustNotExistMatchers because the hashmap.get is cheaper
                // than AC check.
                if (ArrayMembership.checkArrayConsistency(membership, field.arrayMembership) != null) {
                    absentValues.remove(matcher);
                    if (absentValues.isEmpty()) {
                        break;
                    }
                }
            }
        }

        for (NameMatcher<NameState> nameMatcher: absentValues) {
            nextNameStates.add(nameMatcher.getNextState());
        }

        return nextNameStates;
    }

    public int evaluateComplexity(MachineComplexityEvaluator evaluator) {
        int maxComplexity = evaluator.getMaxComplexity();
        int complexity = 0;
        for (ByteMachine byteMachine : valueTransitions.values()) {
            complexity = Math.max(complexity, byteMachine.evaluateComplexity(evaluator));
            if (complexity >= maxComplexity) {
                return maxComplexity;
            }
        }
        return complexity;
    }

    public void gatherObjects(Set<Object> objectSet) {
        if (!objectSet.contains(this)) { // stops looping
            objectSet.add(this);
            for (ByteMachine byteMachine : valueTransitions.values()) {
                byteMachine.gatherObjects(objectSet);
            }
            for (Map.Entry<String, NameMatcher<NameState>> mustNotExistEntry : mustNotExistMatchers.entrySet()) {
                mustNotExistEntry.getValue().getNextState().gatherObjects(objectSet);
            }
            for (Map.Entry<Patterns, Set<SubRule>> entry : patternToTerminalSubRules.entrySet()) {
                objectSet.add(entry.getKey());
                objectSet.addAll(entry.getValue());
            }
            for (Map.Entry<Patterns, Set<SubRule>> entry : patternToNonTerminalSubRules.entrySet()) {
                objectSet.add(entry.getKey());
                objectSet.addAll(entry.getValue());
            }
        }
    }

    @Override
    public String toString() {
        return "NameState{" +
                "valueTransitions=" + valueTransitions +
                ", mustNotExistMatchers=" + mustNotExistMatchers +
                ", patternToTerminalSubRules=" + patternToTerminalSubRules +
                ", patternToNonTerminalSubRules=" + patternToNonTerminalSubRules +
                '}';
    }

    /**
     * Store an ID with a rule to represent a sub-rule, which differentiates between rules of the same name.
     */
    static class SubRule {
        private final Object rule;
        private final double id;

        SubRule(Object rule, double id) {
            this.rule = requireNonNull(rule);
            this.id = id;
        }

        Object getRule() {
            return rule;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || !(o instanceof SubRule)) {
                return false;
            }
            SubRule otherSubRule = (SubRule) o;
            return  rule.equals(otherSubRule.rule) &&
                    id == otherSubRule.id;
        }

        @Override
        public int hashCode() {
            return Objects.hash(rule, id);
        }
    }

}