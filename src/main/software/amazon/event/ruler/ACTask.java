package software.amazon.event.ruler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import static software.amazon.event.ruler.SetOperations.intersection;

class ACTask {

    public final Event event;
    final int fieldCount;

    private final Set<Object> matchingRules = new HashSet<>();

    private final Queue<ACStep> stepQueue = new ArrayDeque<>();

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

    void addStep(final int fieldIndex, final NameState nameState, final Set<SubRuleContext> candidateSubRuleIds,
                 final ArrayMembership membershipSoFar) {
        stepQueue.add(new ACStep(fieldIndex, nameState, candidateSubRuleIds, membershipSoFar));
    }

    boolean stepsRemain() {
        return !stepQueue.isEmpty();
    }

    List<Object> getMatchedRules() {
        return new ArrayList<>(matchingRules);
    }

    void collectRules(final Set<SubRuleContext> candidateSubRuleIds, final NameState nameState, final Patterns pattern,
                      final SubRuleContext.Generator subRuleContextGenerator) {
        Set<SubRuleContext> terminalSubRuleIds = nameState.getTerminalSubRuleIdsForPattern(pattern);
        if (terminalSubRuleIds == null) {
            return;
        }

        // If no candidates, that means we're on the first step, so all sub-rules are candidates.
        if (candidateSubRuleIds == null || candidateSubRuleIds.isEmpty()) {
            for (SubRuleContext terminalSubRuleId : terminalSubRuleIds) {
                matchingRules.add(terminalSubRuleId.getRuleName());
            }
        } else {
            intersection(candidateSubRuleIds, terminalSubRuleIds, matchingRules,
                    SubRuleContext::getRuleName);
        }
    }
}
