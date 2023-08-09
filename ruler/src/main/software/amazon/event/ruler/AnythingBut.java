package software.amazon.event.ruler;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Represents denylist like rule: any value matches if it's *not* in the anything-but list.
 * It supports lists whose members must be all strings or all numbers.
 * Numbers are treated as strings created with ComparableNumber.generate.
 */
public class AnythingBut extends Patterns {

    private final Set<String> values;

    // isNumeric: true means all the value in the Set are numbers; false means all the value are string.
    private final boolean isNumeric;

    AnythingBut(final Set<String> values, final boolean isNumeric) {
        super(MatchType.ANYTHING_BUT);
        this.values = Collections.unmodifiableSet(values);
        this.isNumeric = isNumeric;
    }

    static AnythingBut anythingButMatch(final Set<String> values, final boolean isNumber) {
        return new AnythingBut(values, isNumber);
    }

    public Set<String> getValues() {
        return values;
    }

    boolean isNumeric() {
        return isNumeric;
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

        AnythingBut that = (AnythingBut) o;

        return isNumeric == that.isNumeric && (Objects.equals(values, that.values));
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (values != null ? values.hashCode() : 0);
        result = 31 * result + (isNumeric ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return "AB:"+ values + ", isNum=" + isNumeric + " (" + super.toString() + ")";
    }
}
