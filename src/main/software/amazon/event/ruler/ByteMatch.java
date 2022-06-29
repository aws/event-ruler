package software.amazon.event.ruler;

/**
 * Represents a place in a ByteMachine where we have to do something in addition to
 *  just transitioning to another step
 */
// ByteMatch will be saved in anythingBut ConcurrentSkipListSet which is threadsafe, ordering that require
// the element must implement comparable interface and override appropriate interface for deduplication and
// comparision.
final class ByteMatch extends ByteTransition  {

    private final Patterns pattern;
    private final NameState nextNameState;  // the next state in the higher-level name/value machine
    private volatile ByteMatch nextMatch;   // it's possible to have > 1 matches apply at one point in the machine, so
                                             // this chains them together
    ByteMatch(Patterns pattern, NameState nextNameState) {
        this.pattern = pattern;
        this.nextNameState = nextNameState;
    }

    @Override
    public ByteState getNextByteState() {
        return null;
    }

    @Override
    public ByteTransition setNextByteState(ByteState nextState) {
        return nextState == null ? this : new CompositeByteTransition(nextState, this);
    }

    @Override
    public ByteMatch getMatch() {
        return this;
    }

    @Override
    public ByteTransition setMatch(ByteMatch match) {
        return match;
    }

    void setNextMatch(final ByteMatch match) {
        nextMatch = match;
    }

    Patterns getPattern() {
        return pattern;
    }
    NameState getNextNameState() { return nextNameState; }
    ByteMatch getNextMatch() {
        return nextMatch;
    }

    /**
     * After introducing anything-but, the notion of match equality changes:
     * The  match should be treated as the same when its pattern and nextNameState are identical,
     * It's OK if NextMatch is different, as in some situations (e.g. value in anythingBut set, there could
     * have different next match.
     * @param o, the input match object which is to be compared with current match.
     * @return true if it equals else false.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ByteMatch byteMatch = (ByteMatch) o;

        if (!pattern.equals(byteMatch.pattern)) {
            return false;
        }

        if (nextNameState == null) {
            return byteMatch.nextNameState == null;
        } else {
            return nextNameState.equals(byteMatch.nextNameState);
        }
    }

    @Override
    public int hashCode() {
        int result = pattern.hashCode();
        result = 31 * result + (nextNameState != null ? nextNameState.hashCode() : 0);
        return result;
    }

    // for debugging
    @Override
    public String toString() {
        return "BM: HC=" + hashCode() + " P=" + pattern  + "(" + pattern.pattern() + ") NNS=" + nextNameState + " NM=" + nextMatch;
    }
}
