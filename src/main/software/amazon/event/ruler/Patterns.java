package software.amazon.event.ruler;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The Patterns deal pre-processing of rules for the eventual matching against events.
 * It has subclasses for different match types (like ValuePatterns, Ranges, AnythingBut, etc).
 * This class also acts as the factory for to build patterns which is useful if you have
 * key value pairs and would like to build your rules for them directly instead of using the fancy
 * JSON query language (and its compiler). Once you build rules, you can add them via `Machine.addRule()`.
 *
 * NOTE: The subclasses have additional builders that are only needed by JSON rule compilier and are
 * left out of here intentionally for now.
 */
public class Patterns implements Cloneable  {

    public static final String EXISTS_BYTE_STRING = "N";

    private final MatchType type;

    // This interface is deprecated and we keep it here for backward compatibility only.
    // Note: right now (5/28/2019), only ValuePatterns overrides it.
    @Deprecated
    public String pattern() { return null; }

    Patterns(final MatchType type) {
        this.type = type;
    }

    public MatchType type() {
        return type;
    }

    public static ValuePatterns exactMatch(final String value) {
        return new ValuePatterns(MatchType.EXACT, value);
    }

    // prefixes have to be given as strings, which with our semantics means they will be enclosed in "
    //  characters, like so: "\"foo\"".  We need the starting " preserved, because that's how we pass
    //  string field values around. But we have to amputate the last one because it'll break the prefix
    //  semantics.
    public static ValuePatterns prefixMatch(final String prefix) {
        return new ValuePatterns(MatchType.PREFIX, prefix);
    }

    public static ValuePatterns prefixEqualsIgnoreCaseMatch(final String prefix) {
        return new ValuePatterns(MatchType.PREFIX_EQUALS_IGNORE_CASE, prefix);
    }

    public static ValuePatterns suffixMatch(final String suffix) {
        return new ValuePatterns(MatchType.SUFFIX, new StringBuilder(suffix).reverse().toString());
    }

    public static ValuePatterns suffixEqualsIgnoreCaseMatch(final String suffix) {
        return new ValuePatterns(MatchType.SUFFIX_EQUALS_IGNORE_CASE, new StringBuilder(suffix).reverse().toString());
    }

    public static AnythingBut anythingButMatch(final String anythingBut) {
        return new AnythingBut(Collections.singleton(anythingBut), false);
    }

    public static AnythingBut anythingButMatch(final double anythingBut) {
        return new AnythingBut(Collections.singleton(ComparableNumber.generate(anythingBut)), true);
    }

    public static AnythingBut anythingButMatch(final Set<String> anythingButs) {
        return new AnythingBut(anythingButs, false);
    }

    public static AnythingButValuesSet anythingButIgnoreCaseMatch(final String anythingBut) {
        return new AnythingButValuesSet(MatchType.ANYTHING_BUT_IGNORE_CASE, Collections.singleton(anythingBut));
    }

    public static AnythingButValuesSet anythingButIgnoreCaseMatch(final Set<String> anythingButs) {
        return new AnythingButValuesSet(MatchType.ANYTHING_BUT_IGNORE_CASE, anythingButs);
    }

    public static AnythingBut anythingButNumberMatch(final Set<Double> anythingButs) {
        Set<String> normalizedNumbers = new HashSet<>(anythingButs.size());
        for (Double d : anythingButs) {
            normalizedNumbers.add(ComparableNumber.generate(d));
        }
        return new AnythingBut(normalizedNumbers, true);
    }

    public static AnythingButValuesSet anythingButPrefix(final String prefix) {
        return new AnythingButValuesSet(MatchType.ANYTHING_BUT_PREFIX, Collections.singleton(prefix));
    }

    public static AnythingButValuesSet anythingButPrefix(final Set<String> anythingButs) {
        return new AnythingButValuesSet(MatchType.ANYTHING_BUT_PREFIX, anythingButs);
    }

    public static AnythingButValuesSet anythingButSuffix(final String suffix) {
        return new AnythingButValuesSet(MatchType.ANYTHING_BUT_SUFFIX,
                Collections.singleton(new StringBuilder(suffix).reverse().toString()));
    }

    public static AnythingButValuesSet anythingButSuffix(final Set<String> anythingButs) {
        return new AnythingButValuesSet(MatchType.ANYTHING_BUT_SUFFIX,
                anythingButs.stream().map(s -> new StringBuilder(s).reverse().toString()).collect(Collectors.toSet()));
    }

    public static AnythingButValuesSet anythingButWildcard(final String value) {
        return new AnythingButValuesSet(MatchType.ANYTHING_BUT_WILDCARD, Collections.singleton(value));
    }

    public static AnythingButValuesSet anythingButWildcard(final Set<String> anythingButs) {
        return new AnythingButValuesSet(MatchType.ANYTHING_BUT_WILDCARD, anythingButs);
    }

    public static ValuePatterns numericEquals(final double val) {
        return new ValuePatterns(MatchType.NUMERIC_EQ, ComparableNumber.generate(val));
    }

    public static Patterns existencePatterns() {
        return new Patterns(MatchType.EXISTS);
    }

    public static Patterns absencePatterns() {
        return new Patterns(MatchType.ABSENT);
    }

    // Implement equals-ignore-case by doing lower-case comparisons
    public static ValuePatterns equalsIgnoreCaseMatch(final String value) {
        return new ValuePatterns(MatchType.EQUALS_IGNORE_CASE, value);
    }

    public static ValuePatterns wildcardMatch(final String value) {
        return new ValuePatterns(MatchType.WILDCARD, value);
    }

    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            return new Patterns(this.type);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || !o.getClass().equals(getClass())) {
            return false;
        }

        Patterns patterns = (Patterns) o;

        return type == patterns.type;
    }

    @Override
    public int hashCode() {
        return type != null ? type.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "T:" + type;
    }
}
