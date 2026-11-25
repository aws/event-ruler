package software.amazon.event.ruler.input;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * A parser to be used specifically for equals-ignore-case rules. For code points where lower and upper case UTF-8
 * representations do not differ, we will parse into InputBytes. Otherwise, we will use InputMultiByteSet.
 *
 * Note that there are actually characters whose upper-case/lower-case UTF-8 representations differ in number of bytes.
 * One example where length differs by 1: ⱥ, Ⱥ
 * One example where length differs by 4: ΰ, Ϋ́
 * InputMultiByteSet handles differing byte lengths per code point.
 */
public class EqualsIgnoreCaseParser {

    EqualsIgnoreCaseParser() { }

    public InputCharacter[] parse(String value) {
        return parse(value, false);
    }

    protected InputCharacter[] parse(String value, boolean reverseCodePointBytes) {
        List<InputCharacter> result = new ArrayList<>(value.length());
        for (int offset = 0; offset < value.length(); ) {
            final int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            final String codePointString = new String(Character.toChars(codePoint));
            final byte[] lowerCaseUtf8Bytes = getCodePointUtf8Bytes(codePointString, (s) -> s.toLowerCase(Locale.ROOT),
                    reverseCodePointBytes);
            final byte[] upperCaseUtf8Bytes = getCodePointUtf8Bytes(codePointString, (s) -> s.toUpperCase(Locale.ROOT),
                    reverseCodePointBytes);
            if (Arrays.equals(lowerCaseUtf8Bytes, upperCaseUtf8Bytes)) {
                for (int i = 0; i < lowerCaseUtf8Bytes.length; i++) {
                    result.add(new InputByte(lowerCaseUtf8Bytes[i]));
                }
            } else {
                Set<MultiByte> multiBytes = new HashSet<>();
                multiBytes.add(new MultiByte(lowerCaseUtf8Bytes));
                multiBytes.add(new MultiByte(upperCaseUtf8Bytes));
                result.add(new InputMultiByteSet(multiBytes));
            }
        }
        return result.toArray(new InputCharacter[0]);
    }

    private static byte[] getCodePointUtf8Bytes(final String codePointString,
                                                final Function<String, String> stringTransformer,
                                                final boolean reverseCodePointBytes) {
        final byte[] byteArray = stringTransformer.apply(codePointString).getBytes(StandardCharsets.UTF_8);
        if (reverseCodePointBytes) {
            return reverseByteArray(byteArray);
        }
        return byteArray;
    }

    private static byte[] reverseByteArray(byte[] byteArray) {
        byte[] reversedByteArray = new byte[byteArray.length];
        for (int i = 0; i < byteArray.length; i++) {
            reversedByteArray[i] = byteArray[byteArray.length - i - 1];
        }
        return reversedByteArray;
    }
}