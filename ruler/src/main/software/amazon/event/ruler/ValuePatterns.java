package software.amazon.event.ruler;

import java.util.Objects;

/**
 * The ValuePatterns deal with matching a single value. The single value
 * is specified with the variable pattern.
 */
public class ValuePatterns extends Patterns {

    private final String pattern;

    ValuePatterns(final MatchType type, final String pattern) {
        super(type);
        this.pattern = pattern;
    }

    public String pattern() {
        return pattern;
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

        ValuePatterns that = (ValuePatterns) o;

        return Objects.equals(pattern, that.pattern);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (pattern != null ? pattern.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "VP:" + pattern + " (" + super.toString() + ")";
    }
}
