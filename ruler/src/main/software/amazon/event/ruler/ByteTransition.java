package software.amazon.event.ruler;

import java.util.Set;

/**
 * Represents a transition (on a particular byte value) from a state to a state.
 */
abstract class ByteTransition implements Cloneable {

    /**
     * Returns the state to transfer to.
     *
     * @return the state to transfer to, or {@code null} if this transition does not transfer to a state
     */
    abstract ByteState getNextByteState();

    /**
     * Sets the next state. The method returns this or a new transition that contains the given next state if the
     * next state is not {@code null}. Otherwise, the method returns {@code null} or a new transition that does not
     * support transfer.
     *
     * @param nextState the next state
     * @return this or a new transition that contains the given next state if the next state is not {@code null};
     * otherwise, the method returns {@code null} or a new transition that does not support transfer
     */
    abstract SingleByteTransition setNextByteState(ByteState nextState);

    /**
     * Get a subsequent transition from this transition for the given UTF-8 byte.
     *
     * @param utf8byte The byte to transition on
     * @return The next transition given the byte, or {@code null} if there is not a next transition
     */
    abstract ByteTransition getTransition(byte utf8byte);

    /**
     * Get all the unique transitions (single or compound) reachable from this transition by any UTF-8 byte value.
     *
     * @return Iterable of all transitions reachable from this transition.
     */
    abstract Iterable<ByteTransition> getTransitions();

    /**
     * Returns matches that are triggered if this transition is made. This is a convenience function that traverses the
     * linked list of matches and returns all of them in an Iterable.
     *
     * @return matches that are triggered if this transition is made.
     */
    abstract Iterable<ByteMatch> getMatches();

    /**
     * Returns all shortcuts that are available if this transition is made.
     *
     * @return all shortcuts
     */
    abstract Iterable<ShortcutTransition> getShortcuts();

    /**
     * Get all transitions represented by this transition (can be more than one if this is a compound transition).
     *
     * @return An iterable of all transitions represented by this transition.
     */
    abstract Iterable<SingleByteTransition> expand();

    /**
     * Get a transition that represents all of the next byte states for this transition.
     *
     * @return A transition that represents all of the next byte states for this transition.
     */
    abstract ByteTransition getTransitionForNextByteStates();

    /**
     * Tell if current transition is a shortcut transition or not.
     *
     * @return True if and only if this is a shortcut transition.
     */
    boolean isShortcutTrans() {
        return false;
    }

    /**
     * Tell if current transition is a match transition or not. Does not include shortcut transitions. Only includes
     * transitions that are matches after processing the entire pattern value.
     *
     * @return True if and only if there is a match on this transition.
     */
    boolean isMatchTrans() {
        return false;
    }

    /**
     * Tell if current transition is empty which means doesn't has match nor next state.
     * @return boolean
     */
    boolean isEmpty() {
        return !getMatches().iterator().hasNext() && getNextByteState() == null;
    }

    /**
     * Indicates if it is possible to traverse from the machine's start state to this transition using more than one
     * possible prefix/character-sequence.
     *
     * @return True if prefix is indeterminate; false otherwise.
     */
    boolean hasIndeterminatePrefix() {
        return false;
    }

    public void gatherObjects(Set<Object> objectSet) {
        if (!objectSet.contains(this)) { // stops looping
            objectSet.add(this);
            for (ByteTransition byteMachine : getTransitions()) {
                byteMachine.gatherObjects(objectSet);
            }
            final ByteState nextByteState = getNextByteState();
            if (nextByteState != null) {
                nextByteState.gatherObjects(objectSet);
            }
        }
    }

    @Override
    public ByteTransition clone() {
        try {
            return (ByteTransition) super.clone();
        } catch (CloneNotSupportedException e) {
            return null;
        }
    }
}
