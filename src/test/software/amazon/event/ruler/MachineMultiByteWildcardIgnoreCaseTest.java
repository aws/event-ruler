package software.amazon.event.ruler;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class MachineMultiByteWildcardIgnoreCaseTest {

    private static final String WILDCARD_RULE = "wildcard-rule";
    private static final String IGNORE_CASE_RULE = "ignore-case-rule";
    private static final String KELVIN_SIGN = "\u212A";

    @Parameters(name = "{0} {1} {3}")
    public static Collection<Object[]> patternsAndValues() {
        return Arrays.asList(
                row("*é", "equals", equals("éx"), "zéx", Collections.emptyList()),
                row("*é", "prefix", prefix("éx"), "zéxq", Collections.emptyList()),
                row("*é", "suffix", suffix("éx"), "éy", Collections.emptyList()),
                row("*é", "anything-but", anythingBut("éx"), "zéx", Collections.singletonList(IGNORE_CASE_RULE)),
                row("*д", "equals", equals("дx"), "zдx", Collections.emptyList()),
                row("*д", "suffix", suffix("дx"), "дy", Collections.emptyList()),
                row("*ａ", "equals", equals("ａx"), "zａx", Collections.emptyList()),
                row("*ａ", "suffix", suffix("ａx"), "ａy", Collections.emptyList()),
                row("*" + KELVIN_SIGN, "equals", equals(KELVIN_SIGN + "x"), "zkx", Collections.emptyList()),
                row("*" + KELVIN_SIGN, "suffix", suffix(KELVIN_SIGN + "x"), "ky", Collections.emptyList()),
                row("*k", "equals", equals(KELVIN_SIGN + "x"), "zkx", Collections.emptyList()),
                row("*ı", "equals", equals("ıx"), "zıx", Collections.emptyList()),
                row("*ı", "suffix", suffix("ıx"), "ıy", Collections.emptyList()),
                row("*I", "equals", equals("ıx"), "zIx", Collections.emptyList()),
                row("*次", "equals", equals("次x"), "z次x", Collections.emptyList()),
                row("*次", "suffix", suffix("次x"), "次y", Collections.emptyList())
        );
    }

    private static Object[] row(final String wildcard, final String label, final String ignoreCasePattern,
                                final String value, final List<String> expectedRules) {
        return new Object[] {wildcard, label, ignoreCasePattern, value, expectedRules};
    }

    private static String equals(final String value) {
        return "{\"equals-ignore-case\": \"" + value + "\"}";
    }

    private static String prefix(final String value) {
        return "{\"prefix\": {\"equals-ignore-case\": \"" + value + "\"}}";
    }

    private static String suffix(final String value) {
        return "{\"suffix\": {\"equals-ignore-case\": \"" + value + "\"}}";
    }

    private static String anythingBut(final String value) {
        return "{\"anything-but\": {\"equals-ignore-case\": \"" + value + "\"}}";
    }

    private final String wildcardPattern;
    private final String ignoreCasePattern;
    private final String value;
    private final List<String> expectedRules;

    public MachineMultiByteWildcardIgnoreCaseTest(final String wildcard, final String label,
                                                  final String ignoreCasePattern, final String value,
                                                  final List<String> expectedRules) {
        this.wildcardPattern = "{\"a\": [{\"wildcard\": \"" + wildcard + "\"}]}";
        this.ignoreCasePattern = "{\"a\": [" + ignoreCasePattern + "]}";
        this.value = value;
        this.expectedRules = expectedRules;
    }

    @Test
    public void wildcardRuleAddedFirstDoesNotChangeIgnoreCaseMatches() throws Exception {
        final Machine machine = new Machine();
        machine.addRule(WILDCARD_RULE, wildcardPattern);
        machine.addRule(IGNORE_CASE_RULE, ignoreCasePattern);

        assertEquals(expectedRules, machine.rulesForJSONEvent("{\"a\": \"" + value + "\"}"));
    }

    @Test
    public void wildcardRuleAddedLastDoesNotChangeIgnoreCaseMatches() throws Exception {
        final Machine machine = new Machine();
        machine.addRule(IGNORE_CASE_RULE, ignoreCasePattern);
        machine.addRule(WILDCARD_RULE, wildcardPattern);

        assertEquals(expectedRules, machine.rulesForJSONEvent("{\"a\": \"" + value + "\"}"));
    }
}
