package software.amazon.event.ruler;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class NameStateTest {

    @Test
    public void testAddSubRule() {
        NameState nameState = new NameState();
        nameState.addSubRule("rule1", 1.0, Patterns.exactMatch("a"), true);
        nameState.addSubRule("rule1", 2.0, Patterns.exactMatch("b"), true);
        nameState.addSubRule("rule2", 3.0, Patterns.exactMatch("a"), true);

        assertEquals(new HashSet<>(asList(1.0, 3.0)),
                nameState.getTerminalSubRuleIdsForPattern(Patterns.exactMatch("a")));
        assertEquals(new HashSet<>(asList(2.0)), nameState.getTerminalSubRuleIdsForPattern(Patterns.exactMatch("b")));
        assertNull(nameState.getNonTerminalSubRuleIdsForPattern(Patterns.exactMatch("a")));
    }

    @Test
    public void testDeleteSubRule() {
        NameState nameState = new NameState();
        nameState.addSubRule("rule1", 1.0, Patterns.exactMatch("a"), true);
        nameState.addSubRule("rule2", 2.0, Patterns.exactMatch("b"), true);

        assertEquals(new HashSet<>(asList(1.0)), nameState.getTerminalSubRuleIdsForPattern(Patterns.exactMatch("a")));
        assertEquals(new HashSet<>(asList(2.0)), nameState.getTerminalSubRuleIdsForPattern(Patterns.exactMatch("b")));
        nameState.deleteSubRule(1.0, Patterns.exactMatch("b"), true);
        assertEquals(new HashSet<>(asList(1.0)), nameState.getTerminalSubRuleIdsForPattern(Patterns.exactMatch("a")));
        assertEquals(new HashSet<>(asList(2.0)), nameState.getTerminalSubRuleIdsForPattern(Patterns.exactMatch("b")));
        nameState.deleteSubRule(2.0, Patterns.exactMatch("b"), true);
        assertEquals(new HashSet<>(asList(1.0)), nameState.getTerminalSubRuleIdsForPattern(Patterns.exactMatch("a")));
        assertNull(nameState.getTerminalSubRuleIdsForPattern(Patterns.exactMatch("b")));
        nameState.deleteSubRule(2.0, Patterns.exactMatch("a"), true);
        assertEquals(new HashSet<>(asList(1.0)), nameState.getTerminalSubRuleIdsForPattern(Patterns.exactMatch("a")));
        assertNull(nameState.getTerminalSubRuleIdsForPattern(Patterns.exactMatch("b")));
        nameState.deleteSubRule(1.0, Patterns.exactMatch("a"), false);
        assertEquals(new HashSet<>(asList(1.0)), nameState.getTerminalSubRuleIdsForPattern(Patterns.exactMatch("a")));
        assertNull(nameState.getTerminalSubRuleIdsForPattern(Patterns.exactMatch("b")));
        nameState.deleteSubRule(1.0, Patterns.exactMatch("a"), true);
        assertNull(nameState.getTerminalSubRuleIdsForPattern(Patterns.exactMatch("a")));
        assertNull(nameState.getTerminalSubRuleIdsForPattern(Patterns.exactMatch("b")));
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
    public void testGetNonTerminalPatterns() {
        NameState nameState = new NameState();
        nameState.addSubRule("rule1", 1.0, Patterns.exactMatch("a"), false);
        nameState.addSubRule("rule1", 2.0, Patterns.exactMatch("b"), true);
        nameState.addSubRule("rule2", 3.0, Patterns.exactMatch("a"), true);
        nameState.addSubRule("rule3", 4.0, Patterns.exactMatch("c"), false);

        Set<Patterns> expectedPatterns = new HashSet<>(Arrays.asList(
                Patterns.exactMatch("a"), Patterns.exactMatch("c")));
        assertEquals(expectedPatterns, nameState.getNonTerminalPatterns());
    }

    @Test
    public void testGetTerminalSubRuleIdsForPattern() {
        NameState nameState = new NameState();
        nameState.addSubRule("rule1", 1.0, Patterns.exactMatch("a"), false);
        nameState.addSubRule("rule1", 2.0, Patterns.exactMatch("a"), true);
        nameState.addSubRule("rule2", 3.0, Patterns.exactMatch("b"), false);
        nameState.addSubRule("rule3", 4.0, Patterns.exactMatch("a"), true);

        assertEquals(new HashSet<>(Arrays.asList(2.0, 4.0)),
                nameState.getTerminalSubRuleIdsForPattern(Patterns.exactMatch("a")));
    }

    @Test
    public void testGetNonTerminalSubRuleIdsForPattern() {
        NameState nameState = new NameState();
        nameState.addSubRule("rule1", 1.0, Patterns.exactMatch("a"), true);
        nameState.addSubRule("rule1", 2.0, Patterns.exactMatch("a"), false);
        nameState.addSubRule("rule2", 3.0, Patterns.exactMatch("a"), false);
        nameState.addSubRule("rule3", 4.0, Patterns.exactMatch("b"), false);

        assertEquals(new HashSet<>(Arrays.asList(2.0, 3.0)),
                nameState.getNonTerminalSubRuleIdsForPattern(Patterns.exactMatch("a")));
    }

    @Test
    public void testContainsRule() {
        NameState nameState = new NameState();
        nameState.addSubRule("rule1", 1.0, Patterns.exactMatch("a"), true);
        nameState.addSubRule("rule2", 2.0, Patterns.exactMatch("a"), false);
        nameState.addSubRule("rule1", 2.0, Patterns.exactMatch("b"), false);

        assertTrue(nameState.containsRule("rule1", Patterns.exactMatch("a")));
        assertTrue(nameState.containsRule("rule2", Patterns.exactMatch("a")));
        assertTrue(nameState.containsRule("rule1", Patterns.exactMatch("b")));
        assertFalse(nameState.containsRule("rule3", Patterns.exactMatch("a")));
        assertFalse(nameState.containsRule("rule1", Patterns.exactMatch("c")));
    }
}
