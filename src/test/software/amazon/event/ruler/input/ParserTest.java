package software.amazon.event.ruler.input;

import software.amazon.event.ruler.MatchType;
import org.junit.Test;

import static software.amazon.event.ruler.input.DefaultParser.getParser;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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
        final int[] parserInvokedCount = { 0, 0, 0 };
        DefaultParser parser = new DefaultParser(
            new WildcardParser() {
               @Override
               public InputCharacter[] parse(String value) {
                   parserInvokedCount[0] +=1;
                   return null;
               }
            },
            new EqualsIgnoreCaseParser() {
                @Override
                public InputCharacter[] parse(String value) {
                    parserInvokedCount[1] += 1;
                    return null;
                }
            },
            new SuffixParser() {
                @Override
                public InputCharacter[] parse(String value) {
                    parserInvokedCount[2] += 1;
                    return null;
                }
            },
                new HtmlParser(){
                @Override
                    public InputCharacter[] parse(String value) {
                    parserInvokedCount[3] +=1;
                    return null;
                }
                }
        );

        assertNull(parser.parse(MatchType.WILDCARD, "abc"));
        assertEquals(parserInvokedCount[0], 1);
        assertEquals(parserInvokedCount[1], 0);
        assertEquals(parserInvokedCount[2], 0);

        assertNull(parser.parse(MatchType.EQUALS_IGNORE_CASE, "abc"));
        assertEquals(parserInvokedCount[0], 1);
        assertEquals(parserInvokedCount[1], 1);
        assertEquals(parserInvokedCount[2], 0);

        assertNull(parser.parse(MatchType.SUFFIX, "abc"));
        assertEquals(parserInvokedCount[0], 1);
        assertEquals(parserInvokedCount[1], 1);
        assertEquals(parserInvokedCount[2], 1);
    }
}
