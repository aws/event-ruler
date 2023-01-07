package software.amazon.event.ruler;

import com.fasterxml.jackson.core.io.doubleparser.FastDoubleParser;
import software.amazon.event.ruler.input.InputByte;
import software.amazon.event.ruler.input.InputCharacter;
import software.amazon.event.ruler.input.InputCharacterType;
import software.amazon.event.ruler.input.InputMultiByteSet;
import software.amazon.event.ruler.input.MultiByte;

import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.annotation.concurrent.ThreadSafe;

import static software.amazon.event.ruler.CompoundByteTransition.coalesce;
import static software.amazon.event.ruler.MatchType.EXACT;
import static software.amazon.event.ruler.MatchType.EXISTS;
import static software.amazon.event.ruler.MatchType.SUFFIX;
import static software.amazon.event.ruler.input.MultiByte.MAX_FIRST_BYTE_FOR_ONE_BYTE_CHAR;
import static software.amazon.event.ruler.input.MultiByte.MAX_FIRST_BYTE_FOR_TWO_BYTE_CHAR;
import static software.amazon.event.ruler.input.MultiByte.MAX_NON_FIRST_BYTE;
import static software.amazon.event.ruler.input.MultiByte.MIN_FIRST_BYTE_FOR_ONE_BYTE_CHAR;
import static software.amazon.event.ruler.input.MultiByte.MIN_FIRST_BYTE_FOR_TWO_BYTE_CHAR;
import static software.amazon.event.ruler.input.Parser.getParser;

/**
 * Represents a UTF8-byte-level state machine that matches a Ruler state machine's field values.
 * Each state is a map keyed by utf8 byte. getTransition(byte) yields a Target, which can contain either or
 *  both of the next ByteState, and the first of a chain of Matches, which indicate a match to some pattern.
 */
@ThreadSafe
class ByteMachine {

    // Only these match types support shortcuts during traversal.
    private static final Set<MatchType> SHORTCUT_MATCH_TYPES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(EXACT)));

    private final ByteState startState = new ByteState();
    // For wildcard rule "*", the start state is a match.
    private ByteMatch startStateMatch;

    // non-zero if the machine has a numerical-comparison pattern included.  In which case, values
    //  should be converted to ComparableNumber before testing for matches, if possible.
    //
    private final AtomicInteger hasNumeric = new AtomicInteger(0);

    // as with the previous, but signals the presence of an attempt to match an IP address
    //
    private final AtomicInteger hasIP = new AtomicInteger(0);

    // signal the presence of suffix match in current byteMachine instance
    private final AtomicInteger hasSuffix = new AtomicInteger(0);

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
    //  different NameState objects.  Now, there are optimizations to be had with default-values in ByteStates,
    //  and lazily allocating Matches only when you have unique values, but all of these things would add
    //  considerable complexity, and this is also easy to understand, and probably not much slower.
    //
    private final Set<NameState> anythingButs = ConcurrentHashMap.newKeySet();

    // Multiple different next-namestate steps can result from  processing a single field value, for example
    //  "foot" matches "foot" exactly, "foo" as a prefix, and "hand" as an anything-but.  So, this
    //  method returns a list.
    Set<NameState> transitionOn(String valString) {

        // not thread-safe, but this is only used in the scope of this method on one thread
        final Set<NameState> transitionTo = new HashSet<>();
        boolean fieldValueIsNumeric = false;
        if (hasNumeric.get() > 0) {
            try {
                final double numerically = FastDoubleParser.parseDouble(valString);
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
        if (startState.hasNoTransitions() && startStateMatch == null) {
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
            case WILDCARD:
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
        final InputCharacter[] characters = getParser().parse(pattern.type(), Patterns.EXISTS_BYTE_STRING);
        deleteMatchStep(startState, 0, pattern, characters);
    }

    private void deleteAnythingButPattern(AnythingBut pattern) {
        pattern.getValues().forEach(value ->
            deleteMatchStep(startState, 0, pattern, getParser().parse(pattern.type(), value)));
    }

    private void deleteMatchPattern(ValuePatterns pattern) {
        final InputCharacter[] characters = getParser().parse(pattern.type(), pattern.pattern());

        if (characters.length == 1 && isWildcard(characters[0])) {
            // Only character is wildcard. Remove the start state match.
            startStateMatch = null;
            return;
        }

        deleteMatchStep(startState, 0, pattern, characters);
    }

    private void deleteMatchStep(ByteState byteState, int charIndex, Patterns pattern,
                                 final InputCharacter[] characters) {
        final InputCharacter currentChar = characters[charIndex];
        final ByteTransition trans = getTransition(byteState, currentChar);

        for (SingleByteTransition eachTrans : trans.expand()) {
            if (charIndex < characters.length - 1) {
                // if it is shortcut trans, we delete it directly.
                if (eachTrans.isShortcutTrans()) {
                    deleteMatch(currentChar, byteState, pattern, eachTrans);
                } else {
                    ByteState nextByteState = eachTrans.getNextByteState();
                    if (nextByteState != null && nextByteState != byteState) {
                        deleteMatchStep(nextByteState, charIndex + 1, pattern, characters);
                    }

                    // Perform handling for certain wildcard cases.
                    eachTrans = deleteMatchStepForWildcard(byteState, charIndex, pattern, characters, eachTrans,
                            nextByteState);

                    if (nextByteState != null &&
                            (nextByteState.hasNoTransitions() || nextByteState.hasOnlySelfReferentialTransition())) {
                        // The transition's next state has no meaningful transitions, so compact the transition.
                        putTransitionNextState(byteState, currentChar, eachTrans, null);
                    }
                }
            } else {
                deleteMatch(currentChar, byteState, pattern, eachTrans);
            }
        }
    }

    /**
     * Performs delete match step handling for certain wildcard cases.
     *
     * @param byteState The current ByteState.
     * @param charIndex The index of the current character in characters.
     * @param pattern The pattern we are deleting.
     * @param characters The array of InputCharacters corresponding to the pattern's value.
     * @param transition One of the transitions from byteState using the current byte.
     * @param nextByteState The next ByteState led to by transition.
     * @return Transition, or a replacement for transition if the original instance no longer exists in the machine.
     */
    private SingleByteTransition deleteMatchStepForWildcard(ByteState byteState, int charIndex, Patterns pattern,
                                                            InputCharacter[] characters,
                                                            SingleByteTransition transition, ByteState nextByteState) {
        final InputCharacter currentChar = characters[charIndex];

        // There will be a match using second last character on second last state when last character is
        // a wildcard. This allows empty substring to satisfy wildcard.
        if (charIndex == characters.length - 2 && isWildcard(characters[characters.length - 1])) {
            // Delete the match.
            SingleByteTransition updatedTransition = deleteMatch(currentChar, byteState, pattern, transition);
            if (updatedTransition != null) {
                return updatedTransition;
            }

        // Undo the machine changes for a wildcard as described in the Javadoc of addTransitionNextStateForWildcard.
        } else if (nextByteState != null && isWildcard(currentChar)) {
            // Remove transition for all possible byte values if it leads to an only self-referencing state
            if (nextByteState.hasOnlySelfReferentialTransition()) {
                byteState.removeTransitionForAllBytes(transition);
            }

            // Remove match on last char that exists for second-last char wildcard on second last state to be satisfied
            // by empty substring.
            if (charIndex == characters.length - 2 && isWildcard(characters[characters.length - 2])) {
                deleteMatches(characters[charIndex + 1], byteState, pattern);
            // Remove match on second last char that exists for third last and last char wildcard combination on third
            // last state to be both satisfied by empty substrings. I.e. "ax" can match "a*x*".
            } else if (charIndex == characters.length - 3 && isWildcard(characters[characters.length - 1]) &&
                    isWildcard(characters[characters.length - 3])) {
                deleteMatches(characters[charIndex + 1], byteState, pattern);
            }

            // Remove transition that exists for wildcard to be satisfied by empty substring.
            ByteTransition skipWildcardTransition = getTransition(byteState, characters[charIndex + 1]);
            for (SingleByteTransition eachTrans : skipWildcardTransition.expand()) {
                ByteState skipWildcardState = eachTrans.getNextByteState();
                if (eachTrans.getMatches().isEmpty() && skipWildcardState != null &&
                        (skipWildcardState.hasNoTransitions() ||
                         skipWildcardState.hasOnlySelfReferentialTransition())) {
                    removeTransition(byteState, characters[charIndex + 1], eachTrans);
                }
            }
        }

        return transition;
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
        final ArrayDeque<AbstractMap.SimpleImmutableEntry<Byte, ByteTransition>> byteStatesTraversePathAlongRangeBottomValue =
                new ArrayDeque<>(ComparableNumber.MAX_LENGTH_IN_BYTES);
        final ArrayDeque<AbstractMap.SimpleImmutableEntry<Byte, ByteTransition>> byteStatesTraversePathAlongRangeTopValue =
                new ArrayDeque<>(ComparableNumber.MAX_LENGTH_IN_BYTES);

        ByteTransition forkState = startState;
        int forkOffset = 0;
        byteStatesTraversePathAlongRangeBottomValue.addFirst(new AbstractMap.SimpleImmutableEntry<>(range.bottom[0], forkState));
        byteStatesTraversePathAlongRangeTopValue.addFirst(new AbstractMap.SimpleImmutableEntry<>(range.top[0], forkState));

        // bypass common prefix of range's bottom and top patterns
        // we need move forward the state and save all states traversed for checking later.
        while (range.bottom[forkOffset] == range.top[forkOffset]) {
            forkState = findNextByteStateForRangePattern(forkState, range.bottom[forkOffset]);
            assert forkState != null : "forkState != null";
            byteStatesTraversePathAlongRangeBottomValue.addFirst(new AbstractMap.SimpleImmutableEntry<>(range.bottom[forkOffset], forkState));
            byteStatesTraversePathAlongRangeTopValue.addFirst(new AbstractMap.SimpleImmutableEntry<>(range.top[forkOffset], forkState));
            forkOffset++;
        }

        // when bottom byte on forkOffset position < top byte in same position, there must be matches existing
        // in this state, go ahead to delete matches in the fork state.
        for (byte bb : Range.digitSequence(range.bottom[forkOffset], range.top[forkOffset], false, false)) {
            deleteMatches(getParser().parse(bb), forkState, range);
        }

        // process all the transitions on the bottom range bytes
        ByteTransition state = forkState;
        int lastMatchOffset = forkOffset;

        // see explanation in addRangePattern(), we need delete state and match accordingly.
        for (int offsetB = forkOffset + 1; offsetB < (range.bottom.length - 1); offsetB++) {
            byte b = range.bottom[offsetB];
            if (b < Constants.MAX_DIGIT) {
                while (lastMatchOffset < offsetB) {
                    state = findNextByteStateForRangePattern(state, range.bottom[lastMatchOffset]);
                    assert state != null : "state must be existing for this pattern";
                    byteStatesTraversePathAlongRangeBottomValue.addFirst(
                            new AbstractMap.SimpleImmutableEntry<>(range.bottom[lastMatchOffset], state));
                    lastMatchOffset++;
                }
                assert lastMatchOffset == offsetB : "lastMatchOffset == offsetB";
                for (byte bb : Range.digitSequence(b, Constants.MAX_DIGIT, false, true)) {
                    deleteMatches(getParser().parse(bb), state, range);
                }
            }
        }

        // now for last "bottom" digit
        // see explanation in addRangePattern(), we need to delete states and matches accordingly.
        final byte lastBottom = range.bottom[range.bottom.length - 1];
        final byte lastTop = range.top[range.top.length - 1];
        if ((lastBottom < Constants.MAX_DIGIT) || !range.openBottom) {
            while (lastMatchOffset < range.bottom.length - 1) {
                state = findNextByteStateForRangePattern(state, range.bottom[lastMatchOffset]);
                assert state != null : "state != null";
                byteStatesTraversePathAlongRangeBottomValue.addFirst(new AbstractMap.SimpleImmutableEntry<>(range.bottom[lastMatchOffset], state));
                lastMatchOffset++;
            }
            assert lastMatchOffset == range.bottom.length - 1 : "lastMatchOffset == range.bottom.length - 1";
            if (!range.openBottom) {
                deleteMatches(getParser().parse(lastBottom), state, range);
            }
            // unless the last digit is also at the fork position, fill in the extra matches due to
            //  the strictly-less-than condition (see discussion above)
            if (forkOffset < (range.bottom.length - 1)) {
                for (byte bb : Range.digitSequence(lastBottom, Constants.MAX_DIGIT, false, true)) {
                    deleteMatches(getParser().parse(bb), state, range);
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
                    state = findNextByteStateForRangePattern(state, range.top[lastMatchOffset]);
                    assert state != null : "state must be existing for this pattern";
                    byteStatesTraversePathAlongRangeTopValue.addFirst(new AbstractMap.SimpleImmutableEntry<>(range.top[lastMatchOffset], state));
                    lastMatchOffset++;
                }
                assert lastMatchOffset == offsetT : "lastMatchOffset == offsetT";

                for (byte bb : Range.digitSequence((byte) '0', range.top[offsetT], true, false)) {
                    deleteMatches(getParser().parse(bb), state, range);
                }
            }
        }

        // now for last "top" digit.
        // see explanation in addRangePattern(), we need to delete states and matches accordingly.
        if ((lastTop > '0') || !range.openTop) {
            while (lastMatchOffset < range.top.length - 1) {
                state = findNextByteStateForRangePattern(state, range.top[lastMatchOffset]);
                assert state != null : "state != null";
                byteStatesTraversePathAlongRangeTopValue.addFirst(new AbstractMap.SimpleImmutableEntry<>(range.top[lastMatchOffset], state));
                lastMatchOffset++;
            }
            assert lastMatchOffset == range.top.length - 1 : "lastMatchOffset == range.top.length - 1";
            if (!range.openTop) {
                deleteMatches(getParser().parse(lastTop), state, range);
            }

            // unless the last digit is also at the fork position, fill in the extra matches due to
            //  the strictly-less-than condition (see discussion above)
            if (forkOffset < (range.top.length - 1)) {
                for (byte bb : Range.digitSequence((byte) '0', lastTop, true, false)) {
                    deleteMatches(getParser().parse(bb), state, range);
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

    private void checkAndDeleteStateAlongTraversedPath(
            ArrayDeque<AbstractMap.SimpleImmutableEntry<Byte, ByteTransition>> byteStateQueue) {

        if (byteStateQueue.isEmpty()) {
            return;
        }

        Byte childByteKey = byteStateQueue.pollFirst().getKey();
        while (!byteStateQueue.isEmpty()) {
            final AbstractMap.SimpleImmutableEntry<Byte, ByteTransition> parentStatePair = byteStateQueue.pollFirst();
            final Byte parentByteKey = parentStatePair.getKey();
            final ByteTransition parentByteTransition = parentStatePair.getValue();
            for (SingleByteTransition singleParent : parentByteTransition.expand()) {

                // Check all transitions from singleParent using childByteKey. Delete each transition that is a dead-end
                // (i.e. transition that has no transitions itself).
                ByteTransition transition = getTransition(singleParent, childByteKey);
                for (SingleByteTransition eachTrans : transition.expand()) {
                    ByteState nextState = eachTrans.getNextByteState();
                    if (nextState != null && nextState.hasNoTransitions()) {
                        putTransitionNextState(singleParent.getNextByteState(), getParser().parse(childByteKey),
                                eachTrans, null);
                    }
                }

            }
            childByteKey = parentByteKey;
        }
    }

    private void doTransitionOn(final String valString, final Set<NameState> transitionTo, boolean fieldValueIsNumeric) {
        final Set<NameState> failedAnythingButs = new HashSet<>();
        final byte[] val = valString.getBytes(StandardCharsets.UTF_8);

        // we need to add the name state for key existence
        addExistenceMatch(transitionTo);

        // attempt to harvest the possible suffix match
        addSuffixMatch(val, transitionTo);

        if (startStateMatch != null) {
            transitionTo.add(startStateMatch.getNextNameState());
        }

        // we have to do old-school indexing rather than "for (byte b : val)" because there is some special-casing
        // on transitions on the last byte in the value array
        ByteTransition trans = startState;
        for (int valIndex = 0; valIndex < val.length; valIndex++) {
            final ByteTransition nextTrans = getTransition(trans, val[valIndex]);

            attemptAddShortcutTransitionMatch(nextTrans, valString, EXACT, transitionTo);

            if (!nextTrans.isShortcutTrans()) {

                // process any matches hanging off this transition
                for (ByteMatch match : nextTrans.getMatches()) {
                    switch (match.getPattern().type()) {
                        case EXACT:
                        case EQUALS_IGNORE_CASE:
                        case WILDCARD:
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
                        case EXISTS:
                            // we already harvested these matches via separate functions due to special matching
                            // requirements, so just ignore them here.
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
                                failedAnythingButs.add(match.getNextNameState());
                            }
                            break;

                        case ANYTHING_BUT_PREFIX:
                            failedAnythingButs.add(match.getNextNameState());
                            break;

                        default:
                            throw new RuntimeException("Not implemented yet");

                    }
                }
            }

            trans = nextTrans.getTransitionForNextByteStates();
            if (trans == null) {
                break;
            }
        }

        // This may look like premature optimization, but the first "if" here yields roughly 10x performance
        // improvement.
        if (!anythingButs.isEmpty()) {
            if (!failedAnythingButs.isEmpty()) {
                transitionTo.addAll(anythingButs.stream()
                                                .filter(anythingBut -> !failedAnythingButs.contains(anythingBut))
                                                .collect(Collectors.toList()));
            } else {
                transitionTo.addAll(anythingButs);
            }
        }
    }

    private void addExistenceMatch(final Set<NameState> transitionTo) {
        final byte[] val = Patterns.EXISTS_BYTE_STRING.getBytes(StandardCharsets.UTF_8);

        ByteTransition trans = startState;
        ByteTransition nextTrans = null;
        for (int valIndex = 0; valIndex < val.length && trans != null; valIndex++) {
            nextTrans = getTransition(trans, val[valIndex]);
            trans = nextTrans.getTransitionForNextByteStates();
        }

        if (nextTrans == null) {
            return;
        }

        for (ByteMatch match : nextTrans.getMatches()) {
            if (match.getPattern().type() == EXISTS) {
                transitionTo.add(match.getNextNameState());
                break;
            }
        }
    }

    private void addSuffixMatch(final byte[] val, final Set<NameState> transitionTo) {
        // we only attempt to evaluate suffix matches when there is suffix match in current byte machine instance.
        // it works as performance level to avoid other type of matches from being affected by suffix checking.
        if (hasSuffix.get() > 0) {
            ByteTransition trans = startState;
            // check the byte in reverse order in order to harvest suffix matches
            for (int valIndex = val.length - 1; valIndex >= 0; valIndex--) {
                final ByteTransition nextTrans = getTransition(trans, val[valIndex]);
                for (ByteMatch match : nextTrans.getMatches()) {
                    // given we are traversing in reverse order (from right to left), only suffix matches are eligible
                    // to be collected.
                    if (match.getPattern().type() == SUFFIX) {
                        transitionTo.add(match.getNextNameState());
                    }
                }
                trans = nextTrans.getTransitionForNextByteStates();
                if (trans == null) {
                    break;
                }
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
        for (ShortcutTransition shortcut : transition.getShortcuts()) {
            ByteMatch match = shortcut.getMatch();
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
            case WILDCARD:
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

        final InputCharacter[] characters = getParser().parse(pattern.type(), value);
        ByteState byteState = startState;
        ByteState prevByteState = null;
        int i = 0;
        for (; i < characters.length - 1; i++) {
            ByteTransition trans = getTransition(byteState, characters[i]);
            if (trans.isEmpty()) {
                break;
            }
            ByteState stateToReuse = null;
            for (SingleByteTransition single : trans.expand()) {
                ByteState nextByteState = single.getNextByteState();
                if (canReuseNextByteState(byteState, nextByteState, characters, i)) {
                    stateToReuse = nextByteState;
                    break;
                }
            }

            if (stateToReuse == null) {
                break;
            }
            prevByteState = byteState;
            byteState = stateToReuse;
        }
        // we found our way through the machine with all characters except the last having matches or shortcut.
        return addEndOfMatch(byteState, prevByteState, characters, i, pattern, nameStateToBeReturned);
    }

    private boolean canReuseNextByteState(ByteState byteState, ByteState nextByteState, InputCharacter[] characters,
                                          int i) {
        // We cannot re-use nextByteState if it is non-existent (null) or if it is the same as the current byteState,
        // meaning we are looping on a self-referencing transition.
        if (nextByteState == null || nextByteState == byteState) {
            return false;
        }

        // Case 1: When we have a determinate prefix, we can re-use nextByteState except for the special case where we
        //         are on the second-last character with a final character wildcard. A composite is required for this
        //         case, so we will create a new state.
        //
        //         Example 1: Take the following machine representing an exact match pattern.
        //              a        b        c
        //            -----> A -----> B -----> C
        //         With a byteState of A, a current char of b, and a next and final char of *, we would be unable to
        //         re-use B as nextByteState, since we need to create a new self-referencing composite state D.
        if (!nextByteState.hasIndeterminatePrefix()) {
            return !(i == characters.length - 2 && isWildcard(characters[i + 1]));

        // Case 2: nextByteState has an indeterminate prefix and current character is not a wildcard. We can re-use
        //         nextByteState only if byteState does not have at least two transitions, one using the next
        //         InputCharacter, that eventually converge to a common state.
        //
        //         Example 2a: Take the following machine representing a wildcard pattern.
        //                             ____
        //              a        *    | *  |
        //            -----> A -----> B <--
        //                   | b    b |
        //                   --> C <--
        //         With a byteState of A, and a current char of b, we would be unable to re-use C as nextByteState,
        //         since A has a second transition to B that eventually leads to C as well. But we could re-use B.
        //
        //         Example 2b: Take the following machine representing an equals_ignore_case pattern.
        //              x       Y
        //            -----> A ------
        //                   |  y   v
        //                   -----> B
        //         With a byteState of A, and a current char of y, we would be unable to re-use B as nextByteState,
        //         since A has a second transition to B using char Y.
        } else {
            return !doMultipleTransitionsConvergeForInputByte(byteState, characters, i);
        }
    }

    /**
     * Returns true if the current InputCharacter is the first InputByte of a Java character and there exists at least
     * two transitions, one using the next InputCharacter, away from byteState leading down paths that eventually
     * converge to a common state.
     *
     * @param byteState State where we look for at least two transitions that eventually converge to a common state.
     * @param characters The array of InputCharacters.
     * @param i The current index in the characters array.
     * @return True if and only if multiple transitions eventually converge to a common state.
     */
    private boolean doMultipleTransitionsConvergeForInputByte(ByteState byteState, InputCharacter[] characters, int i) {
        if (!isByte(characters[i])) {
            return false;
        }

        if (!isNextCharacterFirstByteOfMultiByte(characters, i)) {
            // If we are in the midst of a multi-byte sequence, we know that we are dealing with single transitions.
            return false;
        }

        // Scenario 1 where multiple transitions will later converge: wildcard leads to wildcard state and following
        // character can be used to skip wildcard state. Check for a non-self-referencing wildcard transition.
        ByteTransition byteStateTransitionForAllBytes = byteState.getTransitionForAllBytes();
        if (!byteStateTransitionForAllBytes.isEmpty() && byteStateTransitionForAllBytes != byteState) {
            return true;
        }

        // Scenario 2 where multiple transitions will later converge: equals_ignore_case lower and upper case paths.
        // Parse the next Java character into lower and upper case multibyte representations. Check if there are
        // multiple multibytes (paths) and that there exists a transition that both lead to.
        String value = extractNextJavaCharacterFromInputCharacters(characters, i);
        InputCharacter[] inputCharacters = getParser().parse(MatchType.EQUALS_IGNORE_CASE, value);
        InputMultiByteSet inputMultiByteSet = InputMultiByteSet.cast(inputCharacters[0]);
        ByteTransition transition = getTransition(byteState, inputMultiByteSet);
        return inputMultiByteSet.getMultiBytes().size() > 1 && transition != null;
    }

    private boolean isNextCharacterFirstByteOfMultiByte(InputCharacter[] characters, int i) {
        byte firstByte = InputByte.cast(characters[i]).getByte();
        // Since MIN_NON_FIRST_BYTE is (byte) 0x80 = -128 = Byte.MIN_VALUE, we can simply compare against
        // MAX_NON_FIRST_BYTE to see if this is a first byte or not.
        return firstByte > MAX_NON_FIRST_BYTE;
    }

    private String extractNextJavaCharacterFromInputCharacters(InputCharacter[] characters, int i) {
        byte firstByte = InputByte.cast(characters[i]).getByte();
        if (firstByte >= MIN_FIRST_BYTE_FOR_ONE_BYTE_CHAR && firstByte <= MAX_FIRST_BYTE_FOR_ONE_BYTE_CHAR) {
            return new String(new byte[] { firstByte } , StandardCharsets.UTF_8);
        } else if (firstByte >= MIN_FIRST_BYTE_FOR_TWO_BYTE_CHAR && firstByte <= MAX_FIRST_BYTE_FOR_TWO_BYTE_CHAR) {
            return new String(new byte[] { firstByte, InputByte.cast(characters[i + 1]).getByte() },
                    StandardCharsets.UTF_8);
        } else {
            return new String(new byte[] { firstByte, InputByte.cast(characters[i + 1]).getByte(),
                    InputByte.cast(characters[i + 2]).getByte() }, StandardCharsets.UTF_8);
        }
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
        case WILDCARD:
            assert pattern instanceof ValuePatterns;
            return findMatchPattern((ValuePatterns) pattern);
        case EXISTS:
            return findMatchPattern(getParser().parse(pattern.type(), Patterns.EXISTS_BYTE_STRING), pattern);
        default:
            throw new AssertionError(pattern + " is not implemented yet");
        }
    }

    private NameState findAnythingButPattern(AnythingBut pattern) {

        Set<NameState> nextNameStates = new HashSet<>(pattern.getValues().size());
        for (String value : pattern.getValues()) {
            NameState matchPattern = findMatchPattern(getParser().parse(pattern.type(), value), pattern);
            if (matchPattern != null) {
                nextNameStates.add(matchPattern);
            }
        }
        if (!nextNameStates.isEmpty()) {
            assert nextNameStates.size() == 1 : "nextNameStates.size() == 1";
            return nextNameStates.iterator().next();
        }
        return null;
    }

    private NameState findMatchPattern(ValuePatterns pattern) {
        return findMatchPattern(getParser().parse(pattern.type(), pattern.pattern()), pattern);
    }

    private NameState findMatchPattern(final InputCharacter[] characters, final Patterns pattern) {
        Set<SingleByteTransition> transitionsToProcess = new HashSet<>();
        transitionsToProcess.add(startState);
        ByteTransition shortcutTrans = null;

        // Iterate down all possible paths in machine that match characters.
        outerLoop: for (int i = 0; i < characters.length - 1; i++) {
            Set<SingleByteTransition> nextTransitionsToProcess = new HashSet<>();
            for (SingleByteTransition trans : transitionsToProcess) {
                ByteTransition nextTrans = getTransition(trans, characters[i]);
                for (SingleByteTransition eachTrans : nextTrans.expand()) {

                    // Shortcut and stop outer character loop
                    if (SHORTCUT_MATCH_TYPES.contains(pattern.type()) && eachTrans.isShortcutTrans()) {
                        shortcutTrans = eachTrans;
                        break outerLoop;
                    }

                    SingleByteTransition nextByteState = eachTrans.getNextByteState();
                    if (nextByteState == null) {
                        return null;
                    }

                    // We are interested in the first state that hasn't simply led back to trans
                    if (trans != nextByteState) {
                        nextTransitionsToProcess.add(nextByteState);
                    }
                }
            }
            transitionsToProcess = nextTransitionsToProcess;
        }

        // Get all possible transitions for final character.
        Set<ByteTransition> finalTransitionsToProcess = new HashSet<>();
        if (shortcutTrans != null) {
            finalTransitionsToProcess.add(shortcutTrans);
        } else {
            for (SingleByteTransition trans : transitionsToProcess) {
                finalTransitionsToProcess.add(getTransition(trans, characters[characters.length - 1]));
            }
        }

        // Check matches for all possible final transitions until we find the pattern we are looking for.
        for (ByteTransition nextTrans : finalTransitionsToProcess) {
            for (ByteMatch match : nextTrans.getMatches()) {
                if (match.getPattern().equals(pattern)) {
                    return match.getNextNameState();
                }
            }
        }
        return null;
    }

    // before we accept the delete range pattern, the input range pattern must exactly match Range ByteMatch
    //  in all path along the range.
    private NameState findRangePattern(Range range) {

        Set<NameState> nextNameStates = new HashSet<>();
        NameState nextNameState = null;

        ByteTransition forkTrans = startState;
        int forkOffset = 0;

        // bypass common prefix of range's bottom and top patterns
        while (range.bottom[forkOffset] == range.top[forkOffset]) {
            forkTrans = findNextByteStateForRangePattern(forkTrans, range.bottom[forkOffset++]);
            if (forkTrans == null) {
                return null;
            }
        }

        // fill in matches in the fork state
        for (byte bb : Range.digitSequence(range.bottom[forkOffset], range.top[forkOffset], false, false)) {
            nextNameState = findMatchForRangePattern(bb, forkTrans, range);
            if (nextNameState == null) {
                return null;
            }
            nextNameStates.add(nextNameState);
        }

        // process all the transitions on the bottom range bytes
        ByteTransition trans = forkTrans;
        int lastMatchOffset = forkOffset;
        for (int offsetB = forkOffset + 1; offsetB < (range.bottom.length - 1); offsetB++) {
            byte b = range.bottom[offsetB];
            if (b < Constants.MAX_DIGIT) {
                while (lastMatchOffset < offsetB) {
                    trans = findNextByteStateForRangePattern(trans, range.bottom[lastMatchOffset++]);
                    if (trans == null) {
                        return null;
                    }
                }
                assert lastMatchOffset == offsetB : "lastMatchOffset == offsetB";
                for (byte bb : Range.digitSequence(b, Constants.MAX_DIGIT, false, true)) {
                    nextNameState = findMatchForRangePattern(bb, trans, range);
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
                trans = findNextByteStateForRangePattern(trans, range.bottom[lastMatchOffset++]);
                if (trans == null) {
                    return null;
                }
            }
            assert lastMatchOffset == (range.bottom.length - 1) : "lastMatchOffset == (range.bottom.length - 1)";
            if (!range.openBottom) {
                nextNameState = findMatchForRangePattern(lastBottom, trans, range);
                if (nextNameState == null) {
                    return null;
                }
                nextNameStates.add(nextNameState);
            }

            // unless the last digit is also at the fork position, fill in the extra matches due to
            //  the strictly-less-than condition (see discussion above)
            if (forkOffset < (range.bottom.length - 1)) {
                for (byte bb : Range.digitSequence(lastBottom, Constants.MAX_DIGIT, false, true)) {
                    nextNameState = findMatchForRangePattern(bb, trans, range);
                    if (nextNameState == null) {
                        return null;
                    }
                    nextNameStates.add(nextNameState);
                }
            }
        }

        // now process transitions along the top range bytes
        trans = forkTrans;
        lastMatchOffset = forkOffset;
        for (int offsetT = forkOffset + 1; offsetT < (range.top.length - 1); offsetT++) {
            byte b = range.top[offsetT];
            if (b > '0') {
                while (lastMatchOffset < offsetT) {
                    trans = findNextByteStateForRangePattern(trans, range.top[lastMatchOffset++]);
                    if (trans == null) {
                        return null;
                    }
                }
                assert lastMatchOffset == offsetT : "lastMatchOffset == offsetT";

                for (byte bb : Range.digitSequence((byte) '0', range.top[offsetT], true, false)) {
                    nextNameState = findMatchForRangePattern(bb, trans, range);
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
                trans = findNextByteStateForRangePattern(trans, range.top[lastMatchOffset++]);
                if (trans == null) {
                    return null;
                }
            }
            assert lastMatchOffset == (range.top.length - 1) : "lastMatchOffset == (range.top.length - 1)";
            if (!range.openTop) {
                nextNameState = findMatchForRangePattern(lastTop, trans, range);
                if (nextNameState == null) {
                    return null;
                }
                nextNameStates.add(nextNameState);
            }
            // unless the last digit is also at the fork position, fill in the extra matches due to
            //  the strictly-less-than condition (see discussion above)
            if (forkOffset < (range.top.length - 1)) {
                for (byte bb : Range.digitSequence((byte) '0', lastTop, true, false)) {
                    nextNameState = findMatchForRangePattern(bb, trans, range);
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
            forkState = findOrMakeNextByteStateForRangePattern(forkState, range.bottom, forkOffset++);
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
            nextNameState = insertMatchForRangePattern(bb, forkState, nextNameState, range);
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
                    state = findOrMakeNextByteStateForRangePattern(state, range.bottom, lastMatchOffset++);
                }

                assert lastMatchOffset == offsetB : "lastMatchOffset == offsetB";
                assert state != null : "state != null";

                // now add transitions for values greater than this non-9 digit
                for (byte bb : Range.digitSequence(b, Constants.MAX_DIGIT, false, true)) {
                    nextNameState = insertMatchForRangePattern(bb, state, nextNameState, range);
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
                state = findOrMakeNextByteStateForRangePattern(state, range.bottom, lastMatchOffset++);
            }
            assert lastMatchOffset == (range.bottom.length - 1) : "lastMatchOffset == (range.bottom.length - 1)";
            assert state != null : "state != null";

            // now we insert matches for possible values of last digit
            if (!range.openBottom) {
                nextNameState = insertMatchForRangePattern(lastBottom, state, nextNameState, range);
            }

            // unless the last digit is also at the fork position, fill in the extra matches due to
            //  the strictly-less-than condition (see discussion above)
            if (forkOffset < (range.bottom.length - 1)) {
                for (byte bb : Range.digitSequence(lastBottom, Constants.MAX_DIGIT, false, true)) {
                    nextNameState = insertMatchForRangePattern(bb, state, nextNameState, range);
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
                    state = findOrMakeNextByteStateForRangePattern(state, range.top, lastMatchOffset++);
                }
                assert lastMatchOffset == offsetT : "lastMatchOffset == offsetT";
                assert state != null : "state != null";

                // now add transitions for values less than this non-0 digit
                for (byte bb : Range.digitSequence((byte) '0', range.top[offsetT], true, false)) {
                    nextNameState = insertMatchForRangePattern(bb, state, nextNameState, range);
                }
            }
        }

        // now for last "top" digit

        // similarly, if the last digit is 0 and we have openTop, there can be no matches so we're done.
        if ((lastTop > '0') || !range.openTop) {

            // add transitions for any 0's we bypassed
            while (lastMatchOffset < range.top.length - 1) {
                state = findOrMakeNextByteStateForRangePattern(state, range.top, lastMatchOffset++);
            }
            assert lastMatchOffset == (range.top.length - 1) : "lastMatchOffset == (range.top.length - 1)";
            assert state != null : "state != null";

            // now we insert matches for possible values of last digit
            if (!range.openTop) {
                nextNameState = insertMatchForRangePattern(lastTop, state, nextNameState, range);
            }

            // unless the last digit is also at the fork position, fill in the extra matches due to
            //  the strictly-less-than condition (see discussion above)
            if (forkOffset < (range.top.length - 1)) {
                for (byte bb : Range.digitSequence((byte) '0', lastTop, true, false)) {
                    nextNameState = insertMatchForRangePattern(bb, state, nextNameState, range);
                }
            }
        }

        return nextNameState;
    }

    // If we meet shortcut trans, that means Range have byte overlapped with shortcut matches,
    // To simplify the logic, we extend the shortcut to normal byte state chain, then decide whether need create
    // new byte state for current call.
    private ByteTransition extendShortcutTransition(final ByteState state, final ByteTransition trans,
                                                    final InputCharacter character, final int currentIndex) {
        if (!trans.isShortcutTrans()) {
            return trans;
        }

        ShortcutTransition shortcut = (ShortcutTransition) trans;
        Patterns shortcutPattern = shortcut.getMatch().getPattern();
        String valueInCurrentPos = ((ValuePatterns) shortcutPattern).pattern();
        final InputCharacter[] charactersInCurrentPos = getParser().parse(shortcutPattern.type(), valueInCurrentPos);
        ByteState firstNewState = null;
        ByteState currentState = state;
        for (int k = currentIndex; k < charactersInCurrentPos.length-1; k++) {
            // we need keep the current state always pointed to last character.
            final ByteState newByteState = new ByteState();
            newByteState.setIndeterminatePrefix(currentState.hasIndeterminatePrefix());
            if (k != currentIndex) {
                putTransitionNextState(currentState, charactersInCurrentPos[k], shortcut, newByteState);
            } else {
                firstNewState = newByteState;
            }
            currentState = newByteState;
        }

        putTransitionMatch(currentState, charactersInCurrentPos[charactersInCurrentPos.length-1],
                EmptyByteTransition.INSTANCE, shortcut.getMatch());
        removeTransition(currentState, charactersInCurrentPos[charactersInCurrentPos.length-1], shortcut);
        putTransitionNextState(state, character, shortcut, firstNewState);
        return getTransition(state, character);
    }

    private ByteState findOrMakeNextByteStateForRangePattern(ByteState state, final byte[] utf8bytes,
                                                             int currentIndex) {
        final InputCharacter character = getParser().parse(utf8bytes[currentIndex]);
        ByteTransition trans = getTransition(state, character);

        ByteState nextState = trans.getNextByteState();
        if (nextState == null) {
            // the next state is null => create a new state, set the transition's next state to the new state
            nextState = new ByteState();
            nextState.setIndeterminatePrefix(state.hasIndeterminatePrefix());
            putTransitionNextState(state, character, trans.expand().iterator().next(), nextState);
        }
        return nextState;
    }

    //  Return the next byte state after transitioning from state using character at currentIndex. Will create it if it
    //  doesn't exist.
    private ByteState findOrMakeNextByteState(ByteState state, ByteState prevState,
                                              final InputCharacter[] characters, int currentIndex, Patterns pattern) {
        final InputCharacter character = characters[currentIndex];
        ByteTransition trans = getTransition(state, character);
        trans = extendShortcutTransition(state, trans, character, currentIndex);

        ByteState nextState = trans.getNextByteState();
        if (nextState == null || nextState.hasIndeterminatePrefix() ||
                (currentIndex == characters.length - 2 && isWildcard(characters[currentIndex + 1]))) {
            // There are three cases for which we cannot re-use the next state and must add a new next state.
            // 1. Next state is null (does not exist).
            // 2. Next state has an indeterminate prefix, so using it would create unintended matches.
            // 3. We're on second last char and last char is wildcard: next state will be composite with match so empty
            //    substring satisfies wildcard. The composite will self-reference and would create unintended matches.
            nextState = new ByteState();
            nextState.setIndeterminatePrefix(state.hasIndeterminatePrefix());
            addTransitionNextState(state, character, characters, currentIndex, prevState, pattern, nextState);
        }

        return nextState;
    }

    private ByteTransition findNextByteStateForRangePattern(ByteTransition trans, final byte b) {
        if (trans == null) {
            return null;
        }

        ByteTransition nextTrans = getTransition(trans, b);
        return nextTrans.getTransitionForNextByteStates();
    }

    // add a match type pattern, i.e. anything but a numeric range, to the byte machine.
    private NameState addMatchPattern(final ValuePatterns pattern) {
        return addMatchValue(pattern, pattern.pattern(), null);
    }

    // We can reach to this function when we have checked the existing characters array from left to right and found we
    // need add the match in the tail character or we find we can shortcut to tail directly without creating new byte
    // transition in the middle. If we met the shortcut transition, we need compare the input value to adjust it
    // accordingly. Please refer to detail comments in ShortcutTransition.java.
    private NameState addEndOfMatch(ByteState state,
                                    ByteState prevState,
                                    final InputCharacter[] characters,
                                    final int charIndex,
                                    final Patterns pattern,
                                    final NameState nameStateCandidate) {
        final int length = characters.length;
        NameState nameState = (nameStateCandidate == null) ? new NameState() : nameStateCandidate;

        if (length == 1 && isWildcard(characters[0])) {
            // Only character is '*'. Make the start state a match so empty input is matched.
            startStateMatch = new ByteMatch(pattern, nameState);
            return nameState;
        }

        ByteTransition trans = getTransition(state, characters[charIndex]);

        // If it is shortcut transition, we need do adjustment first.
        if (!trans.isEmpty() && trans.isShortcutTrans()) {
            ShortcutTransition shortcut = (ShortcutTransition) trans;
            ByteMatch match = shortcut.getMatch();
            // In add/delete rule path, match must not be null and must not have other match
            assert match != null && SHORTCUT_MATCH_TYPES.contains(match.getPattern().type());
            // If it is the same pattern, just return.
            if (pattern.equals(match.getPattern())) {
                return match.getNextNameState();
            }
            // Have asserted current match pattern must be value patterns
            String valueInCurrentPos = ((ValuePatterns) match.getPattern()).pattern();
            final InputCharacter[] charactersInCurrentPos = getParser().parse(match.getPattern().type(),
                    valueInCurrentPos);
            // find the position <m> where the common prefix ends.
            int m = charIndex;
            for (; m < charactersInCurrentPos.length && m < length; m++) {
                if (!charactersInCurrentPos[m].equals(characters[m])) {
                    break;
                }
            }
            // Extend the prefix part in value to byte transitions, to avoid impact on concurrent read we need firstly
            // make the new byte chain ready for using and leave the old transition removing to the last step.
            // firstNewState will be head of new byte chain and, to avoid impact on concurrent match traffic in read
            // path, it need be linked to current state chain after adjustment done.
            ByteState firstNewState = null;
            ByteState currentState = state;
            for (int k = charIndex; k < m; k++) {
                // we need keep the current state always pointed to last character.
                if (k != charactersInCurrentPos.length -1) {
                    final ByteState newByteState = new ByteState();
                    newByteState.setIndeterminatePrefix(currentState.hasIndeterminatePrefix());
                    if (k != charIndex) {
                        putTransitionNextState(currentState, charactersInCurrentPos[k], shortcut, newByteState);
                    } else {
                        firstNewState = newByteState;
                    }
                    currentState = newByteState;
                }
            }

            // If it reached to last character, link the previous read transition in this character, else create
            // shortcut transition. Note: at this time, the previous transition can still keep working.
            boolean isShortcutNeeded = m < charactersInCurrentPos.length - 1;
            int indexToBeChange = isShortcutNeeded ? m : charactersInCurrentPos.length - 1;
            putTransitionMatch(currentState, charactersInCurrentPos[indexToBeChange], isShortcutNeeded ?
                            new ShortcutTransition() : EmptyByteTransition.INSTANCE, match);
            removeTransition(currentState, charactersInCurrentPos[indexToBeChange], shortcut);

            // At last, we link the new created chain to the byte state path, so no uncompleted change can be felt by
            // reading thread. Note: we already confirmed there is only old shortcut transition at charIndex position,
            // now we have move it to new position, so we can directly replace previous transition with new transition
            // pointed to new byte state chain.
            putTransitionNextState(state, characters[charIndex], shortcut, firstNewState);
        }

        // If there is a exact match transition on tail of path, after adjustment target transitions, we start
        // looking at current remaining characters.
        // If this is tail transition, go directly analyse the remaining characters, traverse to tail of chain:
        boolean isEligibleForShortcut = true;
        int j = charIndex;
        for (; j < (length - 1); j++) {
            // We do not want to re-use an existing state for the second last character in the case of a final-character
            // wildcard pattern. In this case, we will have a self-referencing composite match state, which allows zero
            // or many character to satisfy the wildcard. The self-reference would lead to unintended matches for the
            // existing patterns.
            if (j == length - 2 && isWildcard(characters[j + 1])) {
                break;
            }

            trans = getTransition(state, characters[j]);
            if (trans.isEmpty()) {
                break;
            }
            ByteState nextByteState = trans.getNextByteState();
            if (nextByteState != null) {
                // We cannot re-use a state with an indeterminate prefix without creating unintended matches.
                if (nextByteState.hasIndeterminatePrefix()) {
                    // Since there is more path we are unable to traverse, this means we cannot insert shortcut without
                    // potentially ignoring matches further down path.
                    isEligibleForShortcut = false;
                    break;
                }
                prevState = state;
                state = nextByteState;
            } else {
                // trans has match but no next state, we need prepare a next next state to add trans for either last
                // character or shortcut byte.
                final ByteState newByteState = new ByteState();
                newByteState.setIndeterminatePrefix(state.hasIndeterminatePrefix());
                // Stream will not be empty since trans has been verified as non-empty
                SingleByteTransition single = trans.expand().iterator().next();
                putTransitionNextState(state, characters[j], single, newByteState);
                prevState = state;
                state = newByteState;
            }
        }

        // look for a chance to put in a shortcut transition.
        // However, for the moment, we only do this for a JSON string match i.e beginning with ", not literals
        //  like true or false or numbers, because if we do this for numbers produced by
        //  ComparableNumber.generate(), they can be messed up by addRangePattern.
        if (SHORTCUT_MATCH_TYPES.contains(pattern.type())) {
            // For exactly match, if it is last character already, we just put the real transition with match there.
            if (j == length - 1) {
                return insertMatch(characters, j, state, nameState, pattern, prevState);
            } else if (isEligibleForShortcut) {
                // If current character is not last character, create the shortcut transition with the next
                ByteMatch byteMatch = new ByteMatch(pattern, nameState);
                addTransition(state, characters[j], new ShortcutTransition().setMatch(byteMatch));
                addMatchReferences(byteMatch);
                return nameState;
            }
        }

        // For other match type, keep the old logic to extend all characters to byte state path and put the match in the
        // tail state.
        for (; j < (length - 1); j++) {
            ByteState nextByteState = findOrMakeNextByteState(state, prevState, characters, j, pattern);
            prevState = state;
            state = nextByteState;
        }

        return insertMatch(characters, length - 1, state, nameState, pattern, prevState);
    }

    private NameState insertMatchForRangePattern(byte b, ByteState state, NameState nextNameState,
                                                 Patterns pattern) {
        return insertMatch(new InputCharacter[] { getParser().parse(b) }, 0, state, nextNameState, pattern, null);
    }

    private NameState insertMatch(InputCharacter[] characters, int currentIndex, ByteState state,
                                  NameState nextNameState, Patterns pattern, ByteState prevState) {
        InputCharacter character = characters[currentIndex];

        ByteMatch match = findMatch(getTransition(state, character).getMatches(), pattern);
        if (match != null) {
            // There is a match linked to the transition that's the same type, so we just re-use its nextNameState
            return match.getNextNameState();
        }

        // we make a new NameState and hook it up
        NameState nameState = nextNameState == null ? new NameState() : nextNameState;

        match = new ByteMatch(pattern, nameState);
        addMatchReferences(match);

        if (isWildcard(character)) {
            // Rule ends with '*'. Allow no characters up to many characters to match, using a composite match state
            // that references a self-referencing composite match state. Two composite states are used so the count of
            // matching rule prefixes is accurate in MachineComplexityEvaluator. If there is a previous state, then the
            // first composite already exists and we will find it. Otherwise, create a brand new composite.
            SingleByteTransition composite;
            if (prevState != null) {
                composite = getTransitionHavingNextByteState(prevState, state, characters[currentIndex - 1]);
                assert composite != null : "Composite must exist";
            } else {
                composite = new ByteState().setMatch(match);
            }

            ByteState nextState = composite.getNextByteState();
            CompositeByteTransition compositeClone = (CompositeByteTransition) composite.clone();
            nextState.addTransitionForAllBytes(compositeClone);
            nextState.setIndeterminatePrefix(true);
        } else {
            addTransition(state, character, match);

            // If second last char was wildcard, the previous state will also need to transition to the match so that
            // empty substring matches wildcard.
            if (prevState != null && currentIndex == characters.length - 1 &&
                    isWildcard(characters[characters.length - 2])) {
                addTransition(prevState, character, match);
            }
        }

        return nameState;
    }

    /**
     * Gets the first transition originating from origin on character that has a next byte state of toFind.
     *
     * @param origin The origin transition that we will explore transitions from.
     * @param toFind The state that we are looking for from origin's transitions.
     * @param character The character to retrieve transitions from origin.
     * @return Origin's first transition on character that has a next byte state of toFind, or null if not found.
     */
    private static SingleByteTransition getTransitionHavingNextByteState(SingleByteTransition origin,
            SingleByteTransition toFind, InputCharacter character) {
        for (SingleByteTransition eachTrans : getTransition(origin, character).expand()) {
            if (eachTrans.getNextByteState() == toFind) {
                return eachTrans;
            }
        }
        return null;
    }

    private void addMatchReferences(ByteMatch match) {
        Patterns pattern = match.getPattern();
        switch (pattern.type()) {
        case EXACT:
        case PREFIX:
        case EXISTS:
        case EQUALS_IGNORE_CASE:
        case WILDCARD:
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
            anythingButs.add(match.getNextNameState());
            if (((AnythingBut) pattern).isNumeric()) {
                hasNumeric.incrementAndGet();
            }
            break;
        case ANYTHING_BUT_PREFIX:
            anythingButs.add(match.getNextNameState());
            break;
        default:
            throw new AssertionError("Not implemented yet");
        }
    }

    private NameState findMatchForRangePattern(byte b, ByteTransition trans, Patterns pattern) {
        if (trans == null) {
            return null;
        }

        ByteTransition nextTrans = getTransition(trans, b);

        ByteMatch match = findMatch(nextTrans.getMatches(), pattern);
        return match == null ? null : match.getNextNameState();
    }

    /**
     * Deletes any matches that exist on the given pattern that are accessed from ByteTransition transition using
     * character.
     *
     * @param character The character used to transition.
     * @param transition The transition we are transitioning from to look for matches.
     * @param pattern The pattern whose match we are attempting to delete.
     */
    private void deleteMatches(InputCharacter character, ByteTransition transition, Patterns pattern) {
        if (transition == null) {
            return;
        }

        for (SingleByteTransition single : transition.expand()) {
            ByteTransition trans = getTransition(single, character);
            for (SingleByteTransition eachTrans : trans.expand()) {
                deleteMatch(character, single.getNextByteState(), pattern, eachTrans);
            }
        }
    }

    /**
     * Deletes a match, if it exists, on the given pattern that is accessed from ByteState state using character to
     * transition over SingleByteTransition trans.
     *
     * @param character The character used to transition.
     * @param state The state we are transitioning from.
     * @param pattern The pattern whose match we are attempting to delete.
     * @param trans The transition to the match.
     * @return The updated transition (may or may not be same object as trans) if the match was found. Null otherwise.
     */
    private SingleByteTransition deleteMatch(InputCharacter character, ByteState state, Patterns pattern,
                                             SingleByteTransition trans) {
        if (state == null) {
            return null;
        }

        ByteMatch match = trans.getMatch();

        if (match != null && match.getPattern().equals(pattern)) {
            updateMatchReferences(match);
            return putTransitionMatch(state, character, trans, null);
        }

        return null;
    }

    private void updateMatchReferences(ByteMatch match) {
        Patterns pattern = match.getPattern();
        switch (pattern.type()) {
        case EXACT:
        case PREFIX:
        case EXISTS:
        case EQUALS_IGNORE_CASE:
        case WILDCARD:
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
            anythingButs.remove(match.getNextNameState());
            if (((AnythingBut) pattern).isNumeric()) {
                hasNumeric.decrementAndGet();
            }
            break;
        case ANYTHING_BUT_PREFIX:
            anythingButs.remove(match.getNextNameState());
            break;
        default:
            throw new AssertionError("Not implemented yet");
        }
    }

    private static ByteTransition getTransition(ByteTransition trans, byte b) {
        ByteTransition nextTrans = trans.getTransition(b);
        return nextTrans == null ? EmptyByteTransition.INSTANCE : nextTrans;
    }

    private static ByteTransition getTransition(SingleByteTransition trans, InputCharacter character) {
        switch (character.getType()) {
            case WILDCARD:
                return trans.getTransitionForAllBytes();
            case MULTI_BYTE_SET:
                return getTransitionForMultiBytes(trans, InputMultiByteSet.cast(character).getMultiBytes());
            case BYTE:
                return getTransition(trans, InputByte.cast(character).getByte());
            default:
                throw new AssertionError("Not implemented yet");
        }
    }

    private static ByteTransition getTransitionForMultiBytes(SingleByteTransition trans, Set<MultiByte> multiBytes) {
        Set<SingleByteTransition> candidates = new HashSet<>();

        for (MultiByte multiByte : multiBytes) {
            ByteTransition currentTransition = trans;
            for (byte b : multiByte.getBytes()) {
                ByteTransition nextTransition = currentTransition.getTransition(b);
                if (nextTransition == null) {
                    return EmptyByteTransition.INSTANCE;
                }
                currentTransition = nextTransition;
            }

            if (candidates.isEmpty()) {
                currentTransition.expand().forEach(t -> candidates.add(t));
            } else {
                Set<SingleByteTransition> retainThese = new HashSet<>();
                currentTransition.expand().forEach(t -> retainThese.add(t));
                candidates.retainAll(retainThese);
                if (candidates.isEmpty()) {
                    return EmptyByteTransition.INSTANCE;
                }
            }
        }

        return coalesce(candidates);
    }

    private static void addTransitionNextState(ByteState state, InputCharacter character, InputCharacter[] characters,
                                               int currentIndex, ByteState prevState, Patterns pattern,
                                               ByteState nextState) {
        if (isWildcard(character)) {
            addTransitionNextStateForWildcard(state, nextState);
        } else {
            // If the last character is the wildcard character, and we're on the second last character, set a match on
            // the transition (turning it into a composite) before adding transitions from state and prevState.
            SingleByteTransition single = nextState;
            if (isWildcard(characters[characters.length - 1]) && currentIndex == characters.length - 2) {
                ByteMatch match = new ByteMatch(pattern, new NameState());
                single = nextState.setMatch(match);
                nextState.setIndeterminatePrefix(true);
            } else if (isMultiByteSet(character)) {
                nextState.setIndeterminatePrefix(true);
            }

            addTransition(state, character, single);

            // If there was a previous state and it transitioned using the wildcard character, we need to add a
            // transition from the previous state to allow wildcard match on empty substring.
            if (prevState != null && isWildcard(characters[currentIndex - 1])) {
                addTransition(prevState, character, single);
            }
        }
    }

    /**
     * Assuming a current wildcard character, a next character of byte b, a current state of A, a next state of B, and a
     * next next state of C, this function produces the following state machine:
     *
     *          ____
     *     *   | *  |
     *  A ---> B <--
     *
     * When processing the next byte (b) of the current rule, which transitions to the next next state of C, the
     * addTransitionNextState function will be invoked to transform the state machine to:
     *
     *           ____                         ____
     *      *   | *  |                  *    | *  |
     *   A ---> B <--               A -----> B <--
     *        b |         =====>    | b    b |
     *      C <--                    --> C <--
     *
     * A more naive implementation would skip B altogether, like:
     *
     *   ____
     *  | * |  b
     *  --> A ---> C
     *
     * But the naive implementation does not work in the case of multiple rules in one machine. Since the current state,
     * A, may already have transitions for other rules, adding a self-referential * transition to A can lead to
     * unintended matches using those existing rules. We must create a new state (B) that the wildcard byte transitions
     * to so that we do not affect existing rules in the machine.
     *
     * @param state The current state.
     * @param nextState The next state.
     */
    private static void addTransitionNextStateForWildcard(ByteState state,
                                                          ByteState nextState) {
        // Add self-referential * transition to next state (B).
        nextState.addTransitionForAllBytes(nextState);

        nextState.setIndeterminatePrefix(true);

        // Add * transition from current state (A) to next state (B).
        state.addTransitionForAllBytes(nextState);
    }

    private static void putTransitionNextState(ByteState state, InputCharacter character,
                                               SingleByteTransition transition, ByteState nextState) {
        // In order to avoid change being felt by the concurrent query thread in the middle of change, we clone the
        // trans firstly and will not update state store until the changes have completely applied in the new trans.
        ByteTransition trans = transition.clone();
        SingleByteTransition single = trans.setNextByteState(nextState);
        if (single != null && !single.isEmpty() && single != transition) {
            addTransition(state, character, single);
        }
        removeTransition(state, character, transition);
    }

    /**
     * Returns the cloned transition with the match set.
     */
    private static SingleByteTransition putTransitionMatch(ByteState state, InputCharacter character,
                                                           SingleByteTransition transition, ByteMatch match) {
        // In order to avoid change being felt by the concurrent query thread in the middle of change, we clone the
        // trans firstly and will not update state store until the changes have completely applied in the new trans.
        SingleByteTransition trans = (SingleByteTransition) transition.clone();
        SingleByteTransition single = trans.setMatch(match);
        if (single != null && !single.isEmpty() && single != transition) {
            addTransition(state, character, single);
        }
        removeTransition(state, character, transition);
        return single;
    }

    private static void removeTransition(ByteState state, InputCharacter character, SingleByteTransition transition) {
        if (isWildcard(character)) {
            state.removeTransitionForAllBytes(transition);
        } else if (isMultiByteSet(character)) {
            removeTransitionForMultiByteSet(state, InputMultiByteSet.cast(character), transition);
        } else {
            state.removeTransition(InputByte.cast(character).getByte(), transition);
        }
    }

    private static void removeTransitionForMultiByteSet(ByteState state, InputMultiByteSet multiByteSet,
                                                        SingleByteTransition transition) {
        for (MultiByte multiByte : multiByteSet.getMultiBytes()) {
            removeTransitionForBytes(state, multiByte.getBytes(), 0, transition);
        }
    }

    private static void removeTransitionForBytes(ByteState state, byte[] bytes, int index,
                                                 SingleByteTransition transition) {
        if (index == bytes.length - 1) {
            state.removeTransition(bytes[index], transition);
        } else {
            for (SingleByteTransition single : getTransition(state, bytes[index]).expand()) {
                ByteState nextState = single.getNextByteState();
                if (nextState != null) {
                    removeTransitionForBytes(nextState, bytes, index + 1, transition);
                    if (nextState.hasNoTransitions() || nextState.hasOnlySelfReferentialTransition()) {
                        state.removeTransition(bytes[index], single);
                    }
                }
            }
        }
    }

    private static void addTransition(ByteState state, InputCharacter character, SingleByteTransition transition) {
        if (isWildcard(character)) {
            state.putTransitionForAllBytes(transition);
        } else if (isMultiByteSet(character)) {
            addTransitionForMultiByteSet(state, InputMultiByteSet.cast(character), transition);
        } else {
            state.addTransition(InputByte.cast(character).getByte(), transition);
        }
    }

    private static void addTransitionForMultiByteSet(ByteState state, InputMultiByteSet multiByteSet,
                                                     SingleByteTransition transition) {
        for (MultiByte multiByte : multiByteSet.getMultiBytes()) {
            byte[] bytes = multiByte.getBytes();
            ByteState currentState = state;
            for (int i = 0; i < bytes.length - 1; i++) {
                ByteState nextState = new ByteState();
                nextState.setIndeterminatePrefix(true);
                currentState.addTransition(bytes[i], nextState);
                currentState = nextState;
            }
            currentState.addTransition(bytes[bytes.length - 1], transition);
        }
    }

    private static ByteMatch findMatch(Set<ByteMatch> matches, Patterns pattern) {
        for (ByteMatch match : matches) {
            if (match.getPattern().equals(pattern)) {
                return match;
            }
        }
        return null;
    }

    private static boolean isByte(InputCharacter character) {
        return character.getType() == InputCharacterType.BYTE;
    }

    private static boolean isWildcard(InputCharacter character) {
        return character.getType() == InputCharacterType.WILDCARD;
    }

    private static boolean isMultiByteSet(InputCharacter character) {
        return character.getType() == InputCharacterType.MULTI_BYTE_SET;
    }

    public int evaluateComplexity(MachineComplexityEvaluator evaluator) {
        return (startStateMatch != null ? 1 : 0) + evaluator.evaluate(startState);
    }

    public static final class EmptyByteTransition extends SingleByteTransition {

        static final EmptyByteTransition INSTANCE = new EmptyByteTransition();

        @Override
        public ByteState getNextByteState() {
            return null;
        }

        @Override
        public SingleByteTransition setNextByteState(ByteState nextState) {
            return nextState;
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
            return Collections.emptySet();
        }

        @Override
        public ByteMatch getMatch() {
            return null;
        }

        @Override
        public Set<ShortcutTransition> getShortcuts() {
            return Collections.emptySet();
        }

        @Override
        public SingleByteTransition setMatch(ByteMatch match) {
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
