package software.amazon.event.ruler;

import javax.annotation.concurrent.ThreadSafe;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a state in the machine.
 *
 * The "valueTransitions" map is keyed by field name and yields a ByteMachine
 * that is used to match values.
 *
 * The "keyTransitions" map is keyed by field name and yields a NameMatcher
 * that is used to match keys for [ { exists: false } ].
 */
@ThreadSafe
class NameState {

    // the key is field name. the value is a value matcher which returns.  These have to be
    //  concurrent so they can be accessed while add/delete Rule is active in another thread,
    //  without any locks.
    private final Map<String, ByteMachine> valueTransitions = new ConcurrentHashMap<>();

    // the key is field name. the value is a name matcher which contains the next state after matching
    // an [ { exists: false } ].  These have to be concurrent so they can be accessed
    // while add/delete Rule is active in another thread, without any locks.
    private final Map<String, NameMatcher<NameState>> mustNotExistMatchers = new ConcurrentHashMap<>(1);

    private final Set<Object> rules = Collections.newSetFromMap(new ConcurrentHashMap<>());

    ByteMachine getTransitionOn(final String token) {
        return valueTransitions.get(token);
    }

    Set<Object> getRules() {
        return rules;
    }

    // TODO: unit-test these two methods
    boolean hasRule(final Object name) {
        return rules.contains(name);
    }
    void deleteRule(final Object name) {
        rules.remove(name);
    }

    void removeTransition(String name) {
        valueTransitions.remove(name);
    }

    void removeKeyTransition(String name) {
        mustNotExistMatchers.remove(name);
    }

    boolean isEmpty() {
        return valueTransitions.isEmpty() && rules.isEmpty() && mustNotExistMatchers.isEmpty();
    }

    void addRule(final Object rule) {
        rules.add(rule);
    }

    void addTransition(final String key, final ByteMachine to) {
        valueTransitions.put(key, to);
    }

    void addKeyTransition(final String key, final NameMatcher<NameState> to) {
        mustNotExistMatchers.put(key, to);
    }

    NameMatcher<NameState> getKeyTransitionOn(final String token) {
        return mustNotExistMatchers.get(token);
    }

    boolean hasKeyTransitions() {
        return !mustNotExistMatchers.isEmpty();
    }

    Set<NameState> getNameTransitions(final String[] event) {

        Set<NameState> nextNameStates = new HashSet<>();

        if (mustNotExistMatchers.isEmpty()) {
            return nextNameStates;
        }

        Set<NameMatcher<NameState>> absentValues = new HashSet<>(mustNotExistMatchers.values());

        for (int i = 0; i < event.length; i += 2) {

            NameMatcher<NameState> matcher = mustNotExistMatchers.get(event[i]);
            if (matcher != null) {
                absentValues.remove(matcher);
                if (absentValues.isEmpty()) {
                    break;
                }
            }
        }

        for (NameMatcher<NameState> nameMatcher: absentValues) {
            nextNameStates.add(nameMatcher.getNextState());
        }

        return nextNameStates;
    }

   Set<NameState> getNameTransitions(final Event event, final ArrayMembership membership) {

        final Set<NameState> nextNameStates = new HashSet<>();

        if (mustNotExistMatchers.isEmpty()) {
            return nextNameStates;
        }

        Set<NameMatcher<NameState>> absentValues = new HashSet<>(mustNotExistMatchers.values());

        for (Field field :  event.fields) {
            NameMatcher<NameState> matcher = mustNotExistMatchers.get(field.name);
            if (matcher != null) {
                // we should only consider the field who doesn't violate array consistency.
                // normally, we should first check array consistency of field, then check mustNotExistMatchers, but
                // for performance optimization, we first check mustNotExistMatchers because the hashmap.get is cheaper
                // than AC check.
                if (ArrayMembership.checkArrayConsistency(membership, field.arrayMembership) != null) {
                    absentValues.remove(matcher);
                    if (absentValues.isEmpty()) {
                        break;
                    }
                }
            }
        }

        for (NameMatcher<NameState> nameMatcher: absentValues) {
            nextNameStates.add(nameMatcher.getNextState());
        }

        return nextNameStates;
    }

    @Override
    public String toString() {
        return "NameState{" +
                "valueTransitions=" + valueTransitions +
                ", mustNotExistMatchers=" + mustNotExistMatchers +
                ", rules=" + rules +
                '}';
    }
}
