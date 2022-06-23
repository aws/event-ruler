package software.amazon.event.ruler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;

/**
 * Represents the state of an Array-Consistent rule-finding project.
 */
class ACTask {

    // the event we're matching rules to, and its fieldcount
    public final Event event;
    final int fieldCount;

    // the rules, if we find any
    private final HashSet<Object> rules = new HashSet<>();

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
    void addStep(final int fieldIndex, final NameState nameState, final ArrayMembership membershipSoFar) {
        stepQueue.add(new ACStep(fieldIndex, nameState, membershipSoFar));
    }

    boolean stepsRemain() {
        return !stepQueue.isEmpty();
    }

    List<Object> getMatchedRules() {
        return new ArrayList<>(rules);
    }

    void collectRules(final NameState nameState) {
        rules.addAll(nameState.getRules());
    }
}
