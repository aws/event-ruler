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
import java.util.Objects;
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

    /**
     * Generate context for a sub-rule that can be passed through relevant methods.
     */
    private final SubRuleContext.Generator subRuleContextGenerator = new SubRuleContext.Generator();

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
            addStep(keys, namePatterns, name);
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
     * @param namevals names and values which make up the rule
     */
    public void deletePatternRule(final T name, final Map<String, List<Patterns>> namevals) {
        if (namevals.size() > MAXIMUM_RULE_SIZE) {
            throw new RuntimeException("Size of rule '" + name + "' exceeds max value of " + MAXIMUM_RULE_SIZE);
        }
        final List<String> keys = new ArrayList<>(namevals.keySet());
        Collections.sort(keys);
        synchronized(this) {
            final List<String> deletedKeys =  new ArrayList<>();
            final Set<Double> candidateSubRuleIds = new HashSet<>();
            deleteStep(getStartState(), keys, 0, namevals, name, deletedKeys, candidateSubRuleIds);
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

    private Set<Double> deleteStep(final NameState state,
                                   final List<String> keys,
                                   final int keyIndex,
                                   final Map<String, List<Patterns>> patterns,
                                   final T ruleName,
                                   final List<String> deletedKeys,
                                   final Set<Double> candidateSubRuleIds) {

        final Set<Double> deletedSubRuleIds = new HashSet<>();
        final String key = keys.get(keyIndex);
        ByteMachine byteMachine = state.getTransitionOn(key);
        NameMatcher<NameState> nameMatcher = state.getKeyTransitionOn(key);

        // matchers are null, we have nothing to delete.
        if (byteMachine == null && nameMatcher == null) {
            return deletedSubRuleIds;
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
                boolean isTerminal = nextKeyIndex == keys.size();

                // Trim the candidate sub-rule ID set to contain only the sub-rule IDs present in the next NameState.
                Set<Double> nextNameStateSubRuleIds = isTerminal ?
                        nextNameState.getTerminalSubRuleIdsForPattern(pattern) :
                        nextNameState.getNonTerminalSubRuleIdsForPattern(pattern);
                // If no sub-rule IDs are found for next NameState, then we have no candidates, and will return below
                // without further recursion through the keys.
                if (nextNameStateSubRuleIds == null) {
                    candidateSubRuleIds.clear();
                    // If candidate set is empty, we are at first NameState, so initialize to next NameState's sub-rule IDs.
                    // When initializing, ensure that sub-rule IDs match the provided rule name for deletion.
                } else if (candidateSubRuleIds.isEmpty()) {
                    for (Double nextNameStateSubRuleId : nextNameStateSubRuleIds) {
                        if (Objects.equals(ruleName, nextNameState.getRule(nextNameStateSubRuleId))) {
                            candidateSubRuleIds.add(nextNameStateSubRuleId);
                        }
                    }
                    // Have already initialized candidate set. Just retain the candidates present in the next NameState.
                } else {
                    candidateSubRuleIds.retainAll(nextNameStateSubRuleIds);
                }

                if (isTerminal) {
                    for (Double candidateSubRuleId : candidateSubRuleIds) {
                        if (nextNameState.deleteSubRule(candidateSubRuleId, pattern, true)) {
                            deletedSubRuleIds.add(candidateSubRuleId);
                            // Only delete the pattern if the pattern does not transition to the next NameState.
                            if (!doesNameStateContainPattern(nextNameState, pattern) &&
                                    deletePattern(state, key, pattern)) {
                                deletedKeys.add(key);
                            }
                        }
                    }
                } else {
                    if (candidateSubRuleIds.isEmpty()) {
                        return deletedSubRuleIds;
                    }
                    deletedSubRuleIds.addAll(deleteStep(nextNameState, keys, nextKeyIndex, patterns, ruleName,
                            deletedKeys, new HashSet<>(candidateSubRuleIds)));

                    for (double deletedSubRuleId : deletedSubRuleIds) {
                        nextNameState.deleteSubRule(deletedSubRuleId, pattern, false);
                    }

                    // Unwinding the key recursion, so we aren't on a rule match. Only delete the pattern if the pattern
                    // does not transition to the next NameState.
                    if (!doesNameStateContainPattern(nextNameState, pattern) && deletePattern(state, key, pattern)) {
                        deletedKeys.add(key);
                    }
                }
            }

        }

        return deletedSubRuleIds;
    }

    private boolean doesNameStateContainPattern(final NameState nameState, final Patterns pattern) {
        return nameState.getTerminalPatterns().contains(pattern) ||
                nameState.getNonTerminalPatterns().contains(pattern);
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

    private void addStep(final List<String> keys,
                         final Map<String, List<Patterns>> patterns,
                         final T ruleName) {
        List<String> addedKeys = new ArrayList<>();
        Set<NameState> nameStates[] = new Set[keys.size()];
        if (addStep(getStartState(), keys, 0, patterns, ruleName, addedKeys, nameStates)) {
            SubRuleContext context = subRuleContextGenerator.generate();
            for (int i = 0; i < keys.size(); i++) {
                boolean isTerminal = i + 1 == keys.size();
                for (Patterns pattern : patterns.get(keys.get(i))) {
                    for (NameState nameState : nameStates[i]) {
                        nameState.addSubRule(ruleName, context.getId(), pattern, isTerminal);
                    }
                }
            }
        }
        addIntoUsedFields(addedKeys);
    }

    /**
     * Add a step, meaning keys and patterns, to the provided NameState.
     *
     * @param state NameState to add the step to.
     * @param keys All keys of the rule.
     * @param keyIndex The current index for keys.
     * @param patterns Map of key to patterns.
     * @param ruleName Name of the rule.
     * @param addedKeys Pass in an empty list - this tracks keys that have been added.
     * @param nameStatesForEachKey Pass in array of length keys.size() - this tracks NameStates accessible by each key.
     * @return True if and only if the keys and patterns being added represent a new sub-rule. Specifically, there
     *         exists at least one key or at least one pattern for a key not present in another sub-rule of the rule.
     */
    private boolean addStep(final NameState state,
                            final List<String> keys,
                            final int keyIndex,
                            final Map<String, List<Patterns>> patterns,
                            final T ruleName,
                            List<String> addedKeys,
                            final Set<NameState>[] nameStatesForEachKey) {

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
        // list in in case the BMC doesn't already have a next-step for this pattern and needs to make a new one
        NameState lastNextState = null;
        Set<NameState> nameStates = new HashSet<>();
        if (nameStatesForEachKey[keyIndex] == null) {
            nameStatesForEachKey[keyIndex] = new HashSet<>();
        }
        for (Patterns pattern : patterns.get(key)) {
            if (isNamePattern(pattern)) {
                lastNextState = nameMatcher.addPattern(pattern, lastNextState == null ? new NameState() : lastNextState);
            } else {
                assert byteMachine != null;
                lastNextState = byteMachine.addPattern(pattern, lastNextState);
            }
            nameStates.add(lastNextState);
            nameStatesForEachKey[keyIndex].add(lastNextState);
        }

        // Determine if we are adding a new rule or not. If we are not yet at the terminal key, go deeper recursively.
        // If we are at the terminal key, unwind recursion stack, checking each NameState to see if any pattern for
        // rule name is new. As soon as one rule+pattern for any NameState is new, we know we are processing a new
        // sub-rule and can continue returning true without further NameState checks.
        boolean isRuleNew = false;
        final int nextKeyIndex = keyIndex + 1;
        boolean isTerminal = nextKeyIndex == keys.size();
        for (NameState nameState : nameStates) {
            if (!isTerminal) {
                isRuleNew = addStep(nameState, keys, nextKeyIndex, patterns, ruleName, addedKeys, nameStatesForEachKey)
                        || isRuleNew;
            }
            if (!isRuleNew) {
                for (Patterns pattern : patterns.get(key)) {
                    if (!nameState.containsRule(ruleName, pattern)) {
                        isRuleNew = true;
                        break;
                    }
                }
            }
        }

        return isRuleNew;
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

    /**
     * Gives roughly the number of objects within the machine. This is useful to identify large rule-machines
     * that potentially require loads of memory. The method performs all of its calculation at runtime to avoid
     * taking up memory and making the impact of large rule-machines worse. When calculating this value; we
     * consider any transitions, states, byte-machines, and rules. There's are also a checks to ensure we're
     * not stuck in endless loops (that's possible for wildcard matches) or taking a long time for numeric range
     * matchers.
     *
     * NOTEs:
     * 1. As this method is dependent on number of internal objects, as ruler evolves this will also
     * give different results.
     * 2. It will also give you different results based on the order in which you add or remove rules as in
     * some-cases Ruler takes short-cuts for exact matches (see ShortcutTransition for more details).
     * 3. This method isn't thread safe, and so is prefixed with approximate.
     */
    public int approximateObjectCount() {
        final HashSet<Object> objectSet = new HashSet<>();
        startState.gatherObjects(objectSet);
        return objectSet.size();
    }

    @Override
    public String toString() {
        return "GenericMachine{" +
                "startState=" + startState +
                ", fieldStepsUsedRefCount=" + fieldStepsUsedRefCount +
                '}';
    }
}

