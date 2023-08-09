package software.amazon.event.ruler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

public class ByteMatchTest {

    private ByteMatch match;

    @Before
    public void setUp() {
        match = new ByteMatch(Patterns.exactMatch("abc"), new NameState());
    }

    @Test
    public void getNextByteStateShouldReturnNull() {
        SingleByteTransition nextState = match.getNextByteState();
        assertNull(nextState);
    }

    @Test
    public void setNextByteStateShouldReturnThisMatchWhenGivenNextStateIsNull() {
        ByteTransition transition = match.setNextByteState(null);
        assertSame(match, transition);
    }

    @Test
    public void setNextByteStateShouldReturnNewCompositeTransitionWhenGivenNextStateIsNotNull() {
        ByteState nextState = new ByteState();

        ByteTransition transition = match.setNextByteState(nextState);

        assertTrue(transition instanceof CompositeByteTransition);
        assertSame(nextState, transition.getNextByteState());
        assertEquals(match, transition.getMatches());
    }

    @Test
    public void getMatchShouldReturnThisMatch() {
        ByteMatch actualMatch = match.getMatch();
        assertEquals(match, actualMatch);
    }

    @Test
    public void getMatchesShouldReturnThisMatch() {
        assertEquals(match, match.getMatches());
    }

    @Test
    public void setMatchShouldReturnNullWhenGivenMatchIsNull() {
        assertNull(match.setMatch(null));
    }

    @Test
    public void setMatchShouldReturnGivenMatchWhenGivenMatchIsNotNull() {
        ByteMatch newMatch = new ByteMatch(Patterns.exactMatch("xyz"), new NameState());
        ByteTransition transition = match.setMatch(newMatch);
        assertSame(newMatch, transition);
    }

    @Test
    public void expandShouldReturnMatch() {
        assertEquals(match, match.expand());
    }

    @Test
    public void hasIndeterminatePrefixShouldReturnFalse() {
        assertFalse(match.hasIndeterminatePrefix());
    }

    @Test
    public void getTransitionShouldReturnNull() {
        assertNull(match.getTransition((byte) 'a'));
    }

    @Test
    public void getTransitionForAllBytesShouldReturnNull() {
        assertNull(match.getTransitionForAllBytes());
    }

    @Test
    public void getTransitionsShouldReturnEmptySet() {
        assertEquals(Collections.emptySet(), match.getTransitions());
    }

    @Test
    public void getShortcutsShouldReturnEmptySet() {
        assertEquals(Collections.emptySet(), match.getShortcuts());
    }

    @Test
    public void isMatchTransShouldReturnTrue() {
        assertTrue(match.isMatchTrans());
    }

    @Test
    public void WHEN_MatchIsInitialized_THEN_GettersWork() {
        NameState ns = new NameState();
        ByteMatch cut = new ByteMatch(Patterns.anythingButMatch("foo"), ns);
        assertEquals(ns, cut.getNextNameState());
        assertEquals(Patterns.anythingButMatch("foo"), cut.getPattern());
    }
}