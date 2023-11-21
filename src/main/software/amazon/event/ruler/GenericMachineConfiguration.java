package software.amazon.event.ruler;

/**
 * Configuration for a GenericMachine. For descriptions of the options, see GenericMachine.Builder.
 */
class GenericMachineConfiguration {

    private final boolean additionalNameStateReuse;

    GenericMachineConfiguration(boolean additionalNameStateReuse) {
        this.additionalNameStateReuse = additionalNameStateReuse;
    }

    boolean isAdditionalNameStateReuse() {
        return additionalNameStateReuse;
    }
}

