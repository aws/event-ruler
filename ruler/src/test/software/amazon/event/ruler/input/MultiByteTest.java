package software.amazon.event.ruler.input;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static software.amazon.event.ruler.input.DefaultParser.NINE_BYTE;
import static software.amazon.event.ruler.input.DefaultParser.ZERO_BYTE;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class MultiByteTest {

    @Test
    public void testNoBytes() {
        try {
            new MultiByte();
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) { }
    }

    @Test
    public void testGetBytes() {
        byte[] bytes = new byte[] { (byte) 'a', (byte) 'b' };
        assertArrayEquals(bytes, new MultiByte(bytes).getBytes());
    }

    @Test
    public void testSingularIsSingular() {
        assertEquals((byte) 'a', new MultiByte((byte) 'a').singular());
    }

    @Test
    public void testSingularIsNotSingular() {
        try {
            new MultiByte((byte) 'a', (byte) 'b').singular();
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) { }
    }

    @Test
    public void testIs() {
        assertTrue(new MultiByte((byte) 'a', (byte) 'b').is((byte) 'a', (byte) 'b'));
        assertFalse(new MultiByte((byte) 'a', (byte) 'b').is((byte) 'a'));
        assertFalse(new MultiByte((byte) 'a', (byte) 'b').is((byte) 'a', (byte) 'c'));
        assertFalse(new MultiByte((byte) 'a', (byte) 'b').is((byte) 'b', (byte) 'a'));
    }

    @Test
    public void testIsNumeric() {
        for (int i = 0; i < 10; i++) {
            assertTrue(new MultiByte(String.valueOf(i).getBytes(StandardCharsets.UTF_8)).isNumeric());
        }
        assertFalse(new MultiByte(ZERO_BYTE, NINE_BYTE).isNumeric());
        assertFalse(new MultiByte((byte) 'a').isNumeric());
        assertFalse(new MultiByte((byte) '*').isNumeric());
        assertFalse(new MultiByte((byte) '†').isNumeric());
        assertFalse(new MultiByte((byte) 'É').isNumeric());
        assertFalse(new MultiByte((byte) 'ⱥ').isNumeric());
    }

    @Test
    public void testIsLessThan() {
        assertFalse(new MultiByte((byte) 0x01, (byte) 0xA1).isLessThan(new MultiByte((byte) 0x01, (byte) 0xA1)));
        assertTrue(new MultiByte((byte) 0x01, (byte) 0xA1).isLessThan(new MultiByte((byte) 0x01, (byte) 0xA2)));
        assertFalse(new MultiByte((byte) 0x01, (byte) 0xA1).isLessThan(new MultiByte((byte) 0x01, (byte) 0xA0)));
        assertTrue(new MultiByte((byte) 0x01, (byte) 0xA1).isLessThan(new MultiByte((byte) 0x02, (byte) 0xA1)));
        assertFalse(new MultiByte((byte) 0x01, (byte) 0xA1).isLessThan(new MultiByte((byte) 0x00, (byte) 0xA1)));
        assertFalse(new MultiByte((byte) 0x01, (byte) 0xA1).isLessThan(new MultiByte((byte) 0x01)));
        assertTrue(new MultiByte((byte) 0x01).isLessThan(new MultiByte((byte) 0x01, (byte) 0xA1)));
    }

    @Test
    public void testIsLessThanOrEqualTo() {
        assertTrue(new MultiByte((byte) 0x01, (byte) 0xA1).isLessThanOrEqualTo(new MultiByte((byte) 0x01, (byte) 0xA1)));
        assertTrue(new MultiByte((byte) 0x01, (byte) 0xA1).isLessThanOrEqualTo(new MultiByte((byte) 0x01, (byte) 0xA2)));
        assertFalse(new MultiByte((byte) 0x01, (byte) 0xA1).isLessThanOrEqualTo(new MultiByte((byte) 0x01, (byte) 0xA0)));
        assertTrue(new MultiByte((byte) 0x01, (byte) 0xA1).isLessThanOrEqualTo(new MultiByte((byte) 0x02, (byte) 0xA1)));
        assertFalse(new MultiByte((byte) 0x01, (byte) 0xA1).isLessThanOrEqualTo(new MultiByte((byte) 0x00, (byte) 0xA1)));
        assertFalse(new MultiByte((byte) 0x01, (byte) 0xA1).isLessThanOrEqualTo(new MultiByte((byte) 0x01)));
        assertTrue(new MultiByte((byte) 0x01).isLessThanOrEqualTo(new MultiByte((byte) 0x01, (byte) 0xA1)));
    }

    @Test
    public void testIsGreaterThan() {
        assertFalse(new MultiByte((byte) 0x01, (byte) 0xA1).isGreaterThan(new MultiByte((byte) 0x01, (byte) 0xA1)));
        assertFalse(new MultiByte((byte) 0x01, (byte) 0xA1).isGreaterThan(new MultiByte((byte) 0x01, (byte) 0xA2)));
        assertTrue(new MultiByte((byte) 0x01, (byte) 0xA1).isGreaterThan(new MultiByte((byte) 0x01, (byte) 0xA0)));
        assertFalse(new MultiByte((byte) 0x01, (byte) 0xA1).isGreaterThan(new MultiByte((byte) 0x02, (byte) 0xA1)));
        assertTrue(new MultiByte((byte) 0x01, (byte) 0xA1).isGreaterThan(new MultiByte((byte) 0x00, (byte) 0xA1)));
        assertTrue(new MultiByte((byte) 0x01, (byte) 0xA1).isGreaterThan(new MultiByte((byte) 0x01)));
        assertFalse(new MultiByte((byte) 0x01).isGreaterThan(new MultiByte((byte) 0x01, (byte) 0xA1)));
    }

    @Test
    public void testIsGreaterThanOrEqualTo() {
        assertTrue(new MultiByte((byte) 0x01, (byte) 0xA1).isGreaterThanOrEqualTo(new MultiByte((byte) 0x01, (byte) 0xA1)));
        assertFalse(new MultiByte((byte) 0x01, (byte) 0xA1).isGreaterThanOrEqualTo(new MultiByte((byte) 0x01, (byte) 0xA2)));
        assertTrue(new MultiByte((byte) 0x01, (byte) 0xA1).isGreaterThanOrEqualTo(new MultiByte((byte) 0x01, (byte) 0xA0)));
        assertFalse(new MultiByte((byte) 0x01, (byte) 0xA1).isGreaterThanOrEqualTo(new MultiByte((byte) 0x02, (byte) 0xA1)));
        assertTrue(new MultiByte((byte) 0x01, (byte) 0xA1).isGreaterThanOrEqualTo(new MultiByte((byte) 0x00, (byte) 0xA1)));
        assertTrue(new MultiByte((byte) 0x01, (byte) 0xA1).isGreaterThanOrEqualTo(new MultiByte((byte) 0x01)));
        assertFalse(new MultiByte((byte) 0x01).isGreaterThanOrEqualTo(new MultiByte((byte) 0x01, (byte) 0xA1)));
    }

    @Test
    public void testEquals() {
        assertTrue(new MultiByte((byte) 'a', (byte) 'b').equals(new MultiByte((byte) 'a', (byte) 'b')));
        assertFalse(new MultiByte((byte) 'a', (byte) 'b').equals(new MultiByte((byte) 'b', (byte) 'a')));
        assertFalse(new MultiByte((byte) 'a', (byte) 'b').equals(new MultiByte((byte) 'a', (byte) 'c')));
        assertFalse(new MultiByte((byte) 'a', (byte) 'b').equals(new MultiByte((byte) 'a')));
        assertFalse(new MultiByte((byte) 'a', (byte) 'b').equals(new MultiByte((byte) 'a', (byte) 'b', (byte) 'c')));
        assertFalse(new MultiByte((byte) 'a', (byte) 'b').equals(new Object()));
    }

    @Test
    public void testHashCode() {
        assertEquals(new MultiByte((byte) 'a').hashCode(), new MultiByte((byte) 'a').hashCode());
        assertNotEquals(new MultiByte((byte) 'a').hashCode(), new MultiByte((byte) 'b').hashCode());
    }
}
