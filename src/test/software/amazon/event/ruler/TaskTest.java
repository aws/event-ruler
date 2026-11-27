package software.amazon.event.ruler;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

/**
 * Tests for the per-event table of used fields in {@link Task}. The reference implementations here are the
 * split-and-stream code that Task used before the table, kept so the two can be compared directly.
 */
public class TaskTest {

    private static final String[] EDGE_NAMES = {
        "", ".", "..", "...", "a", "a.", "a..", ".a", "..a", ".a.", "a.b", "a..b", "a.b.", ".a.b", "a.b.c",
        "a.b.c.", "a..b.c", "a.x", "x.a", "a.b.x", "x", "b", "c", "b.a", "a.b.c.d",
    };

    // The old Task.isFieldUsed(String), unchanged.
    private static boolean referenceIsFieldUsed(final String field, final GenericMachine<?> machine) {
        if (field.contains(".")) {
            String[] steps = field.split("\\.");
            return Arrays.stream(steps).allMatch(machine::isFieldStepUsed);
        }
        return machine.isFieldStepUsed(field);
    }

    // What the old Finder.moveFrom loop visited: the first key index at or after keyIndex whose name is used.
    private static int referenceNextUsedKeyIndex(final String[] event, final int keyIndex,
                                                 final GenericMachine<?> machine) {
        for (int i = keyIndex; i < event.length; i += 2) {
            if (referenceIsFieldUsed(event[i], machine)) {
                return i;
            }
        }
        return event.length;
    }

    // Adds one rule with an exists pattern on each field, through addPatternRule so that arbitrary names
    // (including ones made only of dots, which JSON rules cannot express) record their steps in the machine.
    private static Machine machineWithFields(final String... fields) {
        Map<String, List<Patterns>> rule = new HashMap<>();
        for (String field : fields) {
            rule.put(field, Collections.singletonList(Patterns.existencePatterns()));
        }
        Machine machine = new Machine();
        machine.addPatternRule("r", rule);
        return machine;
    }

    @Test
    public void isFieldUsedMatchesSplitOnEdgeCaseNames() throws Exception {
        Machine[] machines = {
            new Machine(),
            machineWithFields("a.b.c"),
            machineWithFields("a..b"),        // records the empty step ""
            machineWithFields("x", "a"),
            machineWithFields(".a.b."),
        };
        for (Machine machine : machines) {
            for (String name : EDGE_NAMES) {
                assertEquals("name '" + name + "' on " + machine,
                        referenceIsFieldUsed(name, machine), Task.isFieldUsed(name, machine));
            }
        }
    }

    @Test
    public void isFieldUsedMatchesSplitOnRandomNames() throws Exception {
        Random random = new Random(20261126L);
        char[] alphabet = {'a', 'b', 'c', '.', '.'};
        for (int m = 0; m < 50; m++) {
            List<String> fields = new ArrayList<>();
            for (int f = 1 + random.nextInt(4); f > 0; f--) {
                fields.add(randomName(random, alphabet, 1 + random.nextInt(6)));
            }
            Machine machine = machineWithFields(fields.toArray(new String[0]));
            for (int n = 0; n < 400; n++) {
                String name = randomName(random, alphabet, random.nextInt(9));
                assertEquals("name '" + name + "' with rule fields " + fields,
                        referenceIsFieldUsed(name, machine), Task.isFieldUsed(name, machine));
            }
        }
    }

    @Test
    public void nextUsedKeyIndexMatchesLinearScan() throws Exception {
        Random random = new Random(42L);
        Machine machine = machineWithFields("a.b", "c", "d.e.f");
        String[] names = {"a", "a.b", "a.x", "c", "c.c", "d.e.f", "d.e", "z", "b.a", "e.f.d"};
        for (int t = 0; t < 2000; t++) {
            // Include odd lengths: a trailing name with no value.
            int length = random.nextInt(25);
            String[] event = new String[length];
            for (int i = 0; i < length; i++) {
                event[i] = (i % 2 == 0) ? names[random.nextInt(names.length)] : "\"v\"";
            }
            Task task = new Task(event, machine);
            for (int keyIndex = 0; keyIndex <= length + 2; keyIndex += 2) {
                int expected = referenceNextUsedKeyIndex(event, keyIndex, machine);
                assertEquals(Arrays.toString(event) + " from " + keyIndex,
                        expected, task.nextUsedKeyIndex(keyIndex));
                assertEquals(Arrays.toString(event) + " at " + keyIndex,
                        keyIndex < length && expected == keyIndex, task.isFieldUsed(keyIndex));
            }
        }
    }

    @Test
    public void emptyEventHasNoUsedFields() throws Exception {
        Task task = new Task(new String[0], machineWithFields("a"));
        assertEquals(0, task.nextUsedKeyIndex(0));
        assertFalse(task.isFieldUsed(0));
    }

    @Test
    public void oddLengthEventBehavesAsBefore() throws Exception {
        // A trailing name with no value is still visited when used, and fails reading its value, as before.
        Machine machine = new Machine();
        machine.addRule("r", "{\"a\": [\"x\"]}");
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> machine.rulesForEvent(Arrays.asList("a")));
        assertThrows(ArrayIndexOutOfBoundsException.class,
                () -> machine.rulesForEvent(Arrays.asList("a", "\"x\"", "a")));
        // An unused trailing name is skipped, as before.
        assertEquals(Collections.singletonList("r"), machine.rulesForEvent(Arrays.asList("a", "\"x\"", "b")));
    }

    @Test
    public void tableIsPerEvent() throws Exception {
        // Each rulesForEvent call builds a new Task, so a rule added between calls is seen by the next event.
        Machine machine = new Machine();
        machine.addRule("r1", "{\"a\": [\"x\"]}");
        List<String> event = Arrays.asList("a", "\"x\"", "b", "\"y\"");
        assertEquals(Collections.singletonList("r1"), machine.rulesForEvent(event));
        machine.addRule("r2", "{\"b\": [\"y\"]}");
        List<String> matched = new ArrayList<>(machine.rulesForEvent(event));
        Collections.sort(matched);
        assertEquals(Arrays.asList("r1", "r2"), matched);
    }

    private static String randomName(final Random random, final char[] alphabet, final int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(alphabet[random.nextInt(alphabet.length)]);
        }
        return sb.toString();
    }
}
