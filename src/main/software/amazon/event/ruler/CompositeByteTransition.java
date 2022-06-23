package software.amazon.event.ruler;

import javax.annotation.Nonnull;

/**
 * Represents a composite transition that has a next state and a match.
 */
final class CompositeByteTransition extends ByteTransition {

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
    public ByteTransition setNextByteState(ByteState nextState) {
        if (nextState == null) {
            return match;
        } else {
            this.nextState = nextState;
            return this;
        }
    }

    @Override
    public ByteMatch getMatch() {
        return match;
    }

    @Override
    public ByteTransition setMatch(ByteMatch match) {
        if (match == null) {
            return nextState;
        } else {
            this.match = match;
            return this;
        }
    }
}
