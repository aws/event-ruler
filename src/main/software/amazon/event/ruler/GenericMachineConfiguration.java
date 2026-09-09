package software.amazon.event.ruler;

/**
 * Configuration for a GenericMachine. For descriptions of the options, see GenericMachine.Builder.
 */
class GenericMachineConfiguration {

    private final boolean additionalNameStateReuse;
    private final boolean ruleOverriding;
    private final boolean useStructuredMatching;
    private final boolean ignoreTrailingContent;

    GenericMachineConfiguration(boolean additionalNameStateReuse, boolean ruleOverriding) {
        this(additionalNameStateReuse, ruleOverriding, false);
    }

    GenericMachineConfiguration(boolean additionalNameStateReuse, boolean ruleOverriding,
                                boolean useStructuredMatching) {
        this(additionalNameStateReuse, ruleOverriding, useStructuredMatching, false);
    }

    GenericMachineConfiguration(boolean additionalNameStateReuse, boolean ruleOverriding,
                                boolean useStructuredMatching, boolean ignoreTrailingContent) {
        this.additionalNameStateReuse = additionalNameStateReuse;
        this.ruleOverriding = ruleOverriding;
        this.useStructuredMatching = useStructuredMatching;
        this.ignoreTrailingContent = ignoreTrailingContent;
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

    /**
     * When true, {@code addRule} and {@code deleteRule} stop reading a rule at its root object's closing
     * brace and ignore anything after it, as every release before 2.2.0 did.
     */
    boolean isIgnoreTrailingContent() {
        return ignoreTrailingContent;
    }
}

