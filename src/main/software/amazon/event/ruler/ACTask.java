package software.amazon.event.ruler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Represents the state of an Array-Consistent rule-finding project.
 */
class ACTask {

    // the event we're matching rules to, and its fieldcount
    public final Event event;
    final int fieldCount;

    // the rules, if we find any
    private final HashSet<Object> rules = new HashSet<>();

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

    List<Object> getMatchedRules() {
        return new ArrayList<>(rules);
    }

    void collectRules(final NameState nameState) {
        rules.addAll(nameState.getRules());
    }
}
