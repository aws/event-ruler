package software.amazon.event.ruler;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

public class PatternsTest {

    @Test
    public void WHEN_MatcherIsInitialized_THEN_GettersWork() {
        ValuePatterns cut = Patterns.exactMatch("foo");
        assertEquals("foo", cut.pattern());
        assertEquals(MatchType.EXACT, cut.type());
    }

    @Test
    public void WHEN_Different_Patterns_Call_Pattern_Then_Work() {

        List<Patterns> patternsList = new ArrayList<>();
        patternsList.add(Patterns.absencePatterns());
        patternsList.add(Patterns.existencePatterns());
        patternsList.add(Patterns.anythingButMatch(Stream.of("a", "b").collect(Collectors.toSet())));
        patternsList.add(Patterns.anythingButMatch(1.23));
        patternsList.add(Patterns.exactMatch("ab"));
        patternsList.add(Patterns.numericEquals(1.23));
        patternsList.add(Patterns.prefixMatch("abc"));
        patternsList.add(Patterns.suffixMatch("zyx"));
        patternsList.add(Patterns.equalsIgnoreCaseMatch("hElLo"));
        patternsList.add(Patterns.wildcardMatch("wild*card"));
        patternsList.add(Range.between(1.1, true, 2.2, false));

        String [] expect = {
                null,
                null,
                null,
                null,
                "ab",
                "11C37937F344B0",
                "abc",
                "xyz",
                "hElLo",
                "wild*card",
                null
        };

        for(int i = 0; i < expect.length; i++) {
            assertEquals(expect[i], patternsList.get(i).pattern());
        }
    }

}