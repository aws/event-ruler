package software.amazon.event.ruler;

import javax.annotation.Nonnull;

/**
 * Matches the Keys in the flattened event with the { [ "exists" : false ] } pattern.
 * and returns the next state if the <b>key does not exist</b> in the event.
 *
 * In the future, when we support multiple patterns, the matcher will return a set of
 * name states matched instead of a single name state.
 *
 * @param <R> generic state type
 */
public interface NameMatcher<R> {

    /**
     * Returns {@code true} if this name matcher contains no patterns.
     */
    boolean isEmpty();

    /**
     * Adds the given pattern to this name matcher and associate it with the existing or new match result.
     *
     * @param pattern   the pattern to be added
     * @param nameState the namestate
     * @return the match result with which the added pattern is associated
     */
    R addPattern(@Nonnull Patterns pattern, @Nonnull NameState nameState);

    /**
     * Removes the given pattern from this name matcher.
     *
     * @param pattern the pattern to be deleted
     */
    void deletePattern(@Nonnull Patterns pattern);

    /**
     * Looks up for the given pattern.
     *
     * @param pattern the pattern to be looked up for
     * @return the match result that is associated with the given pattern if the pattern exists; otherwise {@code null}
     */
    R findPattern(@Nonnull Patterns pattern);

    /**
     * Gets the next state to transition to in case the NameMatcher matches the event.
     */
    R getNextState();
}
