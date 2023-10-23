package software.amazon.event.ruler.input;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.Assert.assertArrayEquals;

public class SuffixEqualsIgnoreCaseParserTest {

    private SuffixEqualsIgnoreCaseParser parser;

    @Before
    public void setup() {
        parser = new SuffixEqualsIgnoreCaseParser();
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
                new InputByte((byte) -96), new InputByte((byte) -128), new InputByte((byte) -30),
                set(new MultiByte((byte) -119, (byte) -61), new MultiByte((byte) -87, (byte) -61)),
                set(new MultiByte((byte) -70, (byte) -56), new MultiByte((byte) -91, (byte) -79, (byte) -30)),
        }, parser.parse("1aA*†Éⱥ"));
    }

    private static InputMultiByteSet set(MultiByte ... multiBytes) {
        return new InputMultiByteSet(new HashSet<>(Arrays.asList(multiBytes)));
    }
}