package software.amazon.event.ruler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

// FIXME docs and maybe make this a logicalPattern to support $or and $not
public class AndPattern extends Patterns {
    private final List<Map<String, List<Patterns>>> values;

    public AndPattern(final MatchType matchType, final List<Map<String, List<Patterns>>> values) {
        super(matchType);
        this.values = deepCopyRules(values);
    }

    // This is not exactly the real deep copy because here we only need deep copy at List element level,
    private static List deepCopyRules(final List<Map<String, List<Patterns>>> rules) {
        return rules.stream().map(rule -> new HashMap(rule)).collect(Collectors.toList());
    }

    public List<Map<String, List<Patterns>>> getValues() {
        return values;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        AndPattern that = (AndPattern) o;
        return (Objects.equals(values, that.values));
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (values != null ? values.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "LP:" + values + ", (" + super.toString() + ")";
    }
}
