package software.amazon.event.ruler;

/**
 * Represents which JSON arrays within an Event structure a particular field appears within, and at which position.
 *  The arrays are identified using integers.
 */
class ArrayMembership {
    private static final IntIntMap EMPTY = new IntIntMap();

    private IntIntMap membership;

    ArrayMembership() {
        membership = new IntIntMap();
    }

    ArrayMembership(final ArrayMembership membership) {
        if (membership.size() == 0) {
            this.membership = EMPTY;
        } else {
            this.membership = (IntIntMap) membership.membership.clone();
        }
    }

    void putMembership(int array, int index) {
        if (index == IntIntMap.NO_VALUE) {
            membership.remove(array);
        } else {
            membership.put(array, index);
        }
    }
    void deleteMembership(int array) {
        membership.remove(array);
    }
    int getMembership(int array) {
        return membership.get(array);
    }
    boolean isEmpty() {
        return membership.isEmpty();
    }
    private int size() {
        return membership.size();
    }

    // for debugging
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (IntIntMap.Entry entry : membership.entries()) {
            sb.append(entry.getKey()).append('[').append(entry.getValue()).append("] ");
        }
        return sb.toString();
    }

    /**
     * We are stepping through the NameState machine field by field, and have built up data on the array memberships
     *  observed so far in this map. We need to compare this to the array-membership data of the field we're looking at
     *  and see if they are consistent.  Either or both memberships might be empty, which simplifies things.
     * Method returns null if the new field's membership is inconsistent with so-far membership.  If it is compatible,
     *  returns the possibly-revised array membership of the matching task.
     *
     * @param fieldMembership Array membership of the field under consideration
     * @param membershipSoFar Array membership observed so far in a rule-matching task
     * @return null or the new matching-task membership so far
     */
    static ArrayMembership checkArrayConsistency(final ArrayMembership membershipSoFar, final ArrayMembership fieldMembership) {

        // no existing memberships, so we'll take the ones from the field, if any
        if (membershipSoFar.isEmpty()) {
            return fieldMembership.isEmpty() ? membershipSoFar : new ArrayMembership(fieldMembership);
        }

        // any change will come from memberships in the new field we're investigating. For each of its memberships
        ArrayMembership newMembership = null;
        for (IntIntMap.Entry arrayEntry : fieldMembership.membership.entries()) {
            final int array = arrayEntry.getKey();
            final int indexInThisArrayOfThisField = arrayEntry.getValue();
            final int indexInThisArrayPreviouslyAppearingInMatch = membershipSoFar.getMembership(array);

            if (indexInThisArrayPreviouslyAppearingInMatch == IntIntMap.NO_VALUE) {

                // if there's no membership so far, this is an acceptable delta. Update the new memberships, first
                //  creating it if necessary
                if (newMembership == null) {
                    newMembership = new ArrayMembership(membershipSoFar);
                }
                newMembership.putMembership(array, indexInThisArrayOfThisField);

            } else {

                // This field does appear within an index that has already appeared in the matching task so far.
                //  If it's in the same element, fine, no updates. If it's a different entry, return null to
                //  signal array-inconsistency.
                if (indexInThisArrayOfThisField != indexInThisArrayPreviouslyAppearingInMatch) {
                    return null;
                }
            }
        }

        // we may have scanned all the fields and not added anything, in which case return the input
        return (newMembership == null) ? membershipSoFar : newMembership;
    }
}
