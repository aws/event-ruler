package software.amazon.event.ruler;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;

import java.util.Collections;
import java.util.Set;

/**
 * Represents a state in a state machine and maps utf-8 bytes to transitions. One byte can have many transitions,
 * meaning this is an NFA state as opposed to a DFA state.
 */
@ThreadSafe
class ByteState extends SingleByteTransition {

    private final ByteMap map = new ByteMap();

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
        return map.isEmpty();
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
        return map.getTransition(utf8byte);
    }

    @Override
    ByteTransition getTransitionForAllBytes() {
        return map.getTransitionForAllBytes();
    }

    @Override
    Set<ByteTransition> getTransitions() {
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
        map.putTransition(utf8byte, transition);
    }

    /**
     * Associates the given transition with all possible byte values in this state. If the state previously contained
     * any transitions for any of the byte values, the old transitions are replaced by the given transition.
     *
     * @param transition the transition to be associated with the given byte value
     */
    void putTransitionForAllBytes(@Nonnull SingleByteTransition transition) {
        map.putTransitionForAllBytes(transition);
    }

    /**
     * Associates the given transition with the given byte value in this state. If the state previously contained any
     * transitions for the byte value, the given transition is added to these old transitions.
     *
     * @param utf8byte   the byte value with which the given transition is to be associated
     * @param transition the transition to be associated with the given byte value
     */
    void addTransition(byte utf8byte, @Nonnull SingleByteTransition transition) {
        map.addTransition(utf8byte, transition);
    }

    /**
     * Associates the given transition with all possible byte values in this state. If the state previously contained
     * any transitions for any of the byte values, the given transition is added to these old transitions.
     *
     * @param transition the transition to be associated with all possible byte values
     */
    void addTransitionForAllBytes(final SingleByteTransition transition) {
        map.addTransitionForAllBytes(transition);
    }

    /**
     * Removes all transitions for the given byte value from this state.
     *
     * @param utf8byte the byte value whose transitions are to be removed from the state
     */
    void removeTransition(byte utf8byte, SingleByteTransition transition) {
        map.removeTransition(utf8byte, transition);
    }

    /**
     * Removes the given transition for all possible byte values from this state.
     *
     * @param transition the transition to be removed for all possible byte values
     */
    void removeTransitionForAllBytes(final SingleByteTransition transition) {
        map.removeTransitionForAllBytes(transition);
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
        return map.numberOfTransitions() == 1 && map.hasTransition(this);
    }

    /**
     * Gets all the ceiling values contained in the ByteMap.
     *
     * @return All the ceiling values contained in the ByteMap.
     */
    Set<Integer> getCeilings() {
        return map.getCeilings();
    }

    @Override
    public String toString() {
        return "BS: " + map;
    }
}
