package software.amazon.event.ruler.input;

import static software.amazon.event.ruler.input.InputCharacterType.WILDCARD;

/**
 * An InputCharacter that represents a wildcard.
 */
public class InputWildcard extends InputCharacter {

    InputWildcard() { }

    @Override
    public InputCharacterType getType() {
        return WILDCARD;
    }

    @Override
    public boolean equals(Object o) {
        return o != null && o.getClass() == getClass();
    }

    @Override
    public int hashCode() {
        return InputWildcard.class.hashCode();
    }

    @Override
    public String toString() {
        return "Wildcard";
    }
}
