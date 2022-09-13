package software.amazon.event.ruler;

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
            newSingles.addAll(expand(storedTransition));
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
            newSingles.addAll(expand(storedTransition));
            newSingles.remove(transition);
            entry.setValue(coalesce(newSingles));
        }
        map = newMap;
    }

    /**
     *  Updates one ceiling=>transition mapping and leaves the map in a consistent state.
     *  It's a two-step process. First of all, we go through the map and find the entry that contains the byte value.
     *  Then we figure out whether the new byte value is at the bottom and/or the top of the entry (can be both if it's
     *  a singleton mapping). If it's not at the bottom, we write a new entry representing the part of the entry less
     *  than the byte value. Then we write a new entry for the byte value. Then if not at the top we write a new entry
     *  representing the proportion greater than the byte value. Then we merge entries mapping to the same transition.
     *  One effect is that you can remove the transition at position X by putting (X, null). An earlier implementation
     *  expanded the map to a ByteMapExtent[256] array of singletons, did the update, then contracted it, but that drove
     *  the compute/memory cost up so much that addRule and deleteRule were showing up in the profiler. Another earlier
     *  implementation tried to do the merging at the same time as the entry wrangling and dissolved into an
     *  incomprehensible pile of special-case code.
     */
    private void updateTransition(final byte utf8byte, final SingleByteTransition transition, Operation operation) {
        final int index = utf8byte & 0xFF;
        final NavigableMap<Integer, ByteTransition> newMap = new TreeMap<>();

        newMap.putAll(map.headMap(index, true));
        Map.Entry<Integer, ByteTransition> targetEntry = map.higherEntry(index);
        int targetCeiling = targetEntry.getKey();
        ByteTransition target = targetEntry.getValue();

        final boolean atBottom = (index == 0) || (!newMap.isEmpty() && newMap.lastKey() == index);
        if (!atBottom) {
            newMap.put(index, target);
        }

        Set<SingleByteTransition> singles = new HashSet<>();
        if (operation != Operation.PUT) {
            singles.addAll(expand(target));
        }
        if (operation == Operation.REMOVE) {
            singles.remove(transition);
        } else {
            singles.add(transition);
        }
        ByteTransition coalesced = coalesce(singles);
        newMap.put(index + 1, coalesced);

        final boolean atTop = index == targetCeiling - 1;
        if (!atTop) {
            newMap.put(targetCeiling, target);
        }

        newMap.putAll(map.tailMap(targetCeiling, false));

        // Merge adjacent mappings with the same transition.
        mergeAdjacentInMapIfNeeded(newMap);

        // Update map in last step to enforce the happen-before relationship.
        // We don't update content in map directly to avoid change being felt by other threads before complete.
        map = newMap;
    }

    /**
     * Merge adjacent entries with equal transitions in inputMap.
     *
     * @param inputMap The map on which we merge adjacent entries with equal transitions.
     */
    private void mergeAdjacentInMapIfNeeded(final NavigableMap<Integer, ByteTransition> inputMap) {
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
        candidates.addAll(firstByteTransition.expand());

        while (iterator.hasNext()) {
            ByteTransition nextByteTransition = iterator.next();
            if (nextByteTransition == null) {
                return ByteMachine.EmptyByteTransition.INSTANCE;
            }
            candidates.retainAll(nextByteTransition.expand());
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
                allTransitions.addAll(expand(transition));
            }
        }
        return allTransitions;
    }

    private static Set<SingleByteTransition> expand(ByteTransition transition) {
        if (transition == null) {
            return new HashSet<>();
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