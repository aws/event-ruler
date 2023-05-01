package software.amazon.event.ruler;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NameStateTest {

    @Test
    public void testAddSubRule() {
        NameState nameState = new NameState();
        nameState.addSubRule("rule1", 1.0, Patterns.exactMatch("a"), true);
        nameState.addSubRule("rule1", 2.0, Patterns.exactMatch("b"), true);
        nameState.addSubRule("rule2", 3.0, Patterns.exactMatch("a"), true);

        assertEquals(new HashSet<>(asList(1.0, 3.0)), nameState.getSubRuleIds(Patterns.exactMatch("a"), true));
        assertEquals(new HashSet<>(asList(2.0)), nameState.getSubRuleIds(Patterns.exactMatch("b"), true));
        assertEquals(new HashSet<>(), nameState.getSubRuleIds(Patterns.exactMatch("c"), true));
        // Implementation detail: non-terminal returns null instead of empty set
        assertEquals(null, nameState.getSubRuleIds(Patterns.exactMatch("a"), false));
        assertEquals(null, nameState.getSubRuleIds(Patterns.exactMatch("b"), false));
    }

    @Test
    public void testDeleteSubRule() {
        NameState nameState = new NameState();
        nameState.addSubRule("rule1", 1.0, Patterns.exactMatch("a"), true);

        assertEquals(new HashSet<>(asList(1.0)), nameState.getSubRuleIds(Patterns.exactMatch("a"), true));
        assertFalse(nameState.deleteSubRule("rule1", 1.0, Patterns.exactMatch("b"), true));
        assertEquals(new HashSet<>(asList(1.0)), nameState.getSubRuleIds(Patterns.exactMatch("a"), true));
        assertFalse(nameState.deleteSubRule("rule2", 1.0, Patterns.exactMatch("a"), true));
        assertEquals(new HashSet<>(asList(1.0)), nameState.getSubRuleIds(Patterns.exactMatch("a"), true));
        assertFalse(nameState.deleteSubRule("rule1", 2.0, Patterns.exactMatch("a"), true));
        assertEquals(new HashSet<>(asList(1.0)), nameState.getSubRuleIds(Patterns.exactMatch("a"), true));
        assertFalse(nameState.deleteSubRule("rule2", 1.0, Patterns.exactMatch("a"), false));
        assertEquals(new HashSet<>(asList(1.0)), nameState.getSubRuleIds(Patterns.exactMatch("a"), true));
        assertTrue(nameState.deleteSubRule("rule1", 1.0, Patterns.exactMatch("a"), true));
        assertEquals(new HashSet<>(), nameState.getSubRuleIds(Patterns.exactMatch("a"), true));
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

        Set<Double> expectedSubRuleIds = new HashSet<>(Arrays.asList(2.0, 3.0));
        assertEquals(expectedSubRuleIds, nameState.getNonTerminalSubRuleIdsForPattern(Patterns.exactMatch("a")));
    }

    @Test
    public void testGetSubRuleIdsForNonTerminal() {
        NameState nameState = new NameState();
        nameState.addSubRule("rule1", 1.0, Patterns.exactMatch("a"), true);
        nameState.addSubRule("rule1", 2.0, Patterns.exactMatch("a"), false);
        nameState.addSubRule("rule2", 3.0, Patterns.exactMatch("a"), false);
        nameState.addSubRule("rule1", 4.0, Patterns.exactMatch("b"), false);
        nameState.addSubRule("rule1", 5.0, Patterns.exactMatch("a"), false);

        Set<Double> expectedSubRuleIds = new HashSet<>(Arrays.asList(2.0, 3.0, 5.0));
        assertEquals(expectedSubRuleIds, nameState.getSubRuleIds(Patterns.exactMatch("a"), false));
    }

    @Test
    public void testGetSubRuleIdsForTerminal() {
        NameState nameState = new NameState();
        nameState.addSubRule("rule1", 1.0, Patterns.exactMatch("a"), true);
        nameState.addSubRule("rule1", 2.0, Patterns.exactMatch("a"), false);
        nameState.addSubRule("rule2", 3.0, Patterns.exactMatch("a"), true);
        nameState.addSubRule("rule1", 4.0, Patterns.exactMatch("b"), false);
        nameState.addSubRule("rule1", 5.0, Patterns.exactMatch("a"), true);

        Set<Double> expectedSubRuleIds = new HashSet<>(Arrays.asList(1.0, 3.0, 5.0));
        assertEquals(expectedSubRuleIds, nameState.getSubRuleIds(Patterns.exactMatch("a"), true));
    }
}
