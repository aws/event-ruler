package software.amazon.event.ruler;

/**
 * Configuration for a GenericMachine. For descriptions of the options, see GenericMachine.Builder.
 */
class GenericMachineConfiguration {

    private final boolean additionalNameStateReuse;
    private final boolean ruleOverriding;

    GenericMachineConfiguration(boolean additionalNameStateReuse, boolean ruleOverriding) {
        this.additionalNameStateReuse = additionalNameStateReuse;
        this.ruleOverriding = ruleOverriding;
    }

    boolean isAdditionalNameStateReuse() {
        return additionalNameStateReuse;
    }

    public boolean isRuleOverriding() {
        return ruleOverriding;
    }
}

