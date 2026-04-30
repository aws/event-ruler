package software.amazon.event.ruler;

/**
 * Configuration for a GenericMachine. For descriptions of the options, see GenericMachine.Builder.
 */
class GenericMachineConfiguration {

    private final boolean additionalNameStateReuse;
    private final boolean ruleOverriding;
    private final boolean useStructuredMatching;

    GenericMachineConfiguration(boolean additionalNameStateReuse, boolean ruleOverriding) {
        this(additionalNameStateReuse, ruleOverriding, false);
    }

    GenericMachineConfiguration(boolean additionalNameStateReuse, boolean ruleOverriding,
                                boolean useStructuredMatching) {
        this.additionalNameStateReuse = additionalNameStateReuse;
        this.ruleOverriding = ruleOverriding;
        this.useStructuredMatching = useStructuredMatching;
    }

    boolean isAdditionalNameStateReuse() {
        return additionalNameStateReuse;
    }

    public boolean isRuleOverriding() {
        return ruleOverriding;
    }

    /**
     * When true, {@link GenericMachine#rulesForJSONEvent(String)} uses {@link StructuredFinder}
     * instead of {@link ACFinder}, providing linear performance on events with large arrays.
     */
    boolean isUseStructuredMatching() {
        return useStructuredMatching;
    }
}

