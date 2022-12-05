package software.amazon.event.ruler.input;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static software.amazon.event.ruler.input.DefaultParser.ASTERISK_BYTE;
import static software.amazon.event.ruler.input.DefaultParser.BACKSLASH_BYTE;

/**
 * A parser to be used specifically for wildcard rules.
 */
public class WildcardParser implements StringValueParser {

    WildcardParser() { }

    @Override
    public InputCharacter[] parse(final String value) {
        final byte[] utf8Bytes = value.getBytes(StandardCharsets.UTF_8);
        final List<InputCharacter> result = new ArrayList<>(utf8Bytes.length);
        for (int i = 0; i < utf8Bytes.length; i++) {
            byte utf8byte = utf8Bytes[i];
            if (utf8byte == ASTERISK_BYTE) {
                if (i + 1 < utf8Bytes.length && utf8Bytes[i + 1] == ASTERISK_BYTE) {
                    throw new ParseException("Consecutive wildcard characters at pos " + i);
                }
                result.add(new InputWildcard());
            } else if (utf8byte == BACKSLASH_BYTE) {
                if (i + 1 < utf8Bytes.length) {
                    byte nextUtf8byte = utf8Bytes[i + 1];
                    if (nextUtf8byte == ASTERISK_BYTE || nextUtf8byte == BACKSLASH_BYTE) {
                        result.add(new InputByte(nextUtf8byte));
                        i++;
                        continue;
                    }
                }
                throw new ParseException("Invalid escape character at pos " + i);
            } else {
                result.add(new InputByte(utf8byte));
            }
        }
        return result.toArray(new InputCharacter[0]);
    }
}