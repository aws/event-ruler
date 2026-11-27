package software.amazon.event.ruler;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Set;

final class CompositeByteTransition extends SingleByteTransition {

    private volatile ByteState nextState;

    private volatile ByteMatch match;

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
        return nextState != null && nextState.hasIndeterminatePrefix();
    }

    @Override
    boolean isMatchTrans() {
        return true;
    }
}
