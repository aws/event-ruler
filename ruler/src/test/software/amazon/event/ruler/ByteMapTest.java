package software.amazon.event.ruler;

import static software.amazon.event.ruler.CompoundByteTransition.coalesce;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ByteMapTest {

    private ByteMap map;
    private SingleByteTransition trans1;
    private SingleByteTransition trans2;
    private SingleByteTransition trans3;
    private SingleByteTransition trans4;

    @Before
    public void setup() {
        map = new ByteMap();
        trans1 = new ByteState();
        trans2 = new ByteState();
        trans3 = new ByteState();
        trans4 = new ByteState();
    }

    @Test
    public void testPutTransitionNonAdjacent() {
        map.putTransition((byte) 'a', trans1);
        map.putTransition((byte) 'c', trans2);

        NavigableMap<Integer, ByteTransition> nMap = map.getMap();
        assertEquals(5, nMap.size());
        Iterator<Map.Entry<Integer, ByteTransition>> it = nMap.entrySet().iterator();
        verifyMapEntry(it.next(), 97);
        verifyMapEntry(it.next(), 98, trans1);
        verifyMapEntry(it.next(), 99);
        verifyMapEntry(it.next(), 100, trans2);
        verifyMapEntry(it.next(), 256);
    }

    @Test
    public void testPutTransitionAdjacent() {
        map.putTransition((byte) 'a', trans1);
        map.putTransition((byte) 'b', trans2);

        NavigableMap<Integer, ByteTransition> nMap = map.getMap();
        assertEquals(4, nMap.size());
        Iterator<Map.Entry<Integer, ByteTransition>> it = nMap.entrySet().iterator();
        verifyMapEntry(it.next(), 97);
        verifyMapEntry(it.next(), 98, trans1);
        verifyMapEntry(it.next(), 99, trans2);
        verifyMapEntry(it.next(), 256);
    }

    @Test
    public void testPutTransitionForAllBytes() {
        map.putTransitionForAllBytes(trans1);
        map.putTransitionForAllBytes(trans2);

        NavigableMap<Integer, ByteTransition> nMap = map.getMap();
        assertEquals(1, nMap.size());
        Iterator<Map.Entry<Integer, ByteTransition>> it = nMap.entrySet().iterator();
        verifyMapEntry(it.next(), 256, trans2);
    }

    @Test
    public void testPutTransitionMinCharacter() {
        map.putTransition((byte) Character.MIN_VALUE, trans1);

        NavigableMap<Integer, ByteTransition> nMap = map.getMap();
        assertEquals(2, nMap.size());
        Iterator<Map.Entry<Integer, ByteTransition>> it = nMap.entrySet().iterator();
        verifyMapEntry(it.next(), 1, trans1);
        verifyMapEntry(it.next(), 256);
    }

    @Test
    public void testPutTransitionMaxCharacter() {
        map.putTransition((byte) Character.MAX_VALUE, trans1);

        NavigableMap<Integer, ByteTransition> nMap = map.getMap();
        assertEquals(2, nMap.size());
        Iterator<Map.Entry<Integer, ByteTransition>> it = nMap.entrySet().iterator();
        verifyMapEntry(it.next(), 255);
        verifyMapEntry(it.next(), 256, trans1);
    }

    @Test
    public void testPutTransitionOverwrites() {
        map.putTransition((byte) 'a', trans1);
        map.putTransition((byte) 'a', trans2);

        NavigableMap<Integer, ByteTransition> nMap = map.getMap();
        assertEquals(3, nMap.size());
        Iterator<Map.Entry<Integer, ByteTransition>> it = nMap.entrySet().iterator();
        verifyMapEntry(it.next(), 97);
        verifyMapEntry(it.next(), 98, trans2);
        verifyMapEntry(it.next(), 256);
    }

    @Test
    public void testAddTransitionDoesNotOverwrite() {
        map.putTransition((byte) 'a', trans1);
        map.addTransition((byte) 'a', trans2);

        NavigableMap<Integer, ByteTransition> nMap = map.getMap();
        assertEquals(3, nMap.size());
        Iterator<Map.Entry<Integer, ByteTransition>> it = nMap.entrySet().iterator();
        verifyMapEntry(it.next(), 97);
        verifyMapEntry(it.next(), 98, trans1, trans2);
        verifyMapEntry(it.next(), 256);
    }

    @Test
    public void testAddTransitionBottomOfRange() {
        map.putTransitionForAllBytes(trans1);
        map.addTransition((byte) 0x00, trans2);

        NavigableMap<Integer, ByteTransition> nMap = map.getMap();
        assertEquals(2, nMap.size());
        Iterator<Map.Entry<Integer, ByteTransition>> it = nMap.entrySet().iterator();
        verifyMapEntry(it.next(), 1, trans1, trans2);
        verifyMapEntry(it.next(), 256, trans1);
    }

    @Test
    public void testAddTransitionMiddleOfRange() {
        map.putTransitionForAllBytes(trans1);
        map.addTransition((byte) 'a', trans2);

        NavigableMap<Integer, ByteTransition> nMap = map.getMap();
        assertEquals(3, nMap.size());
        Iterator<Map.Entry<Integer, ByteTransition>> it = nMap.entrySet().iterator();
        verifyMapEntry(it.next(), 97, trans1);
        verifyMapEntry(it.next(), 98, trans1, trans2);
        verifyMapEntry(it.next(), 256, trans1);
    }

    @Test
    public void testAddTransitionTopOfRange() {
        map.putTransitionForAllBytes(trans1);
        map.addTransition((byte) 0xFF, trans2);

        NavigableMap<Integer, ByteTransition> nMap = map.getMap();
        assertEquals(2, nMap.size());
        Iterator<Map.Entry<Integer, ByteTransition>> it = nMap.entrySet().iterator();
        verifyMapEntry(it.next(), 255, trans1);
        verifyMapEntry(it.next(), 256, trans1, trans2);
    }

    @Test
    public void testAddTransitionForAllBytes() {
        map.putTransitionForAllBytes(trans1);
        map.addTransitionForAllBytes(trans2);

        NavigableMap<Integer, ByteTransition> nMap = map.getMap();
        assertEquals(1, nMap.size());
        Iterator<Map.Entry<Integer, ByteTransition>> it = nMap.entrySet().iterator();
        verifyMapEntry(it.next(), 256, trans1, trans2);
    }

    @Test
    public void testRemoveTransitionBottomOfRange() {
        map.putTransitionForAllBytes(trans1);
        map.removeTransition((byte) 0x00, trans1);

        NavigableMap<Integer, ByteTransition> nMap = map.getMap();
        assertEquals(2, nMap.size());
        Iterator<Map.Entry<Integer, ByteTransition>> it = nMap.entrySet().iterator();
        verifyMapEntry(it.next(), 1);
        verifyMapEntry(it.next(), 256, trans1);
    }

    @Test
    public void testRemoveTransitionMiddleOfRange() {
        map.putTransitionForAllBytes(trans1);
        map.removeTransition((byte) 'a', trans1);

        NavigableMap<Integer, ByteTransition> nMap = map.getMap();
        assertEquals(3, nMap.size());
        Iterator<Map.Entry<Integer, ByteTransition>> it = nMap.entrySet().iterator();
        verifyMapEntry(it.next(), 97, trans1);
        verifyMapEntry(it.next(), 98);
        verifyMapEntry(it.next(), 256, trans1);
    }

    @Test
    public void testRemoveTransitionTopOfRange() {
        map.putTransitionForAllBytes(trans1);
        map.removeTransition((byte) 0xFF, trans1);

        NavigableMap<Integer, ByteTransition> nMap = map.getMap();
        assertEquals(2, nMap.size());
        Iterator<Map.Entry<Integer, ByteTransition>> it = nMap.entrySet().iterator();
        verifyMapEntry(it.next(), 255, trans1);
        verifyMapEntry(it.next(), 256);
    }

    @Test
    public void testRemoveTransitionDifferentTransitionNoEffect() {
        map.putTransition((byte) 'a', trans1);
        map.removeTransition((byte) 'a', trans2);

        NavigableMap<Integer, ByteTransition> nMap = map.getMap();
        assertEquals(3, nMap.size());
        Iterator<Map.Entry<Integer, ByteTransition>> it = nMap.entrySet().iterator();
        verifyMapEntry(it.next(), 97);
        verifyMapEntry(it.next(), 98, trans1);
        verifyMapEntry(it.next(), 256);
    }

    @Test
    public void testRemoveTransitionForAllBytes() {
        map.putTransitionForAllBytes(trans1);
        map.removeTransitionForAllBytes(trans1);

        NavigableMap<Integer, ByteTransition> nMap = map.getMap();
        assertEquals(1, nMap.size());
        Iterator<Map.Entry<Integer, ByteTransition>> it = nMap.entrySet().iterator();
        verifyMapEntry(it.next(), 256);
    }

    @Test
    public void testIsEmpty() {
        assertTrue(map.isEmpty());
        map.addTransition((byte) 'a', trans1);
        assertFalse(map.isEmpty());
        map.removeTransition((byte) 'a', trans1);
        assertTrue(map.isEmpty());
    }

    @Test
    public void testNumberOfTransitions() {
        assertEquals(0, map.numberOfTransitions());
        map.addTransition((byte) 'a', trans1);
        assertEquals(1, map.numberOfTransitions());
        map.addTransition((byte) 'c', trans2);
        assertEquals(2, map.numberOfTransitions());
        map.putTransition((byte) 'a', trans2);
        assertEquals(1, map.numberOfTransitions());
        map.removeTransition((byte) 'a', trans2);
        assertEquals(1, map.numberOfTransitions());
        map.addTransition((byte) 'c', trans1);
        assertEquals(2, map.numberOfTransitions());
        map.removeTransition((byte) 'c', trans2);
        assertEquals(1, map.numberOfTransitions());
        map.removeTransition((byte) 'c', trans1);
        assertEquals(0, map.numberOfTransitions());
    }

    @Test
    public void testHasTransition() {
        assertFalse(map.hasTransition(trans1));
        assertFalse(map.hasTransition(trans2));

        map.addTransition((byte) 'a', trans1);
        assertTrue(map.hasTransition(trans1));
        assertFalse(map.hasTransition(trans2));

        map.addTransition((byte) 'c', trans2);
        assertTrue(map.hasTransition(trans1));
        assertTrue(map.hasTransition(trans2));

        map.putTransition((byte) 'a', trans2);
        assertFalse(map.hasTransition(trans1));
        assertTrue(map.hasTransition(trans2));

        map.removeTransition((byte) 'a', trans2);
        assertFalse(map.hasTransition(trans1));
        assertTrue(map.hasTransition(trans2));

        map.addTransition((byte) 'c', trans1);
        assertTrue(map.hasTransition(trans1));
        assertTrue(map.hasTransition(trans2));

        map.removeTransition((byte) 'c', trans2);
        assertTrue(map.hasTransition(trans1));
        assertFalse(map.hasTransition(trans2));

        map.removeTransition((byte) 'c', trans1);
        assertFalse(map.hasTransition(trans1));
        assertFalse(map.hasTransition(trans2));
    }

    @Test
    public void testGetTransition() {
        map.addTransition((byte) 'b', trans1);
        assertNull(map.getTransition((byte) 'a'));

        map.addTransition((byte) 'a', trans1);
        assertEquals(trans1, map.getTransition((byte) 'a'));

        map.addTransition((byte) 'a', trans2);
        ByteTransition trans = map.getTransition((byte) 'a');
        assertTrue(trans instanceof CompoundByteTransition);
        Set<ByteTransition> result = new HashSet<>();
        trans.expand().forEach(t -> result.add(t));
        assertEquals(new HashSet<>(Arrays.asList(trans1, trans2)), result);
    }

    @Test
    public void testGetTransitionForAllBytesAllOneTransitionExceptOneWithTwo() {
        map.addTransitionForAllBytes(trans1);
        map.addTransition((byte) 'a', trans2);
        assertEquals(coalesce(new HashSet<>(Arrays.asList(trans1))), map.getTransitionForAllBytes());
    }

    @Test
    public void testGetTransitionForAllBytesAllTwoTransitionsExceptOneWithOne() {
        map.addTransitionForAllBytes(trans1);
        map.addTransitionForAllBytes(trans2);
        map.removeTransition((byte) 'a', trans2);
        assertEquals(coalesce(new HashSet<>(Arrays.asList(trans1))), map.getTransitionForAllBytes());
    }

    @Test
    public void testGetTransitionForAllBytesAllOneTransitionExceptOneWithZero() {
        map.addTransitionForAllBytes(trans1);
        map.removeTransition((byte) 'a', trans1);
        assertEquals(ByteMachine.EmptyByteTransition.INSTANCE, map.getTransitionForAllBytes());
    }

    @Test
    public void testGetTransitionForAllBytesAllOneTransitionExceptOneDifferent() {
        map.addTransitionForAllBytes(trans1);
        map.removeTransition((byte) 'a', trans1);
        map.addTransition((byte) 'a', trans2);
        assertEquals(ByteMachine.EmptyByteTransition.INSTANCE, map.getTransitionForAllBytes());
    }

    @Test
    public void testGetTransitionForAllBytesAllThreeTransitionsExceptOneWithTwo() {
        map.addTransitionForAllBytes(trans1);
        map.addTransitionForAllBytes(trans2);
        map.addTransitionForAllBytes(trans3);
        map.addTransition((byte) 'a', trans4);
        map.removeTransition((byte) 'c', trans2);
        assertEquals(coalesce(new HashSet<>(Arrays.asList(trans1, trans3))), map.getTransitionForAllBytes());
    }

    @Test
    public void testGetTransitions() {
        map.addTransitionForAllBytes(trans1);
        map.addTransitionForAllBytes(trans2);
        map.addTransitionForAllBytes(trans3);
        map.addTransition((byte) 'a', trans4);
        map.removeTransition((byte) 'c', trans2);
        assertEquals(new HashSet<>(Arrays.asList(coalesce(new HashSet<>(Arrays.asList(trans1, trans3))),
                                                 coalesce(new HashSet<>(Arrays.asList(trans1, trans2, trans3))),
                                                 coalesce(new HashSet<>(Arrays.asList(trans1, trans2, trans3, trans4)))
                )), map.getTransitions()
        );
    }

    @Test
    public void testGetCeilings() {
        map.addTransition((byte) 'a', trans1);
        map.addTransition((byte) 'c', trans2);
        assertEquals(new HashSet<>(Arrays.asList(97, 98, 99, 100, 256)), map.getCeilings());
    }

    @Test
    public void testToString() {
        map.addTransition((byte) 'a', trans1);
        map.addTransition((byte) 'b', trans2);
        map.addTransition((byte) 'b', trans3);
        // Don't know which order the 'b' transitions will be listed, so accept either.
        String toString = map.toString();
        assertTrue(
                toString.equals(String.format("a->ByteState/%s // b->ByteState/%s,ByteState/%s // ",
                        trans1.hashCode(), trans2.hashCode(), trans3.hashCode())) ||
                        toString.equals(String.format("a->ByteState/%s // b->ByteState/%s,ByteState/%s // ",
                                trans1.hashCode(), trans3.hashCode(), trans2.hashCode()))
        );
    }

    private static void verifyMapEntry(Map.Entry<Integer, ByteTransition> entry, int ceiling,
                                       ByteTransition ... transitions) {
        assertEquals(ceiling, entry.getKey().intValue());
        Set<ByteTransition> result = new HashSet<>();
        if (entry.getValue() != null) {
            entry.getValue().expand().forEach(t -> result.add(t));
        }
        assertEquals(new HashSet<>(Arrays.asList(transitions)), result);
    }
}