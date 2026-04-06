package software.amazon.event.ruler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Array-consistent rule matching with guaranteed linear performance.
 *
 * <p>Indexes the event by dotted field path, then walks the compiled state
 * machine trie with direct path lookups instead of iterating all fields.
 * Array consistency is enforced by checking constraints during the walk.</p>
 *
 * <p>Once all rules in the machine have been matched, the walk exits early
 * to avoid unnecessary work on large events.</p>
 *
 * <p>Complexity: O(F + sum over all trie paths of V_i) where F = total event
 * fields and V_i = number of values for the i-th trie transition's path.
 * For a single large array of N elements with K rule conditions, this is
 * O(N*K) — linear in N. For sibling arrays of size N and M with conditions
 * on different paths, this is O(N+M) — no cross-product.</p>
 */
class StructuredFinder {

    private StructuredFinder() { }

    static List<Object> matchRules(final String json, final GenericMachine<?> machine,
                                   final SubRuleContext.Generator gen) throws Exception {
        final Event event = new Event(json, machine);
        final FieldIndex index = new FieldIndex(event);
        final int ruleCount = gen.getRuleCount();

        final Set<Object> matched = new HashSet<>();
        final NameState startState = machine.getStartState();
        if (startState != null) {
            try {
                walk(startState, index, event, null, new ArrayMembership(), gen, matched, ruleCount);
            } catch (Exception e) {
                // Concurrent modification during walk — return partial results.
                // This matches ACFinder's behavior: concurrent add/delete may
                // produce incomplete results but must not throw.
            }
        }
        return new ArrayList<>(matched);
    }

    /**
     * Walk the state machine trie. Returns true if all rules have been matched
     * (early-exit signal).
     */
    private static boolean walk(final NameState state,
                                final FieldIndex index,
                                final Event event,
                                final Set<SubRuleContext> candidates,
                                final ArrayMembership constraints,
                                final SubRuleContext.Generator gen,
                                final Set<Object> matched,
                                final int ruleCount) {
        // Handle exists:false transitions
        if (handleAbsence(state, event, index, candidates, constraints, gen, matched, ruleCount)) {
            return true;
        }

        // For each field name this state transitions on
        for (final String fieldName : state.getValueTransitionKeys()) {
            final List<IndexedValue> values = index.getConsistent(fieldName, constraints);
            if (values == null) {
                continue;
            }

            final ByteMachine valueMatcher = state.getTransitionOn(fieldName);

            // For each value at this path
            for (final IndexedValue iv : values) {
                // Check array consistency with current constraints
                final ArrayMembership merged =
                        ArrayMembership.checkArrayConsistency(constraints, iv.membership);
                if (merged == null) {
                    continue;
                }

                // Check if value matches any pattern
                for (final NameStateWithPattern nsp : valueMatcher.transitionOn(iv.val)) {
                    final NameState nextState = nsp.getNameState();
                    final Patterns pattern = nsp.getPattern();

                    // Collect terminal matches
                    collectRules(candidates, nextState, pattern, matched);
                    if (matched.size() >= ruleCount) {
                        return true;
                    }

                    // Recurse for non-terminal matches
                    final Set<SubRuleContext> nextCandidates =
                            nextCandidates(candidates, nextState, pattern);
                    if (nextCandidates != null && !nextCandidates.isEmpty()) {
                        if (walk(nextState, index, event, nextCandidates, merged, gen, matched, ruleCount)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Handle exists:false transitions. Returns true if all rules matched (early-exit).
     */
    private static boolean handleAbsence(final NameState state,
                                         final Event event,
                                         final FieldIndex index,
                                         final Set<SubRuleContext> candidates,
                                         final ArrayMembership constraints,
                                         final SubRuleContext.Generator gen,
                                         final Set<Object> matched,
                                         final int ruleCount) {
        if (!state.hasKeyTransitions()) {
            return false;
        }
        for (final NameState nextState : state.getNameTransitions(event, constraints)) {
            if (nextState != null) {
                final Patterns absencePattern = Patterns.absencePatterns();
                collectRules(candidates, nextState, absencePattern, matched);
                if (matched.size() >= ruleCount) {
                    return true;
                }

                final Set<SubRuleContext> nextCandidates =
                        nextCandidates(candidates, nextState, absencePattern);
                if (nextCandidates != null && !nextCandidates.isEmpty()) {
                    if (walk(nextState, index, event, nextCandidates, constraints, gen, matched, ruleCount)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void collectRules(final Set<SubRuleContext> candidates,
                                     final NameState state, final Patterns pattern,
                                     final Set<Object> matched) {
        final Set<SubRuleContext> terminals = state.getTerminalSubRuleIdsForPattern(pattern);
        if (terminals == null) {
            return;
        }
        if (candidates == null || candidates.isEmpty()) {
            for (final SubRuleContext t : terminals) {
                matched.add(t.getRuleName());
            }
        } else {
            SetOperations.intersection(candidates, terminals, matched,
                    SubRuleContext::getRuleName);
        }
    }

    private static Set<SubRuleContext> nextCandidates(final Set<SubRuleContext> current,
                                                      final NameState state,
                                                      final Patterns pattern) {
        final Set<SubRuleContext> ids = state.getNonTerminalSubRuleIdsForPattern(pattern);
        if (ids == null) {
            return null;
        }
        if (current == null || current.isEmpty()) {
            return ids;
        }
        final Set<SubRuleContext> result = new HashSet<>();
        SetOperations.intersection(ids, current, result);
        return result;
    }

    /**
     * Index of event fields by dotted path, with secondary index by array element
     * for O(1) same-element lookups.
     */
    static class FieldIndex {
        private final Map<String, List<IndexedValue>> byPath = new HashMap<>();
        private final Map<String, Map<Integer, Map<Integer, List<IndexedValue>>>> byElement = new HashMap<>();

        FieldIndex(final Event event) {
            for (final Field field : event.fields) {
                final IndexedValue iv = new IndexedValue(field.val, field.arrayMembership);
                byPath.computeIfAbsent(field.name, k -> new ArrayList<>()).add(iv);

                for (final IntIntMap.Entry entry : field.arrayMembership.entries()) {
                    byElement.computeIfAbsent(field.name, k -> new HashMap<>())
                            .computeIfAbsent(entry.getKey(), k -> new HashMap<>())
                            .computeIfAbsent(entry.getValue(), k -> new ArrayList<>())
                            .add(iv);
                }
            }
        }

        List<IndexedValue> getConsistent(final String fieldName, final ArrayMembership constraints) {
            final List<IndexedValue> all = byPath.get(fieldName);
            if (all == null) {
                return null;
            }
            if (constraints.isEmpty()) {
                return all;
            }

            final Map<Integer, Map<Integer, List<IndexedValue>>> fieldByArray = byElement.get(fieldName);
            if (fieldByArray != null) {
                for (final IntIntMap.Entry constraint : constraints.entries()) {
                    final Map<Integer, List<IndexedValue>> fieldByElem = fieldByArray.get(constraint.getKey());
                    if (fieldByElem != null) {
                        final List<IndexedValue> elemValues = fieldByElem.get(constraint.getValue());
                        if (elemValues == null) {
                            return null;
                        }
                        if (constraints.size() <= 1) {
                            return elemValues;
                        }
                        final List<IndexedValue> result = new ArrayList<>();
                        for (final IndexedValue iv : elemValues) {
                            if (ArrayMembership.checkArrayConsistency(constraints, iv.membership) != null) {
                                result.add(iv);
                            }
                        }
                        return result.isEmpty() ? null : result;
                    }
                }
            }

            return all;
        }
    }

    static class IndexedValue {
        final String val;
        final ArrayMembership membership;

        IndexedValue(final String val, final ArrayMembership membership) {
            this.val = val;
            this.membership = membership;
        }
    }
}
