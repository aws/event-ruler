package software.amazon.event.ruler;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a state in a state machine and maps utf-8 bytes to transitions.
 */
@ThreadSafe
final class ByteState extends ByteTransition {

    /**
     * The field is {@code null} when this state contains no transitions. The field stores
     * {@link ByteTransitionEntry} when this state contains one transition and {@link ConcurrentHashMap} when this
     * state contains two or more transitions.
     */
    private volatile Object transitionStore;

    @Override
    public ByteState getNextByteState() {
        return this;
    }

    @Override
    public ByteTransition setNextByteState(ByteState nextState) {
        return nextState;
    }

    @Override
    public ByteMatch getMatch() {
        return null;
    }

    @Override
    public ByteTransition setMatch(ByteMatch match) {
        return match == null ? this : new CompositeByteTransition(this, match);
    }

    /**
     * Returns {@code true} if this state contains no transitions.
     *
     * @return {@code true} if this state contains no transitions
     */
    boolean hasNoTransitions() {
        return transitionStore == null;
    }

    /**
     * Returns the transition to which the given byte value is mapped, or {@code null} if this state contains no
     * transitions for the given byte value.
     *
     * @param utf8byte the byte value whose associated transition is to be returned
     * @return the transition to which the given byte value is mapped, or {@code null} if this state contains no
     * transitions for the given byte value
     */
    ByteTransition getTransition(byte utf8byte) {
        // saving the value to avoid reading an updated value
        Object transitionStore = this.transitionStore;
        if (transitionStore == null) {
            return null;
        } else if (transitionStore instanceof ByteTransitionEntry) {
            ByteTransitionEntry entry = (ByteTransitionEntry) transitionStore;
            return utf8byte == entry.utf8byte ? entry.transition : null;
        } else {
            @SuppressWarnings("unchecked")
            Map<Byte, ByteTransition> map = (Map<Byte, ByteTransition>) transitionStore;
            return map.get(utf8byte);
        }
    }

    @Override
    public String toString() {
        String hc = Long.toString((hashCode()));
        if (transitionStore == null) {
            return "BS: " + hc + ":null";
        } else if (transitionStore instanceof ByteTransitionEntry) {
            ByteTransitionEntry entry = (ByteTransitionEntry) transitionStore;
            ByteState next = entry.transition.getNextByteState();
            return "BS: " + hc + ":\n" + (char) entry.utf8byte + "=>" + next;
        } else {
            @SuppressWarnings("unchecked")
            Map<Byte, ByteTransition> map = (Map<Byte, ByteTransition>) transitionStore;
            StringBuilder sb = new StringBuilder("BS: " + hc);
            for (byte key : map.keySet()) {
                ByteState next = map.get(key).getNextByteState();
                String nextLabel = (next == null) ? "null" : Integer.toString(next.hashCode());
                sb.append("\n ").append((char) key).append("=>").append(nextLabel);
            }
            return sb.toString();
        }
    }




    /**
     * Associates the given transition with the given byte value in this state. If the state previously contained a
     * transition for the byte value, the old transition is replaced by the given transition.
     *
     * @param utf8byte   the byte value with which the given transition is to be associated
     * @param transition the transition to be associated with the given byte value
     */
    void putTransition(byte utf8byte, @Nonnull ByteTransition transition) {
        // saving the value to avoid reading an updated value
        Object transitionStore = this.transitionStore;
        if (transitionStore == null) {
            this.transitionStore = new ByteTransitionEntry(utf8byte, transition);
        } else if (transitionStore instanceof ByteTransitionEntry) {
            ByteTransitionEntry entry = (ByteTransitionEntry) transitionStore;
            if (utf8byte == entry.utf8byte) {
                entry.transition = transition;
            } else {
                Map<Byte, ByteTransition> map = new ConcurrentHashMap<>(2);
                map.put(entry.utf8byte, entry.transition);
                map.put(utf8byte, transition);
                this.transitionStore = map;
            }
        } else {
            @SuppressWarnings("unchecked")
            Map<Byte, ByteTransition> map = (Map<Byte, ByteTransition>) transitionStore;
            map.put(utf8byte, transition);
        }
    }

    /**
     * Removes the transition for the given byte value from this state if it is present.
     *
     * @param utf8byte the byte value whose transition is to be removed from the state
     */
    void removeTransition(byte utf8byte) {
        // saving the value to avoid reading an updated value
        Object transitionStore = this.transitionStore;
        if (transitionStore != null) {
            if (transitionStore instanceof ByteTransitionEntry) {
                ByteTransitionEntry entry = (ByteTransitionEntry) transitionStore;
                if (utf8byte == entry.utf8byte) {
                    this.transitionStore = null;
                }
            } else {
                @SuppressWarnings("unchecked")
                Map<Byte, ByteTransition> map = (Map<Byte, ByteTransition>) transitionStore;
                if (map.containsKey(utf8byte)) {
                    if (map.size() == 2) {
                        for (Map.Entry<Byte, ByteTransition> entry : map.entrySet()) {
                            if (utf8byte != entry.getKey()) {
                                this.transitionStore = new ByteTransitionEntry(entry.getKey(), entry.getValue());
                                break;
                            }
                        }
                    } else {
                        map.remove(utf8byte);
                    }
                }
            }
        }
    }

    private static final class ByteTransitionEntry {

        final byte utf8byte;
        volatile ByteTransition transition;

        ByteTransitionEntry(byte utf8byte, ByteTransition transition) {
            this.utf8byte = utf8byte;
            this.transition = transition;
        }

        @Override
        public String toString() {
            ByteState next = transition.getNextByteState();
            String nextLabel = (next == null) ? "null" : Integer.toString(next.hashCode());
            return "BT: " + (char) utf8byte + "=>" + nextLabel;

        }
    }
}
