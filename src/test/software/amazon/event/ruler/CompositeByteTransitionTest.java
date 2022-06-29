package software.amazon.event.ruler;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertSame;

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
        ByteState actualNextState = compositeTransition.getNextByteState();
        assertSame(nextState, actualNextState);
    }

    @Test
    public void setNextByteStateShouldReturnSetMatchWhenGivenNextStateIsNull() {
        ByteTransition transition = compositeTransition.setNextByteState(null);
        assertSame(match, transition);
    }

    @Test
    public void setNextByteStateShouldReturnThisCompositeTransitionWhenGivenNextStateIsNotNull() {
        ByteState nextState = new ByteState();

        ByteTransition transition = compositeTransition.setNextByteState(nextState);

        assertSame(compositeTransition, transition);
        assertSame(nextState, transition.getNextByteState());
    }

    @Test
    public void getMatchShouldReturnSetMatch() {
        ByteMatch actualMatch = compositeTransition.getMatch();
        assertSame(match, actualMatch);
    }

    @Test
    public void setMatchShouldReturnSetNextStateWhenGivenMatchIsNull() {
        ByteTransition transition = compositeTransition.setMatch(null);
        assertSame(nextState, transition);
    }

    @Test
    public void setMatchShouldReturnThisCompositeTransitionWhenGivenMatchIsNotNull() {
        ByteMatch match = new ByteMatch(Patterns.exactMatch("xyz"), new NameState());

        ByteTransition transition = compositeTransition.setMatch(match);

        assertSame(compositeTransition, transition);
        assertSame(match, transition.getMatch());
    }
}
