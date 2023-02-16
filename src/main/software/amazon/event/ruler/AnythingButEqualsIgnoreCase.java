package software.amazon.event.ruler;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Represents denylist like rule: any value matches if it's *not* in the anything-but/ignore-case list.
 * It supports lists whose members must be all strings.
 * Matching is case-insensitive
 */
public class AnythingButEqualsIgnoreCase extends Patterns {

    private final Set<String> values;

    AnythingButEqualsIgnoreCase(final Set<String> values) {
        super(MatchType.ANYTHING_BUT_IGNORE_CASE);
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

        AnythingButEqualsIgnoreCase that = (AnythingButEqualsIgnoreCase) o;

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
        return "ABIC:"+ values + ", (" + super.toString() + ")";
    }
}
