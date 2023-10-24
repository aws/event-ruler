package software.amazon.event.ruler.input;

/**
 * A parser to be used specifically for suffix equals-ignore-case rules.
 *
 * This extends EqualsIgnoreCaseParser to parse and reverse char bytes into InputMultiByteSet
 * to account for lower-case and upper-case variants.
 *
 */
public class SuffixEqualsIgnoreCaseParser extends EqualsIgnoreCaseParser {

    SuffixEqualsIgnoreCaseParser() { }

    public InputCharacter[] parse(String value) {
        // By using EqualsIgnoreCaseParser, we reverse chars in one pass when getting the char bytes for
        // lower-case and upper-case values.
        return parse(value, true);
    }
}