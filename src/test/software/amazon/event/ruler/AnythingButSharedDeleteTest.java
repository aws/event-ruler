package software.amazon.event.ruler;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Regression tests for https://github.com/aws/event-ruler/issues/259
 * (deleteRule ghost / strand for anything-but patterns).
 *
 * When two rules share the SAME anything-but pattern on a key, that pattern can lead to more than
 * one NameState (one branch per rule). deleteRule walks the NameStates
 * ByteMachine.findAllPatterns reports, so it must report all of them. The anything-but paths used
 * to collapse the discovered NameStates to a single one (asserting size()==1) and return only that,
 * so GenericMachine.deleteStep never visited every branch: the deleted rule's own branch could be
 * skipped, so it kept matching (ghost), or the shared transition was torn down and the surviving
 * rule went with it (over-delete). Either way the machine was never fully emptied. The value-pattern
 * fix from #256 (findAllMatchPattern union semantics) never reached the anything-but paths until
 * this change.
 *
 * The ANYTHING_BUT_WILDCARD type is the one that reaches multiple NameStates in practice, so the two
 * wildcard tests fail before the fix. The prefix test guards the anything-but paths that already
 * collapsed to a single shared NameState, so they keep deleting cleanly after the change.
 */
public class AnythingButSharedDeleteTest {

    /**
     * Two rules share an anything-but-wildcard on one key. Deleting one must leave exactly the other
     * matching, and deleting both must empty the machine. Before the fix, deleting r1 leaves r1's
     * sub-rule stranded, so the event still matches [r2, r1].
     */
    @Test
    public void WHEN_SameKeyRulesShareAnythingButWildcard_THEN_DeleteLeavesNoStrand() throws Exception {
        Machine machine = new Machine();
        String rule = "{ \"a\" : [ { \"anything-but\": { \"wildcard\": \"*foo*\" } } ] }";

        machine.addRule("r1", rule);
        machine.addRule("r2", rule);

        assertEquals(new HashSet<>(Arrays.asList("r1", "r2")),
                new HashSet<>(machine.rulesForJSONEvent("{\"a\" : \"bar\"}")));

        machine.deleteRule("r1", rule);
        assertEquals("after deleting r1, only r2 must remain (no ghost strand)",
                Collections.singletonList("r2"), machine.rulesForJSONEvent("{\"a\" : \"bar\"}"));
        assertTrue("surviving rule r2 must still exclude the wildcard value",
                machine.rulesForJSONEvent("{\"a\" : \"foo\"}").isEmpty());

        machine.deleteRule("r2", rule);
        assertTrue(machine.rulesForJSONEvent("{\"a\" : \"bar\"}").isEmpty());
        assertTrue("machine must be empty after deleting both anything-but-wildcard rules",
                machine.isEmpty());
    }

    /**
     * Two rules share an anything-but-wildcard on key a but diverge on key b. The shared a
     * transition reaches distinct NameStates that continue to the b patterns. Before the fix,
     * deleting r1 tore down the shared transition and took r2's branch with it, so r2 stopped
     * matching (over-delete).
     */
    @Test
    public void WHEN_DivergingRulesShareAnythingButWildcard_THEN_DeleteLeavesNoStrand() throws Exception {
        Machine machine = new Machine();
        String rule1 = "{ \"a\" : [ { \"anything-but\": { \"wildcard\": \"*foo*\" } } ], \"b\" : [ \"b1\" ] }";
        String rule2 = "{ \"a\" : [ { \"anything-but\": { \"wildcard\": \"*foo*\" } } ], \"b\" : [ \"b2\" ] }";

        machine.addRule("r1", rule1);
        machine.addRule("r2", rule2);

        assertEquals(Collections.singletonList("r1"),
                machine.rulesForJSONEvent("{\"a\" : \"bar\", \"b\" : \"b1\"}"));
        assertEquals(Collections.singletonList("r2"),
                machine.rulesForJSONEvent("{\"a\" : \"bar\", \"b\" : \"b2\"}"));

        machine.deleteRule("r1", rule1);
        assertTrue("deleted rule r1 must not still match",
                machine.rulesForJSONEvent("{\"a\" : \"bar\", \"b\" : \"b1\"}").isEmpty());
        assertEquals("surviving rule r2 must still match through the shared transition (no over-delete)",
                Collections.singletonList("r2"),
                machine.rulesForJSONEvent("{\"a\" : \"bar\", \"b\" : \"b2\"}"));

        machine.deleteRule("r2", rule2);
        assertTrue(machine.rulesForJSONEvent("{\"a\" : \"bar\", \"b\" : \"b2\"}").isEmpty());
        assertTrue("machine must be empty after deleting both diverging rules",
                machine.isEmpty());
    }

    /**
     * Guard: the anything-but-prefix path already collapsed to a single shared NameState and deleted
     * cleanly. This must stay true after the fix.
     */
    @Test
    public void WHEN_RulesShareAnythingButPrefix_THEN_DeleteStillClean() throws Exception {
        Machine machine = new Machine();
        String rule = "{ \"a\" : [ { \"anything-but\": { \"prefix\": \"foo\" } } ] }";

        machine.addRule("r1", rule);
        machine.addRule("r2", rule);

        assertEquals(new HashSet<>(Arrays.asList("r1", "r2")),
                new HashSet<>(machine.rulesForJSONEvent("{\"a\" : \"bar\"}")));

        machine.deleteRule("r1", rule);
        assertEquals(Collections.singletonList("r2"), machine.rulesForJSONEvent("{\"a\" : \"bar\"}"));

        machine.deleteRule("r2", rule);
        assertTrue(machine.rulesForJSONEvent("{\"a\" : \"bar\"}").isEmpty());
        assertTrue("machine must be empty after deleting both anything-but-prefix rules",
                machine.isEmpty());
    }
}
