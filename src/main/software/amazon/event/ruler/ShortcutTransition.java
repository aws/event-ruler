package software.amazon.event.ruler;

import java.util.Collections;
import java.util.Set;

// Shortcut transition is designed mainly for exactly match by its memory consuming because the exactly match is always
// in the last byte of value, while it will take a lots of memory if we build a traverse path byte by byte.
// however, if we add another value like "ab"->match1. so we have two transitions separately, but, in term of memory
// cost, the byte "a" and "b" are duplicated in those two transitions and we want to reuse the same path as much as possible.
// The idea here to share the byte which is common among the transitions, when create the exactly match, if we know, by
// along the byte state path, the remaining path doesn't exist, instead of creating a entirely byte path and put the match in last byte,
// we just use the first next byte which is not existing path, and use it to create the shortcut transition to link to the match directly.
// For example, if we have value "abcdfg", current way is "a"->"b"->"c"->"d"->"e"->"f"->"g"->match0
// while, with the shortcut transition, it is just "a"-> match0("abcdef").
// the shortcut transition to proxy to a match directly and will do the adjustment whenever adding new transitions.
// The definition of shortcut transition is a transition which transits to a exact match only by extending a next byte
// which doesn't exist in current byte state path. e.g. if we add value "abcdefg", "a" is the first byte in current path
// which isn't existing yet, so, we put the shortcut transition on state of "a" and it points to match "abcdefg" directly.
//    +---+
//    | a +-->match0-("abcdefg")
//    +-+-+
// then we add "ab" and doing the adjustment, it ends up with the effects like below:
// +---+   +---+   +---+
// | a +-->+ b +-->+ c +-->null
// +---+   +-+-+   +-+-+
//           |       |
//           v       v
//   match1-"ab"     match0-"abcdefg"
// we take "ab" go through current state path which triggers the byteMachine extends to "b" as "a"->"b" and put the real transition
// with match("ab") on state of "b", then do re-evaluate the match0("abcdefg"), at this time, we see, the first byte which
// is not existing is "c", so we create a new shortcut transition of match0("abcdefg") and put it to "c", at the last step,
// we remove the shortcut transition from "a", we leave it as the last step is to avoid any impact to concurrent read request.
// as you see, during the whole change, we didn't change any existing data from "a" to "c", every change is newly built stuff,
// we update existing path only once new proposal is ready.
// Note:
// 1) the adjustment will ensure the shortcut transition will be always at the tail position in the traverse path.
// 2) the byte which has the shortcut transition must be always the next byte in the match which doesn't exist in existing
//    byte path.
// 3) shortcut Transition will only work for exactly match and should only have one match in one shortcut transition.
public class ShortcutTransition extends SingleByteTransition {

    private ByteMatch match;

    @Override
    ByteState getNextByteState() {
        return null;
    }

    @Override
    SingleByteTransition setNextByteState(ByteState nextState) {
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
        return Collections.EMPTY_SET;
    }

    @Override
    ByteMatch getMatch() {
        return match;
    }

    @Override
    SingleByteTransition setMatch(ByteMatch match) {
        this.match = match;
        return this;
    }

    @Override
    public Iterable<ShortcutTransition> getShortcuts() {
        return this;
    }

    @Override
    boolean isShortcutTrans() {
        return true ;
    }
}

