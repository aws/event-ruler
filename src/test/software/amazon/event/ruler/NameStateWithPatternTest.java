package software.amazon.event.ruler;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class NameStateWithPatternTest {

    @Test
    public void testNullNameState() {
        try {
            new NameStateWithPattern(null, Patterns.existencePatterns());
            fail("Expected NullPointerException");
        } catch (NullPointerException e) { }
    }

    @Test
    public void testNullPattern() {
        // null pattern is allowed due to the case that the NameState is the starting state of a Machine.
        new NameStateWithPattern(new NameState(), null);
    }

    @Test
    public void testGetters() {
        NameState nameState = new NameState();
        Patterns pattern = Patterns.exactMatch("abc");
        NameStateWithPattern nameStateWithPattern = new NameStateWithPattern(nameState, pattern);
        assertSame(nameState, nameStateWithPattern.getNameState());
        assertSame(pattern, nameStateWithPattern.getPattern());
    }

    @Test
    public void testEquals() {
        NameState nameState1 = new NameState();
        NameState nameState2 = new NameState();
        Patterns pattern1 = Patterns.exactMatch("abc");
        Patterns pattern2 = Patterns.exactMatch("def");
        assertTrue(new NameStateWithPattern(nameState1, pattern1).equals(
                new NameStateWithPattern(nameState1, pattern1)));
        assertFalse(new NameStateWithPattern(nameState1, pattern1).equals(
                new NameStateWithPattern(nameState2, pattern1)));
        assertFalse(new NameStateWithPattern(nameState1, pattern1).equals(
                new NameStateWithPattern(nameState1, pattern2)));
    }

    @Test
    public void testHashCode() {
        NameState nameState1 = new NameState();
        NameState nameState2 = new NameState();
        Patterns pattern1 = Patterns.exactMatch("abc");
        Patterns pattern2 = Patterns.exactMatch("def");
        assertEquals(new NameStateWithPattern(nameState1, pattern1).hashCode(),
                new NameStateWithPattern(nameState1, pattern1).hashCode());
        assertNotEquals(new NameStateWithPattern(nameState1, pattern1).hashCode(),
                new NameStateWithPattern(nameState2, pattern1).hashCode());
        assertNotEquals(new NameStateWithPattern(nameState1, pattern1).hashCode(),
                new NameStateWithPattern(nameState1, pattern2).hashCode());
    }
}