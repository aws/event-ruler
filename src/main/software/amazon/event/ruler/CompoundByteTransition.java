package software.amazon.event.ruler;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * An implementation of ByteTransition that represents having taken multiple ByteTransitions simultaneously. This is
 * useful for traversing an NFA, i.e. when a single byte could lead you down multiple different transitions.
 */
public final class CompoundByteTransition extends ByteTransition {

    /**
     * The transitions that have all been simultaneously traversed.
     */
    private final Set<SingleByteTransition> byteTransitions;

    /**
     * A view of byteTransitions containing just the shortcuts.
     */
    private Set<ShortcutTransition> shortcutTransitions;

    /**
     * A view of byteTransitions containing just the ones that can contain matches.
     */
    private Set<SingleByteTransition> matchableTransitions;

    /**
     * A ByteTransition representing all nextByteStates from byteTransitions.
     */
    private ByteTransition transitionForNextByteState;

    private CompoundByteTransition(Set<SingleByteTransition> byteTransitions) {
        this.byteTransitions = Collections.unmodifiableSet(byteTransitions);

        Set<ShortcutTransition> shortcutTransitions = new HashSet<>();
        Set<SingleByteTransition> matchableTransitions = new HashSet<>();
        Set<SingleByteTransition> nextByteStates = new HashSet<>();

        this.byteTransitions.forEach(t -> {
            if (t.isShortcutTrans()) {
                shortcutTransitions.add((ShortcutTransition) t);
            }
            if (t.isMatchTrans()) {
                matchableTransitions.add(t);
            }
            ByteState nextByteState = t.getNextByteState();
            if (nextByteState != null) {
                nextByteStates.add(nextByteState);
            }
        });

        this.shortcutTransitions = Collections.unmodifiableSet(shortcutTransitions);
        this.matchableTransitions = Collections.unmodifiableSet(matchableTransitions);
        if (nextByteStates.equals(byteTransitions)) {
            this.transitionForNextByteState = this;
        } else {
            this.transitionForNextByteState = coalesce(nextByteStates);
        }
    }

    static <T extends ByteTransition> T coalesce(Iterable<SingleByteTransition> singles) {
        Iterator<SingleByteTransition> iterator = singles.iterator();
        if (!iterator.hasNext()) {
            return null;
        }

        SingleByteTransition firstElement = iterator.next();
        if (!iterator.hasNext()) {
            return (T) firstElement;
        } else if (singles instanceof Set) {
            return (T) new CompoundByteTransition((Set) singles);
        } else {
            // We expect Iterables with more than one element to always be Sets, so this should be dead code, but adding
            // it here for future-proofing.
            Set<SingleByteTransition> set = new HashSet();
            singles.forEach(single -> set.add(single));
            return (T) new CompoundByteTransition(set);
        }
    }

    /**
     * Returns the nextByteState from all byteTransitions with a preference to states that have determinate prefixes.
     * These states are re-usable when adding rules to the machine.
     *
     * @return First byte state with a preference to re-usable states.
     */
    @Override
    @Nullable
    ByteState getNextByteState() {
        ByteState firstNonNull = null;
        for (ByteTransition trans : byteTransitions) {
            for (SingleByteTransition single : trans.expand()) {
                ByteState nextByteState = single.getNextByteState();
                if (nextByteState != null) {
                    if (!nextByteState.hasIndeterminatePrefix()) {
                        return nextByteState;
                    }
                    if (firstNonNull == null) {
                        firstNonNull = nextByteState;
                    }
                }
            }
        }
        return firstNonNull;
    }

    @Override
    public SingleByteTransition setNextByteState(ByteState nextState) {
        return nextState;
    }

    /**
     * Get all transitions represented by this compound transition.
     *
     * @return A set of all transitions represented by this transition.
     */
    @Override
    Set<SingleByteTransition> expand() {
        return byteTransitions;
    }

    @Override
    ByteTransition getTransitionForNextByteStates() {
        return transitionForNextByteState;
    }

    /**
     * Get the matches given all of the transitions that have been simultaneously traversed. Excludes matches from
     * shortcut transitions as these are not actual matches based on the characters seen so far during the current
     * traversal.
     *
     * @return All matches from all of the simultaneously traversed transitions.
     */
    @Override
    public Set<ByteMatch> getMatches() {
        Set<ByteMatch> matches = new HashSet<>();
        for (SingleByteTransition single : matchableTransitions) {
            single.getMatches().forEach(match -> matches.add(match));
        }
        return matches;
    }

    @Override
    public Set<ShortcutTransition> getShortcuts() {
        return shortcutTransitions;
    }

    /**
     * Get the next transition for a UTF-8 byte given all of the transitions that have been simultaneously traversed.
     *
     * @param utf8byte The byte to transition on
     * @return Null if there are no transitions, the actual transition if 1, or a CompoundByteTransition if 2+
     */
    @Override
    public ByteTransition getTransition(byte utf8byte) {
        Set<SingleByteTransition> singles = new HashSet<>();
        for (SingleByteTransition transition : this.byteTransitions) {
            ByteTransition nextTransition = transition.getTransition(utf8byte);
            if (nextTransition != null) {
                nextTransition.expand().forEach(t -> singles.add(t));
            }
        }
        return coalesce(singles);
    }

    @Override
    public Set<ByteTransition> getTransitions() {
        // Determine all unique ceilings held in the ByteMaps of all nextByteStates.
        Set<Integer> allCeilings = new TreeSet<>();
        for (SingleByteTransition transition : byteTransitions) {
            ByteState nextByteState = transition.getNextByteState();
            if (nextByteState != null) {
                allCeilings.addAll(nextByteState.getCeilings());
            }
        }

        // For each unique ceiling, determine all singular transitions accessible for that byte value, then add
        // coalesced version to final result.
        Set<ByteTransition> result = new HashSet<>();
        for (Integer ceiling : allCeilings) {
            Set<SingleByteTransition> singles = new HashSet<>();
            for (SingleByteTransition transition : byteTransitions) {
                ByteTransition nextTransition = transition.getTransition((byte) (ceiling - 1));
                if (nextTransition != null) {
                    nextTransition.expand().forEach(t -> singles.add(t));
                }
            }

            ByteTransition coalesced = coalesce(singles);
            if (coalesced != null) {
                result.add(coalesced);
            }
        }

        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || o.getClass() != getClass()) {
            return false;
        }
        CompoundByteTransition other = (CompoundByteTransition) o;
        return Objects.equals(byteTransitions, other.byteTransitions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(byteTransitions);
    }

    @Override
    public String toString() {
        return "CBT: " + byteTransitions.toString();
    }
}
