package software.amazon.event.ruler;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A fast primitive int-int map implementation. Keys and values may only be positive.
 */
class IntIntMap implements Cloneable {

    // taken from FastUtil
    private static final int INT_PHI = 0x9E3779B9;

    private static long KEY_MASK = 0xFFFFFFFFL;
    private static final long EMPTY_CELL = -1 & KEY_MASK;

    public static final int NO_VALUE = -1;
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;

    /**
     * Capacity of 8, with data type long, translates to an initial {@link #table} of 64 bytes,
     * which fits perfectly into the common cache line size.
     */
    private static final int DEFAULT_INITIAL_CAPACITY = 8;

    /**
     * Holds key-value int pairs. The highest 32 bits hold the int value, and the lowest 32 bits
     * hold the int key. Must always have a length that is a power of two so that {@link #mask} can
     * be computed correctly.
     */
    private long[] table;

    /**
     * Load factor, must be between (0 and 1)
     */
    private final float loadFactor;

    /**
     * We will resize a map once it reaches this size
     */
    private int threshold;

    /**
     * Current map size
     */
    private int size;

    /**
     * Mask to calculate the position in the table for a key.
     */
    private int mask;

    IntIntMap() {
        this(DEFAULT_INITIAL_CAPACITY);
    }

    IntIntMap(final int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    IntIntMap(final int initialCapacity, final float loadFactor) {
        if (loadFactor <= 0 || loadFactor >= 1) {
            throw new IllegalArgumentException("loadFactor must be in (0, 1)");
        }
        if (initialCapacity <= 0) {
            throw new IllegalArgumentException("initialCapacity must be positive");
        }
        if (Integer.bitCount(initialCapacity) != 1) {
            throw new IllegalArgumentException("initialCapacity must be a power of two");
        }
        this.mask = initialCapacity - 1;
        this.loadFactor = loadFactor;
        this.table = makeTable(initialCapacity);
        this.threshold = (int) (initialCapacity * loadFactor);
    }

    /**
     * Gets the value for {@code key}.
     *
     * @param key
     *            the non-negative key
     * @return the value present at {@code key}, or {@link #NO_VALUE} if none is present.
     */
    int get(final int key) {
        int idx = getStartIndex(key);
        do {
            long cell = table[idx];
            if (cell == EMPTY_CELL) {
                // end of the chain, key does not exist
                return NO_VALUE;
            }
            if (((int) (cell & KEY_MASK)) == key) {
                // found the key
                return (int) (cell >> 32);
            }
            // continue walking the chain
            idx = getNextIndex(idx);
        } while (true);
    }

    /**
     * Puts {@code value} in {@code key}. {@code key} is restricted to positive integers to avoid an
     * unresolvable collision with {@link #EMPTY_CELL}, while {@code value} is restricted to
     * positive integers to avoid an unresolvable collision with {@link #NO_VALUE}.
     *
     * @param key
     *            the non-negative key
     * @param value
     *            the non-negative value
     * @return the value that was previously set for {@code key}, or {@link #NO_VALUE} if none was
     *         present.
     * @throws IllegalArgumentException
     *             if {@code key} is negative
     */
    int put(final int key, final int value) {
        if (key < 0) {
            throw new IllegalArgumentException("key cannot be negative");
        }
        if (value < 0) {
            throw new IllegalArgumentException("value cannot be negative");
        }
        long cellToPut = (((long) key) & KEY_MASK) | (((long) value) << 32);
        int idx = getStartIndex(key);
        do {
            long cell = table[idx];
            if (cell == EMPTY_CELL) {
                // found an empty cell
                table[idx] = cellToPut;
                if (size >= threshold) {
                    rehash(table.length * 2);
                    // 'size' is set inside rehash()
                } else {
                    size++;
                }
                return NO_VALUE;
            }
            if (((int) (cell & KEY_MASK)) == key) {
                // found a non-empty cell with a key matching the one we're writing, so overwrite it
                table[idx] = cellToPut;
                return (int) (cell >> 32);
            }
            // continue walking the chain
            idx = getNextIndex(idx);
        } while (true);
    }

    /**
     * Removes {@code key}.
     *
     * @param key
     *            the non-negative key
     * @return the removed value, or {@link #NO_VALUE} if none was present.
     * @throws IllegalArgumentException
     *             if {@code key} is negative
     */
    int remove(final int key) {
        int idx = getStartIndex(key);
        do {
            long cell = table[idx];
            if (cell == EMPTY_CELL) {
                // end of the chain, key does not exist
                return NO_VALUE;
            }
            if (((int) (cell & KEY_MASK)) == key) {
                // found the key
                size--;
                shiftKeys(idx);
                return (int) (cell >> 32);
            }
            // continue walking the chain
            idx = getNextIndex(idx);
        } while (true);
    }

    /**
     * Returns the number of key-value mappings in this map.
     *
     * @return the number of key-value mappings in this map
     */
    int size() {
        return size;
    }

    boolean isEmpty() {
        return size == 0;
    }

    public Iterable<Entry> entries() {
        return new Iterable<Entry> () {

            @Override
            public Iterator<Entry> iterator() {
                return new EntryIterator();
            }

        };
    }

    @Override
    public Object clone() {
        IntIntMap result;
        try {
            result = (IntIntMap) super.clone();
        } catch (CloneNotSupportedException e) {
            // this shouldn't happen, since we are Cloneable
            throw new InternalError(e);
        }
        result.table = table.clone();
        return result;
    }

    /**
     * Shifts entries with the same hash.
     */
    private void shiftKeys(final int index) {
        int last;
        int pos = index;
        while (true) {
            last = pos;
            do {
                pos = (pos + 1) & mask;
                if (table[pos] == EMPTY_CELL) {
                    table[last] = EMPTY_CELL;
                    return;
                }
                int key = (int) (table[pos] & KEY_MASK);
                int keyStartIndex = getStartIndex(key);
                if (last < pos) { // did we wrap around?
                    /*
                     * (no) if the previous position is after the chain startIndex for key, *or* the
                     * chain startIndex of the key is after the position we're checking, then the
                     * position we're checking now cannot be a part of the current chain
                     */
                    if (last >= keyStartIndex || keyStartIndex > pos) {
                        break;
                    }
                } else {
                    /*
                     * (yes) if the previous position is after the chain startIndex for key, *and*
                     * the chain startIndex of key is after the position we're checking, then the
                     * position we're checking now cannot be a part of the current chain
                     */
                    if (last >= keyStartIndex && keyStartIndex > pos) {
                        break;
                    }
                }
            } while (true);
            table[last] = table[pos];
        }
    }

    private void rehash(final int newCapacity) {
        threshold = (int) (newCapacity * loadFactor);
        mask = newCapacity - 1;

        final int oldCapacity = table.length;
        final long[] oldTable = table;

        table = makeTable(newCapacity);
        size = 0;

        for (int i = oldCapacity - 1; i >= 0; i--) {
            if (oldTable[i] != EMPTY_CELL) {
                final int oldKey = (int) (oldTable[i] & KEY_MASK);
                final int oldValue = (int) (oldTable[i] >> 32);
                put(oldKey, oldValue);
            }
        }
    }

    private static long[] makeTable(final int capacity) {
        long[] result = new long[capacity];
        Arrays.fill(result, EMPTY_CELL);
        return result;
    }

    private int getStartIndex(final int key) {
        return phiMix(key) & mask;
    }

    private int getNextIndex(final int currentIndex) {
        return (currentIndex + 1) & mask;
    }

    /**
     * Computes hashcode for {@code val}.
     *
     * @param val
     * @return the hashcode for {@code val}
     */
    private static int phiMix(final int val) {
        final int h = val * INT_PHI;
        return h ^ (h >> 16);
    }

    static class Entry {
        private final int key;
        private final int value;

        private Entry(final int key, final int value) {
            this.key = key;
            this.value = value;
        }

        public int getKey() {
            return key;
        }

        public int getValue() {
            return value;
        }
    }

    private class EntryIterator implements Iterator<Entry> {

        private static final int NO_NEXT_INDEX = -1;

        private int nextIndex = findNextIndex(0);

        @Override
        public boolean hasNext() {
            return nextIndex != NO_NEXT_INDEX;
        }

        @Override
        public Entry next() {
            if (nextIndex == NO_NEXT_INDEX) {
                throw new NoSuchElementException();
            }
            Entry entry = new Entry((int) (table[nextIndex] & KEY_MASK), (int) (table[nextIndex] >> 32));
            nextIndex = findNextIndex(nextIndex + 1);
            return entry;
        }

        private int findNextIndex(int fromIndex) {
            while (fromIndex < table.length) {
                if (table[fromIndex] != EMPTY_CELL) {
                    return fromIndex;
                }
                fromIndex++;
            }
            return NO_NEXT_INDEX;
        }

    }

}
