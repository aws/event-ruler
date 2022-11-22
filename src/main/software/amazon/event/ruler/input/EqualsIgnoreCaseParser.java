package software.amazon.event.ruler.input;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import software.amazon.event.ruler.MatchType;

/**
 * A parser to be used specifically for equals-ignore-case rules.
 *
 * Note that there are actually characters whose upper-case/lower-case UTF-8 representations differ in number of bytes.
 * One example where length differs by 1: ⱥ, Ⱥ
 * One example where length differs by 4: ΰ, Ϋ́
 * To deal with differing byte lengths per Java character, we will parse each Java character into an InputMultiByteSet.
 */
public class EqualsIgnoreCaseParser implements StringValueParser {

    EqualsIgnoreCaseParser() { }

    public InputCharacter[] parse(final String value) {
        int i = 0;
        final InputCharacter[] result = new InputCharacter[value.length()];
        for (char c : value.toCharArray()) {
            byte[] lower = String.valueOf(c).toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
            byte[] upper = String.valueOf(c).toUpperCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
            Set<MultiByte> multi = new HashSet<>();
            multi.add(new MultiByte(lower));
            multi.add(new MultiByte(upper));
            result[i++] = new InputMultiByteSet(multi);
        }
        return result;
    }
}