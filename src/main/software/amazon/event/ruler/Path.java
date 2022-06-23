package software.amazon.event.ruler;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Represents the current location in a traversal of a JSON object.
 * Both rules and events need to be flattened into name/value pairs, where the name represents the path to the
 *  object.
 */

class Path {
    private final static char SEPARATOR = '.';

    private final Deque<String> path = new ArrayDeque<>();

    // memo-ize
    private String memo = null;

    void push(final String s) {
        memo = null;
        path.add(s);
    }
    String pop() {
        memo = null;

        // in principle could remove null
        return path.removeLast();
    }

    /**
     * return the pathname as a .-separated string.
     * This turns out to be a performance bottleneck so it's memoized and uses StringBuilder rather than StringJoiner.
     * @return the pathname
     */
    String name() {
        if (memo == null) {
            final Object[] steps = path.toArray();
            if (steps.length == 0) {
                memo = "";
            } else {
                final StringBuilder sb = new StringBuilder();
                sb.append(steps[0]);
                for (int i = 1; i < steps.length; i++) {
                    sb.append(SEPARATOR).append(steps[i]);
                }
                memo = sb.toString();
            }
        }

        return memo;
    }

    /**
     * return the pathname as a segmented-string with the indicated separator
     * @param lastStep The next step, which we want to use but not to push on to the path
     * @return the pathname
     */
    String extendedName(final String lastStep) {
        String base = name();
        if (!base.isEmpty()) {
            base += SEPARATOR;
        }
        return base + lastStep;
    }
}
