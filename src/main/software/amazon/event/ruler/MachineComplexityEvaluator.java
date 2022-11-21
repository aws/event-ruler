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
     * evaluation at maxComplexity to keep runtime under control. Otherwise, runtime for this machine would be O(N^2),
     * where N is the number of states accessible from ByteState state. This function will also recursively evaluate all
     * other machines accessible via next NameStates, and will return the maximum observed from any machine.
     *
     * @param state Evaluates a machine beginning at this state.
     * @return The lesser of maxComplexity and the maximum possible number of wildcard rule prefixes from any machines.
     */
    int evaluate(ByteState state) {
        // Upfront cost: generate the map of all the wildcard patterns accessible from every state in the machine.
        // This also evaluates the complexity of all nested machines via next Namestates.
        Map<SingleByteTransition, Set<Patterns>> wildcardPatternsAccessibleFromEachTransition = new HashMap<>();
        int nextNameStateMaxSize = getAccessibleWildcardPatternsAndMaxNextNameStateComplexity(state,
                wildcardPatternsAccessibleFromEachTransition);

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

        return Math.max(nextNameStateMaxSize, maxSize);
    }

    /**
     * Populates a map of SingleByteTransition to all the wildcard patterns accessible from the SingleByteTransition.
     * The map includes all SingleByteTransitions accessible from ByteState state. Also recursively evaluates the
     * complexity of all other machines accessible through any next NameStates from this machine's matches. This
     * function is O(N), where N is the number of states accessible from ByteState state, plus the time to recursively
     * evaluate all other machines, which caps out below O(N^2) for each machine.
     *
     * Note: this function has two purposes. Although we could split this into two separate functions, doing it this way
     * saves a traversal.
     *
     * @param state Starting state.
     * @param result Populate this map with all SingleByteTransitions mapped to all accessible wildcard patterns.
     * @return The maximum complexity from all other machines accessible through any next NameStates.
     */
    private int getAccessibleWildcardPatternsAndMaxNextNameStateComplexity(ByteState state,
                                                                           Map<SingleByteTransition, Set<Patterns>> result) {
        int maxComplexity = 0;
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

            // Consider all patterns directly accessible from this transition.
            for (ByteMatch match : transition.getMatches()) {
                // Add pattern if it is a wildcard pattern.
                if (match.getPattern().type() == WILDCARD) {
                    patterns.add(match.getPattern());
                }

                // Evaluate the complexity of any next NameState.
                NameState nextNameState = match.getNextNameState();
                if (nextNameState != null) {
                    maxComplexity = Math.max(maxComplexity, nextNameState.evaluateComplexity(this));
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

        return maxComplexity;
    }

}