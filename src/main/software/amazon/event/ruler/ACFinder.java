package software.amazon.event.ruler;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Matches rules to events as does Finder, but in an array-consistent fashion, thus the AC prefix on the class name.
 */
class ACFinder {

    private ACFinder() { }

    /**
     * Return any rules that match the fields in the event, but enforce array consistency, i.e. reject
     *  matches where different matches come from the different elements in the same array in the event.
     *
     * @param event the Event structure containing the flattened event information
     * @param machine the compiled state machine
     * @return list of rule names that match. The list may be empty but never null.
     */
    static List<Object> matchRules(final Event event, final GenericMachine<?> machine) {
        return find(new ACTask(event, machine));
    }

    private static List<Object> find(final ACTask task) {

        // bootstrap the machine: Start state, first field
        NameState startState = task.startState();
        if (startState == null) {
            return Collections.emptyList();
        }
        moveFrom(null, new NameStateWithPattern(startState, null), 0, task, new ArrayMembership());

        // each iteration removes a Step and adds zero or more new ones
        while (task.stepsRemain()) {
            tryStep(task);
        }

        return task.getMatchedRules();
    }

    // remove a step from the work queue and see if there's a transition
    private static void tryStep(final ACTask task) {
        final ACStep step = task.nextStep();
        final Field field = task.event.fields.get(step.fieldIndex);

        // if we can step from where we are to the new field without violating array consistency
        final ArrayMembership newMembership = ArrayMembership.checkArrayConsistency(step.membershipSoFar, field.arrayMembership);
        if (newMembership != null) {

            // if there are some possible value pattern matches for this key
            final ByteMachine valueMatcher = step.nameState.getTransitionOn(field.name);
            if (valueMatcher != null) {

                // loop through the value pattern matches, if any
                final int nextFieldIndex = step.fieldIndex + 1;
                for (NameStateWithPattern nextNameStateWithPattern : valueMatcher.transitionOn(field.val)) {

                    // we have moved to a new NameState
                    // this NameState might imply a rule match
                    task.collectRules(step.candidateSubRuleIds, nextNameStateWithPattern);

                    // set up for attempting to move on from the new state
                    moveFrom(step, nextNameStateWithPattern, nextFieldIndex, task, newMembership);
                }
            }
        }
    }

    private static void tryMustNotExistMatch(final ACStep step, final NameState nameState, final ACTask task,
                                             int nextKeyIndex, final ArrayMembership arrayMembership) {
        if (!nameState.hasKeyTransitions()) {
            return;
        }

        for (NameState nextNameState : nameState.getNameTransitions(task.event, arrayMembership)) {
            if (nextNameState != null) {
                addNameState(step, new NameStateWithPattern(nextNameState, Patterns.absencePatterns()), task,
                        nextKeyIndex, arrayMembership);
            }
        }
    }

    // Move from a state.  Give all the remaining event fields a chance to transition from it
    private static void moveFrom(final ACStep step, final NameStateWithPattern fromStateWithPattern, int fieldIndex,
                                 final ACTask task, final ArrayMembership arrayMembership) {
        // There is no step for initial move. Use empty candidate sub-rule set when adding steps. Do not proceed with
        // rest of function as it is only relevant for subsequent moves.
        if (step == null) {
            tryMustNotExistMatch(null, fromStateWithPattern.getNameState(), task, fieldIndex, arrayMembership);

            while (fieldIndex < task.fieldCount) {
                // Calculate candidate sub-rule IDs for next step. If we have a pattern, use it to calculate candidates.
                // Otherwise, use empty candidate set, meaning we are on first step and everything is still a candidate.
                Set<Double> candidateSubRuleIdsForNextStep;
                if (fromStateWithPattern.getPattern() != null) {
                    candidateSubRuleIdsForNextStep = calculateCandidateSubRuleIdsForNextStep(null, fromStateWithPattern);
                    // If there are no more candidate sub-rule IDs, there is no need to proceed further.
                    if (candidateSubRuleIdsForNextStep == null || candidateSubRuleIdsForNextStep.isEmpty()) {
                        break;
                    }
                } else {
                    candidateSubRuleIdsForNextStep = new HashSet<>();
                }

                task.addStep(fieldIndex++, fromStateWithPattern.getNameState(), candidateSubRuleIdsForNextStep,
                        arrayMembership);
            }
            return;
        }

        Set<Double> candidateSubRuleIdsForNextStep = calculateCandidateSubRuleIdsForNextStep(step.candidateSubRuleIds,
                fromStateWithPattern);
        // If there are no more candidate sub-rules, there is no need to proceed further.
        if (candidateSubRuleIdsForNextStep == null || candidateSubRuleIdsForNextStep.isEmpty()) {
            return;
        }

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
        tryMustNotExistMatch(new ACStep(fieldIndex, fromStateWithPattern.getNameState(), candidateSubRuleIdsForNextStep,
                arrayMembership), fromStateWithPattern.getNameState(), task, fieldIndex, arrayMembership);

        // Add more steps using our new set of candidate sub-rules.
        while (fieldIndex < task.fieldCount) {
            task.addStep(fieldIndex++, fromStateWithPattern.getNameState(), candidateSubRuleIdsForNextStep,
                    arrayMembership);
        }
    }

    /**
     * Calculate the candidate sub-rule IDs for the next step.
     *
     * @param currentCandidateSubRuleIds The candidate sub-rule IDs for the current step. Use null to indicate that we
     *                                   are on first step and so there are not yet any candidate sub-rules.
     * @param fromStateWithPattern The NameStateWithPattern for the NameState we are transitioning from.
     * @return The set of candidate sub-rule IDs for the next step. Null means there are no candidates and thus, there
     *         is no point to evaluating subsequent steps.
     */
    private static Set<Double> calculateCandidateSubRuleIdsForNextStep(
            final Set<Double> currentCandidateSubRuleIds, final NameStateWithPattern fromStateWithPattern) {
        // Start out with the candidate sub-rule IDs from the current step.
        Set<Double> candidateSubRuleIdsForNextStep = new HashSet<>();
        if (currentCandidateSubRuleIds != null) {
            candidateSubRuleIdsForNextStep.addAll(currentCandidateSubRuleIds);
        }

        // These are all the sub-rule IDs that use the matched pattern to transition to the next NameState. Note that
        // they are not all candidates as they may have required different values for previously evaluated fields.
        Set<Double> subRuleIds = fromStateWithPattern.getNameState().getNonTerminalSubRuleIdsForPattern(
                fromStateWithPattern.getPattern());

        // If no sub-rules used the matched pattern to transition to the next NameState, then there are no matches to be
        // found by going further.
        if (subRuleIds == null) {
            return null;
        }

        // If there are no candidate sub-rules, this means we are on the first NameState and must initialize the
        // candidate sub-rules to those that used the matched pattern to transition to the next NameState. If there are
        // already candidates, then retain only those that used the matched pattern to transition to the next NameState.
        if (candidateSubRuleIdsForNextStep.isEmpty()) {
            candidateSubRuleIdsForNextStep.addAll(subRuleIds);
        } else {
            candidateSubRuleIdsForNextStep.retainAll(subRuleIds);
        }

        return candidateSubRuleIdsForNextStep;
    }

    private static void addNameState(ACStep step, NameStateWithPattern nameStateWithPattern, ACTask task,
                                     int nextKeyIndex, final ArrayMembership arrayMembership) {
        // one of the matches might imply a rule match
        task.collectRules(step == null ? new HashSet<>() : step.candidateSubRuleIds, nameStateWithPattern);

        moveFrom(step, nameStateWithPattern, nextKeyIndex, task, arrayMembership);
    }
}

