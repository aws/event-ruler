package software.amazon.event.ruler;

import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

abstract class SingleByteTransition extends ByteTransition implements Iterable {

    /**
     * Returns the match that is triggered if this transition is made.
     *
     * @return the match, or {@code null} if this transition does not support matching
     */
    abstract ByteMatch getMatch();

    /**
     * Returns this or another transition that contains the given match if the match is not {@code null}.
     * Otherwise, the method returns {@code null} or a transition that does not support matching.
     *
     * @param match the match
     * @return this or another transition that contains the given match if the match is not {@code null}; otherwise,
     * {@code null} or a transition that does not support matching
     */
    abstract SingleByteTransition setMatch(ByteMatch match);

    /**
     * Get the transition that all bytes lead to. Could be compound if all bytes lead to more than one single
     * transition, or could be the empty transition or {@code null} if there are no transitions that all bytes lead to.
     *
     * @return The transition that all bytes lead to.
     */
    abstract ByteTransition getTransitionForAllBytes();

    @Override
    Iterable<ByteMatch> getMatches() {
        ByteMatch match = getMatch();
        if (match == null) {
            return Collections.emptySet();
        }
        return match;
    }

    @Override
    Iterable<SingleByteTransition> expand() {
        return this;
    }

    @Override
    public Iterator<SingleByteTransition> iterator() {
        return new Iterator<SingleByteTransition>() {
            private boolean hasNext = true;

            @Override
            public boolean hasNext() {
                return hasNext;
            }

            @Override
            public SingleByteTransition next() {
                if (!hasNext) {
                    throw new NoSuchElementException();
                }
                hasNext = false;
                return SingleByteTransition.this;
            }
        };
    }

    @Override
    ByteTransition getTransitionForNextByteStates() {
        return getNextByteState();
    }

    public void gatherObjects(Set<Object> objectSet, int maxObjectCount) {
        if (!objectSet.contains(this) && objectSet.size() < maxObjectCount) {
            objectSet.add(this);
            final ByteMatch match = getMatch();
            if (match != null) {
                match.gatherObjects(objectSet, maxObjectCount);
            }
            for (ByteTransition transition : getTransitions()) {
                transition.gatherObjects(objectSet, maxObjectCount);
            }
            final ByteTransition nextByteStates = getTransitionForNextByteStates();
            if (nextByteStates != null) {
                nextByteStates.gatherObjects(objectSet, maxObjectCount);
            }
        }
    }

}
