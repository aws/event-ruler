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
    ANYTHING_BUT,        // deny list effect
    ANYTHING_BUT_IGNORE_CASE, // deny list effect (case insensitive)
    ANYTHING_BUT_PREFIX, // anything that doesn't start with this
    ANYTHING_BUT_SUFFIX, // anything that doesn't end with this
    EQUALS_IGNORE_CASE,  // case-insensitive string match
    WILDCARD,             // string match using one or more non-consecutive '*' wildcard characters
    HTML, // html code in syntax <tag>value</tag>
}
