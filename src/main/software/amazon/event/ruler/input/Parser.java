package software.amazon.event.ruler.input;

import software.amazon.event.ruler.MatchType;

import java.nio.charset.StandardCharsets;

import static software.amazon.event.ruler.MatchType.EQUALS_IGNORE_CASE;
import static software.amazon.event.ruler.MatchType.WILDCARD;

/**
 * Parses the value for a rule into InputCharacters that are used to add the rule to the Machine. Most characters from a
 * rule's value will be treated by their byte representation, but certain characters, such as for wildcards or regexes,
 * need to be represented differently so the Machine understands their special meaning.
 */
public class Parser {

    static final byte DOLLAR_SIGN_BYTE = 0x24;
    static final byte LEFT_PARENTHESIS_BYTE = 0x28;
    static final byte RIGHT_PARENTHESIS_BYTE = 0x29;
    static final byte ASTERISK_BYTE = 0x2A;
    static final byte PLUS_SIGN_BYTE = 0x2B;
    static final byte COMMA_BYTE = 0x2C;
    static final byte HYPHEN_BYTE = 0x2D;
    static final byte PERIOD_BYTE = 0x2E;
    static final byte ZERO_BYTE = 0x30;
    static final byte NINE_BYTE = 0x39;
    static final byte QUESTION_MARK_BYTE = 0x3F;
    static final byte LEFT_SQUARE_BRACKET_BYTE = 0x5B;
    static final byte BACKSLASH_BYTE = 0x5C;
    static final byte RIGHT_SQUARE_BRACKET_BYTE = 0x5D;
    static final byte CARET_BYTE = 0x5E;
    static final byte LEFT_CURLY_BRACKET_BYTE = 0x7B;
    static final byte VERTICAL_LINE_BYTE = 0x7C;
    static final byte RIGHT_CURLY_BRACKET_BYTE = 0x7D;

    private static final Parser SINGLETON = new Parser();
    private final WildcardParser wildcardParser;
    private final EqualsIgnoreCaseParser equalsIgnoreCaseParser;

    Parser() {
        this(new WildcardParser(), new EqualsIgnoreCaseParser());
    }

    Parser(WildcardParser wildcardParser, EqualsIgnoreCaseParser equalsIgnoreCaseParser) {
        this.wildcardParser = wildcardParser;
        this.equalsIgnoreCaseParser = equalsIgnoreCaseParser;
    }

    public static Parser getParser() {
        return SINGLETON;
    }

    public InputCharacter[] parse(MatchType type, String value) {
        if (type == WILDCARD) {
            return wildcardParser.parse(value);
        } else if (type == EQUALS_IGNORE_CASE) {
            return equalsIgnoreCaseParser.parse(value);
        }

        byte[] utf8bytes = value.getBytes(StandardCharsets.UTF_8);
        InputCharacter[] result = new InputCharacter[utf8bytes.length];
        for (int i = 0; i < utf8bytes.length; i++) {
            byte utf8byte = utf8bytes[i];
            result[i] = new InputByte(utf8byte);
        }
        return result;
    }

    public InputCharacter parse(byte utf8byte) {
        return new InputByte(utf8byte);
    }

}
