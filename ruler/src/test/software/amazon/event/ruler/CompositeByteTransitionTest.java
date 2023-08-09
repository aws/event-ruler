package software.amazon.event.ruler;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class CompositeByteTransitionTest {

    private ByteState nextState;
    private ByteMatch match;
    private CompositeByteTransition compositeTransition;

    @Before
    public void setUp() {
        nextState = new ByteState();
        match = new ByteMatch(Patterns.exactMatch("abc"), new NameState());
        compositeTransition = new CompositeByteTransition(nextState, match);
    }

    @Test
    public void getNextByteStateShouldReturnSetNextState() {
        assertSame(nextState, compositeTransition.getNextByteState());
    }

    @Test
    public void setNextByteStateShouldReturnSetMatchWhenGivenNextStateIsNull() {
        ByteTransition transition = compositeTransition.setNextByteState(null);
        assertSame(match, transition);
    }

    @Test
    public void setNextByteStateShouldReturnThisCompositeTransitionWhenGivenNextStateIsNotNull() {
        compositeTransition = new CompositeByteTransition(null, match);
        ByteState nextState = new ByteState();

        ByteTransition transition = compositeTransition.setNextByteState(nextState);

        assertSame(compositeTransition, transition);
        assertSame(nextState, transition.getNextByteState());
    }

    @Test
    public void getMatchShouldReturnSetMatch() {
        assertSame(match, compositeTransition.getMatch());
    }

    @Test
    public void getMatchesShouldReturnMatch() {
        assertEquals(match, compositeTransition.getMatches());
    }

    @Test
    public void setMatchShouldReturnSetNextStateWhenGivenMatchIsNull() {
        ByteTransition transition = compositeTransition.setMatch(null);
        assertSame(nextState, transition);
    }

    @Test
    public void setMatchShouldReturnThisCompositeTransitionWhenGivenMatchIsNotNull() {
        ByteMatch match = new ByteMatch(Patterns.exactMatch("xyz"), new NameState());

        SingleByteTransition transition = compositeTransition.setMatch(match);

        assertSame(compositeTransition, transition);
        assertSame(match, transition.getMatch());
    }

    @Test
    public void getTransitionForAllBytesShouldReturnNull() {
        assertNull(compositeTransition.getTransitionForAllBytes());
    }

    @Test
    public void getTransitionsShouldReturnEmptySet() {
        assertEquals(Collections.emptySet(), compositeTransition.getTransitions());
    }

    @Test
    public void getShortcutsShouldReturnEmptySet() {
        assertEquals(Collections.emptySet(), compositeTransition.getShortcuts());
    }

    @Test
    public void isMatchTransShouldReturnTrue() {
        assertTrue(compositeTransition.isMatchTrans());
    }


    @Test
    public void expandShouldReturnComposite() {
        assertEquals(compositeTransition, compositeTransition.expand());
    }

    @Test
    public void hasIndeterminatePrefixShouldReturnResultFromNextState() {
        nextState.setIndeterminatePrefix(false);
        assertFalse(compositeTransition.hasIndeterminatePrefix());
        nextState.setIndeterminatePrefix(true);
        assertTrue(compositeTransition.hasIndeterminatePrefix());
        nextState.setIndeterminatePrefix(false);
        assertFalse(compositeTransition.hasIndeterminatePrefix());
    }

    @Test
    public void hasIndeterminatePrefixShouldReturnFalseIfNoNextState() {
        compositeTransition = new CompositeByteTransition(null, match);
        assertFalse(compositeTransition.hasIndeterminatePrefix());
    }
}
