package software.amazon.event.ruler;

import org.junit.Before;
import org.junit.Test;
import software.amazon.event.ruler.Patterns;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ByteMatchTest {

    private ByteMatch match;

    @Before
    public void setUp() {
        match = new ByteMatch(Patterns.exactMatch("abc"), new NameState());
    }

    @Test
    public void getNextByteStateShouldReturnNull() {
        ByteState nextState = match.getNextByteState();
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
        assertSame(match, transition.getMatch());
    }

    @Test
    public void getMatchShouldReturnThisMatch() {
        ByteMatch actualMatch = match.getMatch();
        assertSame(match, actualMatch);
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
    public void WHEN_MatchIsInitialized_THEN_GettersWork() {
        NameState ns = new NameState();
        ByteMatch cut = new ByteMatch(Patterns.anythingButMatch("foo"), ns);
        assertEquals(ns, cut.getNextNameState());
        assertEquals(Patterns.anythingButMatch("foo"), cut.getPattern());
        assertNull(cut.getNextMatch());
    }

    @Test
    public void WHEN_SettersAreCalled_THEN_GettersWork() {
        NameState ns = new NameState();
        ByteMatch cut = new ByteMatch(Patterns.anythingButMatch("foo"), ns);
        ByteMatch s2 = new ByteMatch(Patterns.exactMatch("foo"), ns);
        cut.setNextMatch(s2);
        assertEquals(s2, cut.getNextMatch());
        assertNull(s2.getNextMatch());
    }

    @Test
    public void WHEN_MultipleMatchesAreSet_THEN_TheyChainProperly() {
        NameState ns = new NameState();
        ByteMatch cut = new ByteMatch(Patterns.anythingButMatch("foo"), ns);
        ByteMatch s2 = new ByteMatch(Patterns.exactMatch("foo"), ns);
        ByteMatch s3 = new ByteMatch(Patterns.prefixMatch("foo"), ns);
        cut.setNextMatch(s2);
        s2.setNextMatch(s3);
        assertEquals(s2, cut.getNextMatch());
        assertEquals(s3, s2.getNextMatch());
        assertNull(s3.getNextMatch());
    }

    @Test
    public void TEST_Match_Equal() {
        NameState ns = new NameState();
        ByteMatch s2 = new ByteMatch(Patterns.exactMatch("foo"), ns);
        ByteMatch s3 = new ByteMatch(Patterns.prefixMatch("foo"), ns);

        assertNotEquals(s2, s3);
        assertEquals(s2, new ByteMatch(Patterns.exactMatch("foo"), ns));
        assertEquals(s3, new ByteMatch(Patterns.prefixMatch("foo"), ns));
    }
}