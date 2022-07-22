package software.amazon.event.ruler;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class represents a singular ByteTransition. This is in contrast to a compound ByteTransition that represents
 * having taken multiple distinct transitions simultaneously (i.e. for NFA traversal).
 */
abstract class SingleByteTransition extends ByteTransition {

    /**
     * Returns the match that is triggered if this transition is made.
     *
     * @return the match, or {@code null} if this transition does not support matching
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
    abstract SingleByteTransition setMatch(ByteMatch match);

    /**
     * Get the transition that all bytes lead to. Could be compound if all bytes lead to more than one single
     * transition, or could be the empty transition if there are no transitions that all bytes lead to.
     *
     * @return The transition that all bytes lead to.
     */
    abstract ByteTransition getTransitionForAllBytes();

    @Override
    Set<ByteMatch> getMatches() {
        ByteMatch match = getMatch();
        if (match == null) {
            return Collections.emptySet();
        }
        return Stream.of(match).collect(Collectors.toSet());
    }

    /**
     * Get all transitions represented by this transition, which is simply this as this is a single byte transition.
     *
     * @return A set of all transitions represented by this transition.
     */
    @Override
    Set<SingleByteTransition> expand() {
        return Stream.of(this).collect(Collectors.toSet());
    }

    @Override
    ByteTransition getTransitionForNextByteStates() {
        return getNextByteState();
    }

}
