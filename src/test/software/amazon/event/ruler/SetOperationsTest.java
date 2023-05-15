package software.amazon.event.ruler;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static software.amazon.event.ruler.SetOperations.intersection;

public class SetOperationsTest {

    @Test
    public void testIntersection() {
        Set<String> set1 = new HashSet<>(Arrays.asList("a", "b", "c"));
        Set<String> set2 = new HashSet<>(Arrays.asList("b", "c", "d"));

        Set<String> result = new HashSet<>();
        intersection(set1, set2, result);

        assertEquals(new HashSet<>(Arrays.asList("b", "c")), result);
    }

    @Test
    public void testIntersectionDifferentResultType() {
        String indexString = "abcd";
        Set<String> set1 = new HashSet<>(Arrays.asList("a", "b", "c"));
        Set<String> set2 = new HashSet<>(Arrays.asList("b", "c", "d"));

        Set<Integer> result = new HashSet<>();
        intersection(set1, set2, result, s -> indexString.indexOf(s));

        assertEquals(new HashSet<>(Arrays.asList(1, 2)), result);
    }
}
