package software.amazon.event.ruler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import static software.amazon.event.ruler.SetOperations.intersection;

/**
 * Represents the state of an Array-Consistent rule-finding project.
 */
class ACTask {

    // the event we're matching rules to, and its fieldcount
    public final Event event;
    final int fieldCount;

    // the rules that matched the event, if we find any
    private final Set<Object> matchingRules = new HashSet<>();

    // Steps queued up for processing
    private final Queue<ACStep> stepQueue = new ArrayDeque<>();

    // the state machine
    private final GenericMachine<?> machine;

    ACTask(Event event, GenericMachine<?> machine) {
        this.event = event;
        this.machine = machine;
        fieldCount = event.fields.size();
    }

    NameState startState() {
        return machine.getStartState();
    }

    ACStep nextStep() {
        return stepQueue.remove();
    }

    /*
     *  Add a step to the queue for later consideration
     */
    void addStep(final int fieldIndex, final NameState nameState, final Set<Double> candidateSubRuleIds,
                 final ArrayMembership membershipSoFar) {
        stepQueue.add(new ACStep(fieldIndex, nameState, candidateSubRuleIds, membershipSoFar));
    }

    boolean stepsRemain() {
        return !stepQueue.isEmpty();
    }

    List<Object> getMatchedRules() {
        return new ArrayList<>(matchingRules);
    }

    void collectRules(final Set<Double> candidateSubRuleIds, final NameState nameState, final Patterns pattern) {
        Set<Double> terminalSubRuleIds = nameState.getTerminalSubRuleIdsForPattern(pattern);
        if (terminalSubRuleIds == null) {
            return;
        }

        // If no candidates, that means we're on the first step, so all sub-rules are candidates.
        if (candidateSubRuleIds == null || candidateSubRuleIds.isEmpty()) {
            for (Double terminalSubRuleId : terminalSubRuleIds) {
                matchingRules.add(nameState.getRule(terminalSubRuleId));
            }
        } else {
            intersection(candidateSubRuleIds, terminalSubRuleIds, matchingRules, id -> nameState.getRule(id));
        }
    }
}
