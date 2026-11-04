package software.amazon.event.ruler.input;

import org.junit.Before;
import org.junit.Test;
import software.amazon.event.ruler.Patterns;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;

import static org.junit.Assert.assertArrayEquals;

public class SuffixEqualsIgnoreCaseParserTest {

    private static final String DESERET_CAPITAL_LONG_I = new String(Character.toChars(0x10400));
    private static final String GRINNING_FACE = new String(Character.toChars(0x1F600));

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

    @Test
    public void testParseSupplementaryCharacterWithCaseVariants() {
        assertArrayEquals(new InputCharacter[] {
                reversedDeseretLongISet()
        }, parser.parse(DESERET_CAPITAL_LONG_I));
    }

    @Test
    public void testParseSupplementaryCharacterWithoutCaseVariants() {
        assertArrayEquals(reversedGrinningFaceBytes(), parser.parse(GRINNING_FACE));
    }

    @Test
    public void testParseStringMixingSingleByteAndSupplementaryCharacters() {
        InputCharacter[] reversedGrinningFaceBytes = reversedGrinningFaceBytes();
        assertArrayEquals(new InputCharacter[] {
                set(new MultiByte((byte) 97), new MultiByte((byte) 65)),
                reversedDeseretLongISet(),
                new InputByte((byte) 49),
                reversedGrinningFaceBytes[0], reversedGrinningFaceBytes[1], reversedGrinningFaceBytes[2],
                reversedGrinningFaceBytes[3]
        }, parser.parse("a" + DESERET_CAPITAL_LONG_I + "1" + GRINNING_FACE));
    }

    @Test
    public void testParseReversedSuffixWithSupplementaryCharacterGivesFullByteReversal() {
        String suffix = "a" + GRINNING_FACE + "1";
        byte[] suffixBytes = suffix.getBytes(StandardCharsets.UTF_8);
        InputCharacter[] expected = new InputCharacter[suffixBytes.length];
        for (int i = 0; i < suffixBytes.length; i++) {
            expected[i] = new InputByte(suffixBytes[suffixBytes.length - 1 - i]);
        }
        expected[suffixBytes.length - 1] = set(new MultiByte((byte) 97), new MultiByte((byte) 65));
        assertArrayEquals(expected, parser.parse(Patterns.suffixEqualsIgnoreCaseMatch(suffix).pattern()));
    }

    private static InputMultiByteSet reversedDeseretLongISet() {
        return set(new MultiByte((byte) 0x80, (byte) 0x90, (byte) 0x90, (byte) 0xF0),
                new MultiByte((byte) 0xA8, (byte) 0x90, (byte) 0x90, (byte) 0xF0));
    }

    private static InputCharacter[] reversedGrinningFaceBytes() {
        return new InputCharacter[] {
                new InputByte((byte) 0x80), new InputByte((byte) 0x98), new InputByte((byte) 0x9F),
                new InputByte((byte) 0xF0)
        };
    }

    private static InputMultiByteSet set(MultiByte ... multiBytes) {
        return new InputMultiByteSet(new HashSet<>(Arrays.asList(multiBytes)));
    }
}