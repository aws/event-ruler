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
 * A parser to be used specifically for equals-ignore-case rules. For Java characters where lower and upper case UTF-8
 * representations do not differ, we will parse into InputBytes. Otherwise, we will use InputMultiByteSet.
 *
 * Note that there are actually characters whose upper-case/lower-case UTF-8 representations differ in number of bytes.
 * One example where length differs by 1: ⱥ, Ⱥ
 * One example where length differs by 4: ΰ, Ϋ́
 * InputMultiByteSet handles differing byte lengths per Java character.
 */
public class EqualsIgnoreCaseParser {

    EqualsIgnoreCaseParser() { }

    public InputCharacter[] parse(String value) {
        return parse(value, false);
    }

    protected InputCharacter[] parse(String value, boolean reverseCharBytes) {
        List<InputCharacter> result = new ArrayList<>(value.length());
        for (char c : value.toCharArray()) {
            byte[] lowerCaseUtf8bytes = getCharUtfBytes(c, (ch) -> ch.toLowerCase(Locale.ROOT), reverseCharBytes);
            byte[] upperCaseUtf8bytes = getCharUtfBytes(c, (ch) -> ch.toUpperCase(Locale.ROOT), reverseCharBytes);
            if (Arrays.equals(lowerCaseUtf8bytes, upperCaseUtf8bytes)) {
                for (int i = 0; i < lowerCaseUtf8bytes.length; i++) {
                    result.add(new InputByte(lowerCaseUtf8bytes[i]));
                }
            } else {
                Set<MultiByte> multiBytes = new HashSet<>();
                multiBytes.add(new MultiByte(lowerCaseUtf8bytes));
                multiBytes.add(new MultiByte(upperCaseUtf8bytes));
                result.add(new InputMultiByteSet(multiBytes));
            }
        }
        return result.toArray(new InputCharacter[0]);
    }

    private static byte[] getCharUtfBytes(char c, Function<String, String> stringTransformer, boolean reverseCharBytes) {
        byte[] byteArray = stringTransformer.apply(String.valueOf(c)).getBytes(StandardCharsets.UTF_8);
        if (reverseCharBytes) {
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