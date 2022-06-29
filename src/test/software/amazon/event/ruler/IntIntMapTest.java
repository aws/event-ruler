package software.amazon.event.ruler;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;

public class IntIntMapTest {

    @Test(expected=IllegalArgumentException.class)
    public void constructor_disallowsInitialCapacityThatIsNotAPowerOfTwo() {
        new IntIntMap(3);
    }

    @Test
    public void put_replacesOriginalValue() {
        IntIntMap map = new IntIntMap();
        Assert.assertEquals(IntIntMap.NO_VALUE, map.put(10, 100));
        Assert.assertEquals(1, map.size());
        Assert.assertEquals(100, map.put(10, 200));
        Assert.assertEquals(1, map.size());
    }

    @Test
    public void put_disallowsNegativeKeys() {
        IntIntMap map = new IntIntMap();
        try {
            map.put(-1234, 5678);
            Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException ex) {
        }
    }

    @Test
    public void put_disallowsNegativeValues() {
        IntIntMap map = new IntIntMap();
        try {
            map.put(1234, -5678);
            Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException ex) {
        }
    }

    @Test
    public void get_canRetrieveValues() {
        IntIntMap map = new IntIntMap();
        for (int key = 0; key < 1000; key++) {
            Assert.assertEquals(IntIntMap.NO_VALUE, map.put(key, key * 2));
        }
        for (int key = 0; key < 1000; key++) {
            Assert.assertEquals(key * 2, map.get(key));
        }

        Assert.assertEquals(IntIntMap.NO_VALUE, map.get(1001));
    }

    @Test
    public void remove_canRemoveValues() {
        IntIntMap map = new IntIntMap();
        Assert.assertEquals(IntIntMap.NO_VALUE, map.remove(0));
        map.put(1234, 5678);
        Assert.assertEquals(5678, map.remove(1234));
        Assert.assertTrue(map.isEmpty());
    }

    @Test
    public void iterator_returnsAllValues() {
        IntIntMap map = new IntIntMap();
        Map<Integer, Integer> baseline = new HashMap<>();
        for (int key = 0; key < 1000; key++) {
            map.put(key, key * 2);
            baseline.put(key, key * 2);
        }

        List<IntIntMap.Entry> entries = new ArrayList<>();
        map.entries().iterator().forEachRemaining(entries::add);

        Assert.assertEquals(1000, entries.size());
        for (IntIntMap.Entry entry : entries) {
            Assert.assertEquals(map.get(entry.getKey()), entry.getValue());
            Assert.assertEquals(baseline.get(entry.getKey()).intValue(), entry.getValue());
        }
    }

    @Test
    public void iterator_returnsEmptyIteratorForEmptyMap() {
        IntIntMap map = new IntIntMap();
        Iterator<IntIntMap.Entry> iter = map.entries().iterator();
        Assert.assertFalse(iter.hasNext());
    }

    @Test
    public void iterator_throwsNoSuchElementExceptionWhenNextIsCalledWithNoMoreElements() {
        IntIntMap map = new IntIntMap();
        map.put(1, 100);
        Iterator<IntIntMap.Entry> iter = map.entries().iterator();
        Assert.assertTrue(iter.hasNext());
        IntIntMap.Entry entry = iter.next();
        Assert.assertEquals(1, entry.getKey());
        Assert.assertEquals(100, entry.getValue());

        try {
            iter.next();
            Assert.fail("expected NoSuchElementException");
        } catch (NoSuchElementException ex) {
            // expected
        }
    }

    @Test
    public void clone_createsNewBackingTable() {
        IntIntMap map = new IntIntMap();
        map.put(123, 456);

        IntIntMap cloneMap = (IntIntMap) map.clone();
        cloneMap.put(123, 789);

        Assert.assertEquals(456, map.get(123));
        Assert.assertEquals(789, cloneMap.get(123));
    }

    @Test
    public void stressTest() {
        // deterministic seed to prevent unit test flakiness
        long seed = 1;
        Random random = new Random(seed);

        // set a high load factor to increase the chances that we'll see lots of hash collisions
        float loadFactor = 0.99f;
        IntIntMap map = new IntIntMap(2, loadFactor);

        Map<Integer, Integer> baseline = new HashMap<>();

        for (int trial = 0; trial < 50; trial++) {
            for (int i = 0; i < 100_000; i++) {
                int key = random.nextInt(Integer.MAX_VALUE);
                int value = random.nextInt(Integer.MAX_VALUE);

                int mapOut = map.put(key, value);
                Integer baselineOut = baseline.put(key, value);

                Assert.assertEquals(baselineOut == null ? IntIntMap.NO_VALUE : baselineOut.intValue(), mapOut);
                Assert.assertEquals(baseline.size(), map.size());
            }

            // Now remove half, randomly
            Set<Integer> baselineKeys = new HashSet<>(baseline.keySet());
            for (Integer key : baselineKeys) {
                if (random.nextBoolean()) {
                    Assert.assertEquals(baseline.remove(key).intValue(), map.remove(key));
                }
            }
            Assert.assertEquals(baseline.size(), map.size());
        }
    }

    @Test
    public void stressTest_rehash() {
        // deterministic seed to prevent unit test flakiness
        long seed = 1;
        Random random = new Random(seed);

        for (int trial = 0; trial < 100_000; trial++) {
            // start the map off with the smallest possible initial capacity
            IntIntMap map = new IntIntMap(1);
            Map<Integer, Integer> baseline = new HashMap<>();

            for (int i = 0 ; i < 16; i++) {
                int key = random.nextInt(Integer.MAX_VALUE);
                int value = random.nextInt(Integer.MAX_VALUE);
                map.put(key, value);
                baseline.put(key, value);
            }

            for (Integer key : baseline.keySet()) {
                Assert.assertEquals(baseline.get(key).intValue(), map.get(key));
            }
        }
    }

}
