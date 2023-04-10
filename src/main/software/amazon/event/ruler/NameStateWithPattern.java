package software.amazon.event.ruler;

import javax.annotation.Nullable;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Class that holds a NameState and the Pattern from the match that led to the NameState.
 */
public class NameStateWithPattern {

    private final NameState nameState;
    private final Patterns pattern;

    /**
     * Create a NameStateWithPattern.
     *
     * @param nameState The NameState - cannot be null.
     * @param pattern The pattern - can be null if the NameState is the starting state of a Machine.
     */
    public NameStateWithPattern(NameState nameState, @Nullable Patterns pattern) {
        this.nameState = requireNonNull(nameState);
        this.pattern = pattern;
    }

    public NameState getNameState() {
        return nameState;
    }

    public Patterns getPattern() {
        return pattern;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof NameStateWithPattern)) {
            return false;
        }
        NameStateWithPattern otherNameStateWithPattern = (NameStateWithPattern) o;
        return  nameState.equals(otherNameStateWithPattern.nameState) &&
                pattern.equals(otherNameStateWithPattern.pattern);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nameState, pattern);
    }
}