package software.amazon.event.ruler.input;

/**
 * The different types of InputCharacters, created from a rule, that will be used to add the rule to a ByteMachine.
 */
public enum InputCharacterType {
    BYTE,
    MULTI_BYTE_SET,
    WILDCARD
}
