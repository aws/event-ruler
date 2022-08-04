package software.amazon.event.ruler;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;

import static software.amazon.event.ruler.CompoundByteTransition.coalesce;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class CompoundByteTransitionTest {

    @Test
    public void testCoalesceEmptyList() {
        assertNull(coalesce(new HashSet<>(Arrays.asList())));
    }

    @Test
    public void testCoalesceOneElement() {
        ByteState state = new ByteState();
        assertEquals(state, coalesce(new HashSet<>(Arrays.asList(state))));
    }

    @Test
    public void testCoalesceManyElements() {
        ByteState state1 = new ByteState();
        ByteState state2 = new ByteState();
        ByteTransition result = coalesce(new HashSet<>(Arrays.asList(state1, state2)));
        assertTrue(result instanceof CompoundByteTransition);
        assertEquals(new HashSet<>(Arrays.asList(state1, state2)), result.expand());
    }

    @Test
    public void testExpandDeterminatePrefixComesBeforeIndeterminatePrefix() {
        ByteState state1 = new ByteState();
        state1.setIndeterminatePrefix(true);
        ByteState state2 = new ByteState();
        state2.setIndeterminatePrefix(false);
        ByteTransition result = coalesce(new HashSet<>(Arrays.asList(state1, state2)));
        assertTrue(result instanceof CompoundByteTransition);
        assertEquals(new HashSet<>(Arrays.asList(state2, state1)), result.expand());
    }

    @Test
    public void testExpandCompositeNextStateDeterminatePrefixComesBeforeCompositeNextStateIndeterminatePrefix() {
        ByteState state1 = new ByteState();
        state1.setIndeterminatePrefix(true);
        CompositeByteTransition composite1 = new CompositeByteTransition(state1, null);
        ByteState state2 = new ByteState();
        state2.setIndeterminatePrefix(false);
        CompositeByteTransition composite2 = new CompositeByteTransition(state2, null);
        ByteTransition result = coalesce(new HashSet<>(Arrays.asList(composite1, composite2)));
        assertTrue(result instanceof CompoundByteTransition);
        assertEquals(new HashSet<>(Arrays.asList(composite2, composite1)), result.expand());
    }

    @Test
    public void testExpandDeterminatePrefixComesBeforeNullNextByteState() {
        CompositeByteTransition composite1 = new CompositeByteTransition(null, null);
        ByteState state2 = new ByteState();
        state2.setIndeterminatePrefix(false);
        ByteTransition result = coalesce(new HashSet<>(Arrays.asList(composite1, state2)));
        assertTrue(result instanceof CompoundByteTransition);
        assertEquals(new HashSet<>(Arrays.asList(state2, composite1)), result.expand());
    }

    @Test
    public void testGetNextByteStateReturnsNullWhenNextByteStatesAreAllNull() {
        CompositeByteTransition composite1 = new CompositeByteTransition(null, null);
        CompositeByteTransition composite2 = new CompositeByteTransition(null, null);
        ByteTransition result = coalesce(new HashSet<>(Arrays.asList(composite1, composite2)));
        assertTrue(result instanceof CompoundByteTransition);
        assertNull(result.getNextByteState());
    }

    @Test
    public void testGetNextByteStateReturnsTransitionWithNonNullNextByteState() {
        CompositeByteTransition composite1 = new CompositeByteTransition(null, null);
        ByteState state2 = new ByteState();
        state2.setIndeterminatePrefix(true);
        CompositeByteTransition composite2 = new CompositeByteTransition(state2, null);
        ByteTransition result = coalesce(new HashSet<>(Arrays.asList(composite1, composite2)));
        assertTrue(result instanceof CompoundByteTransition);
        assertEquals(state2, result.getNextByteState());
    }

    @Test
    public void testGetNextByteStateReturnsTransitionWithNextByteStateHavingDeterminatePrefix() {
        ByteState state1 = new ByteState();
        state1.setIndeterminatePrefix(true);
        CompositeByteTransition composite1 = new CompositeByteTransition(state1, null);
        ByteState state2 = new ByteState();
        state2.setIndeterminatePrefix(false);
        CompositeByteTransition composite2 = new CompositeByteTransition(state2, null);
        ByteTransition result = coalesce(new HashSet<>(Arrays.asList(composite1, composite2)));
        assertTrue(result instanceof CompoundByteTransition);
        assertEquals(state2, result.getNextByteState());
    }

    @Test
    public void testSetNextByteState() {
        ByteState state = new ByteState();
        CompoundByteTransition compound = coalesce(new HashSet<>(Arrays.asList(new ByteState(), new ByteState())));
        assertSame(state, compound.setNextByteState(state));
    }

    @Test
    public void testGetMatches() {
        ByteMatch match1 = new ByteMatch(Patterns.exactMatch("a"), new NameState());
        ByteMatch match2 = new ByteMatch(Patterns.exactMatch("b"), new NameState());
        ByteMatch match3 = new ByteMatch(Patterns.exactMatch("c"), new NameState());
        ByteMatch match4 = new ByteMatch(Patterns.exactMatch("d"), new NameState());
        ByteMatch match5 = new ByteMatch(Patterns.exactMatch("d"), new NameState());

        // The shortcut gets match3. Expect this one to be excluded from CompoundByteTransition's getMatches.
        ShortcutTransition shortcut = new ShortcutTransition();
        shortcut.setMatch(match3);

        // ByteState will accomplish nothing.
        ByteState state = new ByteState();

        // The composite gets match4.
        CompositeByteTransition composite = new CompositeByteTransition(null, match4);

        ByteTransition result = coalesce(new HashSet<>(Arrays.asList(
                match1, match2, shortcut, state, composite, match5)));
        assertTrue(result instanceof CompoundByteTransition);
        assertEquals(new HashSet<>(Arrays.asList(match1, match2, match4, match5)), result.getMatches());
    }

    @Test
    public void testGetShortcuts() {
        ByteState state = new ByteState();
        ShortcutTransition shortcut = new ShortcutTransition();
        assertEquals(new HashSet<>(Arrays.asList(shortcut)),
                coalesce(new HashSet<>(Arrays.asList(state, shortcut))).getShortcuts());
    }

    @Test
    public void testGetTransition() {
        SingleByteTransition transition1 = new ByteState();
        SingleByteTransition transition2 = new ByteState();
        SingleByteTransition transition3 = new ByteState();
        SingleByteTransition transition4 = new ByteState();

        ByteState state1 = new ByteState();
        state1.addTransition((byte) 'a', transition1);
        state1.addTransition((byte) 'b', transition2);
        ByteState state2 = new ByteState();
        state2.addTransition((byte) 'b', transition3);
        state2.addTransition((byte) 'b', transition4);

        ByteTransition compound = coalesce(new HashSet<>(Arrays.asList(state1, state2)));
        assertTrue(compound instanceof CompoundByteTransition);

        assertNull(compound.getTransition((byte) 'c'));
        assertEquals(transition1, compound.getTransition((byte) 'a'));

        ByteTransition result = compound.getTransition((byte) 'b');
        assertTrue(result instanceof CompoundByteTransition);
        assertEquals(new HashSet<>(Arrays.asList(transition2, transition3, transition4)), result.expand());
    }

    @Test
    public void testGetTransitionForNextByteStates() {
        SingleByteTransition state = new ByteState();
        ByteState compositeState = new ByteState();
        SingleByteTransition composite = new CompositeByteTransition(compositeState,
                new ByteMatch(Patterns.exactMatch("a"), new NameState()));
        assertEquals(coalesce(new HashSet<>(Arrays.asList(state, compositeState))),
                coalesce(new HashSet<>(Arrays.asList(state, composite))).getTransitionForNextByteStates());
    }

    @Test
    public void testGetTransitions() {
        SingleByteTransition state1 = new ByteState();
        SingleByteTransition state2 = new ByteState();
        SingleByteTransition state3 = new ByteState();
        SingleByteTransition state4 = new ByteState();

        ByteState state5 = new ByteState();
        state5.addTransition((byte) 'a', state1);
        state5.addTransition((byte) 'b', state2);

        ByteState state6 = new ByteState();
        state6.addTransition((byte) 'a', state3);

        ByteState state7 = new ByteState();
        state7.addTransition((byte) 'z', state4);

        assertEquals(new HashSet<>(Arrays.<ByteTransition>asList(coalesce(new HashSet<>(Arrays.asList(state1, state3))),
                                                                                                      state2,
                                                                                                      state4
                )), coalesce(new HashSet<>(Arrays.asList(state5, state6, state7))).getTransitions());
    }

    @Test
    public void testEquals() {
        SingleByteTransition state1 = new ByteState();
        SingleByteTransition state2 = new ByteState();
        SingleByteTransition state3 = new ByteState();
        assertTrue(coalesce(new HashSet<>(Arrays.asList(state1, state2))).equals(
                coalesce(new HashSet<>(Arrays.asList(state1, state2)))
        ));
        assertFalse(coalesce(new HashSet<>(Arrays.asList(state1, state2))).equals(
                coalesce(new HashSet<>(Arrays.asList(state1, state3)))
        ));
        assertFalse(coalesce(new HashSet<>(Arrays.asList(state1, state2))).equals(
                coalesce(new HashSet<>(Arrays.asList(state1, state2, state3)))
        ));
        assertFalse(coalesce(new HashSet<>(Arrays.asList(state1, state2))).equals(
                coalesce(new HashSet<>(Arrays.asList(state1)))
        ));
        assertFalse(coalesce(new HashSet<>(Arrays.asList(state1, state2))).equals(new Object()));
    }

    @Test
    public void testHashCode() {
        SingleByteTransition state1 = new ByteState();
        SingleByteTransition state2 = new ByteState();
        assertEquals(coalesce(new HashSet<>(Arrays.asList(state1, state2))).hashCode(),
                coalesce(new HashSet<>(Arrays.asList(state1, state2))).hashCode()
        );
        assertNotEquals(coalesce(new HashSet<>(Arrays.asList(state1, state2))).hashCode(),
                coalesce(new HashSet<>(Arrays.asList(state1))).hashCode()
        );
    }
}
