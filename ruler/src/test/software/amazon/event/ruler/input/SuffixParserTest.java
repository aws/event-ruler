package software.amazon.event.ruler.input;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public class SuffixParserTest {

    private SuffixParser parser;

    @Before
    public void setup() {
        parser = new SuffixParser();
    }

    @Test
    public void testParseSimpleString() {
        assertArrayEquals(new InputCharacter[] {
                new InputByte((byte) 34), new InputByte((byte) 97) ,
                new InputByte((byte) 98), new InputByte((byte) 99)
        }, parser.parse("\"abc"));
    }

    @Test
    public void testParseReverseString() {
        assertArrayEquals(new InputCharacter[] {
                new InputByte((byte) 34), new InputByte((byte) 100) , new InputByte((byte) 99) ,
                new InputByte((byte) 98), new InputByte((byte) 97)
        }, parser.parse("\"dcba"));
    }

    @Test
    public void testParseChineseString() {
        assertArrayEquals(new InputCharacter[] {
                new InputByte((byte) 34), new InputByte((byte) -88) ,
                new InputByte((byte) -101), new InputByte((byte) -23)
        }, parser.parse("\"雨"));
    }
}