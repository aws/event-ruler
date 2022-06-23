package software.amazon.event.ruler;

import javax.annotation.concurrent.ThreadSafe;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static software.amazon.event.ruler.MatchType.EQUALS_IGNORE_CASE;
import static software.amazon.event.ruler.MatchType.EXACT;
import static software.amazon.event.ruler.MatchType.EXISTS;
import static software.amazon.event.ruler.MatchType.SUFFIX;

/**
 * Represents a UTF8-byte-level state machine that matches a Ruler state machine's field values.
 * Each state is a map keyed by utf8 byte. getTransition(byte) yields a Target, which can contain either or
 *  both of the next ByteState, and the first of a chain of Matches, which indicate a match to some pattern.
 */
@ThreadSafe
class ByteMachine {

    // Only these match types support shortcuts during traversal.
    private static final Set<MatchType> SHORTCUT_MATCH_TYPES =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(EXACT, EQUALS_IGNORE_CASE)));

    private final ByteState startState = new ByteState();

    // non-zero if the machine has a numerical-comparison pattern included.  In which case, values
    //  should be converted to ComparableNumber before testing for matches, if possible.
    //
    private final AtomicInteger hasNumeric = new AtomicInteger(0);

    // as with the previous, but signals the presence of an attempt to match an IP address
    //
    private final AtomicInteger hasIP = new AtomicInteger(0);

    // signal the presence of suffix match in current byteMachine instance
    private final AtomicInteger hasSuffix = new AtomicInteger(0);

    // signal the presence of equals-ignore-case match in current byteMachine instance
    private final AtomicInteger hasEqualsIgnoreCase = new AtomicInteger(0);

    // For anything-but patterns, we assume they've matched unless we can prove they didn't.  When we
    //  add an anything-but to the machine, we put it in just like an exact match and remember it in
    //  this structure.  Then when we traverse the machine, if we get an exact match to the anything-but,
    //  that represents a failure, which we record in the failedAnythingButs hash, and then finally
    //  do a bit of set arithmetic to add the anythingButs, minus the failedAnythingButs, to the
    //  result set.
    //
    // We could do this more straightforwardly by filling in the whole byte state.  For
    //  example, if you had "anything-but: foo", in the first state, "f" would transition to state 2, all 255
    //  other byte values to a "success" match.  In state 2 and state 3, "o" would transition to the next state
    //  and all 255 others to success; then you'd have all 256 values in state 4 be success.  This would be simple
    //  and fast, but VERY memory-expensive, because you'd have 4 fully-filled states, and a thousand or so
    //  different Match objects.  Now, there are optimizations to be had with default-values in ByteStates,
    //  and lazily allocating Matches only when you have unique values, but all of these things would add
    //  considerable complexity, and this is also easy to understand, and probably not much slower.
    //
    private final Set<ByteMatch> anythingButs = ConcurrentHashMap.newKeySet();

    // Multiple different next-namestate steps can result from  processing a single field value, for example
    //  "foot" matches "foot" exactly, "foo" as a prefix, and "hand" as an anything-but.  So, this
    //  method returns a list.
    Set<NameState> transitionOn(String valString) {

        // not thread-safe, but this is only used in the scope of this method on one thread
        final Set<NameState> transitionTo = new HashSet<>();
        boolean fieldValueIsNumeric = false;
        if (hasNumeric.get() > 0) {
            try {
                final double numerically = Double.parseDouble(valString);
                valString = ComparableNumber.generate(numerically);
                fieldValueIsNumeric = true;
            } catch (Exception e) {
                // no-op, couldn't treat this as a sensible number
            }
        } else if (hasIP.get() > 0) {
            try {
                // we'll try both the quoted and unquoted version to help people whose events aren't
                //  in JSON
                if (valString.startsWith("\"") && valString.endsWith("\"")) {
                    valString = CIDR.ipToString(valString.substring(1, valString.length() -1));
                } else {
                    valString = CIDR.ipToString(valString);
                }
            } catch (IllegalArgumentException e) {
                // no-op, couldn't treat this as an IP address
            }
        }
        doTransitionOn(valString, transitionTo, fieldValueIsNumeric);
        return transitionTo;
    }

    boolean isEmpty() {
        if (startState.hasNoTransitions()) {
            assert anythingButs.isEmpty();
            assert hasNumeric.get() == 0;
            assert hasIP.get() == 0;
            return true;
        }
        return false;
    }

    // this is to support deleteRule().  It deletes the ByteStates that exist to support matching the provided pattern.
    void deletePattern(final Patterns pattern) {
        switch (pattern.type()) {
        case NUMERIC_RANGE:
            assert pattern instanceof Range;
            deleteRangePattern((Range) pattern);
            break;
        case ANYTHING_BUT:
            assert pattern instanceof AnythingBut;
            deleteAnythingButPattern((AnythingBut) pattern);
            break;
        case EXACT:
        case NUMERIC_EQ:
        case PREFIX:
        case SUFFIX:
        case ANYTHING_BUT_PREFIX:
        case EQUALS_IGNORE_CASE:
            assert pattern instanceof ValuePatterns;
            deleteMatchPattern((ValuePatterns) pattern);
            break;
        case EXISTS:
            deleteExistencePattern(pattern);
            break;
        default:
            throw new AssertionError(pattern + " is not implemented yet");
        }
    }

    private void deleteExistencePattern(Patterns pattern) {
        final byte[] utf8bytes = Patterns.EXISTS_BYTE_STRING.getBytes(StandardCharsets.UTF_8);
        deleteMatchStep(startState, 0, pattern, utf8bytes);
    }

    private void deleteAnythingButPattern(AnythingBut pattern) {
        pattern.getValues().forEach(value ->
            deleteMatchStep(startState, 0, pattern, value.getBytes(StandardCharsets.UTF_8)));
    }

    private void deleteMatchPattern(ValuePatterns pattern) {
        final byte[] utf8bytes = pattern.pattern().getBytes(StandardCharsets.UTF_8);
        deleteMatchStep(startState, 0, pattern, utf8bytes);
    }

    private void deleteMatchStep(ByteState byteState, int byteIndex, Patterns pattern, final byte[] utf8bytes) {
        if (byteIndex < utf8bytes.length - 1) {
            final byte currentByte = utf8bytes[byteIndex];
            final ByteTransition trans = getTransition(byteState, currentByte);

            // if it is shortcut trans, we need stop loop and delete it directly.
            if (trans.isShortcutTrans()) {
                deleteMatch(currentByte, byteState, pattern);
                return;
            }

            ByteState nextByteState = trans.getNextByteState();
            if (nextByteState != null) {
                deleteMatchStep(nextByteState, byteIndex + 1, pattern, utf8bytes);

                if (nextByteState.hasNoTransitions()) {
                    // the transition's next state has no transitions, compact the transition
                    setTransitionNextState(byteState, currentByte, trans, null);
                }
            }
        } else {
            deleteMatch(utf8bytes[utf8bytes.length - 1], byteState, pattern);
        }
    }

    // this method is only called after findRangePattern proves that matched pattern exist.
    private void deleteRangePattern(Range range) {

        // if range to be deleted does not exist, just return
        if (findRangePattern(range) == null) {
            return;
        }

        // Inside Range pattern, there are bottom value and top value, this function will traverse each byte of bottom
        // and top value separately to hunt down all matches eligible to be deleted.
        // The ArrayDequeue is used to save all byteStates in transition path since state associated with first byte of
        // value to state associated with last byte of value. Then, checkAndDeleteStateAlongTraversedPath function will
        // check each state saved in ArrayDequeue along reverse direction of traversing path and recursively check and
        // delete match and state if it is eligible.
        // Note: byteState is deletable only when it has no match and no transition to next byteState.
        // Refer to definition of class ComparableNumber, the max length in bytes for Number type is 16,
        // so here we take 16 as ArrayDeque capacity which is defined as ComparableNumber.MAX_BYTE_LENGTH.
        final ArrayDeque<AbstractMap.SimpleImmutableEntry<Byte, ByteState>> byteStatesTraversePathAlongRangeBottomValue =
                new ArrayDeque<>(ComparableNumber.MAX_LENGTH_IN_BYTES);
        final ArrayDeque<AbstractMap.SimpleImmutableEntry<Byte, ByteState>> byteStatesTraversePathAlongRangeTopValue =
                new ArrayDeque<>(ComparableNumber.MAX_LENGTH_IN_BYTES);

        ByteState forkState = startState;
        int forkOffset = 0;
        byteStatesTraversePathAlongRangeBottomValue.addFirst(new AbstractMap.SimpleImmutableEntry<>(range.bottom[0], forkState));
        byteStatesTraversePathAlongRangeTopValue.addFirst(new AbstractMap.SimpleImmutableEntry<>(range.top[0], forkState));

        // bypass common prefix of range's bottom and top patterns
        // we need move forward the state and save all states traversed for checking later.
        while (range.bottom[forkOffset] == range.top[forkOffset]) {
            forkState = findNextByteState(forkState, range.bottom[forkOffset]);
            assert forkState != null : "forkState != null";
            byteStatesTraversePathAlongRangeBottomValue.addFirst(new AbstractMap.SimpleImmutableEntry<>(range.bottom[forkOffset], forkState));
            byteStatesTraversePathAlongRangeTopValue.addFirst(new AbstractMap.SimpleImmutableEntry<>(range.top[forkOffset], forkState));
            forkOffset++;
        }

        // when bottom byte on forkOffset position < top byte in same position, there must be matches existing
        // in this state, go ahead to delete matches in the fork state.
        for (byte bb : Range.digitSequence(range.bottom[forkOffset], range.top[forkOffset], false, false)) {
            deleteMatch(bb, forkState, range);
        }

        // process all the transitions on the bottom range bytes
        ByteState state = forkState;
        int lastMatchOffset = forkOffset;

        // see explanation in addRangePattern(), we need delete state and match accordingly.
        for (int offsetB = forkOffset + 1; offsetB < (range.bottom.length - 1); offsetB++) {
            byte b = range.bottom[offsetB];
            if (b < Constants.MAX_DIGIT) {
                while (lastMatchOffset < offsetB) {
                    state = findNextByteState(state, range.bottom[lastMatchOffset]);
                    assert state != null : "state must be existing for this pattern";
                    byteStatesTraversePathAlongRangeBottomValue.addFirst(
                            new AbstractMap.SimpleImmutableEntry<>(range.bottom[lastMatchOffset], state));
                    lastMatchOffset++;
                }
                assert lastMatchOffset == offsetB : "lastMatchOffset == offsetB";
                for (byte bb : Range.digitSequence(b, Constants.MAX_DIGIT, false, true)) {
                    deleteMatch(bb, state, range);
                }
            }
        }

        // now for last "bottom" digit
        // see explanation in addRangePattern(), we need to delete states and matches accordingly.
        final byte lastBottom = range.bottom[range.bottom.length - 1];
        final byte lastTop = range.top[range.top.length - 1];
        if ((lastBottom < Constants.MAX_DIGIT) || !range.openBottom) {
            while (lastMatchOffset < range.bottom.length - 1) {
                state = findNextByteState(state, range.bottom[lastMatchOffset]);
                assert state != null : "state != null";
                byteStatesTraversePathAlongRangeBottomValue.addFirst(new AbstractMap.SimpleImmutableEntry<>(range.bottom[lastMatchOffset], state));
                lastMatchOffset++;
            }
            assert lastMatchOffset == range.bottom.length - 1 : "lastMatchOffset == range.bottom.length - 1";
            if (!range.openBottom) {
                deleteMatch(lastBottom, state, range);
            }
            // unless the last digit is also at the fork position, fill in the extra matches due to
            //  the strictly-less-than condition (see discussion above)
            if (forkOffset < (range.bottom.length - 1)) {
                for (byte bb : Range.digitSequence(lastBottom, Constants.MAX_DIGIT, false, true)) {
                    deleteMatch(bb, state, range);
                }
            }
        }

        // now process transitions along the top range bytes
        // see explanation in addRangePattern(), we need to delete states and matches accordingly.
        state = forkState;
        lastMatchOffset = forkOffset;
        for (int offsetT = forkOffset + 1; offsetT < (range.top.length - 1); offsetT++) {
            byte b = range.top[offsetT];
            if (b > '0') {
                while (lastMatchOffset < offsetT) {
                    state = findNextByteState(state, range.top[lastMatchOffset]);
                    assert state != null : "state must be existing for this pattern";
                    byteStatesTraversePathAlongRangeTopValue.addFirst(new AbstractMap.SimpleImmutableEntry<>(range.top[lastMatchOffset], state));
                    lastMatchOffset++;
                }
                assert lastMatchOffset == offsetT : "lastMatchOffset == offsetT";

                for (byte bb : Range.digitSequence((byte) '0', range.top[offsetT], true, false)) {
                    deleteMatch(bb, state, range);
                }
            }
        }

        // now for last "top" digit.
        // see explanation in addRangePattern(), we need to delete states and matches accordingly.
        if ((lastTop > '0') || !range.openTop) {
            while (lastMatchOffset < range.top.length - 1) {
                state = findNextByteState(state, range.top[lastMatchOffset]);
                assert state != null : "state != null";
                byteStatesTraversePathAlongRangeTopValue.addFirst(new AbstractMap.SimpleImmutableEntry<>(range.top[lastMatchOffset], state));
                lastMatchOffset++;
            }
            assert lastMatchOffset == range.top.length - 1 : "lastMatchOffset == range.top.length - 1";
            if (!range.openTop) {
                deleteMatch(lastTop, state, range);
            }

            // unless the last digit is also at the fork position, fill in the extra matches due to
            //  the strictly-less-than condition (see discussion above)
            if (forkOffset < (range.top.length - 1)) {
                for (byte bb : Range.digitSequence((byte) '0', lastTop, true, false)) {
                    deleteMatch(bb, state, range);
                }
            }
        }

        // by now we should have deleted all matches in all associated byteStates,
        // now we start cleaning up ineffective byteSates along states traversed path we saved before.
        checkAndDeleteStateAlongTraversedPath(byteStatesTraversePathAlongRangeBottomValue);
        checkAndDeleteStateAlongTraversedPath(byteStatesTraversePathAlongRangeTopValue);

        // well done now, we have deleted all matches pattern matched and cleaned all empty state as if that pattern
        // wasn't added into machine before.
    }

    private void checkAndDeleteStateAlongTraversedPath(ArrayDeque<AbstractMap.SimpleImmutableEntry<Byte, ByteState>> byteStateQueue) {

        if (byteStateQueue.isEmpty()) {
            return;
        }

        final AbstractMap.SimpleImmutableEntry<Byte, ByteState> childStatePair = byteStateQueue.pollFirst();
        if (childStatePair != null) {
            Byte childByteKey = childStatePair.getKey();
            ByteState childByteState = childStatePair.getValue();
            while (!byteStateQueue.isEmpty()) {
                final AbstractMap.SimpleImmutableEntry<Byte, ByteState> parentStatePair = byteStateQueue.pollFirst();
                if (parentStatePair != null) {
                    final Byte parentByteKey = parentStatePair.getKey();
                    final ByteState parentByteState = parentStatePair.getValue();
                    if (childByteState != null && childByteState.hasNoTransitions() && parentByteState != null) {
                        ByteTransition transition = getTransition(parentByteState, childByteKey);

                        ByteState nextState = transition.getNextByteState();
                        if (nextState != null && nextState.hasNoTransitions()) {
                            // the transition's next state has no transitions, compact the transition
                            setTransitionNextState(parentByteState, childByteKey, transition, null);
                        }
                    }
                    childByteKey = parentByteKey;
                    childByteState = parentByteState;
                }
            }
        }
    }

    private void doTransitionOn(final String valString, final Set<NameState> transitionTo, boolean fieldValueIsNumeric) {
        final Set<ByteMatch> failedAnythingButs = new HashSet<>();
        final byte[] val = valString.getBytes(StandardCharsets.UTF_8);

        // we need to add the name state for key existence
        addExistenceMatch(transitionTo);

        // attempt to harvest the possible suffix match
        addSuffixMatch(val, transitionTo);

        // attempt to harvest the possible equals-ignore-case match
        addEqualsIgnoreCaseMatch(valString, transitionTo);

        // we have to do old-school indexing rather than "for (byte b : trans)" because there is some special-casing
        // on transitions on the last byte in the value array
        ByteState state = startState;
        for (int valIndex = 0; valIndex < val.length; valIndex++) {
            final ByteTransition trans = getTransition(state, val[valIndex]);

            if (attemptAddShortcutTransitionMatch(trans, valString, EXACT, transitionTo)) {
                // Have successfully taken shortcut. Can break now.
                break;
            }

            // process any matches hanging off this transition
            for (ByteMatch match = trans.getMatch(); match != null; match = match.getNextMatch()) {
                switch (match.getPattern().type()) {
                case EXACT:
                    if (valIndex == (val.length - 1)) {
                        transitionTo.add(match.getNextNameState());
                    }
                    break;
                case NUMERIC_EQ:
                    // only matches at last character
                    if (fieldValueIsNumeric && valIndex == (val.length - 1)) {
                        transitionTo.add(match.getNextNameState());
                    }
                    break;

                case PREFIX:
                    transitionTo.add(match.getNextNameState());
                    break;

                case SUFFIX:
                case EQUALS_IGNORE_CASE:
                case EXISTS:
                    // we already harvested these matches via separate functions due to special matching requirements,
                    // so just ignore them here.
                    break;

                case NUMERIC_RANGE:
                    // as soon as you see the match, you've matched
                    Range range = (Range) match.getPattern();
                    if ((fieldValueIsNumeric && !range.isCIDR) || (!fieldValueIsNumeric && range.isCIDR)) {
                        transitionTo.add(match.getNextNameState());
                    }
                    break;

                case ANYTHING_BUT:
                    AnythingBut anythingBut = (AnythingBut) match.getPattern();
                    // only applies if at last character
                    if (valIndex == (val.length - 1) && anythingBut.isNumeric() == fieldValueIsNumeric) {
                        failedAnythingButs.add(match);
                    }
                    break;

                case ANYTHING_BUT_PREFIX:
                    failedAnythingButs.add(match);
                    break;

                default:
                    throw new RuntimeException("Not implemented yet");

                }
            }

            state = trans.getNextByteState();
            if (state == null) {
                break;
            }
        }

        // This may look like premature optimization, but the first "if" here yields
        // roughly 10x performance improvement.
        if (!anythingButs.isEmpty()) {
            if (!failedAnythingButs.isEmpty()) {
                transitionTo.addAll(anythingButs.stream()
                        .filter(anythingBut -> !failedAnythingButs.contains(anythingBut))
                        .map(ByteMatch::getNextNameState)
                        .collect(Collectors.toList()));
            } else {
                transitionTo.addAll(anythingButs.stream()
                        .map(ByteMatch::getNextNameState)
                        .collect(Collectors.toList()));
            }
        }
    }

    private void addExistenceMatch(final Set<NameState> transitionTo) {
        final byte[] val = Patterns.EXISTS_BYTE_STRING.getBytes(StandardCharsets.UTF_8);

        ByteState state = startState;
        ByteTransition trans = null;
        for (int valIndex = 0; valIndex < val.length && state != null; valIndex++) {
            trans = getTransition(state, val[valIndex]);
            state = trans.getNextByteState();
        }

        if (trans == null) {
            return;
        }

        ByteMatch match = trans.getMatch();
        while (match != null) {
            if (match.getPattern().type() == EXISTS) {
                transitionTo.add(match.getNextNameState());
                break;
            }
            match = match.getNextMatch();
        }
    }

    private void addSuffixMatch(final byte[] val, final Set<NameState> transitionTo) {
        // we only attempt to evaluate suffix matches when there is suffix match in current byte machine instance.
        // it works as performance level to avoid other type of matches from being affected by suffix checking.
        if (hasSuffix.get() > 0) {
            ByteState state = startState;
            // check the byte in reverse order in order to harvest suffix matches
            for (int valIndex = val.length - 1; valIndex >= 0; valIndex--) {
                final ByteTransition trans = getTransition(state, val[valIndex]);
                for (ByteMatch match = trans.getMatch(); match != null; match = match.getNextMatch()) {
                    // given we are traversing in reverse order (from right to left), only suffix matches are eligible
                    // to be collected.
                    if (match.getPattern().type() == SUFFIX) {
                        transitionTo.add(match.getNextNameState());
                    }
                }
                state = trans.getNextByteState();
                if (state == null) {
                    break;
                }
            }
        }
    }

    private void addEqualsIgnoreCaseMatch(final String valString, final Set<NameState> transitionTo) {
        // We only attempt to evaluate equals-ignore-case matches when there is an equals-ignore-case rule in the
        // current byte machine instance. It avoids other match types being affected by equals-ignore-case checking.
        if (hasEqualsIgnoreCase.get() > 0) {
            addIgnoreCaseMatch(valString, transitionTo, EQUALS_IGNORE_CASE);
        }
    }

    // Potentially can re-use this method for prefix-ignore-case and suffix-ignore-case match types.
    // If this machine turns into a NFA, as it likely will to support wildcard matching, it will be simpler to implement
    // ignore-case matching by adding lower-case and upper-case transitions to the byte machine and then removing this
    // special-case match harvesting. As is, there will be a performance hit if a field has ignore-case rules as well as
    // other rules.
    private void addIgnoreCaseMatch(final String valString, final Set<NameState> transitionTo, MatchType matchType) {
        final String valStringLowerCase = valString.toLowerCase(Locale.ROOT);
        final byte[] val = valStringLowerCase.getBytes(StandardCharsets.UTF_8);
        ByteState state = startState;
        for (int valIndex = 0; valIndex < val.length; valIndex++) {
            final ByteTransition trans = getTransition(state, val[valIndex]);

            if (attemptAddShortcutTransitionMatch(trans, valStringLowerCase, matchType, transitionTo)) {
                // Have successfully taken shortcut. Can break now.
                break;
            }

            for (ByteMatch match = trans.getMatch(); match != null; match = match.getNextMatch()) {
                if (!trans.isShortcutTrans() && match.getPattern().type() == matchType) {
                    transitionTo.add(match.getNextNameState());
                }
            }
            state = trans.getNextByteState();
            if (state == null) {
                break;
            }
        }
    }

    /**
     * Evaluates if a provided transition is a shortcut transition with a match having a given match type and value. If
     * so, adds match to transitionTo Set. Used to short-circuit traversal.
     *
     * Note: The adjustment mode can ensure the shortcut transition (if exists) is always at the tail of path. Refer to
     * addEndOfMatch() function for details.
     *
     * @param transition Transition to evaluate.
     * @param value Value desired in match's pattern.
     * @param expectedMatchType Match type expected in match's pattern.
     * @param transitionTo Set that match's next name state will be added to if desired match is found.
     * @return True iff match was added to transitionTo Set.
     */
    private boolean attemptAddShortcutTransitionMatch(final ByteTransition transition, final String value,
            final MatchType expectedMatchType, final Set<NameState> transitionTo) {
        if (transition.isShortcutTrans()) {
            ByteMatch match = transition.getMatch();
            assert match != null;
            if (match.getPattern().type() == expectedMatchType) {
                ValuePatterns valuePatterns = (ValuePatterns) match.getPattern();
                if (valuePatterns.pattern().equals(value)) {
                    // Only one match is possible for shortcut transition
                    transitionTo.add(match.getNextNameState());
                    return true;
                }
            }
        }
        return false;
    }

    // Adds one pattern to a byte machine.
    NameState addPattern(final Patterns pattern) {
        switch (pattern.type()) {
        case NUMERIC_RANGE:
            assert pattern instanceof Range;
            return addRangePattern((Range) pattern);
        case ANYTHING_BUT:
            assert pattern instanceof AnythingBut;
            return addAnythingButPattern((AnythingBut) pattern);

        case ANYTHING_BUT_PREFIX:
        case EXACT:
        case NUMERIC_EQ:
        case PREFIX:
        case SUFFIX:
        case EQUALS_IGNORE_CASE:
            assert pattern instanceof ValuePatterns;
            return addMatchPattern((ValuePatterns) pattern);

        case EXISTS:
            return addExistencePattern(pattern);
        default:
            throw new AssertionError(pattern + " is not implemented yet");
        }
    }

    private NameState addExistencePattern(Patterns pattern) {
        return addMatchValue(pattern, Patterns.EXISTS_BYTE_STRING, null);
    }

    private NameState addAnythingButPattern(AnythingBut pattern) {

        NameState nameStateToBeReturned = null;
        NameState nameStateChecker = null;
        for(String value : pattern.getValues()) {
            nameStateToBeReturned = addMatchValue(pattern, value, nameStateToBeReturned);
            if (nameStateChecker == null) {
                nameStateChecker = nameStateToBeReturned;
            }
            // all the values in the list must point to the same NameState because they are sharing the same pattern
            // object.
            assert nameStateChecker == null || nameStateChecker == nameStateToBeReturned : " nameStateChecker == nameStateToBeReturned";
        }

        return nameStateToBeReturned;
    }

    private NameState addMatchValue(Patterns pattern, String value, NameState nameStateToBeReturned) {

        final byte[] utf8bytes = value.getBytes(StandardCharsets.UTF_8);
        ByteState byteState = startState;
        int i = 0;
        for (; i < utf8bytes.length - 1; i++) {
            ByteTransition trans = getTransition(byteState, utf8bytes[i]);
            if (trans.isEmpty()) {
                break;
            }
            ByteState stateReturned = trans.getNextByteState();
            if (stateReturned == null) {
                break;
            } else {
                byteState = stateReturned;
            }
        }
        // we found our way through the machine with all bytes except the last having matches or shortcut byte.
        return addEndOfMatch(byteState, utf8bytes, i, pattern, nameStateToBeReturned);
    }

    NameState findPattern(final Patterns pattern) {
        switch (pattern.type()) {
        case NUMERIC_RANGE:
            assert pattern instanceof Range;
            return findRangePattern((Range) pattern);
        case ANYTHING_BUT:
            assert pattern instanceof AnythingBut;
            return findAnythingButPattern((AnythingBut) pattern);
        case EXACT:
        case NUMERIC_EQ:
        case PREFIX:
        case SUFFIX:
        case ANYTHING_BUT_PREFIX:
        case EQUALS_IGNORE_CASE:
            assert pattern instanceof ValuePatterns;
            return findMatchPattern((ValuePatterns) pattern);
        case EXISTS:
            return findMatchPattern(Patterns.EXISTS_BYTE_STRING.getBytes(StandardCharsets.UTF_8), pattern);
        default:
            throw new AssertionError(pattern + " is not implemented yet");
        }
    }

    private NameState findAnythingButPattern(AnythingBut pattern) {

        Set<NameState> nextNameStates = pattern.getValues().stream().
                map(value -> findMatchPattern(value.getBytes(StandardCharsets.UTF_8), pattern)).
                filter(Objects::nonNull).collect(Collectors.toSet());
        if (!nextNameStates.isEmpty()) {
            assert nextNameStates.size() == 1 : "nextNameStates.size() == 1";
            return nextNameStates.iterator().next();
        }
        return null;
    }

    private NameState findMatchPattern(ValuePatterns pattern) {
        return findMatchPattern(pattern.pattern().getBytes(StandardCharsets.UTF_8), pattern);
    }

    private NameState findMatchPattern(final byte[] utf8bytes, final Patterns pattern) {
        ByteState byteState = startState;

        ByteTransition shortcutTrans = null;
        // iterate byteState to process last byte
        for (int i = 0; i < utf8bytes.length - 1; i++) {
            final ByteTransition trans = getTransition(byteState, utf8bytes[i]);

            // shortcut and stop loop
            if (trans.isShortcutTrans()) {
                shortcutTrans = trans;
                break;
            }

            byteState = trans.getNextByteState();
            if (byteState == null) {
                return null;
            }
        }

        // for last byte, check its match
        final ByteTransition trans = (shortcutTrans != null) ? shortcutTrans :
                                     getTransition(byteState, utf8bytes[utf8bytes.length - 1]);

        for (ByteMatch match = trans.getMatch(); match != null; match = match.getNextMatch()) {

            // along the chain of match, one match pattern object should only have one element in the chain.
            if (match.getPattern().equals(pattern)) {
                return match.getNextNameState();
            }
        }
        return null;
    }

    // before we accept the delete range pattern, the input range pattern must exactly match Range ByteMatch
    //  in all path along the range.
    private NameState findRangePattern(Range range) {

        Set<NameState> nextNameStates = new HashSet<>();
        NameState nextNameState = null;

        ByteState forkState = startState;
        int forkOffset = 0;

        // bypass common prefix of range's bottom and top patterns
        while (range.bottom[forkOffset] == range.top[forkOffset]) {
            forkState = findNextByteState(forkState, range.bottom[forkOffset++]);
            if (forkState == null) {
                return null;
            }
        }

        // fill in matches in the fork state
        for (byte bb : Range.digitSequence(range.bottom[forkOffset], range.top[forkOffset], false, false)) {
            nextNameState = findMatch(bb, forkState, range);
            if (nextNameState == null) {
                return null;
            }
            nextNameStates.add(nextNameState);
        }

        // process all the transitions on the bottom range bytes
        ByteState state = forkState;
        int lastMatchOffset = forkOffset;
        for (int offsetB = forkOffset + 1; offsetB < (range.bottom.length - 1); offsetB++) {
            byte b = range.bottom[offsetB];
            if (b < Constants.MAX_DIGIT) {
                while (lastMatchOffset < offsetB) {
                    state = findNextByteState(state, range.bottom[lastMatchOffset++]);
                    if (state == null) {
                        return null;
                    }
                }
                assert lastMatchOffset == offsetB : "lastMatchOffset == offsetB";
                for (byte bb : Range.digitSequence(b, Constants.MAX_DIGIT, false, true)) {
                    nextNameState = findMatch(bb, state, range);
                    if (nextNameState == null) {
                        return null;
                    }
                    nextNameStates.add(nextNameState);
                }
            }
        }

        // now for last "bottom" digit
        final byte lastBottom = range.bottom[range.bottom.length - 1];
        final byte lastTop = range.top[range.top.length - 1];
        if ((lastBottom < Constants.MAX_DIGIT) || !range.openBottom) {
            while (lastMatchOffset < range.bottom.length - 1) {
                state = findNextByteState(state, range.bottom[lastMatchOffset++]);
                if (state == null) {
                    return null;
                }
            }
            assert lastMatchOffset == (range.bottom.length - 1) : "lastMatchOffset == (range.bottom.length - 1)";
            if (!range.openBottom) {
                nextNameState = findMatch(lastBottom, state, range);
                if (nextNameState == null) {
                    return null;
                }
                nextNameStates.add(nextNameState);
            }

            // unless the last digit is also at the fork position, fill in the extra matches due to
            //  the strictly-less-than condition (see discussion above)
            if (forkOffset < (range.bottom.length - 1)) {
                for (byte bb : Range.digitSequence(lastBottom, Constants.MAX_DIGIT, false, true)) {
                    nextNameState = findMatch(bb, state, range);
                    if (nextNameState == null) {
                        return null;
                    }
                    nextNameStates.add(nextNameState);
                }
            }
        }

        // now process transitions along the top range bytes
        state = forkState;
        lastMatchOffset = forkOffset;
        for (int offsetT = forkOffset + 1; offsetT < (range.top.length - 1); offsetT++) {
            byte b = range.top[offsetT];
            if (b > '0') {
                while (lastMatchOffset < offsetT) {
                    state = findNextByteState(state, range.top[lastMatchOffset++]);
                    if (state == null) {
                        return null;
                    }
                }
                assert lastMatchOffset == offsetT : "lastMatchOffset == offsetT";

                for (byte bb : Range.digitSequence((byte) '0', range.top[offsetT], true, false)) {
                    nextNameState = findMatch(bb, state, range);
                    if (nextNameState == null) {
                        return null;
                    }
                    nextNameStates.add(nextNameState);
                }
            }
        }

        // now for last "top" digit
        if ((lastTop > '0') || !range.openTop) {
            while (lastMatchOffset < range.top.length - 1) {
                state = findNextByteState(state, range.top[lastMatchOffset++]);
                if (state == null) {
                    return null;
                }
            }
            assert lastMatchOffset == (range.top.length - 1) : "lastMatchOffset == (range.top.length - 1)";
            if (!range.openTop) {
                nextNameState = findMatch(lastTop, state, range);
                if (nextNameState == null) {
                    return null;
                }
                nextNameStates.add(nextNameState);
            }
            // unless the last digit is also at the fork position, fill in the extra matches due to
            //  the strictly-less-than condition (see discussion above)
            if (forkOffset < (range.top.length - 1)) {
                for (byte bb : Range.digitSequence((byte) '0', lastTop, true, false)) {
                    nextNameState = findMatch(bb, state, range);
                    if (nextNameState == null) {
                        return null;
                    }
                    nextNameStates.add(nextNameState);
                }
            }
        }

        // There must only have one nextNameState object returned by this range pattern refer to
        // addRangePattern() where only one nextNameState is used by one pattern.
        assert nextNameStates.size() == 1 : "nextNameStates.size() == 1";
        return nextNameState;
    }

    //  add a numeric range expression to the byte machine.  Note; the code assumes that the patterns
    //  are encoded as pure strings containing only decimal digits, and that the top and bottom values
    //  are equal in length.
    private NameState addRangePattern(final Range range) {

        // we prepare for one new NameSate here which will be used for range match to point to next NameSate.
        // however, it will not be used if match is already existing. in that case, we will reuse NameSate
        // from that match.
        NameState nextNameState = new NameState();

        ByteState forkState = startState;
        int forkOffset = 0;

        // bypass common prefix of range's bottom and top patterns
        while (range.bottom[forkOffset] == range.top[forkOffset]) {
            forkState = findOrMakeNextByteState(forkState, range.bottom[forkOffset], forkOffset++);
        }

        // now we've bypassed any initial positions where the top and bottom patterns are the same, and arrived
        //  at a position where the 'top' and 'bottom' bytes differ. Such a position must occur because we require
        //  that the bottom number be strictly less than the top number.  Let's call the current state the fork state.
        // At the fork state, any byte between the top and bottom byte values means success, the value must be strictly
        //  greater than bottom and less than top.  That leaves the transitions for the bottom and top values.
        // After the fork state, we arrive at a state where any digit greater than the next bottom value
        //  means success, because after the fork state we are already strictly less than the top value, and we
        //  know then that we are greater than the bottom value.  A digit equal to the bottom value leads us
        //  to another state where the same applies; anything greater than the bottom value is success.  Finally,
        //  when we come to the last digit, as before, anything greater than the bottom value is success, and
        //  being equal to the bottom value means failure if the interval is open, because the value is strictly
        //  equal to the bottom of the range.
        // Following the top-byte transition out of the fork state leads to a state where the story is reversed;
        //  any digit lower than the top value means success, successive matches to the top value lead to similar
        //  states, and a final byte that matches the top value means failure if the interval is open at the top.
        // There is a further complication. Consider the case [ > 00299 < 00500 ].  The machine we need to
        //  build is like this:
        //  State0 =0=> State1 ; State1 =0=> State2 ; State2 =3=> MATCH ; State2 =4=> MATCH
        //  That's it. Once you've seen 002 in the input, there's nothing that can follow that will be
        //  strictly greater than the remaining 299.  Once you've seen 005 there's nothing that can
        //  follow that will be strictly less than the remaining 500
        //  But this only works when the suffix of the bottom range pattern is all 9's or if the suffix of the
        //  top range pattern is all 0's
        // What could be simpler?

        // fill in matches in the fork state
        for (byte bb : Range.digitSequence(range.bottom[forkOffset], range.top[forkOffset], false, false)) {
            nextNameState = insertMatch(bb, forkState, nextNameState, range);
        }

        // process all the transitions on the bottom range bytes
        ByteState state = forkState;

        // lastMatchOffset is the last offset where we know we have to put in a match
        int lastMatchOffset = forkOffset;

        for (int offsetB = forkOffset + 1; offsetB < (range.bottom.length - 1); offsetB++) {

            // if b is Constants.MAX_DIGIT, then we should hold off adding transitions until we see a non-maxDigit digit
            //  because of the special case described above.
            byte b = range.bottom[offsetB];
            if (b < Constants.MAX_DIGIT) {
                // add transitions for any 9's we bypassed
                while (lastMatchOffset < offsetB) {
                    state = findOrMakeNextByteState(state, range.bottom[lastMatchOffset], lastMatchOffset++);
                }

                assert lastMatchOffset == offsetB : "lastMatchOffset == offsetB";
                assert state != null : "state != null";

                // now add transitions for values greater than this non-9 digit
                for (byte bb : Range.digitSequence(b, Constants.MAX_DIGIT, false, true)) {
                    nextNameState = insertMatch(bb, state, nextNameState, range);
                }
            }
        }

        // now for last "bottom" digit
        final byte lastBottom = range.bottom[range.bottom.length - 1];
        final byte lastTop = range.top[range.top.length - 1];

        // similarly, if the last digit is 9 and we have openBottom, there can be no matches so we're done.
        if ((lastBottom < Constants.MAX_DIGIT) || !range.openBottom) {

            // add transitions for any 9's we bypassed
            while (lastMatchOffset < range.bottom.length - 1) {
                state = findOrMakeNextByteState(state, range.bottom[lastMatchOffset], lastMatchOffset++);
            }
            assert lastMatchOffset == (range.bottom.length - 1) : "lastMatchOffset == (range.bottom.length - 1)";
            assert state != null : "state != null";

            // now we insert matches for possible values of last digit
            if (!range.openBottom) {
                nextNameState = insertMatch(lastBottom, state, nextNameState, range);
            }

            // unless the last digit is also at the fork position, fill in the extra matches due to
            //  the strictly-less-than condition (see discussion above)
            if (forkOffset < (range.bottom.length - 1)) {
                for (byte bb : Range.digitSequence(lastBottom, Constants.MAX_DIGIT, false, true)) {
                    nextNameState = insertMatch(bb, state, nextNameState, range);
                }
            }
        }

        // now process transitions along the top range bytes
        // restore the state and last match offset to fork position to start analyzing top value bytes ...
        state = forkState;
        lastMatchOffset = forkOffset;
        for (int offsetT = forkOffset + 1; offsetT < (range.top.length - 1); offsetT++) {

            // if b is '0', we should hold off adding transitions until we see a non-'0' digit.
            byte b = range.top[offsetT];

            // if need to add transition
            if (b > '0') {
                while (lastMatchOffset < offsetT) {
                    state = findOrMakeNextByteState(state, range.top[lastMatchOffset], lastMatchOffset++);
                }
                assert lastMatchOffset == offsetT : "lastMatchOffset == offsetT";
                assert state != null : "state != null";

                // now add transitions for values less than this non-0 digit
                for (byte bb : Range.digitSequence((byte) '0', range.top[offsetT], true, false)) {
                    nextNameState = insertMatch(bb, state, nextNameState, range);
                }
            }
        }

        // now for last "top" digit

        // similarly, if the last digit is 0 and we have openTop, there can be no matches so we're done.
        if ((lastTop > '0') || !range.openTop) {

            // add transitions for any 0's we bypassed
            while (lastMatchOffset < range.top.length - 1) {
                state = findOrMakeNextByteState(state, range.top[lastMatchOffset], lastMatchOffset++);
            }
            assert lastMatchOffset == (range.top.length - 1) : "lastMatchOffset == (range.top.length - 1)";
            assert state != null : "state != null";

            // now we insert matches for possible values of last digit
            if (!range.openTop) {
                nextNameState = insertMatch(lastTop, state, nextNameState, range);
            }

            // unless the last digit is also at the fork position, fill in the extra matches due to
            //  the strictly-less-than condition (see discussion above)
            if (forkOffset < (range.top.length - 1)) {
                for (byte bb : Range.digitSequence((byte) '0', lastTop, true, false)) {
                    nextNameState = insertMatch(bb, state, nextNameState, range);
                }
            }
        }

        return nextNameState;
    }

    // return the index of the next byte state after transitioning from the one at the current stateIndex
    //  on the value b.  May have to create it if it doesn't exist.
    private ByteState findOrMakeNextByteState(ByteState state, final byte b, int currentIndex) {
        ByteTransition trans = getTransition(state, b);

        // If we meet shortcut trans, that means Range have byte overlapped with shortcut matches,
        // To simplify the logic, we extend the shortcut to normal byte state chain, then decide whether need create
        // new byte state for current call.
        if(trans.isShortcutTrans()) {
            String valueInCurrentPos = ((ValuePatterns) trans.getMatch().getPattern()).pattern();
            final byte[] utf8bytesInCurrentPos = valueInCurrentPos.getBytes(StandardCharsets.UTF_8);
            ByteState firstNewState = null;
            ByteState currentState = state;
            for (int k = currentIndex; k < utf8bytesInCurrentPos.length-1; k++) {
                // we need keep the current state always pointed to last byte.
                final ByteState newByteState = new ByteState();
                if (k != currentIndex) {
                    setTransitionNextState(currentState, utf8bytesInCurrentPos[k], EmptyByteTransition.INSTANCE, newByteState);
                } else {
                    firstNewState = newByteState;
                }
                currentState = newByteState;
            }
            setTransitionMatch(currentState, utf8bytesInCurrentPos[utf8bytesInCurrentPos.length-1], EmptyByteTransition.INSTANCE, trans.getMatch());
            setTransitionNextState(state, b, EmptyByteTransition.INSTANCE, firstNewState);
            trans = getTransition(state, b);
        }

        ByteState nextState = trans.getNextByteState();
        if (nextState == null) {
            // the next state is null, create a new state, set the transition's next state to the new state
            nextState = new ByteState();
            setTransitionNextState(state, b, trans, nextState);
        }

        return nextState;
    }

    private ByteState findNextByteState(ByteState state, final byte b) {
        if (state == null) {
            return null;
        }

        ByteTransition trans = getTransition(state, b);
        return trans.getNextByteState();
    }

    // add a match type pattern, i.e. anything but a numeric range, to the byte machine.
    private NameState addMatchPattern(final ValuePatterns pattern) {
        return addMatchValue(pattern, pattern.pattern(), null);
    }

    // We can reach to this function when we have checked the existing bytes array from left to right and found we need
    // add the match in the tail byte or we find we can shortcut to tail directly without creating new byte transition
    // in the middle.
    // If we met the shortcut transition, we need compare the input value to adjust it accordingly. please refer to
    // detail comments in ShortcutTransition.java.
    private NameState addEndOfMatch(ByteState byteState,
                                    final byte[] utf8bytes,
                                    final int byteIndex,
                                    final Patterns pattern,
                                    final NameState nameStateCandidate) {

        final NameState nameState = (nameStateCandidate == null) ? new NameState() : nameStateCandidate;

        ByteTransition trans = getTransition(byteState, utf8bytes[byteIndex]);
        // If we reach to addEndOfMatch, it means we have already traversed the path and get stopped at position of
        // current byteIndex.
        assert byteIndex >= utf8bytes.length - 1 || trans.getNextByteState() == null;

        // If it is shortcut transition, we need do adjustment first.
        if (!trans.isEmpty() && trans.isShortcutTrans()) {
            ByteMatch match = trans.getMatch();
            // In add/delete rule path, match must not be null and must not have other match
            assert match != null && SHORTCUT_MATCH_TYPES.contains(match.getPattern().type()) && match.getNextMatch() == null;
            // If it is the same pattern, just return.
            if (pattern.equals(match.getPattern())) {
                return match.getNextNameState();
            }
            // Have asserted current match pattern must be value patterns
            String valueInCurrentPos = ((ValuePatterns) match.getPattern()).pattern();
            final byte[] utf8bytesInCurrentPos = valueInCurrentPos.getBytes(StandardCharsets.UTF_8);
            // find the position <m> where the common prefix ends.
            int m = byteIndex;
            for (; m < utf8bytesInCurrentPos.length && m < utf8bytes.length; m++) {
                if (utf8bytesInCurrentPos[m] != utf8bytes[m]) {
                    break;
                }
            }
            // Extend the prefix part in value to byte transitions, to avoid impact on concurrent read
            // we need firstly make the new byte chain ready for using and leave the old transition removing to the last step.
            // firstNewState will be head of new byte chain and, to avoid impact on concurrent match traffic in read path,
            // it need be linked to current byteState chain after adjustment done.
            ByteState firstNewState = null;
            ByteState currentState = byteState;
            for (int k = byteIndex; k < m; k++) {
                // we need keep the current state always pointed to last byte.
                if (k != utf8bytesInCurrentPos.length -1) {
                    final ByteState newByteState = new ByteState();
                    if (k != byteIndex) {
                        setTransitionNextState(currentState, utf8bytesInCurrentPos[k], EmptyByteTransition.INSTANCE, newByteState);
                    } else {
                        firstNewState = newByteState;
                    }
                    currentState = newByteState;
                }
            }

            // If it reached to last byte, link the previous read transition in this byte, else create shortcut transition.
            // Note: at this time, the previous transition can still keep working.
            int indexToBeChange = m;
            if (m == utf8bytesInCurrentPos.length || m == utf8bytesInCurrentPos.length - 1) {
                indexToBeChange = utf8bytesInCurrentPos.length - 1;
                setTransitionMatch(currentState, utf8bytesInCurrentPos[indexToBeChange], EmptyByteTransition.INSTANCE, match);
            } else { // m is not at tail of utf8bytesInCurrentPos, we just create the shortcut trans to position of m.
                setTransitionMatch(currentState, utf8bytesInCurrentPos[indexToBeChange], new ShortcutTransition(), match);
            }

            // At last, we link the new created chain to the byte state path, so no uncompleted change can be felt by reading thread.
            // Note: we already confirmed there is only old shortcut transition at byteIndex position, now we have move it to new
            // position, so we can directly replace previous transition with new transition pointed to new byte state chain.
            setTransitionNextState(byteState, utf8bytes[byteIndex], EmptyByteTransition.INSTANCE, firstNewState);
        }

        // If there is a exact match transition on tail of path, after adjustment target transitions, we start
        // looking at current remaining bytes.
        // If this is tail transition, go directly analyse the remaining bytes, traverse to tail of chain:
        int j = byteIndex;
        for (; j < (utf8bytes.length - 1); j++) {
            trans = getTransition(byteState,utf8bytes[j]);
            if (trans.isEmpty()) {
                break;
            }
            if (trans.getNextByteState() != null) {
                byteState = trans.getNextByteState();
            } else {
                // trans has match but no next state, we need prepare a next next state to add trans for either last byte
                // or shortcut byte.
                final ByteState newByteState = new ByteState();
                setTransitionNextState(byteState, utf8bytes[j], trans, newByteState);
                byteState = newByteState;
            }
        }

        // look for a chance to put in a shortcut transition.
        // However, for the moment, we only do this for a JSON string match i.e beginning with ", not literals
        //  like true or false or numbers, because if we do this for numbers produced by
        //  ComparableNumber.generate(), they can be messed up by addRangePattern.
        if (SHORTCUT_MATCH_TYPES.contains(pattern.type())) {
            // For exactly match, if it is last byte already, we just put the real transition with match there.
            if (j == utf8bytes.length - 1) {
                return insertMatch(utf8bytes[j], byteState, nameState, pattern);
            } else {
                // If current byte is not last bytes, create the shortcut transition with the next
                ByteMatch byteMatch = new ByteMatch(pattern, nameState);
                setTransitionMatch(byteState, utf8bytes[j], new ShortcutTransition(), byteMatch);
                addMatchReferences(byteMatch);
                return nameState;
            }
        }
        // For other match type, keep the old logic to extend all bytes to byte state path and put the match in the tail state.
        for (; j < (utf8bytes.length - 1); j++) {
            byteState = findOrMakeNextByteState(byteState, utf8bytes[j], j);
        }
        return insertMatch(utf8bytes[utf8bytes.length - 1], byteState, nameState, pattern);
    }

    private NameState insertMatch(byte b, ByteState state, NameState nextNameState, Patterns pattern) {
        ByteTransition trans = getTransition(state, b);

        ByteMatch match = findMatch(trans.getMatch(), pattern);
        if (match != null) {
            // There is a match linked to the transition that's the same type, so we just re-use its nextNameState
            return match.getNextNameState();
        }

        // we make a new NameState and hook it up
        NameState nameState = nextNameState == null ? new NameState() : nextNameState;

        match = new ByteMatch(pattern, nameState);
        match.setNextMatch(trans.getMatch());

        addMatchReferences(match);

        setTransitionMatch(state, b, trans, match);

        return nameState;
    }

    private void addMatchReferences(ByteMatch match) {
        Patterns pattern = match.getPattern();
        switch (pattern.type()) {
        case EXACT:
        case PREFIX:
        case EXISTS:
            break;
        case SUFFIX:
            hasSuffix.incrementAndGet();
            break;
        case NUMERIC_EQ:
            hasNumeric.incrementAndGet();
            break;
        case NUMERIC_RANGE:
            final Range range = (Range) pattern;
            if (range.isCIDR) {
                hasIP.incrementAndGet();
            } else {
                hasNumeric.incrementAndGet();
            }
            break;
        case ANYTHING_BUT:
            anythingButs.add(match);
            if (((AnythingBut) pattern).isNumeric()) {
                hasNumeric.incrementAndGet();
            }
            break;
        case ANYTHING_BUT_PREFIX:
            anythingButs.add(match);
            break;
        case EQUALS_IGNORE_CASE:
            hasEqualsIgnoreCase.incrementAndGet();
            break;
        default:
            throw new AssertionError("Not implemented yet");
        }
    }

    private NameState findMatch(byte b, ByteState state, Patterns pattern) {
        if (state == null) {
            return null;
        }

        ByteTransition trans = getTransition(state, b);

        ByteMatch match = findMatch(trans.getMatch(), pattern);
        return match == null ? null : match.getNextNameState();
    }

    private void deleteMatch(byte b, ByteState state, Patterns pattern) {
        if (state == null) {
            return;
        }

        ByteTransition trans = getTransition(state, b);

        // find the match with the given pattern
        for (ByteMatch match = trans.getMatch(), prevMatch = null; match != null; match = match.getNextMatch()) {
            if (match.getPattern().equals(pattern)) {
                // the match is found
                if (prevMatch == null) {
                    // the match found is the first match in the transition's match list, set the transition's match to
                    // the next match
                    setTransitionMatch(state, b, trans, match.getNextMatch());
                } else {
                    // the match found is not the first match in the transition's match list, remove the match from the
                    // transition's match list
                    prevMatch.setNextMatch(match.getNextMatch());
                }

                updateMatchReferences(match);

                return;
            }

            prevMatch = match;
        }
    }

    private void updateMatchReferences(ByteMatch match) {
        Patterns pattern = match.getPattern();
        switch (pattern.type()) {
        case EXACT:
        case PREFIX:
        case EXISTS:
            break;
        case SUFFIX:
            hasSuffix.decrementAndGet();
            break;
        case NUMERIC_EQ:
            hasNumeric.decrementAndGet();
            break;
        case NUMERIC_RANGE:
            final Range range = (Range) pattern;
            if (range.isCIDR) {
                hasIP.decrementAndGet();
            } else {
                hasNumeric.decrementAndGet();
            }
            break;
        case ANYTHING_BUT:
            anythingButs.remove(match);
            if (((AnythingBut) pattern).isNumeric()) {
                hasNumeric.decrementAndGet();
            }
            break;
        case ANYTHING_BUT_PREFIX:
            anythingButs.remove(match);
            break;
        case EQUALS_IGNORE_CASE:
            hasEqualsIgnoreCase.decrementAndGet();
            break;
        default:
            throw new AssertionError("Not implemented yet");
        }
    }

    private static ByteTransition getTransition(ByteState state, byte b) {
        ByteTransition transition = state.getTransition(b);
        return transition == null ? EmptyByteTransition.INSTANCE : transition;
    }

    private static void setTransitionNextState(ByteState state, byte b, ByteTransition transition,
            ByteState nextState) {
        // In order to avoid change being felt by the concurrent query thread in the middle of change, we clone the
        // trans firstly and will not update state store until the changes have completely applied in the new trans.
        ByteTransition trans = transition.clone();
        trans = trans.setNextByteState(nextState);
        updateTransition(state, b, transition, trans);
    }

    private static void setTransitionMatch(ByteState state, byte b, ByteTransition transition, ByteMatch match) {
        // In order to avoid change being felt by the concurrent query thread in the middle of change, we clone the
        // trans firstly and will not update state store until the changes have completely applied in the new trans.
        ByteTransition trans = transition.clone();
        trans = trans.setMatch(match);
        updateTransition(state, b, transition, trans);
    }

    private static void updateTransition(ByteState state, byte b, ByteTransition oldTransition,
            ByteTransition newTransition) {
        if (newTransition == null || newTransition.isEmpty()) {
            state.removeTransition(b);
        } else if (newTransition != oldTransition) {
            state.putTransition(b, newTransition);
        }
    }

    private static ByteMatch findMatch(ByteMatch match, Patterns pattern) {
        while (match != null) {
            if (match.getPattern().equals(pattern)) {
                return match;
            }
            match = match.getNextMatch();
        }
        return null;
    }

    public static final class EmptyByteTransition extends ByteTransition {

        static final EmptyByteTransition INSTANCE = new EmptyByteTransition();

        @Override
        public ByteState getNextByteState() {
            return null;
        }

        @Override
        public ByteTransition setNextByteState(ByteState nextState) {
            return nextState;
        }

        @Override
        public ByteMatch getMatch() {
            return null;
        }

        @Override
        public ByteTransition setMatch(ByteMatch match) {
            return match;
        }
    }

    @Override
    public String toString() {
        return "ByteMachine{" +
                "startState=" + startState +
                ", hasNumeric=" + hasNumeric +
                ", hasIP=" + hasIP +
                ", hasSuffix=" + hasSuffix +
                ", anythingButs=" + anythingButs +
                '}';
    }
}
