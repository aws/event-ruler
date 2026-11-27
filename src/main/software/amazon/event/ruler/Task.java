package software.amazon.event.ruler;

import javax.annotation.concurrent.ThreadSafe;
import java.util.ArrayDeque;
import java.util.ArrayList;
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

    // Lazily computed by nextUsedKeyIndex; slot k is for key index 2k. Task is used by one thread at a time.
    private int[] nextUsedKeyIndexes;

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

    /**
     * Whether the field name at {@code keyIndex} is used by any rule in the machine.
     *
     * @param keyIndex index of a field name in {@link #event}; must be even, as names and values alternate
     * @return true if {@code keyIndex} is in the event and every dot-separated step of its name is used by some rule
     */
    boolean isFieldUsed(final int keyIndex) {
        return keyIndex < event.length && nextUsedKeyIndex(keyIndex) == keyIndex;
    }

    /**
     * Returns the smallest index {@code i >= keyIndex} of a field name used by any rule, or {@code event.length}
     * if there is none. {@link Finder} calls this on every state transition to visit only the fields that can
     * transition, instead of re-testing every remaining field each time.
     *
     * <p>Computed once per Task, on the first call. Rules added to or deleted from the machine during this call
     * are seen as of that first call.
     *
     * @param keyIndex index of a field name in {@link #event}; must be even, as names and values alternate
     * @return the index of the next used field name, or {@code event.length}
     */
    int nextUsedKeyIndex(final int keyIndex) {
        if (keyIndex >= event.length) {
            return event.length;
        }
        int[] next = nextUsedKeyIndexes;
        if (next == null) {
            next = computeNextUsedKeyIndexes();
            nextUsedKeyIndexes = next;
        }
        return next[keyIndex >>> 1];
    }

    // Slot k holds the answer for key index 2k. An odd-length event has a name with no value at its last index;
    // that index is included, so the event fails in Finder exactly as it did before this table existed.
    private int[] computeNextUsedKeyIndexes() {
        final int[] next = new int[(event.length + 1) >>> 1];
        int nextUsed = event.length;
        for (int k = next.length - 1; k >= 0; k--) {
            final int keyIndex = k << 1;
            if (isFieldUsed(event[keyIndex], machine)) {
                nextUsed = keyIndex;
            }
            next[k] = nextUsed;
        }
        return next;
    }

    /**
     * Whether every dot-separated step of {@code field} is used by some rule in {@code machine}.
     *
     * <p>Steps are taken exactly as {@code field.split("\\.")} would take them: empty leading and inner steps are
     * kept, trailing empty steps are dropped, and a name made only of dots has no steps and so counts as used.
     */
    static boolean isFieldUsed(final String field, final GenericMachine<?> machine) {
        int dot = field.indexOf('.');
        if (dot < 0) {
            return machine.isFieldStepUsed(field);
        }
        int end = field.length();
        while (end > 0 && field.charAt(end - 1) == '.') {
            end--;
        }
        int start = 0;
        while (start < end) {
            final int stepEnd = (dot < 0 || dot > end) ? end : dot;
            if (!machine.isFieldStepUsed(field.substring(start, stepEnd))) {
                return false;
            }
            start = stepEnd + 1;
            dot = field.indexOf('.', start);
        }
        return true;
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
