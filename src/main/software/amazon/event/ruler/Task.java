package software.amazon.event.ruler;

import javax.annotation.concurrent.ThreadSafe;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * Represents the state of a rule-finding project.
 */
@ThreadSafe
class Task {

  public static final String[] INITIAL = new String[0];
  // What we're trying to match rules to
    public final String[] event;

    // the rules, if we find any
    private final Set<Object> rules = new HashSet<>();

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
        this(event.toArray(INITIAL), machine);
    }

    Task(final String[] event, final GenericMachine<?> machine) {
        this.event = event;
        this.machine = machine;
    }

    NameState startState() {
        return this.machine.getStartState();
    }

    // The field used means all steps in the field must be all used individually.
    boolean isFieldUsed(final String field) {
        if (field.contains(".")) {
            final String[] steps = field.split("\\.");
            return Arrays.stream(steps).allMatch(this.machine::isFieldStepUsed);
        }
        return this.machine.isFieldStepUsed(field);
    }

    Step nextStep() {
        return this.stepQueue.remove();
    }

    void addStep(final Step step) {
        // queue it up only if it's the first time we're trying to queue it up
        // otherwise bad things happen, see comment on seenSteps collection
        if (this.seenSteps.add(step)) {
          this.stepQueue.add(step);
        }
    }

    boolean stepsRemain() {
        return !this.stepQueue.isEmpty();
    }

    List<Object> getMatchedRules() {
        return new ArrayList<>(this.rules);
    }

    void collectRules(final NameState nameState) {
      this.rules.addAll(nameState.getRules());
    }
}