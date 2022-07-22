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
    public void testParseStringWithSingleBytesMultiBytesCharactersNonCharactersAndDifferingLengthMultiBytes() {
        assertArrayEquals(new InputCharacter[] {
                set(new MultiByte((byte) 49)),
                set(new MultiByte((byte) 97), new MultiByte((byte) 65)),
                set(new MultiByte((byte) 97), new MultiByte((byte) 65)),
                set(new MultiByte((byte) 42)),
                set(new MultiByte((byte) -30, (byte) -128, (byte) -96)),
                set(new MultiByte((byte) -61, (byte) -87), new MultiByte((byte) -61, (byte) -119)),
                set(new MultiByte((byte) -30, (byte) -79, (byte) -91), new MultiByte((byte) -56, (byte) -70))
        }, parser.parse("1aA*†Éⱥ"));
    }

    private static InputMultiByteSet set(MultiByte ... multiBytes) {
        return new InputMultiByteSet(new HashSet<>(Arrays.asList(multiBytes)));
    }
}
