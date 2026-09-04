package software.amazon.event.ruler;

import org.junit.Test;

import software.amazon.event.ruler.MachineComplexityEvaluatorCorpus.WalkCountingEvaluator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Verifies that complexity evaluation walks each ByteMachine once per evaluation, whatever the sizes of the exact-match
 * value lists ahead of it. All values of one key's list lead to the same next NameState, so recursing into next
 * NameStates once per match (once per list value) walks the machines behind a chain of value lists of sizes L1..Lk
 * L1*...*Lk times; for realistic rules (e.g. 2x50x21 value lists ahead of a wildcard-bearing key) that is thousands of
 * walks of the most expensive machine and tens of gigabytes of transient allocation, for a machine whose reported
 * complexity is far below any configured maximum. Recursing once per distinct next NameState walks each machine once.
 *
 * <p>The tests pin that behavior with deterministic ByteMachine walk counts, not wall-clock time (which would be flaky
 * in CI): each distinct ByteMachine must be walked exactly once per evaluation.
 */
public class MachineComplexityEvaluatorWalkCountTest {

    private static final int MAX_COMPLEXITY = 100;

    /**
     * Four anything-but wildcard patterns with leading stars: the pattern set of the reported production rule, with
     * synthetic values.
     */
    private static final String ANYTHING_BUT_WILDCARD_SET = "[{\"anything-but\": {\"wildcard\": " +
            "[\"*kiwi*\", \"*BASE_QuxB*\", \"*Delta*\", \"*a/example-lake-abc98765432*\"]}}]";

    /**
     * Complexity of a machine holding only exact matches ahead of a wildcard key equals the complexity of the wildcard
     * key's machine alone; asserting against this reference avoids hand-computed constants.
     */
    private static int complexityOfRule(String rule) throws Exception {
        Machine machine = new Machine();
        machine.addRule("reference", rule);
        return machine.evaluateComplexity(new MachineComplexityEvaluator(MAX_COMPLEXITY));
    }

    @Test
    public void testExactMatchListChainWalksEachByteMachineOnce() throws Exception {
        // Keys are added to the machine in lexical order: aaa, bbb, ccc, zzz. The wildcard machine on zzz is
        // reachable via 2x3x4=24 distinct paths through the exact-match values of the preceding keys.
        String rule = rule(
                member("aaa", "a1", "a2"),
                member("bbb", "b1", "b2", "b3"),
                member("ccc", "c1", "c2", "c3", "c4"),
                "\"zzz\": [{\"wildcard\": \"*z\"}]");
        Machine machine = new Machine();
        machine.addRule("rule", rule);

        WalkCountingEvaluator evaluator = new WalkCountingEvaluator(MAX_COMPLEXITY);
        int complexity = machine.evaluateComplexity(evaluator);

        // 4 keys, 4 ByteMachines, 4 walks. Recursing once per match would take 1 + 2 + 2*3 + 2*3*4 = 33 walks.
        assertEquals(4, evaluator.getWalks());
        // Exact-match lists contribute no complexity; the chain evaluates to the wildcard machine's complexity.
        assertEquals(complexityOfRule("{\"zzz\": [{\"wildcard\": \"*z\"}]}"), complexity);
    }

    @Test
    public void testWalkCountIndependentOfExactMatchListSizes() throws Exception {
        String smallLists = rule(
                member("aaa", generatedValues("avalue", 2)),
                member("bbb", generatedValues("bvalue", 2)),
                member("ccc", generatedValues("cvalue", 2)),
                "\"zzz\": [{\"wildcard\": \"*z*\"}]");
        String bigLists = rule(
                member("aaa", generatedValues("avalue", 5)),
                member("bbb", generatedValues("bvalue", 5)),
                member("ccc", generatedValues("cvalue", 5)),
                "\"zzz\": [{\"wildcard\": \"*z*\"}]");
        Machine smallMachine = new Machine();
        smallMachine.addRule("rule", smallLists);
        Machine bigMachine = new Machine();
        bigMachine.addRule("rule", bigLists);

        WalkCountingEvaluator smallEvaluator = new WalkCountingEvaluator(MAX_COMPLEXITY);
        WalkCountingEvaluator bigEvaluator = new WalkCountingEvaluator(MAX_COMPLEXITY);
        int smallComplexity = smallMachine.evaluateComplexity(smallEvaluator);
        int bigComplexity = bigMachine.evaluateComplexity(bigEvaluator);

        // Walk count depends only on the number of distinct ByteMachines, never on value-list sizes (per-match
        // recursion would take 15 and 156 walks respectively).
        assertEquals(4, smallEvaluator.getWalks());
        assertEquals(4, bigEvaluator.getWalks());
        assertEquals(smallComplexity, bigComplexity);
    }

    @Test
    public void testAnythingButWildcardSetAfterExactMatchListsWalksEachByteMachineOnce() throws Exception {
        String rule = rule(
                member("aaa", "ErrCodeOne", "ErrCodeTwo"),
                member("bbb", "b1", "b2", "b3"),
                member("ccc", "c1", "c2", "c3", "c4"),
                "\"zzz\": " + ANYTHING_BUT_WILDCARD_SET);
        Machine machine = new Machine();
        machine.addRule("rule", rule);

        WalkCountingEvaluator evaluator = new WalkCountingEvaluator(MAX_COMPLEXITY);
        int complexity = machine.evaluateComplexity(evaluator);

        assertEquals(4, evaluator.getWalks());
        assertEquals(complexityOfRule("{\"zzz\": " + ANYTHING_BUT_WILDCARD_SET + "}"), complexity);
    }

    /**
     * The reported production shape: two exact-match value lists of sizes 2 and 50, then a list of size 21, then a key
     * holding an anything-but-wildcard set of four leading-star patterns. The wildcard machine is reachable via
     * 2*50*21 = 2100 distinct paths, so recursing once per match walks it 2100 times (2203 walks in total, tens of
     * seconds and tens of gigabytes of transient allocation); recursing once per next NameState walks it once.
     */
    @Test
    public void testProductionShapedRuleWalksEachByteMachineOnce() throws Exception {
        // Two shared-prefix families for the event-name list, a shared-suffix family for the event-source list.
        List<String> eventNames = new ArrayList<>(generatedValues("MakeThing", 25));
        eventNames.addAll(generatedValues("DropThing", 25));
        List<String> eventSources = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            eventSources.add(String.format("svc%02d.example.com", i));
        }
        String rule = rule(
                member("aaa", "ErrCodeOne", "ErrCodeTwo"),
                member("bbb", eventNames),
                member("ccc", eventSources),
                "\"zzz\": " + ANYTHING_BUT_WILDCARD_SET);
        Machine machine = new Machine();
        machine.addRule("rule", rule);

        WalkCountingEvaluator evaluator = new WalkCountingEvaluator(MAX_COMPLEXITY);
        int complexity = machine.evaluateComplexity(evaluator);

        assertEquals(4, evaluator.getWalks());
        assertEquals(complexityOfRule("{\"zzz\": " + ANYTHING_BUT_WILDCARD_SET + "}"), complexity);
    }

    @Test
    public void testDeepExactMatchListChainWalksLinearly() throws Exception {
        // Ten keys, each with two exact-match values, then a wildcard key. Recursing once per match would take
        // 1 + 2 + 4 + ... + 2^10 = 2047 walks; recursing once per next NameState takes one walk per ByteMachine.
        List<String> members = new ArrayList<>();
        for (char key = 'a'; key <= 'j'; key++) {
            String keyName = "" + key + key + key;
            members.add(member(keyName, generatedValues(key + "value", 2)));
        }
        members.add("\"zzz\": [{\"wildcard\": \"*z\"}]");
        Machine machine = new Machine();
        machine.addRule("rule", rule(members.toArray(new String[0])));

        WalkCountingEvaluator evaluator = new WalkCountingEvaluator(MAX_COMPLEXITY);
        int complexity = machine.evaluateComplexity(evaluator);

        assertEquals(11, evaluator.getWalks());
        assertEquals(complexityOfRule("{\"zzz\": [{\"wildcard\": \"*z\"}]}"), complexity);
    }

    @Test
    public void testNestedKeysWalkEachByteMachineOnce() throws Exception {
        String rule = rule(
                "\"aaa\": {\"inner\": [\"a1\", \"a2\"]}",
                member("ccc", "c1", "c2"),
                "\"zzz\": [{\"wildcard\": \"*z\"}]");
        Machine machine = new Machine();
        machine.addRule("rule", rule);

        WalkCountingEvaluator evaluator = new WalkCountingEvaluator(MAX_COMPLEXITY);
        int complexity = machine.evaluateComplexity(evaluator);

        // Per-match recursion would take 1 + 2 + 4 = 7 walks.
        assertEquals(3, evaluator.getWalks());
        assertEquals(complexityOfRule("{\"zzz\": [{\"wildcard\": \"*z\"}]}"), complexity);
    }

    @Test
    public void testOrSubRulesWalkEachByteMachineOnce() throws Exception {
        // The two $or branches create two sub-rules: bar=1 leads to one NameState and bar=2 to another, each
        // carrying its own ByteMachine for key foo. Expected walks: bar's machine, then foo's machine under
        // each of the two branch NameStates: 3 total.
        String rule = "{\"$or\": [{\"bar\": [\"1\"]}, {\"bar\": [\"2\"]}], \"foo\": [{\"wildcard\": \"*f\"}]}";
        Machine machine = new Machine();
        machine.addRule("rule", rule);

        WalkCountingEvaluator evaluator = new WalkCountingEvaluator(MAX_COMPLEXITY);
        int complexity = machine.evaluateComplexity(evaluator);

        assertEquals(3, evaluator.getWalks());
        assertEquals(complexityOfRule("{\"foo\": [{\"wildcard\": \"*f\"}]}"), complexity);
    }

    private static String rule(String... members) {
        return "{" + String.join(", ", members) + "}";
    }

    private static String member(String keyName, String... values) {
        return member(keyName, Arrays.asList(values));
    }

    private static String member(String keyName, List<String> values) {
        List<String> quoted = new ArrayList<>();
        for (String value : values) {
            quoted.add("\"" + value + "\"");
        }
        return "\"" + keyName + "\": [" + String.join(", ", quoted) + "]";
    }

    private static List<String> generatedValues(String valuePrefix, int count) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            values.add(valuePrefix + i);
        }
        return values;
    }
}
