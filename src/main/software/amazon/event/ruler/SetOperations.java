package software.amazon.event.ruler;

import java.util.Set;
import java.util.function.Function;

class SetOperations {

    private SetOperations() { }

    /**
     * Add each element of the intersection of two sets to a third set. This is optimized for performance as it iterates
     * through the smaller set.
     *
     * @param set1 First set involved in intersection.
     * @param set2 Second set involved in intersection.
     * @param addTo Add intersection to this set.
     * @param <T> Type of all three sets.
     */
    public static <T> void intersection(final Set<T> set1, final Set<T> set2, final Set<T> addTo) {
        intersection(set1, set2, addTo, t -> t);
    }

    /**
     * Add the transformation of each element of the intersection of two sets to a third set. This is optimized for
     * performance as it iterates through the smaller set.
     *
     * @param set1 First set involved in intersection.
     * @param set2 Second set involved in intersection.
     * @param addTo Add intersection to this set.
     * @param transform Transform each element of intersection into an element of the third set.
     * @param <T> Type of first two sets.
     * @param <R> Type of addTo set.
     */
    public static <T, R> void intersection(final Set<T> set1, final Set<T> set2, final Set<R> addTo,
                                           final Function<T, R> transform) {
        Set<T> smaller = set1.size() <= set2.size() ? set1 : set2;
        Set<T> larger = set1.size() <= set2.size() ? set2 : set1;
        for (T element : smaller) {
            if (larger.contains(element)) {
                addTo.add(transform.apply(element));
            }
        }
    }
}
