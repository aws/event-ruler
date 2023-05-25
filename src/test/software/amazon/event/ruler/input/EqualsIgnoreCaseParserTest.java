package software.amazon.event.ruler.input;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class EqualsIgnoreCaseParserTest {

    private EqualsIgnoreCaseParser parser;

    @Before
    public void setup() {
        parser = new EqualsIgnoreCaseParser();
    }

    @Test
    public void testParseEmptyString() {
        assertEquals(0, parser.parse("").length);
    }

    @Test
    public void testParseSimpleString() {
        assertArrayEquals(new InputCharacter[] {
                set(new MultiByte((byte) 97), new MultiByte((byte) 65)),
                set(new MultiByte((byte) 98), new MultiByte((byte) 66)),
                set(new MultiByte((byte) 99), new MultiByte((byte) 67)),
        }, parser.parse("aBc"));
    }

    @Test
    public void testParseSimpleStringWithNonLetters() {
        assertArrayEquals(new InputCharacter[] {
                set(new MultiByte((byte) 97), new MultiByte((byte) 65)),
                new InputByte((byte) 49),
                set(new MultiByte((byte) 98), new MultiByte((byte) 66)),
                new InputByte((byte) 50),
                set(new MultiByte((byte) 99), new MultiByte((byte) 67)),
                new InputByte((byte) 33),
        }, parser.parse("a1B2c!"));
    }

    @Test
    public void testParseStringWithSingleBytesMultiBytesCharactersNonCharactersAndDifferingLengthMultiBytes() {
        assertArrayEquals(new InputCharacter[] {
                new InputByte((byte) 49),
                set(new MultiByte((byte) 97), new MultiByte((byte) 65)),
                set(new MultiByte((byte) 97), new MultiByte((byte) 65)),
                new InputByte((byte) 42),
                new InputByte((byte) -30), new InputByte((byte) -128), new InputByte((byte) -96),
                set(new MultiByte((byte) -61, (byte) -87), new MultiByte((byte) -61, (byte) -119)),
                set(new MultiByte((byte) -30, (byte) -79, (byte) -91), new MultiByte((byte) -56, (byte) -70))
        }, parser.parse("1aA*†Éⱥ"));
    }

    private static InputMultiByteSet set(MultiByte ... multiBytes) {
        return new InputMultiByteSet(new HashSet<>(Arrays.asList(multiBytes)));
    }
}
