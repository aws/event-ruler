package software.amazon.event.ruler;

import org.junit.Before;
import org.junit.Test;

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
        ByteState nextState = state.getNextByteState();
        assertSame(state, nextState);
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
        ByteMatch match = state.getMatch();
        assertNull(match);
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
        assertSame(match, transition.getMatch());
    }

    @Test
    public void hasNoTransitionsShouldReturnTrueWhenThisStateHasNoTransitions() {
        boolean hasNoTransitions = state.hasNoTransitions();
        assertTrue(hasNoTransitions);
    }

    @Test
    public void hasNoTransitionsShouldReturnFalseWhenThisStateHasTransitions() {
        state.putTransition((byte) 'a', new ByteState());

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
        state.putTransition((byte) 'a', new ByteState());

        ByteTransition transition = state.getTransition((byte) 'b');

        assertNull(transition);
    }

    @Test
    public void getTransitionShouldReturnTransitionWhenMappingExistsAndThisStateHasOneTransition() {
        byte b = 'a';
        ByteState transition = new ByteState();

        state.putTransition(b, transition);

        ByteTransition actualTransition = state.getTransition(b);

        assertSame(transition, actualTransition);
    }

    @Test
    public void getTransitionShouldReturnTransitionWhenMappingExistsAndThisStateHasTwoTransition() {
        byte b1 = 'a';
        byte b2 = 'b';
        ByteState transition1 = new ByteState();
        ByteState transition2 = new ByteState();

        state.putTransition(b1, transition1);
        state.putTransition(b2, transition2);

        ByteTransition actualTransition = state.getTransition(b1);

        assertSame(transition1, actualTransition);
    }

    @Test
    public void putTransitionShouldCreateMappingWhenMappingDoesNotExistAndThisStateHasNoTransitions() {
        byte b = 'a';
        ByteTransition transition = new ByteState();

        state.putTransition(b, transition);

        assertSame(transition, state.getTransition(b));
    }

    @Test
    public void putTransitionShouldUpdateMappingWhenMappingExistsAndThisStateHasOneTransition() {
        byte b = 'a';
        ByteTransition transition1 = new ByteState();
        ByteTransition transition2 = new ByteState();

        state.putTransition(b, transition1);

        state.putTransition(b, transition2);

        assertSame(transition2, state.getTransition(b));
    }

    @Test
    public void putTransitionShouldCreateMappingWhenMappingDoesNotExistAndThisStateHasOneTransition() {
        byte b1 = 'a';
        byte b2 = 'b';
        ByteTransition transition1 = new ByteState();
        ByteTransition transition2 = new ByteState();

        state.putTransition(b1, transition1);

        state.putTransition(b2, transition2);

        assertSame(transition1, state.getTransition(b1));
        assertSame(transition2, state.getTransition(b2));
    }

    @Test
    public void putTransitionShouldCreateMappingWhenMappingDoesNotExistAndThisStateHasTwoTransitions() {
        byte b1 = 'a';
        byte b2 = 'b';
        byte b3 = 'c';
        ByteTransition transition1 = new ByteState();
        ByteTransition transition2 = new ByteState();
        ByteTransition transition3 = new ByteState();

        state.putTransition(b1, transition1);
        state.putTransition(b2, transition2);

        state.putTransition(b3, transition3);

        assertSame(transition1, state.getTransition(b1));
        assertSame(transition2, state.getTransition(b2));
        assertSame(transition3, state.getTransition(b3));
    }

    @Test
    public void removeTransitionShouldDoNothingWhenMappingDoesNotExistAndThisStateHasNoTransitions() {
        byte b = 'a';

        state.removeTransition(b);

        assertNull(state.getTransition(b));
    }

    @Test
    public void removeTransitionShouldDoNothingWhenMappingDoesNotExistAndThisStateHasOneTransition() {
        byte b = 'a';
        ByteTransition transition = new ByteState();

        state.putTransition(b, transition);

        state.removeTransition((byte) 'b');

        assertSame(transition, state.getTransition(b));
    }

    @Test
    public void removeTransitionShouldRemoveMappingWhenMappingExistsAndThisStateHasOneTransition() {
        byte b = 'a';
        ByteTransition transition = new ByteState();

        state.putTransition(b, transition);

        state.removeTransition(b);

        assertNull(state.getTransition(b));
    }

    @Test
    public void removeTransitionShouldDoNothingWhenMappingDoesNotExistAndThisStateHasTwoTransitions() {
        byte b1 = 'a';
        byte b2 = 'b';
        ByteTransition transition1 = new ByteState();
        ByteTransition transition2 = new ByteState();

        state.putTransition(b1, transition1);
        state.putTransition(b2, transition2);

        state.removeTransition((byte) 'c');

        assertSame(transition1, state.getTransition(b1));
        assertSame(transition2, state.getTransition(b2));
    }

    @Test
    public void removeTransitionShouldRemoveMappingWhenMappingExistsAndThisStateHasTwoTransitions() {
        byte b1 = 'a';
        byte b2 = 'b';
        ByteTransition transition1 = new ByteState();
        ByteTransition transition2 = new ByteState();

        state.putTransition(b1, transition1);
        state.putTransition(b2, transition2);

        state.removeTransition(b1);

        assertNull(state.getTransition(b1));
        assertSame(transition2, state.getTransition(b2));
    }

    @Test
    public void removeTransitionShouldRemoveMappingWhenMappingExistsAndThisStateHasThreeTransitions() {
        byte b1 = 'a';
        byte b2 = 'b';
        byte b3 = 'c';
        ByteTransition transition1 = new ByteState();
        ByteTransition transition2 = new ByteState();
        ByteTransition transition3 = new ByteState();

        state.putTransition(b1, transition1);
        state.putTransition(b2, transition2);
        state.putTransition(b3, transition3);

        state.removeTransition(b1);

        assertNull(state.getTransition(b1));
        assertSame(transition2, state.getTransition(b2));
        assertSame(transition3, state.getTransition(b3));
    }
}
