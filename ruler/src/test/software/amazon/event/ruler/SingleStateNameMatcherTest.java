package software.amazon.event.ruler;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class SingleStateNameMatcherTest {

    private SingleStateNameMatcher nameMatcher = new SingleStateNameMatcher();

    private final NameState nameState = new NameState();

    @Before
    public void setup() {
        nameMatcher.addPattern(Patterns.absencePatterns(), nameState);
    }

    @After
    public void teardown() {
        nameMatcher.deletePattern(Patterns.absencePatterns());
    }

    @Test
    public void testInsertingSamePatternTwice_returnsThePreviouslyAddedNameState() {
        NameState anotherNameState = new NameState();

        NameState state = nameMatcher.addPattern(Patterns.absencePatterns(), anotherNameState);
        assertThat(state, is(equalTo(nameState)));
    }

    @Test
    public void testInsertingNewStateAfterDeletingState_acceptsNewState() {
        nameMatcher.deletePattern(Patterns.absencePatterns());

        NameState anotherNameState = new NameState();
        NameState state = nameMatcher.addPattern(Patterns.absencePatterns(), anotherNameState);
        assertThat(state, is(equalTo(anotherNameState)));
    }

    @Test
    public void testDeletingNameStateFromEmptyMatcher_HasNoEffect() {
        nameMatcher.deletePattern(Patterns.absencePatterns());

        assertThat(nameMatcher.isEmpty(), is(true));
        // delete same state again
        nameMatcher.deletePattern(Patterns.absencePatterns());
    }

    @Test
    public void testFindPattern() {
        NameState state = nameMatcher.findPattern(Patterns.absencePatterns());
        assertThat(state, is(equalTo(nameState)));

        nameMatcher.deletePattern(Patterns.absencePatterns());

        state = nameMatcher.findPattern(Patterns.absencePatterns());
        assertThat(state, is(nullValue()));
    }
}