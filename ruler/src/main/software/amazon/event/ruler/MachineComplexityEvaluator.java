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
     * could be present in simultaneously, counting only states that can lead to a wildcard match pattern. This function
     * will recursively evaluate all other machines accessible via next NameStates, and will return the maximum observed
     * from any machine. Caps out evaluation at maxComplexity to keep runtime under control. Otherwise, runtime for this
     * machine would be O(MN^2), where N is the number of states accessible from ByteState state, and M is the total
     * number of ByteMachines accessible via next NameStates.
     *
     * @param state Evaluates a machine beginning at this state.
     * @return The lesser of maxComplexity and the maximum possible number of wildcard rule prefixes from any machines.
     */
    int evaluate(ByteState state) {
        // Upfront cost: generate the map of all matches accessible from every state in the machine.
        Map<SingleByteTransition, Set<ByteMatch>> matchesAccessibleFromEachTransition =
                getMatchesAccessibleFromEachTransition(state);

        Set<ByteTransition> visited = new HashSet<>();
        visited.add(state);
        int maxSize = 0;

        // We'll do a breadth-first-search but it shouldn't matter.
        Queue<ByteTransition> transitions = new LinkedList<>();
        state.getTransitions().forEach(trans -> transitions.add(trans));
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
                size += getWildcardPatterns(matchesAccessibleFromEachTransition.get(single)).size();

                // Look for "transitions for all bytes" (i.e. wildcard transitions). Since an input value that matches
                // foo will also match foo*, we also need to include in our size wildcard patterns accessible from foo*.
                ByteState nextState = single.getNextByteState();
                if (nextState != null) {
                    for (SingleByteTransition transitionForAllBytes : nextState.getTransitionForAllBytes().expand()) {
                        if (!(transitionForAllBytes instanceof ByteMachine.EmptyByteTransition) &&
                                !contains(transition.expand(), transitionForAllBytes)) {
                            size += getWildcardPatterns(matchesAccessibleFromEachTransition.get(transitionForAllBytes))
                                    .size();
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
                nextTransition.getTransitions().forEach(trans -> transitions.add(trans));
            }
        }

        // Now that we have a maxSize for this ByteMachine, let's recursively get the maxSize for each next NameState
        // accessible via any of this ByteMachine's matches. We will return the maximum maxSize.
        int maxSizeFromNextNameStates = 0;
        Set<ByteMatch> uniqueMatches = new HashSet<>();
        for (Set<ByteMatch> matches : matchesAccessibleFromEachTransition.values()) {
            uniqueMatches.addAll(matches);
        }
        for (ByteMatch match : uniqueMatches) {
            NameState nextNameState = match.getNextNameState();
            if (nextNameState != null) {
                maxSizeFromNextNameStates = Math.max(maxSizeFromNextNameStates, nextNameState.evaluateComplexity(this));
            }
        }

        return Math.max(maxSize, maxSizeFromNextNameStates);
    }

    /**
     * Generates a map of SingleByteTransition to all the matches accessible from the SingleByteTransition. The map
     * includes all SingleByteTransitions accessible from ByteState state. This function is O(N), where N is the number
     * of states accessible from ByteState state.
     *
     * @param state Starting state.
     * @return A map of SingleByteTransition to all the matches accessible from the SingleByteTransition
     */
    private Map<SingleByteTransition, Set<ByteMatch>> getMatchesAccessibleFromEachTransition(ByteState state) {
        Map<SingleByteTransition, Set<ByteMatch>> result = new HashMap<>();
        Set<SingleByteTransition> visited = new HashSet<>();
        Stack<SingleByteTransition> stack = new Stack<>();
        stack.push(state);

        // We'll do a depth-first-search as a state's matches can only be computed once the computation is complete for
        // all deeper states. Let's avoid recursion, which is prone to stack overflow.
        while (!stack.isEmpty()) {
            // Peek instead of pop. Need this transition to remain on stack so we can compute its matches once all
            // deeper states are complete.
            SingleByteTransition transition = stack.peek();
            if (!result.containsKey(transition)) {
                result.put(transition, new HashSet<>());
            }
            Set<ByteMatch> matches = result.get(transition);

            // Visited means we have already processed this transition once (via peeking) and have since computed the
            // matches for all deeper states. Time to compute this transition's matches then pop it from the stack.
            if (visited.contains(transition)) {
                ByteState nextState = transition.getNextByteState();
                if (nextState != null) {
                    for (ByteTransition eachTransition : nextState.getTransitions()) {
                        for (SingleByteTransition single : eachTransition.expand()) {
                            matches.addAll(result.get(single));
                        }
                    }
                }
                stack.pop();
                continue;
            }

            visited.add(transition);

            // Add all matches directly accessible from this transition.
            transition.getMatches().forEach(match -> matches.add(match));

            // Push the next round of deeper states into the stack. By the time we return back to the current transition
            // on the stack, all matches for deeper states will have been computed.
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

    private static boolean contains(Iterable<SingleByteTransition> iterable, SingleByteTransition single) {
        if (iterable instanceof Set) {
            return ((Set) iterable).contains(single);
        }
        for (SingleByteTransition eachSingle : iterable) {
            if (single.equals(eachSingle)) {
                return true;
            }
        }
        return false;
    }

    private static Set<Patterns> getWildcardPatterns(Set<ByteMatch> matches) {
        Set<Patterns> patterns = new HashSet<>();
        for (ByteMatch match : matches) {
            if (match.getPattern().type() == WILDCARD) {
                patterns.add(match.getPattern());
            }
        }
        return patterns;
    }

}