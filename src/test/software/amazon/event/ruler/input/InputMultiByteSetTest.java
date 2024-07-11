package software.amazon.event.ruler.input;

import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static software.amazon.event.ruler.input.InputCharacterType.MULTI_BYTE_SET;

public class InputMultiByteSetTest {

    @Test
    public void testGetType() {
        assertEquals(MULTI_BYTE_SET,
                new InputMultiByteSet(new HashSet<>(Collections.singletonList(new MultiByte((byte) 'a')))).getType());
    }

    @Test
    public void testCast() {
        InputCharacter character = new InputMultiByteSet(new HashSet<>(Collections.singletonList(new MultiByte((byte) 'a'))));
        assertSame(character, InputMultiByteSet.cast(character));
    }

    @Test
    public void testGetMultiBytes() {
        Set<MultiByte> multiBytes = new HashSet<>(Collections.singletonList(new MultiByte((byte) 'a')));
        assertEquals(multiBytes, new InputMultiByteSet(multiBytes).getMultiBytes());
    }

    @Test
    public void testEquals() {
        InputMultiByteSet setA1 = new InputMultiByteSet(new HashSet<>(Collections.singletonList(new MultiByte((byte) 'a'))));
        InputMultiByteSet setA2 = new InputMultiByteSet(new HashSet<>(Collections.singletonList(new MultiByte((byte) 'a'))));
        InputMultiByteSet setB = new InputMultiByteSet(new HashSet<>(Collections.singletonList(new MultiByte((byte) 'b'))));
        assertEquals(setA1, setA2);
        assertNotEquals(setA1, setB);
        assertNotEquals(setA1, new InputWildcard());
    }

    @Test
    public void testHashCode() {
        InputMultiByteSet setA1 = new InputMultiByteSet(new HashSet<>(Collections.singletonList(new MultiByte((byte) 'a'))));
        InputMultiByteSet setA2 = new InputMultiByteSet(new HashSet<>(Collections.singletonList(new MultiByte((byte) 'a'))));
        InputMultiByteSet setB = new InputMultiByteSet(new HashSet<>(Collections.singletonList(new MultiByte((byte) 'b'))));
        assertEquals(setA1.hashCode(), setA2.hashCode());
        assertNotEquals(setA1.hashCode(), setB.hashCode());
    }
}
