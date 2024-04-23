package software.amazon.event.ruler;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** FIXME docs throughout the class
 * Represents a range of numeric values to match against.
 * "Numeric" means that the character repertoire is "digits"; initially, either 0-9 or 0-9a-f. In the current
 *  implementation, the number of digits in the top and bottom of the range is the same.
 */
public final class AndPattern extends Patterns {
    /**
     * Bottom and top of the range. openBottom true means we're looking for > bottom, false means >=
     *  Similarly, openTop true means we're looking for < top, false means <= top.
     */
    final List<List<Map<String, List<Patterns>>>> patterns;

    private AndPattern(List<List<Map<String, List<Patterns>>>> patterns) {
        super(MatchType.AND);
        if (patterns.size() <= 1) {
            throw new IllegalArgumentException("Must more than one patterns, Current size is " + patterns.size());
        }
        this.patterns = patterns;
    }

    private AndPattern(AndPattern pattern) {
        super(MatchType.AND);
        this.patterns = deepCopyRules(pattern.patterns);
    }

    private static List<List<Map<String, List<Patterns>>>> deepCopyRules(final List<List<Map<String, List<Patterns>>>> rules) { // FIXME reusable utility
        return rules.stream().map(AndPattern::deepCopyRules2).collect(Collectors.toList());
    }
    private static List<Map<String, List<Patterns>>> deepCopyRules2(final List<Map<String, List<Patterns>>> rules) { // FIXME reusable utility
        return rules.stream().map(AndPattern::deepCopy3).collect(Collectors.toList());
    }

    private static Map<String, List<Patterns>> deepCopy3(Map<String, List<Patterns>> rule) {
        return new HashMap<>(rule);
    }

    private static AndPattern deepCopy(final AndPattern range) {
        return new AndPattern(range);
    }

    public static AndPattern and(List<List<Map<String, List<Patterns>>>> patterns) {
        return new AndPattern(patterns);
    }

    public List<List<Map<String, List<Patterns>>>> getValues() {
        return patterns;
    }

    @Override
    public Object clone() {
        super.clone();
        return AndPattern.deepCopy(this);
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

        AndPattern value = (AndPattern) o;

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
