package software.amazon.event.ruler.input;

import java.nio.charset.StandardCharsets;

/**
 * A parser to be used specifically for suffix rules.
 *
 * This undoes the `reverse()` from {@code software.amazon.event.ruler.Patterns} intentionally
 * to ensure we can correctly reverse utf-8 characters with 2+ bytes like '大' and '雨'.
 */
public class SuffixParser implements StringValueParser {

    SuffixParser() { }

    @Override
    public InputCharacter[] parse(String value) {
        final byte[] utf8bytes = new StringBuilder(value).reverse()
                .toString().getBytes(StandardCharsets.UTF_8);
        final InputCharacter[] result = new InputCharacter[utf8bytes.length];
        for (int i = 0; i < utf8bytes.length; i++) {
            byte utf8byte = utf8bytes[utf8bytes.length - i - 1];
            result[i] = new InputByte(utf8byte);
        }
        return result;
    }
}
