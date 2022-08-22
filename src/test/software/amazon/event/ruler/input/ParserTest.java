package software.amazon.event.ruler.input;

import software.amazon.event.ruler.MatchType;
import org.junit.Test;

import static software.amazon.event.ruler.input.Parser.getParser;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ParserTest {

    @Test
    public void testParseByte() {
        assertEquals(new InputByte((byte) 'a'), getParser().parse((byte) 'a'));
    }

    @Test
    public void testParseString() {
        assertArrayEquals(new InputCharacter[] { new InputByte((byte) 'a'),
                                                 new InputByte((byte) '*'),
                                                 new InputByte((byte) 'c') },
                getParser().parse(MatchType.EXACT, "a*c"));
    }

    @Test
    public void testOtherMatchTypes() {
        final boolean[] parserInvoked = { false, false };
        Parser parser = new Parser(
            new WildcardParser() {
               @Override
               public InputCharacter[] parse(String value) {
                   parserInvoked[0] = true;
                   return null;
               }
            },
            new EqualsIgnoreCaseParser() {
                @Override
                public InputCharacter[] parse(String value) {
                    parserInvoked[1] = true;
                    return null;
                }
            }
        );

        assertNull(parser.parse(MatchType.WILDCARD, "abc"));
        assertTrue(parserInvoked[0]);
        assertFalse(parserInvoked[1]);

        assertNull(parser.parse(MatchType.EQUALS_IGNORE_CASE, "abc"));
        assertTrue(parserInvoked[0]);
        assertTrue(parserInvoked[1]);
    }
}
