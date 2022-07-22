package software.amazon.event.ruler.input;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * A parser to be used specifically for equals-ignore-case rules.
 *
 * Note that there are actually characters whose upper-case/lower-case UTF-8 representations differ in number of bytes.
 * One example where length differs by 1: ⱥ, Ⱥ
 * One example where length differs by 4: ΰ, Ϋ́
 * To deal with differing byte lengths per Java character, we will parse each Java character into an InputMultiByteSet.
 */
public class EqualsIgnoreCaseParser {

    EqualsIgnoreCaseParser() { }

    public InputCharacter[] parse(String value) {
        int i = 0;
        InputCharacter[] result = new InputCharacter[value.length()];
        for (char c : value.toCharArray()) {
            byte[] lowerCaseUtf8bytes = String.valueOf(c).toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
            byte[] upperCaseUtf8bytes = String.valueOf(c).toUpperCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
            Set<MultiByte> multiBytes = new HashSet<>();
            multiBytes.add(new MultiByte(lowerCaseUtf8bytes));
            multiBytes.add(new MultiByte(upperCaseUtf8bytes));
            result[i++] = new InputMultiByteSet(multiBytes);
        }
        return result;
    }
}