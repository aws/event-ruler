package software.amazon.event.ruler;

import javax.annotation.Nonnull;

/**
 * Implements NameMatcher for the [ { "exists": false } ] pattern by keeping
 * the one next state that the pattern leads to.
 */
public class SingleStateNameMatcher implements NameMatcher<NameState> {

    /**
     * We only have one pattern for name matcher today. Hence, we can just keep
     * a single state here for simplicity. In the future, when multiple patterns
     * come here, we need a byte match.
     */
    private NameState nameState;

    @Override
    public boolean isEmpty() {
        return nameState == null;
    }

    @Override
    public NameState addPattern(@Nonnull final Patterns pattern, final NameState nameState) {
        if (this.nameState == null) {
            this.nameState = nameState;
        }

        return this.nameState;
    }

    @Override
    public void deletePattern(@Nonnull final Patterns pattern) {
        nameState = null;
    }

    @Override
    public NameState findPattern(@Nonnull final Patterns pattern) {
        return nameState;
    }

    @Override
    public NameState getNextState() {
        return nameState;
    }
}