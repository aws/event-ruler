package software.amazon.event.ruler;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class NameStateTest {

    final SubRuleContext c1 = new SubRuleContext(1, "c1");
    final SubRuleContext c2 = new SubRuleContext(2, "c2");
    final SubRuleContext c3 = new SubRuleContext(3, "c3");
    final SubRuleContext c4 = new SubRuleContext(4, "c4");

    @Test
    public void testAddSubRule() {
        NameState nameState = new NameState();
        nameState.addSubRule("rule1", c1, Patterns.exactMatch("a"), true);
        nameState.addSubRule("rule1", c2, Patterns.exactMatch("b"), true);
        nameState.addSubRule("rule2", c3, Patterns.exactMatch("a"), true);

        assertEquals(new HashSet<>(asList(c1, c3)),
                nameState.getTerminalSubRuleIdsForPattern(Patterns.exactMatch("a")));
        assertEquals(new HashSet<>(Collections.singletonList(c2)), nameState.getTerminalSubRuleIdsForPattern(Patterns.exactMatch("b")));
        assertNull(nameState.getNonTerminalSubRuleIdsForPattern(Patterns.exactMatch("a")));
    }

    @Test
    public void testDeleteSubRule() {
        NameState nameState = new NameState();
        nameState.addSubRule("rule1", c1, Patterns.exactMatch("a"), true);
        nameState.addSubRule("rule2", c2, Patterns.exactMatch("b"), true);

        assertEquals(new HashSet<>(Collections.singletonList(c1)), nameState.getTerminalSubRuleIdsForPattern(Patterns.exactMatch("a")));
        assertEquals(new HashSet<>(Collections.singletonList(c2)), nameState.getTerminalSubRuleIdsForPattern(Patterns.exactMatch("b")));
        nameState.deleteSubRule("rule1", c1, Patterns.exactMatch("b"), true);
        assertEquals(new HashSet<>(Collections.singletonList(c1)), nameState.getTerminalSubRuleIdsForPattern(Patterns.exactMatch("a")));
        assertEquals(new HashSet<>(Collections.singletonList(c2)), nameState.getTerminalSubRuleIdsForPattern(Patterns.exactMatch("b")));
        nameState.deleteSubRule("rule2", c2, Patterns.exactMatch("b"), true);
        assertEquals(new HashSet<>(Collections.singletonList(c1)), nameState.getTerminalSubRuleIdsForPattern(Patterns.exactMatch("a")));
        assertNull(nameState.getTerminalSubRuleIdsForPattern(Patterns.exactMatch("b")));
        nameState.deleteSubRule("rule2", c2, Patterns.exactMatch("a"), true);
        assertEquals(new HashSet<>(Collections.singletonList(c1)), nameState.getTerminalSubRuleIdsForPattern(Patterns.exactMatch("a")));
        assertNull(nameState.getTerminalSubRuleIdsForPattern(Patterns.exactMatch("b")));
        nameState.deleteSubRule("rule1", c1, Patterns.exactMatch("a"), false);
        assertEquals(new HashSet<>(Collections.singletonList(c1)), nameState.getTerminalSubRuleIdsForPattern(Patterns.exactMatch("a")));
        assertNull(nameState.getTerminalSubRuleIdsForPattern(Patterns.exactMatch("b")));
        nameState.deleteSubRule("rule1", c1, Patterns.exactMatch("a"), true);
        assertNull(nameState.getTerminalSubRuleIdsForPattern(Patterns.exactMatch("a")));
        assertNull(nameState.getTerminalSubRuleIdsForPattern(Patterns.exactMatch("b")));
    }

    @Test
    public void testGetTerminalPatterns() {
        NameState nameState = new NameState();
        nameState.addSubRule("rule1", c1, Patterns.exactMatch("a"), true);
        nameState.addSubRule("rule1", c2, Patterns.exactMatch("b"), false);
        nameState.addSubRule("rule2", c3, Patterns.exactMatch("a"), false);
        nameState.addSubRule("rule3", c4, Patterns.exactMatch("c"), true);

        Set<Patterns> expectedPatterns = new HashSet<>(Arrays.asList(
                Patterns.exactMatch("a"), Patterns.exactMatch("c")));
        assertEquals(expectedPatterns, nameState.getTerminalPatterns());
    }

    @Test
    public void testGetNonTerminalPatterns() {
        NameState nameState = new NameState();
        nameState.addSubRule("rule1", c1, Patterns.exactMatch("a"), false);
        nameState.addSubRule("rule1", c2, Patterns.exactMatch("b"), true);
        nameState.addSubRule("rule2", c3, Patterns.exactMatch("a"), true);
        nameState.addSubRule("rule3", c4, Patterns.exactMatch("c"), false);

        Set<Patterns> expectedPatterns = new HashSet<>(Arrays.asList(
                Patterns.exactMatch("a"), Patterns.exactMatch("c")));
        assertEquals(expectedPatterns, nameState.getNonTerminalPatterns());
    }

    @Test
    public void testGetTerminalSubRuleIdsForPattern() {
        NameState nameState = new NameState();
        nameState.addSubRule("rule1", c1, Patterns.exactMatch("a"), false);
        nameState.addSubRule("rule1", c2, Patterns.exactMatch("a"), true);
        nameState.addSubRule("rule2", c3, Patterns.exactMatch("b"), false);
        nameState.addSubRule("rule3", c4, Patterns.exactMatch("a"), true);

        assertEquals(new HashSet<>(Arrays.asList(c2, c4)),
                nameState.getTerminalSubRuleIdsForPattern(Patterns.exactMatch("a")));
    }

    @Test
    public void testGetNonTerminalSubRuleIdsForPattern() {
        NameState nameState = new NameState();
        nameState.addSubRule("rule1", c1, Patterns.exactMatch("a"), true);
        nameState.addSubRule("rule1", c2, Patterns.exactMatch("a"), false);
        nameState.addSubRule("rule2", c3, Patterns.exactMatch("a"), false);
        nameState.addSubRule("rule3", c4, Patterns.exactMatch("b"), false);

        assertEquals(new HashSet<>(Arrays.asList(c2, c3)),
                nameState.getNonTerminalSubRuleIdsForPattern(Patterns.exactMatch("a")));
    }

    @Test
    public void testContainsRule() {
        NameState nameState = new NameState();
        nameState.addSubRule("rule1", c1, Patterns.exactMatch("a"), true);
        nameState.addSubRule("rule2", c2, Patterns.exactMatch("a"), false);
        nameState.addSubRule("rule1", c2, Patterns.exactMatch("b"), false);

        assertTrue(nameState.containsRule("rule1", Patterns.exactMatch("a")));
        assertTrue(nameState.containsRule("rule2", Patterns.exactMatch("a")));
        assertTrue(nameState.containsRule("rule1", Patterns.exactMatch("b")));
        assertFalse(nameState.containsRule("rule3", Patterns.exactMatch("a")));
        assertFalse(nameState.containsRule("rule1", Patterns.exactMatch("c")));
    }

    @Test
    public void testNextNameStateWithAdditionalNameStateReuse() {
        NameState nameState = new NameState();
        NameState nextNameState = new NameState();
        nameState.addNextNameState("key", nextNameState);
        assertEquals(nextNameState, nameState.getNextNameState("key"));
        nameState.removeNextNameState("key");
        assertNull(nameState.getNextNameState("key"));
    }
}
