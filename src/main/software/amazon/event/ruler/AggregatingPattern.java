package software.amazon.event.ruler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** FIXME docs throughout the class
 * Represents a range of numeric values to match against.
 * "Numeric" means that the character repertoire is "digits"; initially, either 0-9 or 0-9a-f. In the current
 *  implementation, the number of digits in the top and bottom of the range is the same.
 */
public final class AggregatingPattern extends Patterns { // Pattern that aggregates a bunch of other patterns
    /**
     * Bottom and top of the range. openBottom true means we're looking for > bottom, false means >=
     *  Similarly, openTop true means we're looking for < top, false means <= top.
     */
    final List<List<Map<String, List<Patterns>>>> patterns;

    private AggregatingPattern(List<List<Map<String, List<Patterns>>>> patterns) {
        super(MatchType.AND); // Technically can use this for OR & NOT as well.
        if (patterns.size() <= 1) {
            throw new IllegalArgumentException("Must more than one patterns, Current size is " + patterns.size());
        }
        this.patterns = patterns;
    }

    private AggregatingPattern(AggregatingPattern pattern) {
        super(MatchType.AND);
        this.patterns = deepCopy(pattern.patterns);
    }

    private static List<List<Map<String, List<Patterns>>>> deepCopy(final List<List<Map<String, List<Patterns>>>> rules) { // FIXME reusable utility
        return rules.stream().map(AggregatingPattern::deepCopyRuleLists).collect(Collectors.toList());
    }
    private static List<Map<String, List<Patterns>>> deepCopyRuleLists(final List<Map<String, List<Patterns>>> rules) { // FIXME reusable utility
        return rules.stream().map(AggregatingPattern::deepCopyEachRule).collect(Collectors.toList());
    }

    private static Map<String, List<Patterns>> deepCopyEachRule(Map<String, List<Patterns>> rule) {
        return new HashMap<>(rule);
    }

    private static AggregatingPattern deepCopy(final AggregatingPattern range) {
        return new AggregatingPattern(range);
    }

    public static AggregatingPattern and(List<List<Map<String, List<Patterns>>>> patterns) {
        return new AggregatingPattern(patterns);
    }

    public List<List<Map<String, List<Patterns>>>> getValues() {
        return patterns;
    }

    @Override
    public Object clone() {
        super.clone();
        return AggregatingPattern.deepCopy(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || !o.getClass().equals(getClass())) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        AggregatingPattern value = (AggregatingPattern) o;

        return Objects.equals(patterns, value.patterns);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + Objects.hashCode(patterns);
        return result;
    }

    public String toString() {
        return "AND: ["+ patterns + ", (" + super.toString() + ")]";

    }
}
