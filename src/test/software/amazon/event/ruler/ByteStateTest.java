package software.amazon.event.ruler;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static software.amazon.event.ruler.CompoundByteTransition.coalesce;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ByteStateTest {

    private ByteState state;

    @Before
    public void setUp() {
        state = new ByteState();
    }

    @Test
    public void getNextByteStateShouldReturnThisState() {
        assertSame(state, state.getNextByteState());
    }

    @Test
    public void setNextByteStateShouldReturnNullWhenGivenNextStateIsNull() {
        assertNull(state.setNextByteState(null));
    }

    @Test
    public void setNextByteStateShouldReturnGivenNextStateWhenGivenNextStateIsNotNull() {
        ByteState nextState = new ByteState();

        ByteTransition transition = state.setNextByteState(nextState);

        assertSame(nextState, transition);
    }

    @Test
    public void getMatchShouldReturnNull() {
        assertNull(state.getMatch());
    }

    @Test
    public void getMatchesShouldReturnEmptySet() {
        assertEquals(Collections.emptySet(), state.getMatches());
    }

    @Test
    public void setMatchShouldReturnThisStateWhenGivenMatchIsNull() {
        ByteTransition transition = state.setMatch(null);
        assertSame(state, transition);
    }

    @Test
    public void setMatchShouldReturnNewCompositeTransitionWhenGivenMatchIsNotNull() {
        ByteMatch match = new ByteMatch(Patterns.exactMatch("xyz"), new NameState());

        ByteTransition transition = state.setMatch(match);

        assertTrue(transition instanceof CompositeByteTransition);
        assertSame(state, transition.getNextByteState());
        assertEquals(new HashSet<>(Arrays.asList(match)), transition.getMatches());
    }

    @Test
    public void hasNoTransitionsShouldReturnTrueWhenThisStateHasNoTransitions() {
        boolean hasNoTransitions = state.hasNoTransitions();
        assertTrue(hasNoTransitions);
    }

    @Test
    public void hasNoTransitionsShouldReturnFalseWhenThisStateHasTransitions() {
        state.addTransition((byte) 'a', new ByteState());

        boolean hasNoTransitions = state.hasNoTransitions();

        assertFalse(hasNoTransitions);
    }

    @Test
    public void getTransitionShouldReturnNullWhenThisStateHasNoTransitions() {
        ByteTransition transition = state.getTransition((byte) 'a');
        assertNull(transition);
    }

    @Test
    public void getTransitionShouldReturnNullWhenMappingDoesNotExistAndThisStateHasOneTransition() {
        state.addTransition((byte) 'a', new ByteState());

        ByteTransition transition = state.getTransition((byte) 'b');

        assertNull(transition);
    }

    @Test
    public void getTransitionShouldReturnTransitionWhenMappingExistsAndThisStateHasOneTransition() {
        byte b = 'a';
        SingleByteTransition transition = new ByteState();

        this.state.addTransition(b, transition);

        ByteTransition actualTransition = this.state.getTransition(b);

        assertSame(transition, actualTransition);
    }

    @Test
    public void getTransitionShouldReturnTransitionWhenMappingExistsAndThisStateHasTwoTransition() {
        byte b1 = 'a';
        byte b2 = 'b';
        SingleByteTransition transition1 = new ByteState();
        SingleByteTransition transition2 = new ByteState();

        state.addTransition(b1, transition1);
        state.addTransition(b2, transition2);

        ByteTransition actualTransition = state.getTransition(b1);

        assertSame(transition1, actualTransition);
    }

    @Test
    public void addTransitionShouldCreateMappingWhenMappingDoesNotExistAndThisStateHasNoTransitions() {
        byte b = 'a';
        SingleByteTransition transition = new ByteState();

        state.addTransition(b, transition);

        assertSame(transition, state.getTransition(b));
    }

    @Test
    public void addTransitionShouldProduceCompoundByteStateWhenMappingExistsAndThisStateHasOneTransition() {
        byte b = 'a';
        SingleByteTransition transition1 = new ByteState();
        SingleByteTransition transition2 = new ByteState();

        state.addTransition(b, transition1);

        state.addTransition(b, transition2);

        ByteTransition resultantTransition = state.getTransition(b);
        assertTrue(resultantTransition instanceof CompoundByteTransition);
        CompoundByteTransition compoundByteTransition = (CompoundByteTransition) resultantTransition;
        assertEquals(new HashSet<>(Arrays.asList(transition1, transition2)), compoundByteTransition.expand());
    }

    @Test
    public void addTransitionShouldCreateMappingWhenMappingDoesNotExistAndThisStateHasOneTransition() {
        byte b1 = 'a';
        byte b2 = 'b';
        SingleByteTransition transition1 = new ByteState();
        SingleByteTransition transition2 = new ByteState();

        state.addTransition(b1, transition1);

        state.addTransition(b2, transition2);

        assertSame(transition1, state.getTransition(b1));
        assertSame(transition2, state.getTransition(b2));
    }

    @Test
    public void addTransitionShouldCreateMappingWhenMappingDoesNotExistAndThisStateHasTwoTransitions() {
        byte b1 = 'a';
        byte b2 = 'b';
        byte b3 = 'c';
        SingleByteTransition transition1 = new ByteState();
        SingleByteTransition transition2 = new ByteState();
        SingleByteTransition transition3 = new ByteState();

        state.addTransition(b1, transition1);
        state.addTransition(b2, transition2);

        state.addTransition(b3, transition3);

        assertSame(transition1, state.getTransition(b1));
        assertSame(transition2, state.getTransition(b2));
        assertSame(transition3, state.getTransition(b3));
    }

    @Test
    public void removeTransitionShouldDoNothingWhenMappingDoesNotExistAndThisStateHasNoTransitions() {
        byte b = 'a';

        state.removeTransition(b, new ByteState());

        assertNull(state.getTransition(b));
    }

    @Test
    public void removeTransitionShouldDoNothingWhenMappingDoesNotExistAndThisStateHasOneTransition() {
        byte b = 'a';
        SingleByteTransition transition = new ByteState();

        state.addTransition(b, transition);

        state.removeTransition((byte) 'b', transition);

        assertSame(transition, state.getTransition(b));
    }

    @Test
    public void removeTransitionShouldRemoveMappingWhenMappingExistsAndThisStateHasOneTransition() {
        byte b = 'a';
        SingleByteTransition transition = new ByteState();

        state.addTransition(b, transition);

        state.removeTransition(b, transition);

        assertNull(state.getTransition(b));
    }

    @Test
    public void removeTransitionShouldDoNothingWhenMappingDoesNotExistAndThisStateHasTwoTransitions() {
        byte b1 = 'a';
        byte b2 = 'b';
        SingleByteTransition transition1 = new ByteState();
        SingleByteTransition transition2 = new ByteState();

        state.addTransition(b1, transition1);
        state.addTransition(b2, transition2);

        state.removeTransition((byte) 'c', transition1);

        assertSame(transition1, state.getTransition(b1));
        assertSame(transition2, state.getTransition(b2));
    }

    @Test
    public void removeTransitionShouldRemoveMappingWhenMappingExistsAndThisStateHasTwoTransitions() {
        byte b1 = 'a';
        byte b2 = 'b';
        SingleByteTransition transition1 = new ByteState();
        SingleByteTransition transition2 = new ByteState();

        state.addTransition(b1, transition1);
        state.addTransition(b2, transition2);

        state.removeTransition(b1, transition1);

        assertNull(state.getTransition(b1));
        assertSame(transition2, state.getTransition(b2));
    }

    @Test
    public void removeTransitionShouldRemoveMappingWhenMappingExistsAndThisStateHasThreeTransitions() {
        byte b1 = 'a';
        byte b2 = 'b';
        byte b3 = 'c';
        SingleByteTransition transition1 = new ByteState();
        SingleByteTransition transition2 = new ByteState();
        SingleByteTransition transition3 = new ByteState();

        state.addTransition(b1, transition1);
        state.addTransition(b2, transition2);
        state.addTransition(b3, transition3);

        state.removeTransition(b1, transition1);

        assertNull(state.getTransition(b1));
        assertSame(transition2, state.getTransition(b2));
        assertSame(transition3, state.getTransition(b3));
    }

    @Test
    public void getShortcutsShouldReturnEmptySet() {
        assertEquals(Collections.emptySet(), state.getShortcuts());
    }

    @Test
    public void getTransitionForAllBytesShouldReturnExpectedTransition() {
        SingleByteTransition trans1 = new ByteState();
        SingleByteTransition trans2 = new ByteState();
        SingleByteTransition trans3 = new ByteState();
        SingleByteTransition trans4 = new ByteState();

        state.addTransitionForAllBytes(trans1);
        state.addTransitionForAllBytes(trans2);
        state.addTransitionForAllBytes(trans3);
        state.addTransition((byte) 'a', trans4);
        state.removeTransition((byte) 'c', trans2);
        assertEquals(coalesce(new HashSet<>(Arrays.asList(trans1, trans3))), state.getTransitionForAllBytes());
    }

    @Test
    public void getTransitionsShouldReturnExpectedTransitions() {
        SingleByteTransition trans1 = new ByteState();
        SingleByteTransition trans2 = new ByteState();
        SingleByteTransition trans3 = new ByteState();
        SingleByteTransition trans4 = new ByteState();

        state.addTransitionForAllBytes(trans1);
        state.addTransitionForAllBytes(trans2);
        state.addTransitionForAllBytes(trans3);
        state.addTransition((byte) 'a', trans4);
        state.removeTransition((byte) 'c', trans2);
        assertEquals(new HashSet<>(Arrays.asList(coalesce(new HashSet<>(Arrays.asList(trans1, trans3))),
                                                 coalesce(new HashSet<>(Arrays.asList(trans1, trans2, trans3))),
                                                 coalesce(new HashSet<>(Arrays.asList(trans1, trans2, trans3, trans4)))
                )), state.getTransitions());
    }

    @Test
    public void testGetCeilingsShouldReturnExpectedCeilings() {
        SingleByteTransition trans1 = new ByteState();
        SingleByteTransition trans2 = new ByteState();

        state.addTransition((byte) 'a', trans1);
        state.addTransition((byte) 'c', trans2);
        assertEquals(new HashSet<>(Arrays.asList(97, 98, 99, 100, 256)), state.getCeilings());
    }

    @Test
    public void hasIndeterminatePrefixShouldReturnExpectedBoolean() {
        assertFalse(state.hasIndeterminatePrefix());
        state.setIndeterminatePrefix(true);
        assertTrue(state.hasIndeterminatePrefix());
    }

    @Test
    public void hasOnlySelfReferentialTransitionShouldReturnExpectedBoolean() {
        assertFalse(state.hasOnlySelfReferentialTransition());
        state.putTransition((byte) 'a', state);
        assertTrue(state.hasOnlySelfReferentialTransition());
        state.putTransition((byte) 'b', new ByteState());
        assertFalse(state.hasOnlySelfReferentialTransition());
    }
}
