package software.amazon.event.ruler;

import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a suggestion of a state/token combo from which there might be a transition.  The event token
 *  indexed is always the key of a key/value combination
 */
@Immutable
@ThreadSafe
class Step {
    final int keyIndex;
    final NameState nameState;
    final Set<NameState.SubRule> candidateSubRules;

    Step(final int keyIndex, final NameState nameState, final Set<NameState.SubRule> candidateSubRules) {
        this.keyIndex = keyIndex;
        this.nameState = nameState;
        this.candidateSubRules = candidateSubRules;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Step step = (Step) o;
        return  keyIndex == step.keyIndex &&
                Objects.equals(nameState, step.nameState) &&
                Objects.equals(candidateSubRules, step.candidateSubRules);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyIndex, nameState, candidateSubRules);
    }
}