package software.amazon.event.ruler.input;

import software.amazon.event.ruler.MatchType;

import java.nio.charset.StandardCharsets;

import static software.amazon.event.ruler.MatchType.*;

/**
 * Parses the value for a rule into InputCharacters that are used to add the rule to the Machine. Most characters from a
 * rule's value will be treated by their byte representation, but certain characters, such as for wildcards or regexes,
 * need to be represented differently so the Machine understands their special meaning.
 */
public class DefaultParser implements MatchTypeParser, ByteParser {

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
    static final byte OPEN_HTML_BRACKET_BYTE = 0x3c;
    static final byte CLOSE_HTML_BRACKET_BYTE = 0x3e;
    private static final DefaultParser SINGLETON = new DefaultParser();
    private final WildcardParser wildcardParser;
    private final EqualsIgnoreCaseParser equalsIgnoreCaseParser;
    private final SuffixParser suffixParser;
    private final HtmlParser htmlParser;

    DefaultParser() {
        this(new WildcardParser(), new EqualsIgnoreCaseParser(), new SuffixParser(), new HtmlParser());
    }

    DefaultParser(WildcardParser wildcardParser, EqualsIgnoreCaseParser equalsIgnoreCaseParser, SuffixParser suffixParser, HtmlParser htmlParser) {
        this.wildcardParser = wildcardParser;
        this.equalsIgnoreCaseParser = equalsIgnoreCaseParser;
        this.suffixParser = suffixParser;
        this.htmlParser = htmlParser;

    }

    public static DefaultParser getParser() {
        return SINGLETON;
    }

    @Override
    public InputCharacter[] parse(final MatchType type, final String value) {
        if (type == WILDCARD) {
            return wildcardParser.parse(value);
        } else if (type == EQUALS_IGNORE_CASE || type == ANYTHING_BUT_IGNORE_CASE) {
            return equalsIgnoreCaseParser.parse(value);
        } else if (type == SUFFIX || type == ANYTHING_BUT_SUFFIX) {
            return suffixParser.parse(value);
        } else if (type == HTML){
            return htmlParser.parse(value);
        }

        final byte[] utf8bytes = value.getBytes(StandardCharsets.UTF_8);
        final InputCharacter[] result = new InputCharacter[utf8bytes.length];
        for (int i = 0; i < utf8bytes.length; i++) {
            byte utf8byte = utf8bytes[i];
            result[i] = new InputByte(utf8byte);
        }
        return result;
    }

    @Override
    public InputCharacter parse(final byte utf8byte) {
        return new InputByte(utf8byte);
    }
}
