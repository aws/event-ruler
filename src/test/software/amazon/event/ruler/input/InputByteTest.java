package software.amazon.event.ruler.input;

import org.junit.Test;

import static software.amazon.event.ruler.input.InputCharacterType.BYTE;
import static software.amazon.event.ruler.input.DefaultParser.ASTERISK_BYTE;
import static software.amazon.event.ruler.input.DefaultParser.BACKSLASH_BYTE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class InputByteTest {

    @Test
    public void testGetType() {
        assertEquals(BYTE, new InputByte(ASTERISK_BYTE).getType());
    }

    @Test
    public void testGetByte() {
        assertEquals(ASTERISK_BYTE, new InputByte(ASTERISK_BYTE).getByte());
    }

    @Test
    public void testEquals() {
        assertTrue(new InputByte(ASTERISK_BYTE).equals(new InputByte(ASTERISK_BYTE)));
        assertFalse(new InputByte(ASTERISK_BYTE).equals(new InputByte(BACKSLASH_BYTE)));
        assertFalse(new InputByte(ASTERISK_BYTE).equals(new InputWildcard()));
    }

    @Test
    public void testHashCode() {
        assertEquals(Byte.valueOf((byte) 'a').hashCode(), new InputByte((byte) 'a').hashCode());
        assertNotEquals(Byte.valueOf((byte) 'a').hashCode(), new InputByte((byte) 'b').hashCode());
    }
}
