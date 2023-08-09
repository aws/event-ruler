package software.amazon.event.ruler;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Set;

/**
 * Represents a composite transition that has a next state and a match.
 */
final class CompositeByteTransition extends SingleByteTransition {

    /**
     * The state to transfer to.
     */
    private volatile ByteState nextState;

    /**
     * The first match of a linked list of matches that are triggered if this transition is made.
     */
    private volatile ByteMatch match;

    /**
     * Constructs a {@code CompositeByteTransition} instance with the given next state and the given match.
     *
     * @param nextState the next state
     * @param match     the match
     */
    CompositeByteTransition(@Nonnull ByteState nextState, @Nonnull ByteMatch match) {
        this.nextState = nextState;
        this.match = match;
    }

    @Override
    public ByteState getNextByteState() {
        return nextState;
    }

    @Override
    public SingleByteTransition setNextByteState(ByteState nextState) {
        if (nextState == null) {
            return match;
        } else {
            this.nextState = nextState;
            return this;
        }
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
        return Collections.EMPTY_SET;
    }

    @Override
    ByteMatch getMatch() {
        return match;
    }

    @Override
    public SingleByteTransition setMatch(ByteMatch match) {
        if (match == null) {
            return nextState;
        } else {
            this.match = match;
            return this;
        }
    }

    @Override
    public Set<ShortcutTransition> getShortcuts() {
        return Collections.emptySet();
    }

    @Override
    boolean hasIndeterminatePrefix() {
        return nextState == null ? false : nextState.hasIndeterminatePrefix();
    }

    @Override
    boolean isMatchTrans() {
        return true;
    }
}
