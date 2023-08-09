package software.amazon.event.ruler;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

import static software.amazon.event.ruler.CompoundByteTransition.coalesce;

/**
 * Maps byte values to ByteTransitions. Designed to perform well given the constraints that most ByteStates will have a
 * very small number of transitions, and that for support of wildcards and regexes, we need to efficiently represent the
 * condition where wide ranges of byte values (including *all* of them) will transition to a common next ByteState.
 */
class ByteMap {

    /*
     * Each map entry represents one or more byte values that share a transition. Null means no transition. The key is a
     * single integer which is the ceiling value; all byte values below that and greater than or equal to the floor
     * value map to the associated transition or null. The floor value for any transition is the ceiling from the
     * previous entry, or zero for the zeroth entry in the map.
     */
    private volatile NavigableMap<Integer, ByteTransition> map = new TreeMap<>();

    ByteMap() {
        map.put(256, null);
    }

    void putTransition(final byte utf8byte, final SingleByteTransition transition) {
        updateTransition(utf8byte, transition, Operation.PUT);
    }

    void putTransitionForAllBytes(final SingleByteTransition transition) {
        NavigableMap<Integer, ByteTransition> newMap = new TreeMap<>();
        newMap.put(256, transition);
        map = newMap;
    }

    void addTransition(final byte utf8byte, final SingleByteTransition transition) {
        updateTransition(utf8byte, transition, Operation.ADD);
    }

    void addTransitionForAllBytes(final SingleByteTransition transition) {
        NavigableMap<Integer, ByteTransition> newMap = new TreeMap<>(map);
        for (Map.Entry<Integer, ByteTransition> entry : newMap.entrySet()) {
            Set<SingleByteTransition> newSingles = new HashSet<>();
            ByteTransition storedTransition = entry.getValue();
            expand(storedTransition).forEach(single -> newSingles.add(single));
            newSingles.add(transition);
            entry.setValue(coalesce(newSingles));
        }
        map = newMap;
    }

    void removeTransition(final byte utf8byte, final SingleByteTransition transition) {
        updateTransition(utf8byte, transition, Operation.REMOVE);
    }

    void removeTransitionForAllBytes(final SingleByteTransition transition) {
        NavigableMap<Integer, ByteTransition> newMap = new TreeMap<>(map);
        for (Map.Entry<Integer, ByteTransition> entry : newMap.entrySet()) {
            Set<SingleByteTransition> newSingles = new HashSet<>();
            ByteTransition storedTransition = entry.getValue();
            expand(storedTransition).forEach(single -> newSingles.add(single));
            newSingles.remove(transition);
            entry.setValue(coalesce(newSingles));
        }
        map = newMap;
    }

    /**
     *  Updates one ceiling=>transition mapping and leaves the map in a consistent state. We go through the map and find
     *  the entry that contains the byte value. If the new byte value is not at the bottom of the entry, we write a new
     *  entry representing the part of the entry less than the byte value. Then we write a new entry for the byte value.
     *  Then we merge entries mapping to the same transition.
     */
    private void updateTransition(final byte utf8byte, final SingleByteTransition transition, Operation operation) {
        final int index = utf8byte & 0xFF;
        ByteTransition target =  map.higherEntry(index).getValue();

        ByteTransition coalesced;
        if (operation == Operation.PUT) {
            coalesced = coalesce(transition);
        } else {
            Iterable<SingleByteTransition> targetIterable = expand(target);
            if (!targetIterable.iterator().hasNext()) {
                coalesced = operation == Operation.ADD ? coalesce(transition) : null;
            } else {
                Set<SingleByteTransition> singles = new HashSet<>();
                targetIterable.forEach(single -> singles.add(single));
                if (operation == Operation.ADD) {
                    singles.add(transition);
                } else {
                    singles.remove(transition);
                }
                coalesced = coalesce(singles);
            }
        }

        final boolean atBottom = index == 0 || map.containsKey(index);
        if (!atBottom) {
            map.put(index, target);
        }
        map.put(index + 1, coalesced);

        // Merge adjacent mappings with the same transition.
        mergeAdjacentInMapIfNeeded(map);
    }

    /**
     * Merge adjacent entries with equal transitions in inputMap.
     *
     * @param inputMap The map on which we merge adjacent entries with equal transitions.
     */
    private static void mergeAdjacentInMapIfNeeded(final NavigableMap<Integer, ByteTransition> inputMap) {
        Iterator<Map.Entry<Integer, ByteTransition>> iterator = inputMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, ByteTransition> next1 = iterator.next();
            Map.Entry<Integer, ByteTransition> next2 = inputMap.higherEntry(next1.getKey());
            if (next2 != null && expand(next1.getValue()).equals(expand(next2.getValue()))) {
                iterator.remove();
            }
        }
    }

    boolean isEmpty() {
        return numberOfTransitions() == 0;
    }

    int numberOfTransitions() {
        return getSingleByteTransitions().size();
    }

    boolean hasTransition(final ByteTransition transition) {
        return getSingleByteTransitions().contains(transition);
    }

    ByteTransition getTransition(final byte utf8byte) {
        return map.higherEntry(utf8byte & 0xFF).getValue();
    }

    /**
     * Get the transition that all bytes lead to. Could be compound if all bytes lead to more than one transition, or
     * could be the empty transition if there are no transitions that all bytes lead to.
     *
     * @return The transition that all bytes lead to.
     */
    ByteTransition getTransitionForAllBytes() {
        Set<SingleByteTransition> candidates = new HashSet<>();
        Iterator<ByteTransition> iterator = map.values().iterator();
        ByteTransition firstByteTransition = iterator.next();
        if (firstByteTransition == null) {
            return ByteMachine.EmptyByteTransition.INSTANCE;
        }
        firstByteTransition.expand().forEach(single -> candidates.add(single));

        while (iterator.hasNext()) {
            ByteTransition nextByteTransition = iterator.next();
            if (nextByteTransition == null) {
                return ByteMachine.EmptyByteTransition.INSTANCE;
            }
            Iterable<SingleByteTransition> singles = nextByteTransition.expand();
            if (singles instanceof Set) {
                candidates.retainAll((Set) singles);
            } else if (singles instanceof SingleByteTransition) {
                SingleByteTransition single = (SingleByteTransition) singles;
                if (candidates.contains(single)) {
                    if (candidates.size() > 1) {
                        candidates.clear();
                        candidates.add(single);
                    }
                } else {
                    if (!candidates.isEmpty()) {
                        candidates.clear();
                    }
                }
            } else {
                // singles should always be a Set or SingleByteTransition. Thus, this "else" is expected to be dead code
                // but it is here for logical correctness if anything changes in the future.
                Set<SingleByteTransition> set = new HashSet<>();
                singles.forEach(single -> set.add(single));
                candidates.retainAll(set);
            }
            if (candidates.isEmpty()) {
                return ByteMachine.EmptyByteTransition.INSTANCE;
            }
        }

        return coalesce(candidates);
    }

    /**
     * Get all transitions contained in this map, whether they are single or compound transitions.
     *
     * @return All transitions contained in this map.
     */
    Set<ByteTransition> getTransitions() {
        Set<ByteTransition> result = new HashSet<>(map.values().size());
        for (ByteTransition transition : map.values()) {
            if (transition != null) {
                result.add(transition);
            }
        }
        return result;
    }

    /**
     * Get the ceiling values contained in this map.
     *
     * @return Ceiling values.
     */
    Set<Integer> getCeilings() {
        return map.keySet();
    }

    /**
     * Get all transitions contained in this map, with the compound transitions expanded to singular form.
     *
     * @return All transitions contained in this map, with the compound transitions expanded to singular form.
     */
    private Set<SingleByteTransition> getSingleByteTransitions() {
        Set<SingleByteTransition> allTransitions = new HashSet<>();
        for (ByteTransition transition : map.values()) {
            if (transition != null) {
                expand(transition).forEach(single -> allTransitions.add(single));
            }
        }
        return allTransitions;
    }

    private static Iterable<SingleByteTransition> expand(ByteTransition transition) {
        if (transition == null) {
            return Collections.EMPTY_SET;
        }
        return transition.expand();
    }

    // for testing
    NavigableMap<Integer, ByteTransition> getMap() {
        return map;
    }

    @Override
    public String toString() {
        final NavigableMap<Integer, ByteTransition> thisMap = map;
        StringBuilder sb = new StringBuilder();

        int floor = 0;
        for (Map.Entry<Integer, ByteTransition> entry : thisMap.entrySet()) {
            int ceiling = entry.getKey();
            ByteTransition transition = entry.getValue();
            for (int j = (char) floor; j < ceiling; j++) {
                StringBuilder targets = new StringBuilder();
                for (ByteTransition trans : expand(transition)) {
                    String target = trans.getClass().getName();
                    target = target.substring(target.lastIndexOf('.') + 1) + "/" + trans.hashCode();
                    targets.append(target + ",");
                }
                if (targets.length() > 0) {
                    targets.deleteCharAt(targets.length() - 1);
                    sb.append((char) j).append("->").append(targets).append(" // ");
                }
            }
            floor = ceiling;
        }
        return sb.toString();
    }

    private enum Operation {
        ADD,
        PUT,
        REMOVE
    }
}