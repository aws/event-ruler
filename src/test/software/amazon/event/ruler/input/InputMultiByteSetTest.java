package software.amazon.event.ruler.input;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static software.amazon.event.ruler.input.InputCharacterType.MULTI_BYTE_SET;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class InputMultiByteSetTest {

    @Test
    public void testGetType() {
        assertEquals(MULTI_BYTE_SET,
                new InputMultiByteSet(new HashSet<>(Arrays.asList(new MultiByte((byte) 'a')))).getType());
    }

    @Test
    public void testCast() {
        InputCharacter character = new InputMultiByteSet(new HashSet<>(Arrays.asList(new MultiByte((byte) 'a'))));
        assertSame(character, InputMultiByteSet.cast(character));
    }

    @Test
    public void testGetMultiBytes() {
        Set<MultiByte> multiBytes = new HashSet<>(Arrays.asList(new MultiByte((byte) 'a')));
        assertEquals(multiBytes, new InputMultiByteSet(multiBytes).getMultiBytes());
    }

    @Test
    public void testEquals() {
        InputMultiByteSet setA1 = new InputMultiByteSet(new HashSet<>(Arrays.asList(new MultiByte((byte) 'a'))));
        InputMultiByteSet setA2 = new InputMultiByteSet(new HashSet<>(Arrays.asList(new MultiByte((byte) 'a'))));
        InputMultiByteSet setB = new InputMultiByteSet(new HashSet<>(Arrays.asList(new MultiByte((byte) 'b'))));
        assertTrue(setA1.equals(setA2));
        assertFalse(setA1.equals(setB));
        assertFalse(setA1.equals(new InputWildcard()));
    }

    @Test
    public void testHashCode() {
        InputMultiByteSet setA1 = new InputMultiByteSet(new HashSet<>(Arrays.asList(new MultiByte((byte) 'a'))));
        InputMultiByteSet setA2 = new InputMultiByteSet(new HashSet<>(Arrays.asList(new MultiByte((byte) 'a'))));
        InputMultiByteSet setB = new InputMultiByteSet(new HashSet<>(Arrays.asList(new MultiByte((byte) 'b'))));
        assertEquals(setA1.hashCode(), setA2.hashCode());
        assertNotEquals(setA1.hashCode(), setB.hashCode());
    }
}
