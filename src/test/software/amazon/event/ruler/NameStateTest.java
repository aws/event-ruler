package software.amazon.event.ruler;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;

public class NameStateTest {

    @Test
    public void tesAddSubRule() {
        NameState nameState = new NameState();
        nameState.addSubRule("rule1", 1.0, Patterns.exactMatch("a"), true);
        nameState.addSubRule("rule1", 2.0, Patterns.exactMatch("b"), true);
        nameState.addSubRule("rule2", 3.0, Patterns.exactMatch("a"), true);

        assertEquals(new HashSet<>(asList(1.0)), nameState.getSubRuleIds("rule1", Patterns.exactMatch("a"), true));
        assertEquals(new HashSet<>(asList(2.0)), nameState.getSubRuleIds("rule1", Patterns.exactMatch("b"), true));
        assertEquals(new HashSet<>(asList(3.0)), nameState.getSubRuleIds("rule2", Patterns.exactMatch("a"), true));
        assertEquals(new HashSet<>(), nameState.getSubRuleIds("rule1", Patterns.exactMatch("c"), true));
        assertEquals(new HashSet<>(), nameState.getSubRuleIds("rule2", Patterns.exactMatch("b"), true));
        assertEquals(new HashSet<>(), nameState.getSubRuleIds("rule3", Patterns.exactMatch("a"), true));
        assertEquals(new HashSet<>(), nameState.getSubRuleIds("rule3", Patterns.exactMatch("b"), true));
        assertEquals(new HashSet<>(), nameState.getSubRuleIds("rule1", Patterns.exactMatch("a"), false));
        assertEquals(new HashSet<>(), nameState.getSubRuleIds("rule1", Patterns.exactMatch("b"), false));
        assertEquals(new HashSet<>(), nameState.getSubRuleIds("rule2", Patterns.exactMatch("a"), false));
    }

    @Test
    public void testDeleteSubRule() {
        NameState nameState = new NameState();
        nameState.addSubRule("rule1", 1.0, Patterns.exactMatch("a"), true);

        assertEquals(new HashSet<>(asList(1.0)), nameState.getSubRuleIds("rule1", Patterns.exactMatch("a"), true));
        nameState.deleteSubRule("rule1", 1.0, Patterns.exactMatch("b"), true);
        assertEquals(new HashSet<>(asList(1.0)), nameState.getSubRuleIds("rule1", Patterns.exactMatch("a"), true));
        nameState.deleteSubRule("rule2", 1.0, Patterns.exactMatch("a"), true);
        assertEquals(new HashSet<>(asList(1.0)), nameState.getSubRuleIds("rule1", Patterns.exactMatch("a"), true));
        nameState.deleteSubRule("rule1", 2.0, Patterns.exactMatch("a"), true);
        assertEquals(new HashSet<>(asList(1.0)), nameState.getSubRuleIds("rule1", Patterns.exactMatch("a"), true));
        nameState.deleteSubRule("rule2", 1.0, Patterns.exactMatch("a"), false);
        assertEquals(new HashSet<>(asList(1.0)), nameState.getSubRuleIds("rule1", Patterns.exactMatch("a"), true));
        nameState.deleteSubRule("rule1", 1.0, Patterns.exactMatch("a"), true);
        assertEquals(new HashSet<>(), nameState.getSubRuleIds("rule1", Patterns.exactMatch("a"), true));
    }

    @Test
    public void testGetTerminalPatterns() {
        NameState nameState = new NameState();
        nameState.addSubRule("rule1", 1.0, Patterns.exactMatch("a"), true);
        nameState.addSubRule("rule1", 2.0, Patterns.exactMatch("b"), false);
        nameState.addSubRule("rule2", 3.0, Patterns.exactMatch("a"), false);
        nameState.addSubRule("rule3", 4.0, Patterns.exactMatch("c"), true);

        Set<Patterns> expectedPatterns = new HashSet<>(Arrays.asList(
                Patterns.exactMatch("a"), Patterns.exactMatch("c")));
        assertEquals(expectedPatterns, nameState.getTerminalPatterns());
    }

    @Test
    public void testGetTerminalSubRulesForPattern() {
        NameState nameState = new NameState();
        nameState.addSubRule("rule1", 1.0, Patterns.exactMatch("a"), false);
        nameState.addSubRule("rule1", 2.0, Patterns.exactMatch("a"), true);
        nameState.addSubRule("rule2", 3.0, Patterns.exactMatch("b"), false);
        nameState.addSubRule("rule3", 4.0, Patterns.exactMatch("a"), true);

        Set<NameState.SubRule> expectedSubRules = new HashSet<>(Arrays.asList(
                new NameState.SubRule("rule1", 2.0), new NameState.SubRule("rule3", 4.0)));
        assertEquals(expectedSubRules, nameState.getTerminalSubRulesForPattern(Patterns.exactMatch("a")));
    }

    @Test
    public void testGetNonTerminalSubRulesForPattern() {
        NameState nameState = new NameState();
        nameState.addSubRule("rule1", 1.0, Patterns.exactMatch("a"), true);
        nameState.addSubRule("rule1", 2.0, Patterns.exactMatch("a"), false);
        nameState.addSubRule("rule2", 3.0, Patterns.exactMatch("a"), false);
        nameState.addSubRule("rule3", 4.0, Patterns.exactMatch("b"), false);

        Set<NameState.SubRule> expectedSubRules = new HashSet<>(Arrays.asList(
                new NameState.SubRule("rule1", 2.0), new NameState.SubRule("rule2", 3.0)));
        assertEquals(expectedSubRules, nameState.getNonTerminalSubRulesForPattern(Patterns.exactMatch("a")));
    }

    @Test
    public void testGetSubRuleIds() {
        NameState nameState = new NameState();
        nameState.addSubRule("rule1", 1.0, Patterns.exactMatch("a"), true);
        nameState.addSubRule("rule1", 2.0, Patterns.exactMatch("a"), false);
        nameState.addSubRule("rule2", 3.0, Patterns.exactMatch("a"), false);
        nameState.addSubRule("rule1", 4.0, Patterns.exactMatch("b"), false);
        nameState.addSubRule("rule1", 5.0, Patterns.exactMatch("a"), false);

        Set<Double> expectedSubRuleIds = new HashSet<>(Arrays.asList(2.0, 5.0));
        assertEquals(expectedSubRuleIds, nameState.getSubRuleIds("rule1", Patterns.exactMatch("a"), false));
    }
}
