package software.amazon.event.ruler;

/**
 * Represents a suggestion of a state/token combo from which there might be a transition, in an array-consistent fashion.
 */
class ACStep {
    final int fieldIndex;
    final NameState nameState;
    final ArrayMembership membershipSoFar;

    ACStep(final int fieldIndex, final NameState nameState, final ArrayMembership arrayMembership) {
        this.fieldIndex = fieldIndex;
        this.nameState = nameState;
        this.membershipSoFar = arrayMembership;
    }
}
