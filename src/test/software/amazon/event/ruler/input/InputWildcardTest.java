package software.amazon.event.ruler.input;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static software.amazon.event.ruler.input.DefaultParser.ASTERISK_BYTE;
import static software.amazon.event.ruler.input.InputCharacterType.WILDCARD;

public class InputWildcardTest {

    @Test
    public void testGetType() {
        assertEquals(WILDCARD, new InputWildcard().getType());
    }

    @Test
    public void testEquals() {
        assertEquals(new InputWildcard(), new InputWildcard());
        assertNotEquals(new InputWildcard(), new InputByte(ASTERISK_BYTE));
    }

    @Test
    public void testHashCode() {
        assertEquals(new InputWildcard().hashCode(), new InputWildcard().hashCode());
    }
}
