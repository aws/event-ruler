package software.amazon.event.ruler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents the state of an Array-Consistent rule-finding project.
 */
class ACTask {

    // the event we're matching rules to, and its fieldcount
    public final Event event;
    final int fieldCount;

    // the sub-rules that matched the event, if we find any
    private final Set<NameState.SubRule> matchingSubRules = new HashSet<>();

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
    void addStep(final int fieldIndex, final NameState nameState, final Set<NameState.SubRule> candidateSubRules,
                 final ArrayMembership membershipSoFar) {
        stepQueue.add(new ACStep(fieldIndex, nameState, candidateSubRules, membershipSoFar));
    }

    boolean stepsRemain() {
        return !stepQueue.isEmpty();
    }

    List<Object> getMatchedRules() {
        return new ArrayList<>(matchingSubRules.stream()
                .map(r -> r.getRule())
                .collect(Collectors.toSet()));
    }

    void collectRules(final Set<NameState.SubRule> candidateSubRules, final NameStateWithPattern nameStateWithPattern) {
        Set<NameState.SubRule> terminalSubRules = nameStateWithPattern.getNameState().getTerminalSubRulesForPattern(
                nameStateWithPattern.getPattern());
        if (terminalSubRules == null) {
            return;
        }

        // If no candidates, that means we're on the first step, so all sub-rules are candidates.
        if (candidateSubRules.isEmpty()) {
            matchingSubRules.addAll(terminalSubRules);
        } else {
            for (NameState.SubRule subRule : candidateSubRules) {
                if (terminalSubRules.contains(subRule)) {
                    matchingSubRules.add(subRule);
                }
            }
        }
    }
}
