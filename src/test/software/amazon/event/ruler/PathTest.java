package software.amazon.event.ruler;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PathTest {

    @Test
    public void WHEN_PushAndPopAreUsed_THEN_TheyOperateSymmetrically() {
        Path cut = new Path();
        String[] vals = {"foo", "bar", "baz", "33"};
        for (int i = 0; i < vals.length; i++) {
            cut.push(vals[i]);
            assertEquals(join(vals, i), cut.name());
        }

        for (int i = vals.length - 1; i >= 0; i--) {
            assertEquals(join(vals, i), cut.name());
            assertEquals(vals[i], cut.pop());
        }
    }

    private String join(String[] strings, int last) {
        StringBuilder j = new StringBuilder(strings[0]);
        for (int i = 1; i <= last; i++) {
            j.append(".").append(strings[i]);
        }
        return j.toString();
    }

}