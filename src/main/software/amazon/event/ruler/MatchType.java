package software.amazon.event.ruler;

/**
 * The types of value matches that Ruler supports
 */
public enum MatchType {
    EXACT,               // exact string
    ABSENT,              // absent key pattern
    EXISTS,              // existence pattern
    PREFIX,              // string prefix
    SUFFIX,              // string suffix
    NUMERIC_EQ,          // exact numeric match
    NUMERIC_RANGE,       // numeric range with high & low bound & </<=/>/>= options
    ANYTHING_BUT,        // black list effect
    ANYTHING_BUT_PREFIX, // anything that doesn't start with this
    EQUALS_IGNORE_CASE,  // case-insensitive string match
    WILDCARD,             // string match using one or more non-consecutive '*' wildcard characters
}