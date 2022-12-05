package software.amazon.event.ruler.input;

import org.junit.Test;

import static software.amazon.event.ruler.input.InputCharacterType.WILDCARD;
import static software.amazon.event.ruler.input.DefaultParser.ASTERISK_BYTE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InputWildcardTest {

    @Test
    public void testGetType() {
        assertEquals(WILDCARD, new InputWildcard().getType());
    }

    @Test
    public void testEquals() {
        assertTrue(new InputWildcard().equals(new InputWildcard()));
        assertFalse(new InputWildcard().equals(new InputByte(ASTERISK_BYTE)));
    }

    @Test
    public void testHashCode() {
        assertEquals(new InputWildcard().hashCode(), new InputWildcard().hashCode());
    }
}
