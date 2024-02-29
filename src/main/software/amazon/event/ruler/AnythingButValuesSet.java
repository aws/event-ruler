package software.amazon.event.ruler;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Represents denylist like rule: any value matches if it's *not* in the anything-but set.
 * It supports lists whose members must be all strings.
 * This can be used for anything-but-equals-ignore-case, anything-but-prefix, and anything-but-suffix.
 */
public class AnythingButValuesSet extends Patterns {

    private final Set<String> values;

    AnythingButValuesSet(final MatchType matchType, final Set<String> values) {
        super(matchType);
        this.values = Collections.unmodifiableSet(values);
    }

    public Set<String> getValues() {
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

        AnythingButValuesSet that = (AnythingButValuesSet) o;

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
        return "ABVS:"+ values + ", (" + super.toString() + ")";
    }
}
