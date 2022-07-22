package software.amazon.event.ruler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Stack;

import static software.amazon.event.ruler.MatchType.WILDCARD;

/**
 * Evaluates the complexity of machines.
 */
public class MachineComplexityEvaluator {

    /**
     * Cap evaluation of complexity at this threshold.
     */
    private final int maxComplexity;

    public MachineComplexityEvaluator(int maxComplexity) {
        this.maxComplexity = maxComplexity;
    }

    int getMaxComplexity() {
        return maxComplexity;
    }

    /**
     * Returns the maximum possible number of wildcard rule prefixes that could match a theoretical input value for a
     * machine beginning with ByteState state. This value is equivalent to the maximum number of states a traversal
     * could be present in simultaneously, counting only states that can lead to a wildcard rule pattern. Caps out
     * evaluation at maxComplexity to keep runtime under control. Otherwise, runtime would be O(N^2), where N is the
     * number of states accessible from ByteState state.
     *
     * @param state Evaluates a machine beginning at this state.
     * @return The maximum possible number of wildcard rule prefixes, or maxComplexity, whichever is less.
     */
    int evaluate(ByteState state) {
        // Upfront cost: generate the map of all the wildcard patterns accessible from every state in the machine.
        Map<SingleByteTransition, Set<Patterns>> wildcardPatternsAccessibleFromEachTransition =
                getWildcardPatternsAccessibleFromEachTransition(state);

        Set<ByteTransition> visited = new HashSet<>();
        visited.add(state);
        int maxSize = 0;

        // We'll do a breadth-first-search but it shouldn't matter.
        Queue<ByteTransition> transitions = new LinkedList<>(state.getTransitions());
        while (!transitions.isEmpty()) {
            ByteTransition transition = transitions.remove();
            if (visited.contains(transition)) {
                continue;
            }
            visited.add(transition);

            // The sum of all the wildcard patterns accessible from each SingleByteTransition we are present in on our
            // current traversal is the number of wildcard rule prefixes matching a theoretical worst-case input value.
            int size = 0;
            for (SingleByteTransition single : transition.expand()) {
                size += wildcardPatternsAccessibleFromEachTransition.get(single).size();

                // Look for "transitions for all bytes" (i.e. wildcard transitions). Since an input value that matches
                // foo will also match foo*, we also need to include in our size wildcard patterns accessible from foo*.
                ByteState nextState = single.getNextByteState();
                if (nextState != null) {
                    Set<SingleByteTransition> transitionsForAllBytes = nextState.getTransitionForAllBytes().expand();
                    for (SingleByteTransition transitionForAllBytes : transitionsForAllBytes) {
                        if (!(transitionForAllBytes instanceof ByteMachine.EmptyByteTransition) &&
                                !(transition.expand().contains(transitionForAllBytes))) {
                            size += wildcardPatternsAccessibleFromEachTransition.get(transitionForAllBytes).size();
                        }
                    }
                }
            }
            if (size >= maxComplexity) {
                return maxComplexity;
            }
            if (size > maxSize) {
                maxSize = size;
            }

            // Load up our queue with the next round of transitions, where each transition represents a set of states
            // that could be accessed with a particular byte value.
            ByteTransition nextTransition = transition.getTransitionForNextByteStates();
            if (nextTransition != null) {
                transitions.addAll(nextTransition.getTransitions());
            }
        }

        return maxSize;
    }

    /**
     * Creates and returns a map of SingleByteTransition to all the wildcard patterns accessible from the
     * SingleByteTransition. The map includes all SingleByteTransitions accessible from ByteState state. This function
     * is O(N), where N is the number of states accessible from ByteState state.
     *
     * @param state Create a map containing all SingleByteTransitions accessible from this state.
     * @return A map of SingleByteTransition to all the wildcard patterns accessible from the SingleByteTransition.
     */
    private Map<SingleByteTransition, Set<Patterns>> getWildcardPatternsAccessibleFromEachTransition(ByteState state) {
        Map<SingleByteTransition, Set<Patterns>> result = new HashMap<>();
        Set<SingleByteTransition> visited = new HashSet<>();
        Stack<SingleByteTransition> stack = new Stack<>();
        stack.push(state);

        // We'll do a depth-first-search as a state's patterns can only be computed once the computation is complete for
        // all deeper states. Let's avoid recursion, which is prone to stack overflow.
        while (!stack.isEmpty()) {
            // Peek instead of pop. Need this transition to remain on stack so we can compute its patterns once all
            // deeper states are complete.
            SingleByteTransition transition = stack.peek();
            if (!result.containsKey(transition)) {
                result.put(transition, new HashSet<>());
            }
            Set<Patterns> patterns = result.get(transition);

            // Visited means we have already processed this transition once (via peeking) and have since computed the
            // patterns for all deeper states. Time to compute this transition's patterns then pop it from the stack.
            if (visited.contains(transition)) {
                ByteState nextState = transition.getNextByteState();
                if (nextState != null) {
                    for (ByteTransition eachTransition : nextState.getTransitions()) {
                        for (SingleByteTransition single : eachTransition.expand()) {
                            patterns.addAll(result.get(single));
                        }
                    }
                }
                stack.pop();
                continue;
            }

            visited.add(transition);

            // Add any patterns directly accessible from this transition.
            for (ByteMatch match : transition.getMatches()) {
                if (match.getPattern().type() == WILDCARD) {
                    patterns.add(match.getPattern());
                }
            }

            // Push the next round of deeper states into the stack. By the time we return back to the current transition
            // on the stack, all patterns for deeper states will have been computed.
            ByteState nextState = transition.getNextByteState();
            if (nextState != null) {
                for (ByteTransition eachTransition : nextState.getTransitions()) {
                    for (SingleByteTransition single : eachTransition.expand()) {
                        if (!visited.contains(single)) {
                            stack.push(single);
                        }
                    }
                }
            }
        }

        return result;
    }

}