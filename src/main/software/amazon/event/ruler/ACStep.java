package software.amazon.event.ruler;

import java.util.Set;

/**
 * Represents a suggestion of a state/token combo from which there might be a transition, in an array-consistent fashion.
 */
class ACStep {
    final int fieldIndex;
    final NameState nameState;
    final Set<Double> candidateSubRuleIds;
    final ArrayMembership membershipSoFar;

    ACStep(final int fieldIndex, final NameState nameState, final Set<Double> candidateSubRuleIds,
           final ArrayMembership arrayMembership) {
        this.fieldIndex = fieldIndex;
        this.nameState = nameState;
        this.candidateSubRuleIds = candidateSubRuleIds;
        this.membershipSoFar = arrayMembership;
    }
}
