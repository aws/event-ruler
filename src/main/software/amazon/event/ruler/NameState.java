package software.amazon.event.ruler;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.concurrent.ThreadSafe;

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

    // All rules, both terminal and non-terminal, keyed by pattern, that led to this NameState.
    private final Map<Patterns, Set<Object>> patternToRules = new ConcurrentHashMap<>();

    // All terminal sub-rule IDs, keyed by pattern, that led to this NameState.
    private final Map<Patterns, Set<Double>> patternToTerminalSubRuleIds = new ConcurrentHashMap<>();

    // All non-terminal sub-rule IDs, keyed by pattern, that led to this NameState.
    private final Map<Patterns, Set<Double>> patternToNonTerminalSubRuleIds = new ConcurrentHashMap<>();

    // All sub-rule IDs mapped to the associated rule (name).
    private final Map<Double, Object> subRuleIdToRule = new ConcurrentHashMap<>();

    // All sub-rule IDs mapped to the number of times that sub-rule has been added to this NameState.
    private final Map<Double, Integer> subRuleIdToCount = new ConcurrentHashMap<>();

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
        return patternToTerminalSubRuleIds.keySet();
    }

    /**
     * Get all the non-terminal patterns that have led to this NameState. "Terminal" means the pattern was used by the
     * last field of a rule to lead to this NameState, and thus, the rule's matching criteria have been fully satisfied.
     *
     * NOTE: This function returns the raw key set from one of NameState's internal maps. Mutating it will corrupt the
     * state of NameState. Although standard coding practice would warrant returning a copy of the set, or wrapping it
     * to be immutable, we instead return the raw key set as a performance optimization. This avoids creating new data
     * structures and/or copying elements between them.
     *
     * @return Set of all non-terminal patterns.
     */
    Set<Patterns> getNonTerminalPatterns() {
        return patternToNonTerminalSubRuleIds.keySet();
    }

    /**
     * Get all the terminal sub-rule IDs that used a given pattern to lead to this NameState. "Terminal" means the last
     * field of the sub-rule led to this NameState, and thus, the sub-rule's matching criteria have been satisfied.
     *
     * NOTE: This function returns the raw key set from one of NameState's internal maps. Mutating it will corrupt the
     * state of NameState. Although standard coding practice would warrant returning a copy of the set, or wrapping it
     * to be immutable, we instead return the raw key set as a performance optimization since this is called repeatedly
     * on the critical matching path. This avoids creating new data structures and/or copying elements between them.
     *
     * @param pattern The pattern that the rules must use to get to this NameState.
     * @return The sub-rules, could be null if none for pattern.
     */
    Set<Double> getTerminalSubRuleIdsForPattern(Patterns pattern) {
        return patternToTerminalSubRuleIds.get(pattern);
    }

    /**
     * Get all the non-terminal sub-rule IDs that used a given pattern to lead to this NameState.
     *
     * NOTE: This function returns the raw key set from one of NameState's internal maps. Mutating it will corrupt the
     * state of NameState. Although standard coding practice would warrant returning a copy of the set, or wrapping it
     * to be immutable, we instead return the raw key set as a performance optimization since this is called repeatedly
     * on the critical matching path. This avoids creating new data structures and/or copying elements between them.
     *
     * @param pattern The pattern that the rules must use to get to this NameState.
     * @return The sub-rule IDs, could be null if none for pattern.
     */
    Set<Double> getNonTerminalSubRuleIdsForPattern(Patterns pattern) {
        return patternToNonTerminalSubRuleIds.get(pattern);
    }

    /**
     * Delete a sub-rule to indicate that it no longer transitions to this NameState using the provided pattern.
     *
     * @param subRuleId The ID of the sub-rule.
     * @param pattern The pattern used by the sub-rule to transition to this NameState.
     * @param isTerminal True indicates that the sub-rule is using pattern to match on the final event field.
     * @return True if and only if the sub-rule was found and deleted.
     */
    boolean deleteSubRule(final double subRuleId, final Patterns pattern, final boolean isTerminal) {
        deleteFromPatternToSetMap(patternToRules, pattern, subRuleIdToRule.get(subRuleId));
        Map<Patterns, ?> patternToSubRules = isTerminal ? patternToTerminalSubRuleIds : patternToNonTerminalSubRuleIds;
        boolean deleted = deleteFromPatternToSetMap(patternToSubRules, pattern, subRuleId);
        if (deleted) {
            Integer count = subRuleIdToCount.get(subRuleId);
            if (count == 1) {
                subRuleIdToCount.remove(subRuleId);
                subRuleIdToRule.remove(subRuleId);
            } else {
                subRuleIdToCount.put(subRuleId, count - 1);
            }
        }
        return deleted;
    }

    private static boolean deleteFromPatternToSetMap(final Map<Patterns, ?> map, final Patterns pattern,
                                                     final Object setElement) {
        boolean deleted = false;
        Set<?> set = (Set<?>) map.get(pattern);
        if (set != null) {
            deleted = set.remove(setElement);
            if (set.isEmpty()) {
                map.remove(pattern);
            }
        }
        return deleted;
    }

    void removeTransition(String name) {
        valueTransitions.remove(name);
    }

    void removeKeyTransition(String name) {
        mustNotExistMatchers.remove(name);
    }

    boolean isEmpty() {
        return  valueTransitions.isEmpty() &&
                mustNotExistMatchers.isEmpty() &&
                patternToRules.isEmpty() &&
                patternToTerminalSubRuleIds.isEmpty() &&
                patternToNonTerminalSubRuleIds.isEmpty() &&
                subRuleIdToRule.isEmpty() &&
                subRuleIdToCount.isEmpty();
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
        addToPatternToSetMap(patternToRules, pattern, rule);
        Map<Patterns, ?> patternToSubRules = isTerminal ? patternToTerminalSubRuleIds : patternToNonTerminalSubRuleIds;
        if (addToPatternToSetMap(patternToSubRules, pattern, subRuleId)) {
            Integer count = subRuleIdToCount.get(subRuleId);
            subRuleIdToCount.put(subRuleId, count == null ? 1 : count + 1);
            if (count == null) {
                subRuleIdToRule.put(subRuleId, rule);
            }
        }
    }

    private static boolean addToPatternToSetMap(final Map<Patterns, ?> map, final Patterns pattern,
                                                final Object setElement) {
        if (!map.containsKey(pattern)) {
            ((Map<Patterns, Set>) map).put(pattern, new HashSet<>());
        }
        return ((Set) map.get(pattern)).add(setElement);
    }

    Object getRule(Double subRuleId) {
        return subRuleIdToRule.get(subRuleId);
    }

    /**
     * Determines whether this NameState contains the provided rule accessible via the provided pattern.
     *
     * @param rule The rule, which may have multiple sub-rules.
     * @param pattern The pattern used by the rule to transition to this NameState.
     * @return True indicates that this NameState the provided rule for the provided pattern.
     */
    boolean containsRule(final Object rule, final Patterns pattern) {
        Set<Object> rules = patternToRules.get(pattern);
        return rules != null && rules.contains(rule);
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
            for (Map.Entry<Patterns, Set<Double>> entry : patternToTerminalSubRuleIds.entrySet()) {
                objectSet.add(entry.getKey());
                objectSet.addAll(entry.getValue());
            }
            for (Map.Entry<Patterns, Set<Double>> entry : patternToNonTerminalSubRuleIds.entrySet()) {
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
                ", patternToRules=" + patternToRules +
                ", patternToTerminalSubRuleIds=" + patternToTerminalSubRuleIds +
                ", patternToNonTerminalSubRuleIds=" + patternToNonTerminalSubRuleIds +
                ", subRuleIdToRule=" + subRuleIdToRule +
                ", subRuleIdToCount=" + subRuleIdToCount +
                '}';
    }
}