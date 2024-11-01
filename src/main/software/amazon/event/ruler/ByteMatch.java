package software.amazon.event.ruler;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a place in a ByteMachine where we have to do something in addition to just transitioning to another step.
 */
final class ByteMatch extends SingleByteTransition  {

    private final Patterns pattern;
    private final NameState nextNameState;  // the next state in the higher-level name/value machine

    ByteMatch(Patterns pattern, NameState nextNameState) {
        this.pattern = pattern;
        this.nextNameState = nextNameState;
    }

    @Override
    public ByteState getNextByteState() {
        return null;
    }

    @Override
    public SingleByteTransition setNextByteState(ByteState nextState) {
        return nextState == null ? this : new CompositeByteTransition(nextState, this);
    }

    @Override
    public ByteTransition getTransition(byte utf8byte) {
        return null;
    }

    @Override
    public ByteTransition getTransitionForAllBytes() {
        return null;
    }

    @Override
    public Set<ByteTransition> getTransitions() {
        return Collections.emptySet();
    }

    @Override
    ByteMatch getMatch() {
        return this;
    }

    @Override
    public SingleByteTransition setMatch(ByteMatch match) {
        return match;
    }

    @Override
    public Set<ShortcutTransition> getShortcuts() {
        return Collections.emptySet();
    }

    Patterns getPattern() {
        return pattern;
    }
    NameState getNextNameState() { return nextNameState; }

    @Override
    boolean isMatchTrans() {
        return true;
    }

    public void gatherObjects(Set<Object> objectSet, int maxObjectCount) {
        if (!objectSet.contains(this) && objectSet.size() < maxObjectCount) { // stops looping
            objectSet.add(this);
            nextNameState.gatherObjects(objectSet, maxObjectCount);
        }
    }

    @Override
    public String toString() {
        return "BM: HC=" + hashCode() + " P=" + pattern  + "(" + pattern.pattern() + ") NNS=" + nextNameState;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ByteMatch byteMatch = (ByteMatch) o;
        return Objects.equals(pattern, byteMatch.pattern) && Objects.equals(nextNameState, byteMatch.nextNameState);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pattern, nextNameState);
    }
}
