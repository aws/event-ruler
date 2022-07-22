package software.amazon.event.ruler.input;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import static software.amazon.event.ruler.input.InputCharacterType.MULTI_BYTE_SET;

/**
 * An InputCharacter that represents a set of MultiBytes.
 */
public class InputMultiByteSet extends InputCharacter {

    private final Set<MultiByte> multiBytes;

    InputMultiByteSet(Set<MultiByte> multiBytes) {
        this.multiBytes = Collections.unmodifiableSet(multiBytes);
    }

    public static InputMultiByteSet cast(InputCharacter character) {
        return (InputMultiByteSet) character;
    }

    public Set<MultiByte> getMultiBytes() {
        return multiBytes;
    }

    @Override
    public InputCharacterType getType() {
        return MULTI_BYTE_SET;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || !(o.getClass() == getClass())) {
            return false;
        }

        return ((InputMultiByteSet) o).getMultiBytes().equals(getMultiBytes());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getMultiBytes());
    }

    @Override
    public String toString() {
        return getMultiBytes().toString();
    }

}
