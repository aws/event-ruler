package software.amazon.event.ruler;

/**
 * The types of value matches that Ruler supports
 */
public enum MatchType {
    EXACT,
    ABSENT,
    EXISTS,
    PREFIX,
    PREFIX_EQUALS_IGNORE_CASE,
    SUFFIX,
    SUFFIX_EQUALS_IGNORE_CASE,
    NUMERIC_EQ,
    NUMERIC_RANGE,
    ANYTHING_BUT,
    ANYTHING_BUT_IGNORE_CASE,
    ANYTHING_BUT_PREFIX,
    ANYTHING_BUT_SUFFIX,
    ANYTHING_BUT_WILDCARD,
    EQUALS_IGNORE_CASE,
    WILDCARD,
}
