package software.amazon.event.ruler.input;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class WildcardParserTest {

    private WildcardParser parser;

    @Before
    public void setup() {
        parser = new WildcardParser();
    }

    @Test
    public void testParseNoSpecialCharacters() {
        assertArrayEquals(toArray(toByte('a'), toByte('b'), toByte('c')), parser.parse("abc"));
    }

    @Test
    public void testParseWithWildcard() {
        assertArrayEquals(toArray(toByte('a'), wildcard(), toByte('c')), parser.parse("a*c"));
    }

    @Test
    public void testParseWithEscapedAsterisk() {
        assertArrayEquals(toArray(toByte('a'), toByte('*'), toByte('c')), parser.parse("a\\*c"));
    }

    @Test
    public void testParseWithEscapedBackslash() {
        assertArrayEquals(toArray(toByte('a'), toByte('\\'), toByte('c')), parser.parse("a\\\\c"));
    }

    @Test
    public void testParseWithEscapedBackslashThenWildcard() {
        assertArrayEquals(toArray(toByte('a'), toByte('\\'), wildcard()), parser.parse("a\\\\*"));
    }

    @Test
    public void testParseWithEscapedBackslashThenEscapedAsterisk() {
        assertArrayEquals(toArray(toByte('a'), toByte('\\'), toByte('*')), parser.parse("a\\\\\\*"));
    }

    @Test
    public void testParseWithEscapedAsteriskThenWildcard() {
        assertArrayEquals(toArray(toByte('a'), toByte('*'), wildcard()), parser.parse("a\\**"));
    }

    @Test
    public void testParseWithInvalidEscapeCharacter() {
        try {
            parser.parse("a\\bc");
            fail("Expected ParseException");
        } catch (ParseException e) {
            assertEquals("Invalid escape character at pos 1", e.getMessage());
        }
    }

    @Test
    public void testParseWithConsecutiveWildcardCharacters() {
        try {
            parser.parse("a**");
            fail("Expected ParseException");
        } catch (ParseException e) {
            assertEquals("Consecutive wildcard characters at pos 1", e.getMessage());
        }
    }

    private static InputCharacter[] toArray(InputCharacter ... chars) {
        return chars;
    }

    private static InputByte toByte(char c) {
        return new InputByte((byte) c);
    }

    private static InputWildcard wildcard() {
        return new InputWildcard();
    }
}
