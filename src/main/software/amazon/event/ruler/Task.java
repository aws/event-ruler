package software.amazon.event.ruler;

import javax.annotation.concurrent.ThreadSafe;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import static software.amazon.event.ruler.SetOperations.intersection;

/**
 * Represents the state of a rule-finding project.
 */
@ThreadSafe
class Task {

    // What we're trying to match rules to
    public final String[] event;

    // the rules that matched the event, if we find any
    private final Set<Object> matchingRules = new HashSet<>();

    // Steps queued up for processing
    private final Queue<Step> stepQueue = new ArrayDeque<>();

    // Visiting the same Step multiple times will give the same outcome for all visits,
    // as we are not mutating the state of Task nor Step nor GenericMachine as part of the visit.
    // To prevent explosion of complexity we need to prevent queueing up steps that are still
    // waiting in the stepQueue or we have already ran. We end up holding on to some amount
    // of memory during Task evaluation to track this, but it has an upper bound that is O(n * m) where
    // n = number of values in input event
    // m = number of nodes in the compiled state machine
    private final Set<Step> seenSteps = new HashSet<>();

    // the state machine
    private final GenericMachine<?> machine;

    Task(final List<String> event, final GenericMachine<?> machine) {
        this(event.toArray(new String[0]), machine);
    }

    Task(final String[] event, final GenericMachine<?> machine) {
        this.event = event;
        this.machine = machine;
    }

    NameState startState() {
        return machine.getStartState();
    }

    // The field used means all steps in the field must be all used individually.
    boolean isFieldUsed(final String field) {
        if (field.contains(".")) {
            String[] steps = field.split("\\.");
            return Arrays.stream(steps).allMatch(machine::isFieldStepUsed);
        }
        return machine.isFieldStepUsed(field);
    }

    Step nextStep() {
        return stepQueue.remove();
    }

    void addStep(final Step step) {
        // queue it up only if it's the first time we're trying to queue it up
        // otherwise bad things happen, see comment on seenSteps collection
        if (seenSteps.add(step)) {
            stepQueue.add(step);
        }
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
