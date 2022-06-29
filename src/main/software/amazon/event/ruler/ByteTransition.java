package software.amazon.event.ruler;

/**
 * Represents a transition (on a particular byte value) from a state to a state.
 */
abstract class ByteTransition implements Cloneable {

    /**
     * Returns the state to transfer to.
     *
     * @return the state to transfer to, or {@code null} if this transition does not support state transfer
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
    abstract ByteTransition setNextByteState(ByteState nextState);

    /**
     * Returns the first match of a linked list of matches that are triggered if this transition is made.
     *
     * @return the first match of a linked list of matches, or {@code null} if this transition does not support matching
     */
    abstract ByteMatch getMatch();

    /**
     * Sets the match. The method returns this or a new transition that contains the given match if the match is not
     * {@code null}. Otherwise, the method returns {@code null} or a new transition that does not
     * support matching.
     *
     * @param match the match
     * @return this or a new transition that contains the given match if the match is not {@code null}; otherwise,
     * {@code null} or a new transition that does not support matching
     */
    abstract ByteTransition setMatch(ByteMatch match);

    /**
     * Tell if current transition is a shortcut transition or not.
     * @return boolean
     */
    boolean isShortcutTrans() {
        return false;
    }

    /**
     * Tell if current transition is empty which means doesn't has match nor next state.
     * @return boolean
     */
    boolean isEmpty() {
        return getMatch() == null && getNextByteState() == null;
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
