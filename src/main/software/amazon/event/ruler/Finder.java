package software.amazon.event.ruler;

import javax.annotation.concurrent.ThreadSafe;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static software.amazon.event.ruler.SetOperations.intersection;

/*
 * Notes on the implementation:
 *
 * This is finite-automaton based. The state machine matches field names exactly using a <String, String>
 *  map in the NameState class.  The value matching uses the "ByteMachine" class which, as its name suggests,
 *  runs a different kind of state machine over the bytes in the String-valued fields.
 *
 * Trying to make moveTo() faster by keeping unused Step structs around turns out to make it
 *  run slower. Let Java manage its memory.
 *
 * The Step processing could be done in parallel rather than sequentially; the use of a Queue for
 *  Steps has this in mind.  This could be done by having multiple threads reading
 *  from the task queue.  But it might be more straightforward to run multiple Finders in parallel.
 *  In which case you might want to share the machine; perhaps either static or a singleton.
 *
 * In this class and the other internal classes, we use rule names of type Object while in GenericMachine the rule name
 *  is of generic type T. This is safe as we only operate rule names of the same type T provided by a user. We use a
 *  generic type for rule names in GenericMachine for convenience and to avoid explicit type casts.
 */

/**
 *  Uses a state machine created by software.amazon.event.ruler.Machine to process tokens
 *   representing key-value pairs in an event, and return any matching Rules.
 */
@ThreadSafe
class Finder {

    private static final Patterns ABSENCE_PATTERN = Patterns.absencePatterns();

    private Finder() { }

    /**
     * Return any rules that match the fields in the event.
     *
     * @param event the fields are those from the JSON expression of the event, sorted by key.
     * @param machine the compiled state machine
     * @return list of rule names that match. The list may be empty but never null.
     */
    static List<Object> rulesForEvent(final String[] event, final GenericMachine<?> machine) {
        return find(new Task(event, machine));
    }

    /**
     * Return any rules that match the fields in the event.
     *
     * @param event the fields are those from the JSON expression of the event, sorted by key.
     * @param machine the compiled state machine
     * @return list of rule names that match. The list may be empty but never null.
     */
    static List<Object> rulesForEvent(final List<String> event, final GenericMachine<?> machine) {
        return find(new Task(event, machine));
    }

    private static List<Object> find(final Task task) {

        // bootstrap the machine: Start state, first token
        NameState startState = task.startState();
        if (startState == null) {
            return Collections.emptyList();
        }
        moveFrom(null, startState, 0, task);

        // each iteration removes a Step and adds zero or more new ones
        while (task.stepsRemain()) {
            tryStep(task);
        }

        return task.getMatchedRules();
    }

    // Move from a state.  Give all the remaining tokens a chance to transition from it
    private static void moveFrom(final Set<Double> candidateSubRuleIdsForNextStep, final NameState nameState,
                                 final int tokenIndex, final Task task) {
        /*
         * The Name Matchers look for an [ { exists: false } ] match. They
         * will match if a particular key is not present
         * in the event. Hence, if the name state has any matches configured
         * for the [ { exists: false } ] case, we need to evaluate these
         * matches regardless. The fields in the event can be completely
         * disconnected from the fields configured for [ { exists: false } ],
         * and it does not matter if the current field is used in machine.
         *
         * Another possibility is that there can be a final state configured for
         * [ { exists: false } ] match. This state needs to be evaluated for a match
         * even if we have matched all the keys in the event. This is needed because
         * the final state can still be evaluated to true if the particular event
         * does not have the key configured for [ { exists: false } ].
         */
        tryNameMatching(candidateSubRuleIdsForNextStep, nameState, task, tokenIndex);

        // Add more steps using our new set of candidate sub-rules.
        for (int i = tokenIndex; i < task.event.length; i += 2) {
            if (task.isFieldUsed(task.event[i])) {
                task.addStep(new Step(i, nameState, candidateSubRuleIdsForNextStep));
            }
        }
    }

    private static void moveFromWithPriorCandidates(final Set<Double> candidateSubRuleIds,
                                                    final NameState fromState, final Patterns fromPattern,
                                                    final int tokenIndex, final Task task) {
        Set<Double> candidateSubRuleIdsForNextStep = calculateCandidateSubRuleIdsForNextStep(candidateSubRuleIds,
                fromState, fromPattern);

        // If there are no more candidate sub-rules, there is no need to proceed further.
        if (candidateSubRuleIdsForNextStep != null && !candidateSubRuleIdsForNextStep.isEmpty()) {
            moveFrom(candidateSubRuleIdsForNextStep, fromState, tokenIndex, task);
        }

    }

    /**
     * Calculate the candidate sub-rule IDs for the next step.
     *
     * @param currentCandidateSubRuleIds The candidate sub-rule IDs for the current step. Use null to indicate that we
     *                                   are on first step and so there are not yet any candidate sub-rules.
     * @param fromState The NameState we are transitioning from.
     * @param fromPattern The pattern we used to transition from fromState.
     * @return The set of candidate sub-rule IDs for the next step. Null means there are no candidates and thus, there
     *         is no point to evaluating subsequent steps.
     */
    private static Set<Double> calculateCandidateSubRuleIdsForNextStep(final Set<Double> currentCandidateSubRuleIds,
                                                                       final NameState fromState,
                                                                       final Patterns fromPattern) {
        // These are all the sub-rules that use the matched pattern to transition to the next NameState. Note that they
        // are not all candidates as they may have required different values for previously evaluated fields.
        Set<Double> subRuleIds = fromState.getNonTerminalSubRuleIdsForPattern(fromPattern);

        // If no sub-rules used the matched pattern to transition to the next NameState, then there are no matches to be
        // found by going further.
        if (subRuleIds == null) {
            return null;
        }

        // If there are no candidate sub-rules, this means we are on the first NameState and must initialize the
        // candidate sub-rules to those that used the matched pattern to transition to the next NameState.
        if (currentCandidateSubRuleIds == null || currentCandidateSubRuleIds.isEmpty()) {
            return subRuleIds;
        }

        // There are candidate sub-rules, so retain only those that used the matched pattern to transition to the next
        // NameState.
        Set<Double> candidateSubRuleIdsForNextStep = new HashSet<>();
        intersection(subRuleIds, currentCandidateSubRuleIds, candidateSubRuleIdsForNextStep);
        return candidateSubRuleIdsForNextStep;
    }

    // remove a step from the work queue and see if there's a transition
    private static void tryStep(final Task task) {
        final Step step = task.nextStep();

        tryValueMatching(task, step);
    }

    private static void tryValueMatching(final Task task, Step step) {
        if (step.keyIndex >= task.event.length) {
            return;
        }

        String value = task.event[step.keyIndex];

        if (!task.isFieldUsed(value)) {
            return;
        }

        // if there are some possible value pattern matches for this key
        final ByteMachine valueMatcher = step.nameState.getTransitionOn(value);
        if (valueMatcher != null) {
            final int nextKeyIndex = step.keyIndex + 2;

            // loop through the value pattern matches
            for (NameStateWithPattern nextNameStateWithPattern : valueMatcher.transitionOn(task.event[step.keyIndex + 1])) {
                addNameState(step.candidateSubRuleIds, nextNameStateWithPattern.getNameState(),
                        nextNameStateWithPattern.getPattern(), task, nextKeyIndex);
            }
        }
    }

    private static void tryNameMatching(final Set<Double> candidateSubRuleIds, final NameState nameState,
                                        final Task task, int keyIndex) {
        if (!nameState.hasKeyTransitions()) {
            return;
        }

        for (NameState nextNameState : nameState.getNameTransitions(task.event)) {
            if (nextNameState != null) {
                addNameState(candidateSubRuleIds, nextNameState, ABSENCE_PATTERN, task, keyIndex);
            }
        }
    }

    private static void addNameState(Set<Double> candidateSubRuleIds, NameState nameState, Patterns pattern, Task task,
                                     int nextKeyIndex) {
        // one of the matches might imply a rule match
        task.collectRules(candidateSubRuleIds, nameState, pattern);

        moveFromWithPriorCandidates(candidateSubRuleIds, nameState, pattern, nextKeyIndex, task);
    }
}

