package software.amazon.event.ruler;

import java.util.Set;

class ACStep {
    final int fieldIndex;
    final NameState nameState;
    final Set<SubRuleContext> candidateSubRuleIds;
    final ArrayMembership membershipSoFar;

    ACStep(final int fieldIndex, final NameState nameState, final Set<SubRuleContext> candidateSubRuleIds,
           final ArrayMembership arrayMembership) {
        this.fieldIndex = fieldIndex;
        this.nameState = nameState;
        this.candidateSubRuleIds = candidateSubRuleIds;
        this.membershipSoFar = arrayMembership;
    }
}
