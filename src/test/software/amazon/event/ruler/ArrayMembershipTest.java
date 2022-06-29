package software.amazon.event.ruler;

import org.junit.Test;

import static org.junit.Assert.*;

public class ArrayMembershipTest {
    @Test
    public void WHenARoleIsPutThenItIsRetrieved() {
        ArrayMembership cut = new ArrayMembership();
        int[] indices = { 3, 8, 800000, 77};
        for (int index : indices) {
            assertEquals(-1, cut.getMembership(index));
        }
        for (int index : indices) {
            cut.putMembership(index, index + 27);
            assertEquals(index + 27, cut.getMembership(index));
            assertEquals(-1, cut.getMembership(index + 1));
            assertFalse(cut.isEmpty());
        }
    }

    private void checkWanted(int[][] wanted, ArrayMembership membership) {
        for (int[] pair : wanted) {
            assertEquals(String.format("%d/%d", pair[0], pair[1]), pair[1], membership.getMembership(pair[0]));
            membership.deleteMembership(pair[0]);
        }
        assertTrue("Extra memberships", membership.isEmpty());
    }

    private ArrayMembership fromPairs(int[][] pairs) {
        ArrayMembership am = new ArrayMembership();
        for (int[] pair : pairs) {
            am.putMembership(pair[0], pair[1]);
        }
        return am;
    }

    @Test
    public void WHEN_MembershipsAreCompared_THEN_TheyAreMergedProperly() {

        ArrayMembership empty = new ArrayMembership();

        int[][] sofar = {
                {0, 0},
                {2, 1},
                {4, 2},
                {6, 8}
        };

        int[][] fieldWithNoConflict = {
                {3, 33}
        };
        int[][] fieldConflictingWithPreviousField = {
                {3, 44}
        };
        int[][] wanted1 = {
                {0, 0},
                {2, 1},
                {3, 33},
                {4, 2},
                {6, 8}
        };
        int[][] fieldWithConflict = {
                {2, 2}
        };

        ArrayMembership result;

        result = ArrayMembership.checkArrayConsistency(empty, fromPairs(fieldWithNoConflict));
        checkWanted(fieldWithNoConflict, result);

        result = ArrayMembership.checkArrayConsistency(fromPairs(sofar), empty);
        checkWanted(sofar, result);

        result = ArrayMembership.checkArrayConsistency(fromPairs(sofar), fromPairs(fieldWithNoConflict));
        checkWanted(wanted1, result);

        result = ArrayMembership.checkArrayConsistency(fromPairs(fieldWithNoConflict), fromPairs(sofar));
        checkWanted(wanted1, result);

        result = ArrayMembership.checkArrayConsistency(fromPairs(sofar), fromPairs(fieldWithConflict));
        assertNull(result);

        result = ArrayMembership.checkArrayConsistency(fromPairs(fieldWithConflict), fromPairs(sofar));
        assertNull(result);

        result = ArrayMembership.checkArrayConsistency(fromPairs(sofar), fromPairs(sofar));
        checkWanted(sofar, result);

        result = ArrayMembership.checkArrayConsistency(fromPairs(sofar), fromPairs(fieldWithNoConflict));
        assert result != null;
        result = ArrayMembership.checkArrayConsistency(result, fromPairs(fieldConflictingWithPreviousField));
        assertNull(result);
    }

}