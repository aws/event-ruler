package software.amazon.event.ruler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents a state in a state machine and maps utf-8 bytes to transitions. One byte can have many transitions,
 * meaning this is an NFA state as opposed to a DFA state.
 */
@ThreadSafe
class ByteState extends SingleByteTransition {

    /**
     * The field is a {@link ByteMap}, however, to optimize on memory, this field may be {@code null} when this state
     * contains no transitions, and may be a {@link SingleByteTransitionEntry} when this state contains one transition.
     */
    @Nullable
    private volatile Object transitionStore;

    /* True if this state's placement in the machine means there is more than one possible value that can be matched on
     * a traversal from the start state to this state. This will happen, for example, when a wildcard or regex
     * expression occurs between the start state and this state.
     */
    private boolean hasIndeterminatePrefix = false;

    ByteState getNextByteState() {
        return this;
    }

    @Override
    public SingleByteTransition setNextByteState(ByteState nextState) {
        return nextState;
    }

    @Override
    ByteMatch getMatch() {
        return null;
    }

    @Override
    public SingleByteTransition setMatch(ByteMatch match) {
        return match == null ? this : new CompositeByteTransition(this, match);
    }

    @Override
    public Set<ShortcutTransition> getShortcuts() {
        return Collections.emptySet();
    }

    /**
     * Returns {@code true} if this state contains no transitions.
     *
     * @return {@code true} if this state contains no transitions
     */
    boolean hasNoTransitions() {
        return transitionStore == null;
    }

    /**
     * Returns the transition to which the given byte value is mapped, or {@code null} if this state contains no
     * transitions for the given byte value.
     *
     * @param utf8byte the byte value whose associated transition is to be returned
     * @return the transition to which the given byte value is mapped, or {@code null} if this state contains no
     * transitions for the given byte value
     */
    @Override
    ByteTransition getTransition(byte utf8byte) {
        // Saving the value to avoid reading an updated value
        Object transitionStore = this.transitionStore;
        if (transitionStore == null) {
            return null;
        } else if (transitionStore instanceof SingleByteTransitionEntry) {
            SingleByteTransitionEntry entry = (SingleByteTransitionEntry) transitionStore;
            return utf8byte == entry.utf8byte ? entry.transition : null;
        }
        ByteMap map = (ByteMap) transitionStore;
        return map.getTransition(utf8byte);
    }

    @Override
    ByteTransition getTransitionForAllBytes() {
        // Saving the value to avoid reading an updated value
        Object transitionStore = this.transitionStore;
        if (transitionStore == null || transitionStore instanceof SingleByteTransitionEntry) {
            return ByteMachine.EmptyByteTransition.INSTANCE;
        }
        ByteMap map = (ByteMap) transitionStore;
        return map.getTransitionForAllBytes();
    }

    @Override
    Iterable<ByteTransition> getTransitions() {
        // Saving the value to avoid reading an updated value
        Object transitionStore = this.transitionStore;
        if (transitionStore == null) {
            return Collections.emptySet();
        } else if (transitionStore instanceof SingleByteTransitionEntry) {
            SingleByteTransitionEntry entry = (SingleByteTransitionEntry) transitionStore;
            return entry.transition;
        }
        ByteMap map = (ByteMap) transitionStore;
        return map.getTransitions();
    }

    /**
     * Associates the given transition with the given byte value in this state. If the state previously contained any
     * transitions for the byte value, the old transitions are replaced by the given transition.
     *
     * @param utf8byte   the byte value with which the given transition is to be associated
     * @param transition the transition to be associated with the given byte value
     */
    void putTransition(byte utf8byte, @Nonnull SingleByteTransition transition) {
        // Saving the value to avoid reading an updated value
        Object transitionStore = this.transitionStore;
        if (transitionStore == null) {
            this.transitionStore = new SingleByteTransitionEntry(utf8byte, transition);
        } else if (transitionStore instanceof SingleByteTransitionEntry) {
            SingleByteTransitionEntry entry = (SingleByteTransitionEntry) transitionStore;
            if (utf8byte == entry.utf8byte) {
                entry.transition = transition;
            } else {
                ByteMap map = new ByteMap();
                map.putTransition(entry.utf8byte, entry.transition);
                map.putTransition(utf8byte, transition);
                this.transitionStore = map;
            }
        } else {
            ByteMap map = (ByteMap) transitionStore;
            map.putTransition(utf8byte, transition);
        }
    }

    /**
     * Associates the given transition with all possible byte values in this state. If the state previously contained
     * any transitions for any of the byte values, the old transitions are replaced by the given transition.
     *
     * @param transition the transition to be associated with the given byte value
     */
    void putTransitionForAllBytes(@Nonnull SingleByteTransition transition) {
        ByteMap map = new ByteMap();
        map.putTransitionForAllBytes(transition);
        this.transitionStore = map;
    }

    /**
     * Associates the given transition with the given byte value in this state. If the state previously contained any
     * transitions for the byte value, the given transition is added to these old transitions.
     *
     * @param utf8byte   the byte value with which the given transition is to be associated
     * @param transition the transition to be associated with the given byte value
     */
    void addTransition(byte utf8byte, @Nonnull SingleByteTransition transition) {
        // Saving the value to avoid reading an updated value
        Object transitionStore = this.transitionStore;
        if (transitionStore == null) {
            this.transitionStore = new SingleByteTransitionEntry(utf8byte, transition);
        } else if (transitionStore instanceof SingleByteTransitionEntry) {
            SingleByteTransitionEntry entry = (SingleByteTransitionEntry) transitionStore;
            ByteMap map = new ByteMap();
            map.addTransition(entry.utf8byte, entry.transition);
            map.addTransition(utf8byte, transition);
            this.transitionStore = map;
        } else {
            ByteMap map = (ByteMap) transitionStore;
            map.addTransition(utf8byte, transition);
        }
    }

    /**
     * Associates the given transition with all possible byte values in this state. If the state previously contained
     * any transitions for any of the byte values, the given transition is added to these old transitions.
     *
     * @param transition the transition to be associated with all possible byte values
     */
    void addTransitionForAllBytes(final SingleByteTransition transition) {
        // Saving the value to avoid reading an updated value
        Object transitionStore = this.transitionStore;
        ByteMap map;
        if (transitionStore instanceof ByteMap) {
            map = (ByteMap) transitionStore;
        } else {
            map = new ByteMap();
        }
        if (transitionStore instanceof SingleByteTransitionEntry) {
            SingleByteTransitionEntry entry = (SingleByteTransitionEntry) transitionStore;
            map.addTransition(entry.utf8byte, entry.transition);
        }
        map.addTransitionForAllBytes(transition);
        this.transitionStore = map;
    }

    /**
     * Removes provided transition for the given byte value from this state.
     *
     * @param utf8byte the byte value for which to remove transition from the state
     * @param transition remove this transition for provided byte value from the state
     */
    void removeTransition(byte utf8byte, SingleByteTransition transition) {
        // Saving the value to avoid reading an updated value
        Object transitionStore = this.transitionStore;
        if (transitionStore == null) {
            return;
        }

        if (transitionStore instanceof SingleByteTransitionEntry) {
            SingleByteTransitionEntry entry = (SingleByteTransitionEntry) transitionStore;
            if (utf8byte == entry.utf8byte && transition.equals(entry.transition)) {
                this.transitionStore = null;
            }
        } else {
            ByteMap map = (ByteMap) transitionStore;
            map.removeTransition(utf8byte, transition);
            if (map.isEmpty()) {
                this.transitionStore = null;
            }
        }
    }

    /**
     * Removes the given transition for all possible byte values from this state.
     *
     * @param transition the transition to be removed for all possible byte values
     */
    void removeTransitionForAllBytes(final SingleByteTransition transition) {
        // Saving the value to avoid reading an updated value
        Object transitionStore = this.transitionStore;
        if (transitionStore == null) {
            return;
        }

        if (transitionStore instanceof SingleByteTransitionEntry) {
            SingleByteTransitionEntry entry = (SingleByteTransitionEntry) transitionStore;
            if (transition.equals(entry.transition)) {
                this.transitionStore = null;
            }
        } else {
            ByteMap map = (ByteMap) transitionStore;
            map.removeTransitionForAllBytes(transition);
            if (map.isEmpty()) {
                this.transitionStore = null;
            }
        }
    }

    @Override
    boolean hasIndeterminatePrefix() {
        return hasIndeterminatePrefix;
    }

    void setIndeterminatePrefix(boolean hasIndeterminatePrefix) {
        this.hasIndeterminatePrefix = hasIndeterminatePrefix;
    }

    /**
     * Return true if this state has a self-referential transition and no others.
     *
     * @return True if this state has a self-referential transition and no others, false otherwise.
     */
    boolean hasOnlySelfReferentialTransition() {
        // Saving the value to avoid reading an updated value
        Object transitionStore = this.transitionStore;
        if (transitionStore == null) {
            return false;
        } else if (transitionStore instanceof SingleByteTransitionEntry) {
            SingleByteTransitionEntry entry = (SingleByteTransitionEntry) transitionStore;
            return this.equals(entry.transition);
        }
        ByteMap map = (ByteMap) transitionStore;
        return map.numberOfTransitions() == 1 && map.hasTransition(this);
    }

    /**
     * Gets all the ceiling values contained in the ByteMap or SingleByteTransitionEntry.
     *
     * @return All the ceiling values contained in the ByteMap or SingleByteTransitionEntry.
     */
    Set<Integer> getCeilings() {
        // Saving the value to avoid reading an updated value
        Object transitionStore = this.transitionStore;
        if (transitionStore == null) {
            return Collections.emptySet();
        } else if (transitionStore instanceof SingleByteTransitionEntry) {
            SingleByteTransitionEntry entry = (SingleByteTransitionEntry) transitionStore;
            int index = entry.utf8byte & 0xFF;
            if (index == 0) {
                return Stream.of(index + 1, 256).collect(Collectors.toSet());
            } else if (index == 255) {
                return Stream.of(index, index + 1).collect(Collectors.toSet());
            } else {
                return Stream.of(index, index + 1, 256).collect(Collectors.toSet());
            }
        }
        ByteMap map = (ByteMap) transitionStore;
        return map.getCeilings();
    }

    @Override
    public String toString() {
        return "BS: " + transitionStore;
    }


    private static final class SingleByteTransitionEntry {

        final byte utf8byte;
        volatile SingleByteTransition transition;

        SingleByteTransitionEntry(byte utf8byte, SingleByteTransition transition) {
            this.utf8byte = utf8byte;
            this.transition = transition;
        }

        @Override
        public String toString() {
            ByteState next = transition.getNextByteState();
            String nextLabel = (next == null) ? "null" : Integer.toString(next.hashCode());
            return "SBTE: " + (char) utf8byte + "=>" + nextLabel;
        }
    }
}
