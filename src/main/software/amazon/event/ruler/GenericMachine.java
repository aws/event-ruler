package software.amazon.event.ruler;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

/**
 *  Represents a state machine used to match name/value patterns to rules.
 *  The machine is thread safe. The concurrency strategy is:
 *  Multi-thread access assumed, single-thread update enforced by synchronized on
 *  addRule/deleteRule.
 *  ConcurrentHashMap and ConcurrentSkipListSet are used so that writer and readers can be in tables
 *  simultaneously. So all changes the writer made could be synced to and viable by all readers (in other threads).
 *  Though it may  generate a half-built rule to rulesForEvent() e.g. when a long rule is adding and
 *  in the middle of adding, some event is coming to query machine, it won't generate side impact with rulesForEvent
 *  because each step of routing will check next State and transition map before moving forward.
 *
 *  T is a type representing a Rule name, it should be an immutable class.
 */
public class GenericMachine<T> {
    /**
     * This could be increased but is an initial control
     */
    private static final int MAXIMUM_RULE_SIZE = 256;

    /**
     * The start state of matching and adding rules.
     */
    private final NameState startState = new NameState();

    /**
     * A Map of all reference counts for each step in field name separated by "." used by current rules.
     * when reference count is 0, we could safely remove the key from map.
     * Use ConcurrentHashMap to support concurrent access.
     * For example, if we get rule { "a" : { "b" : [ 123 ] } }, the flatten field name is "a.b", the step name
     * will be "a" and "b", which will be tracked in this map.
     */
    private final Map<String, Integer> fieldStepsUsedRefCount = new ConcurrentHashMap<>();

    public GenericMachine() {}

    /**
     * Return any rules that match the fields in the event in a way that is Array-Consistent (thus trailing "AC" on
     *  names of implementing classes). Array-Consistent means that we reject matches where fields which are members
     *  of different elements of the same JSON array in the event are matched.
     * @param jsonEvent The JSON representation of the event
     * @return list of rule names that match. The list may be empty but never null.
     */
    @SuppressWarnings("unchecked")
    public List<T> rulesForJSONEvent(final String jsonEvent) throws Exception {
        final Event event = new Event(jsonEvent, this);
        return (List<T>) ACFinder.matchRules(event, this);
    }
    @SuppressWarnings("unchecked")
    public List<T> rulesForJSONEvent(final JsonNode eventRoot) {
        final Event event = new Event(eventRoot, this);
        return (List<T>) ACFinder.matchRules(event, this);
    }

    /**
     * Return any rules that match the fields in the event.
     *
     * @param jsonEvent The JSON representation of the event
     * @return list of rule names that match. The list may be empty but never null.
     * @deprecated The rulesForJSONEvent version provides array-consistent matching, which is probably what your users want.
     */
    @Deprecated
    @SuppressWarnings("unchecked")
    public List<T> rulesForEvent(final String jsonEvent) {
        return (List<T>) Finder.rulesForEvent(Event.flatten(jsonEvent), this);
    }

    /**
     * Return any rules that match the fields in the event.
     *
     * @param event the fields are those from the JSON expression of the event, sorted by key.
     * @return list of rule names that match. The list may be empty but never null.
     */
    @SuppressWarnings("unchecked")
    public List<T> rulesForEvent(final List<String> event) {
        return (List<T>) Finder.rulesForEvent(event, this);
    }

    /**
     * Return any rules that match the fields in the parsed Json event.
     *
     * @param eventRoot the root node of the parsed JSON.
     * @return list of rule names that match. The list may be empty but never null.
     * @deprecated The rulesForJSONEvent version provides array-consistent matching, which is probably what your users want.
     */
    @Deprecated
    @SuppressWarnings("unchecked")
    public List<T> rulesForEvent(final JsonNode eventRoot) {
        return (List<T>) Finder.rulesForEvent(Event.flatten(eventRoot), this);
    }

    /**
     * Return any rules that match the fields in the event.
     *
     * @param event the fields are those from the JSON expression of the event, sorted by key.
     * @return list of rule names that match. The list may be empty but never null.
     */
    @SuppressWarnings("unchecked")
    public List<T> rulesForEvent(final String[] event) {
        return (List<T>) Finder.rulesForEvent(event, this);
    }

    /**
     * The root state for the machine
     *
     * @return the root state, null if the machine has no rules in it
     */
    final NameState getStartState() {
        return startState;
    }

    /**
     * Check to see whether a field step name is actually used in any rule.
     *
     * @param stepName the field step name to check
     * @return true if the field is used in any rule, false otherwise
     */
    boolean isFieldStepUsed(final String stepName) {
        if (fieldStepsUsedRefCount.get(stepName) != null) {
            return true;
        }
        // We need split step name further with "." to check each sub step individually to cover the case if customer
        // use the "." internally in field of event, e.g. rule: {"a.b" : [123]} and {"a" : { "b" : [123] }} will both
        // result "a" and "b" being added into fieldStepsUsedRefCount map in addPatternRule API, at that time, we
        // cannot distinguish the "." is from original event or added by us when we flatten the event, but they are
        // both possible to match some rules, so we do this check.
        // The main reason we decided to put "recordStep" work in addPatternRule API is this API have been used by many
        // customer directly already and we want them to get the same performance boost.
        if (stepName.contains(".")) {
            String[] steps = stepName.split("\\.");
            return Arrays.stream(steps).allMatch(step -> (fieldStepsUsedRefCount.get(step) != null));
        }
        return false;
    }

    /**
     * Add a rule to the machine.  A rule is a set of names, each of which is associated with one or more values.
     *
     * "synchronized" here is to ensure that only one thread is updating the machine at any point in time.
     * This method relies on the caller not changing the arguments while it is exiting - but once it has completed,
     *  this library has no dependence on those structures.
     * Multiple rules with the same name may be incrementally added, producing an "or" relationship with
     *  relationship with previous namevals added for that name. E.g.
     *   addRule r1  with namevalue {a,[1]}, then call again with r1 {a,[2]}, it will have same effect call addRule with
     *   r1 {a, [1,2]}
     *
     * @param name ARN of the rule
     * @param namevals names and values which make up the rule
     */
    public void addRule(final T name, final Map<String, List<String>> namevals) {
        final Map<String, List<Patterns>> patternMap = new HashMap<>();
        for (final Map.Entry<String, List<String>> nameVal : namevals.entrySet()) {
            patternMap.put(nameVal.getKey(), nameVal.getValue().stream().map(Patterns::exactMatch).
                    collect(Collectors.toList()));
        }
        addPatternRule(name, patternMap);
    }

    /**
     * Add a rule to the machine. A rule is a set of names, each of which
     * is associated with one or more value-patterns. These can be simple values (like "1", "a", "true")
     * or more sophisticated (like {anything-but : "1", range: { ... } })
     *
     * @param name ARN of the rule4
     * @param namevals names and values which make up the rule
     */
    public void addPatternRule(final T name, final Map<String, List<Patterns>> namevals) {

        if (namevals.size() > MAXIMUM_RULE_SIZE) {
            throw new RuntimeException("Size of rule '" + name + "' exceeds max value of " + MAXIMUM_RULE_SIZE);
        }

        // clone namevals for ruler use.
        Map<String, List<Patterns>> namePatterns = new HashMap<>();
        for (final Map.Entry<String, List<Patterns>> nameVal : namevals.entrySet()) {
            namePatterns.put(nameVal.getKey(), nameVal.getValue().stream().map(p -> (Patterns)(p.clone())).
                    collect(Collectors.toList()));
        }

        final ArrayList<String> keys = new ArrayList<>(namePatterns.keySet());
        Collections.sort(keys);
        synchronized(this) {
            final List<String> addedKeys =  new ArrayList<>();
            addStep(getStartState(), keys, 0, namePatterns, name, addedKeys);
            addIntoUsedFields(addedKeys);
        }
    }

    /**
     * Delete rule from the machine.  The rule is a set of name/values.
     * "synchronized" here is to ensure that only one thread is updating the machine at any point in time.
     * This method relies on the caller not changing the arguments while it is exiting - but once it has completed,
     *  this library has no dependence on those structures.
     * As with addRule, multiple name/val patterns attached to a rule name may be removed all at once or
     *  one by one.
     * Machine will only remove a rule which both matches the name/vals and the provided rule name:
     *  - if name/vals provided have not reached any rules, return;
     *  - if name/vals provided have reached any rules, only rule which matched the input rule name will be removed.
     *
     * Note: this API will only probe rule which is able to be approached by input namevalues,
     *  it doesn't remove all rules associated with rule name unless the input rule expression have covered all rules
     *  associated with rule name. For example:
     *  r1 {a, [1,2]} was added into machine, you could call deleteRule with r1 {a, [1]}, machine in this case will
     *  delete r1 {a, [1]} only, so event like a=1 will not match r1 any more.
     *  if caller calls deleteRule again with input r1 {a, [2]}, machine will remove another rule of r1.
     *  The eventual result will be same as one time deleteRule call with r1 {a, [1,2]}.
     *  So, caller is expected to save its rule expression if want to entirely remove the rule unless deliberately
     *  want to remove partial rule from rule name.
     *
     * @param name ARN of the rule
     * @param namevals names & values which make up the rule
     */
    void deletePatternRule(final T name, final Map<String, List<Patterns>> namevals) {
        if (namevals.size() > MAXIMUM_RULE_SIZE) {
            throw new RuntimeException("Size of rule '" + name + "' exceeds max value of " + MAXIMUM_RULE_SIZE);
        }
        final List<String> keys = new ArrayList<>(namevals.keySet());
        Collections.sort(keys);
        synchronized(this) {
            final List<String> deletedKeys =  new ArrayList<>();
            deleteStep(getStartState(), keys, 0, namevals, name, deletedKeys);
            // check and delete the key from filedUsed ...
            checkAndDeleteUsedFields(deletedKeys);
        }

    }

    public void deleteRule(final T name, final Map<String, List<String>> namevals) {
        final Map<String, List<Patterns>> patternMap = new HashMap<>();
        for (final Map.Entry<String, List<String>> nameVal : namevals.entrySet()) {
            patternMap.put(nameVal.getKey(), nameVal.getValue().stream().map(Patterns::exactMatch)
                    .collect(Collectors.toList()));
        }
        deletePatternRule(name, patternMap);
    }

    private void deleteStep(final NameState state,
                            final List<String> keys,
                            final int keyIndex,
                            final Map<String, List<Patterns>> patterns,
                            final T ruleName,
                            List<String> deletedKeys) {

        final String key = keys.get(keyIndex);
        ByteMachine byteMachine = state.getTransitionOn(key);
        NameMatcher<NameState> nameMatcher = state.getKeyTransitionOn(key);

        // matchers are null, we have nothing to delete.
        if (byteMachine == null && nameMatcher == null) {
            return;
        }

        for (Patterns pattern : patterns.get(key)) {
            NameState nextNameState = null;
            if (isNamePattern(pattern)) {
                if (nameMatcher != null) {
                    nextNameState = nameMatcher.findPattern(pattern);
                }
            } else {
                if (byteMachine != null) {
                    nextNameState = byteMachine.findPattern(pattern);
                }
            }
            if (nextNameState != null) {
                // If this was the last step, then reaching the last state means the rule matched, and we should delete
                // the rule from the next NameState.
                final int nextKeyIndex = keyIndex + 1;
                if (nextKeyIndex == keys.size()) {
                    if (nextNameState.hasRuleWithPattern(ruleName, pattern)) {
                        nextNameState.deleteRuleWithPattern(ruleName, pattern);
                        // Only delete the pattern if:
                        //   1. There are no other rules using the same pattern also leading to the next NameState, and
                        //   2. The next NameState is a dead-end; it doesn't transition to a ByteMachine or NameMatcher.
                        Set<Patterns> nextPatterns = nextNameState.getPatterns();
                        boolean doesNextNameStateStillContainPattern = nextPatterns.contains(pattern);
                        if (!doesNextNameStateStillContainPattern && !nextNameState.hasTransitions()
                                && deletePattern(state, key, pattern)) {
                            deletedKeys.add(key);
                        }
                    }
                } else {
                    deleteStep(nextNameState, keys, nextKeyIndex, patterns, ruleName, deletedKeys);
                    // Unwinding the key recursion, so we aren't on a rule match. Only delete the pattern if the next
                    // NameState is a dead-end, meaning it doesn't transition to a ByteMachine or NameMatcher.
                    if (!nextNameState.hasTransitions() && deletePattern(state, key, pattern)) {
                        deletedKeys.add(key);
                    }
                }
            }

        }

    }

    /**
     * Delete given pattern from either NameMatcher or ByteMachine. Remove the parent NameState's transition to
     * NameMatcher or ByteMachine if empty after pattern deletion.
     *
     * @param parentNameState The NameState transitioning to the NameMatcher or ByteMachine.
     * @param key The key we transition on.
     * @param pattern The pattern to delete.
     * @return True if and only if transition from parent NameState was removed.
     */
    private boolean deletePattern(final NameState parentNameState, final String key, Patterns pattern) {
        if (isNamePattern(pattern)) {
            NameMatcher<NameState> nameMatcher = parentNameState.getKeyTransitionOn(key);
            nameMatcher.deletePattern(pattern);
            if (nameMatcher.isEmpty()) {
                parentNameState.removeKeyTransition(key);
                return true;
            }
        } else {
            ByteMachine byteMachine = parentNameState.getTransitionOn(key);
            byteMachine.deletePattern(pattern);
            if (byteMachine.isEmpty()) {
                parentNameState.removeTransition(key);
                return true;
            }
        }
        return false;
    }

    /**
     * Add a rule to the machine.  A rule is a set of names, each of which
     * is associated with one or more values.
     *
     * @param name ARN of the rule
     * @param json the JSON form of the rule
     */
    public void addRule(final T name, final String json) throws IOException {
        try {
            JsonRuleCompiler.compile(json).forEach(rule -> addPatternRule(name, rule));
        } catch (JsonParseException e) {
            addPatternRule(name, RuleCompiler.compile(json));
        }
    }

    /**
     * Add a rule to the machine.  A rule is a set of names, each of which
     * is associated with one or more values.
     *
     * @param name ARN of the rule
     * @param json the JSON form of the rule
     */
    public void addRule(final T name, final Reader json) throws IOException {
        try {
            JsonRuleCompiler.compile(json).forEach(rule -> addPatternRule(name, rule));
        } catch (JsonParseException e) {
            addPatternRule(name, RuleCompiler.compile(json));
        }
    }

    /**
     * Add a rule from the machine.  A rule is a set of names, each of which
     * is associated with one or more values.
     *
     * @param name ARN of the rule
     * @param json the JSON form of the rule
     */
    public void addRule(final T name, final InputStream json) throws IOException {
        try {
            JsonRuleCompiler.compile(json).forEach(rule -> addPatternRule(name, rule));
        } catch (JsonParseException e) {
            addPatternRule(name, RuleCompiler.compile(json));
        }
    }

    /**
     * Add a rule to the machine.  A rule is a set of names, each of which
     * is associated with one or more values.
     *
     * @param name ARN of the rule
     * @param json the JSON form of the rule
     */
    public void addRule(final T name, final byte[] json) throws IOException {
        try {
            JsonRuleCompiler.compile(json).forEach(rule -> addPatternRule(name, rule));
        } catch (JsonParseException e) {
            addPatternRule(name, RuleCompiler.compile(json));
        }
    }

    /**
     * Delete a rule from the machine.  A rule is a set of names, each of which
     * is associated with one or more values.
     *
     * @param name ARN of the rule
     * @param json the JSON form of the rule
     */
    public void deleteRule(final T name, final String json) throws IOException {
        try {
            JsonRuleCompiler.compile(json).forEach(rule -> deletePatternRule(name, rule));
        } catch (JsonParseException e) {
            deletePatternRule(name, RuleCompiler.compile(json));
        }
    }

    /**
     * Delete a rule from the machine.  A rule is a set of names, each of which
     * is associated with one or more values.
     *
     * @param name ARN of the rule
     * @param json the JSON form of the rule
     */
    public void deleteRule(final T name, final Reader json) throws IOException {
        try {
            JsonRuleCompiler.compile(json).forEach(rule -> deletePatternRule(name, rule));
        } catch (JsonParseException e) {
            deletePatternRule(name, RuleCompiler.compile(json));
        }
    }

    /**
     * Delete a rule to the machine.  A rule is a set of names, each of which
     * is associated with one or more values.
     *
     * @param name ARN of the rule
     * @param json the JSON form of the rule
     */
    public void deleteRule(final T name, final InputStream json) throws IOException {
        try {
            JsonRuleCompiler.compile(json).forEach(rule -> deletePatternRule(name, rule));
        } catch (JsonParseException e) {
            deletePatternRule(name, RuleCompiler.compile(json));
        }
    }

    private void addStep(final NameState state,
                         final List<String> keys,
                         final int keyIndex,
                         final Map<String, List<Patterns>> patterns,
                         final T ruleName,
                         List<String> addedKeys) {

        final String key = keys.get(keyIndex);
        ByteMachine byteMachine = state.getTransitionOn(key);
        NameMatcher<NameState> nameMatcher = state.getKeyTransitionOn(key);

        if (byteMachine == null && hasValuePatterns(patterns.get(key))) {
            byteMachine = new ByteMachine();
            state.addTransition(key, byteMachine);
            addedKeys.add(key);
        }

        if (nameMatcher == null && hasKeyPatterns(patterns.get(key))) {
            nameMatcher = createNameMatcher();
            state.addKeyTransition(key, nameMatcher);
            addedKeys.add(key);
        }
        // for each pattern, we'll provisionally add it to the BMC, which may already have it.  Pass the states
        //  list in in case the BMC doesn't already have a next-step for this pattern and needs to make a new one
        //
        NameState lastNextState = null;
        Set<NameState> nameStates = new HashSet<>();
        for (Patterns pattern : patterns.get(key)) {
            if (isNamePattern(pattern)) {
                assert nameMatcher != null;
                final NameState nameStateForSupplier = lastNextState == null ? new NameState() : lastNextState;
                lastNextState = nameMatcher.addPattern(pattern, () -> nameStateForSupplier);
            } else {
                assert byteMachine != null;
                lastNextState = byteMachine.addPattern(pattern, lastNextState);
            }
            nameStates.add(lastNextState);
        }

        for (NameState nameState : nameStates) {
            // if this was the last step, then reaching the last state means the rule matched.
            final int nextKeyIndex = keyIndex + 1;
            if (nextKeyIndex == keys.size()) {
                for (Patterns pattern : patterns.get(key)) {
                    nameState.addRuleWithPattern(ruleName, pattern);
                }
            } else {
                addStep(nameState, keys, nextKeyIndex, patterns, ruleName, addedKeys);
            }
        }

    }

    private boolean hasValuePatterns(List<Patterns> patterns) {
        return patterns.stream().anyMatch(p -> !isNamePattern(p));
    }

    private boolean hasKeyPatterns(List<Patterns> patterns) {
        return patterns.stream().anyMatch(this::isNamePattern);
    }

    private boolean isNamePattern(Patterns pattern) {
        return pattern.type() == MatchType.ABSENT;
    }

    private void addIntoUsedFields(List<String> keys) {
        for (String key : keys) {
            recordFieldStep(key);
        }
    }

    private void checkAndDeleteUsedFields(final List<String> keys) {
        for (String key : keys) {
            eraseFieldStep(key);
        }
    }

    public boolean isEmpty() {
        return startState.isEmpty() && fieldStepsUsedRefCount.isEmpty();
    }

    @Nonnull
    @SuppressWarnings("unchecked")
    private <R> NameMatcher<R> createNameMatcher() {
        return (NameMatcher<R>) new SingleStateNameMatcher();
    }


    private void recordFieldStep(String fieldName) {
        final String [] steps = fieldName.split("\\.");
        for (String step : steps) {
            fieldStepsUsedRefCount.compute(step, (k, v) -> (v == null) ? 1 : v + 1);
        }
    }

    private void eraseFieldStep(String fieldName) {
        final String [] steps = fieldName.split("\\.");
        for (String step : steps) {
            fieldStepsUsedRefCount.compute(step, (k, v) -> (v == 1) ? null : v - 1);
        }
    }

    public int evaluateComplexity(MachineComplexityEvaluator evaluator) {
        return startState.evaluateComplexity(evaluator);
    }

    @Override
    public String toString() {
        return "GenericMachine{" +
                "startState=" + startState +
                ", fieldStepsUsedRefCount=" + fieldStepsUsedRefCount +
                '}';
    }
}

